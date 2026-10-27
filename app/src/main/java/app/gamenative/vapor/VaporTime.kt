package app.gamenative.vapor

/** Short "how long ago" text for save slots. */
object VaporTime {
    fun ago(now: Long, then: Long): String {
        val s = ((now - then) / 1000L).coerceAtLeast(0L)
        return when {
            s < 45 -> "just now"
            s < 90 -> "1 min ago"
            s < 3600 -> "${s / 60} min ago"
            s < 7200 -> "1 hour ago"
            s < 86_400 -> "${s / 3600} hours ago"
            s < 172_800 -> "yesterday"
            else -> "${s / 86_400} days ago"
        }
    }
}
