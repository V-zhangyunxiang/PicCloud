package com.poseidon.ad.restrict.ctr

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * CTR 点击计数器，按 session 记录广告点击次数。
 */
internal object CtrClickTracker {
    private val clickCount: MutableMap<String, Int> = mutableMapOf()
    private val lock = ReentrantLock()

    /**
     * 针对指定 session 递增点击次数，并返回最新计数。
     */
    fun increment(sessionId: String): Int {
        return lock.withLock {
            val count = (clickCount[sessionId] ?: 0).inc()
            clickCount[sessionId] = count
            count
        }
    }
}
