"""Player-approved, verbatim RP scene memory for cross-platform cards.

No automatic summaries or inferred personal facts: only a completed exchange
the player explicitly chooses to pin becomes persistent character memory.
"""
from __future__ import annotations

MAX_PINNED_MEMORY = 4000
MAX_QUOTE = 500


def _quote(text: str) -> str:
    """Indent continuation lines so a quote cannot spoof a speaker header."""
    # Unicode line separators also render as new lines in many chat views.
    # Strip bidi formatting controls that could visually reverse speaker tags.
    sanitized = (text.strip()[:MAX_QUOTE].replace("\r\n", "\n")
                 .replace("\r", "\n").replace("\u0085", "\n")
                 .replace("\u2028", "\n").replace("\u2029", "\n"))
    sanitized = sanitized.translate({code: None for code in
        (*range(0x202A, 0x202F), *range(0x2066, 0x206A))})
    return sanitized.replace("\n", "\n│ ")


def pin_last_exchange(memory: str, messages: list[dict]) -> str:
    if not isinstance(memory, str) or len(memory) > MAX_PINNED_MEMORY:
        raise ValueError("Память слишком длинная; отредактируй её вручную.")
    if not isinstance(messages, list) or len(messages) < 2:
        raise ValueError("Для закрепления нужна завершённая сцена из двух реплик.")
    user, assistant = messages[-2:]
    if (not isinstance(user, dict) or not isinstance(assistant, dict)
            or user.get("role") != "user" or assistant.get("role") != "assistant"
            or not isinstance(user.get("content"), str)
            or not isinstance(assistant.get("content"), str)
            or not user["content"].strip() or not assistant["content"].strip()):
        raise ValueError("Последняя сцена ещё не завершена ответом персонажа.")
    note = (
        "Сцена, подтверждённая игроком (дословные фрагменты):\n"
        "Игрок: " + _quote(user["content"]) + "\n"
        "Персонаж: " + _quote(assistant["content"])
    )
    if note in memory:
        return memory
    updated = memory.rstrip() + ("\n\n" if memory.strip() else "") + note
    if len(updated) > MAX_PINNED_MEMORY:
        raise ValueError("Память заполнена. Сократи старые записи перед добавлением сцены.")
    return updated
