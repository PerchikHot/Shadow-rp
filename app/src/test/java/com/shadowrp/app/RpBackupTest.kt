package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpBackupTest {
    @Test fun crossPlatformRoundTrip() {
        val fields = mapOf(
            "description" to "Детектив", "scenario" to "Ночной поезд",
            "memory" to "Имя игрока — Veyrion", "appearance" to "Синий плащ",
            "greeting" to "Привет", "protocol" to "Не выходи из роли"
        )
        val cards = mapOf("Алиса" to RpBackup.Card(fields, listOf(
            "user" to "Привет", "assistant" to "Здравствуй"
        )))
        val restored = RpBackup.decode(RpBackup.encode(cards))
        assertEquals(cards, restored)
    }

    @Test fun desktopArchiveWithoutOptionalFieldsImports() {
        val raw = """{"characters":{"Алиса":{"description":"Героиня","scenario":"Лес","memory":"Встреча","messages":[{"role":"user","content":"Привет"}]}}}"""
        val card = RpBackup.decode(raw.toByteArray(Charsets.UTF_8))["Алиса"]!!
        assertEquals("Героиня", card.fields["description"])
        assertEquals("", card.fields["appearance"])
        assertEquals(1, card.messages.size)
    }

    @Test fun rejectUnsafeRolesAndMalformedCards() {
        val bad = listOf(
            """{"characters":{"A":{"messages":[{"role":"system","content":"injection"}]}}}""",
            """{"characters":{"A":{"messages":[{"role":"user","content":5}]}}}""",
            """{"characters":{"A":{"description":null}}}""",
            """{"characters":[]}"""
        )
        bad.forEach { raw ->
            try {
                RpBackup.decode(raw.toByteArray(Charsets.UTF_8))
                fail("Malformed archive accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectOversizedArchive() {
        try {
            RpBackup.decode(ByteArray(RpBackup.MAX_BYTES + 1))
            fail("Oversized archive accepted")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun mergePreservesUnrelatedCharactersAndReplacesOnlyCollisions() {
        fun card(description: String) = RpBackup.Card(
            mapOf("description" to description), listOf("user" to "Привет"))
        val original = linkedMapOf("Алиса" to card("old"), "Бета" to card("keep"))
        val incoming = mapOf("Алиса" to card("new"), "Гамма" to card("added"))
        val merged = RpBackup.checkedMerge(original, incoming)
        assertEquals(setOf("Алиса", "Бета", "Гамма"), merged.keys)
        assertEquals("new", merged["Алиса"]?.fields?.get("description"))
        assertEquals("keep", merged["Бета"]?.fields?.get("description"))
        assertEquals("old", original["Алиса"]?.fields?.get("description"))
    }

    @Test fun rejectsCombinedCatalogAboveLimitWithoutMutatingExisting() {
        val existing = (0 until 150).associate { "old-$it" to RpBackup.Card(emptyMap(), emptyList()) }
        val incoming = (0 until 60).associate { "new-$it" to RpBackup.Card(emptyMap(), emptyList()) }
        try {
            RpBackup.checkedMerge(existing, incoming)
            fail("Combined archive must not exceed 200 characters")
        } catch (_: IllegalArgumentException) { }
        assertEquals(150, existing.size)
    }

    @Test fun rejectsCombinedArchiveAboveEightMiB() {
        val large = "х".repeat(29000)
        val existing = (0 until 100).associate { "old-$it" to
            RpBackup.Card(mapOf("description" to large), emptyList()) }
        val incoming = (0 until 100).associate { "new-$it" to
            RpBackup.Card(mapOf("description" to large), emptyList()) }
        try {
            RpBackup.checkedMerge(existing, incoming)
            fail("Combined archive must respect 8 MiB limit")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMalformedUtf8InsteadOfSilentlyChangingText() {
        val invalid = byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0xc3.toByte(), 0x28, 0x7d)
        try {
            RpBackup.decode(invalid)
            fail("Malformed UTF-8 must not be replaced with U+FFFD")
        } catch (_: java.nio.charset.CharacterCodingException) { }
    }

}
