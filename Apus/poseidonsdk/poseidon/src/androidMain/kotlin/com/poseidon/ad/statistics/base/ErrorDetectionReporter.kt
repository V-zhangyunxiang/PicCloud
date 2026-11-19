package com.poseidon.ad.statistics.base

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 通用错误侦测打点，记录异常信息，便于排查。
 * Generic error detection reporter that captures abnormal states for diagnostics.
 */
internal class ErrorDetectionReporter(
    private val name: String,
    private val extra: String,
) : Reporter(eventName = EventName.ERROR_DETECTION) {

    override fun makeParameters(): Map<String, Any?> = mapOf(
        ParamKey.GENERIC_NAME to name,
        ParamKey.GENERIC_EXTRA to extra,
    )

    override fun defaultEnable(): Boolean = false
}
