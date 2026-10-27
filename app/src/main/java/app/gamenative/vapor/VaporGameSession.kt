package app.gamenative.vapor

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import java.util.concurrent.Executor
import timber.log.Timber

/**
 * Per-game helpers that run while a game is on screen: sustained performance mode, an optional
 * max-refresh-rate lock, and warnings when the phone overheats or the battery runs low.
 * Everything is undone in [stop].
 */
class VaporGameSession private constructor(private val activity: Activity) {
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var sustainedEnabled = false
    private var previousModeId: Int? = null
    private var lastThermalWarned = 0
    private var batteryWarned = false

    private fun begin() {
        VaporSettings.init(activity)
        if (VaporSettings.sustainedPerformance) enableSustainedPerformance()
        if (VaporSettings.lockMaxRefreshRate) lockMaxRefreshRate()
        if (VaporSettings.thermalWarnings) {
            watchThermals()
            watchBattery()
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
        lastThermalWarned = status
        val msg = if (status >= PowerManager.THERMAL_STATUS_CRITICAL) {
            "Phone is very hot — Android is slowing it down. Lower the FPS cap or take a break."
        } else {
            "Phone is getting hot. A 30-45 FPS cap will keep frame rates steadier."
        }
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(activity, "Battery at $pct% — plug in or lower the FPS cap to keep playing.", Toast.LENGTH_LONG).show()
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
        fun start(activity: Activity): VaporGameSession = VaporGameSession(activity).also { it.begin() }
    }
}
