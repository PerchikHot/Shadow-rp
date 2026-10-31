package com.shadowrp.app

import java.net.URI
import java.net.URL

/** No credentials in URL, no API key sent to an unencrypted remote server. */
internal object RpRemotePolicy {
    fun endpoint(base: String, apiKey: String): URL {
        val uri = try { URI(base.trim()) }
            catch (_: Exception) { throw IllegalArgumentException("Неверный URL модели") }
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
        return URL(base.trim().trimEnd('/') + "/chat/completions")
    }

    fun shouldRetryStatus(status: Int, attempt: Int): Boolean =
        attempt in 0..1 && status in setOf(429, 502, 503, 504)
}
