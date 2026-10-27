package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Test

class IniEditTest {
    private val falloutLike = "[General]\nSStartingCell=\nbUseThreadedAI=0\n\n[Display]\niSize W=1280\n"

    @Test
    fun replacesValueInSection() {
        val out = IniEdit.set(falloutLike, "General", "bUseThreadedAI", "1")
        assertEquals("[General]\nSStartingCell=\nbUseThreadedAI=1\n\n[Display]\niSize W=1280\n", out)
    }

    @Test
    fun addsMissingKeyInsideSectionNotAtEnd() {
        val out = IniEdit.set(falloutLike, "General", "iNumHWThreads", "2")
        assertEquals("[General]\nSStartingCell=\nbUseThreadedAI=0\niNumHWThreads=2\n\n[Display]\niSize W=1280\n", out)
    }

    @Test
    fun sectionMatchIsCaseInsensitiveAndKeepsCrlf() {
        val out = IniEdit.set("[general]\r\nfoo=1\r\n", "General", "foo", "2")
        assertEquals("[general]\r\nfoo=2\r\n", out)
    }

    @Test
    fun appendsSectionWhenMissing() {
        assertEquals("[Display]\na=1\n\n[General]\nb=2\n", IniEdit.set("[Display]\na=1\n", "General", "b", "2"))
    }

    @Test
    fun runningTwiceChangesNothing() {
        val once = IniEdit.set(falloutLike, "General", "iNumHWThreads", "2")
        assertEquals(once, IniEdit.set(once, "General", "iNumHWThreads", "2"))
    }
}
