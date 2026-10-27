package app.gamenative.vapor

/** FPS helpers shared by the Quick Menu and the heat/charging logic. */
object VaporFps {
    private val QUICK = intArrayOf(30, 40, 45, 60, 90, 120)

    /** One-tap caps that the display can actually show, at most 5 so they fit one row. */
    fun quickCaps(maxHz: Int): List<Int> {
        val max = if (maxHz > 0) maxHz else 60
        return QUICK.filter { it <= max }.let { if (it.size > 5) it.take(5) else it }
    }
}
