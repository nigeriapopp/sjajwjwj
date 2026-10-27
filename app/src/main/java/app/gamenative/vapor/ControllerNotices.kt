package app.gamenative.vapor

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import app.gamenative.ui.util.SnackbarManager
import com.winlator.inputcontrols.ControllerManager
import kotlin.math.roundToInt

/** A connected controller as Vapor reports it. */
data class PadInfo(val key: String, val name: String, val slot: Int, val batteryPct: Int?)

/** Pure diffing so the wording and rules can be unit tested. */
object PadNoticeLogic {
    const val LOW_BATTERY_PCT = 15

    fun connectedText(p: PadInfo): String = buildString {
        append(cleanName(p.name)).append(" connected")
        if (p.slot >= 0) append(" · Player ").append(p.slot + 1)
        if (p.batteryPct != null) append(" · ").append(p.batteryPct).append("% battery")
    }

    fun disconnectedText(name: String): String = "${cleanName(name)} disconnected"

    fun lowBatteryText(p: PadInfo): String = "${cleanName(p.name)} battery low (${p.batteryPct}%)"

    /** Messages for controllers that appeared or disappeared since [previous]. */
    fun diff(previous: Map<String, String>, current: List<PadInfo>): List<String> {
        val out = mutableListOf<String>()
        val currentKeys = current.map { it.key }.toSet()
        current.filter { it.key !in previous }.forEach { out += connectedText(it) }
        previous.filterKeys { it !in currentKeys }.values.forEach { out += disconnectedText(it) }
        return out
    }

    fun cleanName(raw: String): String {
        val n = raw.trim()
        return when {
            n.isEmpty() -> "Controller"
            n.contains("xbox", true) -> "Xbox controller"
            n.contains("dualsense", true) -> "DualSense"
            n.contains("dualshock", true) || n.contains("wireless controller", true) -> "PlayStation controller"
            n.contains("pro controller", true) -> "Switch Pro controller"
            n.contains("joy-con", true) -> "Joy-Con"
            else -> n
        }
    }
}

/**
 * In-game notices: "Xbox controller connected · Player 1 · 80% battery", disconnect notices,
 * and a one-time warning when a controller's battery runs low.
 */
class ControllerNotices {
    private val handler = Handler(Looper.getMainLooper())
    private var known: Map<String, String> = emptyMap()
    private val warnedLow = mutableSetOf<String>()
    private var started = false

    private val poll = object : Runnable {
        override fun run() {
            checkLowBattery()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        handler.postDelayed(poll, POLL_MS)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(poll)
    }

    /** Call after every device scan. The first call only records what is already connected. */
    fun onDevicesScanned(firstScan: Boolean) {
        val pads = currentPads()
        if (!firstScan) PadNoticeLogic.diff(known, pads).forEach { SnackbarManager.show(it) }
        else pads.forEach { SnackbarManager.show(PadNoticeLogic.connectedText(it)) }
        known = pads.associate { it.key to it.name }
    }

    private fun checkLowBattery() {
        for (p in currentPads()) {
            val pct = p.batteryPct ?: continue
            if (pct <= PadNoticeLogic.LOW_BATTERY_PCT && warnedLow.add(p.key)) {
                SnackbarManager.show(PadNoticeLogic.lowBatteryText(p))
            }
        }
    }

    private fun currentPads(): List<PadInfo> = try {
        val cm = ControllerManager.getInstance()
        cm.detectedDevices.filterNotNull().map { d ->
            PadInfo(
                key = d.descriptor ?: d.id.toString(),
                name = d.name ?: "",
                slot = cm.getSlotForDevice(d.id),
                batteryPct = batteryPercent(d),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun batteryPercent(d: InputDevice): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val state = d.batteryState
            if (!state.isPresent) return null
            val cap = state.capacity
            if (cap.isNaN() || cap < 0f) null else (cap * 100f).roundToInt()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val POLL_MS = 120_000L
    }
}
