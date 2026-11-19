package com.poseidon.ad.main.load

import com.poseidon.ad.main.strategy.limit.load.mode.AdLoadModeControlCfg
import com.poseidon.ad.main.strategy.limit.load.mode.ModeDelayCfg
import com.poseidon.ad.main.strategy.limit.load.mode.ModeSerialCfg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdLoadTrafficDecisionResolverTest {

    @Test
    fun `null config yields immediate`() {
        val result = evaluateAdLoadTrafficOutcome(cfg = null, adUnitId = "unit_a")
        assertTrue(result is AdLoadTrafficOutcome.RunImmediately)
    }

    @Test
    fun `serial override for matching ad unit`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "immediate",
            modeSerial = ModeSerialCfg(adUnitIds = listOf("unit_x")),
            modeDelay = null,
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "unit_x")
        assertTrue(result is AdLoadTrafficOutcome.EnqueueSerial)
    }

    @Test
    fun `serial override does not match other ad unit`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "immediate",
            modeSerial = ModeSerialCfg(adUnitIds = listOf("unit_x")),
            modeDelay = null,
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "unit_y")
        assertTrue(result is AdLoadTrafficOutcome.RunImmediately)
    }

    @Test
    fun `default serial without delay config`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "serial",
            modeSerial = null,
            modeDelay = null,
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "any")
        assertTrue(result is AdLoadTrafficOutcome.EnqueueSerial)
    }

    @Test
    fun `default delay without delay config falls back to serial`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "delay",
            modeSerial = null,
            modeDelay = null,
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "any")
        assertTrue(result is AdLoadTrafficOutcome.EnqueueSerial)
    }

    @Test
    fun `default delay with delay config and no per-unit override`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "delay",
            modeSerial = null,
            modeDelay = ModeDelayCfg(timeInMillis = 500L, adUnitIds = null),
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "unit_z")
        assertTrue(result is AdLoadTrafficOutcome.ScheduleDelay)
        assertEquals(500L, (result as AdLoadTrafficOutcome.ScheduleDelay).delayMillis)
    }

    @Test
    fun `per-unit delay override takes precedence`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "serial",
            modeSerial = null,
            modeDelay = ModeDelayCfg(
                timeInMillis = 500L,
                adUnitIds = mapOf("unit_q" to 1200L),
            ),
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "unit_q")
        assertTrue(result is AdLoadTrafficOutcome.ScheduleDelay)
        assertEquals(1200L, (result as AdLoadTrafficOutcome.ScheduleDelay).delayMillis)
    }

    @Test
    fun `default immediate with delay config but no per-unit match`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "immediate",
            modeSerial = null,
            modeDelay = ModeDelayCfg(timeInMillis = 300L, adUnitIds = mapOf("other" to 999L)),
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "unit_m")
        assertTrue(result is AdLoadTrafficOutcome.RunImmediately)
    }

    @Test
    fun `unknown default mode falls back to immediate`() {
        val cfg = AdLoadModeControlCfg(
            modeDefault = "unknown_mode",
            modeSerial = null,
            modeDelay = null,
        )
        val result = evaluateAdLoadTrafficOutcome(cfg = cfg, adUnitId = "any")
        assertTrue(result is AdLoadTrafficOutcome.RunImmediately)
    }
}
