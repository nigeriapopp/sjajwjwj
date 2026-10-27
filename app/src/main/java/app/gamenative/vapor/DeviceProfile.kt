package app.gamenative.vapor

import java.util.Locale

/**
 * Hardware facts Vapor uses to pick per-phone defaults. Kept free of Android types so the
 * classifier can be unit tested on the JVM.
 */
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val socModel: String,
    val hardware: String,
    val gpuRenderer: String,
    val totalRamMb: Long,
    val cpuCores: Int,
    val maxCpuMhz: Int,
    val maxRefreshHz: Int,
    val sdkInt: Int,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)

enum class DeviceTier(val label: String) {
    FLAGSHIP("Flagship"),
    HIGH("High-end"),
    MID("Mid-range"),
    LOW("Entry-level"),
}

enum class GpuFamily(val label: String) {
    ADRENO("Qualcomm Adreno"),
    XCLIPSE("Samsung Xclipse (AMD RDNA)"),
    MALI("Arm Mali / Immortalis"),
    POWERVR("PowerVR"),
    UNKNOWN("Unknown GPU"),
}

/** What Vapor will apply for this phone. */
data class TuningPlan(
    val fpsTarget: Int,
    val screenSize: String,
    val showFps: Boolean,
)

data class DeviceProfile(
    val snapshot: DeviceSnapshot,
    val tier: DeviceTier,
    val gpuFamily: GpuFamily,
    val chipName: String,
    val knownDeviceName: String?,
    val score: Int,
    val plan: TuningPlan,
    val notes: List<String>,
) {
    /** Plain-text report shown in Settings > Phone Check. */
    fun toReport(): String = buildString {
        appendLine("Phone: ${knownDeviceName ?: "${snapshot.manufacturer} ${snapshot.model}"}")
        appendLine("Chip: $chipName")
        appendLine("GPU: ${snapshot.gpuRenderer.ifBlank { gpuFamily.label }}")
        appendLine("RAM: ${"%.1f".format(Locale.US, snapshot.totalRamMb / 1024.0)} GB · CPU cores: ${snapshot.cpuCores}")
        if (snapshot.maxCpuMhz > 0) appendLine("Max CPU clock: ${snapshot.maxCpuMhz} MHz")
        appendLine("Display: up to ${snapshot.maxRefreshHz} Hz")
        appendLine()
        appendLine("Performance class: ${tier.label} (score $score/100)")
        appendLine()
        appendLine("Vapor defaults for new games:")
        appendLine("• FPS cap: ${plan.fpsTarget}")
        appendLine("• Game resolution: ${plan.screenSize}")
        appendLine("• FPS counter: ${if (plan.showFps) "on" else "off"}")
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("Notes for this phone:")
            notes.forEach { appendLine("• $it") }
        }
    }.trimEnd()
}

object DeviceClassifier {

    private data class Chip(val name: String, val score: Int)

    /** Known SoCs, keyed by lower-case Build.SOC_MODEL / Build.HARDWARE / board codename. */
    private val chips: Map<String, Chip> = mapOf(
        // Snapdragon 8 series
        "sm8850" to Chip("Snapdragon 8 Elite Gen 5", 100),
        "sm8750" to Chip("Snapdragon 8 Elite", 96),
        "sun" to Chip("Snapdragon 8 Elite", 96),
        "sm8650" to Chip("Snapdragon 8 Gen 3", 90),
        "pineapple" to Chip("Snapdragon 8 Gen 3", 90),
        "sm8550" to Chip("Snapdragon 8 Gen 2", 84),
        "kalama" to Chip("Snapdragon 8 Gen 2", 84),
        "sm8475" to Chip("Snapdragon 8+ Gen 1", 78),
        "sm8450" to Chip("Snapdragon 8 Gen 1", 74),
        "taro" to Chip("Snapdragon 8 Gen 1", 74),
        "sm8350" to Chip("Snapdragon 888", 66),
        "lahaina" to Chip("Snapdragon 888", 66),
        "sm8250" to Chip("Snapdragon 865/870", 60),
        "kona" to Chip("Snapdragon 865/870", 60),
        "sm8150" to Chip("Snapdragon 855", 52),
        "msmnile" to Chip("Snapdragon 855", 52),
        "sdm845" to Chip("Snapdragon 845", 42),
        // Snapdragon 7/6 series
        "sm8635" to Chip("Snapdragon 8s Gen 3", 80),
        "sm7675" to Chip("Snapdragon 7+ Gen 3", 70),
        "sm7550" to Chip("Snapdragon 7 Gen 3", 50),
        "sm7475" to Chip("Snapdragon 7+ Gen 2", 62),
        "sm7450" to Chip("Snapdragon 7 Gen 1", 46),
        "sm7325" to Chip("Snapdragon 778G", 46),
        "sm7225" to Chip("Snapdragon 750G", 34),
        "sm6375" to Chip("Snapdragon 695", 28),
        // Samsung Exynos
        "s5e9955" to Chip("Exynos 2500", 86),
        "s5e9945" to Chip("Exynos 2400", 80),
        "s5e9925" to Chip("Exynos 2200", 66),
        "s5e9840" to Chip("Exynos 2100", 52),
        "s5e8845" to Chip("Exynos 1480", 40),
        "s5e8835" to Chip("Exynos 1380", 34),
        // MediaTek Dimensity
        "mt6993" to Chip("Dimensity 9500", 96),
        "mt6991" to Chip("Dimensity 9400", 92),
        "mt6989" to Chip("Dimensity 9300", 86),
        "mt6985" to Chip("Dimensity 9200", 78),
        "mt6983" to Chip("Dimensity 9000", 70),
        "mt6895" to Chip("Dimensity 8100", 58),
        "mt6896" to Chip("Dimensity 8200", 60),
        "mt6897" to Chip("Dimensity 8300", 66),
        "mt6877" to Chip("Dimensity 900/1080", 38),
        // Google Tensor
        "gs101" to Chip("Google Tensor", 56),
        "gs201" to Chip("Google Tensor G2", 60),
        "zuma" to Chip("Google Tensor G3", 64),
        "zumapro" to Chip("Google Tensor G4", 66),
        "laguna" to Chip("Google Tensor G5", 72),
    )

    private val galaxyS22Models = mapOf(
        "sm-s901" to "Samsung Galaxy S22",
        "sm-s906" to "Samsung Galaxy S22+",
        "sm-s908" to "Samsung Galaxy S22 Ultra",
    )

    fun gpuFamily(renderer: String): GpuFamily {
        val r = renderer.lowercase(Locale.ENGLISH)
        return when {
            r.contains("adreno") -> GpuFamily.ADRENO
            r.contains("xclipse") || r.contains("samsung amd") -> GpuFamily.XCLIPSE
            r.contains("mali") || r.contains("immortalis") -> GpuFamily.MALI
            r.contains("powervr") -> GpuFamily.POWERVR
            else -> GpuFamily.UNKNOWN
        }
    }

    /** Rough GPU score when the SoC itself is unknown. */
    internal fun scoreFromGpu(renderer: String): Int? {
        val r = renderer.lowercase(Locale.ENGLISH)
        Regex("adreno[^0-9]*([0-9]{3})").find(r)?.let { m ->
            val n = m.groupValues[1].toInt()
            return when {
                n >= 840 -> 100
                n >= 830 -> 96
                n >= 750 -> 90
                n >= 740 -> 84
                n >= 730 -> 74
                n >= 725 -> 62
                n >= 710 -> 44
                n >= 660 -> 66
                n >= 650 -> 60
                n >= 640 -> 52
                n >= 630 -> 42
                n >= 600 -> 28
                else -> 18
            }
        }
        if (r.contains("xclipse")) {
            return when {
                r.contains("950") -> 86
                r.contains("940") -> 80
                r.contains("930") -> 72
                r.contains("920") -> 66
                else -> 40
            }
        }
        if (r.contains("immortalis")) return 86
        Regex("mali-g([0-9]{2,3})").find(r)?.let { m ->
            val n = m.groupValues[1].toInt()
            return when {
                n >= 925 -> 92
                n >= 720 -> 80
                n >= 715 -> 72
                n >= 710 -> 64
                n >= 78 -> 54
                n >= 77 -> 46
                n >= 68 -> 34
                n >= 57 -> 24
                else -> 16
            }
        }
        return null
    }

    private fun lookupChip(s: DeviceSnapshot): Chip? {
        val keys = listOf(s.socModel, s.hardware, s.device)
            .map { it.lowercase(Locale.ENGLISH).trim() }
            .filter { it.isNotEmpty() }
        for (key in keys) {
            chips[key]?.let { return it }
            // SOC_MODEL often carries a suffix, e.g. "SM8450-AB"
            chips.entries.firstOrNull { key.startsWith(it.key) && it.key.length >= 5 }?.let { return it.value }
        }
        return null
    }

    fun knownDeviceName(s: DeviceSnapshot): String? {
        val model = s.model.lowercase(Locale.ENGLISH)
        return galaxyS22Models.entries.firstOrNull { model.startsWith(it.key) }?.value
    }

    fun isGalaxyS22(s: DeviceSnapshot): Boolean = knownDeviceName(s) != null

    fun classify(s: DeviceSnapshot): DeviceProfile {
        val family = gpuFamily(s.gpuRenderer)
        val chip = lookupChip(s)
        var score = chip?.score ?: scoreFromGpu(s.gpuRenderer) ?: 35

        // Memory is a hard limit for PC games: Wine + the game + Android need room.
        when {
            s.totalRamMb in 1..4500 -> score = minOf(score, 30)
            s.totalRamMb in 4501..6500 -> score = minOf(score, 55)
        }
        score = score.coerceIn(0, 100)

        val tier = when {
            score >= 80 -> DeviceTier.FLAGSHIP
            score >= 60 -> DeviceTier.HIGH
            score >= 38 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }

        val refresh = if (s.maxRefreshHz > 0) s.maxRefreshHz else 60
        val fps = when (tier) {
            DeviceTier.FLAGSHIP, DeviceTier.HIGH -> 60
            // 45 paces evenly only on a 90 Hz panel; everywhere else 30 is smoother than 45.
            DeviceTier.MID -> if (refresh % 45 == 0) 45 else 30
            DeviceTier.LOW -> 30
        }
        val resolution = when (tier) {
            DeviceTier.LOW -> "960x540"
            else -> "1280x720"
        }

        val notes = mutableListOf<String>()
        val s22 = knownDeviceName(s)
        if (s22 != null) {
            when (family) {
                GpuFamily.ADRENO -> {
                    notes += "Snapdragon S22: uses the Turnip open-source Adreno driver for the best DirectX compatibility."
                    notes += "The Snapdragon 8 Gen 1 throttles when hot. For long sessions of heavy 3D games, drop the in-game FPS cap to 40 or 45."
                }
                GpuFamily.XCLIPSE -> {
                    notes += "Exynos S22: Turnip doesn't support Xclipse GPUs, so Vapor uses Samsung's own Vulkan driver."
                    notes += "If a game shows a black screen, try the other DXVK version or WineD3D in the game's settings."
                }
                else -> {}
            }
            notes += "Turn on Game Booster's performance profile and take the phone out of its case for steadier FPS."
        }
        if (family == GpuFamily.MALI) {
            notes += "Mali GPUs run through the system Vulkan driver. DX11 games work best; some DX12 titles may not start."
        }
        if (s.totalRamMb in 1..6500) {
            notes += "With under 8 GB of RAM, close other apps before playing and stick to older or lighter games."
        }
        if (refresh >= 120 && fps == 60) {
            notes += "Your ${refresh} Hz screen paces 30, 40 and 60 FPS caps evenly."
        }
        notes += when (tier) {
            DeviceTier.FLAGSHIP -> "Expect 60 FPS in most indie and 2010s games; many newer games run at 30-60 FPS on low settings."
            DeviceTier.HIGH -> "Expect 60 FPS in indie and older games; 30-45 FPS in 2015-2019 3D games on low settings."
            DeviceTier.MID -> "Expect smooth play in 2D, indie and older 3D games. Newer 3D games may be too heavy."
            DeviceTier.LOW -> "Best for 2D, indie and early-2000s games. Most modern 3D games will be too slow."
        }

        return DeviceProfile(
            snapshot = s,
            tier = tier,
            gpuFamily = family,
            chipName = chip?.name ?: s.socModel.ifBlank { s.hardware.ifBlank { "Unknown" } },
            knownDeviceName = s22,
            score = score,
            plan = TuningPlan(fpsTarget = fps, screenSize = resolution, showFps = true),
            notes = notes,
        )
    }
}
