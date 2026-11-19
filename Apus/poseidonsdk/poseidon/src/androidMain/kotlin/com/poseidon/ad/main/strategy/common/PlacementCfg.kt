package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.mediation.MediationDef

/**
 * Represents an ad unit
 * @param id ad unit id
 * @param mediation ad network type
 * @param adType ad type
 * @param description description information
 * @see [com.poseidon.ad.mediation.MediationDef]
 */
@kotlinx.serialization.Serializable
internal data class PlacementCfg(
    @kotlinx.serialization.SerialName("pos_id") val id: String,
    @kotlinx.serialization.SerialName("pos_mediation") @MediationDef val mediation: Int,
    @kotlinx.serialization.SerialName("pos_ad_type") @AdTypeDef val adType: Int,
    @kotlinx.serialization.SerialName("pos_desc") val description: String? = null
)