package app.gamenative.vapor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Downloads an APK from GitHub (Vapor itself or a Minecraft launcher) and hands it to Android's installer.
 * Android installs it as an update when the package and signing key match, or as a new app otherwise.
 */
object VaporUpdater {
    sealed class Result {
        object Started : Result()
        object NeedsPermission : Result()
        object KeyMismatch : Result()
        data class Failed(val reason: String) : Result()
    }

    fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    /** Asks GitHub for the newest Vapor release. Null when offline or nothing is published. */
    suspend fun checkLatest(): VaporRelease? = withContext(Dispatchers.IO) {
        httpText(VaporUpdates.LATEST_URL)?.let { VaporUpdates.parse(it) }
    }

    /** Latest release APK of any public GitHub repo ("owner/name"), for launchers. */
    suspend fun latestApkUrl(repo: String, preferArm64: Boolean = true): Pair<String, Long>? = withContext(Dispatchers.IO) {
        val json = httpText("https://api.github.com/repos/$repo/releases/latest") ?: return@withContext null
        try {
            val assets = org.json.JSONObject(json).optJSONArray("assets") ?: return@withContext null
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", true) }
            val pick = (if (preferArm64) apks.firstOrNull { it.optString("name").contains("arm64", true) } else null)
                ?: apks.firstOrNull { it.optString("name").contains("universal", true) || it.optString("name").contains("all", true) }
                ?: apks.firstOrNull()
            pick?.let { it.optString("browser_download_url") to it.optLong("size") }
        } catch (e: Exception) {
            null
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] could not open install permission screen")
        }
    }

    /**
     * Downloads [url] and opens the installer.
     * @param selfUpdate true for Vapor's own APK: checks the signing key first so the player gets a clear message.
     */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        fileName: String,
        selfUpdate: Boolean,
        onProgress: (Float) -> Unit,
    ): Result {
        if (!canInstall(context)) return Result.NeedsPermission
        val file = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "vapor_apk").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, fileName)
                download(url, out, onProgress)
                out
            } catch (e: Exception) {
                Timber.w(e, "[Vapor] download failed")
                null
            }
        } ?: return Result.Failed("Download failed. Check your connection and try again.")

        if (selfUpdate && !sameSigningKey(context, file)) return Result.KeyMismatch

        return try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Result.Started
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] could not open installer")
            Result.Failed("Couldn't open the installer.")
        }
    }

    private fun sameSigningKey(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)?.signingInfo ?: return true
            val installed = pm.getPackageInfo(context.packageName, flags).signingInfo ?: return true
            val a = archive.apkContentsSigners.map { it.toCharsString() }.toSet()
            val b = installed.apkContentsSigners.map { it.toCharsString() }.toSet()
            a.isEmpty() || a == b
        } catch (e: Exception) {
            true
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Vapor-Android")
        }

    private fun httpText(url: String): String? = try {
        val c = open(url)
        try {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        Timber.w(e, "[Vapor] request failed: $url")
        null
    }

    private fun download(url: String, out: File, onProgress: (Float) -> Unit) {
        val c = open(url)
        try {
            c.setRequestProperty("Accept", "application/octet-stream")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            var lastReported = -1
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("incomplete download")
            onProgress(1f)
        } finally {
            c.disconnect()
        }
    }
}
