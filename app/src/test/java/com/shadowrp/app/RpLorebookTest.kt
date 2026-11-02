package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpLorebookTest {
    @Test fun triggersOnlyRecentWholeWords() {
        val lore = "Портал => Портал ведёт в синий город\nМаяк => Маяк выключен"
        assertEquals("Портал: Портал ведёт в синий город",
            RpLorebook.resolve(lore, listOf("user" to "Мы нашли ПОРТАЛ!")))
        assertEquals("", RpLorebook.resolve(lore, listOf("user" to "Портальный зал")))
        assertEquals("", RpLorebook.resolve(lore,
            listOf("user" to "Маяк") + List(8) { "assistant" to "тишина" }))
        assertEquals(2, RpLorebook.parse(lore).size)
    }

    @Test fun assistantRepliesNeverReactivateLore() {
        val lore = "Портал => Скрытый проход\nМаяк => Потух"
        assertEquals("", RpLorebook.resolve(lore,
            listOf("assistant" to "Портал находится у реки")))
        assertEquals("Маяк: Потух", RpLorebook.resolve(lore,
            listOf("assistant" to "Портал находится у реки",
                "user" to "Посмотрим на маяк")))
        assertEquals("", RpLorebook.resolve(lore,
            listOf("user" to "Маяк") + List(8) { "assistant" to "Маяк" }))
    }

    @Test fun rejectsAmbiguousOrOversizedEntries() {
        for (raw in listOf("без разделителя", "А => текст",
            "Портал => факт\nпортал => другой",
            "Мир => " + "а".repeat(301),
            "x".repeat(8001))) {
            try {
                RpLorebook.parse(raw)
                fail("Invalid lorebook accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun boundsInjectedContextWithoutChangingSavedLore() {
        val lore = (1..30).joinToString("\n") {
            "ключ$it => " + "ф".repeat(200)
        }
        val history = listOf("user" to (1..30).joinToString(" ") { "ключ$it" })
        val resolved = RpLorebook.resolve(lore, history)
        assertTrue(resolved.length <= RpLorebook.MAX_CONTEXT)
        assertTrue(resolved.contains("ключ1:"))
        assertEquals(30, RpLorebook.parse(lore).size)
    }
}
