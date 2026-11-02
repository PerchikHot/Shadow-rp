package com.shadowrp.app

/** Portable dialogue search and rewind rules. No storage or Android UI dependencies. */
internal object RpHistory {
    data class Match(val index: Int, val role: String, val preview: String)

    // Python desktop uses casefold(), where ß expands to ss. Keep Android
    // search compatible and map folded offsets back to original text before
    // slicing the preview, otherwise long scenes show the wrong passage.
    private fun folded(value: String): String =
        value.lowercase(java.util.Locale.ROOT).replace("ß", "ss")

    private fun originalMatchOffset(content: String, needle: String): Int {
        val foldedAt = folded(content).indexOf(folded(needle))
        if (foldedAt < 0) return -1
        var offset = 0
        for ((index, char) in content.withIndex()) {
            val width = folded(char.toString()).length
            if (offset + width > foldedAt) return index
            offset += width
        }
        return content.length
    }

    fun search(messages: List<Pair<String, String>>, query: String, limit: Int = 50): List<Match> {
        require(query.length <= 200 && limit in 1..100) { "Слишком длинный запрос поиска" }
        val trimmed = query.trim()
        val first = trimmed.substringBefore(' ').lowercase()
        val aliases = mapOf(
            "role:player" to "user", "role:user" to "user",
            "role:игрок" to "user", "role:character" to "assistant",
            "role:assistant" to "assistant", "role:персонаж" to "assistant")
        require(!first.startsWith("role:") || first in aliases) {
            "Неизвестный фильтр участника"
        }
        val roleFilter = aliases[first]
        val needle = if (roleFilter == null) trimmed
            else trimmed.substringAfter(' ', "").trim()
        val result = ArrayList<Match>()
        for (index in messages.indices.reversed()) {
            val (role, content) = messages[index]
            if (role != "user" && role != "assistant") continue
            if (roleFilter != null && role != roleFilter) continue
            val matchAt = if (needle.isEmpty()) 0 else originalMatchOffset(content, needle)
            if (matchAt < 0) continue
            // Long scenes often mention the searched event near the end.
            // Center the preview around the match so it is visible in the UI.
            val from = (matchAt - 48).coerceAtLeast(0)
            val to = (from + 160).coerceAtMost(content.length)
            val snippet = content.substring(from, to)
                .replace(Regex("\\s+"), " ").trim()
            val preview = (if (from > 0) "…" else "") + snippet +
                (if (to < content.length) "…" else "")
            result.add(Match(index, role, preview))
            if (result.size == limit) break
        }
        return result
    }

    /** Retain the selected player's turn so the model can regenerate its reply. */
    fun rewind(messages: List<Pair<String, String>>, index: Int): List<Pair<String, String>> {
        require(index in messages.indices && messages[index].first == "user") {
            "Выбери реплику игрока для отката"
        }
        require(index < messages.lastIndex) { "После этой реплики ещё нет ответа для отката" }
        require(messages.all { it.first == "user" || it.first == "assistant" }) {
            "История содержит неподдерживаемые роли"
        }
        return messages.take(index + 1)
    }
}
