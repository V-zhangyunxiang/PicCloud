package com.poseidon.ad.restrict.unification

import kotlinx.datetime.Clock
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.strategy.common.AdTypeFrequencyControlCfg
import com.poseidon.ad.main.strategy.common.AdTypeFrequencyControlsCfg
import com.poseidon.ad.restrict.FrequencyCheckResult
import com.poseidon.ad.common.toolkit.BJTimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 广告类型频控检查器，针对单一 adType 维度执行展示/点击/间隔限制。
 */
internal class AdTypeFrequencyChecker(
    private val tag: String = "AdFrequencyControlManager",
    private val adTypeNameResolver: (Int) -> String,
    private val adTypeControlResolver: (Int, AdTypeFrequencyControlsCfg?) -> AdTypeFrequencyControlCfg?
) {

    /**
     * 校验指定广告类型的频控，保持与原有流程一致。
     */
    suspend fun check(adType: Int, controls: AdTypeFrequencyControlsCfg?, stage: FrequencyCheckStage): FrequencyCheckResult {
        return runCatching {
            val adTypeName = adTypeNameResolver(adType)
            logInfo(tag) { "Checking ad type frequency control for: $adTypeName" }

            val control = adTypeControlResolver(adType, controls)
            if (control == null) {
                logInfo(tag) { "No frequency control configured for ad type: $adTypeName" }
                return@runCatching FrequencyCheckResult.Allow
            }

            val dayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val stats = RoomPolicyDispatcher.run {
                val dao = PoseidonSetupBaseProcessor.poseidonSetupProcessor().database().adFrequencyStatsDao()
                dao.getFrequencyStats(dayKey, "ad_type", adTypeName)
            }

            val currentDisplays = stats?.displayCount ?: 0L
            val currentClicks = stats?.clickCount ?: 0L
            val lastDisplayTime = stats?.lastDisplayTime ?: 0L

            logInfo(tag) {
                "AdType[$adTypeName] stats - displays: $currentDisplays/${control.maxDisplays}, " +
                        "clicks: $currentClicks/${control.maxClicks}, lastDisplay: $lastDisplayTime, interval: ${control.minInterval}s"
            }

            if (control.maxDisplays >= 0 && currentDisplays >= control.maxDisplays) {
                return@runCatching FrequencyCheckResult.Block(
                    "AdType[$adTypeName] display limit exceeded: $currentDisplays >= ${control.maxDisplays}"
                )
            }

            if (control.maxClicks >= 0 && currentClicks >= control.maxClicks) {
                return@runCatching FrequencyCheckResult.Block(
                    "AdType[$adTypeName] click limit exceeded: $currentClicks >= ${control.maxClicks}"
                )
            }

            if (control.minInterval > 0 && lastDisplayTime > 0) {
                val now = Clock.System.now().toEpochMilliseconds()
                val intervalSeconds = (now - lastDisplayTime) / 1000
                if (intervalSeconds < control.minInterval) {
                    if (stage == FrequencyCheckStage.SHOW) {
                        return@runCatching FrequencyCheckResult.Block(
                            "AdType[$adTypeName] interval too short: ${intervalSeconds}s < ${control.minInterval}s"
                        )
                    } else {
                        logInfo(tag) {
                            "AdType[$adTypeName] interval too short during load stage: ${intervalSeconds}s < ${control.minInterval}s, allowing load"
                        }
                    }
                }
            }

            logInfo(tag) { "Ad type $adTypeName frequency check passed" }
            FrequencyCheckResult.Allow
        }.onFailure { throwable ->
            logWarn(
                throwable,
                tag
            ) { "Failed to check ad type frequency: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }
}
