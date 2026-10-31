package com.shadowrp.app

/** Compact Qwen-compatible prompt. Local runtime has only 768 context tokens.
 * Preserve scenario and approved memory while keeping the prompt bounded.
 */
internal object RpLocalPrompt {
    fun build(
        name: String, persona: String, appearance: String, scenario: String,
        protocol: String, memory: String, identity: String,
        history: List<Pair<String, String>>, qwen3: Boolean
    ): String {
        val system = "Ты ${name.take(60)}, персонаж RP. " +
            "Характер: ${persona.take(120)}. " +
            "Внешность: ${appearance.take(75)}. " +
            "Сценарий: ${scenario.take(110)}. " +
            "Правила: ${protocol.take(75)}. " +
            "Закреплённая память игрока: ${memory.take(180)}. " +
            "Игрок: ${identity.take(50)}. " +
            "Продолжай сюжет по-русски от лица персонажа. Не придумывай прошлые встречи."
        val recent = history.takeLast(3).filter { it.first == "user" || it.first == "assistant" }
            .joinToString("") { (role, value) ->
                "<|im_start|>$role\n" + value.take(100) + "<|im_end|>\n"
            }
        return "<|im_start|>system\n" + system + "<|im_end|>\n" +
            recent + "<|im_start|>assistant\n" +
            (if (qwen3) "<think>\n\n</think>\n" else "")
    }
}
