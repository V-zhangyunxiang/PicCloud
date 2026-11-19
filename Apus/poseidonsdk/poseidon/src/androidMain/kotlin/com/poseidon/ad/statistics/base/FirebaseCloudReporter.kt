package com.poseidon.ad.statistics.base

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 远程配置下发成功的打点，方便观察策略版本。
 * Reporter for Firebase Remote Config delivery, helping trace strategy versions.
 */
internal class FirebaseCloudReporter(
    private val versionName: String,
) : Reporter(eventName = EventName.REMOTE_CONFIG) {

    override fun makeParameters(): Map<String, Any?> = mapOf(
        ParamKey.GENERIC_NAME to ParamKey.REMOTE_CONFIG_EVENT_NAME,
        ParamKey.VERSION_NAME to versionName,
    )
}
