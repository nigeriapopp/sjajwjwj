package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaporUpdatesTest {
    @Test
    fun tagsMapToVersionCodes() {
        assertEquals(1012, VaporUpdates.versionCodeFromTag("vapor-build12"))
        assertEquals(1007, VaporUpdates.versionCodeFromTag("vapor-1.4.0-vapor1-build7"))
        assertNull(VaporUpdates.versionCodeFromTag("v1.0"))
    }

    @Test
    fun parsesLatestRelease() {
        val json = """{"tag_name":"vapor-build15","name":"Vapor 1.5.0 (build 15)","body":"notes",
            "assets":[{"name":"readme.txt","browser_download_url":"x","size":1},
                      {"name":"Vapor-1.5.0-vapor.15.apk","browser_download_url":"https://e/x.apk","size":900}]}"""
        val r = VaporUpdates.parse(json)!!
        assertEquals(1015, r.versionCode)
        assertEquals("https://e/x.apk", r.apkUrl)
        assertEquals(900L, r.apkSize)
        assertTrue(VaporUpdates.isNewer(r, 24))
        assertFalse(VaporUpdates.isNewer(r, 1015))
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(VaporUpdates.parse("""{"tag_name":"vapor-build3","assets":[]}"""))
        assertNull(VaporUpdates.parse("not json"))
    }
}
