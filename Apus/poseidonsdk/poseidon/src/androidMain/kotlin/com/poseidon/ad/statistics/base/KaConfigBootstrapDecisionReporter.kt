package com.poseidon.ad.statistics.base

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

internal class KaConfigBootstrapDecisionReporter(
    private val defaultVersion: String?,
    private val kaVersion: String?,
    private val kaKey: String?,
    private val winner: String,
    private val reason: String,
    private val kaSource: String?,
) : Reporter(eventName = EventName.KA_CONFIG_BOOTSTRAP_DECISION) {

    override fun makeParameters(): Map<String, Any?> = mapOf(
        ParamKey.DEFAULT_VERSION to defaultVersion,
        ParamKey.KA_VERSION to kaVersion,
        ParamKey.KA_KEY to kaKey,
        ParamKey.WINNER to winner,
        ParamKey.REASON to reason,
        ParamKey.REASON to reason,
        ParamKey.KA_SOURCE to kaSource,
    )

    override fun resolvePoseidonConfigVersion(): VersionHelper? {
        return null
    }
}
