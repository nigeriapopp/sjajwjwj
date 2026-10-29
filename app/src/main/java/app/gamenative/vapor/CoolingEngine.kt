package app.gamenative.vapor

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import app.gamenative.ui.util.SnackbarManager
import timber.log.Timber

/**
 * Smart cooling: every 10 s reads Android's 10-second thermal forecast and status, and lowers the FPS cap in
 * small steps just before the phone would throttle (see [CoolingPolicy]); restores it once cool.
 */
class CoolingEngine private constructor(
    private val pm: PowerManager,
    mode: CoolingPolicy.Mode,
    private val userCap: () -> Int,
    private val apply: (Int?) -> Unit,
) {
    private val policy = CoolingPolicy(mode)
    private val handler = Handler(Looper.getMainLooper())
    private var levelSinceMs = 0L
    private var cooledMs = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun step() {
        val now = System.currentTimeMillis()
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        } else {
            Float.NaN
        }
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
        val before = policy.level
        if (!policy.update(headroom, status, now)) return
        if (before > 0) cooledMs += now - levelSinceMs
        levelSinceMs = now
        val cap = CoolingPolicy.capFor(policy.level, userCap())
        Timber.i("[Vapor] smart cooling level ${policy.level} (headroom=$headroom status=$status) cap=$cap")
        apply(cap)
        if (cap != null && policy.level > before) {
            SnackbarManager.show("Smart cooling: $cap FPS for now so the phone doesn't throttle")
        } else if (policy.level == 0) {
            SnackbarManager.show("Phone cooled down: full FPS again")
        }
    }

    private fun start() {
        running = true
        handler.postDelayed(tick, TICK_MS)
    }

    /** Stops and returns how long the cap was lowered, in ms. */
    fun stop(): Long {
        running = false
        handler.removeCallbacks(tick)
        if (policy.level > 0) cooledMs += System.currentTimeMillis() - levelSinceMs
        return cooledMs
    }

    companion object {
        // Android refreshes the thermal headroom forecast at most every ~10 s (faster calls return NaN).
        private const val TICK_MS = 10_000L

        fun attachIfEnabled(context: Context, userCap: () -> Int, apply: (Int?) -> Unit): CoolingEngine? {
            VaporSettings.init(context)
            val mode = CoolingPolicy.Mode.fromOrdinal(VaporSettings.coolingMode)
            if (mode == CoolingPolicy.Mode.OFF || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            return CoolingEngine(pm, mode, userCap, apply).also { it.start() }
        }
    }
}
