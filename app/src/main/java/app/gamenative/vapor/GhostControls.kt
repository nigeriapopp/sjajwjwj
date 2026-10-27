package app.gamenative.vapor

import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Ghost mode: on-screen controls fade to a faint outline after a few seconds without touches,
 * so they stop covering the game. Any touch brings them back to full strength instantly.
 */
class GhostControls(private val target: () -> View?) {
    private val handler = Handler(Looper.getMainLooper())

    private val fade = Runnable {
        if (!VaporSettings.ghostControls) return@Runnable
        val view = target() ?: return@Runnable
        if (view.visibility == View.VISIBLE) {
            view.animate().alpha(GHOST_ALPHA).setDuration(FADE_OUT_MS).start()
        }
    }

    /** Call on every touch while the controls are showing. */
    fun onTouch() {
        handler.removeCallbacks(fade)
        val view = target() ?: return
        if (view.alpha < 1f) {
            view.animate().cancel()
            view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
        }
        if (VaporSettings.ghostControls) handler.postDelayed(fade, IDLE_MS)
    }

    /** Stop fading and restore full opacity (controls hidden, game closing, or setting turned off). */
    fun reset() {
        handler.removeCallbacks(fade)
        target()?.let {
            it.animate().cancel()
            it.alpha = 1f
        }
    }

    companion object {
        const val GHOST_ALPHA = 0.3f
        const val IDLE_MS = 4000L
        const val FADE_OUT_MS = 450L
        const val FADE_IN_MS = 120L
    }
}
