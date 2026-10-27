package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSummaryTest {
    @Test
    fun fullRecap() {
        val text = SessionSummary.format(
            mapOf(
                "game_name" to "Fallout 3",
                "session_length" to 2520,
                "avg_fps" to 52.4,
                "frame_p99_ms" to 26.3,
                "battery_temp_c" to 38,
            ),
        )
        assertEquals("Fallout 3 · 42 min · 52 FPS avg · 38 FPS 1% low · battery 38°C", text)
    }

    @Test
    fun shortSessionsAreSkipped() {
        assertNull(SessionSummary.format(mapOf("session_length" to 30)))
    }

    @Test
    fun longSessionsShowHours() {
        assertEquals("1 h 5 min", SessionSummary.duration(3900))
    }
}
