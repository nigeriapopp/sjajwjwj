package app.gamenative.vapor

import java.io.File
import timber.log.Timber

/**
 * Fast Launch: upstream re-unpacks the GPU wrapper, the 21 MB extra_libs bundle, zink DLLs and
 * DXVK/D8VK on every single launch. Vapor writes a small marker after each unpack and skips the
 * work next time when the marker matches and the key files are still there.
 *
 * Any mismatch, a missing file, the setting turned off, or "Repair game files" falls back to the
 * full unpack, so the worst case is exactly the old behaviour.
 */
object VaporFastLaunch {
    const val DX_MARKER = ".vapor_dx"
    const val WRAPPER_MARKER = ".vapor_wrapper"
    const val PREFIX_WRAPPER_MARKER = ".vapor_prefix_wrapper"

    /** True when the files described by [key] are already in place and can be reused. */
    fun isCurrent(marker: File, key: String, vararg required: File): Boolean {
        if (!VaporSettings.fastLaunch || VaporSettings.repairPending) return false
        val ok = try {
            marker.isFile && marker.readText() == key && required.all { it.exists() }
        } catch (e: Exception) {
            false
        }
        if (ok) Timber.i("[Vapor] Fast Launch: reusing ${marker.name} ($key)")
        return ok
    }

    fun mark(marker: File, key: String) {
        try {
            marker.parentFile?.mkdirs()
            marker.writeText(key)
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] Fast Launch: could not write ${marker.name}")
        }
    }

    fun clear(marker: File) {
        try {
            marker.delete()
        } catch (e: Exception) { }
    }

    private const val CRASH_WINDOW_MS = 40_000L

    @Volatile
    private var sessionStartedAt = 0L

    /** The game screen opened. */
    fun onSessionStarted() {
        sessionStartedAt = System.currentTimeMillis()
    }

    /** Graphics files are unpacked for this launch, so a requested repair is done. */
    fun onFilesPrepared() {
        if (VaporSettings.repairPending) VaporSettings.repairPending = false
    }

    /**
     * A game that closes by itself within 40 s of opening probably crashed: do a full unpack next time.
     * Quitting from the menu never counts. Returns true when it looked like a crash.
     */
    fun onSessionEnded(gameEndedOnItsOwn: Boolean = true): Boolean {
        val started = sessionStartedAt
        sessionStartedAt = 0L
        if (started == 0L || !gameEndedOnItsOwn) return false
        if (shouldRepairAfter(System.currentTimeMillis() - started)) {
            Timber.i("[Vapor] Fast Launch: short session, next launch does a full unpack")
            VaporSettings.repairPending = true
            return true
        }
        return false
    }

    fun shouldRepairAfter(sessionMs: Long): Boolean = sessionMs in 0 until CRASH_WINDOW_MS
}
