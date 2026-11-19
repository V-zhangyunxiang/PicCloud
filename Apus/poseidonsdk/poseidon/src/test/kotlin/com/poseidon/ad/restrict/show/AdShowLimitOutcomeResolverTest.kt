package com.poseidon.ad.restrict.show

import com.poseidon.ad.main.strategy.limit.show.AdLimitShowItemCfg
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdShowLimitOutcomeResolverTest {

    private fun cfg(
        tsf: Boolean = true,
        tif: Int = 2,
        til: Int = 3,
        scenes: List<String>? = null,
    ) = AdLimitShowItemCfg(switch = 1, scenes = scenes, tsf = tsf, tif = tif, til = til)

    @Test
    fun `scene not in scoped list is always allowed`() {
        val item = cfg(scenes = listOf("scene_a", "scene_b"))
        val result = resolveAdShowLimitOutcome(
            sceneName = "scene_c",
            item = item,
            todayCheckCount = 100,
            firstCheckPassed = null,
            firstCheckShown = null,
            recentShownFlags = emptyList(),
        )
        assertTrue(result)
    }

    @Test
    fun `no scoped scenes means all scenes are limited`() {
        val item = cfg(tsf = false, scenes = null)
        val result = resolveAdShowLimitOutcome(
            sceneName = "any_scene",
            item = item,
            todayCheckCount = 0,
            firstCheckPassed = null,
            firstCheckShown = null,
            recentShownFlags = emptyList(),
        )
        assertFalse(result)
    }

    @Test
    fun `zero checks returns tsf true`() {
        val item = cfg(tsf = true)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 0,
            firstCheckPassed = null,
            firstCheckShown = null,
            recentShownFlags = emptyList(),
        )
        assertTrue(result)
    }

    @Test
    fun `zero checks returns tsf false`() {
        val item = cfg(tsf = false)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 0,
            firstCheckPassed = null,
            firstCheckShown = null,
            recentShownFlags = emptyList(),
        )
        assertFalse(result)
    }

    @Test
    fun `count equals tif plus 1 always true`() {
        val item = cfg(tif = 2)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 3,
            firstCheckPassed = true,
            firstCheckShown = false,
            recentShownFlags = emptyList(),
        )
        assertTrue(result)
    }

    @Test
    fun `count less than tif plus 1 first passed and not shown`() {
        val item = cfg(tif = 5)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 2,
            firstCheckPassed = true,
            firstCheckShown = false,
            recentShownFlags = emptyList(),
        )
        assertTrue(result)
    }

    @Test
    fun `count less than tif plus 1 first not passed`() {
        val item = cfg(tif = 5)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 2,
            firstCheckPassed = false,
            firstCheckShown = false,
            recentShownFlags = emptyList(),
        )
        assertFalse(result)
    }

    @Test
    fun `count less than tif plus 1 first shown`() {
        val item = cfg(tif = 5)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 2,
            firstCheckPassed = true,
            firstCheckShown = true,
            recentShownFlags = emptyList(),
        )
        assertFalse(result)
    }

    @Test
    fun `rolling window remainder zero always true`() {
        val item = cfg(tif = 2, til = 3)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 8,
            firstCheckPassed = true,
            firstCheckShown = true,
            recentShownFlags = listOf(true, true),
        )
        assertTrue(result)
    }

    @Test
    fun `rolling window none shown returns true`() {
        val item = cfg(tif = 2, til = 3)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 5,
            firstCheckPassed = true,
            firstCheckShown = false,
            recentShownFlags = listOf(false, false),
        )
        assertTrue(result)
    }

    @Test
    fun `rolling window any shown returns false`() {
        val item = cfg(tif = 2, til = 3)
        val result = resolveAdShowLimitOutcome(
            sceneName = "s",
            item = item,
            todayCheckCount = 5,
            firstCheckPassed = true,
            firstCheckShown = false,
            recentShownFlags = listOf(false, true),
        )
        assertFalse(result)
    }
}
