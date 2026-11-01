package com.shadowrp.app

/** Portable dialogue search and rewind rules. No storage or Android UI dependencies. */
internal object RpHistory {
    data class Match(val index: Int, val role: String, val preview: String)

    fun search(messages: List<Pair<String, String>>, query: String, limit: Int = 50): List<Match> {
        require(query.length <= 200 && limit in 1..100) { "Слишком длинный запрос поиска" }
        val needle = query.trim()
        val result = ArrayList<Match>()
        for (index in messages.indices.reversed()) {
            val (role, content) = messages[index]
            if (role != "user" && role != "assistant") continue
            if (needle.isNotEmpty() && !content.contains(needle, ignoreCase = true)) continue
            result.add(Match(index, role, content.replace(Regex("\\s+"), " ").take(160)))
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
