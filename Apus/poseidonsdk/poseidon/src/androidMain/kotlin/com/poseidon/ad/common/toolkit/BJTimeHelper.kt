package com.poseidon.ad.common.toolkit

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 北京时区工具类，负责统一生成与北京时间相关的日期、调试信息。
 * 该对象不会持有状态，所有方法都是线程安全且无副作用的。
 */
internal object BJTimeHelper {

    private const val TAG = "BeijingTimeHelper"
    private const val FALLBACK_DAY = "1970-01-01"
    private const val BJ_ZONE_ID = "Asia/Shanghai"

    private val beijingZone = TimeZone.of(BJ_ZONE_ID)
    private val systemZone: TimeZone
        get() = TimeZone.currentSystemDefault()

    /**
     * 获取当前北京日期对应的 DayKey（yyyy-MM-dd）。
     */
    fun getCurrentBeijingDayKey(): String = safeCompute(
        action = {
            val beijingDate = Clock.System.now().toLocalDateTime(beijingZone).date
            val dayKey = beijingDate.toString()
            logInfo(TAG) { "[current] BJ day key -> $dayKey" }
            dayKey
        },
        fallback = ::fallbackDayKey,
        hintOnFailure = "get current Beijing day key"
    )

    /**
     * 将 UTC 时间戳转换为北京 DayKey。
     */
    fun timestampToBJDayKey(timestamp: Long): String = safeCompute(
        action = {
            val beijingDate = Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(beijingZone)
                .date
            val dayKey = beijingDate.toString()
            logInfo(TAG) { "timestamp=$timestamp => BJ day key $dayKey" }
            dayKey
        },
        fallback = ::fallbackDayKey,
        hintOnFailure = "convert timestamp to Beijing day key"
    )

    /**
     * 输出当前 UTC/本地/北京时区的调试信息。
     */
    fun getTimezoneDebugInfo(): String = safeCompute(
        action = {
            val now = Clock.System.now()
            val beijingTime = now.toLocalDateTime(beijingZone)
            val localTime = now.toLocalDateTime(systemZone)
            val beijingDayKey = getCurrentBeijingDayKey()

            buildString {
                appendLine("Timezone Debug Info:")
                appendLine("- UTC timestamp: ${now.toEpochMilliseconds()}")
                appendLine("- Beijing time: $beijingTime")
                appendLine("- Local time: $localTime")
                appendLine("- Beijing day key: $beijingDayKey")
                appendLine("- Local timezone: $systemZone")
                append("- Beijing timezone: $beijingZone")
            }
        },
        fallback = { "Failed to get timezone debug info" },
        hintOnFailure = "collect timezone debug info"
    )

    /**
     * 当无法计算北京日期时，使用当前系统时区的日期作为降级方案。
     */
    private fun fallbackDayKey(): String {
        return try {
            val localDate = Clock.System.now().toLocalDateTime(systemZone).date
            val fallback = localDate.toString()
            logInfo(TAG) { "Fallback day key from local zone: $fallback" }
            fallback
        } catch (_: Throwable) {
            FALLBACK_DAY
        }
    }

    /**
     * 包装一个易出错的时间计算逻辑，统一处理日志与兜底值。
     */
    private inline fun <T> safeCompute(
        action: () -> T,
        fallback: () -> T,
        hintOnFailure: String
    ): T {
        return try {
            action()
        } catch (error: Throwable) {
            logWarn(error, TAG) { "Failed to $hintOnFailure: ${error.message}" }
            fallback()
        }
    }
}
