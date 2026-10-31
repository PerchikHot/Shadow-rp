package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpLocalPromptTest {
    @Test fun localPromptPreservesPortableCardFields() {
        val prompt = RpLocalPrompt.build("Алиса", "детектив", "синий плащ",
            "поезд", "не выходить из роли", "встреча у маяка",
            "Игрок", listOf("user" to "Продолжай"), true)
        listOf("детектив", "синий плащ", "поезд", "не выходить из роли",
            "встреча у маяка", "Продолжай").forEach { assertTrue(prompt.contains(it)) }
        assertTrue(prompt.endsWith("<think>\n\n</think>\n"))
    }

    @Test fun localPromptBoundsUntrustedCardAndHistory() {
        val prompt = RpLocalPrompt.build("n".repeat(1000), "p".repeat(10000),
            "a".repeat(10000), "s".repeat(10000), "r".repeat(10000),
            "m".repeat(10000), "i".repeat(10000),
            List(100) { "user" to "x".repeat(20000) }, false)
        assertTrue(prompt.length < 1600)
        assertFalse(prompt.contains("p".repeat(121)))
        assertEquals(3, Regex("<\\|im_start\\|>user").findAll(prompt).count())
    }
}
