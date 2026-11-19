package io.threadtamer.runtime

import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal object ConcurrencyEngine {
    private const val LOG_LABEL = "ThreadTamer"

    private val running = AtomicBoolean(false)
    private val launchedCount = AtomicInteger()
    private val deniedCount = AtomicInteger()
    private val workerCount = AtomicInteger()
    private val executorCount = AtomicInteger()
    private val startupWorkerCount = AtomicInteger()
    private val handlerAdviceCount = AtomicInteger()
    private val namingSequence = AtomicInteger()
    private val originByThread = ConcurrentHashMap<Long, String>()
    private val firstSeenAt = ConcurrentHashMap<Long, Long>()
    private val priorThreadIds = AtomicReference<Set<Long>>(emptySet())
    private val observationJob = AtomicReference<ScheduledFuture<*>?>()
    private val observationClock = ScheduledThreadPoolExecutor(1) { action ->
        Thread(action, "thread-tamer-observer").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        setKeepAliveTime(30L, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
    }

    @Volatile
    private var limits = ConcurrencyLimits()

    @Volatile
    private var installedAt = monotonicMillis()

    private val vendorNameMarkers = listOf(
        "thinkup_", "anythink_", "Anythink", "BGAd-", "AdWorker(", "vng_",
        "ComponentExecutor", "pag_", "csj_", "GmsDynamite", "OkHttp ",
        "BidMachine", "Fyber", "AppLovin", "AudienceNetwork", "UnityAds"
    )

    private val lowPriorityFamilies = listOf(
        "com.vungle.", "com.fyber.", "io.bidmachine.", "com.smartdigimkttech.",
        "com.aggregatortech.", "com.tramini.", "com.varbto."
    )

    fun activate(replacement: ConcurrencyLimits) {
        observationJob.getAndSet(null)?.cancel(false)
        limits = replacement
        installedAt = monotonicMillis()
        launchedCount.set(0)
        deniedCount.set(0)
        workerCount.set(0)
        executorCount.set(0)
        startupWorkerCount.set(0)
        handlerAdviceCount.set(0)
        originByThread.clear()
        firstSeenAt.clear()
        priorThreadIds.set(emptySet())
        running.set(replacement.active)
        if (replacement.active && replacement.emitDiagnostics) {
            scheduleObservation(replacement)
            Log.i(LOG_LABEL, "installed notice=${replacement.warmThreadNotice} alarm=${replacement.warmThreadAlarm}")
        }
    }

    fun deactivate() {
        running.set(false)
        observationJob.getAndSet(null)?.cancel(false)
    }

    fun readSnapshot(): ConcurrencySnapshot {
        val stacks = Thread.getAllStackTraces()
        val threads = stacks.keys
        val current = limits
        val categories = threads.groupingBy { candidate -> classify(candidate, stacks[candidate].orEmpty(), current) }.eachCount()
        val observedIds = threads.mapTo(linkedSetOf()) { it.id }
        val previousIds = priorThreadIds.getAndSet(observedIds)
        val newDescriptions = threads.asSequence()
            .filter { it.id !in previousIds }
            .sortedBy { it.name }
            .take(24)
            .map { candidate ->
                val age = monotonicMillis() - firstSeenAt.computeIfAbsent(candidate.id) { monotonicMillis() }
                "${candidate.name}#${candidate.id}{state=${candidate.state},ageMs=$age,origin=${originByThread[candidate.id] ?: "unknown"}}"
            }
            .toList()
        return ConcurrencySnapshot(
            processThreads = threads.size,
            daemonThreads = threads.count { it.isDaemon },
            vendorThreads = categories[ThreadOwner.VENDOR] ?: 0,
            hostThreads = categories[ThreadOwner.HOST] ?: 0,
            systemThreads = categories[ThreadOwner.SYSTEM] ?: 0,
            unclassifiedThreads = categories[ThreadOwner.UNKNOWN] ?: 0,
            interceptedLaunches = launchedCount.get(),
            rejectedLaunches = deniedCount.get(),
            constructedWorkers = workerCount.get(),
            constructedExecutors = executorCount.get(),
            startupWorkers = startupWorkerCount.get(),
            handlerAdvisories = handlerAdviceCount.get(),
            stateCounts = threads.groupingBy { it.state.name }.eachCount().toSortedMap(),
            topThreadNames = threads.groupingBy { it.name.orEmpty().ifBlank { "<unnamed>" } }.eachCount()
                .entries.sortedByDescending { it.value }.take(12).associate { it.key to it.value },
            vendorFamilies = threads.asSequence()
                .mapNotNull { candidate -> vendorFamily(candidate, stacks[candidate].orEmpty(), current) }
                .groupingBy { it }.eachCount().toSortedMap(),
            newlyObserved = newDescriptions,
            firstSeenOrigins = originByThread.toMap()
        )
    }

    fun launch(candidate: Thread) {
        if (!running.get()) {
            candidate.start()
            return
        }
        val current = limits
        val sourceType = findOrigin()
        launchedCount.incrementAndGet()
        record(candidate, sourceType, "start")
        tune(candidate, sourceType, current)
        if (current.failClosedAtCeiling && !isExempt(candidate.name.orEmpty(), sourceType, current)) {
            val liveCount = Thread.getAllStackTraces().size
            if (liveCount >= current.processThreadCeiling + current.overflowAllowance) {
                deniedCount.incrementAndGet()
                if (current.emitDiagnostics) {
                    Log.w(LOG_LABEL, "launch-denied live=$liveCount thread=${candidate.name} source=$sourceType")
                }
                return
            }
        }
        candidate.start()
    }

    fun thread(): Thread = prepare(Thread(), "constructor")
    fun thread(job: Runnable): Thread = prepare(Thread(job), "constructor-runnable")
    fun thread(label: String): Thread = prepare(Thread(label), "constructor-name")
    fun thread(job: Runnable, label: String): Thread = prepare(Thread(job, label), "constructor-runnable-name")
    fun thread(team: ThreadGroup, job: Runnable): Thread = prepare(Thread(team, job), "constructor-group-runnable")
    fun thread(team: ThreadGroup, label: String): Thread = prepare(Thread(team, label), "constructor-group-name")
    fun thread(team: ThreadGroup, job: Runnable, label: String): Thread =
        prepare(Thread(team, job, label), "constructor-group-runnable-name")
    fun thread(team: ThreadGroup, job: Runnable, label: String, stackBytes: Long): Thread =
        prepare(Thread(team, job, label, stackBytes), "constructor-full")

    fun looperThread(label: String): HandlerThread = prepareLooper(HandlerThread(label), "handler-constructor")
    fun looperThread(label: String, importance: Int): HandlerThread =
        prepareLooper(HandlerThread(label, importance), "handler-priority-constructor")

    fun cachedExecutor(maker: ThreadFactory?): ExecutorService {
        val sourceType = findOrigin()
        if (!shouldShape(sourceType)) {
            return maker?.let(Executors::newCachedThreadPool) ?: Executors.newCachedThreadPool()
        }
        return shapedPool(
            sourceType = sourceType,
            coreRequest = 0,
            maximumRequest = Int.MAX_VALUE,
            idleDuration = limits.reclaimAfterSeconds,
            idleUnit = TimeUnit.SECONDS,
            backlog = SynchronousQueue(),
            maker = maker,
            refusal = ThreadPoolExecutor.AbortPolicy(),
            category = "elastic"
        )
    }

    fun fixedExecutor(width: Int, maker: ThreadFactory?): ExecutorService {
        val sourceType = findOrigin()
        if (!shouldShape(sourceType)) {
            return maker?.let { Executors.newFixedThreadPool(width.coerceAtLeast(1), it) }
                ?: Executors.newFixedThreadPool(width.coerceAtLeast(1))
        }
        return shapedPool(
            sourceType = sourceType,
            coreRequest = width,
            maximumRequest = width,
            idleDuration = limits.reclaimAfterSeconds,
            idleUnit = TimeUnit.SECONDS,
            backlog = LinkedBlockingQueue(),
            maker = maker,
            refusal = ThreadPoolExecutor.AbortPolicy(),
            category = "fixed"
        )
    }

    fun serialExecutor(maker: ThreadFactory?): ExecutorService = fixedExecutor(1, maker)

    fun scheduledExecutor(width: Int, maker: ThreadFactory?): ScheduledExecutorService {
        val sourceType = findOrigin()
        if (!shouldShape(sourceType)) {
            return maker?.let { Executors.newScheduledThreadPool(width.coerceAtLeast(1), it) }
                ?: Executors.newScheduledThreadPool(width.coerceAtLeast(1))
        }
        return shapedScheduler(width, maker, ThreadPoolExecutor.AbortPolicy(), sourceType, "scheduled")
    }

    fun serialScheduler(maker: ThreadFactory?): ScheduledExecutorService = scheduledExecutor(1, maker)

    fun stealingExecutor(width: Int?): ExecutorService {
        val resolved = width ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        return Executors.newWorkStealingPool(resolved.coerceAtLeast(1))
    }

    fun rawExecutor(
        coreRequest: Int,
        maximumRequest: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>,
        maker: ThreadFactory?,
        refusal: RejectedExecutionHandler
    ): ThreadPoolExecutor {
        val sourceType = findOrigin()
        if (!shouldShape(sourceType)) {
            return if (maker == null) {
                ThreadPoolExecutor(coreRequest, maximumRequest, idleDuration, idleUnit, backlog, refusal)
            } else {
                ThreadPoolExecutor(coreRequest, maximumRequest, idleDuration, idleUnit, backlog, maker, refusal)
            }
        }
        val shape = poolShape(coreRequest, maximumRequest, backlog, sourceType)
        val lifetime = PoolGeometry.positiveLifetime(idleDuration, idleUnit, limits.reclaimAfterSeconds)
        executorCount.incrementAndGet()
        return ThreadPoolExecutor(
            shape.coreWidth,
            shape.maximumWidth,
            lifetime.first,
            lifetime.second,
            shape.backlog,
            tracingFactory(maker, sourceType, "direct"),
            refusal
        ).apply { if (corePoolSize > 0) allowCoreThreadTimeOut(true) }
    }

    fun rawScheduler(
        width: Int,
        maker: ThreadFactory?,
        refusal: RejectedExecutionHandler
    ): ScheduledThreadPoolExecutor {
        val sourceType = findOrigin()
        if (!shouldShape(sourceType)) {
            return if (maker == null) ScheduledThreadPoolExecutor(width, refusal)
            else ScheduledThreadPoolExecutor(width, maker, refusal)
        }
        return shapedScheduler(width, maker, refusal, sourceType, "direct-scheduled")
    }

    private fun <T : Thread> prepare(candidate: T, channel: String): T {
        if (!running.get()) return candidate
        val sourceType = findOrigin()
        workerCount.incrementAndGet()
        if (monotonicMillis() - installedAt <= limits.launchObservationMillis) {
            startupWorkerCount.incrementAndGet()
        }
        record(candidate, sourceType, channel)
        if (shouldShape(sourceType) || belongsToVendor(candidate, limits)) {
            rename(candidate, sourceType)
            tune(candidate, sourceType, limits)
        }
        return candidate
    }

    private fun prepareLooper(candidate: HandlerThread, channel: String): HandlerThread {
        val prepared = prepare(candidate, channel)
        val current = limits
        if (current.emitDiagnostics && (
                current.advisoryLooperNames.any { prepared.name.startsWith(it) } ||
                    current.advisoryCallers.any { findOrigin().startsWith(it) }
                )
        ) {
            handlerAdviceCount.incrementAndGet()
            Log.i(LOG_LABEL, "handler-thread source=${originByThread[prepared.id]} name=${prepared.name}")
        }
        return prepared
    }

    private fun shapedPool(
        sourceType: String,
        coreRequest: Int,
        maximumRequest: Int,
        idleDuration: Long,
        idleUnit: TimeUnit,
        backlog: BlockingQueue<Runnable>,
        maker: ThreadFactory?,
        refusal: RejectedExecutionHandler,
        category: String
    ): ThreadPoolExecutor {
        val shape = poolShape(coreRequest, maximumRequest, backlog, sourceType)
        val lifetime = PoolGeometry.positiveLifetime(idleDuration, idleUnit, limits.reclaimAfterSeconds)
        executorCount.incrementAndGet()
        return ThreadPoolExecutor(
            shape.coreWidth,
            shape.maximumWidth,
            lifetime.first,
            lifetime.second,
            shape.backlog,
            tracingFactory(maker, sourceType, category),
            refusal
        ).apply { allowCoreThreadTimeOut(true) }
    }

    /**
     * A finite maximum combined with SynchronousQueue rejects ordinary bursts because that queue
     * has no storage. Keeping the original unbounded maximum avoids rejection but defeats Thread
     * Tamer's process-thread control.
     *
     * Convert unbounded direct-handoff pools into fixed-width, queue-backed pools. The configured
     * workers continue making progress while excess tasks wait instead of spawning unlimited
     * threads or being rejected. Other queue shapes retain their supplied queue and are width-capped.
     */
    private fun poolShape(
        coreRequest: Int,
        maximumRequest: Int,
        backlog: BlockingQueue<Runnable>,
        sourceType: String
    ): PoolShape {
        if (backlog is SynchronousQueue<*> && maximumRequest == Int.MAX_VALUE) {
            val width = parallelismCap(sourceType)
            return PoolShape(width, width, LinkedBlockingQueue())
        }
        val geometry = PoolGeometry.boundedPair(coreRequest, maximumRequest, parallelismCap(sourceType))
        return PoolShape(geometry.first, geometry.second, backlog)
    }

    private data class PoolShape(
        val coreWidth: Int,
        val maximumWidth: Int,
        val backlog: BlockingQueue<Runnable>
    )

    private fun shapedScheduler(
        width: Int,
        maker: ThreadFactory?,
        refusal: RejectedExecutionHandler,
        sourceType: String,
        category: String
    ): ScheduledThreadPoolExecutor {
        val chosenWidth = width.coerceAtLeast(1).coerceAtMost(timerCap(sourceType))
        executorCount.incrementAndGet()
        return ScheduledThreadPoolExecutor(
            chosenWidth,
            tracingFactory(maker, sourceType, category),
            refusal
        ).apply {
            setKeepAliveTime(limits.reclaimAfterSeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
            removeOnCancelPolicy = true
            allowCoreThreadTimeOut(true)
        }
    }

    private fun tracingFactory(upstream: ThreadFactory?, sourceType: String, category: String): ThreadFactory {
        val delegate = upstream ?: ThreadFactory { action ->
            Thread(action, "tamer-$category-${namingSequence.incrementAndGet()}")
        }
        return ThreadFactory { action ->
            val candidate = delegate.newThread(action) ?: Thread(action)
            workerCount.incrementAndGet()
            if (monotonicMillis() - installedAt <= limits.launchObservationMillis) {
                startupWorkerCount.incrementAndGet()
            }
            record(candidate, sourceType, "factory:$category")
            rename(candidate, sourceType)
            tune(candidate, sourceType, limits)
            candidate
        }
    }

    private fun findOrigin(): String {
        return Thread.currentThread().stackTrace.firstNotNullOfOrNull { frame ->
            val typeName = frame.className
            typeName.takeUnless {
                it.startsWith("io.threadtamer.runtime.") ||
                    it.startsWith("java.lang.Thread") ||
                    it.startsWith("java.util.concurrent.") ||
                    it.startsWith("dalvik.system.")
            }
        } ?: "unknown"
    }

    private fun shouldShape(sourceType: String): Boolean {
        if (!running.get()) return false
        return limits.exemptCallers.none(sourceType::startsWith)
    }

    private fun belongsToVendor(candidate: Thread, current: ConcurrencyLimits): Boolean {
        if (vendorNameMarkers.any { candidate.name.orEmpty().startsWith(it) }) return true
        val source = originByThread[candidate.id].orEmpty()
        if (current.vendorCallers.any { source.contains(it) }) return true
        return candidate.stackTrace.any { frame -> current.vendorCallers.any(frame.className::startsWith) }
    }

    private fun classify(candidate: Thread, stack: Array<out StackTraceElement>, current: ConcurrencyLimits): ThreadOwner {
        if (vendorFamily(candidate, stack, current) != null) return ThreadOwner.VENDOR
        val label = candidate.name.orEmpty()
        val systemName = current.exemptThreadNames.any(label::startsWith) ||
            label.startsWith("Jit thread pool") || label.startsWith("HeapTaskDaemon") ||
            label.startsWith("Profile Saver") || label.startsWith("JDWP")
        if (systemName || stack.any { frame -> current.exemptCallers.any(frame.className::startsWith) }) {
            return ThreadOwner.SYSTEM
        }
        if (stack.isNotEmpty() || originByThread.containsKey(candidate.id)) return ThreadOwner.HOST
        return ThreadOwner.UNKNOWN
    }

    private fun vendorFamily(
        candidate: Thread,
        stack: Array<out StackTraceElement>,
        current: ConcurrencyLimits
    ): String? {
        val origin = originByThread[candidate.id].orEmpty()
        current.vendorCallers.firstOrNull { origin.contains(it) }?.let { return it.removeSuffix(".") }
        stack.forEach { frame ->
            current.vendorCallers.firstOrNull(frame.className::startsWith)?.let { return it.removeSuffix(".") }
        }
        return vendorNameMarkers.firstOrNull { candidate.name.orEmpty().startsWith(it) }?.removeSuffix("_")
    }

    private fun isExempt(threadLabel: String, sourceType: String, current: ConcurrencyLimits): Boolean {
        return current.exemptThreadNames.any(threadLabel::startsWith) ||
            current.exemptCallers.any(sourceType::startsWith)
    }

    private fun parallelismCap(sourceType: String): Int {
        val current = limits
        return if (current.vendorCallers.any(sourceType::startsWith)) {
            current.vendorParallelismCeiling
        } else {
            current.generalParallelismCeiling
        }.coerceAtLeast(1)
    }

    private fun timerCap(sourceType: String): Int {
        val current = limits
        return if (current.vendorCallers.any(sourceType::startsWith)) {
            current.vendorTimerWidth
        } else {
            current.generalTimerWidth
        }.coerceAtLeast(1)
    }

    private fun rename(candidate: Thread, sourceType: String) {
        val shortSource = sourceType.substringBefore('$').substringAfterLast('.').ifBlank { "unknown" }
        val previous = candidate.name.orEmpty().ifBlank { "worker" }
        if (!previous.startsWith("$shortSource~")) {
            runCatching { candidate.name = "$shortSource~$previous" }
        }
    }

    private fun tune(candidate: Thread, sourceType: String, current: ConcurrencyLimits) {
        if (isExempt(candidate.name.orEmpty(), sourceType, current)) return
        if (lowPriorityFamilies.any(sourceType::startsWith)) {
            runCatching { candidate.priority = (Thread.NORM_PRIORITY - 2).coerceAtLeast(Thread.MIN_PRIORITY) }
        }
    }

    private fun record(candidate: Thread, sourceType: String, channel: String) {
        originByThread.putIfAbsent(candidate.id, "$channel@$sourceType")
    }

    private fun scheduleObservation(current: ConcurrencyLimits) {
        val initial = (current.launchObservationMillis / 3L).coerceIn(5_000L, 10_000L)
        val interval = current.samplePeriodMillis.coerceAtLeast(250L)
        observationJob.set(observationClock.scheduleAtFixedRate({
            runCatching {
                val report = readSnapshot()
                val age = monotonicMillis() - installedAt
                val level = when {
                    report.processThreads >= current.warmThreadAlarm -> "alarm"
                    report.processThreads >= current.warmThreadNotice -> "notice"
                    else -> "normal"
                }
                Log.i(
                    LOG_LABEL,
                    "sample level=$level ageMs=$age total=${report.processThreads} daemon=${report.daemonThreads} " +
                        "system=${report.systemThreads} vendor=${report.vendorThreads} host=${report.hostThreads} " +
                        "unknown=${report.unclassifiedThreads} launches=${report.interceptedLaunches} " +
                        "denied=${report.rejectedLaunches} workers=${report.constructedWorkers} " +
                        "startupWorkers=${report.startupWorkers} executors=${report.constructedExecutors} " +
                        "states=${report.stateCounts} families=${report.vendorFamilies} top=${report.topThreadNames} " +
                        "new=${report.newlyObserved}"
                )
            }.onFailure { problem -> Log.w(LOG_LABEL, "sample-failed", problem) }
        }, initial, interval, TimeUnit.MILLISECONDS))
    }

    private enum class ThreadOwner {
        SYSTEM,
        VENDOR,
        HOST,
        UNKNOWN
    }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
}
