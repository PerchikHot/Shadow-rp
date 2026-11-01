package com.shadowrp.app

/** Player-approved scene memory, compatible with the Windows rp_memory.py format.
 * No AI-generated summaries or unconfirmed facts are saved.
 */
internal object RpSceneMemory {
    const val MAX_MEMORY = 4000
    private const val MAX_QUOTE = 500

    // Keep multiline dialogue readable without allowing a quoted line to
    // masquerade as another top-level speaker in persistent scene memory.
    private fun quote(text: String): String = text.trim().take(MAX_QUOTE)
        .replace("\r\n", "\n").replace('\r', '\n')
        .replace('\u0085', '\n').replace('\u2028', '\n').replace('\u2029', '\n')
        .replace(Regex("[\\u202A-\\u202E\\u2066-\\u2069]"), "")
        .replace("\n", "\n│ ")

    fun pinLastExchange(memory: String, messages: List<Pair<String, String>>): String {
        require(memory.length <= MAX_MEMORY) { "Память слишком длинная; отредактируй её вручную." }
        require(messages.size >= 2) { "Для закрепления нужна завершённая сцена из двух реплик." }
        val (userRole, userText) = messages[messages.size - 2]
        val (assistantRole, assistantText) = messages.last()
        require(userRole == "user" && assistantRole == "assistant" &&
            userText.isNotBlank() && assistantText.isNotBlank()) {
            "Последняя сцена ещё не завершена ответом персонажа."
        }
        val note = "Сцена, подтверждённая игроком (дословные фрагменты):\n" +
            "Игрок: " + quote(userText) + "\n" +
            "Персонаж: " + quote(assistantText)
        if (memory.contains(note)) return memory
        val updated = memory.trimEnd() + (if (memory.isNotBlank()) "\n\n" else "") + note
        require(updated.length <= MAX_MEMORY) {
            "Память заполнена. Сократи старые записи перед добавлением сцены."
        }
        return updated
    }
}
