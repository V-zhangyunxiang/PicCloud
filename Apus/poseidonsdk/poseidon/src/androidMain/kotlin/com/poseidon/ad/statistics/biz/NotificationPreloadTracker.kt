package com.poseidon.ad.statistics.biz

import androidx.annotation.IntDef
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 通知闪屏广告预加载流程打点。
 */
internal class NotificationSplashAdPreloadReporter(
    private val sceneName: String,
    private val sessionId: String,
    @TypeDef private val type: Int,
    private val extra: String? = null,
    private val passed: Boolean? = null,
) : Reporter(eventName = EventName.NOTIFICATION_SPLASH) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.NOTIFY_SCENE_NAME to sceneName,
            ParamKey.NOTIFY_SESSION_ID to sessionId,
            ParamKey.NOTIFY_TYPE to type,
            ParamKey.GENERIC_EXTRA to extra,
            ParamKey.NOTIFY_PASSED to passed,
        )
    }

    override fun defaultEnable(): Boolean = false

    companion object {
        const val TYPE_TRIGGER = 1
        const val TYPE_CACHE_CHECKING = 2
        const val TYPE_REQUEST_INTERVAL_CHECKING = 3
        const val TYPE_CONSECUTIVE_LOAD_FAILURES_CHECKING = 4
        const val TYPE_AD_FILL_UNUSED_CHECKING = 5
        const val TYPE_AD_IMPRESSION_LIMIT_CHECKING = 6
        const val TYPE_CONFIGURATION_CHECKING = 7
        const val TYPE_LOAD = 8
        const val TYPE_NETWORK_CHECKING = 9

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            TYPE_TRIGGER,
            TYPE_CACHE_CHECKING,
            TYPE_REQUEST_INTERVAL_CHECKING,
            TYPE_CONSECUTIVE_LOAD_FAILURES_CHECKING,
            TYPE_AD_FILL_UNUSED_CHECKING,
            TYPE_AD_IMPRESSION_LIMIT_CHECKING,
            TYPE_CONFIGURATION_CHECKING,
            TYPE_NETWORK_CHECKING,
            TYPE_LOAD
        )
        annotation class TypeDef
    }
}
