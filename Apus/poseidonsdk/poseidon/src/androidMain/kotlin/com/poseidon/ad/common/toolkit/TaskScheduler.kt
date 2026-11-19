package com.poseidon.ad.common.toolkit

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock

/**
 * 通用任务完成协调器，确保耗时操作只执行一次，并复用结果给所有监听者。
 */
abstract class TaskScheduler<S, F> {
    private val state = atomic(STATE_NONE)
    private val listeners = mutableListOf<CompletionListener<S, F>>()
    private val lock = reentrantLock()
    private val successData = atomic<S?>(null)
    private val failureData = atomic<F?>(null)

    /**
     * 注册监听器并保证如果已有结果则立刻回调。
     */
    fun handleCompletion(listener: CompletionListener<S, F>) {
        when (state.value) {
            STATE_NONE -> handleStateNone(listener)
            STATE_PENDING -> enqueueListener(listener)
            STATE_SUCCESS -> deliverStoredSuccess(listener)
            STATE_FAILURE -> deliverStoredFailure(listener)
            else -> error("Wrong state: ${state.value}")
        }
    }

    protected abstract fun execute(listener: CompletionListener<S, F>)

    /**
     * 处理初次进入的监听：抢占状态、加入队列并触发执行。
     */
    private fun handleStateNone(listener: CompletionListener<S, F>) {
        if (!state.compareAndSet(STATE_NONE, STATE_PENDING)) {
            handleCompletion(listener)
            return
        }
        enqueueListener(listener)
        execute(createDispatchingListener())
    }

    /**
     * 将监听器加入队列，线程安全。
     */
    private fun enqueueListener(listener: CompletionListener<S, F>) {
        lock.lock()
        try {
            listeners.add(listener)
        } finally {
            lock.unlock()
        }
    }

    /**
     * 构造执行过程中的监听器，负责写入状态并通知队列。
     */
    private fun createDispatchingListener(): CompletionListener<S, F> {
        return object : CompletionListener<S, F> {
            override fun onSuccess(data: S) {
                storeSuccess(data)
                transitionState(expected = STATE_PENDING, next = STATE_SUCCESS) {
                    notifyListeners { it.onSuccess(data) }
                }
            }

            override fun onFailure(data: F) {
                storeFailure(data)
                transitionState(expected = STATE_PENDING, next = STATE_FAILURE) {
                    notifyListeners { it.onFailure(data) }
                }
            }
        }
    }

    /**
     * 缓存成功结果。
     */
    private fun storeSuccess(data: S) {
        check(successData.compareAndSet(null, data)) {
            "Success data is already set: ${successData.value}"
        }
    }

    /**
     * 缓存失败结果。
     */
    private fun storeFailure(data: F) {
        check(failureData.compareAndSet(null, data)) {
            "Failure data is already set: ${failureData.value}"
        }
    }

    /**
     * 状态跳转校验，并在成功后执行通知逻辑。
     */
    private inline fun transitionState(
        expected: Int,
        next: Int,
        crossinline afterTransition: () -> Unit
    ) {
        check(state.compareAndSet(expected, next)) {
            "State is not pending when ${if (next == STATE_SUCCESS) "success" else "failure"}: ${state.value}"
        }
        afterTransition()
    }

    /**
     * 依次通知所有监听者，并在通知后移除。
     */
    private inline fun notifyListeners(crossinline block: (CompletionListener<S, F>) -> Unit) {
        lock.lock()
        try {
            val iterator = listeners.iterator()
            while (iterator.hasNext()) {
                block(iterator.next())
                iterator.remove()
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 将已经缓存的成功数据同步给新监听。
     */
    private fun deliverStoredSuccess(listener: CompletionListener<S, F>) {
        val data = successData.value
        checkNotNull(data) { "Success data is null" }
        listener.onSuccess(data)
    }

    /**
     * 将已经缓存的失败数据同步给新监听。
     */
    private fun deliverStoredFailure(listener: CompletionListener<S, F>) {
        val data = failureData.value
        checkNotNull(data) { "Failure data is null" }
        listener.onFailure(data)
    }

    companion object {
        private const val STATE_NONE = 1
        private const val STATE_PENDING = 2
        private const val STATE_SUCCESS = 3
        private const val STATE_FAILURE = 4
    }
}

interface CompletionListener<S, F> {
    fun onSuccess(data: S)
    fun onFailure(data: F)
}
