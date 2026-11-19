package com.poseidon.ad.restrict.content

import com.poseidon.ad.main.adinfo.AD_TYPE_BANNER
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.AdTypeDef

/**
 * 页面广告统计信息，负责记录各种广告类型的计数。
 */
internal data class PageAdStat(
    var interstitialCount: Int = 0,
    var rewardedCount: Int = 0,
    var nativeCount: Int = 0,
    var appOpenCount: Int = 0,
    var bannerCount: Int = 0
) {

    /**
     * 当前页面所有广告数量之和。
     */
    val total: Int
        get() = interstitialCount + rewardedCount + nativeCount + appOpenCount + bannerCount

    /**
     * 根据广告类型记录一次展示。
     */
    fun increment(@AdTypeDef adType: Int, pageName: String): Boolean {
        return when (adType) {
            AD_TYPE_INTERSTITIAL -> {
                interstitialCount += 1
                true
            }

            AD_TYPE_REWARDED -> {
                rewardedCount += 1
                true
            }

            AD_TYPE_OPEN -> {
                appOpenCount += 1
                true
            }

            AD_TYPE_NATIVE -> incrementSingle(adType, pageName, { nativeCount }) { nativeCount = it }
            AD_TYPE_BANNER -> incrementSingle(adType, pageName, { bannerCount }) { bannerCount = it }
            else -> false
        }
    }

    /**
     * 将其他页面的统计结果复制到当前对象。
     */
    fun copyFrom(other: PageAdStat) {
        interstitialCount = other.interstitialCount
        rewardedCount = other.rewardedCount
        nativeCount = other.nativeCount
        appOpenCount = other.appOpenCount
        bannerCount = other.bannerCount
    }

    /**
     * 重置当前所有计数。
     */
    fun reset() {
        interstitialCount = 0
        rewardedCount = 0
        nativeCount = 0
        appOpenCount = 0
        bannerCount = 0
    }

    private fun incrementSingle(
        @AdTypeDef adType: Int,
        pageName: String,
        currentValue: () -> Int,
        setValue: (Int) -> Unit
    ): Boolean {
        val existing = currentValue()
        return if (existing == 0) {
            setValue(1)
            true
        } else {
            ContentBalanceLogger.singleCountAlreadyRecorded(pageName, adType, existing)
            true
        }
    }
}
