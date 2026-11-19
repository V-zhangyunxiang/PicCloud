package com.poseidon.ad.restrict.unification

import com.poseidon.ad.restrict.content.AdContentBalanceMtg
import com.poseidon.ad.restrict.new.NewUserFirstShowMtg
import kotlinx.datetime.Clock
import com.poseidon.ad.restrict.FrequencyCheckResult
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.common.storage.db.RoomBackedPolicyState
import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.main.adinfo.ERROR_CODE_AD_CONTENT_IMBALANCE
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_CLICK_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_DISPLAY_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_INTERVAL_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_CLICK_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_INTERVAL_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_NEW_USER_FIRST_COLD_START_BLOCKED
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_SCENE_CLICK_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_SCENE_DISPLAY_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FREQUENCY_CONTROL_SCENE_INTERVAL_LIMIT
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.strategy.common.AdTypeFrequencyControlCfg
import com.poseidon.ad.main.strategy.common.AdTypeFrequencyControlsCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.common.toolkit.BJTimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn

/**
 * 广告频控管理器
 * 实现三级组合的广告频控策略：全局、广告类型、场景
 */
internal object AdFrequencyControlManager {
    private const val TAG = "AdFrequencyControlManager"
    private val globalFrequencyChecker = GlobalFrequencyChecker(TAG)
    private val adTypeFrequencyChecker = AdTypeFrequencyChecker(TAG, ::getAdTypeName, ::getAdTypeControl)
    private val sceneFrequencyChecker = SceneFrequencyChecker(TAG)

    fun isPersistedFrequencyConfigured(config: PoseidonConfig? = PoseidonConfig.get()): Boolean {
        return config?.globalControl?.frequencySwitch == true
    }

    // 广告类型常量定义
    private const val AD_TYPE_INTERSTITIAL = 1  // 插屏广告
    private const val AD_TYPE_REWARDED = 2      // 激励广告
    private const val AD_TYPE_NATIVE = 3        // 原生广告
    private const val AD_TYPE_OPEN = 4          // 开屏广告
    private const val AD_TYPE_BANNER = 5        // Banner广告

    /**
     * 检查加载广告时的频控
     * @param sceneName 场景名称
     * @param adType 广告类型
     * @return 频控检查结果
     */
    suspend fun checkLoadFrequency(sceneName: String, adType: Int): FrequencyCheckResult {
        return checkFrequencyInternal(sceneName, adType, FrequencyCheckStage.LOAD)
    }

    /**
     * 检查展示广告时的频控
     * @param sceneName 场景名称
     * @param adType 广告类型
     * @return 频控检查结果
     */
    suspend fun checkShowFrequency(sceneName: String, adType: Int): FrequencyCheckResult {
        logInfo(TAG) {
            "Checking show frequency for scene: $sceneName, adType: ${
                getAdTypeName(
                    adType
                )
            }"
        }
        return runCatching {
            val config = PoseidonConfig.Companion.get()
            if (config == null) {
                logInfo(TAG) { "No configuration found, allowing show" }
                return@runCatching FrequencyCheckResult.Allow
            }

            // 检查首显
            val firstShowResult = checkNewUserFirstColdStart(sceneName, config.scenes, markOnBlock = true)
            if (firstShowResult is FrequencyCheckResult.Block) {
                logInfo(TAG) { "Show blocked by new user first cold start control: ${firstShowResult.reason}" }
                return@runCatching firstShowResult
            }

            // 广告大于内容检查
            val contentBalanceResult = AdContentBalanceMtg.evaluateBeforeShow(sceneName, adType, config)
            if (contentBalanceResult is FrequencyCheckResult.Block) {
                logInfo(TAG) { "Show blocked by content balance control: ${contentBalanceResult.reason}" }
                return@runCatching contentBalanceResult
            }

            // 展示阶段执行频控检查
            val frequencyResult = checkFrequencyInternal(sceneName, adType, FrequencyCheckStage.SHOW, config)
            if (frequencyResult is FrequencyCheckResult.Block) {
                logInfo(TAG) { "Show blocked by frequency control: ${frequencyResult.reason}" }
            }
            frequencyResult
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to check show frequency: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }

    /**
     * 记录广告展示
     * @param sceneName 场景名称
     * @param adType 广告类型
     * @param timestamp 展示时间戳(毫秒)，默认为当前时间
     */
    fun recordAdDisplay(sceneName: String, adType: Int, timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
        runCatching {
            if (!RoomBackedPolicyState.isFrequencyPersistenceEnabled()) {
                logInfo(TAG) { "Skip ad display Room persistence because frequency control is disabled." }
                NewUserFirstShowMtg.markSceneFirstShowApplied(sceneName)
                AdContentBalanceMtg.recordDisplay(sceneName, adType)
                return@runCatching
            }
            logInfo(TAG) {
                "Recording ad display - scene: $sceneName, adType: ${getAdTypeName(adType)}, timestamp: $timestamp"
            }

            val dayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val adTypeName = getAdTypeName(adType)
            RoomPolicyDispatcher.dispatch {
                val statsDao = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database().adFrequencyStatsDao()

                // 更新全局统计
                statsDao.insertOrUpdateDisplayCount(dayKey, "global", "global", timestamp)
                logInfo(TAG) { "Updated global display count for day: $dayKey" }

                // 更新广告类型统计
                statsDao.insertOrUpdateDisplayCount(dayKey, "ad_type", adTypeName, timestamp)
                logInfo(TAG) { "Updated ad type display count for: $adTypeName, day: $dayKey" }

                // 更新场景统计
                statsDao.insertOrUpdateDisplayCount(dayKey, "scene", sceneName, timestamp)
                logInfo(TAG) { "Updated scene display count for: $sceneName, day: $dayKey" }
            }

            // 标记first_show策略已应用（如果是新用户首次冷启动）
            NewUserFirstShowMtg.markSceneFirstShowApplied(sceneName)

            // 记录广告大于内容策略计数
            AdContentBalanceMtg.recordDisplay(sceneName, adType)

            logInfo(TAG) {
                "Ad display recorded successfully (Beijing Time: $dayKey) - scene: $sceneName, adType: $adTypeName"
            }

        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to record ad display: ${throwable.message}" }
        }
    }

    /**
     * 记录广告点击
     * @param sceneName 场景名称
     * @param adType 广告类型
     */
    fun recordAdClick(sceneName: String, adType: Int) {
        runCatching {
            if (!RoomBackedPolicyState.isFrequencyPersistenceEnabled()) {
                logInfo(TAG) { "Skip ad click Room persistence because frequency control is disabled." }
                return@runCatching
            }
            logInfo(TAG) {
                "Recording ad click - scene: $sceneName, adType: ${getAdTypeName(adType)}"
            }

            val dayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val adTypeName = getAdTypeName(adType)
            RoomPolicyDispatcher.dispatch {
                val statsDao = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database().adFrequencyStatsDao()

                // 更新全局统计
                statsDao.insertOrUpdateClickCount(dayKey, "global", "global")
                logInfo(TAG) { "Updated global click count for day: $dayKey" }

                // 更新广告类型统计
                statsDao.insertOrUpdateClickCount(dayKey, "ad_type", adTypeName)
                logInfo(TAG) { "Updated ad type click count for: $adTypeName, day: $dayKey" }

                // 更新场景统计
                statsDao.insertOrUpdateClickCount(dayKey, "scene", sceneName)
                logInfo(TAG) { "Updated scene click count for: $sceneName, day: $dayKey" }
            }

            logInfo(TAG) {
                "Ad click recorded successfully (Beijing Time: $dayKey) - scene: $sceneName, adType: $adTypeName"
            }

        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to record ad click: ${throwable.message}" }
        }
    }

    private suspend fun checkFrequencyInternal(
        sceneName: String,
        adType: Int,
        stage: FrequencyCheckStage,
        providedConfig: PoseidonConfig? = null
    ): FrequencyCheckResult {
        return runCatching {
            logInfo(TAG) {
                "Checking ${stage.name.lowercase()} frequency for scene: $sceneName, adType: ${
                    getAdTypeName(
                        adType
                    )
                }"
            }

            val config = providedConfig ?: PoseidonConfig.Companion.get()
            if (config == null) {
                logInfo(TAG) { "No configuration found, allowing ${stage.name.lowercase()}" }
                return@runCatching FrequencyCheckResult.Allow
            }

            NewUserFirstShowMtg.updateTargetScenes(config.scenes)
            val sessionFirstColdStart = NewUserFirstShowMtg.ensureSessionStateInitialized()
            logInfo(TAG) { "Session first cold start state during ${stage.name.lowercase()}: $sessionFirstColdStart" }

            val globalControl = config.globalControl
            if (!RoomBackedPolicyState.isFrequencyPersistenceEnabled(config)) {
                logInfo(TAG) { "Frequency control switch is off, allowing ${stage.name.lowercase()}" }
                return@runCatching FrequencyCheckResult.Allow
            }
            if (globalControl == null) {
                logInfo(TAG) { "Frequency control config is missing, allowing ${stage.name.lowercase()}" }
                return@runCatching FrequencyCheckResult.Allow
            }

            // 重置过期数据
            resetExpiredDataIfNeeded()

            // 检查是否启用特殊能力（跳过全局和广告类型频控）
            val adTypeControl = getAdTypeControl(adType, config.adTypeControls)
            val skipFrequencyChecks = adTypeControl?.skipFrequencyChecks == true

            if (skipFrequencyChecks) {
                logInfo(TAG) {
                    "Special capability enabled for ${getAdTypeName(adType)}, skipping global and ad type frequency checks for stage ${stage.name.lowercase()}"
                }
                // 仅检查场景频控
                sceneFrequencyChecker.check(sceneName, config.scenes, stage)
            } else {
                // 三级频控检查：全局 -> 广告类型 -> 场景
                globalFrequencyChecker.check(globalControl, stage)
                    .takeIf { it is FrequencyCheckResult.Block }
                    ?: adTypeFrequencyChecker.check(adType, config.adTypeControls, stage)
                        .takeIf { it is FrequencyCheckResult.Block }
                    ?: sceneFrequencyChecker.check(sceneName, config.scenes, stage)
            }

        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to check ${stage.name.lowercase()} frequency: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }

    /**
     * 标记首次冷启动完成（在Application启动完成时调用）
     */
    fun markFirstColdStartCompleted() {
        logInfo(TAG) { "Marking first cold start as completed" }
        NewUserFirstShowMtg.markFirstColdStartCompleted()
    }

    /**
     * 检查新用户首次冷启动广告控制
     * @param sceneName 场景名称
     * @param scenes 场景配置列表
     * @return 频控检查结果
     */
    private fun checkNewUserFirstColdStart(
        sceneName: String,
        scenes: List<SceneCfg>?,
        markOnBlock: Boolean = false
    ): FrequencyCheckResult {
        return runCatching {
            val isSessionFirstColdStart = NewUserFirstShowMtg.ensureSessionStateInitialized()
            val scene = scenes?.find { it.name == sceneName }
            if (scene == null) {
                logInfo(TAG) { "Scene $sceneName not found in configuration, allowing display" }
                return@runCatching FrequencyCheckResult.Allow
            }

            if (!isSessionFirstColdStart) {
                logInfo(TAG) { "Session is not first cold start, allowing display for scene: $sceneName" }
                return@runCatching FrequencyCheckResult.Allow
            }

            if (!scene.firstShow) {
                if (!NewUserFirstShowMtg.isSceneFirstShowApplied(sceneName)) {
                    logInfo(TAG) {
                        "New user first cold start detected, blocking ad display for scene: $sceneName (first_show=false, markOnBlock=$markOnBlock)"
                    }
                    if (markOnBlock) {
                        NewUserFirstShowMtg.markSceneFirstShowApplied(sceneName)
                    }
                    return@runCatching FrequencyCheckResult.Block(
                        "New user first cold start - ad display disabled for scene: $sceneName"
                    )
                } else {
                    logInfo(TAG) {
                        "First show already applied for scene: $sceneName, allowing display"
                    }
                }
            } else {
                logInfo(TAG) { "Scene $sceneName allows first show (first_show=true)" }
            }

            FrequencyCheckResult.Allow
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to check new user first cold start: ${throwable.message}" }
        }.getOrDefault(FrequencyCheckResult.Allow)
    }

    /**
     * 重置过期数据（如果需要）
     */
    private suspend fun resetExpiredDataIfNeeded() {
        runCatching {
            // 检查是否需要重置昨天的数据（基于北京时间）
            val currentDayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val lastResetDay = getLastResetDay()

            if (currentDayKey != lastResetDay) {
                logInfo(TAG) { "Resetting frequency stats for new Beijing day: $currentDayKey (last reset: $lastResetDay)" }

                // 清理3天前的数据
                val cutoffDayKey = BJTimeHelper.timestampToBJDayKey(
                    Clock.System.now().toEpochMilliseconds() - 3 * 24 * 60 * 60 * 1000L
                )

                RoomPolicyDispatcher.run {
                    val database = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database()
                    database.adFrequencyStatsDao().deleteStatsBefore(cutoffDayKey)
                }

                saveLastResetDay(currentDayKey)
                logInfo(TAG) { "Frequency stats reset completed for Beijing day: $currentDayKey" }
            }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to reset expired data: ${throwable.message}" }
        }
    }

    /**
     * 获取广告类型名称
     * @param adType 广告类型代码
     * @return 广告类型名称
     */
    private fun getAdTypeName(adType: Int): String {
        return when (adType) {
            AD_TYPE_INTERSTITIAL -> "interstitial"
            AD_TYPE_REWARDED -> "rewarded"
            AD_TYPE_NATIVE -> "native"
            AD_TYPE_OPEN -> "app_open"
            AD_TYPE_BANNER -> "banner"
            else -> "unknown"
        }
    }

    /**
     * 获取广告类型频控配置
     * @param adType 广告类型代码
     * @param controls 广告类型频控配置
     * @return 对应的频控配置或null
     */
    private fun getAdTypeControl(adType: Int, controls: AdTypeFrequencyControlsCfg?): AdTypeFrequencyControlCfg? {
        return when (adType) {
            AD_TYPE_INTERSTITIAL -> controls?.interstitial
            AD_TYPE_REWARDED -> controls?.rewarded
            AD_TYPE_NATIVE -> controls?.native
            AD_TYPE_OPEN -> controls?.appOpen
            AD_TYPE_BANNER -> controls?.banner
            else -> null
        }
    }

    /**
     * 获取上次重置日期
     */
    private fun getLastResetDay(): String? {
        return runCatching {
            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider = KeyProvider { "frequency_control_last_reset_day" }
            sharedPreference.getString(keyProvider, null)
        }.getOrNull()
    }

    /**
     * 保存上次重置日期
     */
    private fun saveLastResetDay(dayKey: String) {
        runCatching {
            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider = KeyProvider { "frequency_control_last_reset_day" }
            sharedPreference.putString(keyProvider, dayKey)
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to save last reset day: ${throwable.message}" }
        }
    }

    /**
     * 获取频控调试信息
     */
    fun getDebugInfo(): String {
        return runCatching {
            val beijingTimeInfo = BJTimeHelper.getTimezoneDebugInfo()
            val newUserInfo = NewUserFirstShowMtg.getDebugInfo()
            val currentDayKey = BJTimeHelper.getCurrentBeijingDayKey()
            val lastResetDay = getLastResetDay()

            """
            AdFrequencyControlManager Debug Info:
            
            $beijingTimeInfo
            
            $newUserInfo
            
            Frequency Control State:
            - Current Beijing day key: $currentDayKey
            - Last reset day: $lastResetDay
            """.trimIndent()
        }.getOrDefault("Failed to get debug info")
    }

    /**
     * 将频控检查结果转换为错误码
     * @param result 频控检查结果
     * @return 对应的错误码，允许时返回null
     */
    fun convertFrequencyResultToErrorCode(result: FrequencyCheckResult): Int? {
        return when (result) {
            is FrequencyCheckResult.Allow -> null
            is FrequencyCheckResult.Block -> {
                when {
                    result.reason.contains("Global display limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT
                    result.reason.contains("Global click limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_CLICK_LIMIT
                    result.reason.contains("Global interval too short") ->
                        ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_INTERVAL_LIMIT
                    result.reason.contains("AdType") && result.reason.contains("display limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_DISPLAY_LIMIT
                    result.reason.contains("AdType") && result.reason.contains("click limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_CLICK_LIMIT
                    result.reason.contains("AdType") && result.reason.contains("interval too short") ->
                        ERROR_CODE_FREQUENCY_CONTROL_AD_TYPE_INTERVAL_LIMIT
                    result.reason.contains("Scene") && result.reason.contains("display limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_SCENE_DISPLAY_LIMIT
                    result.reason.contains("Scene") && result.reason.contains("click limit exceeded") ->
                        ERROR_CODE_FREQUENCY_CONTROL_SCENE_CLICK_LIMIT
                    result.reason.contains("Scene") && result.reason.contains("interval too short") ->
                        ERROR_CODE_FREQUENCY_CONTROL_SCENE_INTERVAL_LIMIT
                    result.reason.contains("New user first cold start") ->
                        ERROR_CODE_FREQUENCY_CONTROL_NEW_USER_FIRST_COLD_START_BLOCKED
                    result.reason.contains("Ad content over limit") ->
                        ERROR_CODE_AD_CONTENT_IMBALANCE
                    else -> ERROR_CODE_FREQUENCY_CONTROL_GLOBAL_DISPLAY_LIMIT // 默认错误码
                }
            }
        }
    }

    /**
     * 清除所有频控数据（仅用于测试）
     */
    internal suspend fun clearAllDataForTest() {
        runCatching {
            RoomPolicyDispatcher.run {
                val database = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().database()
                database.adFrequencyStatsDao().deleteAllFrequencyStats()
            }

            val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
            val keyProvider = KeyProvider { "frequency_control_last_reset_day" }
            sharedPreference.putString(keyProvider, null)

            NewUserFirstShowMtg.clearAllStateForTest()
            AdContentBalanceMtg.resetAll()

            logInfo(TAG) { "All frequency control data cleared for test" }
        }.onFailure { throwable ->
            logWarn(
                throwable,
                TAG
            ) { "Failed to clear all data for test: ${throwable.message}" }
        }
    }
}
