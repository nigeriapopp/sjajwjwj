package app.gamenative.vapor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneGraphicsPresetsTest {
    private fun tmp(): File = Files.createTempDirectory("vapor").toFile()

    @Test
    fun unrealGameGetsTieredSettings() {
        val install = tmp()
        File(install, "MyGame/Content/Paks").mkdirs()
        File(install, "Engine/Content/Paks").mkdirs()
        val user = tmp()
        val applied = PhoneGraphicsPresets.apply(install, user, DeviceTier.HIGH, 60)
        assertEquals("Unreal (MyGame)", applied)
        val ini = File(user, "AppData/Local/MyGame/Saved/Config/WindowsNoEditor/GameUserSettings.ini").readText()
        assertTrue(ini.contains("[ScalabilityGroups]"))
        assertTrue(ini.contains("sg.ResolutionQuality=75"))
        assertTrue(ini.contains("sg.ShadowQuality=0"))
        assertTrue(ini.contains("FrameRateLimit=60.000000"))
        assertTrue(File(user, "AppData/Local/MyGame/Saved/Config/Windows/GameUserSettings.ini").isFile)
    }

    @Test
    fun existingUnrealSettingsAreEditedNotReplaced() {
        val install = tmp()
        File(install, "Game/Content/Paks").mkdirs()
        val user = tmp()
        val ini = File(user, "AppData/Local/Game/Saved/Config/Windows/GameUserSettings.ini")
        ini.parentFile.mkdirs()
        ini.writeText("[ScalabilityGroups]\nsg.ShadowQuality=3\n\n[Other]\nKeep=1\n")
        PhoneGraphicsPresets.apply(install, user, DeviceTier.LOW, 30)
        val text = ini.readText()
        assertTrue(text.contains("sg.ShadowQuality=0"))
        assertTrue(text.contains("Keep=1"))
        assertTrue(text.contains("sg.ResolutionQuality=50"))
    }

    @Test
    fun bethesdaPrefsPatchedOnlyWhenPresent() {
        val install = tmp()
        File(install, "Fallout3.exe").writeText("x")
        val user = tmp()
        assertNull(PhoneGraphicsPresets.apply(install, user, DeviceTier.HIGH, 60))
        val prefs = File(user, "Documents/My Games/Fallout3/FalloutPrefs.ini")
        prefs.parentFile.mkdirs()
        prefs.writeText("[Display]\niShadowMapResolution=2048\niMultiSample=8\n")
        assertEquals("Fallout3", PhoneGraphicsPresets.apply(install, user, DeviceTier.HIGH, 60))
        val text = prefs.readText()
        assertTrue(text.contains("iShadowMapResolution=512"))
        assertTrue(text.contains("iMultiSample=0"))
    }

    @Test
    fun saveFoldersAreCreated() {
        val user = tmp()
        File(user, "Saved Games").writeText("stray file")
        val fixed = LaunchPrep.ensureSaveFolders(user)
        assertTrue(fixed >= 6)
        LaunchPrep.SAVE_DIRS.forEach { assertTrue(it, File(user, it).isDirectory) }
        assertEquals(0, LaunchPrep.ensureSaveFolders(user))
    }
}
