package com.shadowrp.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RpReplySafetyTest {
    @Test fun acceptsOnlyUnchangedPendingUserTurn() {
        val snapshot = listOf("user" to "Начинаем", "assistant" to "Да", "user" to "Продолжай")
        assertTrue(RpReplySafety.canApply(snapshot, snapshot.toList()))
        assertFalse(RpReplySafety.canApply(snapshot, snapshot + ("assistant" to "Дубликат")))
        assertFalse(RpReplySafety.canApply(snapshot, snapshot.dropLast(1)))
        assertFalse(RpReplySafety.canApply(snapshot, null))
        assertFalse(RpReplySafety.canApply(listOf("assistant" to "Старый ответ"),
            listOf("assistant" to "Старый ответ")))
        assertFalse(RpReplySafety.canApply(emptyList(), emptyList()))
    }

    @Test fun acceptsTypedTextPartsAndRejectsNonTextModelOutput() {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", "Первый"))
            .put(JSONObject().put("type", "text").put("text", "Второй"))
        assertEquals("Первый\nВторой", RpReplySafety.extractContent(parts))
        assertEquals("Привет", RpReplySafety.extractContent(" Привет "))
        for (bad in listOf(
            JSONArray(), JSONArray().put(JSONObject().put("type", "image_url")
                .put("image_url", "https://example.org")), JSONArray().put("bad"),
            JSONArray().put(JSONObject().put("type", "text").put("text", 42)),
            JSONObject.NULL, null
        )) {
            try {
                RpReplySafety.extractContent(bad)
                fail("Unsupported model reply must be rejected")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun boundsModelOutputAndRejectsEmptyReplies() {
        assertEquals("Привет", RpReplySafety.clean("  Привет  "))
        assertEquals(20000, RpReplySafety.clean("а".repeat(25000)).length)
        try {
            RpReplySafety.clean(" \n\t ")
            fail("Blank replies must be rejected")
        } catch (_: IllegalArgumentException) {
            // A failed request keeps the user turn available for retry.
        }
    }
}
