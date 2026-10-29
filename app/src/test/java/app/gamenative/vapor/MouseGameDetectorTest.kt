package app.gamenative.vapor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseGameDetectorTest {
    private fun check(code: Int?, controller: Boolean = false, userChose: Boolean = false, steam: Boolean = true) =
        MouseGameDetector.shouldAutoEnable(true, steam, code, controller, userChose, alreadyTouchscreen = false)

    @Test
    fun keyboardMouseSteamGameGetsTapToClick() = assertTrue(check(0))

    @Test
    fun controllerGamesKeepTheirControls() {
        assertFalse(check(1))
        assertFalse(check(2))
    }

    @Test
    fun respectsControllerUserChoiceAndUnknownStores() {
        assertFalse(check(0, controller = true))
        assertFalse(check(0, userChose = true))
        assertFalse(check(0, steam = false))
        assertFalse(check(null))
        assertFalse(MouseGameDetector.shouldAutoEnable(false, true, 0, false, false, false))
    }
}
