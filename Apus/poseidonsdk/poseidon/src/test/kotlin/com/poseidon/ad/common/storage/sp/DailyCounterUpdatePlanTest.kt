package com.poseidon.ad.common.storage.sp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCounterUpdatePlanTest {

    @Test
    fun `null saved date triggers full reset`() {
        val plan = computeDailyCounterPlan(
            savedDate = null,
            currentDate = "2025-01-15",
            savedCounter = 5,
            applyIncrement = false,
        )
        assertTrue(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals("2025-01-15", plan.dateValue)
        assertEquals(0, plan.counterValue)
    }

    @Test
    fun `empty saved date triggers full reset`() {
        val plan = computeDailyCounterPlan(
            savedDate = "",
            currentDate = "2025-01-15",
            savedCounter = 3,
            applyIncrement = false,
        )
        assertTrue(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals(0, plan.counterValue)
    }

    @Test
    fun `different date triggers reset and increments if requested`() {
        val plan = computeDailyCounterPlan(
            savedDate = "2025-01-14",
            currentDate = "2025-01-15",
            savedCounter = 10,
            applyIncrement = true,
        )
        assertTrue(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals(1, plan.counterValue)
    }

    @Test
    fun `same date no increment does nothing`() {
        val plan = computeDailyCounterPlan(
            savedDate = "2025-01-15",
            currentDate = "2025-01-15",
            savedCounter = 7,
            applyIncrement = false,
        )
        assertFalse(plan.needsPersistDate)
        assertFalse(plan.needsPersistCounter)
        assertEquals(7, plan.counterValue)
    }

    @Test
    fun `same date with increment persists counter only`() {
        val plan = computeDailyCounterPlan(
            savedDate = "2025-01-15",
            currentDate = "2025-01-15",
            savedCounter = 7,
            applyIncrement = true,
        )
        assertFalse(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals(8, plan.counterValue)
    }

    @Test
    fun `date reset with increment starts from 1`() {
        val plan = computeDailyCounterPlan(
            savedDate = "2025-01-10",
            currentDate = "2025-01-15",
            savedCounter = 99,
            applyIncrement = true,
        )
        assertTrue(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals("2025-01-15", plan.dateValue)
        assertEquals(1, plan.counterValue)
    }

    @Test
    fun `date reset without increment resets to 0`() {
        val plan = computeDailyCounterPlan(
            savedDate = "2025-01-10",
            currentDate = "2025-01-15",
            savedCounter = 99,
            applyIncrement = false,
        )
        assertTrue(plan.needsPersistDate)
        assertTrue(plan.needsPersistCounter)
        assertEquals(0, plan.counterValue)
    }
}
