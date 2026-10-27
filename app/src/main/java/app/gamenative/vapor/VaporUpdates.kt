package app.gamenative.vapor

import org.json.JSONObject

/** A newer Vapor build found on GitHub Releases. */
data class VaporRelease(val versionCode: Int, val title: String, val apkUrl: String, val apkSize: Long, val notes: String)

/** Pure parsing of the GitHub "latest release" response (unit tested). */
object VaporUpdates {
    const val REPO = "nigeriapopp/sjajwjwj"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    private val BUILD = Regex("build(\\d+)")

    /** Release tags look like vapor-build12 (or older vapor-1.4.0-vapor1-build7): versionCode = 1000 + build. */
    fun versionCodeFromTag(tag: String): Int? = BUILD.findAll(tag).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { 1000 + it }

    fun parse(json: String): VaporRelease? = try {
        val o = JSONObject(json)
        val code = versionCodeFromTag(o.optString("tag_name"))
        val assets = o.optJSONArray("assets")
        var url: String? = null
        var size = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
        }
        if (code == null || url.isNullOrEmpty()) {
            null
        } else {
            VaporRelease(code, o.optString("name").ifEmpty { o.optString("tag_name") }, url, size, o.optString("body"))
        }
    } catch (e: Exception) {
        null
    }

    fun isNewer(release: VaporRelease?, installedCode: Long): Boolean = release != null && release.versionCode > installedCode
}
