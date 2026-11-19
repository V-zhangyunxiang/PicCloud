package io.threadtamer.consumer

import io.threadtamer.runtime.ConcurrencyInterceptors
import io.threadtamer.runtime.ConcurrencyLimits
import io.threadtamer.runtime.ThreadTamer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadTamerRuntimeTest {
    @After
    fun resetRuntime() {
        ThreadTamer.uninstall()
    }

    @Test
    fun `host executor width is bounded and reclaimable`() {
        ThreadTamer.install(
            ConcurrencyLimits(
                generalParallelismCeiling = 2,
                reclaimAfterSeconds = 1L
            )
        )

        val executor = ConcurrencyInterceptors.fixedWorkers(12) as ThreadPoolExecutor
        try {
            assertEquals(2, executor.maximumPoolSize)
            assertEquals(2, executor.corePoolSize)
            assertTrue(executor.allowsCoreThreadTimeOut())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cached executor queues bursts without exceeding its thread ceiling`() {
        ThreadTamer.install(ConcurrencyLimits(generalParallelismCeiling = 2))
        val executor = ConcurrencyInterceptors.cachedWorkers() as ThreadPoolExecutor

        try {
            assertEquals(2, executor.corePoolSize)
            assertEquals(2, executor.maximumPoolSize)
            assertTrue(executor.queue is LinkedBlockingQueue<*>)
            assertEquals(30L, executor.getKeepAliveTime(TimeUnit.SECONDS))
            assertBoundedBurstCompletes(executor)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `raw Google and OkHttp direct handoff pools queue instead of rejecting`() {
        ThreadTamer.install(ConcurrencyLimits(generalParallelismCeiling = 2))
        for (originalCoreWidth in listOf(0, 2)) {
            val executor = ConcurrencyInterceptors.forgeExecutor(
                originalCoreWidth,
                Int.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                SynchronousQueue()
            )
            try {
                assertEquals(2, executor.corePoolSize)
                assertEquals(2, executor.maximumPoolSize)
                assertTrue(executor.queue is LinkedBlockingQueue<*>)
                assertTrue(executor.rejectedExecutionHandler is ThreadPoolExecutor.AbortPolicy)
                assertBoundedBurstCompletes(executor)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `default constructor bridges retain abort rejection policy`() {
        ThreadTamer.install()
        val executor = ConcurrencyInterceptors.forgeExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        )
        val scheduler = ConcurrencyInterceptors.forgeScheduledExecutor(1)

        try {
            assertTrue(executor.rejectedExecutionHandler is ThreadPoolExecutor.AbortPolicy)
            assertTrue(scheduler.rejectedExecutionHandler is ThreadPoolExecutor.AbortPolicy)
        } finally {
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `constructed thread keeps its job and records origin`() {
        ThreadTamer.install()
        var ran = false
        val worker = ConcurrencyInterceptors.forgeThread(Runnable { ran = true })

        ConcurrencyInterceptors.ignite(worker)
        worker.join(2_000L)

        val report = ThreadTamer.snapshot()
        assertTrue(ran)
        assertTrue(report.constructedWorkers >= 1)
        assertTrue(report.interceptedLaunches >= 1)
        assertTrue(report.firstSeenOrigins.containsKey(worker.id))
    }

    private fun assertBoundedBurstCompletes(executor: ThreadPoolExecutor) {
        val firstWorkersEntered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(3)
        val active = AtomicInteger()
        val peak = AtomicInteger()

        repeat(3) {
            executor.execute {
                val activeNow = active.incrementAndGet()
                peak.updateAndGet { previous -> maxOf(previous, activeNow) }
                firstWorkersEntered.countDown()
                try {
                    release.await(5, TimeUnit.SECONDS)
                } finally {
                    active.decrementAndGet()
                    finished.countDown()
                }
            }
        }

        assertTrue("two capped workers should start", firstWorkersEntered.await(2, TimeUnit.SECONDS))
        assertEquals("the third task should wait in the replacement queue", 1, executor.queue.size)
        release.countDown()
        assertTrue("queued work must complete without rejection", finished.await(2, TimeUnit.SECONDS))
        assertTrue("the configured ceiling must be enforced", peak.get() <= 2)
    }
}
