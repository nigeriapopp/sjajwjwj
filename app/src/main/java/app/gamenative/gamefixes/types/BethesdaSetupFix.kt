package app.gamenative.gamefixes

import android.content.Context
import app.gamenative.vapor.BethesdaSetup
import app.gamenative.vapor.DeviceTier
import app.gamenative.vapor.VaporSettings
import com.winlator.container.Container
import com.winlator.core.envvars.EnvVars
import com.winlator.xenvironment.ImageFs
import java.io.File
import timber.log.Timber

/**
 * Makes Bethesda's Gamebryo games (Oblivion, Fallout 3, New Vegas) start reliably under Wine on a phone:
 *  - launches the game exe instead of the launcher (the launcher's Play button is a common crash point,
 *    and it needs a mouse click on a phone);
 *  - creates the user ini from the shipped default and the prefs ini from the shipped Low/Medium preset
 *    when they are missing, so the game never starts with half-empty settings;
 *  - windowed at exactly the container's resolution (fullscreen mode switches are a classic Wine crash);
 *  - reports 4 CPU cores to the game, on the phone's fast cores (Proton's WINE_CPU_TOPOLOGY), which
 *    avoids the many-core startup crash even before the ini fix is in place;
 *  - after the game crashes once, turns off winegstreamer for it (Crash Guard safe mode).
 */
class BethesdaSetupFix(private val spec: BethesdaSetup.Spec) : GameFix {
    override fun apply(
        context: Context,
        gameId: String,
        installPath: String,
        installPathWindows: String,
        container: Container,
    ): Boolean {
        val install = File(installPath)
        var changed = false

        runCatching {
            val exe = BethesdaSetup.pickExecutable(container.executablePath, spec, install)
            if (exe != null && exe != container.executablePath) {
                Timber.tag("GameFixes").i("Vapor: launching ${spec.gameExe} directly instead of '${container.executablePath}'")
                container.executablePath = exe
                changed = true
            }
        }.onFailure { Timber.tag("GameFixes").w(it, "Vapor: exe check failed") }

        runCatching {
            val env = EnvVars(container.envVars)
            var envChanged = false
            if (!env.has("WINE_CPU_TOPOLOGY")) {
                env.put("WINE_CPU_TOPOLOGY", BethesdaSetup.cpuTopologyFromFreqs(readMaxFreqs()))
                envChanged = true
            }
            // 32-bit game: let it use 4 GB instead of 2 GB (DXVK's allocations otherwise run it out of
            // address space mid-game). Proton reads this; patching the exe instead breaks New Vegas.
            if (!env.has("WINE_LARGE_ADDRESS_AWARE")) {
                env.put("WINE_LARGE_ADDRESS_AWARE", "1")
                envChanged = true
            }
            // Crash Guard: after this game crashed, turn off Wine's media decoder for it
            // (costs in-game music/radio, but is the best-known fix for these crashes under Wine).
            val current = if (env.has("WINEDLLOVERRIDES")) env.get("WINEDLLOVERRIDES") else ""
            val updated = when {
                VaporSettings.crashCount(container.id) >= 1 -> BethesdaSetup.withoutGstreamer(current)
                // Crash Guard was reset: give the music back (only if Vapor turned it off).
                VaporSettings.isSafeModeOn(container.id) -> BethesdaSetup.withGstreamer(current)
                else -> current
            }
            if (updated != current) {
                if (updated.isEmpty()) env.remove("WINEDLLOVERRIDES") else env.put("WINEDLLOVERRIDES", updated)
                envChanged = true
                VaporSettings.setSafeModeOn(container.id, updated.contains("winegstreamer=d"))
                if (updated.contains("winegstreamer=d")) {
                    Timber.tag("GameFixes").i("Vapor: crash safe mode for $gameId: winegstreamer off")
                    app.gamenative.ui.util.SnackbarManager.show(
                        "Crash Guard: safe mode on for this game (music may be off). Settings → Performance to share the crash log.",
                    )
                }
            }
            if (envChanged) {
                container.envVars = env.toString()
                changed = true
            }
        }.onFailure { Timber.tag("GameFixes").w(it, "Vapor: env setup failed") }

        if (changed) runCatching { container.saveData() }

        runCatching {
            val myGames = File(container.rootDir, ".wine/drive_c/users/${ImageFs.USER}/Documents/My Games/${spec.myGamesFolder}")
            val tier = runCatching { DeviceTier.valueOf(VaporSettings.tierName) }.getOrNull() ?: DeviceTier.HIGH
            val (w, h) = BethesdaSetup.parseSize(container.screenSize)
            val made = BethesdaSetup.prepareIniFiles(install, myGames, spec, tier, w, h)
            if (made.isNotEmpty()) Timber.tag("GameFixes").i("Vapor: created ${made.joinToString()} for $gameId")
        }.onFailure { Timber.tag("GameFixes").w(it, "Vapor: ini setup failed") }

        return true
    }

    private fun readMaxFreqs(): List<Long> {
        val n = Runtime.getRuntime().availableProcessors()
        return (0 until n).map { i ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrDefault(0L)
        }
    }
}
