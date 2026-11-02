package com.shadowrp.app

/** Player-authored conditional lore, never inferred from model replies.
 * Each line is "keyword => fact"; only facts triggered by recent dialogue
 * enter the prompt. This format is shared with Windows Shadow RP.
 */
internal object RpLorebook {
    const val MAX_TEXT = 8000
    const val MAX_ENTRIES = 40
    const val MAX_CONTEXT = 1600

    fun parse(raw: String): List<Pair<String, String>> {
        require(raw.length <= MAX_TEXT) { "Лорбук больше 8000 символов" }
        val entries = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val separator = line.indexOf("=>")
            require(separator >= 0) { "Формат записи: ключ => факт" }
            val keyword = line.substring(0, separator).trim()
            val fact = line.substring(separator + 2).trim()
            require(keyword.length in 2..60 && fact.length in 1..300 &&
                (keyword + fact).none { it.code < 32 || it.code == 127 }) {
                "Некорректная запись лорбука"
            }
            require(seen.add(keyword.lowercase(java.util.Locale.ROOT))) {
                "Повторяющийся ключ лорбука"
            }
            entries.add(keyword to fact)
            require(entries.size <= MAX_ENTRIES) { "В лорбуке больше 40 записей" }
        }
        return entries
    }

    fun resolve(raw: String, history: List<Pair<String, String>>): String {
        val entries = parse(raw)
        val recent = history.takeLast(8)
            .filter { it.first == "user" || it.first == "assistant" }
            .joinToString(" ") { it.second.takeLast(2000) }.takeLast(4000)
        if (recent.isBlank()) return ""
        val selected = mutableListOf<String>()
        var remaining = MAX_CONTEXT
        for ((keyword, fact) in entries) {
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}_])" + Regex.escape(keyword) +
                    "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
            if (!pattern.containsMatchIn(recent)) continue
            val line = "$keyword: $fact"
            if (line.length + 1 > remaining) continue
            selected.add(line)
            remaining -= line.length + 1
        }
        return selected.joinToString("\n")
    }
}
