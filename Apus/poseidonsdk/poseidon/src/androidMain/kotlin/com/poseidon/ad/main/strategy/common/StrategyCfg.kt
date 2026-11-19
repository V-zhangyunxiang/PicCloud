package com.poseidon.ad.main.strategy.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an ad strategy
 * @param id strategy ID
 * @param placements ad units included in the strategy
 * @param description strategy description
 */
@Serializable
internal data class StrategyCfg(
    @SerialName("pos_id") val id: Int,
    @SerialName("pos_placements") val placements: List<PlacementCfg>,
    @SerialName("pos_desc") val description: String,
)
