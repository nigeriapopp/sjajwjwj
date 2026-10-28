package app.gamenative.vapor

import java.io.File
import java.nio.charset.StandardCharsets

/** Pure logic for BethesdaSetupFix (unit tested). */
object BethesdaSetup {
    data class Spec(
        val gameExe: String,
        val launcherExe: String,
        val myGamesFolder: String,
        /** Shipped default for the user ini, e.g. Fallout_default.ini. */
        val defaultIni: String,
        val userIni: String,
        /** Prefs ini the launcher normally writes; null for Oblivion (settings live in the user ini). */
        val prefsIni: String?,
    )

    val FALLOUT3 = Spec("Fallout3.exe", "FalloutLauncher.exe", "Fallout3", "Fallout_default.ini", "Fallout.ini", "FalloutPrefs.ini")
    val NEW_VEGAS = Spec("FalloutNV.exe", "FalloutNVLauncher.exe", "FalloutNV", "Fallout_default.ini", "Fallout.ini", "FalloutPrefs.ini")
    val OBLIVION = Spec("Oblivion.exe", "OblivionLauncher.exe", "Oblivion", "Oblivion_default.ini", "Oblivion.ini", null)

    /**
     * The exe to launch: the game itself when the current pick is empty or the launcher.
     * Keeps any folder prefix. Returns null when nothing should change.
     */
    fun pickExecutable(current: String, spec: Spec, installDir: File): String? {
        val name = current.substringAfterLast('/').substringAfterLast('\\')
        if (current.isNotEmpty() && !name.equals(spec.launcherExe, ignoreCase = true)) return null
        val prefix = current.substring(0, current.length - name.length)
        val candidate = prefix + spec.gameExe
        val onDisk = File(installDir, candidate.replace('\\', '/'))
        if (onDisk.isFile) return candidate
        // Case differs on disk (Steam keeps the depot's spelling).
        val dir = onDisk.parentFile ?: return null
        val match = dir.listFiles()?.firstOrNull { it.isFile && it.name.equals(spec.gameExe, ignoreCase = true) } ?: return null
        return prefix + match.name
    }

    /**
     * Tell the game it has 4 cores, on the fast ones. Phones list little cores first, so the last
     * four are the big ones (on the S22: three A710 + the X2).
     */
    fun cpuTopology(cores: Int): String {
        if (cores <= 4) return "$cores:" + (0 until cores).joinToString(",")
        return "4:" + (cores - 4 until cores).joinToString(",")
    }

    /**
     * Same, but picks the 4 cores with the highest max clock ([maxFreqs] per core, 0 = unknown).
     * Falls back to [cpuTopology] when the clocks can't be read.
     */
    fun cpuTopologyFromFreqs(maxFreqs: List<Long>): String {
        if (maxFreqs.size <= 4 || maxFreqs.all { it <= 0L }) return cpuTopology(maxFreqs.size)
        val fastest = maxFreqs.indices
            .sortedWith(compareByDescending<Int> { maxFreqs[it] }.thenByDescending { it })
            .take(4)
            .sorted()
        return "4:" + fastest.joinToString(",")
    }

    /** Removes the winegstreamer=d entry Crash Guard added (after "Reset Crash Guard"). */
    fun withGstreamer(overrides: String): String =
        overrides.split(';').filter { it.isNotBlank() && !it.trim().equals("winegstreamer=d", ignoreCase = true) }
            .joinToString(";")

    /** Adds winegstreamer=d (Wine's media decoder: a known Fallout 3 / Oblivion crash) to a DLL override list. */
    fun withoutGstreamer(overrides: String): String {
        if (overrides.contains("winegstreamer", ignoreCase = true)) return overrides
        return if (overrides.isBlank()) "winegstreamer=d" else overrides.trimEnd(';') + ";winegstreamer=d"
    }

    fun parseSize(screenSize: String?): Pair<Int, Int> {
        val parts = screenSize?.lowercase()?.split("x")?.mapNotNull { it.trim().toIntOrNull() }
        return if (parts != null && parts.size == 2 && parts[0] > 0 && parts[1] > 0) parts[0] to parts[1] else 1280 to 720
    }

    /** Preset file shipped in the install folder that the launcher would copy to the prefs ini. */
    fun presetFor(installDir: File, tier: DeviceTier): File? {
        val order = when (tier) {
            DeviceTier.FLAGSHIP -> listOf("Medium.ini", "Low.ini")
            else -> listOf("Low.ini", "Medium.ini")
        }
        val files = installDir.listFiles() ?: return null
        for (name in order) {
            files.firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    /** Display values every prefs file gets: windowed at the container size, light shadows, no MSAA. */
    fun displayValues(tier: DeviceTier, width: Int, height: Int): Map<String, String> =
        linkedMapOf(
            "iSize W" to width.toString(),
            "iSize H" to height.toString(),
            "bFull Screen" to "0",
        ) + PhoneGraphicsPresets.bethesdaDisplay(tier)

    /**
     * Creates missing ini files. Existing files are only given the display size/window values
     * when they were created by Vapor before (never overwrites a player's own settings otherwise).
     * @return names of the files created
     */
    fun prepareIniFiles(installDir: File, myGames: File, spec: Spec, tier: DeviceTier, width: Int, height: Int): List<String> {
        val made = mutableListOf<String>()
        val cs = StandardCharsets.ISO_8859_1
        myGames.mkdirs()

        val user = File(myGames, spec.userIni)
        if (!user.isFile) {
            val default = installDir.listFiles()?.firstOrNull { it.isFile && it.name.equals(spec.defaultIni, ignoreCase = true) }
            if (default != null) {
                var text = default.readText(cs)
                text = IniEdit.set(text, "General", "bUseThreadedAI", "1")
                text = IniEdit.set(text, "General", "iNumHWThreads", "2")
                if (spec.prefsIni == null) {
                    // Oblivion keeps display settings in the user ini.
                    displayValues(tier, width, height).forEach { (k, v) -> text = IniEdit.set(text, "Display", k, v) }
                }
                user.writeText(text, cs)
                made += spec.userIni
            }
        }

        val prefsName = spec.prefsIni ?: return made
        val prefs = File(myGames, prefsName)
        if (!prefs.isFile) {
            val preset = presetFor(installDir, tier)
            var text = preset?.readText(cs) ?: ""
            displayValues(tier, width, height).forEach { (k, v) -> text = IniEdit.set(text, "Display", k, v) }
            if (preset != null) {
                prefs.writeText(text, cs)
                made += prefsName
            }
        }
        return made
    }
}
