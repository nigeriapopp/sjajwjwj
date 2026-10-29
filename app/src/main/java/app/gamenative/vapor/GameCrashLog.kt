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
 * When a game exits with an error, Vapor saves a crash report the player can share:
 * a summary of the game's settings, the error lines, the game engine's own log (Unity Player.log /
 * Unreal Saved/Logs), then the cleaned app + Wine log. Android only lets an app read its own logs,
 * which includes Wine and the game (child processes).
 */
object GameCrashLog {
    private const val FILE_NAME = "vapor_last_game_crash.txt"
    private const val LOG_LINES = 20000
    private const val TAIL_LINES = 1500

    private fun dir(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, "debug_reports").apply { mkdirs() } }

    fun latest(context: Context): File? = dir(context)?.let { File(it, FILE_NAME) }?.takeIf { it.isFile && it.length() > 0 }

    /**
     * Saves the report. Blocking: call off the main thread.
     * @param settings one-line-per-setting summary of the container (wine, driver, DXVK, presets)
     * @param containerRoot the container folder, used to find the engine's own log files
     */
    /** @return true when the log shows gamescope's display server failed to start. */
    fun capture(
        context: Context,
        gameName: String,
        status: Int,
        settings: String = "",
        containerRoot: File? = null,
    ): Boolean {
        var gamescopeFailed = false
        try {
            val out = File(dir(context) ?: return false, FILE_NAME)
            val raw = try {
                // Only this session's lines, so an old error (e.g. a past gamescope failure) can't be blamed.
                val since = VaporFastLaunch.sessionStartMs
                val range = if (since > 0) {
                    arrayOf("-T", SimpleDateFormat("MM-dd HH:mm:ss.000", Locale.US).format(Date(since - 120_000L)))
                } else {
                    arrayOf("-t", LOG_LINES.toString())
                }
                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time") + range)
                p.inputStream.bufferedReader().use { it.readLines() }.also { p.destroy() }
            } catch (e: Exception) {
                listOf("Could not read the log: ${e.message}")
            }
            val cleaned = CrashReportFormat.clean(raw)
            val keyLines = CrashReportFormat.keyLines(cleaned)
            gamescopeFailed = CrashReportFormat.gamescopeFailed(cleaned)
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"

            val userDir = containerRoot?.let { SaveSnapshots.userDir(it) }
            val unityLog = userDir?.let { CrashReportFormat.newestFile(File(it, "AppData/LocalLow"), "Player.log") }
            val unrealLog = userDir?.let { CrashReportFormat.newestLog(File(it, "AppData/Local")) }
                ?.takeIf { it.path.contains("Saved${File.separator}Logs") }

            out.writeText(
                buildString {
                    appendLine("Vapor game crash report")
                    appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("Game: $gameName")
                    appendLine("Exit status: $status")
                    appendLine("Vapor: $version")
                    appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}), tier ${VaporSettings.tierName}")
                    appendLine("Fast Launch: ${VaporSettings.fastLaunch}")
                    appendLine("Secure Folder / other user: ${VaporPaths.isSecondaryUser()}")
                    if (gamescopeFailed) appendLine("Diagnosis: gamescope display failed to start -> next launch uses the classic display")
                    if (settings.isNotBlank()) {
                        appendLine()
                        appendLine("===== Game settings =====")
                        appendLine(settings.trim())
                    }
                    appendLine()
                    appendLine("===== Error lines (most useful part) =====")
                    if (keyLines.isEmpty()) appendLine("(none found)") else keyLines.forEach { appendLine(it) }
                    unityLog?.let {
                        appendLine()
                        appendLine("===== Unity log: ${it.parentFile?.name}/${it.name} (last 200 lines) =====")
                        appendLine(CrashReportFormat.tail(it, 200))
                    }
                    unrealLog?.let {
                        appendLine()
                        appendLine("===== Unreal log: ${it.name} (last 200 lines) =====")
                        appendLine(CrashReportFormat.tail(it, 200))
                    }
                    appendLine()
                    appendLine("===== App + Wine log (last $TAIL_LINES lines, file unpacking removed) =====")
                    cleaned.takeLast(TAIL_LINES).forEach { appendLine(it) }
                },
            )
            Timber.i("[Vapor] saved game crash log to ${out.absolutePath}")
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] could not save crash log")
        }
        return gamescopeFailed
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
