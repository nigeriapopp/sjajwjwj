package app.gamenative.vapor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassifierTest {

    private fun snap(
        model: String = "Pixel",
        soc: String = "",
        hardware: String = "",
        gpu: String = "",
        ramMb: Long = 8192,
        refresh: Int = 120,
    ) = DeviceSnapshot(
        manufacturer = "x",
        model = model,
        device = "",
        socModel = soc,
        hardware = hardware,
        gpuRenderer = gpu,
        totalRamMb = ramMb,
        cpuCores = 8,
        maxCpuMhz = 3000,
        maxRefreshHz = refresh,
        sdkInt = 34,
    )

    @Test
    fun galaxyS22Snapdragon() {
        val p = DeviceClassifier.classify(snap(model = "SM-S901U", soc = "SM8450", gpu = "Adreno (TM) 730"))
        assertEquals("Samsung Galaxy S22", p.knownDeviceName)
        assertEquals("Snapdragon 8 Gen 1", p.chipName)
        assertEquals(DeviceTier.HIGH, p.tier)
        assertEquals(GpuFamily.ADRENO, p.gpuFamily)
        assertEquals(60, p.plan.fpsTarget)
        assertTrue(p.notes.any { it.contains("Turnip") })
    }

    @Test
    fun galaxyS22UltraExynos() {
        val p = DeviceClassifier.classify(snap(model = "SM-S908B", soc = "s5e9925", gpu = "Samsung Xclipse 920"))
        assertEquals("Samsung Galaxy S22 Ultra", p.knownDeviceName)
        assertEquals("Exynos 2200", p.chipName)
        assertEquals(GpuFamily.XCLIPSE, p.gpuFamily)
        assertEquals(DeviceTier.HIGH, p.tier)
        assertTrue(p.notes.any { it.contains("Xclipse") })
    }

    @Test
    fun socSuffixAndFlagship() {
        val p = DeviceClassifier.classify(snap(soc = "SM8650-AC", gpu = "Adreno (TM) 750"))
        assertEquals(DeviceTier.FLAGSHIP, p.tier)
        assertEquals(60, p.plan.fpsTarget)
    }

    @Test
    fun unknownSocFallsBackToGpu() {
        val p = DeviceClassifier.classify(snap(gpu = "Mali-G57 MC2", refresh = 60))
        assertEquals(DeviceTier.LOW, p.tier)
        assertEquals(30, p.plan.fpsTarget)
        assertEquals("960x540", p.plan.screenSize)
    }

    @Test
    fun lowRamCapsTier() {
        val p = DeviceClassifier.classify(snap(soc = "SM8650", gpu = "Adreno (TM) 750", ramMb = 4000))
        assertEquals(DeviceTier.LOW, p.tier)
    }

    @Test
    fun midTierUses45OnlyOn90Hz() {
        val at90 = DeviceClassifier.classify(snap(soc = "SM7325", refresh = 90))
        val at120 = DeviceClassifier.classify(snap(soc = "SM7325", refresh = 120))
        assertEquals(DeviceTier.MID, at90.tier)
        assertEquals(45, at90.plan.fpsTarget)
        assertEquals(30, at120.plan.fpsTarget)
    }

    @Test
    fun reportMentionsKeyFacts() {
        val r = DeviceClassifier.classify(snap(model = "SM-S906E", soc = "SM8450", gpu = "Adreno (TM) 730")).toReport()
        assertTrue(r.contains("Galaxy S22+"))
        assertTrue(r.contains("FPS cap: 60"))
    }
}
