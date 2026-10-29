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

    @Volatile
    private var packageName: String? = null

    /** libvaporpath.so (native path redirect for prebuilt binaries), when it shipped in the APK. */
    @Volatile
    private var preloadLib: String? = null

    /** Call once at app start. */
    @JvmStatic
    fun init(context: Context) {
        val pkg = context.packageName
        val dataDir = runCatching { context.applicationInfo.dataDir }.getOrNull() ?: return
        configure(pkg, dataDir)
        preloadLib = runCatching {
            java.io.File(context.applicationInfo.nativeLibraryDir, "libvaporpath.so").takeIf { it.isFile }?.path
        }.getOrNull()
    }

    /** Testable core of [init]. */
    @JvmStatic
    fun configure(packageName: String, dataDir: String, preload: String? = null) {
        this.packageName = packageName
        legacyPrefix = "/data/data/$packageName"
        legacyPrefix0 = "/data/user/0/$packageName"
        realPrefix = dataDir.trimEnd('/')
        if (preload != null) preloadLib = preload
    }

    @Volatile
    private var legacyPrefix0: String? = null

    /**
     * True when prebuilt binaries with user-0 paths compiled in (gamescope's Xwayland, the Vulkan wrapper, …)
     * can run here: either this is the main user, or the native redirect library is available.
     */
    @JvmStatic
    fun binariesRedirected(): Boolean = !isSecondaryUser() || preloadLib != null

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
        if (!isSecondaryUserDir(real)) return value
        var out = value
        if (out.contains(legacy)) out = out.replace(legacy, real)
        legacyPrefix0?.let { l0 -> if (out.contains(l0)) out = out.replace(l0, real) }
        return out
    }

    @JvmStatic
    fun fixEnv(env: Array<String>?): Array<String>? {
        if (env == null || !isSecondaryUser()) return env
        val fixed = env.map { fix(it) ?: it }.toMutableList()
        val lib = preloadLib ?: return fixed.toTypedArray()
        // Native redirect for paths compiled into binaries. The values are added after fix() so the
        // package name below is never rewritten.
        val i = fixed.indexOfFirst { it.startsWith("LD_PRELOAD=") }
        if (i >= 0) {
            val cur = fixed[i].removePrefix("LD_PRELOAD=")
            if (!cur.split(':').contains(lib)) fixed[i] = "LD_PRELOAD=" + if (cur.isEmpty()) lib else "$lib:$cur"
        } else {
            fixed += "LD_PRELOAD=$lib"
        }
        fixed.removeAll { it.startsWith("VAPOR_PATH_PKG=") || it.startsWith("VAPOR_PATH_TO=") }
        fixed += "VAPOR_PATH_PKG=$packageName"
        fixed += "VAPOR_PATH_TO=$realPrefix"
        return fixed.toTypedArray()
    }

    /** Same as [fixEnv] for a ProcessBuilder environment. */
    @JvmStatic
    fun fixEnv(env: MutableMap<String, String>) {
        if (!isSecondaryUser()) return
        val arr = fixEnv(env.map { "${it.key}=${it.value}" }.toTypedArray()) ?: return
        env.clear()
        for (kv in arr) {
            val eq = kv.indexOf('=')
            if (eq > 0) env[kv.substring(0, eq)] = kv.substring(eq + 1)
        }
    }
}
