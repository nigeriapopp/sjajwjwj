package app.gamenative.vapor

/** Short tips shown once each during the first few game sessions. */
object VaporTips {
    const val SHADER_NOTICE =
        "First launch: short stutters are normal while graphics shaders build. It gets smoother the more you play."

    private val TIPS = listOf(
        "Tip: swipe back (or press the back button) to open the game menu.",
        "Tip: pick up a controller and the touch controls hide. Touch the screen to bring them back.",
        "Tip: running hot? Use the 30/40/45 FPS buttons in the game menu for steadier play.",
    )

    /** Tip for the given session number (0-based), or null once all tips have been shown. */
    fun next(sessionIndex: Int): String? = TIPS.getOrNull(sessionIndex)
}
