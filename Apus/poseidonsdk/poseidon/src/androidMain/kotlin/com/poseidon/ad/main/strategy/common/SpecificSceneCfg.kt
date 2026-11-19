package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.strategy.SWITCH_OFF
import com.poseidon.ad.mediation.MediationDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpecificSceneCfg(
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_scene_names") val sceneNames: List<String>,
    @SerialName("pos_placements") val adUnits: List<SpecificScenePlacementCfg>,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}

@Serializable
internal data class SpecificScenePlacementCfg(
    @SerialName("pos_id") val id: String,
    @SerialName("pos_mediation") @MediationDef val network: Int,
    @SerialName("pos_switch") val switch: Int,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}
