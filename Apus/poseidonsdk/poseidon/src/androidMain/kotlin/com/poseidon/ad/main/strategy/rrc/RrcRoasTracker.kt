package com.poseidon.ad.main.strategy.rrc

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.storage.db.AD_REVENUE_REPOSITORY_IMPL
import com.poseidon.ad.common.storage.db.AdRevenueRecord
import com.poseidon.ad.common.storage.sp.SpHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.statistics.SupportAnalyticsBackend
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

private const val TAG = "RrcRoasTracker"

/**
 * 根据云控配置执行 RRC ROAS 上报。
 */
internal suspend fun trackRrcRoas(record: AdRevenueRecord) {
    val config = RrcRoasCfg.get()
    if (config == null) {
        logInfo(TAG) { "config is null, skip" }
        return
    }
    if (!config.enabled) {
        logInfo(TAG) { "config disabled, skip" }
        return
    }

    val sharedPreference = poseidonSetupProcessor().sharedPreference
    trackArpuLimits(config, sharedPreference)
    trackEcpmThresholds(config, record, sharedPreference)
}

private suspend fun trackArpuLimits(
    config: RrcRoasCfg,
    sharedPreference: SpHelper
) {
    val limits = config.arpuLimits.orEmpty()
    if (limits.isEmpty()) {
        return
    }

    val firstOpenTime = poseidonSetupProcessor().awaitNewUserFirstOpenTime()
    if (firstOpenTime == null || firstOpenTime <= 0L) {
        logInfo(TAG) { "First-open timeline unavailable, skip ARPU tracking" }
        return
    }
    limits
        .filterNot { limit -> sharedPreference.getBoolean(limit.toKey(), false) }
        .forEach { limit ->
            val cycle = limit.timeCycleHours
            val arpu = limit.arpuValue
            if (cycle == null || arpu == null) {
                logInfo(TAG) { "invalid arpu limit: $limit" }
                return@forEach
            }
            val periodEnd = Instant.fromEpochMilliseconds(firstOpenTime)
                .plus(cycle, DateTimeUnit.HOUR, TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val totalRevenue = AD_REVENUE_REPOSITORY_IMPL.sumRevenue(
                fromTime = firstOpenTime,
                toTime = periodEnd
            )
            if (totalRevenue >= arpu) {
                logInfo(TAG) { "arpu threshold reached: total=$totalRevenue limit=$arpu" }
                reportRrcRoas(limit.adjustToken.orEmpty(), limit.firebaseEvent.orEmpty())
                sharedPreference.putBoolean(limit.toKey(), true)
            } else {
                logInfo(TAG) { "arpu threshold not reached: total=$totalRevenue limit=$arpu" }
            }
        }
}

private fun trackEcpmThresholds(
    config: RrcRoasCfg,
    record: AdRevenueRecord,
    sharedPreference: SpHelper
) {
    val merged = config.mergedEcpmList
    if (merged.isEmpty()) {
        return
    }

    merged
        .filterNot { entry -> sharedPreference.getBoolean(entry.toKey(), false) }
        .forEach { entry ->
            val ecpmLimit = entry.ecpmValue
            if (ecpmLimit == null) {
                logInfo(TAG) { "invalid ecpm entry: $entry" }
                return@forEach
            }
            if (record.eCPM >= ecpmLimit) {
                logInfo(TAG) { "ecpm threshold reached: current=${record.eCPM} limit=$ecpmLimit" }
                reportRrcRoas(entry.adjustToken.orEmpty(), entry.firebaseEvent.orEmpty())
                sharedPreference.putBoolean(entry.toKey(), true)
            } else {
                logInfo(TAG) { "ecpm threshold not reached: current=${record.eCPM} limit=$ecpmLimit" }
            }
        }
}

private fun reportRrcRoas(
    adjustToken: String,
    firebaseEvent: String
) {
    trackAdjustRrcRoas(adjustToken)
    trackFirebaseRrcRoas(firebaseEvent)
}

private fun trackAdjustRrcRoas(token: String) {
    if (token.isEmpty()) return
    val adjustEvent = AdjustEvent(token)
    Adjust.trackEvent(adjustEvent)
}

private fun trackFirebaseRrcRoas(eventName: String) {
    if (eventName.isEmpty()) return
    PoseidonSetupProcessor.getInstance().analyticsFactory.obtain(by = SupportAnalyticsBackend.Firebase).logEvent(
        name = eventName,
        parameters = mapOf()
    )
}
