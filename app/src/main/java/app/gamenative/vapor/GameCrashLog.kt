package app.gamenative.vapor

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * When a game exits with an error, Vapor saves the recent app + Wine log so the player can share it.
 * Android only lets an app read its own logs, which includes Wine and the game (child processes).
 */
object GameCrashLog {
    private const val FILE_NAME = "vapor_last_game_crash.txt"
    private const val LOG_LINES = 4000

    private fun dir(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, "debug_reports").apply { mkdirs() } }

    fun latest(context: Context): File? = dir(context)?.let { File(it, FILE_NAME) }?.takeIf { it.isFile && it.length() > 0 }

    /** Saves the log. Blocking: call off the main thread. */
    fun capture(context: Context, gameName: String, status: Int) {
        try {
            val out = File(dir(context) ?: return, FILE_NAME)
            val logcat = try {
                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString()))
                p.inputStream.bufferedReader().use { it.readText() }.also { p.destroy() }
            } catch (e: Exception) {
                "Could not read the log: ${e.message}"
            }
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
            out.writeText(
                buildString {
                    appendLine("Vapor game crash report")
                    appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("Game: $gameName")
                    appendLine("Exit status: $status")
                    appendLine("Vapor: $version")
                    appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}), tier ${VaporSettings.tierName}")
                    appendLine("Fast Launch: ${VaporSettings.fastLaunch}")
                    appendLine()
                    append(logcat)
                },
            )
            Timber.i("[Vapor] saved game crash log to ${out.absolutePath}")
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] could not save crash log")
        }
    }

    /** Opens the Android share sheet for the last crash log. Returns false if there is none. */
    fun share(context: Context): Boolean {
        val file = latest(context) ?: return false
        return try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Vapor game crash log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share crash log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] share crash log failed")
            false
        }
    }
}
