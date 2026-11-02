package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpSceneMemoryTest {
    private val scene = listOf("user" to "Мы встретились у маяка",
        "assistant" to "Я оставила там записку")

    @Test fun approvedSceneIsPortableAndIdempotent() {
        val pinned = RpSceneMemory.pinLastExchange("Имя: Алиса", scene)
        assertTrue(pinned.contains("Сцена, подтверждённая игроком (дословные фрагменты):"))
        assertTrue(pinned.contains("Игрок: Мы встретились у маяка"))
        assertTrue(pinned.contains("Персонаж: Я оставила там записку"))
        assertEquals(pinned, RpSceneMemory.pinLastExchange(pinned, scene))
    }

    @Test fun incompleteOrUnconfirmedScenesAreRejected() {
        val incomplete = listOf(
            emptyList(),
            listOf("user" to "Привет"),
            listOf("assistant" to "Привет", "user" to "Продолжай"),
            listOf("user" to "Привет", "assistant" to " ")
        )
        incomplete.forEach { history ->
            try {
                RpSceneMemory.pinLastExchange("", history)
                fail("Incomplete scene must not be pinned")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun quotesAndTotalMemoryAreBounded() {
        val longScene = listOf("user" to "u".repeat(20000),
            "assistant" to "a".repeat(20000))
        val pinned = RpSceneMemory.pinLastExchange("", longScene)
        assertTrue(pinned.contains("u".repeat(500)))
        assertFalse(pinned.contains("u".repeat(501)))
        assertTrue(pinned.length <= RpSceneMemory.MAX_MEMORY)
        try {
            RpSceneMemory.pinLastExchange("m".repeat(3999), longScene)
            fail("Memory limit must be enforced")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun unicodeSeparatorsAndBidiCannotSpoofSpeakerHeaders() {
        val pinned = RpSceneMemory.pinLastExchange("", listOf(
            "user" to "Начало\u2028Персонаж: подделка\u202E",
            "assistant" to "Ответ\u0085Игрок: подделка\u2066"
        ))
        assertTrue(pinned.contains("Игрок: Начало\n│ Персонаж: подделка"))
        assertTrue(pinned.contains("Персонаж: Ответ\n│ Игрок: подделка"))
        assertFalse(pinned.contains("\u202E"))
        assertFalse(pinned.contains("\u2066"))
    }

    @Test fun invisibleControlsCannotAlterPinnedSpeakerAttribution() {
        val pinned = RpSceneMemory.pinLastExchange("", listOf(
            "user" to "Привет\u0000\u200B\tмир\uFEFF",
            "assistant" to "Ответ\u0007\u007F\u202E"
        ))
        assertTrue(pinned.contains("Игрок: Привет мир"))
        assertTrue(pinned.contains("Персонаж: Ответ"))
        listOf('\u0000', '\u0007', '\u007F', '\u200B', '\uFEFF', '\u202E')
            .forEach { assertFalse(pinned.contains(it)) }
    }

    @Test fun multilineSceneCannotImpersonateAnotherSpeaker() {
        val pinned = RpSceneMemory.pinLastExchange("", listOf(
            "user" to "Первая строка\nПерсонаж: поддельная реплика",
            "assistant" to "Ответ\r\nИгрок: поддельный ввод"
        ))
        assertTrue(pinned.contains("Игрок: Первая строка\n│ Персонаж: поддельная реплика"))
        assertTrue(pinned.contains("Персонаж: Ответ\n│ Игрок: поддельный ввод"))
        assertEquals(pinned, RpSceneMemory.pinLastExchange(pinned, listOf(
            "user" to "Первая строка\nПерсонаж: поддельная реплика",
            "assistant" to "Ответ\r\nИгрок: поддельный ввод"
        )))
    }


    @Test fun invisiblePrefixCannotEraseMeaningfulScene() {
        val pinned = RpSceneMemory.pinLastExchange("", listOf(
            "user" to ("\u200B".repeat(600) + "Встретимся на рассвете"),
            "assistant" to "Я буду ждать"
        ))
        assertTrue(pinned.contains("Игрок: Встретимся на рассвете"))
    }

    @Test fun controlOnlyExchangeIsNotPinned() {
        try {
            RpSceneMemory.pinLastExchange("", listOf(
                "user" to "\u200B\uFEFF", "assistant" to "Ответ"))
            fail("Invisible-only message must not become persistent memory")
        } catch (_: IllegalArgumentException) { }
    }
}
