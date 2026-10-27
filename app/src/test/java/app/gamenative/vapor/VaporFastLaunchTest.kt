package app.gamenative.vapor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaporFastLaunchTest {
    @Test
    fun quickExitsCountAsCrashes() {
        assertTrue(VaporFastLaunch.shouldRepairAfter(5_000))
        assertTrue(VaporFastLaunch.shouldRepairAfter(39_000))
        assertFalse(VaporFastLaunch.shouldRepairAfter(41_000))
        assertFalse(VaporFastLaunch.shouldRepairAfter(-1))
    }
}
