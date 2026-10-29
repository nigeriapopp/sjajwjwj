package app.gamenative.vapor

/**
 * Decides when to turn on "tap to click" (touchscreen mode: a tap clicks exactly where the finger is,
 * drag = left-drag, hold or two-finger tap = right click) for a game automatically.
 *
 * Steam only lists controller_support for games that support a controller ("partial" / "full"), so a
 * Steam game without it is a keyboard-and-mouse game.
 */
object MouseGameDetector {
    const val NONE = 0

    fun shouldAutoEnable(
        enabled: Boolean,
        isSteamGame: Boolean,
        controllerSupportCode: Int?,
        hasPhysicalController: Boolean,
        userChoseMode: Boolean,
        alreadyTouchscreen: Boolean,
    ): Boolean {
        if (!enabled || alreadyTouchscreen || userChoseMode || hasPhysicalController) return false
        if (!isSteamGame || controllerSupportCode == null) return false
        return controllerSupportCode == NONE
    }

    const val USER_CHOSE_EXTRA = "vapor_input_user"
    const val AUTO_EXTRA = "vapor_input_auto"
}
