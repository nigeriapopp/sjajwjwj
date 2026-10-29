package app.gamenative.vapor

import java.io.File

/**
 * Pure helpers that turn raw logs into a crash report a person can read (unit tested).
 * The raw log is mostly file-unpacking spam, so the useful lines go first.
 */
object CrashReportFormat {
    /** Log lines that are never useful for a crash: file unpacking, chmod, Java GC. */
    private val NOISE = listOf(
        "D/Extraction", "D/FileUtils", "D/ContentsManager", "concurrent mark compact GC",
        "I/AssetUtils", "D/ProcessHelper", "D/BasePrefix",
    )

    /** Lines that usually point at the cause. */
    private val SIGNAL = Regex(
        "(err:|Unhandled exception|unhandled page fault|wine: |SIGSEGV|SIGABRT|SIGILL|segfault|" +
            "Assertion|fatal|Fatal|FATAL|Crash|crash!|Exception|exception code|" +
            "Failed to|failed to|Could not|could not|not found|Unable to|unable to|" +
            "Guest program terminated|VK_ERROR|DXVK: |out of memory|Out of memory)",
    )

    fun isNoise(line: String): Boolean = NOISE.any { line.contains(it) }

    fun clean(lines: List<String>): List<String> = lines.filterNot { isNoise(it) }

    /** The last [max] lines that look like errors, without exact repeats. */
    fun keyLines(lines: List<String>, max: Int = 80): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (line in lines.asReversed()) {
            if (!SIGNAL.containsMatchIn(line)) continue
            // Ignore the timestamp/pid prefix when de-duplicating.
            val key = line.substringAfter("): ", line)
            if (seen.add(key)) out += line
            if (out.size >= max) break
        }
        return out.asReversed()
    }

    /** Newest file named [name] (case-insensitive) under [root], up to [depth] folders down. */
    fun newestFile(root: File, name: String, depth: Int = 4): File? {
        var best: File? = null
        fun walk(dir: File, d: Int) {
            if (d > depth) return
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (java.nio.file.Files.isSymbolicLink(c.toPath())) continue
                if (c.isDirectory) walk(c, d + 1)
                else if (c.name.equals(name, ignoreCase = true) && (best == null || c.lastModified() > best!!.lastModified())) best = c
            }
        }
        if (root.isDirectory) walk(root, 0)
        return best
    }

    /** Newest *.log file under [root] (Unreal writes Saved/Logs/<Game>.log). */
    fun newestLog(root: File, depth: Int = 5): File? {
        var best: File? = null
        fun walk(dir: File, d: Int) {
            if (d > depth) return
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (java.nio.file.Files.isSymbolicLink(c.toPath())) continue
                if (c.isDirectory) walk(c, d + 1)
                else if (c.name.endsWith(".log", ignoreCase = true) && (best == null || c.lastModified() > best!!.lastModified())) best = c
            }
        }
        if (root.isDirectory) walk(root, 0)
        return best
    }

    fun tail(file: File, lines: Int): String = try {
        file.readLines().takeLast(lines).joinToString("\n")
    } catch (e: Exception) {
        "(could not read ${file.name}: ${e.message})"
    }
}
