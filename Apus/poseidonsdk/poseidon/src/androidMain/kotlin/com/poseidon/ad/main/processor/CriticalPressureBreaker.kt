package com.poseidon.ad.main.processor

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Debug
import android.os.SystemClock
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.isMainThread
import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.adinfo.ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_NATIVE_PRELOAD
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_THREAD_PRELOAD
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_TRIM_GC_PRELOAD
import com.poseidon.ad.main.adinfo.evictAllExpiredStrategyCacheAds
import com.poseidon.ad.main.adinfo.evictAllStrategyCacheAdsBelowEcpm
import com.poseidon.ad.main.preload.notification.NotificationSplashAdManager
import com.poseidon.ad.main.preload.share.CeilAutomation
import com.poseidon.ad.main.preload.share.FloorAutomation
import com.poseidon.ad.main.preload.share.SharedCeilAdPool
import com.poseidon.ad.main.preload.share.SharedFloorAdPool
import com.poseidon.ad.main.preload.special.SpecificAdSceneCacheBackup
import kotlinx.atomicfu.atomic
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Pressure-activated circuit breaker.
 *
 * Behavior changes only while the device is inside the critical pressure state; a device that
 * never trips sees zero change in how ads load, cache, or show. One rule everywhere: at CRITICAL,
 * a cached ad below the show-gate eCPM bar is destroyed wherever it is found; a cached ad at or
 * above the bar survives and may still be shown.
 */
internal object CriticalPressureBreaker {

    private const val TAG = "CriticalPressureBreaker"

    const val REMOTE_MASTER_ENABLED_KEY = "poseidon_pressure_breaker_enabled"
    const val REMOTE_SHOW_GATE_ENABLED_KEY = "poseidon_pressure_show_gate_enabled"
    const val REMOTE_LOAD_GATE_ENABLED_KEY = "poseidon_pressure_load_gate_enabled"
    const val REMOTE_SCAVENGE_ENABLED_KEY = "poseidon_pressure_scavenge_enabled"
    const val REMOTE_EMERGENCY_GC_ENABLED_KEY = "poseidon_pressure_breaker_gc_enabled"
    const val REMOTE_CRITICAL_HEAP_USAGE_PERCENT_KEY = "poseidon_preload_critical_heap_usage_percent"
    const val REMOTE_CRITICAL_MIN_HEAP_HEADROOM_MB_KEY = "poseidon_preload_critical_min_heap_headroom_mb"
    const val REMOTE_THREAD_SKIP_COUNT_KEY = "poseidon_pressure_thread_skip_count"
    const val REMOTE_THREAD_SUPPRESS_COUNT_KEY = "poseidon_pressure_thread_suppress_count"
    const val REMOTE_NATIVE_HEAP_MB_KEY = "poseidon_pressure_native_heap_mb"
    const val REMOTE_BLOCKING_GC_DELTA_KEY = "poseidon_pressure_blocking_gc_delta_60s"
    const val REMOTE_SHOW_MIN_ECPM_KEY = "poseidon_critical_show_min_ecpm"

    internal const val DEFAULT_CRITICAL_HEAP_USAGE_PERCENT = 90
    internal const val DEFAULT_CRITICAL_MIN_HEAP_HEADROOM_MB = 16
    internal const val DEFAULT_THREAD_SKIP_COUNT = 350
    internal const val DEFAULT_THREAD_SUPPRESS_COUNT = 450
    internal const val DEFAULT_BLOCKING_GC_DELTA_60S = 3
    internal const val DEFAULT_SHOW_MIN_ECPM = 10.0

    private const val SIGNALS_CACHE_MILLIS = 10_000L
    private const val SIGNALS_SHOW_GATE_MAX_AGE_MILLIS = 15_000L
    private const val SIGNALS_STALE_MILLIS = 60_000L
    private const val BLOCKING_GC_WINDOW_MILLIS = 60_000L
    private const val EMERGENCY_GC_MIN_INTERVAL_MILLIS = 5 * 60 * 1000L

    internal enum class BreakerReason { HEAP, THREAD, NATIVE_HEAP, TRIM, GC_STORM }

    internal data class PressureSignals(
        val threads: Int,
        val nativeHeapAllocatedBytes: Long,
        val lastTrimLevel: Int,
        val blockingGcDelta60s: Int,
        val sampledAtElapsedRealtime: Long,
    )

    internal data class PressureTrip(val reason: BreakerReason, val entersCritical: Boolean)

    private data class CriticalState(val reason: BreakerReason, val enteredAtElapsedRealtime: Long)

    private data class BlockingGcSample(val atElapsedRealtime: Long, val count: Int)

    private val criticalStateRef = AtomicReference<CriticalState?>()
    private val signalsRef = AtomicReference<PressureSignals?>()
    private val trimLowSkipPreloadAtomic = atomic(false)
    private val lastEmergencyGcElapsedRealtime = atomic(0L)
    private val blockingGcSamples = ArrayDeque<BlockingGcSample>()
    private val blockingGcSamplesLock = Any()

    // ---------- remote configuration ----------

    private fun remoteString(key: String): String {
        return PoseidonSetupProcessor.getInstance().remoteConfig.getString(key)
    }

    fun masterEnabled(): Boolean {
        return remoteString(REMOTE_MASTER_ENABLED_KEY).toBooleanStrictOrNull() ?: false // disabled by default
    }

    private fun showGateEnabled(): Boolean {
        return remoteString(REMOTE_SHOW_GATE_ENABLED_KEY).toBooleanStrictOrNull() ?: false
    }

    private fun loadGateEnabled(): Boolean {
        return remoteString(REMOTE_LOAD_GATE_ENABLED_KEY).toBooleanStrictOrNull() ?: false
    }

    private fun scavengeEnabled(): Boolean {
        return remoteString(REMOTE_SCAVENGE_ENABLED_KEY).toBooleanStrictOrNull() ?: false
    }

    private fun emergencyGcEnabled(): Boolean {
        return remoteString(REMOTE_EMERGENCY_GC_ENABLED_KEY).toBooleanStrictOrNull() ?: false
    }

    fun criticalHeapUsagePercent(): Int {
        return remoteString(REMOTE_CRITICAL_HEAP_USAGE_PERCENT_KEY)
            .toIntOrNull()?.coerceIn(80, 97) ?: DEFAULT_CRITICAL_HEAP_USAGE_PERCENT
    }

    fun criticalMinHeapHeadroomBytes(): Long {
        return remoteString(REMOTE_CRITICAL_MIN_HEAP_HEADROOM_MB_KEY)
            .toIntOrNull()?.coerceIn(8, 64)?.mebibytes ?: DEFAULT_CRITICAL_MIN_HEAP_HEADROOM_MB.mebibytes
    }

    fun showMinEcpm(): Double {
        return remoteString(REMOTE_SHOW_MIN_ECPM_KEY).toDoubleOrNull() ?: DEFAULT_SHOW_MIN_ECPM
    }

    private fun threadSkipCount(): Int {
        return remoteString(REMOTE_THREAD_SKIP_COUNT_KEY)
            .toIntOrNull()?.coerceIn(200, 1000) ?: DEFAULT_THREAD_SKIP_COUNT
    }

    private fun threadSuppressCount(): Int {
        return remoteString(REMOTE_THREAD_SUPPRESS_COUNT_KEY)
            .toIntOrNull()?.coerceIn(250, 1500) ?: DEFAULT_THREAD_SUPPRESS_COUNT
    }

    /**
     * Native-heap trip limit. Default (key absent): 1x the device's Java heap cap — per-heap-class
     * calibration, since native allocations beyond the entire Java heap budget are pathological
     * for this process. Remote semantics: explicit 0 disables this input; >0 sets an absolute MiB.
     */
    private fun nativeHeapLimitBytes(): Long {
        val raw = remoteString(REMOTE_NATIVE_HEAP_MB_KEY).toIntOrNull()
        if (raw != null) {
            if (raw == 0) return 0L
            return raw.coerceIn(64, 4096).mebibytes
        }
        return Runtime.getRuntime().maxMemory()
    }

    private fun blockingGcDeltaLimit(): Int {
        return remoteString(REMOTE_BLOCKING_GC_DELTA_KEY)
            .toIntOrNull()?.coerceIn(1, 20) ?: DEFAULT_BLOCKING_GC_DELTA_60S
    }

    // ---------- signal sampling (background threads only for fresh reads) ----------

    internal fun currentSignals(maxAgeMillis: Long = SIGNALS_CACHE_MILLIS): PressureSignals? {
        val now = SystemClock.elapsedRealtime()
        val cached = signalsRef.get()
        if (cached != null && now - cached.sampledAtElapsedRealtime <= maxAgeMillis) {
            return cached
        }
        if (!isMainThread()) {
            val fresh = sampleSignals(now)
            signalsRef.set(fresh)
            LogHub.info(TAG) {
                "threadcount: ${fresh.threads}\n blocking gc${fresh.blockingGcDelta60s}\n native heap alloc bytes${fresh.nativeHeapAllocatedBytes}"
            }
            recoverIfCleared(trigger = "signals_refresh")
            return fresh
        }
        // Main thread never performs the reads; reuse a recent sample and refresh asynchronously.
        dispatchToBackground { currentSignals() }
        return cached?.takeIf { now - it.sampledAtElapsedRealtime <= SIGNALS_STALE_MILLIS }
    }

    /** Returns the last sample without triggering any read. Safe on any thread. */
    internal fun peekCachedSignals(): PressureSignals? {
        return signalsRef.get()
    }

    private fun sampleSignals(now: Long): PressureSignals {
        val blockingGcCount = runCatching {
            Debug.getRuntimeStat("art.gc.blocking-gc-count")?.toIntOrNull()
        }.getOrNull()
        return PressureSignals(
            threads = runCatching { readThreadCount() }.getOrDefault(-1),
            nativeHeapAllocatedBytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(-1L),
            lastTrimLevel = runCatching { readLastTrimLevel() }.getOrDefault(-1),
            blockingGcDelta60s = recordAndComputeBlockingGcDelta(now, blockingGcCount),
            sampledAtElapsedRealtime = now,
        )
    }

    private fun readThreadCount(): Int {
        return File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Threads:") }
                ?.removePrefix("Threads:")?.trim()?.toIntOrNull() ?: -1
        }
    }

    private fun readLastTrimLevel(): Int {
        val outState = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(outState)
        return outState.lastTrimLevel
    }

    /**
     * Records a new blocking GC sample and computes how many blocking GCs
     * occurred within the sliding time window.
     *
     * @param now   Current elapsed realtime in milliseconds (e.g. SystemClock.elapsedRealtime())
     * @param count Cumulative blocking GC count from Debug.getRuntimeStat("art.gc.blocking-gc-count")
     * @return Number of blocking GC events in the window, or -1 if count is null
     */
    private fun recordAndComputeBlockingGcDelta(now: Long, count: Int?): Int {
        // Guard against missing runtime stats (API < 23 or restricted)
        if (count == null) return -1

        synchronized(blockingGcSamplesLock) {
            // 1. Append the newest sample to the tail of the queue
            blockingGcSamples.addLast(BlockingGcSample(now, count))

            // 2. Evict samples that fell outside the sliding window.
            //    This keeps only entries within [now - WINDOW, now].
            while (blockingGcSamples.isNotEmpty() &&
                now - blockingGcSamples.first().atElapsedRealtime > BLOCKING_GC_WINDOW_MILLIS
            ) {
                blockingGcSamples.removeFirst()
            }

            // 3. The head of the queue is now the oldest sample still inside the window.
            //    Delta = current cumulative count - oldest cumulative count.
            //    This gives the exact number of blocking GCs that fired during the window.
            val oldest = blockingGcSamples.first()
            return (count - oldest.count).coerceAtLeast(0)
        }
    }

    // ---------- state machine ----------

    /** Called by the preload guard / show gate when the critical heap band fires. */
    internal fun onCriticalHeapPressure() {
        if (!masterEnabled()) return
        noteCritical(BreakerReason.HEAP)
    }

    /**
     * Consulted by [PreloadMemoryGuard] after its own bands pass. Returns the trip reason when
     * preload must be skipped, null otherwise. Applies in foreground and background alike.
     */
    internal fun evaluatePreloadTrip(): BreakerReason? {
        if (!masterEnabled()) return null
        if (trimLowSkipPreloadAtomic.value) {
            // Self-heal: the trim-LOW flag must not pin preload off for the rest of a session
            // when the system never sends a follow-up trim callback.
            val signals = currentSignals()
            val level = signals?.lastTrimLevel ?: -1
            if (level in 0..ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
                trimLowSkipPreloadAtomic.value = false
            } else {
                return BreakerReason.TRIM
            }
        }
        val signals = currentSignals() ?: return null
        val trip = evaluatePressureTrip(
            threads = signals.threads,
            nativeHeapAllocatedBytes = signals.nativeHeapAllocatedBytes,
            lastTrimLevel = signals.lastTrimLevel,
            blockingGcDelta60s = signals.blockingGcDelta60s,
            threadSkipCount = threadSkipCount(),
            nativeHeapLimitBytes = nativeHeapLimitBytes(),
            blockingGcDeltaLimit = blockingGcDeltaLimit(),
        ) ?: return null
        if (trip.entersCritical) {
            noteCritical(trip.reason)
        }
        return trip.reason
    }

    /** Show-gate query. Main-thread safe: heap math plus cached signals only, never IO. */
    fun showGateTripReason(): BreakerReason? {
        if (!masterEnabled() || !showGateEnabled()) return null
        return pressureReason(threadSkipCount = threadSuppressCount())
    }

    /**
     * Load-gate query for host-requested loads (`platformPreload`, new-user preload). At CRITICAL
     * no new load runs: the load itself is the allocation spike, and a sub-threshold result would
     * be destroyed by the scavenge anyway. Main-thread safe.
     */
    fun loadGateTripReason(): BreakerReason? {
        if (!masterEnabled() || !loadGateEnabled()) return null
        return pressureReason(threadSkipCount = threadSuppressCount())
    }

    /**
     * For SDK-initiated optional work (e.g. show-time realtime preloads): like the show gate but
     * also honors the trim-LOW skip flag. Main-thread safe.
     */
    fun shouldSuppressOptionalWork(): Boolean {
        if (!masterEnabled()) return false
        if (trimLowSkipPreloadAtomic.value) return true
        return pressureReason(threadSkipCount = threadSuppressCount()) != null
    }

    private fun pressureReason(threadSkipCount: Int): BreakerReason? {
        val runtime = Runtime.getRuntime()
        val heapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        if (isCriticalHeapPressure(
                heapUsedBytes = heapUsedBytes,
                heapMaxBytes = runtime.maxMemory(),
                criticalHeapUsagePercent = criticalHeapUsagePercent(),
                criticalMinHeapHeadroomBytes = criticalMinHeapHeadroomBytes(),
            )
        ) {
            onCriticalHeapPressure()
            return BreakerReason.HEAP
        }
        criticalStateRef.get()?.let { return it.reason }
        val signals = currentSignals(maxAgeMillis = SIGNALS_SHOW_GATE_MAX_AGE_MILLIS) ?: return null
        val trip = evaluatePressureTrip(
            threads = signals.threads,
            nativeHeapAllocatedBytes = signals.nativeHeapAllocatedBytes,
            lastTrimLevel = signals.lastTrimLevel,
            blockingGcDelta60s = signals.blockingGcDelta60s,
            threadSkipCount = threadSkipCount,
            nativeHeapLimitBytes = nativeHeapLimitBytes(),
            blockingGcDeltaLimit = blockingGcDeltaLimit(),
        ) ?: return null
        if (!trip.entersCritical) return null
        noteCritical(trip.reason)
        return trip.reason
    }

    private fun noteCritical(reason: BreakerReason) {
        val now = SystemClock.elapsedRealtime()
        if (criticalStateRef.get() != null) return
        if (!criticalStateRef.compareAndSet(null, CriticalState(reason, now))) return
        LogHub.warn(TAG) { "enter critical pressure state: reason=$reason" }
        dispatchToBackground {
            val destroyed = scavengeSubThresholdCaches(reason = "critical_enter_$reason")
            maybeRunEmergencyGc()
            LogHub.warn(TAG) { "critical entry scavenge done: reason=$reason, destroyed=$destroyed" }
            recoverIfCleared(trigger = "post_scavenge_$reason")
        }
    }

    private fun recoverIfCleared(trigger: String) {
        val state = criticalStateRef.get() ?: return
        val runtime = Runtime.getRuntime()
        val heapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        if (!hasRecoveredFromMemoryPressure(heapUsedBytes, runtime.maxMemory())) return
        val signals = signalsRef.get()
        if (signals != null) {
            val trip = evaluatePressureTrip(
                threads = signals.threads,
                nativeHeapAllocatedBytes = signals.nativeHeapAllocatedBytes,
                lastTrimLevel = signals.lastTrimLevel,
                blockingGcDelta60s = signals.blockingGcDelta60s,
                threadSkipCount = threadSkipCount(),
                nativeHeapLimitBytes = nativeHeapLimitBytes(),
                blockingGcDeltaLimit = blockingGcDeltaLimit(),
            )
            if (trip?.entersCritical == true) return
        }
        if (criticalStateRef.compareAndSet(state, null)) {
            LogHub.info(TAG) {
                "exit critical pressure state: trigger=$trigger, enteredReason=${state.reason}"
            }
        }
    }

    // ---------- scavenge & emergency gc ----------

    internal fun scavengeSubThresholdCaches(reason: String): Int {
        if (!masterEnabled() || !scavengeEnabled()) return 0
        val minEcpmToKeep = showMinEcpm()
        var destroyed = 0
        destroyed += evictAllStrategyCacheAdsBelowEcpm(minEcpmToKeep, reason)
        destroyed += SharedCeilAdPool.scavengeBelowEcpm(minEcpmToKeep, reason)
        destroyed += SharedFloorAdPool.scavengeBelowEcpm(minEcpmToKeep, reason)
        destroyed += CeilAutomation.scavengeBelowEcpm(minEcpmToKeep, reason)
        destroyed += FloorAutomation.scavengeBelowEcpm(minEcpmToKeep, reason)
        destroyed += SpecificAdSceneCacheBackup.scavengeBelowEcpm(minEcpmToKeep, reason)
        destroyed += NotificationSplashAdManager.scavengeBelowEcpm(minEcpmToKeep, reason)
        LogHub.warn(TAG) {
            "scavenged sub-threshold cached ads: reason=$reason, minEcpmToKeep=$minEcpmToKeep, destroyed=$destroyed"
        }
        return destroyed
    }

    /** Bounded emergency reclaim: at most once per [EMERGENCY_GC_MIN_INTERVAL_MILLIS], worker only. */
    internal fun maybeRunEmergencyGc() {
        if (!emergencyGcEnabled()) return
        val now = SystemClock.elapsedRealtime()
        while (true) {
            val previous = lastEmergencyGcElapsedRealtime.value
            if (previous != 0L && now >= previous && now - previous < EMERGENCY_GC_MIN_INTERVAL_MILLIS) {
                return
            }
            if (lastEmergencyGcElapsedRealtime.compareAndSet(previous, now)) break
        }
        LogHub.warn(TAG) { "run emergency gc under critical pressure" }
        runCatching { Runtime.getRuntime().gc() }
    }

    // ---------- trim handling ----------

    val componentCallbacks: ComponentCallbacks2 = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }

        override fun onTrimMemory(level: Int) {
            if (!masterEnabled()) return
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    noteCritical(BreakerReason.TRIM)
                }

                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    trimLowSkipPreloadAtomic.value = true
                }

                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    trimLowSkipPreloadAtomic.value = false
                }

                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    dispatchToBackground { evictExpiredCaches(reason = "trim_$level") }
                }
            }
        }
    }

    private fun evictExpiredCaches(reason: String) {
        evictAllExpiredStrategyCacheAds(reason)
        SharedCeilAdPool.evictExpiredAdsNow(reason)
        SharedFloorAdPool.evictExpiredAdsNow(reason)
        CeilAutomation.evictExpiredAdsNow(reason)
        FloorAutomation.evictExpiredAdsNow(reason)
        SpecificAdSceneCacheBackup.evictExpiredAdsNow(reason)
        NotificationSplashAdManager.evictExpiredAdNow(reason)
    }

    internal fun preloadReasonCodeFor(reason: BreakerReason): Int {
        return when (reason) {
            BreakerReason.THREAD -> ERROR_CODE_CRITICAL_PRESSURE_THREAD_PRELOAD
            BreakerReason.NATIVE_HEAP -> ERROR_CODE_CRITICAL_PRESSURE_NATIVE_PRELOAD
            BreakerReason.TRIM,
            BreakerReason.GC_STORM -> ERROR_CODE_CRITICAL_PRESSURE_TRIM_GC_PRELOAD

            BreakerReason.HEAP -> ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
        }
    }
}

// ---------- pure seams (unit tested) ----------

internal fun isCriticalHeapPressure(
    heapUsedBytes: Long,
    heapMaxBytes: Long,
    criticalHeapUsagePercent: Int = CriticalPressureBreaker.DEFAULT_CRITICAL_HEAP_USAGE_PERCENT,
    criticalMinHeapHeadroomBytes: Long = CriticalPressureBreaker.DEFAULT_CRITICAL_MIN_HEAP_HEADROOM_MB.mebibytes,
): Boolean {
    if (heapMaxBytes <= 0L) return false
    val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
    val heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes
    val criticalUsageThresholdBytes = heapMaxBytes * criticalHeapUsagePercent / 100
    val criticalHeadroomBytes = min(
        criticalMinHeapHeadroomBytes,
        heapMaxBytes * (100 - criticalHeapUsagePercent) / 100,
    )
    return normalizedHeapUsedBytes >= criticalUsageThresholdBytes ||
        heapHeadroomBytes <= criticalHeadroomBytes
}

internal fun evaluatePressureTrip(
    threads: Int,
    nativeHeapAllocatedBytes: Long,
    lastTrimLevel: Int,
    blockingGcDelta60s: Int,
    threadSkipCount: Int = CriticalPressureBreaker.DEFAULT_THREAD_SKIP_COUNT,
    nativeHeapLimitBytes: Long = 0L,
    blockingGcDeltaLimit: Int = CriticalPressureBreaker.DEFAULT_BLOCKING_GC_DELTA_60S,
): CriticalPressureBreaker.PressureTrip? {
    if (threads >= threadSkipCount) {
        return CriticalPressureBreaker.PressureTrip(
            CriticalPressureBreaker.BreakerReason.THREAD,
            entersCritical = true,
        )
    }
    if (nativeHeapLimitBytes in 1..nativeHeapAllocatedBytes) {
        return CriticalPressureBreaker.PressureTrip(
            CriticalPressureBreaker.BreakerReason.NATIVE_HEAP,
            entersCritical = true,
        )
    }
    if (blockingGcDelta60s >= blockingGcDeltaLimit) {
        return CriticalPressureBreaker.PressureTrip(
            CriticalPressureBreaker.BreakerReason.GC_STORM,
            entersCritical = true,
        )
    }
    if (lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
        return CriticalPressureBreaker.PressureTrip(
            CriticalPressureBreaker.BreakerReason.TRIM,
            entersCritical = true,
        )
    }
    if (lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
        return CriticalPressureBreaker.PressureTrip(
            CriticalPressureBreaker.BreakerReason.TRIM,
            entersCritical = false,
        )
    }
    return null
}
