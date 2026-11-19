package com.poseidon.ad.cloud

import android.os.Environment
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Firebase 云配置层封装，负责与 Firebase Remote Config 通讯，并在 Debug 环境下支持本地 SD 卡覆盖。
 */
class FirebaseCloudImpl(private val delegateProvider: () -> FirebaseRemoteConfig) : FirebaseCloud {

    companion object {
        private const val TAG = "FirebaseCloudImpl"
        private const val LOCAL_CONFIG_FILE_NAME = "poseidon_local_config.properties"
    }

    /**
     * Firebase Remote Config 真实实例，懒加载确保首次使用时才初始化。
     */
    private val delegate: FirebaseRemoteConfig by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { delegateProvider() }

    /**
     * 内存缓存，避免每次 getString 都走 Firebase 的阻塞式 getBlocking 路径。
     * 首次读取后缓存，配置更新时清空缓存使下次读取刷新。
     */
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val listeners = CopyOnWriteArraySet<FirebaseCloudUpdateListener>()
    private val attachedListener = AtomicBoolean(false)

    /**
     * 本地覆盖配置，Debug 模式下从外部存储一次性读取，读取不到时保持空集合。
     */
    private val localProperties: Properties by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val properties = Properties()
        val context = PoseidonSetupProcessor.getInstance().application
        if (isExternalStorageReadable(context)) {
            val localFile = File(Environment.getExternalStorageDirectory(), LOCAL_CONFIG_FILE_NAME)
            if (localFile.exists() && localFile.isFile) {
                runCatching {
                    localFile.inputStream().use { stream ->
                        properties.load(stream)
                    }
                }.onSuccess {
                    logInfo(TAG) { "Loaded local override config from ${localFile.absolutePath}." }
                }.onFailure { throwable ->
                    logWarn(throwable = throwable, tag = TAG) {
                        "Failed to load local override config: ${throwable.message}"
                    }
                }
            } else {
                logInfo(TAG) { "Local override config not found at ${localFile.absolutePath}, skip loading." }
            }
        } else {
            logInfo(TAG) { "External storage is not readable, skip local override config loading." }
        }
        properties
    }

    /**
     * 获取目标 key 对应的配置，Debug 模式优先返回本地覆盖值。
     */
    override fun getString(key: String): String {
        val processor = PoseidonSetupProcessor.getInstance()
        if (processor.debug) {
            val localValue = localProperties.getProperty(key)
            if (localValue != null) {
                logInfo(TAG) { "Return local override for key='$key'." }
                return localValue
            }
            logInfo(TAG) { "Local override missing for key='$key', fallback to remote value." }
        }
        val cached = memoryCache[key]
        if (cached != null) {
            return cached
        }
        val remoteValue = delegate.getString(key)
        memoryCache[key] = remoteValue
        logInfo(TAG) { "Return remote config for key='$key'." }
        return remoteValue
    }

    override fun getBoolean(key: String): Boolean {
        val processor = PoseidonSetupProcessor.getInstance()
        if (processor.debug) {
            val localValue = localProperties.getProperty(key)
            if (localValue != null) {
                logInfo(TAG) { "Return local boolean override for key='$key'." }
                return localValue.toBoolean()
            }
            logInfo(TAG) { "Local boolean override missing for key='$key', fallback to remote value." }
        }
        return delegate.getBoolean(key)
    }

    /**
     * 注册远端配置更新监听，便于将更新事件透传到上层。
     */
    @Synchronized
    override fun addOnConfigUpdateListener(configUpdateListener: FirebaseCloudUpdateListener) {
        listeners += configUpdateListener
    }

    override fun onAttach() {
        logInfo(TAG) { "on attach Firebase Remote Config" }
        if (!attachedListener.compareAndSet(false, true)) return
        runCatching {
            delegate.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(update: ConfigUpdate) {
                    delegate.fetchAndActivate().addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            logInfo(TAG) { "Remote config updated but fetch and activate failed: ${task.exception?.message}" }
                            return@addOnCompleteListener
                        }
                        logInfo(TAG) { "Remote config updated, keys=${update.updatedKeys}." }
                        if (listeners.isEmpty()) {
                            logInfo(TAG) { "Remote config no listeners" }
                        }
                        memoryCache.clear()
                        listeners.forEach {
                            it.onUpdate(update.updatedKeys)
                        }
                    }
                }

                override fun onError(exception: FirebaseRemoteConfigException) {
                    logWarn(throwable = exception, tag = TAG) {
                        "Remote config listener error: ${exception.message}"
                    }
                }
            })
        }.onFailure {
            attachedListener.set(false)
        }
    }

    override fun callOnFetch() {
        delegate.fetch(0L)
        logInfo(TAG) { "Remote call on fetch!" }
    }
}
