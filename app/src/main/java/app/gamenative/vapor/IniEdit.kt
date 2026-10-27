package app.gamenative.vapor

/** Section-aware INI edits (the stock ini fixer appends missing keys to the end of the file). */
object IniEdit {
    /**
     * Sets [key]=[value] inside [section] (case-insensitive). Replaces an existing value, adds the key
     * at the end of that section if missing, or appends the section if it does not exist.
     */
    fun set(content: String, section: String, key: String, value: String): String {
        val nl = if (content.contains("\r\n")) "\r\n" else "\n"
        val lines = content.split(Regex("\r?\n")).toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)

        var start = -1
        for (i in lines.indices) {
            if (lines[i].trim().equals("[$section]", ignoreCase = true)) {
                start = i
                break
            }
        }
        if (start == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("[$section]")
            lines.add("$key=$value")
            return lines.joinToString(nl) + nl
        }

        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                end = i
                break
            }
        }
        for (i in start + 1 until end) {
            val eq = lines[i].indexOf('=')
            if (eq > 0 && lines[i].substring(0, eq).trim().equals(key, ignoreCase = true)) {
                lines[i] = lines[i].substring(0, eq).trim() + "=" + value
                return lines.joinToString(nl) + nl
            }
        }
        // Insert after the last non-blank line of the section.
        var insertAt = end
        while (insertAt > start + 1 && lines[insertAt - 1].isBlank()) insertAt--
        lines.add(insertAt, "$key=$value")
        return lines.joinToString(nl) + nl
    }
}
