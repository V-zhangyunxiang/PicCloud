package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.strategy.SWITCH_OFF
import com.poseidon.ad.main.strategy.SWITCH_ON
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an ad scene
 * @param name scene name
 * @param strategyId the ad strategy id used by this scene, [StrategyCfg.id]
 * @param adType the ad type displayed in this scene
 * @param switch scene switch, [com.poseidon.ad.core.strategy.SWITCH_OFF] to disable, other values to enable
 * @param enableClientBidding whether to enable client-side bidding, default is true
 * @param allowNoCacheRequest whether to allow requesting ad without cache
 * @param allowNoCachePreload whether to allow preloading ad without cache
 * @param allowPreloadWhenFullscreenAdDismissed whether to allow preloading ad when full screen ad is dismissed
 * @param allowPreloadWhenNativeAdImpressed whether to allow preloading ad when native ad is impressed
 * @param allowPreloadWhenAdFailedToShow whether to allow preloading ad when ad fails to show
 * @param maxDisplays maximum displays allowed per day for this scene
 * @param maxClicks maximum clicks allowed per day for this scene
 * @param minInterval minimum interval between displays in seconds for this scene
 * @param firstShow whether to allow ad display for new user first cold start
 * @param description scene description
 */
@Serializable
data class SceneCfg(
    @SerialName("pos_name") val name: String,
    @SerialName("pos_strategy_id") val strategyId: Int,
    @SerialName("pos_ad_type") @AdTypeDef val adType: Int,
    @SerialName("pos_switch") val switch: Int = SWITCH_ON,
    @SerialName("pos_enable_client_bidding") val enableClientBidding: Boolean = true,
    @SerialName("pos_allow_no_cache_request") val allowNoCacheRequest: Boolean,
    @SerialName("pos_allow_no_cache_preload") val allowNoCachePreload: Boolean = true,
    @SerialName("pos_allow_preload_when_fullscreen_ad_dismissed") val allowPreloadWhenFullscreenAdDismissed: Boolean = true,
    @SerialName("pos_allow_preload_when_native_ad_impressed") val allowPreloadWhenNativeAdImpressed: Boolean = true,
    @SerialName("pos_allow_preload_when_ad_failed_to_show") val allowPreloadWhenAdFailedToShow: Boolean = true,
    @SerialName("pos_max_displays") val maxDisplays: Int = Int.MAX_VALUE,
    @SerialName("pos_max_clicks") val maxClicks: Int = Int.MAX_VALUE,
    @SerialName("pos_min_interval") val minInterval: Long = 0L,
    @SerialName("pos_first_show") val firstShow: Boolean = true,
    @SerialName("pos_page") val page: String? = null,
    @SerialName("pos_desc") val description: String
) {
    fun isSwitchOn(): Boolean {
        return switch != SWITCH_OFF
    }
}

