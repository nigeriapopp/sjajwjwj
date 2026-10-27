package app.gamenative.vapor

import app.gamenative.ui.util.SnackbarManager
import kotlin.math.roundToInt

/** One-line recap shown after leaving a game, e.g. "Fallout 3 · 42 min · 52 FPS avg · 38 FPS 1% low". */
object SessionSummary {
    private const val MIN_SECONDS = 60L

    fun format(props: Map<String, Any?>): String? {
        val seconds = (props["session_length"] as? Number)?.toLong() ?: return null
        if (seconds < MIN_SECONDS) return null
        val parts = mutableListOf<String>()
        (props["game_name"] as? String)?.takeIf { it.isNotBlank() }?.let { parts += it }
        parts += duration(seconds)
        (props["avg_fps"] as? Number)?.toDouble()?.takeIf { it > 0.5 }?.let { parts += "${it.roundToInt()} FPS avg" }
        (props["frame_p99_ms"] as? Number)?.toDouble()?.takeIf { it > 0.0 }?.let {
            parts += "${(1000.0 / it).roundToInt()} FPS 1% low"
        }
        (props["battery_temp_c"] as? Number)?.toInt()?.let { parts += "battery $it°C" }
        return parts.joinToString(" · ")
    }

    fun duration(seconds: Long): String {
        val minutes = seconds / 60
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    fun show(props: Map<String, Any?>) {
        format(props)?.let { SnackbarManager.show(it) }
    }
}
