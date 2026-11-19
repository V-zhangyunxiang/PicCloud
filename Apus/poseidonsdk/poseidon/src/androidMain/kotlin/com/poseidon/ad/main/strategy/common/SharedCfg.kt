package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.preload.share.CacheKey
import com.poseidon.ad.main.strategy.SWITCH_OFF
import com.poseidon.ad.mediation.MediationDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SharedCfg(
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_cache_key") val cacheKeyId: Int = CacheKey.AdType.id,
    @SerialName("pos_placements") val adUnits: List<SharedAdUnitCfg>,
) {

    val resolvedCacheKey: CacheKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CacheKey.entries.firstOrNull { it.id == cacheKeyId } ?: CacheKey.AdType
    }

    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}

@Serializable
internal data class SharedAdUnitCfg(
    @SerialName("pos_id") val id: String,
    @SerialName("pos_mediation") @MediationDef val mediation: Int,
    @SerialName("pos_ad_type") @AdTypeDef val adType: Int,
    @SerialName("pos_switch") val switch: Int,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}
