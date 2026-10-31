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
}
