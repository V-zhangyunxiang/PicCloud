package com.poseidon.ad.statistics

import com.poseidon.ad.BuildConfig
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey

/**
 * Analyzer 抽象基类，封装远程开关和事件构造逻辑。
 */
internal abstract class Reporter(private val eventName: String) {

    /**
     * 触发一次打点，先读取远程开关再执行。
     */
    fun analyze() {
        val enable = poseidonSetupProcessor().remoteConfig.getString("poseidon_analyzer_enable_$eventName")
            .toBooleanStrictOrNull() ?: defaultEnable()
        if (!enable) {
            return
        }
        val analytics = poseidonSetupProcessor().analyticsFactory.obtain(by = SupportAnalyticsBackend.Firebase)
        val parameters = makeParameters()
            .toMutableMap()
            .apply {
                if (!containsKey(ParamKey.POSEIDON_CONFIG_VERSION)) {
                    // Attach current Poseidon config version for analytics correlation.
                    put(ParamKey.POSEIDON_CONFIG_VERSION, resolvePoseidonConfigVersion()?.toString())
                }
                if (!containsKey(ParamKey.POSEIDON_SDK_VERSION)) {
                    // Attach SDK build label (version-flavor-buildType) to every reporter event.
                    put(ParamKey.POSEIDON_SDK_VERSION, poseidonSdkVersionLabel())
                }
            }
        analytics.logEvent(name = eventName, parameters = parameters)
    }

    /**
     * 默认开关值，子类可覆盖。
     */
    protected open fun defaultEnable(): Boolean = true

    /**
     * 构造事件参数，由子类实现。
     */
    protected abstract fun makeParameters(): Map<String, Any?>

    /**
     * 允许子类覆盖策略版本来源，默认回落至全局配置。
     */
    protected open fun resolvePoseidonConfigVersion(): VersionHelper? {
       return PoseidonConfig.version()
    }
}

/**
 * SDK build label in the form {libVersion}-{productFlavor}-{buildType}.
 */
internal fun poseidonSdkVersionLabel(): String {
    return "${BuildConfig.POSEIDON_LIB_VERSION}-${BuildConfig.POSEIDON_PRODUCT_NAME}-${BuildConfig.BUILD_TYPE}"
}
