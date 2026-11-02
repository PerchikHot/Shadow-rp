package com.shadowrp.app

/** Player-approved scene memory, compatible with the Windows rp_memory.py format.
 * No AI-generated summaries or unconfirmed facts are saved.
 */
internal object RpSceneMemory {
    const val MAX_MEMORY = 4000
    private const val MAX_QUOTE = 500

    // Keep multiline dialogue readable without allowing a quoted line to
    // masquerade as another top-level speaker in persistent scene memory.
    private fun quote(text: String): String {
        // Sanitize before truncation: 500 invisible characters must not
        // crowd out the actual roleplay text from a pinned scene.
        val cleaned = text.trim()
            .replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u0085', '\n').replace('\u2028', '\n').replace('\u2029', '\n')
            .replace('\t', ' ')
            .replace(Regex("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F\\u007F\\u200B\\uFEFF\\u202A-\\u202E\\u2066-\\u2069]"), "")
            .trim()
        return cleaned.take(MAX_QUOTE).replace("\n", "\n│ ")
    }

    fun pinLastExchange(memory: String, messages: List<Pair<String, String>>): String {
        require(memory.length <= MAX_MEMORY) { "Память слишком длинная; отредактируй её вручную." }
        require(messages.size >= 2) { "Для закрепления нужна завершённая сцена из двух реплик." }
        val (userRole, userText) = messages[messages.size - 2]
        val (assistantRole, assistantText) = messages.last()
        require(userRole == "user" && assistantRole == "assistant" &&
            userText.isNotBlank() && assistantText.isNotBlank()) {
            "Последняя сцена ещё не завершена ответом персонажа."
        }
        val playerQuote = quote(userText)
        val characterQuote = quote(assistantText)
        require(playerQuote.isNotBlank() && characterQuote.isNotBlank()) {
            "Пустую после очистки сцену нельзя закрепить."
        }
        val note = "Сцена, подтверждённая игроком (дословные фрагменты):\n" +
            "Игрок: " + playerQuote + "\n" +
            "Персонаж: " + characterQuote
        if (memory.contains(note)) return memory
        val updated = memory.trimEnd() + (if (memory.isNotBlank()) "\n\n" else "") + note
        require(updated.length <= MAX_MEMORY) {
            "Память заполнена. Сократи старые записи перед добавлением сцены."
        }
        return updated
    }
}
