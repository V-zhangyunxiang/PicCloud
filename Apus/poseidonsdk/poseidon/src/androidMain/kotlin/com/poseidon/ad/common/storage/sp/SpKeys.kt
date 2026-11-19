package com.poseidon.ad.common.storage.sp

/**
 * 提供 SP key 的统一接口，便于不同模块扩展。
 */
fun interface KeyProvider {
    fun key(): String
}

/**
 * 批量写入条目，用于 SpHelper.writeBatch()。
 */
sealed interface SpBatchEntry {
    val key: KeyProvider
    data class BooleanValue(override val key: KeyProvider, val value: Boolean) : SpBatchEntry
    data class IntValue(override val key: KeyProvider, val value: Int) : SpBatchEntry
    data class LongValue(override val key: KeyProvider, val value: Long) : SpBatchEntry
    data class StringValue(override val key: KeyProvider, val value: String?) : SpBatchEntry
}

/**
 * 默认 key 列表，兼容原有枚举值。
 */
enum class Key : KeyProvider {
    HighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalledAdjustTracked,
    HighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalledAdjustTracked,
    AdImpressionCumulativeRevenueGreatThan0dot01,
    AdmobOrFacebookAdImpressionCount,
    AdmobOrFacebookAdImpressionLastDate,
    NotificationSplashAdLastRequestTimeInMillis,
    NotificationSplashAdConsecutiveLoadFailuresCount,
    NotificationSplashAdConsecutiveLoadFailuresCountLastDate,
    NotificationSplashAdFillCount,
    NotificationSplashAdFillCountLastDate,
    SharedAdConsecutiveLoadFailuresCountByAdUnitId,
    SharedAdConsecutiveLoadFailuresCountByAdUnitIdLastDate,
    SharedAdRequestCountByAdUnitId,
    SharedAdRequestCountByAdUnitIdLastDate,
    LimitedFullscreenAdLastClosedTimeInMillisV2,
    AeoTracking,
    CmpIsInEurope,
    @Suppress("SpellCheckingInspection")
    DeviceIdGenerateBySdk,
    @Suppress("SpellCheckingInspection")
    CtrIsGreatThanLimit,
    @Suppress("SpellCheckingInspection")
    CtrTooHighLastDate,
    @Suppress("SpellCheckingInspection")
    AdjustAttributionNetworkSync,
    @Suppress("SpellCheckingInspection")
    AdjustAttributionCampaignSync,
    @Suppress("SpellCheckingInspection")
    PoseidonKaCandidate,
    SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId,
    SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitIdLastDate,
    SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId,
    SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitIdLastDate,
    AutomationAdConsecutiveLoadFailuresCountByAdUnitId,
    RrcRoasTracking;

    override fun key(): String = name
}
