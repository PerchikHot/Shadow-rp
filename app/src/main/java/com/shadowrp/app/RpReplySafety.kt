package com.shadowrp.app

import org.json.JSONArray
import org.json.JSONObject

/** Guard asynchronous replies against imports, edited histories and double sends. */
internal object RpReplySafety {
    fun canApply(
        requestedHistory: List<Pair<String, String>>,
        currentHistory: List<Pair<String, String>>?
    ): Boolean = requestedHistory.isNotEmpty() &&
        requestedHistory.last().first == "user" &&
        currentHistory == requestedHistory

    /** Reject unfinished model output before committing it to RP history.
     * Older OpenAI-compatible local servers may omit finish_reason.
     */
    fun extractChoice(choice: JSONObject): String {
        val reason = choice.opt("finish_reason")
        require(reason == null || reason == JSONObject.NULL || reason == "stop") {
            "Модель не завершила ответ; сократи контекст или проверь настройки"
        }
        val message = choice.optJSONObject("message")
            ?: throw IllegalArgumentException("Неверный формат ответа модели")
        require(message.optString("role", "assistant") == "assistant" &&
            !message.has("tool_calls") &&
            (message.isNull("refusal") || !message.has("refusal"))) {
            "Модель вернула не текстовую реплику персонажа"
        }
        return extractContent(message.opt("content"))
    }

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
        require(reply.length <= 20000) {
            "Ответ модели слишком длинный; сократи контекст или лимит ответа"
        }
        return reply
    }
}
