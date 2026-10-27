package app.gamenative.vapor

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Converts a PC game's graphics settings to values a phone can run well, on the game's first launch.
 * Only touches engines whose settings files are well known; after that the player's own in-game
 * changes are kept.
 *
 *  - Unreal Engine 4/5: writes the scalability groups (sg.*) and frame limit in GameUserSettings.ini.
 *    Shadows, foliage and effects cost the most on mobile GPUs, so they drop first; textures stay higher
 *    because phones have plenty of shared memory.
 *  - Bethesda (Oblivion, Fallout 3/NV/4, Skyrim): shadow map size, anti-aliasing, depth of field.
 */
object PhoneGraphicsPresets {
    /** 0 = Low, 1 = Medium, 2 = High, 3 = Epic. ResolutionQuality is a % of the output resolution. */
    fun unrealScalability(tier: DeviceTier): Map<String, String> = when (tier) {
        DeviceTier.FLAGSHIP -> mapOf(
            "sg.ResolutionQuality" to "85", "sg.ViewDistanceQuality" to "2", "sg.AntiAliasingQuality" to "1",
            "sg.ShadowQuality" to "1", "sg.PostProcessQuality" to "1", "sg.TextureQuality" to "2",
            "sg.EffectsQuality" to "1", "sg.FoliageQuality" to "1", "sg.ShadingQuality" to "1",
            "sg.GlobalIlluminationQuality" to "1", "sg.ReflectionQuality" to "1",
        )
        DeviceTier.HIGH -> mapOf(
            "sg.ResolutionQuality" to "75", "sg.ViewDistanceQuality" to "1", "sg.AntiAliasingQuality" to "1",
            "sg.ShadowQuality" to "0", "sg.PostProcessQuality" to "1", "sg.TextureQuality" to "2",
            "sg.EffectsQuality" to "0", "sg.FoliageQuality" to "0", "sg.ShadingQuality" to "1",
            "sg.GlobalIlluminationQuality" to "0", "sg.ReflectionQuality" to "0",
        )
        DeviceTier.MID -> mapOf(
            "sg.ResolutionQuality" to "66", "sg.ViewDistanceQuality" to "0", "sg.AntiAliasingQuality" to "0",
            "sg.ShadowQuality" to "0", "sg.PostProcessQuality" to "0", "sg.TextureQuality" to "1",
            "sg.EffectsQuality" to "0", "sg.FoliageQuality" to "0", "sg.ShadingQuality" to "0",
            "sg.GlobalIlluminationQuality" to "0", "sg.ReflectionQuality" to "0",
        )
        DeviceTier.LOW -> mapOf(
            "sg.ResolutionQuality" to "50", "sg.ViewDistanceQuality" to "0", "sg.AntiAliasingQuality" to "0",
            "sg.ShadowQuality" to "0", "sg.PostProcessQuality" to "0", "sg.TextureQuality" to "0",
            "sg.EffectsQuality" to "0", "sg.FoliageQuality" to "0", "sg.ShadingQuality" to "0",
            "sg.GlobalIlluminationQuality" to "0", "sg.ReflectionQuality" to "0",
        )
    }

    fun unrealGameSettings(fpsCap: Int): Map<String, String> = mapOf(
        "FrameRateLimit" to "%.6f".format(java.util.Locale.US, fpsCap.toFloat()),
        "bUseVSync" to "False",
        "bUseDynamicResolution" to "False",
    )

    /** [Display] values for Bethesda prefs ini files. */
    fun bethesdaDisplay(tier: DeviceTier): Map<String, String> = mapOf(
        "iShadowMapResolution" to if (tier == DeviceTier.FLAGSHIP) "1024" else "512",
        "iMultiSample" to "0",
        "bDoDepthOfField" to "0",
        "iMaxAnisotropy" to when (tier) {
            DeviceTier.FLAGSHIP -> "4"
            DeviceTier.HIGH -> "2"
            else -> "0"
        },
        "bTransparencyMultisampling" to "0",
    )

    /** Bethesda games by executable: My Games folder and prefs file. */
    private val BETHESDA = listOf(
        Triple("Oblivion.exe", "Oblivion", "Oblivion.ini"),
        Triple("Fallout3.exe", "Fallout3", "FalloutPrefs.ini"),
        Triple("FalloutNV.exe", "FalloutNV", "FalloutPrefs.ini"),
        Triple("TESV.exe", "Skyrim", "SkyrimPrefs.ini"),
        Triple("SkyrimSE.exe", "Skyrim Special Edition", "SkyrimPrefs.ini"),
        Triple("Fallout4.exe", "Fallout4", "Fallout4Prefs.ini"),
    )

    /** Unreal project names: folders in the install dir that hold Content/Paks. */
    fun findUnrealProjects(installDir: File): List<String> {
        val out = mutableListOf<String>()
        fun visit(dir: File, depth: Int) {
            if (depth > 2) return
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (!c.isDirectory || c.name.equals("Engine", true)) continue
                if (File(c, "Content/Paks").isDirectory) out += c.name else visit(c, depth + 1)
            }
        }
        visit(installDir, 0)
        return out.distinct()
    }

    /**
     * Applies the presets. [userDir] is the Wine user profile (drive_c/users/xuser).
     * @return a short description of what changed, or null when nothing applied (try again next launch).
     */
    fun apply(installDir: File, userDir: File, tier: DeviceTier, fpsCap: Int): String? {
        val done = mutableListOf<String>()

        for (project in findUnrealProjects(installDir)) {
            val configRoot = File(userDir, "AppData/Local/$project/Saved/Config")
            // UE4 shipping builds use WindowsNoEditor, UE5 uses Windows: write both.
            for (platform in listOf("WindowsNoEditor", "Windows")) {
                val ini = File(configRoot, "$platform/GameUserSettings.ini")
                var text = if (ini.isFile) ini.readText() else ""
                unrealScalability(tier).forEach { (k, v) -> text = IniEdit.set(text, "ScalabilityGroups", k, v) }
                unrealGameSettings(fpsCap).forEach { (k, v) ->
                    text = IniEdit.set(text, "/Script/Engine.GameUserSettings", k, v)
                }
                ini.parentFile?.mkdirs()
                ini.writeText(text)
            }
            done += "Unreal ($project)"
        }

        for ((exe, folder, prefs) in BETHESDA) {
            if (!File(installDir, exe).isFile) continue
            val ini = File(userDir, "Documents/My Games/$folder/$prefs")
            // The game writes its prefs file on first run; patch it once it exists.
            if (!ini.isFile) continue
            var text = ini.readText(StandardCharsets.ISO_8859_1)
            bethesdaDisplay(tier).forEach { (k, v) -> text = IniEdit.set(text, "Display", k, v) }
            ini.writeText(text, StandardCharsets.ISO_8859_1)
            done += folder
        }

        return if (done.isEmpty()) null else done.joinToString()
    }
}
