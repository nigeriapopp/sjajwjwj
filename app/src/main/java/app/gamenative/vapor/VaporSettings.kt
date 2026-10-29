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

    /** Reuse unpacked graphics files between launches (see VaporFastLaunch). */
    var fastLaunch: Boolean
        get() = p()?.getBoolean("fast_launch", true) ?: true
        set(v) { p()?.edit()?.putBoolean("fast_launch", v)?.apply() }

    /** Set by "Repair game files": the next launch does a full unpack. */
    var repairPending: Boolean
        get() = p()?.getBoolean("repair_pending", false) ?: false
        set(v) { p()?.edit()?.putBoolean("repair_pending", v)?.apply() }

    var tierName: String
        get() = p()?.getString(KEY_TIER, "") ?: ""
        set(v) { p()?.edit()?.putString(KEY_TIER, v)?.apply() }

    /** Look for a newer Vapor build on GitHub when the app opens. */
    var autoUpdateCheck: Boolean
        get() = p()?.getBoolean("auto_update_check", true) ?: true
        set(v) { p()?.edit()?.putBoolean("auto_update_check", v)?.apply() }

    /** Version code the player chose to skip. */
    var skippedUpdate: Int
        get() = p()?.getInt("skipped_update", 0) ?: 0
        set(v) { p()?.edit()?.putInt("skipped_update", v)?.apply() }

    /** Convert each game's graphics settings to phone-friendly values on its first launch. */
    var phonePresets: Boolean
        get() = p()?.getBoolean("phone_presets", true) ?: true
        set(v) { p()?.edit()?.putBoolean("phone_presets", v)?.apply() }

    fun presetsApplied(gameKey: String): Boolean = p()?.getBoolean("presets_$gameKey", false) ?: true

    fun markPresetsApplied(gameKey: String) {
        p()?.edit()?.putBoolean("presets_$gameKey", true)?.apply()
    }

    /** Clears every per-game preset marker so presets apply again on each game's next launch. */
    fun resetPresets() {
        val prefs = p() ?: return
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("presets_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** How many times a game (by container id) crashed; drives Crash Guard's safe-mode steps. */
    fun crashCount(key: String): Int = p()?.getInt("crashes_$key", 0) ?: 0

    fun recordCrash(key: String) {
        val prefs = p() ?: return
        prefs.edit().putInt("crashes_$key", prefs.getInt("crashes_$key", 0) + 1).apply()
    }

    fun isSafeModeOn(key: String): Boolean = p()?.getBoolean("safemode_$key", false) ?: false

    fun setSafeModeOn(key: String, on: Boolean) {
        p()?.edit()?.putBoolean("safemode_$key", on)?.apply()
    }

    /** Turn on tap-to-click automatically in keyboard-and-mouse games. */
    var tapToClick: Boolean
        get() = p()?.getBoolean("tap_to_click", true) ?: true
        set(v) { p()?.edit()?.putBoolean("tap_to_click", v)?.apply() }

    /** Smart cooling mode (CoolingPolicy.Mode ordinal): 0 off, 1 Max FPS (default), 2 Balanced, 3 Coolest. */
    var coolingMode: Int
        get() = p()?.getInt("cooling_mode", if (p()?.getBoolean("heat_step_down", false) == true) 2 else 1) ?: 1
        set(v) { p()?.edit()?.putInt("cooling_mode", v)?.apply() }

    var secondaryUserNoticeShown: Boolean
        get() = p()?.getBoolean("secondary_user_notice", false) ?: false
        set(v) { p()?.edit()?.putBoolean("secondary_user_notice", v)?.apply() }

    /** Gamescope couldn't start for this game (container id): use the classic X11 display. */
    fun gamescopeFailed(key: String): Boolean = p()?.getBoolean("no_gamescope_$key", false) ?: false

    fun setGamescopeFailed(key: String, failed: Boolean) {
        p()?.edit()?.putBoolean("no_gamescope_$key", failed)?.apply()
    }

    /** Forget all recorded crashes: games leave Crash Guard safe mode on their next launch. */
    fun resetCrashes() {
        val prefs = p() ?: return
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("crashes_") || it.startsWith("no_gamescope_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** Keep a rolling automatic save backup after each session. */
    var autoSaveBackup: Boolean
        get() = p()?.getBoolean("auto_save_backup", true) ?: true
        set(v) { p()?.edit()?.putBoolean("auto_save_backup", v)?.apply() }
}
