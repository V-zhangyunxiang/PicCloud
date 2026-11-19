package com.poseidon.ad.restrict.show

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Facebook
import com.poseidon.ad.main.price.AdSourceIdDef
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 广告曝光限制器，负责统计并判断 Admob/Facebook 是否到达上限。
 */
object AdShowLimit {
    private val sourceFilter = ImpressionSourceFilter()
    private val counter = ImpressionCounter()

    /**
     * 在广告曝光时调用，记录指定平台的计数。
     */
    fun callOnAdImpressed(@AdSourceIdDef adSourceId: Int) {
        if (!sourceFilter.isLimitedSource(adSourceId)) return
        counter.ensureTodayInitialized()
        counter.increment()
    }

    /**
     * 判断是否还能加载广告。
     */
    fun canLoadAd(): Boolean = canShowOrLoadAd()

    /**
     * 判断是否还能展示广告。
     */
    fun canShowAd(): Boolean = canShowOrLoadAd()

    /**
     * 统一判断逻辑，复用加载/展示。
     */
    private fun canShowOrLoadAd(): Boolean {
        val maxCount = RemoteConfigLimitFetcher.maxImpressionCount()
        if (maxCount < 0) return true
        counter.ensureTodayInitialized()
        val count = counter.currentCount().coerceAtLeast(0)
        return count < maxCount
    }
}

/**
 * 管理允许限流的广告来源。
 */
private class ImpressionSourceFilter {
    fun isLimitedSource(@AdSourceIdDef adSourceId: Int): Boolean {
        return adSourceId == AD_SOURCE_ID_Facebook || adSourceId == AD_SOURCE_ID_Admob
    }
}

/**
 * 负责维护 SharedPreferences 中的计数与日期。
 */
private class ImpressionCounter {
    private val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference

    fun ensureTodayInitialized() {
        val plan = computeDailyCounterPlan(
            savedDate = sharedPreference.getString(Key.AdmobOrFacebookAdImpressionLastDate, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = sharedPreference.getInt(Key.AdmobOrFacebookAdImpressionCount, 0),
            applyIncrement = false,
        )
        if (plan.needsPersistDate) {
            sharedPreference.writeBatch(listOf(
                SpBatchEntry.IntValue(key = Key.AdmobOrFacebookAdImpressionCount, value = 0),
                SpBatchEntry.StringValue(key = Key.AdmobOrFacebookAdImpressionLastDate, value = plan.dateValue),
            ))
        }
    }

    fun increment() {
        val count = currentCount() + 1
        sharedPreference.putInt(Key.AdmobOrFacebookAdImpressionCount, count)
    }

    fun currentCount(): Int = sharedPreference.getInt(Key.AdmobOrFacebookAdImpressionCount, 0)
}

/**
 * 统一从远程配置获取阈值。
 */
private object RemoteConfigLimitFetcher {
    private const val KEY_MAX_COUNT = "admob_or_facebook_ad_max_impression_count"

    fun maxImpressionCount(): Long {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig.getString(KEY_MAX_COUNT)
            .toLongOrNull() ?: -1
    }
}

/**
 * 返回当前日期字符串，格式 yyyy-MM-dd。
 */
internal fun currentDateFormatWithYearMonthDay(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return dateFormat.format(Date())
}
