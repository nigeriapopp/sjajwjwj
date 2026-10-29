package app.gamenative.vapor

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaporPathsTest {
    @After
    fun reset() = VaporPaths.configure("app.gamenative", "/data/user/0/app.gamenative")

    @Test
    fun mainUserIsUntouched() {
        VaporPaths.configure("app.gamenative", "/data/user/0/app.gamenative")
        assertFalse(VaporPaths.isSecondaryUser())
        val v = "DXVK_STATE_CACHE_PATH=/data/data/app.gamenative/files/imagefs/home/xuser/.cache"
        assertEquals(v, VaporPaths.fix(v))
    }

    @Test
    fun secureFolderPathsAreRewritten() {
        VaporPaths.configure("app.gamenative", "/data/user/150/app.gamenative")
        assertTrue(VaporPaths.isSecondaryUser())
        assertEquals(
            "MEDIACONV_BLANK_WAV=/data/user/150/app.gamenative/files/imagefs/home/xuser/blank.wav",
            VaporPaths.fix("MEDIACONV_BLANK_WAV=/data/data/app.gamenative/files/imagefs/home/xuser/blank.wav"),
        )
        assertEquals("/data/user/150/app.gamenative/storage", VaporPaths.fix("/data/data/app.gamenative/storage"))
        assertArrayEquals(
            arrayOf("A=1", "B=/data/user/150/app.gamenative/files"),
            VaporPaths.fixEnv(arrayOf("A=1", "B=/data/data/app.gamenative/files")),
        )
        // Other apps' paths stay as they are.
        assertEquals("/data/data/com.other/x", VaporPaths.fix("/data/data/com.other/x"))
    }

    @Test
    fun legacyDataDirCountsAsMainUser() {
        assertFalse(VaporPaths.isSecondaryUserDir("/data/data/app.gamenative"))
        assertTrue(VaporPaths.isSecondaryUserDir("/data/user/10/app.gamenative"))
    }
}
