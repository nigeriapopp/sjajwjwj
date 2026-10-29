package app.gamenative.vapor

/**
 * Smart cooling rules (pure, unit tested).
 *
 * Android's thermal headroom is a forecast: 1.0 means "about to be throttled hard" (SEVERE). Acting a little
 * before that with a small FPS step keeps frame times even; waiting means Android cuts clocks by itself and
 * FPS falls off a cliff. Levels: 0 = full cap, 1 = small step (~-15%), 2 = 45 FPS, 3 = 30 FPS.
 */
class CoolingPolicy(private val mode: Mode) {
    enum class Mode(val label: String, val step1: Float, val step2: Float, val restoreBelow: Float) {
        OFF("Off", Float.MAX_VALUE, Float.MAX_VALUE, 0f),
        MAX_FPS("Max FPS", 0.90f, 0.97f, 0.80f),
        BALANCED("Balanced", 0.80f, 0.90f, 0.70f),
        COOLEST("Coolest", 0.70f, 0.85f, 0.60f),
        ;

        companion object {
            fun fromOrdinal(i: Int): Mode = entries.getOrElse(i) { MAX_FPS }
        }
    }

    var level = 0
        private set

    private var coolSinceMs = -1L

    /**
     * @param headroom forecast from PowerManager.getThermalHeadroom, NaN when unknown
     * @param status PowerManager thermal status (0 none .. 3 severe, 4 critical, 5+ emergency)
     * @return true when the level changed
     */
    fun update(headroom: Float, status: Int, nowMs: Long): Boolean {
        if (mode == Mode.OFF) return false
        val wanted = when {
            status >= 4 -> 3
            status >= 3 || (!headroom.isNaN() && headroom >= mode.step2) -> 2
            !headroom.isNaN() && headroom >= mode.step1 -> 1
            else -> 0
        }
        if (wanted > level) {
            level = wanted
            coolSinceMs = -1L
            return true
        }
        // Cooler: step back up one level at a time, only after it stayed cool for a while.
        val cool = status <= 2 && (headroom.isNaN() || headroom < mode.restoreBelow) && wanted < level
        if (!cool) {
            coolSinceMs = -1L
            return false
        }
        if (coolSinceMs < 0) coolSinceMs = nowMs
        if (nowMs - coolSinceMs >= RESTORE_AFTER_MS) {
            level -= 1
            coolSinceMs = if (level > 0) nowMs else -1L
            return true
        }
        return false
    }

    companion object {
        const val RESTORE_AFTER_MS = 60_000L

        /** The FPS cap for a level, never above the player's own cap (0 = no cap means 60). */
        fun capFor(level: Int, userCap: Int): Int? {
            val base = if (userCap > 0) userCap else 60
            val cap = when (level) {
                0 -> return null
                1 -> nice((base * 0.85f).toInt())
                2 -> 45
                else -> 30
            }
            return minOf(cap, base)
        }

        /** Rounds to caps that pace evenly on 60/90/120 Hz screens. */
        private fun nice(fps: Int): Int = listOf(100, 90, 72, 60, 50, 45, 40, 36, 30).firstOrNull { it <= fps } ?: 30
    }
}
