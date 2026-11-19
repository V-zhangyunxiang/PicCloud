package com.poseidon.ad.cloud

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Tests the in-memory caching logic extracted from FirebaseCloudImpl.
 * Since FirebaseRemoteConfig requires Android context, we test the pure caching
 * behavior using the same ConcurrentHashMap pattern.
 */
class FirebaseCloudImplCacheTest {

    private val memoryCache = ConcurrentHashMap<String, String>()
    private var delegateCallCount = 0

    private fun simulateGetString(key: String, delegateValue: String): String {
        val cached = memoryCache[key]
        if (cached != null) {
            return cached
        }
        delegateCallCount++
        val remoteValue = delegateValue
        memoryCache[key] = remoteValue
        return remoteValue
    }

    @Test
    fun `first read goes to delegate and caches`() {
        delegateCallCount = 0
        val result = simulateGetString("threshold", "150.0")
        assertEquals("150.0", result)
        assertEquals(1, delegateCallCount)
    }

    @Test
    fun `second read returns from cache without delegate call`() {
        delegateCallCount = 0
        simulateGetString("threshold", "150.0")
        val result = simulateGetString("threshold", "999.0")
        assertEquals("150.0", result)
        assertEquals(1, delegateCallCount)
    }

    @Test
    fun `different keys each go to delegate once`() {
        delegateCallCount = 0
        simulateGetString("key_a", "value_a")
        simulateGetString("key_b", "value_b")
        simulateGetString("key_a", "ignored")
        simulateGetString("key_b", "ignored")
        assertEquals(2, delegateCallCount)
    }

    @Test
    fun `cache clear forces re-read from delegate`() {
        delegateCallCount = 0
        simulateGetString("threshold", "150.0")
        assertEquals(1, delegateCallCount)

        memoryCache.clear()

        val result = simulateGetString("threshold", "200.0")
        assertEquals("200.0", result)
        assertEquals(2, delegateCallCount)
    }

    @Test
    fun `cache clear only affects subsequent reads`() {
        delegateCallCount = 0
        simulateGetString("key_a", "val_a")
        simulateGetString("key_b", "val_b")
        assertEquals(2, delegateCallCount)

        memoryCache.clear()

        val a = simulateGetString("key_a", "new_a")
        val b = simulateGetString("key_b", "new_b")
        assertEquals("new_a", a)
        assertEquals("new_b", b)
        assertEquals(4, delegateCallCount)
    }

    @Test
    fun `empty string is cached correctly`() {
        delegateCallCount = 0
        val first = simulateGetString("empty_key", "")
        assertEquals("", first)
        assertEquals(1, delegateCallCount)

        val second = simulateGetString("empty_key", "should_not_reach")
        assertEquals("", second)
        assertEquals(1, delegateCallCount)
    }
}
