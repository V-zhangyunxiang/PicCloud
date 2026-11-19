package com.poseidon.ad.statistics.biz

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.Reporter

internal class PreloadMemoryPressureDeviceReporter(
    private val snapshot: PreloadMemoryPressureDeviceSnapshot,
) : Reporter(eventName = EventName.PRELOAD_MEMORY_PRESSURE_DEVICE) {

//    "report_defer_heap_usage_percent" to snapshot.reportDeferHeapUsagePercent,
    // "product" to snapshot.product,
    // "guard_enabled" to snapshot.guardEnabled,
//    "supported_abis" to snapshot.supportedAbis,
    //"hardware" to snapshot.hardware,
//    "screen_density" to snapshot.screenDensity,
    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            "trigger_reason" to snapshot.triggerReason,
            "foreground" to snapshot.foreground,
            "system_low_memory" to snapshot.systemLowMemory,
            "guard_max_heap_usage_percent" to snapshot.guardMaxHeapUsagePercent,
            "guard_min_heap_headroom_mb" to snapshot.guardMinHeapHeadroomMb,
            "report_min_heap_usage_percent" to snapshot.reportMinHeapUsagePercent,
            "heap_usage_percent" to snapshot.heapUsagePercent,
            "heap_used_mb" to snapshot.heapUsedMb,
            "heap_max_mb" to snapshot.heapMaxMb,
            "heap_headroom_mb" to snapshot.heapHeadroomMb,
            "runtime_max_heap_mb" to snapshot.runtimeMaxHeapMb,
            "system_total_ram_mb" to snapshot.systemTotalRamMb,
            "system_avail_ram_mb" to snapshot.systemAvailRamMb,
            "system_low_memory_threshold_mb" to snapshot.systemLowMemoryThresholdMb,
            "app_large_heap" to snapshot.appLargeHeap,
            "android_release_sdk" to "${snapshot.androidRelease},${snapshot.androidSdkInt}",
            "manufacturer" to snapshot.manufacturer,
            "brand" to snapshot.brand,
            "model" to snapshot.model,
            "device" to snapshot.device,
            "screen_width_px" to snapshot.screenWidthPx,
            "screen_height_px" to snapshot.screenHeightPx,
            "threads" to snapshot.pressureThreads,
            "native_heap_mb" to snapshot.pressureNativeHeapMb,
            "last_trim_level" to snapshot.pressureLastTrimLevel,
            "blocking_gc_delta" to snapshot.pressureBlockingGcDelta,
        )
    }
}

internal data class PreloadMemoryPressureDeviceSnapshot(
    val triggerReason: String,
    val foreground: Boolean,
    val systemLowMemory: Boolean,
    val guardEnabled: Boolean,
    val guardMaxHeapUsagePercent: Int,
    val guardMinHeapHeadroomMb: Int,
    val reportMinHeapUsagePercent: Int,
    val reportDeferHeapUsagePercent: Int,
    val heapUsagePercent: Int,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val heapHeadroomMb: Long,
    val runtimeMaxHeapMb: Long,
    val systemTotalRamMb: Long?,
    val systemAvailRamMb: Long?,
    val systemLowMemoryThresholdMb: Long?,
    val appLargeHeap: Boolean,
    val androidSdkInt: Int,
    val androidRelease: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val supportedAbis: String,
    val screenDensity: Double,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val pressureThreads: Int?,
    val pressureNativeHeapMb: Long?,
    val pressureLastTrimLevel: Int?,
    val pressureBlockingGcDelta: Int?,
)
