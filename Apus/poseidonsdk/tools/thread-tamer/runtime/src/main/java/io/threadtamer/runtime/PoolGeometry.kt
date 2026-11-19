package io.threadtamer.runtime

import java.util.concurrent.TimeUnit

internal object PoolGeometry {
    fun boundedPair(coreRequest: Int, maximumRequest: Int, ceiling: Int): Pair<Int, Int> {
        val upper = maximumRequest.coerceAtLeast(1).coerceAtMost(ceiling.coerceAtLeast(1))
        return coreRequest.coerceAtLeast(0).coerceAtMost(upper) to upper
    }

    fun positiveLifetime(
        durationRequest: Long,
        durationUnit: TimeUnit,
        defaultSeconds: Long
    ): Pair<Long, TimeUnit> = if (durationRequest > 0L) {
        durationRequest to durationUnit
    } else {
        defaultSeconds.coerceAtLeast(1L) to TimeUnit.SECONDS
    }
}
