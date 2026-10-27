package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Test

class VaporTimeTest {
    private val now = 10_000_000_000L

    @Test
    fun readableAges() {
        assertEquals("just now", VaporTime.ago(now, now - 10_000))
        assertEquals("1 min ago", VaporTime.ago(now, now - 60_000))
        assertEquals("12 min ago", VaporTime.ago(now, now - 12 * 60_000))
        assertEquals("1 hour ago", VaporTime.ago(now, now - 3_700_000))
        assertEquals("5 hours ago", VaporTime.ago(now, now - 5 * 3_600_000))
        assertEquals("yesterday", VaporTime.ago(now, now - 30 * 3_600_000L))
        assertEquals("3 days ago", VaporTime.ago(now, now - 3 * 86_400_000L))
        assertEquals("just now", VaporTime.ago(now, now + 5_000))
    }
}
