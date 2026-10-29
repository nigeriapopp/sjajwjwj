package app.gamenative.vapor

import android.content.Context

/**
 * Makes Vapor work in any Android user, e.g. Samsung Secure Folder (user 150) or a work profile.
 *
 * A lot of the Windows stack spells its paths as `/data/data/app.gamenative/...`. On Android that always
 * points at user 0's storage, so in Secure Folder those paths fail with "Permission denied" (controller
 * shared memory, DXVK cache, media files, the E: drive). Every Wine process gets its environment through
 * ProcessHelper, which passes it through [fixEnv] so those paths point at the current user's real folder.
 */
object VaporPaths {
    @Volatile
    private var legacyPrefix: String? = null

    @Volatile
    private var realPrefix: String? = null

    /** Call once at app start. */
    @JvmStatic
    fun init(context: Context) {
        val pkg = context.packageName
        val dataDir = runCatching { context.applicationInfo.dataDir }.getOrNull() ?: return
        configure(pkg, dataDir)
    }

    /** Testable core of [init]. */
    @JvmStatic
    fun configure(packageName: String, dataDir: String) {
        legacyPrefix = "/data/data/$packageName"
        realPrefix = dataDir.trimEnd('/')
    }

    /** True when Vapor runs outside the main user (Secure Folder, work profile, second user). */
    @JvmStatic
    fun isSecondaryUser(): Boolean = isSecondaryUserDir(realPrefix)

    @JvmStatic
    fun isSecondaryUserDir(dataDir: String?): Boolean {
        if (dataDir.isNullOrEmpty()) return false
        return !(dataDir.startsWith("/data/data/") || dataDir.startsWith("/data/user/0/"))
    }

    /** Rewrites user-0 spellings of the app folder to the current user's folder. No-op in the main user. */
    @JvmStatic
    fun fix(value: String?): String? {
        if (value == null) return null
        val legacy = legacyPrefix ?: return value
        val real = realPrefix ?: return value
        if (!isSecondaryUserDir(real) || !value.contains(legacy)) return value
        return value.replace(legacy, real)
    }

    @JvmStatic
    fun fixEnv(env: Array<String>?): Array<String>? {
        if (env == null || !isSecondaryUser()) return env
        return Array(env.size) { i -> fix(env[i]) ?: env[i] }
    }
}
