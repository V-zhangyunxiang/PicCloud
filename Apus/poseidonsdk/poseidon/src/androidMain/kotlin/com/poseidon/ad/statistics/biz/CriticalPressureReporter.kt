package com.poseidon.ad.statistics.biz

import android.os.SystemClock
import com.poseidon.ad.api.AdLoadListener
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.adinfo.ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED
import com.poseidon.ad.main.processor.CriticalPressureBreaker
import com.poseidon.ad.main.processor.PreloadMemoryGuard
import com.poseidon.ad.main.processor.isSkipReportDue
import com.poseidon.ad.main.processor.mebibytes
import kotlinx.atomicfu.atomic

/**
 * Single owner for critical-pressure message construction and load-side reporting. Preload skips,
 * the host load gate, and the new-user load gate use `kjv_b_l_ad_unit_loaded`. Show suppression is
 * delivered through the required show-failure callback and reported once by the canonical
 * `biz_adpv` callback relay; it must not also be emitted as a unit-response event.
 *
 * Message shape (single structured format, pipe-parseable):
 * `CRITICAL_PRESSURE:action=<action>,sceneName=<scene>,code=<code>,reason=<reason>,` +
 * `threads=<n>,native_mb=<n>,trim=<level>,bgc_delta=<n>[,action-specific extras]`
 */
internal object CriticalPressureReporter {

    private const val TAG = "CriticalPressureReporter"

    private val lastSkipReportElapsedRealtime = atomic(0L)

    // ---------- message builders ----------

    private fun signalsSuffix(signals: CriticalPressureBreaker.PressureSignals?): String {
        val nativeMb = signals?.nativeHeapAllocatedBytes?.takeIf { it >= 0L }?.let { it / 1048576L } ?: -1L
        return ",threads=${signals?.threads ?: -1}" +
            ",native_mb=$nativeMb" +
            ",trim=${signals?.lastTrimLevel ?: -1}" +
            ",bgc_delta=${signals?.blockingGcDelta60s ?: -1}"
    }

    fun preloadSkipMessage(
        sceneName: String,
        reason: CriticalPressureBreaker.BreakerReason,
        signals: CriticalPressureBreaker.PressureSignals?,
    ): String {
        return "CRITICAL_PRESSURE:action=preload_skip,sceneName=$sceneName" +
            ",code=${CriticalPressureBreaker.preloadReasonCodeFor(reason)}" +
            ",reason=$reason" +
            signalsSuffix(signals)
    }

    fun loadSuppressedMessage(
        sceneName: String,
        reason: CriticalPressureBreaker.BreakerReason,
        signals: CriticalPressureBreaker.PressureSignals? = CriticalPressureBreaker.peekCachedSignals(),
    ): String {
        return "CRITICAL_PRESSURE:action=load_suppressed,sceneName=$sceneName" +
            ",code=$ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED" +
            ",reason=$reason" +
            signalsSuffix(signals)
    }

    fun showSuppressedMessage(
        sceneName: String,
        reason: CriticalPressureBreaker.BreakerReason,
        candidates: Int,
        bestEcpm: Double?,
        destroyedCandidates: Int,
        destroyedScavenged: Int,
        signals: CriticalPressureBreaker.PressureSignals? = CriticalPressureBreaker.peekCachedSignals(),
    ): String {
        return "CRITICAL_PRESSURE:action=show_suppressed,sceneName=$sceneName" +
            ",code=$ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED" +
            ",reason=$reason" +
            ",candidates=$candidates" +
            ",best_ecpm=${bestEcpm ?: -1.0}" +
            ",destroyed_candidates=$destroyedCandidates" +
            ",destroyed_scavenged=$destroyedScavenged" +
            signalsSuffix(signals)
    }

    // ---------- dispatch ----------

    /**
     * Preload-skip reports (guard decision point): the load callback always fires (host contract).
     * Analytics are rate-limited to one per 15-minute slot for the legacy -1053 path only; breaker
     * trips (-1055/-1056/-1057) are evidence and must not be dropped at the wall. The 16MiB heap
     * reserve also applies only to the legacy -1053 path.
     */
    fun reportPreloadSkip(sceneName: String, loadCallback: AdLoadListener?, code: Int, message: String) {
        val isBreakerTrip = code != ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
        val reportAnalytics = isBreakerTrip || claimSkipReportSlot(SystemClock.elapsedRealtime())
        LogHub.info(TAG) {
            "report preload skip: scene=$sceneName, code=$code, " +
                "hasLoadCallback=${loadCallback != null}, isBreakerTrip=$isBreakerTrip, reportAnalytics=$reportAnalytics"
        }
        loadCallback?.onAdLoadFailed(code = code, message = message)
        if (!reportAnalytics) return
        if (code == ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD && !hasReportingHeapHeadroom()) {
            LogHub.warn(TAG) { "drop preload skip analytics: heap headroom below reporting reserve" }
            return
        }
        dispatch(sceneName = sceneName, code = code, message = message)
    }

    /** Load-gate reports belong to the unit-response event family. */
    fun reportLoadGate(sceneName: String, message: String) {
        LogHub.warn(TAG) {
            "report load-gate suppression: scene=$sceneName, " +
                "code=$ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED, message=$message"
        }
        dispatch(
            sceneName = sceneName,
            code = ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED,
            message = message,
        )
    }

    private fun dispatch(sceneName: String, code: Int, message: String) {
        runCatching {
            UnitResponseTracker(
                sessionId = StringHelper.newSessionId(),
                sceneName = sceneName,
                resultCode = code,
                resultMessage = message,
            ).analyze()
        }.onFailure { throwable ->
            LogHub.warn(throwable = throwable, tag = TAG) { "pressure report dispatch failed: code=$code" }
        }
    }

    private fun claimSkipReportSlot(now: Long): Boolean {
        while (true) {
            val previous = lastSkipReportElapsedRealtime.value
            if (!isSkipReportDue(now, previous, PreloadMemoryGuard.SKIP_REPORT_INTERVAL_MILLIS)) return false
            if (lastSkipReportElapsedRealtime.compareAndSet(previous, now)) return true
        }
    }

    private fun hasReportingHeapHeadroom(): Boolean {
        val runtime = Runtime.getRuntime()
        val headroomBytes = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        return headroomBytes >= PreloadMemoryGuard.MIN_REPORTING_HEAP_HEADROOM_MB.mebibytes
    }
}

/**
 * Keeps the pressure fields needed for production diagnosis inside Firebase's 100-character
 * string-value limit. Scene and result code are intentionally omitted because both reporters
 * already send them as dedicated parameters. The full message is still delivered to host
 * callbacks and logs.
 */
internal fun criticalPressureAnalyticsMessage(message: String): String {
    if (!message.startsWith(CRITICAL_PRESSURE_MESSAGE_PREFIX)) return message

    val fields = message.substringAfter(':')
        .split(',')
        .mapNotNull { token ->
            val separator = token.indexOf('=')
            if (separator <= 0 || separator == token.lastIndex) null
            else token.substring(0, separator) to token.substring(separator + 1)
        }
        .toMap()

    val compactAction = when (fields["action"]) {
        "preload_skip" -> "ps"
        "load_suppressed" -> "ls"
        "show_suppressed" -> "ss"
        else -> fields["action"]
    }
    return listOfNotNull(
        "cp",
        compactAction?.let { "a=$it" },
        fields["reason"]?.let { "r=$it" },
        fields["candidates"]?.let { "c=$it" },
        fields["best_ecpm"]?.let { "e=$it" },
        fields["threads"]?.let { "t=$it" },
        fields["native_mb"]?.let { "n=$it" },
        fields["trim"]?.let { "m=$it" },
        fields["bgc_delta"]?.let { "g=$it" },
        fields["destroyed_candidates"]?.let { "dc=$it" },
        fields["destroyed_scavenged"]?.let { "ds=$it" },
    ).joinToString(separator = "|")
}

private const val CRITICAL_PRESSURE_MESSAGE_PREFIX = "CRITICAL_PRESSURE:"
