package com.poseidon.ad.core.processor

import androidx.annotation.MainThread
import com.poseidon.ad.cloud.FirebaseCloud
import com.poseidon.ad.common.storage.db.DriverFactory
import com.poseidon.ad.common.storage.db.PoseidonRoomDatabase
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.common.storage.db.createDatabase
import com.poseidon.ad.common.storage.sp.SpHelper
import com.poseidon.ad.common.toolkit.checkMainThread
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.isMainThread
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.watchNetworkConnection
import com.poseidon.ad.main.preload.notification.NotificationSplashAdManager
import com.poseidon.ad.main.preload.new.shouldBlockOrdinaryPreload
import com.poseidon.ad.main.preload.notification.preloadAdWhenNotificationDisplayedSceneName
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenConnectivityChanged
import com.poseidon.ad.main.processor.createPlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.show.FullscreenAdImpressionIntervalLimitConfiguration
import com.poseidon.ad.main.strategy.aeo.AeoCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.main.strategy.estimated.ecpm.EstimatedEcpmCfg
import com.poseidon.ad.main.strategy.limit.show.AdLimitShowCfg
import com.poseidon.ad.main.strategy.rrc.RrcRoasCfg
import com.poseidon.ad.restrict.gdpr.ConsentManagementPlatform
import com.poseidon.ad.restrict.show.AdShowLimitConfigProcessor
import com.poseidon.ad.special.ThreadCountWatcher
import com.poseidon.ad.special.topon.AdjustAttributionNetworkStore
import com.poseidon.ad.special.topon.AdjustCampaignStore
import com.poseidon.ad.special.topon.FirebaseUserPropertySyncer
import com.poseidon.ad.special.topon.TopOnTrafficGrouping
import com.poseidon.ad.statistics.FirebaseAnalytics
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.experimental.ExperimentalObjCRefinement

/**
 * 平台抽象层，承担跨端共享的初始化、配置与能力接入工作。
 */
@Suppress("SpellCheckingInspection")
abstract class PoseidonSetupBaseProcessor(
    val debug: Boolean,
    val newUserFirstOpenTime: () -> Long,
    val awaitNewUserFirstOpenTime: suspend () -> Long?,
    val isNewUser: () -> Boolean,
    val remoteConfig: FirebaseCloud,
    val analyticsFactory: FirebaseAnalytics.Factory,
    val sharedPreference: SpHelper,
    private val driverFactory: DriverFactory,
) {

    /**
     * 跨端共享的数据库实例，按需懒加载确保线程安全。
     * Lazily initialized shared Room database guarded for thread safety.
     */
    private val databaseInstance: PoseidonRoomDatabase by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        createDatabase(driverFactory = driverFactory)
    }

    suspend fun database(): PoseidonRoomDatabase {
        return RoomPolicyDispatcher.run { databaseInstance }
    }

    init {
        if (debug) {
            Napier.base(DebugAntilog(defaultTag = "Poseidon"))
            ThreadCountWatcher.start()
        }
    }

    /**
     * 启动核心能力，注册远程配置监听并触发必要的预加载逻辑。
     */
    @MainThread
    open fun initialize() {
        logInfo(TAG) { "initialize: debug=$debug, remoteConfig=$remoteConfig, isMainThread=${isMainThread()}" }
        checkNotNull(instanceAtomic.value) { "Must call register() before initialize()" }
        checkMainThread { "initialize() must be called from the main thread." }
        prewarmDiskBackedState()
       dispatchToBackground {
           remoteConfig.addOnConfigUpdateListener(PoseidonConfig.Companion)
           remoteConfig.addOnConfigUpdateListener(AeoCfg.Companion)
           remoteConfig.addOnConfigUpdateListener(EstimatedEcpmCfg.Companion)
           remoteConfig.addOnConfigUpdateListener(RrcRoasCfg.Companion)
           remoteConfig.addOnConfigUpdateListener(FullscreenAdImpressionIntervalLimitConfiguration.Companion)
           remoteConfig.addOnConfigUpdateListener(AdLimitShowCfg.Companion)
           remoteConfig.onAttach()
       }
//        watchNetworkConnection { isConnected ->
//            if (isConnected) {
//                dispatchToBackground {
//                    logInfo(TAG) { "network isConnected, check sharepool, check notification" }
//                    tryPreloadSharedAdWhenConnectivityChanged()
//                    preloadAdWhenNotificationDisplayedSceneName()
//                        ?.takeUnless { it.isEmpty() }?.let { sceneName ->
//                            if (shouldBlockOrdinaryPreload(sceneName = sceneName)) {
//                                logInfo(TAG) {
//                                    "Skip notification preload on connectivity change because ordinary preload is still gated: sceneName=$sceneName"
//                                }
//                            } else {
//                                NotificationSplashAdManager.preloadAd(
//                                    loadParameter = createPlatformInterstitialAdLoadParameter(
//                                        sceneName = sceneName
//                                    )
//                                )
//                            }
//                        }
//                }
//            }
//        }
        ConsentManagementPlatform.appStartup()
        AdShowLimitConfigProcessor.startAutoCleanTask()
        AdjustAttributionNetworkStore.readjustAttributionNetworkWhenStartup()
        AdjustCampaignStore.readjustAttributionCampaignWhenStartup()
        TopOnTrafficGrouping.appStartup()
        platformInitialize()
    }

    /**
     * 平台相关的额外初始化逻辑，由具体实现决定是否覆盖。
     */
    protected open fun platformInitialize() {}

    private fun prewarmDiskBackedState() {
        dispatchToBackground {
            runCatching {
                Cryptor.prewarm()
                //moves first-open disk cost out of the main startup/callback path.
                database().openHelper.writableDatabase
                PoseidonConfig.prewarm()
                EstimatedEcpmCfg.prewarm()
                FirebaseUserPropertySyncer.syncAtColdStart()
            }.onFailure { throwable ->
                logInfo(TAG) { "Prewarm disk-backed state failed: ${throwable.message}" }
            }
        }
    }

    /**
     * 查询应用首次安装时间，供频控与策略判断使用。
     */
    abstract fun getAppInstallTime(): Long

    /**
     * 查询最近一次应用更新的时间戳。
     */
    abstract fun getAppUpdateTime(): Long

    /**
     * 完成平台实例注册，确保全局单例可用。
     */
    @MainThread
    open fun register(): PoseidonSetupBaseProcessor {
        logInfo(TAG) { "register: debug=$debug, remoteConfig=$remoteConfig, isMainThread=${isMainThread()}" }
        checkMainThread { "register() must be called from the main thread." }
        check(instanceAtomic.compareAndSet(null, this)) {
            "PoseidonSetupProcessor instance already set."
        }
        return this
    }

    companion object {
        private const val TAG = "PoseidonSetupProcessor"

        /**
         * 全局持有的跨端平台实例引用。
         */
        internal val instanceAtomic: AtomicRef<PoseidonSetupBaseProcessor?> = atomic(null)

        /**
         * 获取已注册的平台实例并转换为期望类型。
         */
        internal inline fun <reified T : PoseidonSetupBaseProcessor> getInstance(): T {
            val instance = checkNotNull(instanceAtomic.value) { "PoseidonSetupProcessor instance not set." }
            return if (instance is T) {
                instance
            } else {
                error("PoseidonSetupProcessor instance is not ${T::class}.")
            }
        }

        @OptIn(ExperimentalObjCRefinement::class)
        /**
         * 向 SDK 其他模块提供的平台抽象实例。
         */
        fun poseidonSetupProcessor(): PoseidonSetupBaseProcessor = getInstance()
    }
}
