"""Bounded, redirect-safe OpenAI-compatible chat transport for Shadow RP.

Retries only transient HTTP statuses and connection failures. Never logs API
keys or raw server error bodies. A model response is capped before JSON decode.
"""
from __future__ import annotations

import json
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

MAX_RESPONSE_BYTES = 2 * 1024 * 1024
TRANSIENT_HTTP = {429, 502, 503, 504}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        # Never forward Authorization to an untrusted redirected host.
        return None


def chat_completion(base: str, api_key: str, model: str, messages: list[dict],
                    *, opener=None, sleep=None, attempts: int = 3) -> str:
    if not 1 <= attempts <= 3:
        raise ValueError("Invalid retry count")
    if not model.strip() or len(model) > 120:
        raise ValueError("Укажи корректное имя модели")
    url = base.rstrip("/") + "/chat/completions"
    payload = json.dumps({"model": model.strip(), "messages": messages},
                         ensure_ascii=False).encode("utf-8")
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
                answer = result["choices"][0]["message"]["content"]
            except (ValueError, KeyError, IndexError, TypeError) as exc:
                raise ValueError("Неверный формат ответа модели") from exc
            if not isinstance(answer, str) or not answer.strip():
                raise ValueError("Модель вернула пустой ответ")
            return answer[:20000]
        except HTTPError as exc:
            if exc.code not in TRANSIENT_HTTP or attempt + 1 == attempts:
                raise ValueError(f"Ошибка API: HTTP {exc.code}") from None
            # Respect Retry-After but cap delays so the UI stays responsive.
            header = exc.headers.get("Retry-After", "") if exc.headers else ""
            try:
                delay = min(3.0, max(0.0, float(header)))
            except ValueError:
                delay = float(attempt + 1)
            wait(delay)
        except (URLError, TimeoutError, ConnectionError) as exc:
            if attempt + 1 == attempts:
                raise ValueError("Нет соединения с моделью; проверь адрес и сеть") from None
            wait(float(attempt + 1))
    raise RuntimeError("Unreachable retry state")
