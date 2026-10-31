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
}
