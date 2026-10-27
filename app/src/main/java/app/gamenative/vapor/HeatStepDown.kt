package app.gamenative.vapor

import android.content.Context
import android.os.Build
import android.os.PowerManager
import app.gamenative.ui.util.SnackbarManager
import timber.log.Timber

/**
 * Pure rules for Heat Step-Down. Android thermal status: 2 = MODERATE, 3 = SEVERE, 4 = CRITICAL.
 * Hot → lower the cap a step so frame times stay even instead of the phone throttling hard.
 * Cooled back to MODERATE or below → restore the player's own cap.
 */
class HeatStepDownLogic {
    sealed class Result {
        data class Cap(val fps: Int) : Result()
        object Restore : Result()
        object None : Result()
    }

    var level = 0
        private set

    fun onStatus(status: Int, userCap: Int): Result {
        val target = when {
            status >= 4 -> 2
            status >= 3 -> maxOf(level, 1)
            status <= 2 -> 0
            else -> level
        }
        if (target == level) return Result.None
        level = target
        if (level == 0) return Result.Restore
        val base = if (userCap > 0) userCap else 60
        val fps = if (level >= 2) minOf(base, 30) else minOf(base, 45)
        return if (fps >= base) Result.None else Result.Cap(fps)
    }
}

/** Listens to Android's thermal status during a game and applies [HeatStepDownLogic]. */
class HeatStepDown private constructor(
    private val pm: PowerManager,
    private val userCap: () -> Int,
    private val apply: (Int?) -> Unit,
) {
    private val logic = HeatStepDownLogic()
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    private fun attach(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val l = PowerManager.OnThermalStatusChangedListener { status ->
            when (val r = logic.onStatus(status, userCap())) {
                is HeatStepDownLogic.Result.Cap -> {
                    apply(r.fps)
                    SnackbarManager.show("Phone is hot — capped at ${r.fps} FPS until it cools down")
                }
                HeatStepDownLogic.Result.Restore -> {
                    apply(null)
                    SnackbarManager.show("Phone cooled down — FPS cap restored")
                }
                HeatStepDownLogic.Result.None -> {}
            }
        }
        try {
            pm.addThermalStatusListener(context.mainExecutor, l)
            listener = l
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] heat step-down unavailable")
        }
    }

    fun detach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        listener?.let { runCatching { pm.removeThermalStatusListener(it) } }
        listener = null
    }

    companion object {
        /**
         * @param apply called with a temporary cap, or null to go back to the player's cap
         * @return null when the setting is off or the phone has no thermal API
         */
        fun attachIfEnabled(context: Context, userCap: () -> Int, apply: (Int?) -> Unit): HeatStepDown? {
            VaporSettings.init(context)
            if (!VaporSettings.heatStepDown || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            return HeatStepDown(pm, userCap, apply).also { it.attach(context) }
        }
    }
}
