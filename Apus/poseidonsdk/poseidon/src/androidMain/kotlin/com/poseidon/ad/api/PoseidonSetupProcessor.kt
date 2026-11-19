package com.poseidon.ad.api

import com.poseidon.ad.cloud.FirebaseCloudImpl
import com.poseidon.ad.common.storage.db.DriverFactory
import com.poseidon.ad.common.storage.sp.SpHelper
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.main.adinfo.ActivityLifecycleCallbacks
import com.poseidon.ad.main.processor.CriticalPressureBreaker
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.max.init.MaxSetup
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.restrict.new.NewUserInstallInfoProvider
import com.poseidon.ad.restrict.new.NewUserTimelineTracker
import com.poseidon.ad.statistics.FirebaseAnalytics
import com.poseidon.ad.statistics.FirebaseAnalyticsImpl
import com.poseidon.ad.statistics.ReporterWrapper
import com.poseidon.ad.statistics.SupportAnalyticsBackend
import java.util.concurrent.atomic.AtomicReference

/**
 * Android端实现，负责与系统层交互并承接业务配置。
 */
@Suppress("SpellCheckingInspection")
class PoseidonSetupProcessor private constructor(
    val application: android.app.Application,
    firebaseRemoteConfigProvider: () -> com.google.firebase.remoteconfig.FirebaseRemoteConfig,
    val mainActivityClass: Class<out android.app.Activity>,
    private val poseidonSharedPreference: SpHelper,
    private val poseidonTimelineTracker: NewUserTimelineTracker,
) : com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor(
    debug = com.poseidon.ad.BuildConfig.POSEIDON_DEBUG_SWITCH,
    newUserFirstOpenTime = poseidonTimelineTracker::getFirstOpenTime,
    awaitNewUserFirstOpenTime = poseidonTimelineTracker::awaitFirstOpenTime,
    isNewUser = { NewUserInstallInfoProvider.isNewUser() },
    remoteConfig = FirebaseCloudImpl(delegateProvider = firebaseRemoteConfigProvider),
    analyticsFactory = object : FirebaseAnalytics.Factory() {
        override fun create(by: SupportAnalyticsBackend): FirebaseAnalytics {
            return ReporterWrapper(
                delegate = when (by) {
                    SupportAnalyticsBackend.Firebase -> FirebaseAnalyticsImpl(
                        application
                    )
                }
            )
        }
    },
    sharedPreference = poseidonSharedPreference,
    driverFactory = DriverFactory(context = application)
) {

    constructor(
        application: android.app.Application,
        firebaseRemoteConfigProvider: () -> com.google.firebase.remoteconfig.FirebaseRemoteConfig = {
            com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
        },
        mainActivityClass: Class<out android.app.Activity>,
        poseidonSharedPreference: SpHelper = SpHelper(application),
    ) : this(
        application = application,
        firebaseRemoteConfigProvider = firebaseRemoteConfigProvider,
        mainActivityClass = mainActivityClass,
        poseidonSharedPreference = poseidonSharedPreference,
        poseidonTimelineTracker = NewUserTimelineTracker(poseidonSharedPreference),
    )

    internal val memoryPressureListenerRef = AtomicReference<PoseidonMemoryPressureListener?>()

    /**
     * 在主进程中初始化 Android 端依赖与广告聚合 SDK。
     */
    override fun platformInitialize() {
        application.registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks)
        application.registerComponentCallbacks(CriticalPressureBreaker.componentCallbacks)
        dispatchToBackground {
            initializeConfiguredMediations()
        }
    }

    /**
     * 将门面 API 暴露在 Android 端实现中，保持父类逻辑不变。
     */
    @androidx.annotation.MainThread
    override fun initialize() {
        dispatchToBackground {
            poseidonTimelineTracker.hydrate()
        }
        super.initialize()
    }

    /**
     * 重写注册函数，返回更精确的类型供调用方使用。
     */
    @androidx.annotation.MainThread
    override fun register(): PoseidonSetupProcessor {
        super.register()
        return this
    }

    fun inNewUserFirstDayAfterInstalled(): Boolean {
        return poseidonTimelineTracker.inFirstDayAfterInstall()
    }

    fun inNewUserNextDayAfterInstalled(): Boolean {
        return poseidonTimelineTracker.inNextDayAfterInstall()
    }

    fun setMemoryPressureListener(listener: PoseidonMemoryPressureListener?) {
        memoryPressureListenerRef.set(listener)
    }

    private fun initializeConfiguredMediations() {
        val mediations = PoseidonConfig.get()?.mediations
        if (mediations == null) {
            logInfo(TAG) { "Mediations config missing, initializing all aggregators" }
            initTopOn()
//            initAdmob()
//            initMax()
            return
        }

        var initialized = false
        if (mediations.topOn != null) {
            initTopOn()
            initialized = true
        } else {
            logInfo(TAG) { "TopOn config missing, skip TopOn initialization" }
        }

//        if (mediations.admob != null) {
//            initAdmob()
//            initialized = true
//        } else {
//            logInfo(TAG) { "AdMob config missing, skip AdMob initialization" }
//        }
//
//        if (mediations.max != null) {
//            initMax()
//            initialized = true
//        } else {
//            logInfo(TAG) { "MAX config missing, skip MAX initialization" }
//        }

        if (!initialized) {
            logInfo(TAG) { "No mediation configuration available, initializing all aggregators by default" }
            initTopOn()
//            initAdmob()
//            initMax()
        }
    }

    private fun initTopOn() {
        TopOnSetup.init(com.poseidon.ad.core.processor.MediationSetupCallback.Companion.Empty)
    }

    private fun initAdmob() {
        AdmobSetup.init(com.poseidon.ad.core.processor.MediationSetupCallback.Companion.Empty)
    }

    private fun initMax() {
        MaxSetup.init(com.poseidon.ad.core.processor.MediationSetupCallback.Companion.Empty)
    }

    /**
     * 拉取系统安装时间，便于做新老用户判定。
     */
    override fun getAppInstallTime(): Long {
        return runCatching {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            packageInfo.firstInstallTime.also { installTime ->
                logInfo(TAG) { "App install time: $installTime" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to get app install time: ${throwable.message}" }
        }.getOrDefault(System.currentTimeMillis())
    }

    override fun getAppUpdateTime(): Long {
        return runCatching {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            packageInfo.lastUpdateTime.also { updateTime ->
                logInfo(TAG) { "App update time: $updateTime" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to get app update time: ${throwable.message}" }
        }.getOrDefault(System.currentTimeMillis())
    }

    companion object {

        private const val TAG = "PoseidonSetupProcessor"

        /**
         * 标记应用是否处于前台，供广告策略使用。
         */
        var isAppInForeground: Boolean = false

        @JvmStatic
        /**
         * 提供实例的便捷获取方式。
         */
        fun getInstance(): PoseidonSetupProcessor = getInstance<PoseidonSetupProcessor>()
    }
}
