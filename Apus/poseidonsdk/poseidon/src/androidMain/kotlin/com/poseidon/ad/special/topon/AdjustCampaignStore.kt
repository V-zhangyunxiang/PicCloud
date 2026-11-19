package com.poseidon.ad.special.topon

import com.poseidon.ad.common.storage.sp.Key
import com.adjust.sdk.Adjust
import com.poseidon.ad.statistics.biz.AdjustAttributionCampaignAnalyzer

internal object AdjustCampaignStore {

    private val store = AdjustAttributionStoreBase(
        preferenceKey = Key.AdjustAttributionCampaignSync,
        analyzer = { from, campaign ->
            AdjustAttributionCampaignAnalyzer(
                attributionCampaignFrom = from,
                attributionCampaignValue = campaign
            ).analyze()
        },
        valueExtractor = { attribution -> attribution?.campaign }
    )

    internal fun onAttributionChanged(campaign: String?) {
        store.handleAttributionChanged(origin = "onAttributionChanged", value = campaign)
    }

    fun getAttributionCampaignSync(): String? {
        store.refreshFromAdjust(origin = "getAttributionCampaignAsync")
        return store.readCachedValue()
    }

    internal fun readjustAttributionCampaignWhenStartup() {
        store.refreshFromAdjust(origin = "readjustAttributionCampaignWhenStartup")
    }
}

internal fun AdjustCampaignStore.getAttributionCampaignAsync(action: (campaign: String?) -> Unit) {
    Adjust.getAttribution { attribution -> action(attribution?.campaign) }
}
