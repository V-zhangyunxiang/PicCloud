package com.poseidon.ad.special.topon

import com.poseidon.ad.statistics.biz.TopOnTrafficGroupingReporter
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.main.strategy.common.PoseidonConfig

/**
 * TopOn 流量协调器，负责整合归因数据并通知 TopOn。
 * 采用预检机制避免不必要的 runOnMainThread 调度。
 */
internal class TopOnTrafficCoordinator {
    @Volatile
    private var attributionChannelNameSent: String? = null
    @Volatile
    private var attributionCampaignSent: String? = null

    /**
     * 处理 Adjust 回调的归因变化。
     */
    fun handleAttributionChanged(originalChannel: String?, originalCampaign: String?) {
        val sourceResult = TopOnAttributionSource.resolveFromAdjust(originalChannel, originalCampaign)
        val payload = sourceResult.payload
        if (sourceResult.overridden) {
            TopOnTrafficLogger.debugOverride(
                originalChannel = sourceResult.originalChannel,
                originalCampaign = sourceResult.originalCampaign,
                channel = payload.channel,
                campaign = payload.campaign
            )
        } else {
            TopOnTrafficLogger.attributionChanged(
                channel = payload.channel,
                campaign = payload.campaign,
                adUnitId = "onAttributionChanged"
            )
        }

        AdjustAttributionNetworkStore.onAttributionChanged(network = originalChannel)
        if (originalCampaign != null) {
            AdjustCampaignStore.onAttributionChanged(campaign = originalCampaign)
        }
        AttributionChannelMtg.update(attributionChannelName = originalChannel)
        FirebaseUserPropertySyncer.sync(payload)

        TopOnTrafficGrouping.mergeCachedAttribution(
            channel = payload.channel,
            campaign = payload.campaign,
        )

        PoseidonConfig.reEvaluateKaWhenAttributionChanged()

        tryDispatchToTopOn(
            rawChannel = payload.channel,
            rawCampaign = payload.campaign,
            adUnitId = "onAttributionChanged",
        )
    }

    /**
     * TopOn 请求前调用。使用内存缓存避免每次从 SP 读取。
     */
    fun prepareBeforeAdLoad(adUnitId: String) {
        refreshCachedAttributionForLoad()
        val channel = TopOnTrafficGrouping.cachedChannel
        val campaign = TopOnTrafficGrouping.cachedCampaign

        TopOnTrafficLogger.beforeLoad(
            channel = channel,
            campaign = campaign,
            adUnitId = adUnitId
        )

        tryDispatchToTopOn(
            rawChannel = channel,
            rawCampaign = campaign,
            adUnitId = adUnitId,
        )
    }

    /**
     * 仅在首次或 debug 模式下重新从 store 读取，否则使用内存缓存。
     */
    private fun refreshCachedAttributionForLoad() {
        val isDebug = PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug
        if (isDebug || (TopOnTrafficGrouping.cachedChannel == null && TopOnTrafficGrouping.cachedCampaign == null)) {
            val attribution = TopOnAttributionSource.resolveForAdLoad()
            TopOnTrafficGrouping.overwriteCachedAttribution(
                channel = attribution.channel,
                campaign = attribution.campaign,
            )
        }
    }

    /**
     * 预检 → 跳过无变化的 dispatch → 仅在需要时 runOnMainThread。
     */
    private fun tryDispatchToTopOn(rawChannel: String?, rawCampaign: String?, adUnitId: String) {
        val preCheck = evaluateTopOnDispatchNeed(
            rawChannel = rawChannel,
            rawCampaign = rawCampaign,
            sentChannel = attributionChannelNameSent,
            sentCampaign = attributionCampaignSent,
        )

        TopOnTrafficLogger.sendAttempt(
            originalChannel = rawChannel,
            originalCampaign = rawCampaign,
            formattedChannel = preCheck.normalizedChannel,
            formattedCampaign = preCheck.normalizedCampaign,
            adUnitId = adUnitId
        )

        if (!preCheck.requiresDispatch) {
            if (preCheck.normalizedChannel.isNullOrEmpty()) {
                TopOnTrafficLogger.skipEmptyChannel()
            } else {
                TopOnTrafficLogger.skipNoChange(channel = preCheck.normalizedChannel, campaign = preCheck.normalizedCampaign)
            }
            return
        }

        runOnMainThread {
            val confirmed = evaluateTopOnDispatchNeed(
                rawChannel = rawChannel,
                rawCampaign = rawCampaign,
                sentChannel = attributionChannelNameSent,
                sentCampaign = attributionCampaignSent,
            )
            val confirmedChannel = confirmed.normalizedChannel
            if (!confirmed.requiresDispatch || confirmedChannel.isNullOrEmpty()) {
                if (confirmedChannel.isNullOrEmpty()) {
                    TopOnTrafficLogger.skipEmptyChannel()
                } else {
                    TopOnTrafficLogger.skipNoChange(channel = confirmedChannel, campaign = confirmed.normalizedCampaign)
                }
                return@runOnMainThread
            }

            val previousChannel = attributionChannelNameSent
            val previousCampaign = attributionCampaignSent

            TopOnTrafficGroupingReporter(
                adUnitId = adUnitId,
                attributionChannelName = confirmedChannel
            ).analyze()

            TopOnTrafficGrouping.sendAttributionChannelToTopOn(
                attributionChannelName = confirmedChannel,
                attributionCampaign = confirmed.normalizedCampaign
            )

            attributionChannelNameSent = confirmedChannel
            attributionCampaignSent = confirmed.normalizedCampaign

            TopOnTrafficLogger.sendSuccess(
                channel = confirmedChannel,
                campaign = confirmed.normalizedCampaign,
                adUnitId = adUnitId,
                previousChannel = previousChannel,
                previousCampaign = previousCampaign
            )
        }
    }
}
