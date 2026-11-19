package com.poseidon.ad.main.processor

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import com.poseidon.ad.api.AdLoadListener
import com.poseidon.ad.api.PoseidonMemoryPressureEvent
import com.poseidon.ad.api.PoseidonMemoryPressureLevel
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.adinfo.ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
import com.poseidon.ad.statistics.biz.CriticalPressureReporter
import com.poseidon.ad.statistics.biz.PreloadMemoryPressureDeviceReporter
import com.poseidon.ad.statistics.biz.PreloadMemoryPressureDeviceSnapshot
import kotlinx.atomicfu.atomic
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.atomic.AtomicReference

internal const val BACKGROUND_LOW_MEMORY_PRELOAD_MESSAGE = "BACKGROUND_LOW_MEMORY_PRELOAD"

internal object PreloadMemoryGuard {
    const val TAG = "PreloadMemoryGuard"
    const val REMOTE_SWITCH_KEY = "poseidon_background_low_memory_preload_guard_enabled"
    const val REMOTE_MAX_HEAP_USAGE_PERCENT_KEY = "poseidon_background_preload_max_heap_usage_percent"
    const val REMOTE_MIN_HEAP_HEADROOM_MB_KEY = "poseidon_background_preload_min_heap_headroom_mb"
    const val REMOTE_DEVICE_REPORT_ENABLED_KEY = "poseidon_memory_pressure_device_report_enabled"
    const val REMOTE_DEVICE_REPORT_MIN_HEAP_USAGE_PERCENT_KEY = "poseidon_memory_pressure_device_report_min_percent"
    const val REMOTE_DEVICE_REPORT_DEFER_HEAP_USAGE_PERCENT_KEY = "poseidon_memory_pressure_device_report_defer_percent"

    internal const val DEFAULT_MAX_HEAP_USAGE_PERCENT = 80
    internal const val DEFAULT_MIN_HEAP_HEADROOM_MB = 32
    internal const val CRITICAL_HEAP_USAGE_PERCENT = 90
    internal const val CRITICAL_HEAP_HEADROOM_MB = 16
    internal const val MIN_REPORTING_HEAP_HEADROOM_MB = 16
    internal const val SKIP_REPORT_INTERVAL_MILLIS = 15 * 60 * 1000L
    internal const val MEMORY_PRESSURE_NOTIFY_INTERVAL_MILLIS = 5 * 60 * 1000L
    internal const val LOW_MEMORY_PRESSURE_RECOVERY_HEAP_USAGE_PERCENT = 70
    internal const val LOW_MEMORY_PRESSURE_RECOVERY_HEAP_HEADROOM_MB = 64
    internal const val DEFAULT_DEVICE_REPORT_MIN_HEAP_USAGE_PERCENT = 90
    internal const val DEFAULT_DEVICE_REPORT_DEFER_HEAP_USAGE_PERCENT = 95

    private val memoryPressureDeviceReportedThisSession = atomic(false)
    private val pendingMemoryPressureDeviceSnapshot = AtomicReference<PreloadMemoryPressureDeviceSnapshot?>()
    private val memoryPressureNotifyLock = Any()
    private var lastNotifiedMemoryPressureLevel: PoseidonMemoryPressureLevel? = null
    private var lastMemoryPressureNotifyElapsedRealtime = 0L

    fun shouldSkipPreload(
        context: Context,
        sceneName: String,
        loadCallback: AdLoadListener? = null,
    ): Boolean {
        val remoteConfig = PoseidonSetupProcessor.getInstance().remoteConfig
        val remoteEnabledValue = remoteConfig.getString(REMOTE_SWITCH_KEY)
        val remoteMaxHeapUsagePercent = remoteConfig.getString(REMOTE_MAX_HEAP_USAGE_PERCENT_KEY)
        val remoteMinHeapHeadroomMb = remoteConfig.getString(REMOTE_MIN_HEAP_HEADROOM_MB_KEY)
        val enabled = backgroundPreloadGuardEnabled(remoteEnabledValue)
        val maxHeapUsagePercent = remoteMaxHeapUsagePercent
            .toIntOrNull()?.coerceIn(50, 95) ?: DEFAULT_MAX_HEAP_USAGE_PERCENT
        val minHeapHeadroomMb = remoteMinHeapHeadroomMb
            .toIntOrNull()?.coerceIn(8, 128) ?: DEFAULT_MIN_HEAP_HEADROOM_MB
        val deviceReportEnabled = remoteConfig.getString(REMOTE_DEVICE_REPORT_ENABLED_KEY)
            .toBooleanStrictOrNull() ?: false
        val deviceReportMinHeapUsagePercent = remoteConfig.getString(REMOTE_DEVICE_REPORT_MIN_HEAP_USAGE_PERCENT_KEY)
            .toIntOrNull()?.coerceIn(80, 95) ?: DEFAULT_DEVICE_REPORT_MIN_HEAP_USAGE_PERCENT
        val deviceReportDeferHeapUsagePercent =
            remoteConfig.getString(REMOTE_DEVICE_REPORT_DEFER_HEAP_USAGE_PERCENT_KEY)
                .toIntOrNull()
                ?.coerceIn(deviceReportMinHeapUsagePercent, 99)
                ?: DEFAULT_DEVICE_REPORT_DEFER_HEAP_USAGE_PERCENT.coerceAtLeast(deviceReportMinHeapUsagePercent)
        val runtime = Runtime.getRuntime()
        val heapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        val heapMaxBytes = runtime.maxMemory()
        val isAppInForeground = PoseidonSetupProcessor.isAppInForeground
        val isSystemLowMemory = context.isLowMemory()
        val minHeapHeadroomBytes = minHeapHeadroomMb.mebibytes
        LogHub.info(TAG) {
            "background preload memory guard input: " +
                "enabled=$enabled(raw=$remoteEnabledValue), " +
                "foreground=$isAppInForeground, " +
                "systemLowMemory=$isSystemLowMemory, " +
                "maxHeapUsagePercent=$maxHeapUsagePercent(raw=$remoteMaxHeapUsagePercent), " +
                "minHeapHeadroomMb=$minHeapHeadroomMb(raw=$remoteMinHeapHeadroomMb), " +
                heapSnapshot(heapUsedBytes, heapMaxBytes)
        }
        maybeReportMemoryPressureDevice(
            context = context.applicationContext,
            deviceReportEnabled = deviceReportEnabled,
            guardEnabled = enabled,
            isAppInForeground = isAppInForeground,
            isSystemLowMemory = isSystemLowMemory,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            maxHeapUsagePercent = maxHeapUsagePercent,
            minHeapHeadroomMb = minHeapHeadroomMb,
            reportMinHeapUsagePercent = deviceReportMinHeapUsagePercent,
            reportDeferHeapUsagePercent = deviceReportDeferHeapUsagePercent,
        )
        val criticalHeapUsagePercent = CriticalPressureBreaker.criticalHeapUsagePercent()
        val criticalMinHeapHeadroomBytes = CriticalPressureBreaker.criticalMinHeapHeadroomBytes()
        var shouldSkip = shouldSkipPreload(
            enabled = enabled,
            isAppInForeground = isAppInForeground,
            isSystemLowMemory = isSystemLowMemory,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            maxHeapUsagePercent = maxHeapUsagePercent,
            minHeapHeadroomBytes = minHeapHeadroomBytes,
            criticalHeapUsagePercent = criticalHeapUsagePercent,
            criticalMinHeapHeadroomBytes = criticalMinHeapHeadroomBytes,
        )
        var tripReason: CriticalPressureBreaker.BreakerReason? = null
        if (shouldSkip) {
            if (isCriticalHeapPressure(
                    heapUsedBytes = heapUsedBytes,
                    heapMaxBytes = heapMaxBytes,
                    criticalHeapUsagePercent = criticalHeapUsagePercent,
                    criticalMinHeapHeadroomBytes = criticalMinHeapHeadroomBytes,
                )
            ) {
                CriticalPressureBreaker.onCriticalHeapPressure()
            }
        } else {
            tripReason = CriticalPressureBreaker.evaluatePreloadTrip()
            if (tripReason != null) {
                LogHub.warn(TAG) {
                    "skip background preload: critical pressure breaker trip, reason=$tripReason"
                }
                shouldSkip = true
            }
        }
        val pressureLevel = evaluateMemoryPressureLevel(
            enabled = enabled,
            isSystemLowMemory = isSystemLowMemory,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            maxHeapUsagePercent = maxHeapUsagePercent,
            minHeapHeadroomBytes = minHeapHeadroomBytes,
            criticalHeapUsagePercent = criticalHeapUsagePercent,
            criticalMinHeapHeadroomBytes = criticalMinHeapHeadroomBytes,
        )
        if (pressureLevel == null) {
            resetMemoryPressureNotificationIfRecovered(
                heapUsedBytes = heapUsedBytes,
                heapMaxBytes = heapMaxBytes,
            )
        } else {
            notifyMemoryPressure(
                level = pressureLevel,
                foreground = isAppInForeground,
                systemLowMemory = isSystemLowMemory,
                heapUsedBytes = heapUsedBytes,
                heapMaxBytes = heapMaxBytes,
            )
        }
        if (shouldSkip) {
            // Decision-point reporting via the single owner: the reason is in scope here.
            val code: Int
            val message: String
            if (tripReason != null) {
                code = CriticalPressureBreaker.preloadReasonCodeFor(tripReason)
                message = CriticalPressureReporter.preloadSkipMessage(
                    sceneName = sceneName,
                    reason = tripReason,
                    signals = CriticalPressureBreaker.peekCachedSignals(),
                )
            } else {
                code = ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD
                message = backgroundLowMemoryPreloadMessage(sceneName)
            }
            CriticalPressureReporter.reportPreloadSkip(
                sceneName = sceneName,
                loadCallback = loadCallback,
                code = code,
                message = message,
            )
        }
        return shouldSkip
    }

    internal fun shouldSkipPreload(
        enabled: Boolean,
        isAppInForeground: Boolean,
        isSystemLowMemory: Boolean,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        maxHeapUsagePercent: Int = DEFAULT_MAX_HEAP_USAGE_PERCENT,
        minHeapHeadroomBytes: Long = DEFAULT_MIN_HEAP_HEADROOM_MB.mebibytes,
        criticalHeapUsagePercent: Int = CRITICAL_HEAP_USAGE_PERCENT,
        criticalMinHeapHeadroomBytes: Long = CRITICAL_HEAP_HEADROOM_MB.mebibytes,
    ): Boolean {
        if (!enabled) {
            LogHub.info(TAG) { "allow background preload: guard disabled by remote config" }
            return false
        }
        if (heapMaxBytes <= 0L) {
            LogHub.warn(TAG) {
                "allow background preload: invalid max heap, cannot evaluate memory guard, " +
                    "heapMaxBytes=$heapMaxBytes, heapUsedBytes=$heapUsedBytes"
            }
            return false
        }

        val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
        val heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes
        val criticalUsageThresholdBytes = heapMaxBytes * criticalHeapUsagePercent / 100
        val criticalHeadroomBytes = min(
            criticalMinHeapHeadroomBytes,
            heapMaxBytes * (100 - criticalHeapUsagePercent) / 100,
        )
        LogHub.info(TAG) {
            "background preload memory guard thresholds: " +
                "foreground=$isAppInForeground, " +
                "systemLowMemory=$isSystemLowMemory, " +
                "criticalUsageThreshold=${criticalUsageThresholdBytes.toMiBString()}, " +
                "criticalHeadroomThreshold=${criticalHeadroomBytes.toMiBString()}, " +
                heapSnapshot(normalizedHeapUsedBytes, heapMaxBytes)
        }
        if (normalizedHeapUsedBytes >= criticalUsageThresholdBytes ||
            heapHeadroomBytes <= criticalHeadroomBytes
        ) {
            LogHub.warn(TAG) {
                "skip background preload: critical heap pressure, " +
                    "used=${normalizedHeapUsedBytes.toMiBString()}, " +
                    "headroom=${heapHeadroomBytes.toMiBString()}, " +
                    "criticalUsageThreshold=${criticalUsageThresholdBytes.toMiBString()}, " +
                    "criticalHeadroomThreshold=${criticalHeadroomBytes.toMiBString()}"
            }
            return true
        }
        if (isSystemLowMemory) {
            LogHub.warn(TAG) {
                "skip background preload: ActivityManager reports system low memory, " +
                    heapSnapshot(normalizedHeapUsedBytes, heapMaxBytes)
            }
            return true
        }
        if (isAppInForeground) {
            LogHub.info(TAG) {
                "allow background preload: app is foreground and heap is below critical threshold"
            }
            return false
        }

        val usageThresholdBytes = heapMaxBytes * maxHeapUsagePercent.coerceIn(1, 100) / 100
        val effectiveMinHeadroomBytes = min(
            max(0L, minHeapHeadroomBytes),
            heapMaxBytes * 20 / 100,
        )
        val skipForUsage = normalizedHeapUsedBytes >= usageThresholdBytes
        val skipForHeadroom = heapHeadroomBytes <= effectiveMinHeadroomBytes
        val shouldSkip = skipForUsage || skipForHeadroom
        if (shouldSkip) {
            LogHub.warn(TAG) {
                "skip background preload: background heap threshold reached, " +
                    "skipForUsage=$skipForUsage, " +
                    "skipForHeadroom=$skipForHeadroom, " +
                    "usageThreshold=${usageThresholdBytes.toMiBString()}($maxHeapUsagePercent%), " +
                    "headroomThreshold=${effectiveMinHeadroomBytes.toMiBString()}, " +
                    heapSnapshot(normalizedHeapUsedBytes, heapMaxBytes)
            }
        } else {
            LogHub.info(TAG) {
                "allow background preload: background heap is healthy, " +
                    "usageThreshold=${usageThresholdBytes.toMiBString()}($maxHeapUsagePercent%), " +
                    "headroomThreshold=${effectiveMinHeadroomBytes.toMiBString()}, " +
                    heapSnapshot(normalizedHeapUsedBytes, heapMaxBytes)
            }
        }
        return shouldSkip
    }



    private fun maybeReportMemoryPressureDevice(
        context: Context,
        deviceReportEnabled: Boolean,
        guardEnabled: Boolean,
        isAppInForeground: Boolean,
        isSystemLowMemory: Boolean,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        maxHeapUsagePercent: Int,
        minHeapHeadroomMb: Int,
        reportMinHeapUsagePercent: Int,
        reportDeferHeapUsagePercent: Int,
    ) {
        val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes.coerceAtLeast(0L))
        val heapHeadroomBytes = if (heapMaxBytes > 0L) {
            heapMaxBytes - normalizedHeapUsedBytes
        } else {
            0L
        }
        val action = determineMemoryPressureDeviceReportAction(
            enabled = deviceReportEnabled,
            alreadyReported = memoryPressureDeviceReportedThisSession.value,
            hasPendingSnapshot = pendingMemoryPressureDeviceSnapshot.get() != null,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            reportingHeadroomBytes = heapHeadroomBytes,
            reportMinHeapUsagePercent = reportMinHeapUsagePercent,
            reportDeferHeapUsagePercent = reportDeferHeapUsagePercent,
            minReportingHeadroomBytes = MIN_REPORTING_HEAP_HEADROOM_MB.mebibytes,
        )
        when (action) {
            MemoryPressureDeviceReportAction.NONE -> Unit
            MemoryPressureDeviceReportAction.CACHE -> {
                val snapshot = buildMemoryPressureDeviceSnapshot(
                    context = context,
                    triggerReason = "deferred_high_heap_pressure",
                    foreground = isAppInForeground,
                    systemLowMemory = isSystemLowMemory,
                    guardEnabled = guardEnabled,
                    maxHeapUsagePercent = maxHeapUsagePercent,
                    minHeapHeadroomMb = minHeapHeadroomMb,
                    reportMinHeapUsagePercent = reportMinHeapUsagePercent,
                    reportDeferHeapUsagePercent = reportDeferHeapUsagePercent,
                    heapUsedBytes = heapUsedBytes,
                    heapMaxBytes = heapMaxBytes,
                )
                cachePeakMemoryPressureSnapshot(snapshot)
            }
            MemoryPressureDeviceReportAction.REPORT_NOW -> {
                val snapshot = buildMemoryPressureDeviceSnapshot(
                    context = context,
                    triggerReason = "high_heap_pressure",
                    foreground = isAppInForeground,
                    systemLowMemory = isSystemLowMemory,
                    guardEnabled = guardEnabled,
                    maxHeapUsagePercent = maxHeapUsagePercent,
                    minHeapHeadroomMb = minHeapHeadroomMb,
                    reportMinHeapUsagePercent = reportMinHeapUsagePercent,
                    reportDeferHeapUsagePercent = reportDeferHeapUsagePercent,
                    heapUsedBytes = heapUsedBytes,
                    heapMaxBytes = heapMaxBytes,
                )
                reportMemoryPressureDeviceSnapshot(snapshot)
            }
            MemoryPressureDeviceReportAction.FLUSH_PENDING -> {
                pendingMemoryPressureDeviceSnapshot.get()?.let { snapshot ->
                    reportMemoryPressureDeviceSnapshot(snapshot.copy(triggerReason = "deferred_high_heap_pressure_flush"))
                }
            }
        }
    }

    private fun cachePeakMemoryPressureSnapshot(snapshot: PreloadMemoryPressureDeviceSnapshot) {
        while (true) {
            val previous = pendingMemoryPressureDeviceSnapshot.get()
            if (previous != null && previous.heapUsagePercent >= snapshot.heapUsagePercent) {
                LogHub.info(TAG) {
                    "keep cached memory pressure device report: " +
                        "cachedHeapUsagePercent=${previous.heapUsagePercent}, " +
                        "newHeapUsagePercent=${snapshot.heapUsagePercent}"
                }
                return
            }
            if (pendingMemoryPressureDeviceSnapshot.compareAndSet(previous, snapshot)) {
                LogHub.info(TAG) {
                    "cache memory pressure device report until heap is safer: " +
                        "heapUsagePercent=${snapshot.heapUsagePercent}, " +
                        "heapHeadroomMb=${snapshot.heapHeadroomMb}"
                }
                return
            }
        }
    }

    private fun reportMemoryPressureDeviceSnapshot(snapshot: PreloadMemoryPressureDeviceSnapshot) {
        if (!memoryPressureDeviceReportedThisSession.compareAndSet(expect = false, update = true)) {
            LogHub.info(TAG) {
                "drop memory pressure device report: already reported this session"
            }
            return
        }
        pendingMemoryPressureDeviceSnapshot.set(null)
        LogHub.info(TAG) {
            "send memory pressure device report: " +
                "triggerReason=${snapshot.triggerReason}, " +
                "heapUsagePercent=${snapshot.heapUsagePercent}, " +
                "heapHeadroomMb=${snapshot.heapHeadroomMb}, " +
                "model=${snapshot.manufacturer}/${snapshot.model}"
        }
        runCatching {
            PreloadMemoryPressureDeviceReporter(snapshot).analyze()
        }.onFailure { throwable ->
            LogHub.warn(throwable = throwable, tag = TAG) {
                "memory pressure device report failed"
            }
        }
    }

    private fun buildMemoryPressureDeviceSnapshot(
        context: Context,
        triggerReason: String,
        foreground: Boolean,
        systemLowMemory: Boolean,
        guardEnabled: Boolean,
        maxHeapUsagePercent: Int,
        minHeapHeadroomMb: Int,
        reportMinHeapUsagePercent: Int,
        reportDeferHeapUsagePercent: Int,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
    ): PreloadMemoryPressureDeviceSnapshot {
        val normalizedHeapUsedBytes = if (heapMaxBytes > 0L) {
            heapUsedBytes.coerceIn(0L, heapMaxBytes)
        } else {
            heapUsedBytes.coerceAtLeast(0L)
        }
        val heapHeadroomBytes = if (heapMaxBytes > 0L) heapMaxBytes - normalizedHeapUsedBytes else 0L
        val heapUsagePercent = if (heapMaxBytes > 0L) {
            (normalizedHeapUsedBytes * 100 / heapMaxBytes).toInt()
        } else {
            -1
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = activityManager?.let {
            runCatching {
                ActivityManager.MemoryInfo().also(it::getMemoryInfo)
            }.getOrNull()
        }
        val displayMetrics = context.resources.displayMetrics
        val appLargeHeap = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val pressureSignals = CriticalPressureBreaker.peekCachedSignals()
        return PreloadMemoryPressureDeviceSnapshot(
            triggerReason = triggerReason,
            foreground = foreground,
            systemLowMemory = systemLowMemory,
            guardEnabled = guardEnabled,
            guardMaxHeapUsagePercent = maxHeapUsagePercent,
            guardMinHeapHeadroomMb = minHeapHeadroomMb,
            reportMinHeapUsagePercent = reportMinHeapUsagePercent,
            reportDeferHeapUsagePercent = reportDeferHeapUsagePercent,
            heapUsagePercent = heapUsagePercent,
            heapUsedMb = normalizedHeapUsedBytes.toMiB(),
            heapMaxMb = heapMaxBytes.toMiB(),
            heapHeadroomMb = heapHeadroomBytes.toMiB(),
            runtimeMaxHeapMb = Runtime.getRuntime().maxMemory().toMiB(),
            systemTotalRamMb = memoryInfo?.totalMem?.toMiB(),
            systemAvailRamMb = memoryInfo?.availMem?.toMiB(),
            systemLowMemoryThresholdMb = memoryInfo?.threshold?.toMiB(),
            appLargeHeap = appLargeHeap,
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(","),
            screenDensity = displayMetrics.density.toDouble(),
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels,
            pressureThreads = pressureSignals?.threads?.takeIf { it >= 0 },
            pressureNativeHeapMb = pressureSignals?.nativeHeapAllocatedBytes?.takeIf { it >= 0L }?.toMiB(),
            pressureLastTrimLevel = pressureSignals?.lastTrimLevel?.takeIf { it >= 0 },
            pressureBlockingGcDelta = pressureSignals?.blockingGcDelta60s?.takeIf { it >= 0 },
        )
    }

    private fun Context.isLowMemory(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: run {
                LogHub.warn(TAG) {
                    "ActivityManager is unavailable; treating system low memory as false"
                }
                return false
            }
        return runCatching {
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo).lowMemory
        }.onFailure { throwable ->
            LogHub.warn(throwable = throwable, tag = TAG) {
                "Failed to read ActivityManager.MemoryInfo.lowMemory"
            }
        }.getOrDefault(false)
    }

    private fun notifyMemoryPressure(
        level: PoseidonMemoryPressureLevel,
        foreground: Boolean,
        systemLowMemory: Boolean,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
    ) {
        val listener = PoseidonSetupProcessor.getInstance().memoryPressureListenerRef.get() ?: return
        val now = SystemClock.elapsedRealtime()
        if (!claimMemoryPressureNotifySlot(level, now)) {
            LogHub.info(TAG) {
                "suppress host memory pressure callback: level=$level, cooldown active"
            }
            return
        }
        val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
        val event = PoseidonMemoryPressureEvent(
            level = level,
            foreground = foreground,
            systemLowMemory = systemLowMemory,
            heapUsagePercent = (normalizedHeapUsedBytes * 100 / heapMaxBytes).toInt(),
            heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes,
        )
        runOnMainThread {
            runCatching {
                listener.onMemoryPressure(event)
            }.onFailure { throwable ->
                LogHub.warn(throwable = throwable, tag = TAG) {
                    "Host memory pressure listener failed"
                }
            }
        }
    }

    private fun claimMemoryPressureNotifySlot(
        level: PoseidonMemoryPressureLevel,
        now: Long,
    ): Boolean = synchronized(memoryPressureNotifyLock) {
        val previousLevel = lastNotifiedMemoryPressureLevel
        val previousTime = lastMemoryPressureNotifyElapsedRealtime
        val due = previousLevel == null ||
            previousLevel != level ||
            now < previousTime ||
            now - previousTime >= MEMORY_PRESSURE_NOTIFY_INTERVAL_MILLIS
        if (!due) return@synchronized false

        lastNotifiedMemoryPressureLevel = level
        lastMemoryPressureNotifyElapsedRealtime = now
        true
    }

    private fun resetMemoryPressureNotificationIfRecovered(
        heapUsedBytes: Long,
        heapMaxBytes: Long,
    ) = synchronized(memoryPressureNotifyLock) {
        val previousLevel = lastNotifiedMemoryPressureLevel ?: return@synchronized
        if (!hasRecoveredFromMemoryPressure(heapUsedBytes, heapMaxBytes)) return@synchronized

        LogHub.info(TAG) {
            "host memory pressure callback re-armed after recovery: " +
                "previousLevel=$previousLevel, " +
                heapSnapshot(heapUsedBytes, heapMaxBytes)
        }
        lastNotifiedMemoryPressureLevel = null
        lastMemoryPressureNotifyElapsedRealtime = 0L
    }
}

internal fun isSkipReportDue(now: Long, previous: Long, intervalMillis: Long): Boolean {
    return previous == 0L || now < previous || now - previous >= intervalMillis
}

internal fun backgroundPreloadGuardEnabled(remoteValue: String): Boolean {
    return remoteValue.toBooleanStrictOrNull() ?: true
}

internal fun evaluateMemoryPressureLevel(
    enabled: Boolean,
    isSystemLowMemory: Boolean,
    heapUsedBytes: Long,
    heapMaxBytes: Long,
    maxHeapUsagePercent: Int = PreloadMemoryGuard.DEFAULT_MAX_HEAP_USAGE_PERCENT,
    minHeapHeadroomBytes: Long = PreloadMemoryGuard.DEFAULT_MIN_HEAP_HEADROOM_MB.mebibytes,
    criticalHeapUsagePercent: Int = PreloadMemoryGuard.CRITICAL_HEAP_USAGE_PERCENT,
    criticalMinHeapHeadroomBytes: Long = PreloadMemoryGuard.CRITICAL_HEAP_HEADROOM_MB.mebibytes,
): PoseidonMemoryPressureLevel? {
    if (!enabled || heapMaxBytes <= 0L) return null

    val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
    val heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes
    val criticalUsageThresholdBytes = heapMaxBytes * criticalHeapUsagePercent / 100
    val criticalHeadroomBytes = min(
        criticalMinHeapHeadroomBytes,
        heapMaxBytes * (100 - criticalHeapUsagePercent) / 100,
    )
    if (isSystemLowMemory ||
        normalizedHeapUsedBytes >= criticalUsageThresholdBytes ||
        heapHeadroomBytes <= criticalHeadroomBytes
    ) {
        return PoseidonMemoryPressureLevel.CRITICAL
    }

    val usageThresholdBytes = heapMaxBytes * maxHeapUsagePercent.coerceIn(1, 100) / 100
    val effectiveMinHeadroomBytes = min(
        max(0L, minHeapHeadroomBytes),
        heapMaxBytes * 20 / 100,
    )
    return if (normalizedHeapUsedBytes >= usageThresholdBytes ||
        heapHeadroomBytes <= effectiveMinHeadroomBytes
    ) {
        PoseidonMemoryPressureLevel.LOW
    } else {
        null
    }
}

internal fun hasRecoveredFromMemoryPressure(
    heapUsedBytes: Long,
    heapMaxBytes: Long,
): Boolean {
    if (heapMaxBytes <= 0L) return false
    val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
    val heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes
    val heapUsagePercent = normalizedHeapUsedBytes * 100 / heapMaxBytes
    return heapUsagePercent <= PreloadMemoryGuard.LOW_MEMORY_PRESSURE_RECOVERY_HEAP_USAGE_PERCENT &&
        heapHeadroomBytes >= PreloadMemoryGuard.LOW_MEMORY_PRESSURE_RECOVERY_HEAP_HEADROOM_MB.mebibytes
}

internal enum class MemoryPressureDeviceReportAction {
    NONE,
    REPORT_NOW,
    CACHE,
    FLUSH_PENDING,
}

internal fun determineMemoryPressureDeviceReportAction(
    enabled: Boolean,
    alreadyReported: Boolean,
    hasPendingSnapshot: Boolean,
    heapUsedBytes: Long,
    heapMaxBytes: Long,
    reportingHeadroomBytes: Long,
    reportMinHeapUsagePercent: Int = PreloadMemoryGuard.DEFAULT_DEVICE_REPORT_MIN_HEAP_USAGE_PERCENT,
    reportDeferHeapUsagePercent: Int = PreloadMemoryGuard.DEFAULT_DEVICE_REPORT_DEFER_HEAP_USAGE_PERCENT,
    minReportingHeadroomBytes: Long = PreloadMemoryGuard.MIN_REPORTING_HEAP_HEADROOM_MB.mebibytes,
): MemoryPressureDeviceReportAction {
    if (!enabled || alreadyReported || heapMaxBytes <= 0L) {
        return MemoryPressureDeviceReportAction.NONE
    }
    val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
    val heapUsagePercent = normalizedHeapUsedBytes * 100 / heapMaxBytes
    val safeToReport = reportingHeadroomBytes >= minReportingHeadroomBytes
    val minPercent = reportMinHeapUsagePercent.coerceIn(1, 99)
    val deferPercent = reportDeferHeapUsagePercent.coerceIn(minPercent, 99)

    if (heapUsagePercent > deferPercent) {
        return MemoryPressureDeviceReportAction.CACHE
    }
    if (hasPendingSnapshot) {
        return if (safeToReport) {
            MemoryPressureDeviceReportAction.FLUSH_PENDING
        } else {
            MemoryPressureDeviceReportAction.NONE
        }
    }
    if (heapUsagePercent >= minPercent) {
        return if (safeToReport) {
            MemoryPressureDeviceReportAction.REPORT_NOW
        } else {
            MemoryPressureDeviceReportAction.CACHE
        }
    }
    return MemoryPressureDeviceReportAction.NONE
}

internal val Int.mebibytes: Long
    get() = toLong() * 1024L * 1024L

private fun Long.toMiB(): Long {
    return this / 1024L / 1024L
}

private fun heapSnapshot(heapUsedBytes: Long, heapMaxBytes: Long): String {
    if (heapMaxBytes <= 0L) {
        return "heapUsed=${heapUsedBytes.toMiBString()}, heapMax=${heapMaxBytes.toMiBString()}, heapUsagePercent=unknown"
    }
    val normalizedHeapUsedBytes = heapUsedBytes.coerceIn(0L, heapMaxBytes)
    val heapHeadroomBytes = heapMaxBytes - normalizedHeapUsedBytes
    val heapUsagePercent = normalizedHeapUsedBytes * 100 / heapMaxBytes
    return "heapUsed=${normalizedHeapUsedBytes.toMiBString()}, " +
        "heapMax=${heapMaxBytes.toMiBString()}, " +
        "heapHeadroom=${heapHeadroomBytes.toMiBString()}, " +
        "heapUsagePercent=$heapUsagePercent%"
}

private fun Long.toMiBString(): String {
    return "${this / 1024L / 1024L}MiB"
}

internal fun backgroundLowMemoryPreloadMessage(sceneName: String): String {
    return "${BACKGROUND_LOW_MEMORY_PRELOAD_MESSAGE}:sceneName:${sceneName}, code=${ERROR_CODE_BACKGROUND_LOW_MEMORY_PRELOAD}"
}
