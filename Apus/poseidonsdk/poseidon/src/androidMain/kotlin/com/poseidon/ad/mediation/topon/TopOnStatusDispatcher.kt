package com.poseidon.ad.mediation.topon

import com.poseidon.ad.common.toolkit.logWarn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns bounded-parallel TopOn status resolution outside TopOn and Android main-thread callbacks.
 *
 * Status resolution and bid extraction for different ads are independent. Parallelism prevents one
 * slow extraction from blocking every TopOn format while keeping the SDK's thread footprint bounded.
 */
internal object TopOnStatusDispatcher {
    private const val PARALLELISM = 6
    private const val THREAD_NAME_PREFIX = "PoseidonTopOnStatus"
    private val threadNumber = AtomicInteger(0)

    private val dispatcher: CoroutineDispatcher =
        Executors.newFixedThreadPool(PARALLELISM) { runnable ->
            Thread(runnable, "$THREAD_NAME_PREFIX-${threadNumber.incrementAndGet()}")
        }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun dispatch(action: () -> Unit): Job {
        return scope.launch {
            runCatching(action).onFailure { throwable ->
                logWarn(throwable = throwable, tag = THREAD_NAME_PREFIX) {
                    "TopOn status task failed: ${throwable.message}"
                }
            }
        }
    }
}
