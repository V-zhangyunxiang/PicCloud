package com.poseidon.ad.main.strategy.limit.load.mode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ModeDelayCfg(
    @SerialName("pos_time_in_millis") val timeInMillis: Long,
    @SerialName("pos_ad_unit_ids") val adUnitIds: Map<String, Long>?
)
