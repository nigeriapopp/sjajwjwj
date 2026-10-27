package app.gamenative.vapor

import android.content.Context
import android.content.SharedPreferences

/** Vapor's own settings, kept apart from upstream preferences so updates never collide. */
object VaporSettings {
    private const val FILE = "vapor_settings"

    private const val KEY_AUTOTUNE_VERSION = "autotune_version"
    private const val KEY_FPS_TARGET = "fps_target"
    private const val KEY_SHOW_FPS = "show_fps_default"
    private const val KEY_SUSTAINED = "sustained_performance"
    private const val KEY_LOCK_REFRESH = "lock_max_refresh"
    private const val KEY_THERMAL_WARNINGS = "thermal_warnings"
    private const val KEY_BATTERY_SAVER = "respect_battery_saver"
    private const val KEY_TIER = "tier"

    const val DEFAULT_FPS_TARGET = 60

    /** Vapor ships from its own GitHub releases, so the upstream update prompt stays off. */
    val upstreamUpdatesEnabled: Boolean = false

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Application context, available once [init] has run. */
    @Volatile
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        if (prefs == null) {
            val app = context.applicationContext
            appContext = app
            prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences? = prefs

    var autoTuneVersion: Int
        get() = p()?.getInt(KEY_AUTOTUNE_VERSION, 0) ?: 0
        set(v) { p()?.edit()?.putInt(KEY_AUTOTUNE_VERSION, v)?.apply() }

    /** FPS cap for games that have no cap saved yet. */
    var fpsTarget: Int
        get() = p()?.getInt(KEY_FPS_TARGET, DEFAULT_FPS_TARGET) ?: DEFAULT_FPS_TARGET
        set(v) { p()?.edit()?.putInt(KEY_FPS_TARGET, v.coerceIn(15, 240))?.apply() }

    var showFpsByDefault: Boolean
        get() = p()?.getBoolean(KEY_SHOW_FPS, true) ?: false
        set(v) { p()?.edit()?.putBoolean(KEY_SHOW_FPS, v)?.apply() }

    /** Android sustained-performance mode: lower peak clocks, far less throttling. */
    var sustainedPerformance: Boolean
        get() = p()?.getBoolean(KEY_SUSTAINED, false) ?: false
        set(v) { p()?.edit()?.putBoolean(KEY_SUSTAINED, v)?.apply() }

    var lockMaxRefreshRate: Boolean
        get() = p()?.getBoolean(KEY_LOCK_REFRESH, false) ?: false
        set(v) { p()?.edit()?.putBoolean(KEY_LOCK_REFRESH, v)?.apply() }

    var thermalWarnings: Boolean
        get() = p()?.getBoolean(KEY_THERMAL_WARNINGS, true) ?: true
        set(v) { p()?.edit()?.putBoolean(KEY_THERMAL_WARNINGS, v)?.apply() }

    /** Cap new games at 30 FPS while Battery Saver or the Game Dashboard battery mode is on. */
    var respectBatterySaver: Boolean
        get() = p()?.getBoolean(KEY_BATTERY_SAVER, true) ?: true
        set(v) { p()?.edit()?.putBoolean(KEY_BATTERY_SAVER, v)?.apply() }

    /** Hide on-screen controls while a controller is used; bring them back on touch. */
    var smartControls: Boolean
        get() = p()?.getBoolean("smart_controls", true) ?: true
        set(v) { p()?.edit()?.putBoolean("smart_controls", v)?.apply() }

    /** Fade on-screen controls after a few seconds without touching them. */
    var ghostControls: Boolean
        get() = p()?.getBoolean("ghost_controls", true) ?: true
        set(v) { p()?.edit()?.putBoolean("ghost_controls", v)?.apply() }

    /** Lower the FPS cap a step when the phone overheats, restore it when cool. */
    var heatStepDown: Boolean
        get() = p()?.getBoolean("heat_step_down", false) ?: false
        set(v) { p()?.edit()?.putBoolean("heat_step_down", v)?.apply() }

    /** Silence notifications while playing (needs Do Not Disturb access). */
    var doNotDisturb: Boolean
        get() = p()?.getBoolean("do_not_disturb", false) ?: false
        set(v) { p()?.edit()?.putBoolean("do_not_disturb", v)?.apply() }

    /** A small notice every hour of play. */
    var playtimeReminders: Boolean
        get() = p()?.getBoolean("playtime_reminders", false) ?: false
        set(v) { p()?.edit()?.putBoolean("playtime_reminders", v)?.apply() }

    var sessionsPlayed: Int
        get() = p()?.getInt("sessions_played", 0) ?: 0
        set(v) { p()?.edit()?.putInt("sessions_played", v)?.apply() }

    /** Records that [gameKey] has been launched. Returns true the first time. */
    fun markGamePlayed(gameKey: String): Boolean {
        val prefs = p() ?: return false
        val key = "played_$gameKey"
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    var tierName: String
        get() = p()?.getString(KEY_TIER, "") ?: ""
        set(v) { p()?.edit()?.putString(KEY_TIER, v)?.apply() }
}
