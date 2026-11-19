package com.poseidon.ad.common.storage.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Owns ordered Room work for persisted policy and revenue state.
 *
 * A single lane preserves write-before-read ordering across frequency, show-limit, and revenue
 * operations without coupling database work to generic SDK background tasks.
 */
internal object RoomPolicyDispatcher {
    private const val THREAD_NAME = "PoseidonRoomPolicy"

    internal val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, THREAD_NAME)
        }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val contextMarker = RoomPolicyContext
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + contextMarker)
    private val operationMutex = Mutex()

    fun dispatch(action: suspend () -> Unit): Job {
        return scope.launch {
            operationMutex.withLock { action() }
        }
    }

    suspend fun <T> run(action: suspend () -> T): T {
        if (currentCoroutineContext()[RoomPolicyContext.Key] != null) {
            return action()
        }
        return operationMutex.withLock {
            withContext(dispatcher + contextMarker) { action() }
        }
    }
}

private object RoomPolicyContext : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<RoomPolicyContext>
}
