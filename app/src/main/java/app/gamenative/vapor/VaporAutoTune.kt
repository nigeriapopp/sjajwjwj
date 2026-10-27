package app.gamenative.vapor

import android.app.ActivityManager
import android.app.GameManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import app.gamenative.PrefManager
import com.winlator.core.GPUInformation
import java.io.File
import timber.log.Timber

/**
 * Phone Auto-Tune: reads the phone's hardware once and sets sensible defaults for every game
 * (FPS cap, resolution, FPS counter). Users can re-run it from Settings > Performance.
 */
object VaporAutoTune {
    /** Bump when the tuning rules change so existing installs get re-tuned once. */
    const val CURRENT_VERSION = 1

    private const val GAME_MODE_BATTERY = 3 // GameManager.GAME_MODE_BATTERY (API 31)

    @Volatile
    private var cachedProfile: DeviceProfile? = null

    fun applyIfNeeded(context: Context) {
        VaporSettings.init(context)
        if (VaporSettings.autoTuneVersion >= CURRENT_VERSION) return
        apply(context)
    }

    /** Runs the tuner now and returns what it picked. */
    fun apply(context: Context): DeviceProfile {
        VaporSettings.init(context)
        val profile = profile(context, refresh = true)
        val plan = profile.plan
        try {
            VaporSettings.fpsTarget = plan.fpsTarget
            VaporSettings.showFpsByDefault = plan.showFps
            VaporSettings.tierName = profile.tier.name
            // Only lower the resolution on weak phones, and only from the stock 720p default,
            // so a resolution the user picked is never overwritten.
            if (profile.tier == DeviceTier.LOW && PrefManager.screenSize == "1280x720") {
                PrefManager.screenSize = plan.screenSize
            }
            // Strong phones: allow the "blazing" download tier (more parallel requests and unpack workers)
            // when the user is still on the stock "fast" tier.
            if ((profile.tier == DeviceTier.FLAGSHIP || profile.tier == DeviceTier.HIGH) &&
                profile.snapshot.cpuCores >= 8 && PrefManager.downloadSpeed == 24
            ) {
                PrefManager.downloadSpeed = 32
            }
            // Turn the in-game FPS counter on the first time only; later runs leave the user's choice.
            if (VaporSettings.autoTuneVersion == 0 && plan.showFps) {
                PrefManager.showFps = true
            }
            VaporSettings.autoTuneVersion = CURRENT_VERSION
            Timber.i("[Vapor] Auto-Tune: ${profile.chipName} -> ${profile.tier} fps=${plan.fpsTarget}")
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] Auto-Tune failed")
        }
        return profile
    }

    fun profile(context: Context, refresh: Boolean = false): DeviceProfile {
        if (!refresh) cachedProfile?.let { return it }
        val p = DeviceClassifier.classify(captureSnapshot(context))
        cachedProfile = p
        return p
    }

    /**
     * FPS cap for a game with no saved cap. Drops to 30 while Battery Saver or the Android
     * Game Dashboard "battery" mode is on.
     */
    fun defaultFpsTarget(context: Context?): Int {
        val base = VaporSettings.fpsTarget
        if (context == null || !VaporSettings.respectBatterySaver) return base
        return if (isBatteryMode(context)) minOf(base, 30) else base
    }

    fun isBatteryMode(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isPowerSaveMode == true) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val gm = context.getSystemService(GameManager::class.java)
                gm?.gameMode == GAME_MODE_BATTERY
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun captureSnapshot(context: Context): DeviceSnapshot {
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
        val renderer = try {
            GPUInformation.getRenderer(context).orEmpty()
        } catch (e: Throwable) {
            ""
        }
        val ramMb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        } catch (e: Exception) {
            0L
        }

        var maxRefresh = 60
        var width = 0
        var height = 0
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val modes = display.supportedModes
                val best = modes.maxByOrNull { it.refreshRate }
                if (best != null) maxRefresh = Math.round(best.refreshRate)
                val mode = display.mode
                width = maxOf(mode.physicalWidth, mode.physicalHeight)
                height = minOf(mode.physicalWidth, mode.physicalHeight)
            }
        } catch (e: Exception) {
            // keep defaults
        }

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.BOARD.orEmpty(),
            socModel = socModel,
            hardware = Build.HARDWARE.orEmpty(),
            gpuRenderer = renderer,
            totalRamMb = ramMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            maxCpuMhz = readMaxCpuMhz(),
            maxRefreshHz = maxRefresh,
            sdkInt = Build.VERSION.SDK_INT,
            screenWidth = width,
            screenHeight = height,
        )
    }

    private fun readMaxCpuMhz(): Int {
        var maxKhz = 0L
        val cores = Runtime.getRuntime().availableProcessors()
        for (i in 0 until cores) {
            try {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (f.canRead()) {
                    val v = f.readText().trim().toLongOrNull() ?: 0L
                    if (v > maxKhz) maxKhz = v
                }
            } catch (e: Exception) {
                // some kernels hide cpufreq from apps
            }
        }
        return (maxKhz / 1000L).toInt()
    }
}
