"""Bounded, redirect-safe OpenAI-compatible chat transport for Shadow RP.

Retries only transient HTTP statuses and connection failures. Never logs API
keys or raw server error bodies. A model response is capped before JSON decode.
"""
from __future__ import annotations

import json
import math
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from rp_context import validate_endpoint

MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_REQUEST_BYTES = 2 * 1024 * 1024
# Temporary upstream failures should not end a long-running RP scene.
TRANSIENT_HTTP = {408, 429, 500, 502, 503, 504, 521, 522, 524}


def _extract_text(result: dict) -> str:
    """Accept plain text or standard OpenAI-compatible typed text parts."""
    # Some OpenAI-compatible endpoints return HTTP 200 with malformed
    # choices. Treat those as retryable user-facing errors, not uncaught
    # AttributeError/TypeError in the background worker.
    choices = result.get("choices")
    if (not isinstance(choices, list) or not choices or
            not isinstance(choices[0], dict)):
        raise ValueError("Неверный формат ответа модели")
    choice = choices[0]
    message = choice.get("message")
    if not isinstance(message, dict) or "content" not in message:
        raise ValueError("Неверный формат ответа модели")
    content = message["content"]
    # A provider may return HTTP 200 with a cut-off or filtered answer.
    # Never append a partial scene as though the character finished speaking.
    finish_reason = choice.get("finish_reason")
    if finish_reason not in (None, "stop"):
        raise ValueError("Модель не завершила ответ; сократи контекст или проверь настройки")
    message = choice.get("message")
    if (not isinstance(message, dict) or
            message.get("role", "assistant") != "assistant" or
            message.get("tool_calls") is not None or
            message.get("function_call") is not None or
            message.get("refusal") is not None):
        raise ValueError("Модель вернула не текстовую реплику персонажа")
    if isinstance(content, str):
        answer = content
    elif isinstance(content, list) and 0 < len(content) <= 100:
        parts = []
        for part in content:
            if (not isinstance(part, dict) or part.get("type") != "text"
                    or not isinstance(part.get("text"), str)):
                raise ValueError("Неподдерживаемый формат ответа модели")
            parts.append(part["text"])
        answer = "\n".join(parts)
    else:
        raise ValueError("Неподдерживаемый формат ответа модели")
    if not answer.strip():
        raise ValueError("Модель вернула пустой ответ")
    # Silently cutting a long response in the middle of a sentence corrupts
    # RP continuity and persists an invented stopping point. Leave the last
    # player turn available for retry instead of saving a partial reply.
    if len(answer) > 20000:
        raise ValueError("Ответ модели слишком длинный; сократи контекст или лимит ответа")
    return answer


def _retry_after_delay(header: str, attempt: int) -> float:
    """Honor delta-seconds and RFC 7231 dates, capped for interactive RP."""
    fallback = float(attempt + 1)
    if not header:
        return fallback
    try:
        value = float(header)
        if math.isfinite(value):
            return min(3.0, max(0.0, value))
    except (ValueError, OverflowError):
        pass
    try:
        date = parsedate_to_datetime(header)
        if date.tzinfo is None:
            return fallback
        return min(3.0, max(0.0, (date - datetime.now(timezone.utc)).total_seconds()))
    except (ValueError, TypeError, OverflowError, IndexError):
        return fallback


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        # Never forward Authorization to an untrusted redirected host.
        return None


def chat_completion(base: str, api_key: str, model: str, messages: list[dict],
                    *, opener=None, sleep=None, attempts: int = 3) -> str:
    if not 1 <= attempts <= 3:
        raise ValueError("Invalid retry count")
    if not isinstance(model, str) or not model.strip() or len(model) > 120:
        raise ValueError("Укажи корректное имя модели")
    if (not isinstance(api_key, str) or len(api_key) > 8192 or
            any(ord(char) < 32 or ord(char) == 127 for char in api_key)):
        raise ValueError("Некорректный API-ключ")
    # Enforce endpoint safety even if a new caller bypasses the UI wrapper.
    url = validate_endpoint(base) + "/chat/completions"
    if not isinstance(messages, list) or len(messages) > 80:
        raise ValueError("Некорректный контекст диалога")
    # Never forward imported arbitrary objects or hidden tool instructions
    # as API messages. RP history contains only text from these three roles.
    cleaned_messages = []
    for index, turn in enumerate(messages):
        if (not isinstance(turn, dict) or
                turn.get("role") not in ("system", "user", "assistant") or
                not isinstance(turn.get("content"), str) or
                not turn["content"].strip() or
                (turn["role"] == "system" and index != 0)):
            raise ValueError("Некорректный контекст диалога")
        # Imported Android/Windows backups can include timestamps and local
        # UI metadata. Send only the OpenAI-compatible wire fields; never
        # forward arbitrary imported keys to the model endpoint.
        cleaned_messages.append({"role": turn["role"], "content": turn["content"]})
    payload = json.dumps({"model": model.strip(), "messages": cleaned_messages},
                         ensure_ascii=False).encode("utf-8")
    if len(payload) > MAX_REQUEST_BYTES:
        raise ValueError("Контекст диалога превышает 2 МБ")
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = "Bearer " + api_key
    client = opener if opener is not None else build_opener(NoRedirect())
    wait = sleep if sleep is not None else time.sleep
    for attempt in range(attempts):
        request = Request(url, data=payload, headers=headers, method="POST")
        try:
            with client.open(request, timeout=45) as response:
                raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                raise ValueError("Ответ модели превышает 2 МБ")
            try:
                result = json.loads(raw)
            except (ValueError, UnicodeError) as exc:
                raise ValueError("Неверный JSON ответа модели") from exc
            if not isinstance(result, dict):
                raise ValueError("Неверный формат ответа модели")
            return _extract_text(result)
        except HTTPError as exc:
            if exc.code not in TRANSIENT_HTTP or attempt + 1 == attempts:
                raise ValueError(f"Ошибка API: HTTP {exc.code}") from None
            # Respect Retry-After but cap delays so the UI stays responsive.
            header = exc.headers.get("Retry-After", "") if exc.headers else ""
            wait(_retry_after_delay(header, attempt))
        except (URLError, TimeoutError, ConnectionError) as exc:
            if attempt + 1 == attempts:
                raise ValueError("Нет соединения с моделью; проверь адрес и сеть") from None
            wait(float(attempt + 1))
    raise RuntimeError("Unreachable retry state")
