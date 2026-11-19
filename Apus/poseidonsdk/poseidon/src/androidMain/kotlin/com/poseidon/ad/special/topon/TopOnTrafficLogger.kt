package com.poseidon.ad.special.topon

import com.poseidon.ad.common.toolkit.logInfo

/**
 * TopOn 流量分组日志工具，保持英文日志的一致性。
 */
internal object TopOnTrafficLogger {

    fun debugOverride(originalChannel: String?, originalCampaign: String?, channel: String?, campaign: String?) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "Debug override - Original: channel=$originalChannel, campaign=$originalCampaign"
        }
        logInfo(TOPON_TRAFFIC_TAG) {
            "Debug override - Effective: channel=$channel, campaign=$campaign"
        }
    }

    fun attributionChanged(channel: String?, campaign: String?, adUnitId: String) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "callOnAttributionChanged: attributionChannelName=$channel, attributionCampaign=$campaign, adUnitId=$adUnitId"
        }
    }

    fun beforeLoad(channel: String?, campaign: String?, adUnitId: String) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "callBeforeTopOnAdLoad: attributionChannelName=$channel, attributionCampaign=$campaign, adUnitId=$adUnitId"
        }
    }

    fun sendAttempt(originalChannel: String?, originalCampaign: String?, formattedChannel: String?, formattedCampaign: String?, adUnitId: String) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "trySendAttributionInfoToTopOn: originalChannel=$originalChannel, originalCampaign=$originalCampaign, formattedChannel=$formattedChannel, formattedCampaign=$formattedCampaign, adUnitId=$adUnitId"
        }
    }

    fun skipEmptyChannel() {
        logInfo(TOPON_TRAFFIC_TAG) { "Skipping TopOn custom map update: formattedChannelName is null or empty" }
    }

    fun sendSuccess(channel: String, campaign: String?, adUnitId: String, previousChannel: String?, previousCampaign: String?) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "sendAttributionInfoToTopOn: channel=$channel, campaign=$campaign, adUnitId=$adUnitId, previousChannel=$previousChannel, previousCampaign=$previousCampaign"
        }
        logInfo(TOPON_TRAFFIC_TAG) {
            "TopOn custom map updated successfully: channel=$channel, campaign=$campaign"
        }
    }

    fun skipNoChange(channel: String?, campaign: String?) {
        logInfo(TOPON_TRAFFIC_TAG) {
            "Skipping TopOn custom map update: no changes detected (channel=$channel, campaign=$campaign)"
        }
    }

    fun customMap(detail: String) {
        logInfo(TOPON_TRAFFIC_TAG) { detail }
    }
}
