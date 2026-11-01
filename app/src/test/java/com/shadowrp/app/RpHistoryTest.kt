package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpHistoryTest {
    private val history = listOf(
        "user" to "Привет, Алиса",
        "assistant" to "Привет, путешественник",
        "user" to "Мы встретились в лесу",
        "assistant" to "Я помню наш лес",
        "user" to "Продолжим завтра"
    )

    @Test fun searchIsRecentFirstAndCaseInsensitive() {
        val matches = RpHistory.search(history, "ЛЕС")
        assertEquals(listOf(3, 2), matches.map { it.index })
        assertEquals(listOf("assistant", "user"), matches.map { it.role })
        assertEquals(5, RpHistory.search(history, "").size)
        assertEquals(listOf(4, 3), RpHistory.search(history, "", 2).map { it.index })
    }

    @Test fun speakerFilteredSearchIsPortableAndNonDestructive() {
        assertEquals(listOf(2), RpHistory.search(history, "role:player лес").map { it.index })
        assertEquals(listOf(3), RpHistory.search(history, "role:character лес").map { it.index })
        assertEquals(listOf(4, 2, 0), RpHistory.search(history, "role:игрок").map { it.index })
        assertEquals(listOf(3, 1), RpHistory.search(history, "role:assistant").map { it.index })
        assertEquals(5, history.size)
        try {
            RpHistory.search(history, "role:admin лес")
            fail("Unknown speaker filter accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rewindKeepsSelectedPlayerTurnAndDoesNotMutateSource() {
        val result = RpHistory.rewind(history, 2)
        assertEquals(history.take(3), result)
        assertEquals(5, history.size)
        assertEquals("user", result.last().first)
    }

    @Test fun rejectAssistantLastTurnAndInvalidIndex() {
        for (index in listOf(-1, 1, 3, 4, 5)) {
            try {
                RpHistory.rewind(history, index)
                fail("Invalid rewind accepted: $index")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectOversizedSearchAndUnsupportedRoles() {
        try {
            RpHistory.search(history, "a".repeat(201))
            fail("Oversized query accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            RpHistory.rewind(listOf("system" to "hidden", "user" to "hi", "assistant" to "hey"), 1)
            fail("Unsupported role accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
