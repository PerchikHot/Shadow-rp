package com.shadowrp.app

/** Player-approved scene memory, compatible with the Windows rp_memory.py format.
 * No AI-generated summaries or unconfirmed facts are saved.
 */
internal object RpSceneMemory {
    const val MAX_MEMORY = 4000
    private const val MAX_QUOTE = 500

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
            "Игрок: " + userText.trim().take(MAX_QUOTE) + "\n" +
            "Персонаж: " + assistantText.trim().take(MAX_QUOTE)
        if (memory.contains(note)) return memory
        val updated = memory.trimEnd() + (if (memory.isNotBlank()) "\n\n" else "") + note
        require(updated.length <= MAX_MEMORY) {
            "Память заполнена. Сократи старые записи перед добавлением сцены."
        }
        return updated
    }
}
