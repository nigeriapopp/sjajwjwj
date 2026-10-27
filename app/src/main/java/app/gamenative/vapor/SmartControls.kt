package app.gamenative.vapor

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Smart Controls: the on-screen controls get out of the way while a physical controller is in use,
 * and come back as soon as the screen is touched.
 *
 * Pure decision logic so it can be unit tested; XServerScreen performs the actual show/hide.
 */
class SmartControls(
    private val enabled: () -> Boolean = { VaporSettings.smartControls },
) {
    enum class Action { NONE, SHOW, HIDE }

    /** Set when the player toggles controls from the Quick Menu: their choice wins for the session. */
    var pinned = false
        private set

    /** True while controls are hidden because a controller was being used. */
    var hiddenByController = false
        private set

    /** True from the revealing touch-down until that finger lifts: the whole gesture is swallowed. */
    var swallowingGesture = false

    fun onUserToggle() {
        pinned = true
        hiddenByController = false
    }

    /** Startup / hotplug logic hid the controls because a controller is connected. */
    fun onHiddenForController() {
        if (!pinned) hiddenByController = true
    }

    /**
     * A gamepad button or stick was used.
     * @param blocked a menu, editor or touchscreen-only mode is active
     */
    fun onGamepadInput(controlsVisible: Boolean, blocked: Boolean): Action {
        if (!enabled() || pinned || blocked || !controlsVisible) return Action.NONE
        hiddenByController = true
        return Action.HIDE
    }

    /**
     * A finger touched the screen. SHOW means: bring the controls back and swallow this touch so
     * picking up the phone never presses anything in game.
     */
    fun onTouchDown(controlsVisible: Boolean, hasController: Boolean, blocked: Boolean): Action {
        if (!enabled() || pinned || blocked || controlsVisible) return Action.NONE
        if (!hiddenByController && !hasController) return Action.NONE
        hiddenByController = false
        swallowingGesture = true
        return Action.SHOW
    }

    companion object {
        const val STICK_THRESHOLD = 0.5f

        private val AXES = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
        )

        /** True when a stick, trigger or d-pad moved far enough to count as deliberate input. */
        fun isDeliberateMotion(event: MotionEvent): Boolean {
            if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK &&
                event.source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD
            ) {
                return false
            }
            for (axis in AXES) {
                if (abs(event.getAxisValue(axis)) > STICK_THRESHOLD) return true
            }
            return false
        }
    }
}
