package com.poseidon.ad.statistics.base

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 配置版本埋点，记录来源与版本信息，辅助策略核对。
 * Reporter that logs config version and source for debugging configuration changes.
 */
@Suppress("SpellCheckingInspection")
internal class AdConfigVersionReporter(
    private val from: String,
    private val version: String,
) : Reporter(eventName = EventName.CONFIG_VERSION) {

    override fun makeParameters(): Map<String, Any?> = mapOf(
        ParamKey.VERSION_FROM to from,
        ParamKey.VERSION_VALUE to version,
    )

    override fun defaultEnable(): Boolean = false
}
