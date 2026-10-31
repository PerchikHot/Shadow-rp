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

    @Test fun retriesOnlyTemporaryServerErrors() {
        for (code in listOf(429, 502, 503, 504)) {
            assertTrue(RpRemotePolicy.shouldRetryStatus(code, 0))
            assertTrue(RpRemotePolicy.shouldRetryStatus(code, 1))
            assertFalse(RpRemotePolicy.shouldRetryStatus(code, 2))
        }
        for (code in listOf(301, 400, 401, 403, 404, 500)) {
            assertFalse(RpRemotePolicy.shouldRetryStatus(code, 0))
        }
    }
}
