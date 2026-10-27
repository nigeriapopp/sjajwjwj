package app.gamenative.vapor

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import org.json.JSONObject
import timber.log.Timber

/**
 * Save snapshots: copies of a game's save folders that can be put back later.
 *
 * Windows games under Wine cannot be frozen mid-frame like console emulators, so Vapor snapshots the
 * places games keep their saves inside the game's own Windows user profile (Documents, AppData,
 * Saved Games). Loading puts those files back before the game starts.
 */
object SaveSnapshots {
    const val USER = "xuser"
    const val DIR_NAME = ".vapor_snapshots"
    const val MAX_SNAPSHOTS = 12
    private const val META = "snapshot.json"
    private const val PENDING = "pending_restore"
    private const val MAX_FILE_BYTES = 256L * 1024 * 1024

    val SAVE_FOLDERS = listOf("Documents", "AppData", "Saved Games")

    /** Folders that only hold caches; skipping them keeps snapshots small and fast. */
    private val SKIP_DIRS = setOf(
        "cache", "caches", "shadercache", "shader cache", "dxcache", "glcache", "d3dscache",
        "temp", "tmp", "crashdumps", "crashes", "logs", "nvidia", "webcache", "gpucache", "code cache",
    )

    data class Snapshot(
        val id: String,
        val label: String,
        val createdAt: Long,
        val bytes: Long,
        val files: Int,
        val dir: File,
    )

    fun userDir(containerRoot: File) = File(containerRoot, ".wine/drive_c/users/$USER")

    fun snapshotsDir(containerRoot: File) = File(containerRoot, DIR_NAME)

    fun list(containerRoot: File): List<Snapshot> {
        val dirs = snapshotsDir(containerRoot).listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readMeta(it) }.sortedByDescending { it.createdAt }
    }

    /** Copies the save folders into a new slot. Returns null when there was nothing to save. */
    fun create(containerRoot: File, label: String, now: Long = System.currentTimeMillis()): Snapshot? {
        val source = userDir(containerRoot)
        if (!source.isDirectory) return null
        val id = "s$now"
        val target = File(snapshotsDir(containerRoot), id)
        val tmp = File(snapshotsDir(containerRoot), "$id.partial")
        tmp.deleteRecursively()
        var bytes = 0L
        var files = 0
        try {
            val profile = source.canonicalFile
            for (folder in SAVE_FOLDERS) {
                val from = File(source, folder)
                if (!from.isDirectory) continue
                // Wine may link a profile folder to the unix home, which here is the whole container:
                // never copy a folder that contains the profile itself.
                val real = from.canonicalFile
                if (profile.path.startsWith(real.path + File.separator) || profile == real) continue
                val stats = copyTree(from, File(tmp, folder))
                bytes += stats.first
                files += stats.second
            }
            if (files == 0) {
                tmp.deleteRecursively()
                return null
            }
            File(tmp, META).writeText(
                JSONObject()
                    .put("label", label)
                    .put("createdAt", now)
                    .put("bytes", bytes)
                    .put("files", files)
                    .toString(),
            )
            target.deleteRecursively()
            if (!tmp.renameTo(target)) {
                tmp.deleteRecursively()
                return null
            }
            prune(containerRoot)
            return readMeta(target)
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] snapshot failed")
            tmp.deleteRecursively()
            return null
        }
    }

    /**
     * Puts a snapshot's files back. Files written after the snapshot stay (nothing is deleted), so a
     * restore can never lose a save the player made later. The current saves are snapshotted first.
     */
    fun restore(containerRoot: File, snapshot: Snapshot, backupFirst: Boolean = true): Boolean {
        if (!snapshot.dir.isDirectory) return false
        if (backupFirst) create(containerRoot, "Before loading", System.currentTimeMillis())
        val dest = userDir(containerRoot)
        return try {
            for (folder in SAVE_FOLDERS) {
                val from = File(snapshot.dir, folder)
                if (from.isDirectory) copyTree(from, File(dest, folder))
            }
            true
        } catch (e: Exception) {
            Timber.w(e, "[Vapor] restore failed")
            false
        }
    }

    fun delete(snapshot: Snapshot): Boolean = snapshot.dir.deleteRecursively()

    /** Asks for [snapshot] to be loaded the next time the game starts (it is running now). */
    fun requestRestoreOnNextLaunch(containerRoot: File, snapshot: Snapshot) {
        snapshotsDir(containerRoot).mkdirs()
        File(snapshotsDir(containerRoot), PENDING).writeText(snapshot.id)
    }

    fun pendingRestore(containerRoot: File): String? =
        File(snapshotsDir(containerRoot), PENDING).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    /** Called before Wine starts. Returns the loaded snapshot, if any. */
    fun applyPendingRestore(containerRoot: File): Snapshot? {
        val pendingFile = File(snapshotsDir(containerRoot), PENDING)
        val id = pendingRestore(containerRoot) ?: return null
        pendingFile.delete()
        val snap = list(containerRoot).firstOrNull { it.id == id } ?: return null
        return if (restore(containerRoot, snap)) snap else null
    }

    const val AUTO_LABEL = "Auto backup"
    const val MAX_AUTO = 3

    /**
     * Rolling backup after a play session: skipped when no save file changed since the last one,
     * keeps the newest [MAX_AUTO].
     */
    fun autoBackup(containerRoot: File, now: Long = System.currentTimeMillis()): Snapshot? {
        val lastAuto = list(containerRoot).firstOrNull { it.label == AUTO_LABEL }
        if (lastAuto != null && latestSaveChange(containerRoot) <= lastAuto.createdAt) return null
        val snap = create(containerRoot, AUTO_LABEL, now) ?: return null
        list(containerRoot).filter { it.label == AUTO_LABEL }.drop(MAX_AUTO).forEach { delete(it) }
        return snap
    }

    /** Newest modification time of any file in the save folders (0 when there are none). */
    fun latestSaveChange(containerRoot: File): Long {
        val source = userDir(containerRoot)
        var newest = 0L
        fun walk(dir: File, depth: Int) {
            if (depth > 12) return
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (java.nio.file.Files.isSymbolicLink(c.toPath())) continue
                if (c.isDirectory) {
                    if (c.name.lowercase() !in SKIP_DIRS) walk(c, depth + 1)
                } else if (c.lastModified() > newest) {
                    newest = c.lastModified()
                }
            }
        }
        val profile = runCatching { source.canonicalFile }.getOrNull() ?: return 0L
        for (folder in SAVE_FOLDERS) {
            val f = File(source, folder)
            if (!f.isDirectory) continue
            val real = f.canonicalFile
            if (profile.path.startsWith(real.path + File.separator) || profile == real) continue
            walk(f, 0)
        }
        return newest
    }

    /** Keeps the newest [MAX_SNAPSHOTS]; automatic backups go first. */
    private fun prune(containerRoot: File) {
        val all = list(containerRoot)
        if (all.size <= MAX_SNAPSHOTS) return
        val removable = all.sortedWith(
            compareBy<Snapshot>({ it.label != "Before loading" && it.label != AUTO_LABEL }, { it.createdAt }),
        )
        removable.take(all.size - MAX_SNAPSHOTS).forEach { delete(it) }
    }

    private fun readMeta(dir: File): Snapshot? = try {
        val json = JSONObject(File(dir, META).readText())
        Snapshot(
            id = dir.name,
            label = json.optString("label", "Save"),
            createdAt = json.optLong("createdAt", dir.lastModified()),
            bytes = json.optLong("bytes", 0),
            files = json.optInt("files", 0),
            dir = dir,
        )
    } catch (e: Exception) {
        null
    }

    /** Copies regular files only (links are skipped so nothing outside the profile is followed). */
    private fun copyTree(from: File, to: File): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        val children = from.listFiles() ?: return 0L to 0
        for (child in children) {
            val path = child.toPath()
            if (Files.isSymbolicLink(path)) continue
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                if (child.name.lowercase() in SKIP_DIRS) continue
                val sub = copyTree(child, File(to, child.name))
                bytes += sub.first
                files += sub.second
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                val len = child.length()
                if (len > MAX_FILE_BYTES) continue
                to.mkdirs()
                child.copyTo(File(to, child.name), overwrite = true)
                bytes += len
                files++
            }
        }
        return bytes to files
    }

    fun describeSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    }
}
