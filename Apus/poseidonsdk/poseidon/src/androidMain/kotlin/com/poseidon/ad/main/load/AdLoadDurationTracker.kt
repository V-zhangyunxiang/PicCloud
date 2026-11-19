package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.TimeHelper

internal data class AdLoadDurations(
    val duration: Long,
    val preRequestDuration: Long,
    val requestDuration: Long,
    val afterRequestDuration: Long,
    val trafficControlDuration: Long?,
)

/** Records timing only; it never completes or suppresses an ad callback. */
internal class AdLoadDurationTracker(private val startedAt: Long) {
    private var waitingAt: Long? = null
    private var releasedAt: Long? = null
    private var requestStartedAt: Long? = null
    private var requestReturnedAt: Long? = null
    private var completed = false

    @Synchronized
    fun markWaiting(now: Long = TimeHelper.currentTimeInMillis()) {
        if (waitingAt == null && !completed) {
            waitingAt = now
        }
    }

    @Synchronized
    fun markReleased(now: Long = TimeHelper.currentTimeInMillis()) {
        if (waitingAt != null && releasedAt == null && !completed) {
            releasedAt = now
        }
    }

    @Synchronized
    fun markRequestStarted(now: Long = TimeHelper.currentTimeInMillis()) {
        if (requestStartedAt == null && !completed) {
            requestStartedAt = now
        }
    }

    @Synchronized
    fun markRequestReturned(now: Long = TimeHelper.currentTimeInMillis()) {
        if (requestStartedAt != null && requestReturnedAt == null && !completed) {
            requestReturnedAt = now
        }
    }

    /** Later callbacks, including automatic refreshes, have no new observable request start. */
    @Synchronized
    fun finish(now: Long = TimeHelper.currentTimeInMillis()): AdLoadDurations? {
        if (completed) {
            return null
        }
        completed = true
        val waitingDuration = waitingAt?.let { (releasedAt ?: now) - it }?.coerceAtLeast(0L)
        val requestStart = requestStartedAt
        val requestReturn = requestReturnedAt
        val pre = ((requestStart ?: now) - startedAt - (waitingDuration ?: 0L)).coerceAtLeast(0L)
        val request = if (requestStart != null) {
            ((requestReturn ?: now) - requestStart).coerceAtLeast(0L)
        } else {
            0L
        }
        val after = requestReturn?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return AdLoadDurations(
            duration = pre + request + after,
            preRequestDuration = pre,
            requestDuration = request,
            afterRequestDuration = after,
            trafficControlDuration = waitingDuration,
        )
    }
}
