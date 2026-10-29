package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingPolicyTest {
    @Test
    fun maxFpsWaitsUntilClose() {
        val p = CoolingPolicy(CoolingPolicy.Mode.MAX_FPS)
        assertFalse(p.update(0.85f, 1, 0))
        assertEquals(0, p.level)
        assertTrue(p.update(0.91f, 2, 2_000))
        assertEquals(1, p.level)
        assertTrue(p.update(0.98f, 2, 4_000))
        assertEquals(2, p.level)
        assertTrue(p.update(0.5f, 4, 6_000))
        assertEquals(3, p.level)
    }

    @Test
    fun restoresOneStepAfterStayingCool() {
        val p = CoolingPolicy(CoolingPolicy.Mode.MAX_FPS)
        p.update(0.98f, 3, 0)
        assertEquals(2, p.level)
        // Cooler but not cool enough: stays.
        assertFalse(p.update(0.85f, 2, 10_000))
        assertFalse(p.update(0.70f, 1, 20_000))
        assertFalse(p.update(0.70f, 1, 70_000))
        assertTrue(p.update(0.70f, 1, 80_001))
        assertEquals(1, p.level)
        assertTrue(p.update(0.70f, 1, 140_002))
        assertEquals(0, p.level)
    }

    @Test
    fun unknownHeadroomUsesStatus() {
        val p = CoolingPolicy(CoolingPolicy.Mode.MAX_FPS)
        assertFalse(p.update(Float.NaN, 2, 0))
        assertTrue(p.update(Float.NaN, 3, 1_000))
        assertEquals(2, p.level)
    }

    @Test
    fun offNeverActs() {
        val p = CoolingPolicy(CoolingPolicy.Mode.OFF)
        assertFalse(p.update(2f, 5, 0))
        assertEquals(0, p.level)
    }

    @Test
    fun capsStayBelowPlayersCap() {
        assertNull(CoolingPolicy.capFor(0, 60))
        assertEquals(50, CoolingPolicy.capFor(1, 60))
        assertEquals(100, CoolingPolicy.capFor(1, 120))
        assertEquals(45, CoolingPolicy.capFor(2, 60))
        assertEquals(30, CoolingPolicy.capFor(2, 30))
        assertEquals(30, CoolingPolicy.capFor(3, 0))
        assertEquals(40, CoolingPolicy.capFor(2, 40))
    }
}
