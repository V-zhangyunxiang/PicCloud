package io.threadtamer.runtime

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PoolGeometryTest {
    @Test
    fun `pool widths stay ordered and within the selected cap`() {
        assertEquals(3 to 3, PoolGeometry.boundedPair(7, 9, 3))
        assertEquals(0 to 1, PoolGeometry.boundedPair(-2, 0, 0))
    }

    @Test
    fun `non-positive lifetime uses reclaim fallback`() {
        assertEquals(30L to TimeUnit.SECONDS, PoolGeometry.positiveLifetime(0L, TimeUnit.MILLISECONDS, 30L))
        assertEquals(8L to TimeUnit.MINUTES, PoolGeometry.positiveLifetime(8L, TimeUnit.MINUTES, 30L))
    }
}
