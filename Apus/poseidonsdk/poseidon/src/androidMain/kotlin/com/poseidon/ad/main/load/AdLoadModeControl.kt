package com.poseidon.ad.main.load

import android.os.Environment
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.common.toolkit.runDelayed
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.main.strategy.limit.load.mode.AdLoadModeControlCfg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File

private typealias Loadable = () -> Unit

internal object AdLoadModeControl {

    private const val TAG = "AdLoadModeControl"

    private var runningSessionIdInSerialQueue: String? = null
    private val serialLoadQueue: ArrayDeque<Pair<String, Loadable>> = ArrayDeque()

    private var anyAdIsLoading: Boolean = false

    @MainThread
    fun checking(sessionId: String, adUnitId: String, onWaiting: () -> Unit = {}, loadable: Loadable) {
        val outcome = evaluateAdLoadTrafficOutcome(
            cfg = AdLoadModeControlCfg.get(),
            adUnitId = adUnitId,
        )
        when (outcome) {
            is AdLoadTrafficOutcome.RunImmediately -> loadable()
            is AdLoadTrafficOutcome.EnqueueSerial -> serialLoad(sessionId = sessionId, onWaiting = onWaiting, loadable = loadable)
            is AdLoadTrafficOutcome.ScheduleDelay -> delayLoad(delayMillis = outcome.delayMillis, onWaiting = onWaiting, loadable = loadable)
        }
    }

    private val delayRunTasks = mutableListOf<DelayRunTask>()

    @MainThread
    private fun delayLoad(delayMillis: Long, onWaiting: () -> Unit, loadable: Loadable) {
        if (anyAdIsLoading) {
            onWaiting()
            DelayRunTask(delayMillis = delayMillis, loadable = loadable).run()
        } else {
            loadable()
        }
    }

    private class DelayRunTask(
        private val delayMillis: Long,
        private val loadable: Loadable,
    ) {
        private var job: Job? = null

        @MainThread
        fun run() {
            delayRunTasks.add(this)
            job = runDelayed(context = Dispatchers.Main, delayMillis = delayMillis) {
                delayRunTasks.remove(this)
                job = null
                loadable()
            }
        }

        @MainThread
        fun wakeup() {
            if (job == null) {
                logWarn(tag = TAG) { "wakeup: job is null, this is a bug!" }
            }

            job?.cancel()
            loadable()
        }
    }

    @AnyThread
    fun anyAdLoadStart(sessionId: String, adUnitId: String) {
        logInfo(TAG) {
            "======> anyAdLoadStart: sessionId=$sessionId, adUnitId=$adUnitId, runningSessionIdInSerialQueue=$runningSessionIdInSerialQueue, delayRunTasks=$delayRunTasks"
        }
        anyAdIsLoading = true
    }

    @AnyThread
    fun anyAdLoadComplete(sessionId: String) = runOnMainThread {
        anyAdIsLoading = false
        with(delayRunTasks.iterator()) {
            while (hasNext()) {
                next().wakeup()
                remove()
            }
        }
        logInfo(TAG) {
            "anyAdLoadComplete: sessionId=$sessionId, runningSessionIdInSerialQueue=$runningSessionIdInSerialQueue"
        }
        if (runningSessionIdInSerialQueue == sessionId) {
            runningSessionIdInSerialQueue = null
            tryLoadFirstAdOfSerialQueue()
        }
    }

    @MainThread
    private fun tryLoadFirstAdOfSerialQueue() {
        if (runningSessionIdInSerialQueue != null) {
            logInfo(TAG) { "tryLoadFirstAdOfSerialQueue: Not load, runningSessionIdInSerialQueue($runningSessionIdInSerialQueue) is not null" }
        } else {
            val first = serialLoadQueue.removeFirstOrNull()
            if (first != null) {
                val sessionId = first.first
                val loadable = first.second
                logInfo(TAG) { "tryLoadFirstAdOfSerialQueue: Load, sessionId=$sessionId, loadable=$loadable" }

                runningSessionIdInSerialQueue = sessionId
                loadable.invoke()
            } else {
                logInfo(TAG) { "tryLoadFirstAdOfSerialQueue: Not load, serialLoadQueue is empty" }
            }
        }
    }

    @MainThread
    private fun serialLoad(sessionId: String, onWaiting: () -> Unit, loadable: Loadable) {
        logInfo(TAG) {
            "serialLoad: sessionId=$sessionId, loadable=$loadable"
        }
        if (runningSessionIdInSerialQueue != null) {
            onWaiting()
        }
        serialLoadQueue.addLast(sessionId to loadable)
        tryLoadFirstAdOfSerialQueue()
    }
}

internal enum class AdLoadTrafficControlMode(val value: String) {
    SERIAL(value = "serial"),
    DELAY(value = "delay"),
    IMMEDIATE(value = "immediate"),
}

fun getAdLoadModeControlLocalConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}
