package com.shadowrp.app

import java.net.URI
import java.net.URL

/** No credentials in URL, no API key sent to an unencrypted remote server. */
internal object RpRemotePolicy {
    fun endpoint(base: String, apiKey: String): URL {
        val normalized = base.trim().trimEnd('/')
        require(normalized.isNotEmpty() && normalized.length <= 2048 &&
            normalized.none { it.isWhitespace() || it.code < 32 || it.code == 127 }) {
            "URL модели содержит недопустимые символы"
        }
        val uri = try { URI(normalized) }
            catch (_: Exception) { throw IllegalArgumentException("Неверный URL модели") }
        val rawPath = uri.rawPath.orEmpty()
        require(rawPath.split('/').none { it == "." || it == ".." } &&
            !rawPath.contains("%2f", ignoreCase = true) &&
            !rawPath.contains("%5c", ignoreCase = true) &&
            !rawPath.contains("%2e", ignoreCase = true) &&
            !rawPath.contains('\\')) { "Небезопасный путь API" }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        require(scheme == "https" || scheme == "http") { "Используй HTTPS или HTTP локальной модели" }
        require(!host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null) {
            "URL модели не должен содержать логин, ключ, параметры или фрагмент"
        }
        val loopback = host in setOf("localhost", "127.0.0.1", "::1")
        require(apiKey.isBlank() || scheme == "https" || loopback) {
            "API-ключ нельзя передавать по незашифрованному HTTP вне устройства"
        }
        require(!uri.path.orEmpty().endsWith("/chat/completions")) {
            "Укажи базовый адрес API, без /chat/completions"
        }
        return URL(normalized + "/chat/completions")
    }

    /** Retain the newest complete RP turns under a predictable context budget.
     * The saved transcript is never truncated; only the API request is bounded.
     * Invalid roles and empty messages are excluded from the model context.
     */
    fun boundedHistory(history: List<Pair<String, String>>,
                       maxMessages: Int = 30, charBudget: Int = 24000
    ): List<Pair<String, String>> {
        require(maxMessages in 1..60 && charBudget in 1000..120000)
        val selected = mutableListOf<Pair<String, String>>()
        var remaining = charBudget
        for ((role, raw) in history.asReversed().asSequence()
            .filter { it.first in setOf("user", "assistant") && it.second.isNotBlank() }
            .take(maxMessages)) {
            if (role !in setOf("user", "assistant") || raw.isBlank()) continue
            var content = raw.takeLast(10000)
            if (content.length > remaining) {
                if (selected.isNotEmpty()) break
                content = content.takeLast(remaining)
            }
            selected.add(role to content)
            remaining -= content.length
            if (remaining == 0) break
        }
        val result = selected.asReversed().toMutableList()
        // If the oldest retained reply lost its preceding player question
        // to the message/character budget, do not feed an orphaned answer
        // back to the model. Preserve a single newest turn as-is.
        if (result.size > 1 && result.first().first == "assistant") {
            val valid = history.filter { it.first in setOf("user", "assistant") &&
                it.second.isNotBlank() }
            val previous = valid.size - result.size - 1
            if (previous >= 0 && valid[previous].first == "user") {
                result.removeAt(0)
            }
        }
        return result
    }

    fun shouldRetryStatus(status: Int, attempt: Int): Boolean =
        attempt in 0..1 && status in setOf(408, 429, 500, 502, 503, 504, 521, 522, 524)

    /** Bound Retry-After so a busy provider cannot freeze a RP session.
     * Runs only on background worker threads, never on Android's UI thread.
     */
    fun retryDelayMillis(header: String?, attempt: Int): Long {
        val fallback = 500L * (attempt.coerceIn(0, 2) + 1)
        val seconds = header?.trim()?.toLongOrNull() ?: return fallback
        // Negative Retry-After is malformed, not a request to retry at once.
        if (seconds < 0L) return fallback
        return seconds.coerceAtMost(3L) * 1000L
    }
}
