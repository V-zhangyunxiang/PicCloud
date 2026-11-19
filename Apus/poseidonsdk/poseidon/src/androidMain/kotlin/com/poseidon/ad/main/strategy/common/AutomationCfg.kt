package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.strategy.SWITCH_OFF
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AutomationCfg(
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_ceiling") val ceil: AutomationItemCfg?,
    @SerialName("pos_floor") val floor: AutomationItemCfg?,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}

@Serializable
internal data class AutomationItemCfg(
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_topOn_placements") val topOnAdUnits: List<PlacementCfg>,
    @SerialName("pos_admob_placements") val admobAdUnits: List<PlacementCfg>,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}
