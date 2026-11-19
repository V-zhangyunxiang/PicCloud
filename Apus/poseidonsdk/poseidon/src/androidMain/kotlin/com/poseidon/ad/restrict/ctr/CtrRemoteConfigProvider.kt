package com.poseidon.ad.restrict.ctr

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor

/**
 * CTR 限制相关的远程配置读取器，统一封装 key 与默认值。
 */
internal object CtrRemoteConfigProvider {

    private val remoteConfig get() = PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig

    /**
     * 总开关：1 表示开启。
     */
    fun isCtrSwitchOn(): Boolean {
        return remoteConfig.getString("poseidon_ctr_enabled") == "1"
    }

    /**
     * 强制关闭广告的点击阈值，默认 2。
     */
    fun forceCloseThreshold(): Int {
        return remoteConfig.getString("poseidon_ctr_force_close_ad_on_click_count").toIntOrNull() ?: 2
    }

    /**
     * 当天禁播的点击阈值，默认 5。
     */
    fun dailyLimitThreshold(): Int {
        return remoteConfig.getString("poseidon_ctr_cannot_show_ad_today_on_click_count").toIntOrNull() ?: 5
    }

    /**
     * 是否允许在超限后强制关闭广告，默认为 true。
     */
    fun forceCloseEnabled(): Boolean {
        val value = remoteConfig.getString("poseidon_ctr_close_ad_enabled")
        return value.takeUnless { it.isEmpty() }?.toBoolean() ?: true
    }

    private fun rawActivityPartialNames(): String {
        return remoteConfig.getString("poseidon_ctr_activity_partial_name")
    }

    /**
     * 需要检查的 Activity 名称片段，默认包含 Google/Facebook/MBridge。
     */
    fun activityPartialNames(): List<String> {
        val names = rawActivityPartialNames()
        if (names.isEmpty()) {
            return listOf("com.google.android.gms.ads.AdActivity", "com.facebook.ads", "com.mbridge.msdk")
        }
        return if (names.contains("|")) {
            names.split("|").map { it.trim() }
        } else {
            listOf(names)
        }
    }

    /**
     * 返回原始配置字符串，供分析日志使用。
     */
    fun activityPartialNameRawForLog(): String {
        return rawActivityPartialNames()
    }
}
