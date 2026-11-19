package com.poseidon.ad.special.topon

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.storage.file.sdcard.TestPropertiesMgt

/**
 * TopOn 归因数据源，负责处理 Debug 覆盖与默认值。
 */
internal object TopOnAttributionSource {

    /**
     * 针对 Adjust 回调场景，根据 Debug 配置生成有效归因信息。
     */
    fun resolveFromAdjust(originalChannel: String?, originalCampaign: String?): AttributionSourceResult {
        val channelOverride = debugOverrideChannel()
        val campaignOverride = debugOverrideCampaign()
        val effectiveChannel = channelOverride ?: originalChannel
        val effectiveCampaign = campaignOverride ?: originalCampaign
        val overridden = PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug &&
            (effectiveChannel != originalChannel || effectiveCampaign != originalCampaign)
        return AttributionSourceResult(
            payload = AttributionPayload(channel = effectiveChannel, campaign = effectiveCampaign),
            overridden = overridden,
            originalChannel = originalChannel,
            originalCampaign = originalCampaign
        )
    }

    /**
     * 在 TopOn 请求前获取可用的归因数据。
     */
    fun resolveForAdLoad(): AttributionPayload {
        val channel = debugOverrideChannel() ?: AdjustAttributionNetworkStore.getAttributionNetworkSync()
        val campaign = debugOverrideCampaign() ?: AdjustCampaignStore.getAttributionCampaignSync()
        return AttributionPayload(channel = channel, campaign = campaign)
    }

    private fun debugOverrideChannel(): String? {
        if (!PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug) {
            return null
        }
        return TestPropertiesMgt.getTestChannel()?.takeIf { it.isNotEmpty() }
    }

    private fun debugOverrideCampaign(): String? {
        if (!PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug) {
            return null
        }
        return TestPropertiesMgt.getTestCampaign()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Debug 覆盖结果，包含有效归因与原始数据。
 */
internal data class AttributionSourceResult(
    val payload: AttributionPayload,
    val overridden: Boolean,
    val originalChannel: String?,
    val originalCampaign: String?
)
