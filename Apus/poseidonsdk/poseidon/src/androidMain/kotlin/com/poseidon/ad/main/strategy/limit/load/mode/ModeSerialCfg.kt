package com.poseidon.ad.main.strategy.limit.load.mode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ModeSerialCfg(
    @SerialName("pos_ad_unit_ids") val adUnitIds: List<String>?
)
