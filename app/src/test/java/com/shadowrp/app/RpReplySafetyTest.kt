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

    @Test fun rejectsUnfinishedOrToolCallRepliesAcrossBothChatModes() {
        val plain = JSONObject().put("finish_reason", "stop")
            .put("message", JSONObject().put("role", "assistant")
                .put("content", "Продолжаем сцену"))
        assertEquals("Продолжаем сцену", RpReplySafety.extractChoice(plain))
        assertEquals("Локальная модель", RpReplySafety.extractChoice(
            JSONObject().put("message", JSONObject().put("content", "Локальная модель"))))
        for (reason in listOf("length", "content_filter", "tool_calls", "function_call")) {
            val choice = JSONObject().put("finish_reason", reason)
                .put("message", JSONObject().put("content", "Обрыв сцены"))
            try {
                RpReplySafety.extractChoice(choice)
                fail("Incomplete model reply must not be stored: $reason")
            } catch (_: IllegalArgumentException) {}
        }
        for (message in listOf(
            JSONObject().put("content", "частичный ответ")
                .put("tool_calls", JSONArray().put(JSONObject().put("id", "call_1"))),
            JSONObject().put("content", "частичный ответ").put("refusal", "blocked"),
            JSONObject().put("role", "tool").put("content", "untrusted")
        )) {
            try {
                RpReplySafety.extractChoice(JSONObject().put("message", message))
                fail("Non-character response must be rejected")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun boundsModelOutputAndRejectsEmptyReplies() {
        assertEquals("Привет", RpReplySafety.clean("  Привет  "))
        try {
            RpReplySafety.clean("а".repeat(25000))
            fail("Oversized reply must remain retryable, not silently truncated")
        } catch (_: IllegalArgumentException) {}
        try {
            RpReplySafety.clean(" \n\t ")
            fail("Blank replies must be rejected")
        } catch (_: IllegalArgumentException) {
            // A failed request keeps the user turn available for retry.
        }
    }
}
