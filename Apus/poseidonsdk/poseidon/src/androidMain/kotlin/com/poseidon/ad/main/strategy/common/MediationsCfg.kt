package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.mediation.MEDIATION_ADMOB
import com.poseidon.ad.mediation.MEDIATION_MAX
import com.poseidon.ad.mediation.MEDIATION_TOPON
import com.poseidon.ad.mediation.MediationDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediationsCfg(
    @SerialName("pos_topOn") val topOn: TopOnMediation? = null,
    @SerialName("pos_admob") val admob: AdmobMediation? = null,
    @SerialName("pos_max") val max: MaxMediation? = null,
)

/**
 * @param enablePresetStrategy Whether to enable TopOn's preset strategy, default is enabled [https://help.toponad.com/cn/docs/wERYJv?search=1]
 */
@Serializable
data class TopOnMediation(
    @SerialName("pos_id") val id: Int,
    @SerialName("pos_app_id") val appId: String,
    @SerialName("pos_app_key") val appKey: String,
    @SerialName("pos_enable_preset_strategy") val enablePresetStrategy: Boolean = true,
) : Mediation {
    init {
        assetMediationId(expect = MEDIATION_TOPON, actual = id)
    }
}

@Serializable
data class AdmobMediation(
    @SerialName("pos_id") val id: Int,
) : Mediation {
    init {
        assetMediationId(expect = MEDIATION_ADMOB, actual = id)
    }
}

@Serializable
data class MaxMediation(
    @SerialName("pos_id") val id: Int,
    @SerialName("pos_applovin_sdk_key") val applovinSdkKey: String,
) : Mediation {
    init {
        assetMediationId(expect = MEDIATION_MAX, actual = id)
    }
}

interface Mediation

private fun Mediation.assetMediationId(@MediationDef expect: Int, actual: Int) {
    require(actual == expect) {
        "MediationId was incorrect for ${this::class.simpleName}, expect: $expect, actual: $actual"
    }
}
