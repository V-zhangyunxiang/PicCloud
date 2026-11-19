package com.poseidon.ad.special.topon

import com.poseidon.ad.common.storage.sp.Key
import com.adjust.sdk.Adjust
import com.poseidon.ad.statistics.biz.AdjustAttributionNetworkAnalyzer

object AdjustAttributionNetworkStore {
    private val store = AdjustAttributionStoreBase(
        preferenceKey = Key.AdjustAttributionNetworkSync,
        analyzer = { from, network ->
            AdjustAttributionNetworkAnalyzer(
                attributionNetworkFrom = from,
                attributionNetworkValue = network
            ).analyze()
        },
        valueExtractor = { attribution -> attribution?.network }
    )

    internal fun onAttributionChanged(network: String?) {
        store.handleAttributionChanged(origin = "onAttributionChanged", value = network)
    }

    fun getAttributionNetworkSync(): String? {
        store.refreshFromAdjust(origin = "getAttributionNetworkAsync")
        return store.readCachedValue()
    }

    internal fun readjustAttributionNetworkWhenStartup() {
        store.refreshFromAdjust(origin = "readjustAttributionNetworkWhenStartup")
    }
}

internal fun AdjustAttributionNetworkStore.getAttributionNetworkAsync(action: (network: String?) -> Unit) {
    Adjust.getAttribution { attribution -> action(attribution?.network) }
}
