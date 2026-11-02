package com.shadowrp.app

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Portable, user-approved character and dialogue archive shared with Windows Shadow RP.
 * No API keys, model files, avatars, device identifiers or other preferences are exported.
 */
internal object RpBackup {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_CHARACTERS = 200
    private const val MAX_MESSAGES = 10000
    private const val MAX_FIELD = 30000
    private const val MAX_MESSAGE = 20000
    private val fields = listOf("description", "scenario", "memory", "appearance", "greeting", "protocol", "lorebook")

    data class Card(
        val fields: Map<String, String>,
        val messages: List<Pair<String, String>>
    )

    fun encode(cards: Map<String, Card>): ByteArray {
        require(cards.size <= MAX_CHARACTERS) { "Слишком много персонажей" }
        val characters = JSONObject()
        val encodedNames = mutableSetOf<String>()
        cards.forEach { (name, card) ->
            checkName(name)
            require(encodedNames.add(name.lowercase(java.util.Locale.ROOT))) {
                "В архиве есть персонажи с одинаковыми именами без учёта регистра"
            }
            val item = JSONObject()
            fields.forEach { field ->
                val value = card.fields[field] ?: ""
                require(value.length <= MAX_FIELD) { "Слишком длинное поле $field" }
                if (field == "lorebook") RpLorebook.parse(value)
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
        // Unlike String(bytes, UTF_8), a strict decoder does not silently
        // replace malformed characters in portable character names or dialogue.
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val root = JSONObject(decoder.decode(ByteBuffer.wrap(bytes)).toString())
        val characters = root.optJSONObject("characters")
            ?: throw IllegalArgumentException("Неверный формат архива Shadow RP")
        require(characters.length() <= MAX_CHARACTERS) { "Слишком много персонажей" }
        val result = linkedMapOf<String, Card>()
        val decodedNames = mutableSetOf<String>()
        val names = characters.keys()
        while (names.hasNext()) {
            val name = names.next()
            checkName(name)
            require(decodedNames.add(name.lowercase(java.util.Locale.ROOT))) {
                "Неоднозначные имена персонажей в архиве"
            }
            val item = characters.optJSONObject(name)
                ?: throw IllegalArgumentException("Некорректная карточка персонажа")
            val values = fields.associateWith { field ->
                if (!item.has(field)) "" else {
                    val value = item.opt(field)
                    require(value is String && value.length <= MAX_FIELD) { "Некорректное поле $field" }
                    value.also { if (field == "lorebook") RpLorebook.parse(it) }
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

    /** Preflight the entire merged archive before Android writes any preferences.
     * The Windows importer performs the same merged 200-card / 8-MiB check.
     * Never mutate the caller's maps when a collision is rejected.
     */
    fun checkedMerge(existing: Map<String, Card>, incoming: Map<String, Card>): Map<String, Card> {
        // A case-only name collision is ambiguous in the character picker.
        // The caller can explicitly choose "keep both" instead of silently
        // creating a second visually identical conversation.
        val oldNames = existing.keys.associateBy { it.lowercase(java.util.Locale.ROOT) }
        incoming.keys.forEach { name ->
            val previous = oldNames[name.lowercase(java.util.Locale.ROOT)]
            require(previous == null || previous == name) {
                "Имя персонажа отличается только регистром; импортируй как копию"
            }
        }
        val merged = LinkedHashMap(existing)
        merged.putAll(incoming)
        encode(merged) // validates names, fields, roles, history and total size
        return merged
    }

    /** Android parity with Windows 'keep both' import. The returned second map
     * contains only renamed/new incoming cards to write to preferences.
     * Reserve all incoming names before generating suffixes, so no import can
     * overwrite a different incoming card or an existing conversation.
     */
    fun checkedMergeAsCopies(
        existing: Map<String, Card>, incoming: Map<String, Card>
    ): Pair<Map<String, Card>, Map<String, Card>> {
        encode(existing)
        encode(incoming)
        val merged = LinkedHashMap(existing)
        val toWrite = linkedMapOf<String, Card>()
        val occupied = existing.keys.map { it.lowercase(java.util.Locale.ROOT) }.toMutableSet()
        val reserved = (existing.keys + incoming.keys)
            .map { it.lowercase(java.util.Locale.ROOT) }.toMutableSet()
        incoming.forEach { (name, card) ->
            var candidate = name
            if (candidate.lowercase(java.util.Locale.ROOT) in occupied) {
                var found = false
                for (number in 2..MAX_CHARACTERS + 1) {
                    val suffix = " (импорт $number)"
                    val next = name.take(100 - suffix.length).trimEnd() + suffix
                    if (next.lowercase(java.util.Locale.ROOT) !in reserved) {
                        candidate = next
                        found = true
                        break
                    }
                }
                require(found) { "Нет свободного имени для импортированного персонажа" }
            }
            val key = candidate.lowercase(java.util.Locale.ROOT)
            require(key !in occupied) { "Повторяющееся имя персонажа" }
            merged[candidate] = card
            toWrite[candidate] = card
            occupied.add(key)
            reserved.add(key)
        }
        encode(merged) // Full preflight before Android writes a single preference.
        return merged to toWrite
    }

    private fun checkName(name: String) {
        // Character names become local preference keys and visible chat labels.
        // Reject control characters instead of creating invisible duplicate
        // names or multi-line entries that break the character picker.
        require(name.isNotBlank() && name.length <= 100 &&
            name.none { it.code < 32 || it.code == 127 }) {
            "Некорректное имя персонажа"
        }
    }

    private fun checkMessage(role: String, content: String) {
        require(role == "user" || role == "assistant") { "Некорректная роль сообщения" }
        require(content.length <= MAX_MESSAGE) { "Слишком длинное сообщение" }
    }
}
