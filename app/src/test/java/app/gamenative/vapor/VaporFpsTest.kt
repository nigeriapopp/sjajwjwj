package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Test

class VaporFpsTest {
    @Test
    fun capsFitTheDisplay() {
        assertEquals(listOf(30, 40, 45, 60), VaporFps.quickCaps(60))
        assertEquals(listOf(30, 40, 45, 60, 90), VaporFps.quickCaps(120))
        assertEquals(listOf(30, 40, 45, 60, 90), VaporFps.quickCaps(240))
        assertEquals(listOf(30, 40, 45, 60), VaporFps.quickCaps(0))
    }
}
