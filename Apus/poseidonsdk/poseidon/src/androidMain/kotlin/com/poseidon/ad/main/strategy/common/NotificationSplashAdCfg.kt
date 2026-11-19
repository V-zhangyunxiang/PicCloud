package com.poseidon.ad.main.strategy.common

import com.poseidon.ad.main.strategy.SWITCH_OFF
import com.poseidon.ad.mediation.MediationDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NotificationSplashAdCfg(
    @SerialName("pos_show_ad_scene_name") val showAdSceneName: String,
    @SerialName("pos_preload_ad_when_noti_show_scene_name") val preloadAdWhenNotificationDisplayedSceneName: String,
    @SerialName("pos_preload_ad_when_noti_click_scene_name") val preloadAdWhenNotificationClickedSceneName: String,
    @SerialName("pos_enable_client_bidding") val enableClientBidding: Boolean = true,
    @SerialName("pos_switch") val switch: Int,
    @SerialName("pos_placement_id") val placementId: String,
    @SerialName("pos_mediation") @MediationDef val mediation: Int,
    @SerialName("pos_fallback_strategy_id") val fallbackStrategyId: Int? = null,
    @SerialName("pos_allow_preload_when_fallback_ad_dismissed") val allowPreloadWhenFallbackAdDismissed: Boolean = true,
    @SerialName("pos_allow_preload_when_fallback_ad_failed_to_show") val allowPreloadWhenFallbackAdFailedToShow: Boolean = true,
) {
    fun isSwitchOn(): Boolean = switch != SWITCH_OFF
}
