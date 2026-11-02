package com.shadowrp.app

import org.junit.Assert.*
import org.junit.Test

class RpRemotePolicyTest {
    @Test fun requiresEncryptedRemoteConnectionsForApiKeys() {
        assertEquals("https://example.org/v1/chat/completions",
            RpRemotePolicy.endpoint("https://example.org/v1", "secret").toString())
        assertEquals("http://127.0.0.1:11434/v1/chat/completions",
            RpRemotePolicy.endpoint("http://127.0.0.1:11434/v1", "secret").toString())
        assertEquals("http://192.168.1.5:1234/v1/chat/completions",
            RpRemotePolicy.endpoint("http://192.168.1.5:1234/v1", "").toString())
        for (url in listOf(
            "http://192.168.1.5:1234/v1", "http://example.org/v1",
            "https://user:password@example.org/v1", "https://example.org/v1?key=secret",
            "https://example.org/v1#token", "file:///tmp/model",
            "https://example.org/v1/chat/completions"
        )) {
            try {
                RpRemotePolicy.endpoint(url, "secret")
                fail("Should reject: $url")
            } catch (_: IllegalArgumentException) {}
        }
    }


    @Test fun excludesOrphanedOldestReplyFromAndroidModelContext() {
        val dialogue = listOf(
            "user" to "Old question", "assistant" to "Old answer",
            "user" to "New question", "assistant" to "New answer")
        assertEquals(dialogue.takeLast(2),
            RpRemotePolicy.boundedHistory(dialogue, maxMessages = 3))
        assertEquals(dialogue.takeLast(1),
            RpRemotePolicy.boundedHistory(dialogue, maxMessages = 1))
        assertEquals(4, dialogue.size)
    }

    @Test fun importedCharacterOnlyPrefixDoesNotPolluteAndroidContext() {
        val dialogue = listOf(
            "assistant" to "Older greeting",
            "assistant" to "Old character-only line",
            "user" to "Start this scene",
            "assistant" to "Scene answer")
        assertEquals(dialogue.takeLast(2),
            RpRemotePolicy.boundedHistory(dialogue, maxMessages = 3))
        assertEquals(dialogue.subList(1, 2),
            RpRemotePolicy.boundedHistory(dialogue.take(2), maxMessages = 2))
        assertEquals(4, dialogue.size)
    }

    @Test fun rejectsUnsafeEndpointPathsWithoutBreakingLocalLanModels() {
        assertEquals("http://192.168.1.5:1234/v1/chat/completions",
            RpRemotePolicy.endpoint("http://192.168.1.5:1234/v1", "").toString())
        for (value in listOf(
            "https://api.example.org/v1/../admin",
            "https://api.example.org/v1/%2fadmin",
            "https://api.example.org/v1/%5cadmin", "https://api.example.org/v1/%2e%2e/admin",
            "https://api.example.org/v1/.%2e/admin",
            "https://api.example.org/v1" + 10.toChar() + "/chat",
            "https://api.example.org/v1/chat completions"
        )) {
            try {
                RpRemotePolicy.endpoint(value, "secret")
                fail("Should reject unsafe URL")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun boundsLongSingleAndGroupChatsWithoutChangingHistory() {
        val history = (1..50).map { index ->
            (if (index % 2 == 0) "assistant" else "user") to ("turn$index:" + "a".repeat(1600))
        }
        val before = history.toList()
        val compact = RpRemotePolicy.boundedHistory(history)
        assertEquals(before, history)
        assertTrue(compact.size <= 30)
        assertTrue(compact.sumOf { it.second.length } <= 24000)
        assertEquals(history.last().first, compact.last().first)
        assertEquals(history.last().second, compact.last().second)
        assertEquals("user", RpRemotePolicy.boundedHistory(
            listOf("system" to "secret", "user" to "hello")).single().first)
        assertEquals(1000, RpRemotePolicy.boundedHistory(
            listOf("user" to "x".repeat(20000)), charBudget = 1000
        ).single().second.length)
        assertTrue(RpRemotePolicy.boundedHistory(
            listOf("assistant" to " ", "system" to "ignore")).isEmpty())
    }

    @Test fun keepsNewestPartOfLongTurnsAndSkipsInvalidRoleSpam() {
        val newest = "old".repeat(5000) + "LATEST-SCENE"
        val noisy = listOf("user" to "earlier context") +
            List(40) { "system" to "ignored" } +
            listOf("assistant" to newest)
        val compact = RpRemotePolicy.boundedHistory(noisy, maxMessages = 2)
        assertEquals(2, compact.size)
        assertEquals("earlier context", compact.first().second)
        assertTrue(compact.last().second.endsWith("LATEST-SCENE"))
        assertEquals(10000, compact.last().second.length)
        assertEquals("user", compact.first().first)
    }

    @Test fun retryAfterIsBoundedAndNeverBlocksTheUiIndefinitely() {
        assertEquals(3000L, RpRemotePolicy.retryDelayMillis("600", 0))
        assertEquals(2000L, RpRemotePolicy.retryDelayMillis("2", 0))
        assertEquals(0L, RpRemotePolicy.retryDelayMillis("0", 1))
        assertEquals(500L, RpRemotePolicy.retryDelayMillis("invalid", 0))
        assertEquals(1000L, RpRemotePolicy.retryDelayMillis(null, 1))
        assertEquals(1500L, RpRemotePolicy.retryDelayMillis("-1", 2))
    }

    @Test fun retriesOnlyTemporaryServerErrors() {
        for (code in listOf(408, 429, 500, 502, 503, 504, 521, 522, 524)) {
            assertTrue(RpRemotePolicy.shouldRetryStatus(code, 0))
            assertTrue(RpRemotePolicy.shouldRetryStatus(code, 1))
            assertFalse(RpRemotePolicy.shouldRetryStatus(code, 2))
        }
        for (code in listOf(301, 400, 401, 403, 404)) {
            assertFalse(RpRemotePolicy.shouldRetryStatus(code, 0))
        }
    }
}
