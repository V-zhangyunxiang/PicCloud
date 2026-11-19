package com.poseidon.ad.special.topon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopOnDispatchDecisionResolverTest {

    @Test
    fun `null channel skips dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = null,
            rawCampaign = "camp",
            sentChannel = null,
            sentCampaign = null,
        )
        assertFalse(verdict.requiresDispatch)
        assertNull(verdict.normalizedChannel)
    }

    @Test
    fun `empty channel skips dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "",
            rawCampaign = "camp",
            sentChannel = null,
            sentCampaign = null,
        )
        assertFalse(verdict.requiresDispatch)
    }

    @Test
    fun `channel with only special chars normalizes to empty and skips`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "!@#\$%",
            rawCampaign = "camp",
            sentChannel = null,
            sentCampaign = null,
        )
        assertFalse(verdict.requiresDispatch)
        assertNull(verdict.normalizedChannel)
    }

    @Test
    fun `new channel triggers dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "Adjust_Network",
            rawCampaign = "camp_1",
            sentChannel = null,
            sentCampaign = null,
        )
        assertTrue(verdict.requiresDispatch)
        assertEquals("Adjust_Network", verdict.normalizedChannel)
        assertEquals("camp_1", verdict.normalizedCampaign)
    }

    @Test
    fun `same channel and campaign skips dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "network",
            rawCampaign = "campaign",
            sentChannel = "network",
            sentCampaign = "campaign",
        )
        assertFalse(verdict.requiresDispatch)
    }

    @Test
    fun `channel changed triggers dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "networkB",
            rawCampaign = "campaign",
            sentChannel = "networkA",
            sentCampaign = "campaign",
        )
        assertTrue(verdict.requiresDispatch)
    }

    @Test
    fun `campaign changed triggers dispatch`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "network",
            rawCampaign = "campB",
            sentChannel = "network",
            sentCampaign = "campA",
        )
        assertTrue(verdict.requiresDispatch)
    }

    @Test
    fun `special chars stripped during normalization`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "net-work!123",
            rawCampaign = "camp@ign#2",
            sentChannel = null,
            sentCampaign = null,
        )
        assertTrue(verdict.requiresDispatch)
        assertEquals("network123", verdict.normalizedChannel)
        assertEquals("campign2", verdict.normalizedCampaign)
    }

    @Test
    fun `null campaign normalized to null`() {
        val verdict = evaluateTopOnDispatchNeed(
            rawChannel = "network",
            rawCampaign = null,
            sentChannel = "network",
            sentCampaign = null,
        )
        assertFalse(verdict.requiresDispatch)
        assertNull(verdict.normalizedCampaign)
    }
}
