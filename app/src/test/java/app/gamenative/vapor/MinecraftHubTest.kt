package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftHubTest {
    @Test
    fun galaxyS22GetsHighTuning() {
        val t = MinecraftGuide.tuningFor(totalRamMb = 7_700, tier = DeviceTier.HIGH)
        assertEquals(3072, t.ramMb)
        assertEquals(10, t.renderDistance)
        assertEquals(60, t.fpsCap)
    }

    @Test
    fun lowRamPhonesStaySafe() {
        val t = MinecraftGuide.tuningFor(totalRamMb = 4_000, tier = DeviceTier.LOW)
        assertEquals(1536, t.ramMb)
        assertEquals(6, t.renderDistance)
    }

    @Test
    fun guideMentionsTheTunedNumbers() {
        val steps = MinecraftGuide.guide(MinecraftGuide.tuningFor(12_000, DeviceTier.FLAGSHIP))
        assertEquals(6, steps.size)
        assertTrue(steps[4].body.contains("4096 MB"))
    }
}
