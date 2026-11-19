package com.poseidon.ad.main.processor

import com.poseidon.ad.main.adinfo.ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NightBackgroundPreloadGuardTest {

    @Test
    fun `US background skips from 2 inclusive to 6 exclusive`() {
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("", false, "US", 1))
        assertTrue(NightBackgroundPreloadGuard.shouldSkipPreload("", false, "US", 2))
        assertTrue(NightBackgroundPreloadGuard.shouldSkipPreload("", false, "US", 5))
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("", false, "US", 6))
    }

    @Test
    fun `foreground never skips during US night window`() {
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("", true, "US", 3))
    }

    @Test
    fun `non US background never skips during night window`() {
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("", false, "CN", 3))
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("", false, null, 3))
    }

    @Test
    fun `guard is enabled by default and can be disabled remotely`() {
        assertTrue(nightBackgroundPreloadGuardEnabled(""))
        assertTrue(nightBackgroundPreloadGuardEnabled("true"))
        assertTrue(nightBackgroundPreloadGuardEnabled("invalid"))
        assertFalse(nightBackgroundPreloadGuardEnabled("false"))
        assertFalse(NightBackgroundPreloadGuard.shouldSkipPreload("false", false, "US", 3))
    }

    @Test
    fun `skip message carries scene and error code`() {
        assertEquals(
            "US_NIGHT_BACKGROUND_PRELOAD:sceneName:SharedCeilAdPool, code=$ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD",
            usNightBackgroundPreloadMessage("SharedCeilAdPool"),
        )
    }
}
