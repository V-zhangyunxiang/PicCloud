package com.poseidon.ad.restrict.content

import kotlinx.atomicfu.locks.withLock
import java.util.concurrent.locks.ReentrantLock

/**
 * 页面上下文控制器，负责串行化访问与状态切换。
 */
internal class PageContextController {
    private val lock = ReentrantLock()
    private val state = PageContextState()

    /**
     * 在指定页面上下文中执行逻辑。
     */
    fun <T> withPage(pageName: String, block: PageContextState.() -> T): T {
        return lock.withLock {
            state.update(pageName)
            state.block()
        }
    }

    /**
     * 清空所有上下文状态。
     */
    fun reset() {
        lock.withLock { state.clear() }
    }
}

/**
 * 页面上下文状态，记录最新与上一次的页面及统计。
 */
internal class PageContextState(
    var lastPage: String? = null,
    var currentPage: String? = null,
    val lastStats: PageAdStat = PageAdStat(),
    val currentStats: PageAdStat = PageAdStat()
) {

    /**
     * 在页面切换时更新上下文并输出必要日志。
     */
    fun update(pageName: String) {
        val current = currentPage
        if (current == null) {
            currentPage = pageName
            currentStats.reset()
            ContentBalanceLogger.pageInitialized(pageName)
            return
        }

        if (current != pageName) {
            ContentBalanceLogger.pageTransition(current, pageName, currentStats.total)
            lastPage = current
            lastStats.copyFrom(currentStats)
            currentPage = pageName
            currentStats.reset()
        }
    }

    /**
     * 清理所有上下文字段。
     */
    fun clear() {
        lastPage = null
        currentPage = null
        lastStats.reset()
        currentStats.reset()
    }
}
