package com.poseidon.ad.main.adinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAdRankingResolverTest {

    private val threshold = 150.0

    @Test
    fun `both ecpms positive compares by ecpm`() {
        val result = compareCacheAdRank(
            leftEcpm = 200.0, rightEcpm = 100.0,
            leftNumber = 1, rightNumber = 1,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result > 0)
    }

    @Test
    fun `both ecpms positive right wins`() {
        val result = compareCacheAdRank(
            leftEcpm = 50.0, rightEcpm = 120.0,
            leftNumber = 1, rightNumber = 1,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result < 0)
    }

    @Test
    fun `both ecpms null same ad type compares by number`() {
        val result = compareCacheAdRank(
            leftEcpm = null, rightEcpm = null,
            leftNumber = 3, rightNumber = 1,
            leftAdType = 2, rightAdType = 2,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result > 0)
    }

    @Test
    fun `both ecpms null different ad type compares by ad type priority`() {
        // AD_TYPE_REWARDED=3 priority=10, AD_TYPE_NATIVE=5 priority=3
        val result = compareCacheAdRank(
            leftEcpm = null, rightEcpm = null,
            leftNumber = 1, rightNumber = 1,
            leftAdType = AD_TYPE_REWARDED, rightAdType = AD_TYPE_NATIVE,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result > 0)
    }

    @Test
    fun `left null right high ecpm right wins`() {
        val result = compareCacheAdRank(
            leftEcpm = null, rightEcpm = 200.0,
            leftNumber = 3, rightNumber = 1,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertEquals(-1, result)
    }

    @Test
    fun `left null right low ecpm falls to tiebreak`() {
        val result = compareCacheAdRank(
            leftEcpm = null, rightEcpm = 50.0,
            leftNumber = 3, rightNumber = 1,
            leftAdType = 2, rightAdType = 2,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result > 0)
    }

    @Test
    fun `right null left high ecpm left wins`() {
        val result = compareCacheAdRank(
            leftEcpm = 200.0, rightEcpm = null,
            leftNumber = 1, rightNumber = 3,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertEquals(1, result)
    }

    @Test
    fun `right null left low ecpm falls to tiebreak`() {
        val result = compareCacheAdRank(
            leftEcpm = 50.0, rightEcpm = null,
            leftNumber = 1, rightNumber = 3,
            leftAdType = 2, rightAdType = 2,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result < 0)
    }

    @Test
    fun `equal ecpms returns zero`() {
        val result = compareCacheAdRank(
            leftEcpm = 100.0, rightEcpm = 100.0,
            leftNumber = 1, rightNumber = 1,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertEquals(0, result)
    }

    @Test
    fun `zero ecpm treated as null`() {
        val result = compareCacheAdRank(
            leftEcpm = 0.0, rightEcpm = 200.0,
            leftNumber = 1, rightNumber = 1,
            leftAdType = 1, rightAdType = 1,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertEquals(-1, result)
    }

    @Test
    fun `negative ecpm treated as null both null same type different number`() {
        val result = compareCacheAdRank(
            leftEcpm = -5.0, rightEcpm = null,
            leftNumber = 2, rightNumber = 1,
            leftAdType = 2, rightAdType = 2,
            leftAdSourceId = 1, rightAdSourceId = 1,
            threshold = threshold,
        )
        assertTrue(result > 0)
    }
}
