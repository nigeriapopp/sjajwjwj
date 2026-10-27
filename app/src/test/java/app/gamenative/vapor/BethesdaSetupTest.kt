package app.gamenative.vapor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BethesdaSetupTest {
    private fun tmp(): File = Files.createTempDirectory("beth").toFile()

    private fun fo3Install(): File = tmp().apply {
        File(this, "Fallout3.exe").writeText("x")
        File(this, "FalloutLauncher.exe").writeText("x")
        File(this, "Fallout_default.ini").writeText("[General]\nbUseThreadedAI=0\nsLanguage=ENGLISH\n")
        File(this, "Low.ini").writeText("[Display]\niSize W=1024\niSize H=768\nbFull Screen=1\niShadowMapResolution=1024\n[Water]\nbUseWaterReflections=0\n")
        File(this, "High.ini").writeText("[Display]\niShadowMapResolution=2048\n")
    }

    @Test
    fun launcherIsReplacedByGameExe() {
        val dir = fo3Install()
        assertEquals("Fallout3.exe", BethesdaSetup.pickExecutable("FalloutLauncher.exe", BethesdaSetup.FALLOUT3, dir))
        assertEquals("Fallout3.exe", BethesdaSetup.pickExecutable("", BethesdaSetup.FALLOUT3, dir))
        assertNull(BethesdaSetup.pickExecutable("Fallout3.exe", BethesdaSetup.FALLOUT3, dir))
        assertNull(BethesdaSetup.pickExecutable("fose_loader.exe", BethesdaSetup.FALLOUT3, dir))
    }

    @Test
    fun exeCaseOnDiskIsKept() {
        val dir = tmp()
        File(dir, "FALLOUT3.EXE").writeText("x")
        assertEquals("FALLOUT3.EXE", BethesdaSetup.pickExecutable("falloutlauncher.exe", BethesdaSetup.FALLOUT3, dir))
    }

    @Test
    fun fastCoresAreReported() {
        assertEquals("4:4,5,6,7", BethesdaSetup.cpuTopology(8))
        assertEquals("4:0,1,2,3", BethesdaSetup.cpuTopology(4))
        assertEquals("2:0,1", BethesdaSetup.cpuTopology(2))
    }

    @Test
    fun missingIniFilesAreCreatedFromShippedPresets() {
        val dir = fo3Install()
        val myGames = File(tmp(), "Fallout3")
        val made = BethesdaSetup.prepareIniFiles(dir, myGames, BethesdaSetup.FALLOUT3, DeviceTier.HIGH, 1600, 720)
        assertEquals(listOf("Fallout.ini", "FalloutPrefs.ini"), made)
        val user = File(myGames, "Fallout.ini").readText()
        assertTrue(user.contains("bUseThreadedAI=1"))
        assertTrue(user.contains("iNumHWThreads=2"))
        assertTrue(user.contains("sLanguage=ENGLISH"))
        val prefs = File(myGames, "FalloutPrefs.ini").readText()
        assertTrue(prefs.contains("iSize W=1600"))
        assertTrue(prefs.contains("iSize H=720"))
        assertTrue(prefs.contains("bFull Screen=0"))
        assertTrue(prefs.contains("iShadowMapResolution=512"))
        assertTrue(prefs.contains("bUseWaterReflections=0"))
        assertTrue(BethesdaSetup.prepareIniFiles(dir, myGames, BethesdaSetup.FALLOUT3, DeviceTier.HIGH, 1600, 720).isEmpty())
    }

    @Test
    fun playersOwnFilesAreNotTouched() {
        val dir = fo3Install()
        val myGames = File(tmp(), "Fallout3").apply { mkdirs() }
        File(myGames, "FalloutPrefs.ini").writeText("[Display]\niSize W=800\n")
        BethesdaSetup.prepareIniFiles(dir, myGames, BethesdaSetup.FALLOUT3, DeviceTier.LOW, 1280, 720)
        assertEquals("[Display]\niSize W=800\n", File(myGames, "FalloutPrefs.ini").readText())
    }

    @Test
    fun oblivionDisplayGoesInUserIni() {
        val dir = tmp()
        File(dir, "Oblivion_default.ini").writeText("[Display]\niSize W=640\n")
        val myGames = File(tmp(), "Oblivion")
        BethesdaSetup.prepareIniFiles(dir, myGames, BethesdaSetup.OBLIVION, DeviceTier.MID, 1280, 720)
        val ini = File(myGames, "Oblivion.ini").readText()
        assertTrue(ini.contains("iSize W=1280"))
        assertFalse(File(myGames, "FalloutPrefs.ini").exists())
    }

    @Test
    fun gstreamerSafeModeAppendsOnce() {
        assertEquals("winegstreamer=d", BethesdaSetup.withoutGstreamer(""))
        assertEquals("icu=n;winegstreamer=d", BethesdaSetup.withoutGstreamer("icu=n;"))
        assertEquals("winegstreamer=n", BethesdaSetup.withoutGstreamer("winegstreamer=n"))
    }
}
