package com.poseidon.ad.core.processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns mediation initialization work independently from generic SDK background work.
 *
 * TopOn, AdMob, and MAX may initialize concurrently, while the limited view prevents mediation
 * startup from consuming an unbounded number of IO workers.
 */
internal object MediationInitDispatcher {
    private const val PARALLELISM = 3

    private val dispatcher = Dispatchers.IO.limitedParallelism(PARALLELISM)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun dispatch(action: () -> Unit): Job {
        return scope.launch { action() }
    }
}
