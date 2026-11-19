package com.poseidon.ad.statistics

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import com.poseidon.ad.common.toolkit.logInfo

/**
 * Firebase 打点接口，定义日志上报的最小能力。
 */
@Suppress("SpellCheckingInspection")
interface FirebaseAnalytics {

    /**
     * 上报应用事件。
     *
     * @param name 事件名称
     * @param parameters 事件参数键值对
     */
    fun logEvent(name: String, parameters: Map<String, Any?>)

    fun setUserProperty(name: String, value: String?)

    /**
     * Analytics 工厂，负责按后端类型提供实例。
     */
    abstract class Factory {

        private val cache: MutableMap<SupportAnalyticsBackend, FirebaseAnalytics> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { mutableMapOf() }
        private val lock = reentrantLock()

        /**
         * 获取指定后端的实现，带缓存且线程安全。
         */
        fun obtain(by: SupportAnalyticsBackend): FirebaseAnalytics {
            var impl = cache[by]
            if (impl == null) {
                lock.withLock {
                    impl = cache[by]
                    if (impl == null) {
                        cache[by] = create(by).also { impl = it }
                    }
                }
            }
            return impl!!
        }

        protected abstract fun create(by: SupportAnalyticsBackend): FirebaseAnalytics
    }
}

/**
 * 可扩展的 Analytics 后端列表。
 */
enum class SupportAnalyticsBackend {
    Firebase
}

/**
 * 带日志输出的装饰器，实现上报前打印参数。
 */
@Suppress("SpellCheckingInspection")
internal class ReporterWrapper(private val delegate: FirebaseAnalytics) : FirebaseAnalytics {

    override fun logEvent(name: String, parameters: Map<String, Any?>) {
        // Attach SDK build label to direct Firebase events that bypass Reporter.analyze().
        val enriched = if (parameters.containsKey(BizAdStatisticsConstants.ParamKey.POSEIDON_SDK_VERSION)) {
            parameters
        } else {
            parameters + (BizAdStatisticsConstants.ParamKey.POSEIDON_SDK_VERSION to poseidonSdkVersionLabel())
        }
        logInfo(TAG) { "logEvent: name=$name, parameters=$enriched" }
        delegate.logEvent(name, enriched)
    }

    override fun setUserProperty(name: String, value: String?) {
        logInfo(TAG) { "setUserProperty: name=$name, value=$value" }
        delegate.setUserProperty(name, value)
    }

    private companion object {
        private const val TAG = "ReporterWrapper"
    }
}
