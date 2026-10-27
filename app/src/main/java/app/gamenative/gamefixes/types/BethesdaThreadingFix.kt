package app.gamenative.gamefixes

import android.content.Context
import app.gamenative.vapor.IniEdit
import com.winlator.container.Container
import com.winlator.xenvironment.ImageFs
import java.io.File
import java.nio.charset.StandardCharsets
import timber.log.Timber

/**
 * Fallout 3, New Vegas and Oblivion crash at startup on CPUs with many cores (every modern phone).
 * The well-known fix is `bUseThreadedAI=1` and `iNumHWThreads=2` in the [General] section.
 *
 * Patches the default ini in the install folder (used to create the user ini on first launch) and
 * the user ini in Documents\My Games if it already exists.
 */
class BethesdaThreadingFix(
    private val myGamesFolder: String,
    private val defaultIni: String,
    private val userIni: String,
) : GameFix {
    override fun apply(
        context: Context,
        gameId: String,
        installPath: String,
        installPathWindows: String,
        container: Container,
    ): Boolean {
        val targets = listOf(
            File(installPath, defaultIni),
            File(
                container.rootDir,
                ".wine/drive_c/users/${ImageFs.USER}/Documents/My Games/$myGamesFolder/$userIni",
            ),
        )
        var patchedAny = false
        for (file in targets) {
            if (!file.isFile) continue
            runCatching {
                val original = file.readText(StandardCharsets.ISO_8859_1)
                var updated = IniEdit.set(original, "General", "bUseThreadedAI", "1")
                updated = IniEdit.set(updated, "General", "iNumHWThreads", "2")
                if (updated != original) {
                    file.writeText(updated, StandardCharsets.ISO_8859_1)
                    Timber.tag("GameFixes").i("Vapor: many-core fix applied to ${file.name} for $gameId")
                }
                patchedAny = true
            }.onFailure { Timber.tag("GameFixes").w(it, "Vapor: could not patch ${file.name}") }
        }
        return patchedAny
    }
}
