package com.poseidon.ad.restrict.unification

import kotlinx.datetime.Clock
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.restrict.FrequencyCheckResult
import com.poseidon.ad.main.strategy.common.GlobalFrequencyControlCfg
import com.poseidon.ad.common.toolkit.BJTimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 全局频控检查器，封装全局级别的展示/点击限制逻辑。
 */
internal class GlobalFrequencyChecker(private val tag: String = "AdFrequencyControlManager") {

    /**
     * 执行全局频控校验，保持与原逻辑一致。
     */
    suspend fun check(globalControl: GlobalFrequencyControlCfg, stage: FrequencyCheckStage): FrequencyCheckResult {
        return runCatching {
            logInfo(tag) { "Checking global frequency control" }

            val dayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val stats = RoomPolicyDispatcher.run {
                val dao = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database().adFrequencyStatsDao()
                dao.getFrequencyStats(dayKey, "global", "global")
            }

            val currentDisplays = stats?.displayCount ?: 0L
            val currentClicks = stats?.clickCount ?: 0L
            val lastDisplayTime = stats?.lastDisplayTime ?: 0L

            val displayLimitText = if (globalControl.maxDisplays < 0) "unlimited" else "${globalControl.maxDisplays}"
            val clickLimitText = if (globalControl.maxClicks < 0) "unlimited" else "${globalControl.maxClicks}"
            val intervalText = if (globalControl.minInterval <= 0) "no limit" else "${globalControl.minInterval}s"

            logInfo(tag) {
                "Global stats - displays: $currentDisplays/$displayLimitText, " +
                        "clicks: $currentClicks/$clickLimitText, " +
                        "lastDisplay: $lastDisplayTime, minInterval: $intervalText"
            }

            if (globalControl.maxDisplays >= 0 && currentDisplays >= globalControl.maxDisplays) {
                return@runCatching FrequencyCheckResult.Block(
                    "Global display limit exceeded: $currentDisplays >= ${globalControl.maxDisplays}"
                )
            }

            if (globalControl.maxClicks >= 0 && currentClicks >= globalControl.maxClicks) {
                return@runCatching FrequencyCheckResult.Block(
                    "Global click limit exceeded: $currentClicks >= ${globalControl.maxClicks}"
                )
            }

            if (globalControl.minInterval > 0 && lastDisplayTime > 0) {
                val now = Clock.System.now().toEpochMilliseconds()
                val intervalSeconds = (now - lastDisplayTime) / 1000
                if (intervalSeconds < globalControl.minInterval) {
                    if (stage == FrequencyCheckStage.SHOW) {
                        return@runCatching FrequencyCheckResult.Block(
                            "Global interval too short: ${intervalSeconds}s < ${globalControl.minInterval}s"
                        )
                    } else {
                        logInfo(tag) {
                            "Global interval too short during load stage: ${intervalSeconds}s < ${globalControl.minInterval}s, allowing load"
                        }
                    }
                }
            }

            logInfo(tag) { "Global frequency check passed" }
            FrequencyCheckResult.Allow
        }.onFailure { throwable ->
            logWarn(
                throwable,
                tag
            ) { "Failed to check global frequency: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }
}
