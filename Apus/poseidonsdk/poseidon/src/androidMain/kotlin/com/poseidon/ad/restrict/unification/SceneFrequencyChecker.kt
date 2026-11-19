package com.poseidon.ad.restrict.unification

import kotlinx.datetime.Clock
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.restrict.FrequencyCheckResult
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.common.toolkit.BJTimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 场景级频控检查器，基于配置中的 scene 列表执行统计与阈值判断。
 */
internal class SceneFrequencyChecker(private val tag: String = "AdFrequencyControlManager") {

    /**
     * 校验某个场景在当前阶段是否满足频控要求。
     */
    suspend fun check(sceneName: String, scenes: List<SceneCfg>?, stage: FrequencyCheckStage): FrequencyCheckResult {
        return runCatching {
            logInfo(tag) { "Checking scene frequency control for: $sceneName" }

            val scene = scenes?.find { it.name == sceneName }
            if (scene == null) {
                logInfo(tag) { "Scene $sceneName not found in configuration" }
                return@runCatching FrequencyCheckResult.Allow
            }

            val dayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val stats = RoomPolicyDispatcher.run {
                val dao = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database().adFrequencyStatsDao()
                dao.getFrequencyStats(dayKey, "scene", sceneName)
            }

            val currentDisplays = stats?.displayCount ?: 0L
            val currentClicks = stats?.clickCount ?: 0L
            val lastDisplayTime = stats?.lastDisplayTime ?: 0L

            val displayLimitText = if (scene.maxDisplays < 0) "unlimited" else "${scene.maxDisplays}"
            val clickLimitText = if (scene.maxClicks < 0) "unlimited" else "${scene.maxClicks}"
            val intervalText = if (scene.minInterval <= 0) "no limit" else "${scene.minInterval}s"

            logInfo(tag) {
                "Scene $sceneName stats - displays: $currentDisplays/$displayLimitText, " +
                        "clicks: $currentClicks/$clickLimitText, lastDisplay: $lastDisplayTime, minInterval: $intervalText"
            }

            if (scene.maxDisplays >= 0 && currentDisplays >= scene.maxDisplays) {
                return@runCatching FrequencyCheckResult.Block(
                    "Scene[$sceneName] display limit exceeded: $currentDisplays >= ${scene.maxDisplays}"
                )
            }

            if (scene.maxClicks >= 0 && currentClicks >= scene.maxClicks) {
                return@runCatching FrequencyCheckResult.Block(
                    "Scene[$sceneName] click limit exceeded: $currentClicks >= ${scene.maxClicks}"
                )
            }

            if (scene.minInterval > 0 && lastDisplayTime > 0) {
                val now = Clock.System.now().toEpochMilliseconds()
                val intervalSeconds = (now - lastDisplayTime) / 1000
                if (intervalSeconds < scene.minInterval) {
                    if (stage == FrequencyCheckStage.SHOW) {
                        return@runCatching FrequencyCheckResult.Block(
                            "Scene[$sceneName] interval too short: ${intervalSeconds}s < ${scene.minInterval}s"
                        )
                    } else {
                        logInfo(tag) {
                            "Scene[$sceneName] interval too short during load stage: ${intervalSeconds}s < ${scene.minInterval}s, allowing load"
                        }
                    }
                }
            }

            logInfo(tag) { "Scene $sceneName frequency check passed" }
            FrequencyCheckResult.Allow

        }.onFailure { throwable ->
            logWarn(
                throwable,
                tag
            ) { "Failed to check scene frequency: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }
}
