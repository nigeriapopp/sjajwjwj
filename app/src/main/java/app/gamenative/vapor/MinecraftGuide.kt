package app.gamenative.vapor

/** Phone-tuned Minecraft settings and the setup guide (no Android types, unit tested). */
object MinecraftGuide {
    /** In-launcher settings that suit this phone. */
    data class Tuning(
        val ramMb: Int,
        val renderDistance: Int,
        val resolutionPercent: Int,
        val fpsCap: Int,
    )

    fun tuningFor(totalRamMb: Long, tier: DeviceTier): Tuning {
        val ram = when {
            totalRamMb >= 11_500 -> 4096
            totalRamMb >= 7_500 -> 3072
            totalRamMb >= 5_500 -> 2048
            else -> 1536
        }
        return when (tier) {
            DeviceTier.FLAGSHIP -> Tuning(ram, 12, 100, 60)
            DeviceTier.HIGH -> Tuning(ram, 10, 85, 60)
            DeviceTier.MID -> Tuning(ram, 8, 70, 45)
            DeviceTier.LOW -> Tuning(ram, 6, 60, 30)
        }
    }

    data class Step(val title: String, val body: String)

    fun guide(t: Tuning): List<Step> = listOf(
        Step(
            "1. Install the launcher",
            "Tap Get launcher below, download the APK from its official GitHub page, and install it. It's free and open source.",
        ),
        Step(
            "2. Sign in with Microsoft",
            "Open the launcher → Accounts → Microsoft. Use the account that owns Minecraft: Java Edition (Java & Bedrock bundle works).",
        ),
        Step(
            "3. Install a version",
            "Pick a release (1.20.1 or 1.21.x are great on phones). Choose Fabric as the mod loader — it's the lightest.",
        ),
        Step(
            "4. Add performance mods",
            "In the launcher's mod browser search Modrinth for Sodium, Lithium and FerriteCore. Or install the \"Fabulously Optimized\" modpack, which bundles them.",
        ),
        Step(
            "5. Tune it for this phone",
            "Memory: ${t.ramMb} MB · Render distance: ${t.renderDistance} chunks · Resolution: ${t.resolutionPercent}% · Max FPS: ${t.fpsCap}. " +
                "If the screen stays black, switch the renderer in the version's settings.",
        ),
        Step(
            "6. Add more mods",
            "Search mods, resource packs and shaders right inside the launcher (Modrinth and CurseForge). Check each mod matches your Minecraft version and Fabric.",
        ),
    )
}
