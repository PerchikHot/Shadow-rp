package com.shadowrp.app

import org.json.JSONArray

/** Guard asynchronous replies against imports, edited histories and double sends. */
internal object RpReplySafety {
    fun canApply(
        requestedHistory: List<Pair<String, String>>,
        currentHistory: List<Pair<String, String>>?
    ): Boolean = requestedHistory.isNotEmpty() &&
        requestedHistory.last().first == "user" &&
        currentHistory == requestedHistory

    /** Parse standard OpenAI-compatible plain or typed text responses. */
    fun extractContent(content: Any?): String {
        if (content is String) return clean(content)
        require(content is JSONArray && content.length() in 1..100) {
            "Неподдерживаемый формат ответа модели"
        }
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i)
            require(part != null && part.optString("type") == "text" &&
                part.opt("text") is String) {
                "Неподдерживаемый формат ответа модели"
            }
            parts.add(part!!.getString("text"))
        }
        return clean(parts.joinToString("\n"))
    }

    fun clean(raw: String): String {
        val reply = raw.trim()
        require(reply.isNotEmpty()) { "Модель вернула пустой ответ" }
        return reply.take(20000)
    }
}
