package com.poseidon.ad.main.processor

import com.poseidon.ad.api.PoseidonMemoryPressureLevel
import com.poseidon.ad.main.processor.MemoryPressureDeviceReportAction.CACHE
import com.poseidon.ad.main.processor.MemoryPressureDeviceReportAction.FLUSH_PENDING
import com.poseidon.ad.main.processor.MemoryPressureDeviceReportAction.NONE
import com.poseidon.ad.main.processor.MemoryPressureDeviceReportAction.REPORT_NOW
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PreloadMemoryGuardTest {

    @Test
    fun `guard is enabled when remote value is absent`() {
        assertTrue(backgroundPreloadGuardEnabled(""))
        assertTrue(backgroundPreloadGuardEnabled("true"))
        assertFalse(backgroundPreloadGuardEnabled("false"))
    }

    @Test
    fun `disabled guard allows preload`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = false,
            isAppInForeground = false,
            isSystemLowMemory = true,
            heapUsedBytes = 90,
            heapMaxBytes = 100,
        )

        assertFalse(shouldSkip)
    }

    @Test
    fun `foreground and low memory allows preload`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = true,
            isSystemLowMemory = true,
            heapUsedBytes = 20,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 0,
        )

        assertFalse(shouldSkip)
    }

    @Test
    fun `background and normal memory allows preload`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = false,
            isSystemLowMemory = false,
            heapUsedBytes = 20,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 10,
        )

        assertFalse(shouldSkip)
    }

    @Test
    fun `background and low memory skips preload when enabled`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = false,
            isSystemLowMemory = true,
            heapUsedBytes = 20,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 10,
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun `background preload skips at heap usage threshold`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = false,
            isSystemLowMemory = false,
            heapUsedBytes = 80,
            heapMaxBytes = 100,
            maxHeapUsagePercent = 80,
            minHeapHeadroomBytes = 0,
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun `critical heap pressure skips preload in foreground`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = true,
            isSystemLowMemory = false,
            heapUsedBytes = 90,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 0,
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun `background preload skips when reserved heap headroom is reached`() {
        val shouldSkip = PreloadMemoryGuard.shouldSkipPreload(
            enabled = true,
            isAppInForeground = false,
            isSystemLowMemory = false,
            heapUsedBytes = 80,
            heapMaxBytes = 100,
            maxHeapUsagePercent = 95,
            minHeapHeadroomBytes = 30,
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun `skip reporting is rate limited`() {
        assertTrue(isSkipReportDue(now = 100, previous = 0, intervalMillis = 1_000))
        assertFalse(isSkipReportDue(now = 1_099, previous = 100, intervalMillis = 1_000))
        assertTrue(isSkipReportDue(now = 1_100, previous = 100, intervalMillis = 1_000))
    }

    @Test
    fun `memory pressure is low at configured heap usage threshold`() {
        val level = evaluateMemoryPressureLevel(
            enabled = true,
            isSystemLowMemory = false,
            heapUsedBytes = 80,
            heapMaxBytes = 100,
            maxHeapUsagePercent = 80,
            minHeapHeadroomBytes = 0,
        )

        assertEquals(PoseidonMemoryPressureLevel.LOW, level)
    }

    @Test
    fun `memory pressure is critical at critical heap usage threshold`() {
        val level = evaluateMemoryPressureLevel(
            enabled = true,
            isSystemLowMemory = false,
            heapUsedBytes = 90,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 0,
        )

        assertEquals(PoseidonMemoryPressureLevel.CRITICAL, level)
    }

    @Test
    fun `memory pressure is critical when system reports low memory`() {
        val level = evaluateMemoryPressureLevel(
            enabled = true,
            isSystemLowMemory = true,
            heapUsedBytes = 10,
            heapMaxBytes = 100,
            minHeapHeadroomBytes = 0,
        )

        assertEquals(PoseidonMemoryPressureLevel.CRITICAL, level)
    }

    @Test
    fun `memory pressure is absent when heap is healthy`() {
        val level = evaluateMemoryPressureLevel(
            enabled = true,
            isSystemLowMemory = false,
            heapUsedBytes = 10,
            heapMaxBytes = 100,
            maxHeapUsagePercent = 80,
            minHeapHeadroomBytes = 0,
        )

        assertNull(level)
    }

    @Test
    fun `memory pressure notification recovers only below recovery threshold`() {
        val mib = 1024L * 1024L
        assertFalse(
            hasRecoveredFromMemoryPressure(
                heapUsedBytes = 79 * mib,
                heapMaxBytes = 100 * mib,
            )
        )
        assertTrue(
            hasRecoveredFromMemoryPressure(
                heapUsedBytes = 60 * mib,
                heapMaxBytes = 200 * mib,
            )
        )
    }

    @Test
    fun `device memory pressure report is absent below threshold`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = false,
            heapUsedBytes = 89,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 20,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(NONE, action)
    }

    @Test
    fun `device memory pressure reports immediately from 90 to 95 percent`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = false,
            heapUsedBytes = 95,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 20,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(REPORT_NOW, action)
    }

    @Test
    fun `device memory pressure caches above 95 percent`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = false,
            heapUsedBytes = 96,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 20,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(CACHE, action)
    }

    @Test
    fun `device memory pressure flushes pending snapshot when reporting is safe`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = true,
            heapUsedBytes = 88,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 20,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(FLUSH_PENDING, action)
    }

    @Test
    fun `device memory pressure keeps pending snapshot when reporting lacks headroom`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = true,
            heapUsedBytes = 88,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 5,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(NONE, action)
    }

    @Test
    fun `device memory pressure does not report twice in one session`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = true,
            hasPendingSnapshot = false,
            heapUsedBytes = 95,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 20,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(NONE, action)
    }

    @Test
    fun `device memory pressure caches 90 to 95 percent when reporting lacks headroom`() {
        val action = determineMemoryPressureDeviceReportAction(
            enabled = true,
            alreadyReported = false,
            hasPendingSnapshot = false,
            heapUsedBytes = 94,
            heapMaxBytes = 100,
            reportingHeadroomBytes = 5,
            minReportingHeadroomBytes = 10,
        )

        assertEquals(CACHE, action)
    }
}
