package com.shadowrp.app

import org.json.JSONArray
import org.json.JSONObject

/** Portable, user-approved character and dialogue archive shared with Windows Shadow RP.
 * No API keys, model files, avatars, device identifiers or other preferences are exported.
 */
internal object RpBackup {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_CHARACTERS = 200
    private const val MAX_MESSAGES = 10000
    private const val MAX_FIELD = 30000
    private const val MAX_MESSAGE = 20000
    private val fields = listOf("description", "scenario", "memory", "appearance", "greeting", "protocol")

    data class Card(
        val fields: Map<String, String>,
        val messages: List<Pair<String, String>>
    )

    fun encode(cards: Map<String, Card>): ByteArray {
        require(cards.size <= MAX_CHARACTERS) { "Слишком много персонажей" }
        val characters = JSONObject()
        cards.forEach { (name, card) ->
            checkName(name)
            val item = JSONObject()
            fields.forEach { field ->
                val value = card.fields[field] ?: ""
                require(value.length <= MAX_FIELD) { "Слишком длинное поле $field" }
                item.put(field, value)
            }
            require(card.messages.size <= MAX_MESSAGES) { "Слишком длинная история" }
            val messages = JSONArray()
            card.messages.forEach { (role, content) ->
                checkMessage(role, content)
                messages.put(JSONObject().put("role", role).put("content", content))
            }
            item.put("messages", messages)
            characters.put(name, item)
        }
        val bytes = JSONObject().put("characters", characters).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Архив больше 8 МБ" }
        return bytes
    }

    fun decode(bytes: ByteArray): Map<String, Card> {
        require(bytes.size <= MAX_BYTES) { "Архив больше 8 МБ" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val characters = root.optJSONObject("characters")
            ?: throw IllegalArgumentException("Неверный формат архива Shadow RP")
        require(characters.length() <= MAX_CHARACTERS) { "Слишком много персонажей" }
        val result = linkedMapOf<String, Card>()
        val names = characters.keys()
        while (names.hasNext()) {
            val name = names.next()
            checkName(name)
            val item = characters.optJSONObject(name)
                ?: throw IllegalArgumentException("Некорректная карточка персонажа")
            val values = fields.associateWith { field ->
                if (!item.has(field)) "" else {
                    val value = item.opt(field)
                    require(value is String && value.length <= MAX_FIELD) { "Некорректное поле $field" }
                    value
                }
            }
            val messages = item.optJSONArray("messages")
                ?: if (!item.has("messages")) JSONArray() else throw IllegalArgumentException("Некорректная история")
            require(messages.length() <= MAX_MESSAGES) { "Слишком длинная история" }
            val history = ArrayList<Pair<String, String>>(messages.length())
            for (i in 0 until messages.length()) {
                val message = messages.optJSONObject(i)
                    ?: throw IllegalArgumentException("Некорректное сообщение")
                val role = message.opt("role")
                val content = message.opt("content")
                require(role is String && content is String) { "Некорректное сообщение" }
                checkMessage(role, content)
                history.add(role to content)
            }
            result[name] = Card(values, history)
        }
        return result
    }

    private fun checkName(name: String) {
        require(name.isNotBlank() && name.length <= 100) { "Некорректное имя персонажа" }
    }

    private fun checkMessage(role: String, content: String) {
        require(role == "user" || role == "assistant") { "Некорректная роль сообщения" }
        require(content.length <= MAX_MESSAGE) { "Слишком длинное сообщение" }
    }
}
