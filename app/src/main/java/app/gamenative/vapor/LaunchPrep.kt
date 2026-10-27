package app.gamenative.vapor

import android.content.Context
import app.gamenative.ui.util.SnackbarManager
import java.io.File
import timber.log.Timber

/** Runs right before Wine starts a game: save-folder check and phone graphics presets. */
object LaunchPrep {
    /** Where Windows games keep saves. Missing or read-only folders make some games fail to save. */
    val SAVE_DIRS = listOf(
        "Documents",
        "Documents/My Games",
        "Saved Games",
        "AppData/Local",
        "AppData/LocalLow",
        "AppData/Roaming",
    )

    /** Makes sure every save folder exists and is writable. Returns how many were fixed. */
    fun ensureSaveFolders(userDir: File): Int {
        var fixed = 0
        for (rel in SAVE_DIRS) {
            val dir = File(userDir, rel)
            try {
                if (dir.exists() && !dir.isDirectory) {
                    // A stray file where a folder should be: move it aside.
                    dir.renameTo(File(dir.parentFile, dir.name + ".vapor-moved"))
                }
                if (!dir.isDirectory && dir.mkdirs()) fixed++
                if (dir.isDirectory && !dir.canWrite() && dir.setWritable(true, false)) fixed++
            } catch (e: Exception) {
                Timber.w(e, "[Vapor] save folder check failed for $rel")
            }
        }
        return fixed
    }

    fun run(context: Context, gameKey: String, containerRoot: File, installDir: File?) {
        VaporSettings.init(context)
        val userDir = SaveSnapshots.userDir(containerRoot)
        if (userDir.isDirectory) {
            val fixed = ensureSaveFolders(userDir)
            if (fixed > 0) Timber.i("[Vapor] fixed $fixed save folders")
        }

        if (installDir == null || !installDir.isDirectory || !VaporSettings.phonePresets) return
        if (VaporSettings.presetsApplied(gameKey)) return
        try {
            val tier = runCatching { DeviceTier.valueOf(VaporSettings.tierName) }.getOrNull()
                ?: VaporAutoTune.profile(context).tier
            val applied = PhoneGraphicsPresets.apply(installDir, userDir, tier, VaporAutoTune.defaultFpsTarget(context))
            if (applied != null) {
                VaporSettings.markPresetsApplied(gameKey)
                Timber.i("[Vapor] phone graphics presets applied: $applied")
                SnackbarManager.show("Graphics set for your phone (${tier.label}). Change them any time in the game's options.")
            }
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] phone presets failed")
        }
    }
}
