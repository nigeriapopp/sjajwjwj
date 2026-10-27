package app.gamenative.vapor

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import app.gamenative.ui.util.SnackbarManager
import java.util.concurrent.Executor
import timber.log.Timber

/**
 * Per-game helpers that run while a game is on screen: sustained performance mode, an optional
 * max-refresh-rate lock, and warnings when the phone overheats or the battery runs low.
 * Everything is undone in [stop].
 */
class VaporGameSession private constructor(
    private val activity: Activity,
    private val gameKey: String,
) {
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var sustainedEnabled = false
    private var previousModeId: Int? = null
    private var lastThermalWarned = 0
    private var batteryWarned = false
    private var previousInterruptionFilter: Int? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun begin() {
        VaporSettings.init(activity)
        if (VaporSettings.sustainedPerformance) enableSustainedPerformance()
        if (VaporSettings.lockMaxRefreshRate) lockMaxRefreshRate()
        if (VaporSettings.thermalWarnings) {
            watchThermals()
            watchBattery()
        }
        if (VaporSettings.doNotDisturb) enableDoNotDisturb()
        scheduleFirstLaunchNotices()
        warnIfLowMemory()
        if (VaporSettings.playtimeReminders) schedulePlaytimeReminder(1)
    }

    /** Heavy PC games need room: warn when Android reports little free memory at launch. */
    private fun warnIfLowMemory() {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val freeGb = info.availMem / (1024.0 * 1024.0 * 1024.0)
            if (freeGb < LOW_MEMORY_GB) {
                handler.postDelayed({
                    SnackbarManager.show(
                        "Only %.1f GB of memory free — close other apps if the game stutters or crashes.".format(freeGb),
                    )
                }, 3_000L)
            }
        } catch (e: Exception) { }
    }

    private fun schedulePlaytimeReminder(hours: Int) {
        handler.postDelayed({
            SnackbarManager.show(if (hours == 1) "You've been playing for 1 hour." else "You've been playing for $hours hours.")
            schedulePlaytimeReminder(hours + 1)
        }, HOUR_MS)
    }

    /** First time a game runs: explain shader stutter. First few sessions overall: one tip each. */
    private fun scheduleFirstLaunchNotices() {
        val firstRunOfGame = VaporSettings.markGamePlayed(gameKey)
        val tip = VaporTips.next(VaporSettings.sessionsPlayed)
        VaporSettings.sessionsPlayed = VaporSettings.sessionsPlayed + 1
        if (firstRunOfGame) {
            handler.postDelayed({ SnackbarManager.show(VaporTips.SHADER_NOTICE) }, 6_000L)
        }
        if (tip != null) {
            handler.postDelayed({ SnackbarManager.show(tip) }, if (firstRunOfGame) 16_000L else 6_000L)
        }
    }

    private fun enableDoNotDisturb() {
        try {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (!nm.isNotificationPolicyAccessGranted) return
            previousInterruptionFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] do not disturb unavailable")
        }
    }

    private fun enableSustainedPerformance() {
        try {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (pm.isSustainedPerformanceModeSupported) {
                activity.window.setSustainedPerformanceMode(true)
                sustainedEnabled = true
                Timber.i("[Vapor] sustained performance mode on")
            }
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] sustained performance mode unavailable")
        }
    }

    private fun lockMaxRefreshRate() {
        try {
            val window = activity.window
            val display = activity.windowManager.defaultDisplay ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val attrs = window.attributes
            previousModeId = attrs.preferredDisplayModeId
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
            Timber.i("[Vapor] display locked to ${best.refreshRate} Hz")
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] could not lock refresh rate")
        }
    }

    private fun watchThermals() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val listener = PowerManager.OnThermalStatusChangedListener { status -> onThermalStatus(status) }
            val mainExecutor: Executor = activity.mainExecutor
            pm.addThermalStatusListener(mainExecutor, listener)
            thermalListener = listener
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] thermal listener unavailable")
        }
    }

    private fun onThermalStatus(status: Int) {
        // Warn once per level, only for SEVERE (3) and up.
        if (status < PowerManager.THERMAL_STATUS_SEVERE || status <= lastThermalWarned) return
        // Heat Step-Down posts its own notice when it lowers the cap.
        if (VaporSettings.heatStepDown) return
        lastThermalWarned = status
        val msg = if (status >= PowerManager.THERMAL_STATUS_CRITICAL) {
            "Phone is very hot — Android is slowing it down. Lower the FPS cap or take a break."
        } else {
            "Phone is getting hot. A 30-45 FPS cap will keep frame rates steadier."
        }
        SnackbarManager.show(msg)
    }

    private fun watchBattery() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (batteryWarned) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (level < 0 || scale <= 0 || plugged) return
                val pct = level * 100 / scale
                if (pct <= 15) {
                    batteryWarned = true
                    SnackbarManager.show("Battery at $pct% — plug in or lower the FPS cap to keep playing.")
                }
            }
        }
        try {
            activity.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiver = receiver
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] battery receiver unavailable")
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        previousInterruptionFilter?.let { filter ->
            try {
                (activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.setInterruptionFilter(filter)
            } catch (e: Exception) { }
        }
        previousInterruptionFilter = null
        try {
            if (sustainedEnabled) activity.window.setSustainedPerformanceMode(false)
        } catch (e: Exception) { }
        previousModeId?.let { id ->
            try {
                val attrs = activity.window.attributes
                attrs.preferredDisplayModeId = id
                activity.window.attributes = attrs
            } catch (e: Exception) { }
        }
        thermalListener?.let { l ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.removeThermalStatusListener(l)
                }
            } catch (e: Exception) { }
        }
        batteryReceiver?.let { r ->
            try {
                activity.unregisterReceiver(r)
            } catch (e: Exception) { }
        }
        thermalListener = null
        batteryReceiver = null
        previousModeId = null
        sustainedEnabled = false
    }

    companion object {
        private const val LOW_MEMORY_GB = 2.0
        private const val HOUR_MS = 60L * 60L * 1000L

        fun start(activity: Activity, gameKey: String): VaporGameSession =
            VaporGameSession(activity, gameKey).also { it.begin() }
    }
}
