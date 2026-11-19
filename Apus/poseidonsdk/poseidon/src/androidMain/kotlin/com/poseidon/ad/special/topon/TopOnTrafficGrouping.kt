package com.poseidon.ad.special.topon

import com.adjust.sdk.AdjustAttribution
import com.thinkup.core.api.TUSDK
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.statistics.biz.AdjustAttributionAnalyzer
import com.poseidon.ad.common.toolkit.logInfo
import org.json.JSONObject

/**
 * TopOn 流量分组入口对象，暴露归因变更及加载前准备接口。
 */
object TopOnTrafficGrouping {
    private val coordinator = TopOnTrafficCoordinator()

    @Volatile
    internal var cachedChannel: String? = null
        private set

    @Volatile
    internal var cachedCampaign: String? = null
        private set

    /**
     * 启动时预热归因缓存，避免首次加载时阻塞读取。
     */
    internal fun appStartup() {
        overwriteCachedAttribution(
            channel = AdjustAttributionNetworkStore.getAttributionNetworkSync(),
            campaign = AdjustCampaignStore.getAttributionCampaignSync(),
        )
    }

    internal fun onAttributionChanged(
        attributionChannelName: String?,
        attributionCampaign: String? = null
    ) {
        coordinator.handleAttributionChanged(
            originalChannel = attributionChannelName,
            originalCampaign = attributionCampaign
        )
    }

    /**
     * 在 TopOn 请求前调用，确保最新归因信息同步。
     */
    internal fun callBeforeTopOnAdLoad(adUnitId: String) {
        coordinator.prepareBeforeAdLoad(adUnitId)
    }

    /**
     * 完全替换缓存值（用于启动和 debug 场景）。
     */
    internal fun overwriteCachedAttribution(channel: String?, campaign: String?) {
        cachedChannel = channel
        cachedCampaign = campaign
    }

    /**
     * 合并归因信息：仅保留非空新值，保持旧的非空值不被覆盖。
     */
    internal fun mergeCachedAttribution(channel: String?, campaign: String?) {
        channel?.takeUnless(String::isEmpty)?.let { cachedChannel = it }
        campaign?.takeUnless(String::isEmpty)?.let { cachedCampaign = it }
    }

    /**
     * Call when attribution changed.
     *
     * @param adjustAttribution The name of the current attribution of the device [AdjustAttribution]
     *
     * @see [com.adjust.sdk.AdjustConfig.setOnAttributionChangedListener]
     */
    fun callOnAttributionChanged(adjustAttribution: AdjustAttribution?) {
        val map = adjustAttribution?.toMap() ?: emptyMap()
        val campaign = adjustAttribution?.campaign
        val network = adjustAttribution?.network

        logInfo(TOPON_TRAFFIC_TAG) {
            "callOnAttributionChanged: network=$network, campaign=$campaign, fullAttribution=$map"
        }

        runCatching { JSONObject(map).toString() }
            .onSuccess { json ->
                AdjustAttributionAnalyzer(
                    attributionFrom = "callOnAttributionChanged",
                    attributionValue = json
                ).analyze()
            }
            .onFailure { t ->
                ErrorDetectionReporter(
                    name = "AdjustAttributionParseError",
                    extra = t.toString()
                ).analyze()
            }

        onAttributionChanged(
            attributionChannelName = network,
            attributionCampaign = campaign
        )

        logInfo(TOPON_TRAFFIC_TAG) {
            "Attribution change processed: network=$network, campaign=$campaign"
        }
    }
}

internal fun TopOnTrafficGrouping.sendAttributionChannelToTopOn(
    attributionChannelName: String,
    attributionCampaign: String?
) {
    val customMap = mutableMapOf<String, Any>("channel" to attributionChannelName)
    if (!attributionCampaign.isNullOrEmpty()) {
        customMap["campaign"] = attributionCampaign
    }

    TopOnTrafficLogger.customMap("Sending custom map to TopOn: $customMap")

    // https://help.toponad.com/cn/docs/AB0pfG
    // effective for all placements
    TUSDK.initCustomMap(customMap)

    TopOnTrafficLogger.customMap(
        "TopOn custom map initialized successfully with channel='$attributionChannelName' and campaign='$attributionCampaign'"
    )
}
