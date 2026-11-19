package com.poseidon.ad.common.toolkit

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

private const val MAIN_THREAD_ERROR_MESSAGE = "Must be called from the main thread."

/** 获取当前线程名称 */
fun currentThreadName(): String? = ThreadGuards.currentThreadName()

/** 判断当前是否在主线程 */
fun isMainThread(): Boolean = ThreadGuards.isMainThread()

/** 校验当前执行环境为主线程 */
fun checkMainThread(lazyMessage: (() -> Any)? = null) {
    ThreadGuards.ensureMainThread(lazyMessage)
}

/** 将任务派发到 IO 后台线程执行 */
fun dispatchToBackground(action: suspend () -> Unit): Job {
    return CoroutineScope(Dispatchers.IO).launch { action() }
}

/** 在 IO 后台线程延迟执行任务。 */
fun postDelayedToBackground(delayMillis: Long, action: suspend () -> Unit): Job {
    require(delayMillis >= 0L) { "delayMillis must not be negative" }
    return CoroutineScope(Dispatchers.IO).launch {
        delay(delayMillis.milliseconds)
        action()
    }
}

/** 将任务投递到主线程 */
fun postOnMainThread(action: () -> Unit): Job = MainThreadExecutor.launch(action)

/** 如果在主线程立即执行，否则切换到主线程 */
fun runOnMainThread(action: () -> Unit) {
    MainThreadExecutor.runWithThreadCheck(action)
}

/** 在主线程延迟执行任务 */
fun postDelayed(delayMillis: Long, action: () -> Unit) {
    MainThreadExecutor.postDelayed(delayMillis, action)
}

/** 指定协程上下文延迟执行 */
fun runDelayed(context: CoroutineContext, delayMillis: Long, action: () -> Unit): Job {
    return AsyncDelayScheduler.schedule(context, delayMillis, action)
}

/** 以固定频率调度任务 */
fun schedule(delay: Long, period: Long, action: () -> Unit): Cancelable {
    return TimerScheduler.schedule(delay, period, action)
}

/** 线程校验工具，专门处理主线程判断 */
private object ThreadGuards {
    /** 获取当前线程名 */
    fun currentThreadName(): String? = Thread.currentThread().name

    /** 是否主线程 */
    fun isMainThread(): Boolean = Looper.getMainLooper() == Looper.myLooper()

    /** 确保运行在主线程 */
    fun ensureMainThread(lazyMessage: (() -> Any)?) {
        check(isMainThread()) { lazyMessage?.invoke() ?: MAIN_THREAD_ERROR_MESSAGE }
    }
}

/** 主线程执行器，统一主线程 Handler 与 Scope 的管理 */
private object MainThreadExecutor {
    /** 主线程 Handler，确保延迟任务在主线程触发 */
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()

    /** 发起主线程协程执行 */
    fun launch(action: () -> Unit): Job = scope.launch { action() }

    /** 根据线程状态执行或切换 */
    fun runWithThreadCheck(action: () -> Unit) {
        if (ThreadGuards.isMainThread()) {
            action()
        } else {
            launch(action)
        }
    }

    /** Handler 延迟执行 */
    fun postDelayed(delayMillis: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMillis)
    }
}

/** 通用延迟调度器，用于自定义协程上下文 */
private object AsyncDelayScheduler {
    /** 基于传入上下文创建新的协程并延迟执行 */
    fun schedule(context: CoroutineContext, delayMillis: Long, action: () -> Unit): Job {
        return CoroutineScope(context).launch {
            delay(delayMillis.milliseconds)
            action()
        }
    }
}

/** 定时调度器，封装 Timer 与 TimerTask。 */
private object TimerScheduler {
    /** 启动定时器并返回可取消对象 */
    fun schedule(delay: Long, period: Long, action: () -> Unit): Cancelable {
        require(delay >= 0L) { "delay must not be negative" }
        require(period > 0L) { "period must be positive" }
        val timer = Timer()
        val task = ActionTimerTask(action)
        timer.schedule(task, delay, period)
        return Cancelable {
            timer.cancel()
            timer.purge()
        }
    }
}

/** 封装 TimerTask 的执行动作。 */
private class ActionTimerTask(
    private val action: () -> Unit,
) : TimerTask() {
    override fun run() {
        action()
    }
}
