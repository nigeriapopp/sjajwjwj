package app.gamenative.vapor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatTest {
    private val log = listOf(
        "10-28 20:35:17.985 D/Extraction(10350): linking a to b",
        "10-28 20:35:18.325 D/FileUtils(10350): Attempting to chmod x",
        "10-28 20:35:19.563 I/app.gamenative(10350): Background concurrent mark compact GC freed 18MB",
        "10-28 20:36:01.000 I/XServerScreen(10350): 0024:err:module:import_dll Library UnityPlayer.dll not found",
        "10-28 20:36:01.100 I/XServerScreen(10350): 0024:err:module:import_dll Library UnityPlayer.dll not found",
        "10-28 20:36:02.000 I/XServerScreen(10350): wine: Unhandled page fault on read access",
        "10-28 20:36:03.000 E/app.gamenative(10350): Guest program terminated with status: 1",
        "10-28 20:36:03.100 I/XServerScreen(10350): normal line",
    )

    @Test
    fun noiseIsRemoved() {
        val c = CrashReportFormat.clean(log)
        assertEquals(5, c.size)
        assertTrue(c.none { it.contains("D/Extraction") || it.contains("chmod") || it.contains("GC freed") })
    }

    @Test
    fun errorLinesAreFoundOnceInOrder() {
        val k = CrashReportFormat.keyLines(CrashReportFormat.clean(log))
        assertEquals(3, k.size)
        assertTrue(k[0].contains("UnityPlayer.dll not found"))
        assertTrue(k[1].contains("Unhandled page fault"))
        assertTrue(k[2].contains("status: 1"))
    }

    @Test
    fun findsNewestUnityPlayerLog() {
        val root = Files.createTempDirectory("crash").toFile()
        val a = File(root, "Old Co/Game/Player.log").apply { parentFile.mkdirs(); writeText("old"); setLastModified(1000) }
        val b = File(root, "Robot Gentleman/60 Seconds Reatomized/Player.log").apply { parentFile.mkdirs(); writeText("a\nb\nc"); setLastModified(5000) }
        assertEquals(b, CrashReportFormat.newestFile(root, "Player.log"))
        assertEquals("b\nc", CrashReportFormat.tail(b, 2))
        assertTrue(a.exists())
    }
}
