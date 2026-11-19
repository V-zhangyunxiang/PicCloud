package com.poseidon.ad.api

/**
 * Callback for host apps that want to observe SDK-detected memory pressure.
 */
fun interface PoseidonMemoryPressureListener {
    fun onMemoryPressure(event: PoseidonMemoryPressureEvent)
}

enum class PoseidonMemoryPressureLevel {
    LOW,
    CRITICAL
}

data class PoseidonMemoryPressureEvent(
    val level: PoseidonMemoryPressureLevel,
    val foreground: Boolean,
    val systemLowMemory: Boolean,
    val heapUsagePercent: Int,
    val heapHeadroomBytes: Long,
)
