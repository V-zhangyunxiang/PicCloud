package com.poseidon.ad.restrict.new

import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.common.storage.sp.SpHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 管理 SDK 内部定义的“新用户时间轴”，负责首开时间记录与首日/次日判断。
 * Tracks the first open timestamp and exposes helper queries for new-user windows.
 */
internal class NewUserTimelineTracker(private val sharedPreference: SpHelper) {

    private val startupFirstOpenCandidate = System.currentTimeMillis()
    private val resolvedFirstOpenTime = AtomicLong(UNKNOWN_TS)
    private val hydrationStarted = AtomicBoolean(false)
    private val hydrationResult = CompletableDeferred<Long?>()

    /**
     * Returns only the in-memory value. Disk hydration is deliberately never triggered by a getter.
     */
    fun getFirstOpenTime(): Long {
        return resolvedFirstOpenTime.get()
    }

    /**
     * Reads and, when necessary, creates the first-open timestamp. The caller owns the background
     * dispatcher; this method must never be invoked from a synchronous main-thread path.
     */
    fun hydrate() {
        if (!hydrationStarted.compareAndSet(false, true)) {
            return
        }

        val persisted = runCatching {
            sharedPreference.getLong(FIRST_OPEN_KEY, UNKNOWN_TS)
        }
            .onFailure { throwable ->
                logWarn(throwable, TAG) {
                    "Failed to read first open timestamp: ${throwable.message}"
                }
            }
            .getOrNull()

        if (persisted == null) {
            hydrationResult.complete(null)
            return
        }

        val resolved = if (persisted > 0L) {
            persisted
        } else {
            startupFirstOpenCandidate.also { timestamp ->
                runCatching { sharedPreference.putLong(FIRST_OPEN_KEY, timestamp) }
                    .onSuccess {
                        logInfo(TAG) { "Recorded firstOpenTime=$timestamp" }
                    }
                    .onFailure { throwable ->
                        logWarn(throwable, TAG) {
                            "Failed to persist first open timestamp: ${throwable.message}"
                        }
                    }
            }
        }

        resolvedFirstOpenTime.set(resolved)
        hydrationResult.complete(resolved)
    }

    /** Waits without blocking a thread. Intended only for work already running asynchronously. */
    suspend fun awaitFirstOpenTime(): Long? {
        return hydrationResult.await()
    }

    fun inFirstDayAfterInstall(): Boolean {
        return dayIndexSinceFirstOpen() == 0
    }

    fun inNextDayAfterInstall(): Boolean {
        return dayIndexSinceFirstOpen() == 1
    }

    private fun dayIndexSinceFirstOpen(now: Long = System.currentTimeMillis()): Int {
        val firstOpenTime = getFirstOpenTime()
        if (firstOpenTime <= 0L) {
            return UNKNOWN_DAY_INDEX
        }
        val delta = (now - firstOpenTime).coerceAtLeast(0L)
        return (delta / MILLIS_IN_DAY).toInt()
    }

    companion object {
        private const val TAG = "NewUserTimeline"
        private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L
        private const val UNKNOWN_TS = -1L
        private const val UNKNOWN_DAY_INDEX = -1
        private val FIRST_OPEN_KEY = KeyProvider {
            "Poseidon_NewUserFirstOpenTimestamp"
        }
    }
}
