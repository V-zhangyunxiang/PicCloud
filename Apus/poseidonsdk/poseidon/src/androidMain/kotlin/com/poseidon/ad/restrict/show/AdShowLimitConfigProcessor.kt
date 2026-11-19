package com.poseidon.ad.restrict.show

import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.poseidon.ad.common.storage.db.AdLimitShowInfoEntity
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.schedule
import com.poseidon.ad.main.strategy.limit.show.AdLimitShowCfg
import com.poseidon.ad.main.strategy.limit.show.AdLimitShowItemCfg
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * 广告展示限制管理类，负责检测展示资格与自动清理历史记录。
 */
private const val TAG = "AdShowLimitMgt"

internal object AdShowLimitConfigProcessor {
    private val repository = AdLimitShowRepository()
    private val autoCleaner = AutoCleanTask()

    /**
     * 校验指定 scene 是否可以展示，返回数据库行 ID 与结果。
     */
    suspend fun check(sceneName: String): Pair<Long?, Boolean> {
        return runCatching {
            val item = AdLimitShowCfg.getAdLimitShowItem()
            if (item == null || !item.isSwitchOn()) {
                logInfo(TAG) { "[check-CanShow] ${describeConfigState(item)}" }
                sceneLimiter.clearCache()
                return@runCatching null to true
            }
            val canShow = sceneLimiter.canShow(sceneName, item)
            val rowId = repository.insertCheckResult(sceneName, canShow)
            sceneLimiter.invalidateScene(sceneName)
            logInfo(TAG) { "[check] rowId=$rowId, canShow=$canShow" }
            rowId to canShow
        }.onFailure { throwable ->
            handleError("[check] Running check failure: $throwable")
        }.onSuccess {
            logInfo(TAG) { "[check] Running check success." }
        }.getOrDefault(null to true)
    }

    /**
     * 上报指定行已展示成功，避免重复判断。
     */
    fun updateShown(rowId: Long?) {
        if (rowId == null) return
        RoomPolicyDispatcher.dispatch {
            repository.markShown(rowId)
        }
    }

    /**
     * 开启自动清理任务，每 5 分钟轮询一次。
     */
    fun startAutoCleanTask() {
        autoCleaner.resetClock()
        schedule(0L, 5.minutes.inWholeMilliseconds) {
            RoomPolicyDispatcher.dispatch {
                runCatching {
                    if (!AdLimitShowCfg.get()?.isAutoCleanSwitchOn().orTrue()) {
                        logInfo(TAG) { "[startAutoCleanTask] Auto clean switch is off!" }
                        return@dispatch
                    }
                    autoCleaner.cleanIfNeeded()
                }.onFailure {
                    handleError("[startAutoCleanTask] Running auto clean task failure: $it")
                }.onSuccess {
                    logInfo(TAG) { "[startAutoCleanTask] Running auto clean task success" }
                }
            }
        }
    }

    /**
     * 负责核心判断逻辑的 scene 限制器。
     */
    private val sceneLimiter = SceneLimiter()

    /**
     * 自动清理任务实现。
     */
    private class AutoCleanTask {
        private var lastCleanTimestamp = 0L

        fun resetClock() {
            lastCleanTimestamp = Clock.System.now().toEpochMilliseconds()
        }

        suspend fun cleanIfNeeded() {
            val now = Clock.System.now().toEpochMilliseconds()
            if (abs(now - lastCleanTimestamp) < 1.days.inWholeMilliseconds) return
            lastCleanTimestamp = now
            repository.deleteBefore(now - 3.days.inWholeMilliseconds)
        }
    }

    /**
     * 统一处理错误上报逻辑。
     */
    private fun handleError(msg: String) {
        if (PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug) {
            error(msg)
        } else {
            ErrorDetectionReporter(
                name = "AdLimitShowManagerErrorAnalyzer",
                extra = msg
            ).analyze()
        }
    }

    private fun describeConfigState(item: AdLimitShowItemCfg?): String =
        if (item == null) {
            "AdLimitShowItem is null!"
        } else {
            "Switch is off!"
        }
}

/**
 * 封装数据库访问，避免在管理类中分散 CRUD 调用。
 */
private class AdLimitShowRepository {
    private suspend fun dao() =
        PoseidonSetupBaseProcessor.poseidonSetupProcessor().database().adLimitShowInfoDao()

    suspend fun insertCheckResult(scene: String, canShow: Boolean): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        return RoomPolicyDispatcher.run {
            dao().insert(
                AdLimitShowInfoEntity(
                    scene = scene,
                    pass = if (canShow) 1L else 0L,
                    time = now,
                    shown = 0L
                )
            )
        }
    }

    suspend fun markShown(rowId: Long) {
        RoomPolicyDispatcher.run {
            dao().updateShownById(shown = 1L, id = rowId)
        }
    }

    suspend fun deleteBefore(timestamp: Long) {
        RoomPolicyDispatcher.run {
            val dao = dao()
            val infos = dao.queryBefore(timestamp)
            if (infos.isNotEmpty()) {
                infos.forEach { info -> dao.deleteById(info.id) }
            }
        }
    }

    suspend fun countSceneToday(scene: String, bounds: DayBounds): Long {
        return RoomPolicyDispatcher.run {
            dao().countBetween(scene, bounds.start, bounds.end)
        }
    }

    suspend fun firstSceneToday(scene: String, bounds: DayBounds) =
        RoomPolicyDispatcher.run {
            dao().firstBetween(scene, bounds.start, bounds.end)
        }

    suspend fun recentSceneRecords(scene: String, bounds: DayBounds, count: Long) =
        RoomPolicyDispatcher.run {
            dao().recentBetween(scene, bounds.start, bounds.end, count)
        }
}

/**
 * 单日时间范围，start/end 均为毫秒。
 */
private data class DayBounds(val start: Long, val end: Long)

/**
 * 场景限流逻辑，负责解析配置与场景匹配。
 * 每日首次查询从 DB 获取后缓存在内存中，后续查询在同一天内直接使用缓存。
 */
private class SceneLimiter {
    private val repository = AdLimitShowRepository()

    @Volatile
    private var cachedDay: String? = null
    private val sceneCountCache = mutableMapOf<String, Long>()
    private val sceneFirstCache = mutableMapOf<String, AdLimitShowInfoEntity?>()

    suspend fun canShow(sceneName: String, item: AdLimitShowItemCfg): Boolean {
        val bounds = DayBoundsProvider.todayBounds()
        val todayKey = "${bounds.start}_${item.hashCode()}"
        ensureDayCacheValid(todayKey)

        val count = sceneCountCache.getOrPut(sceneName) {
            repository.countSceneToday(sceneName, bounds)
        }
        val first = sceneFirstCache.getOrPut(sceneName) {
            repository.firstSceneToday(sceneName, bounds)
        }

        val recentShownFlags = if (count > 0) {
            val modulus = count % (item.til + 1)
            if (modulus > 0) {
                repository.recentSceneRecords(sceneName, bounds, modulus).map { it.shown == 1L }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        return resolveAdShowLimitOutcome(
            sceneName = sceneName,
            item = item,
            todayCheckCount = count.toInt(),
            firstCheckPassed = first?.let { it.pass == 1L },
            firstCheckShown = first?.let { it.shown == 1L },
            recentShownFlags = recentShownFlags,
        )
    }

    private fun ensureDayCacheValid(todayKey: String) {
        if (cachedDay != todayKey) {
            cachedDay = todayKey
            sceneCountCache.clear()
            sceneFirstCache.clear()
        }
    }

    fun invalidateScene(sceneName: String) {
        sceneCountCache.remove(sceneName)
        sceneFirstCache.remove(sceneName)
    }

    fun clearCache() {
        sceneCountCache.clear()
        sceneFirstCache.clear()
    }
}

/**
 * 负责计算当日起止时间。
 */
private object DayBoundsProvider {
    fun todayBounds(): DayBounds {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone).date
        val start = now.atStartOfDayIn(zone).toEpochMilliseconds()
        val end = now.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1
        return DayBounds(start, end)
    }
}

private fun Boolean?.orTrue(): Boolean = this ?: true
