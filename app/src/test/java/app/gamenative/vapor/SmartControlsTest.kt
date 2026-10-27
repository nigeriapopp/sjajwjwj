package app.gamenative.vapor

import app.gamenative.vapor.SmartControls.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartControlsTest {
    private fun sc(enabled: Boolean = true) = SmartControls { enabled }

    @Test
    fun controllerInputHidesVisibleControls() {
        val s = sc()
        assertEquals(Action.HIDE, s.onGamepadInput(controlsVisible = true, blocked = false))
        assertTrue(s.hiddenByController)
    }

    @Test
    fun touchBringsThemBackAndSwallowsGesture() {
        val s = sc()
        s.onGamepadInput(controlsVisible = true, blocked = false)
        assertEquals(Action.SHOW, s.onTouchDown(controlsVisible = false, hasController = true, blocked = false))
        assertTrue(s.swallowingGesture)
        assertFalse(s.hiddenByController)
    }

    @Test
    fun startedWithControllerTouchStillReveals() {
        val s = sc()
        assertEquals(Action.SHOW, s.onTouchDown(controlsVisible = false, hasController = true, blocked = false))
    }

    @Test
    fun noControllerAndNotHiddenByUsDoesNothing() {
        val s = sc()
        assertEquals(Action.NONE, s.onTouchDown(controlsVisible = false, hasController = false, blocked = false))
    }

    @Test
    fun manualToggleWinsForTheSession() {
        val s = sc()
        s.onUserToggle()
        assertEquals(Action.NONE, s.onGamepadInput(controlsVisible = true, blocked = false))
        assertEquals(Action.NONE, s.onTouchDown(controlsVisible = false, hasController = true, blocked = false))
    }

    @Test
    fun menusAndDisabledSettingBlockEverything() {
        assertEquals(Action.NONE, sc().onGamepadInput(controlsVisible = true, blocked = true))
        assertEquals(Action.NONE, sc().onTouchDown(controlsVisible = false, hasController = true, blocked = true))
        assertEquals(Action.NONE, sc(enabled = false).onGamepadInput(controlsVisible = true, blocked = false))
        assertEquals(Action.NONE, sc(enabled = false).onTouchDown(controlsVisible = false, hasController = true, blocked = false))
    }

    @Test
    fun alreadyHiddenControlsDontHideAgain() {
        assertEquals(Action.NONE, sc().onGamepadInput(controlsVisible = false, blocked = false))
    }
}
