package com.poseidon.ad.main.strategy.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 远端下发的 Adjust token 配置定义。
 */
@Serializable
data class AdjustEventTokensCfg(
    @SerialName("pos_adjust_token_ad_impression") val adImpressionToken: String? = null,
    @SerialName("pos_adjust_token_fullscreen_impression") val fullscreenAdImpressionToken: String? = null,
    @SerialName("pos_adjust_token_high_value_first_day") val highValueFirstDayToken: String? = null,
    @SerialName("pos_adjust_token_high_value_next_day") val highValueNextDayToken: String? = null,
)
