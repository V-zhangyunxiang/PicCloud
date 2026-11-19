package com.poseidon.ad.special

import android.os.SystemClock
import androidx.annotation.Keep
import com.poseidon.ad.common.toolkit.Cancelable
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.schedule
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Samples the process thread table, including native-only threads.
 *
 * This is intentionally backed by /proc instead of Thread.getAllStackTraces():
 * mediation SDKs can create native threads that are invisible to the Java API.
 */
@Keep
internal object ThreadCountWatcher {
    private const val TAG = "ThreadCountWatcher"
    private const val TASK_DIRECTORY = "/proc/self/task"

    private val runningSchedule = AtomicReference<Cancelable?>(null)
    private val state = AtomicReference(State())

    @Synchronized
    fun start(
        configuration: Configuration = Configuration(),
    ): Cancelable {
        runningSchedule.get()?.let { return it }

        val schedule = schedule(
            delay = 0L,
            period = configuration.samplePeriodMillis.coerceAtLeast(100L),
        ) {
            sample(configuration = configuration)
        }
        lateinit var guardedSchedule: Cancelable
        guardedSchedule = Cancelable {
            schedule.cancel()
            synchronized(this) {
                if (runningSchedule.get() === guardedSchedule) {
                    runningSchedule.set(null)
                }
            }
        }
        runningSchedule.set(guardedSchedule)
        return guardedSchedule
    }

    @Synchronized
    fun stop() {
        runningSchedule.getAndSet(null)?.cancel()
    }

    fun latest(): Sample? = state.get().latest

    private fun sample(
        configuration: Configuration,
    ) {
        val threadNames = readThreadNames()
        if (threadNames.isEmpty()) return

        val previousState = state.get()
        val totalCount = threadNames.size
        val peakCount = maxOf(previousState.peakCount, totalCount)
        val sample = Sample(
            uptimeMillis = SystemClock.uptimeMillis(),
            totalCount = totalCount,
            deltaFromPrevious = previousState.latest?.let { totalCount - it.totalCount } ?: 0,
            peakCount = peakCount,
            threadNames = if (configuration.includeThreadNames) {
                threadNames
            } else {
                emptyMap()
            },
        )
        val nextState = State(
            latest = sample,
            peakCount = peakCount,
            history = (previousState.history + sample).takeLast(configuration.maxHistorySize.coerceAtLeast(1)),
        )
        state.set(nextState)

        logInfo(TAG) {
            "process thread count: " +
                "total=${sample.totalCount}, " +
                "delta=${sample.deltaFromPrevious}, " +
                "peak=${sample.peakCount}, " +
                "threadNames=${sample.threadNameCounts()}"
        }
    }

    private fun readThreadNames(): Map<Int, String> {
        val taskDirectories = File(TASK_DIRECTORY).listFiles() ?: return emptyMap()
        val names = LinkedHashMap<Int, String>(taskDirectories.size)
        for (taskDirectory in taskDirectories) {
            val tid = taskDirectory.name.toIntOrNull() ?: continue
            val name = runCatching {
                File(taskDirectory, "comm").readText().trim()
            }.getOrDefault("")
            names[tid] = name.ifEmpty { "<unnamed>" }
        }
        return names
    }

    data class Configuration(
        val samplePeriodMillis: Long = 5_000L,
        val maxHistorySize: Int = 60,
        val includeThreadNames: Boolean = true,
    )

    data class Sample(
        val uptimeMillis: Long,
        val totalCount: Int,
        val deltaFromPrevious: Int,
        val peakCount: Int,
        val threadNames: Map<Int, String>,
    ) {
        fun threadNameCounts(): Map<String, Int> {
            return threadNames.values.groupingBy { it }.eachCount()
        }
    }

    private data class State(
        val latest: Sample? = null,
        val peakCount: Int = 0,
        val history: List<Sample> = emptyList(),
    )
}