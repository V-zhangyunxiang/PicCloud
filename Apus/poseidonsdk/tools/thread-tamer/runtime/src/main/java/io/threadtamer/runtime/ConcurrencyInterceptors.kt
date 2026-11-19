package io.threadtamer.runtime

import android.os.HandlerThread
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Static ABI used exclusively by bytecode emitted by the companion Gradle plugin. */
object ConcurrencyInterceptors {
    @JvmStatic fun ignite(worker: Thread) = ConcurrencyEngine.launch(worker)

    @JvmStatic fun forgeThread(): Thread = ConcurrencyEngine.thread()
    @JvmStatic fun forgeThread(job: Runnable): Thread = ConcurrencyEngine.thread(job)
    @JvmStatic fun forgeThread(label: String): Thread = ConcurrencyEngine.thread(label)
    @JvmStatic fun forgeThread(job: Runnable, label: String): Thread = ConcurrencyEngine.thread(job, label)
    @JvmStatic fun forgeThread(team: ThreadGroup, job: Runnable): Thread = ConcurrencyEngine.thread(team, job)
    @JvmStatic fun forgeThread(team: ThreadGroup, label: String): Thread = ConcurrencyEngine.thread(team, label)
    @JvmStatic fun forgeThread(team: ThreadGroup, job: Runnable, label: String): Thread =
        ConcurrencyEngine.thread(team, job, label)
    @JvmStatic fun forgeThread(team: ThreadGroup, job: Runnable, label: String, stackBytes: Long): Thread =
        ConcurrencyEngine.thread(team, job, label, stackBytes)

    @JvmStatic fun forgeLooperThread(label: String): HandlerThread = ConcurrencyEngine.looperThread(label)
    @JvmStatic fun forgeLooperThread(label: String, importance: Int): HandlerThread =
        ConcurrencyEngine.looperThread(label, importance)

    @JvmStatic fun cachedWorkers(): ExecutorService = ConcurrencyEngine.cachedExecutor(null)
    @JvmStatic fun cachedWorkers(maker: ThreadFactory): ExecutorService = ConcurrencyEngine.cachedExecutor(maker)
    @JvmStatic fun fixedWorkers(width: Int): ExecutorService = ConcurrencyEngine.fixedExecutor(width, null)
    @JvmStatic fun fixedWorkers(width: Int, maker: ThreadFactory): ExecutorService =
        ConcurrencyEngine.fixedExecutor(width, maker)
    @JvmStatic fun singleWorker(): ExecutorService = ConcurrencyEngine.serialExecutor(null)
    @JvmStatic fun singleWorker(maker: ThreadFactory): ExecutorService = ConcurrencyEngine.serialExecutor(maker)
    @JvmStatic fun scheduledWorkers(width: Int): ScheduledExecutorService =
        ConcurrencyEngine.scheduledExecutor(width, null)
    @JvmStatic fun scheduledWorkers(width: Int, maker: ThreadFactory): ScheduledExecutorService =
        ConcurrencyEngine.scheduledExecutor(width, maker)
    @JvmStatic fun singleScheduler(): ScheduledExecutorService = ConcurrencyEngine.serialScheduler(null)
    @JvmStatic fun singleScheduler(maker: ThreadFactory): ScheduledExecutorService =
        ConcurrencyEngine.serialScheduler(maker)
    @JvmStatic fun workStealers(): ExecutorService = ConcurrencyEngine.stealingExecutor(null)
    @JvmStatic fun workStealers(width: Int): ExecutorService = ConcurrencyEngine.stealingExecutor(width)

    @JvmStatic
    fun forgeExecutor(
        coreWidth: Int,
        maximumWidth: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>
    ): ThreadPoolExecutor = ConcurrencyEngine.rawExecutor(
        coreWidth, maximumWidth, idleDuration, idleUnit, backlog, null, ThreadPoolExecutor.AbortPolicy()
    )

    @JvmStatic
    fun forgeExecutor(
        coreWidth: Int,
        maximumWidth: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>,
        maker: ThreadFactory
    ): ThreadPoolExecutor = ConcurrencyEngine.rawExecutor(
        coreWidth, maximumWidth, idleDuration, idleUnit, backlog, maker, ThreadPoolExecutor.AbortPolicy()
    )

    @JvmStatic
    fun forgeExecutor(
        coreWidth: Int,
        maximumWidth: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>,
        refusal: RejectedExecutionHandler
    ): ThreadPoolExecutor = ConcurrencyEngine.rawExecutor(
        coreWidth, maximumWidth, idleDuration, idleUnit, backlog, null, refusal
    )

    @JvmStatic
    fun forgeExecutor(
        coreWidth: Int,
        maximumWidth: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>,
        maker: ThreadFactory,
        refusal: RejectedExecutionHandler
    ): ThreadPoolExecutor = ConcurrencyEngine.rawExecutor(
        coreWidth, maximumWidth, idleDuration, idleUnit, backlog, maker, refusal
    )

    @JvmStatic fun forgeScheduledExecutor(width: Int): ScheduledThreadPoolExecutor =
        ConcurrencyEngine.rawScheduler(width, null, ThreadPoolExecutor.AbortPolicy())
    @JvmStatic fun forgeScheduledExecutor(width: Int, maker: ThreadFactory): ScheduledThreadPoolExecutor =
        ConcurrencyEngine.rawScheduler(width, maker, ThreadPoolExecutor.AbortPolicy())
    @JvmStatic fun forgeScheduledExecutor(width: Int, refusal: RejectedExecutionHandler): ScheduledThreadPoolExecutor =
        ConcurrencyEngine.rawScheduler(width, null, refusal)
    @JvmStatic fun forgeScheduledExecutor(
        width: Int,
        maker: ThreadFactory,
        refusal: RejectedExecutionHandler
    ): ScheduledThreadPoolExecutor = ConcurrencyEngine.rawScheduler(width, maker, refusal)
}
