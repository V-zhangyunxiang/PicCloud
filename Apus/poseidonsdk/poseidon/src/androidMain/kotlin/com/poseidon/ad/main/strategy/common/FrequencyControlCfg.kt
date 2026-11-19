package com.poseidon.ad.main.strategy.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 全局频控配置
 * @param frequencySwitch 频控总开关
 * @param maxDisplays 最大展示次数
 * @param maxClicks 最大点击次数
 * @param minInterval 最小展示间隔(秒)
 */
@Serializable
data class GlobalFrequencyControlCfg(
    @SerialName("pos_frequency_switch") val frequencySwitch: Boolean = false,
    @SerialName("pos_max_displays") val maxDisplays: Int = Int.MAX_VALUE,
    @SerialName("pos_max_clicks") val maxClicks: Int = Int.MAX_VALUE,
    @SerialName("pos_min_interval") val minInterval: Long = 0L,
    @SerialName("pos_ad_restriction_by_content") val adRestrictionByContent: Boolean = false
)

/**
 * 单个广告类型频控配置
 * @param maxDisplays 最大展示次数
 * @param maxClicks 最大点击次数
 * @param minInterval 最小展示间隔(秒)
 * @param skipFrequencyChecks 是否跳过频控检查（绕过全局和广告类型频控，但场景频控仍生效）
 */
@Serializable
data class AdTypeFrequencyControlCfg(
    @SerialName("pos_max_displays") val maxDisplays: Int = Int.MAX_VALUE,
    @SerialName("pos_max_clicks") val maxClicks: Int = Int.MAX_VALUE,
    @SerialName("pos_min_interval") val minInterval: Long = 0L,
    @SerialName("pos_skip_frequency_checks") val skipFrequencyChecks: Boolean = false
)

/**
 * 所有广告类型频控配置
 * @param interstitial 插屏广告频控
 * @param appOpen 开屏广告频控
 * @param native 原生广告频控
 * @param rewarded 激励广告频控
 * @param banner Banner广告频控
 */
@Serializable
data class AdTypeFrequencyControlsCfg(
    @SerialName("pos_interstitial") val interstitial: AdTypeFrequencyControlCfg? = null,
    @SerialName("pos_app_open") val appOpen: AdTypeFrequencyControlCfg? = null,
    @SerialName("pos_native") val native: AdTypeFrequencyControlCfg? = null,
    @SerialName("pos_rewarded") val rewarded: AdTypeFrequencyControlCfg? = null,
    @SerialName("pos_banner") val banner: AdTypeFrequencyControlCfg? = null
)
