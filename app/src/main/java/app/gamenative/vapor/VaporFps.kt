package app.gamenative.vapor

/** FPS helpers shared by the Quick Menu and the heat/charging logic. */
object VaporFps {
    private val QUICK = intArrayOf(30, 40, 45, 60, 90, 120)

    /**
     * One-tap caps the display can show, at most 4 so they fit one row on a phone. The screen's own top
     * rate (90/120) is always offered.
     */
    fun quickCaps(maxHz: Int): List<Int> {
        val max = if (maxHz > 0) maxHz else 60
        val fit = QUICK.filter { it <= max }
        if (fit.size <= 4) return fit
        return listOf(30, 45, 60, fit.last()).distinct()
    }
}
