"""Search and safely rewind RP dialogue without altering other character cards.

Rewinding keeps the selected player message so a new reply can be generated.
The desktop UI creates a separately importable checkpoint before saving.
"""
from __future__ import annotations

from rp_backup import validate_backup, encode_backup


def search_dialogue(messages: list[dict], query: str, limit: int = 50):
    """Return recent matches as (zero-based index, role, bounded preview)."""
    if not isinstance(messages, list) or not isinstance(query, str):
        raise ValueError("Некорректный запрос поиска")
    if not 1 <= limit <= 100 or len(query) > 200:
        raise ValueError("Слишком длинный запрос поиска")
    needle = query.strip().casefold()
    results = []
    for index in range(len(messages) - 1, -1, -1):
        message = messages[index]
        if not isinstance(message, dict):
            continue
        role, content = message.get("role"), message.get("content")
        if role not in ("user", "assistant") or not isinstance(content, str):
            continue
        if needle and needle not in content.casefold():
            continue
        preview = " ".join(content.split())[:160]
        results.append((index, role, preview))
        if len(results) >= limit:
            break
    return results


def rewind_to_user_turn(data: dict, character: str, turn_index: int):
    """Return a validated copy with later turns removed, never mutate input."""
    clean = validate_backup(data)
    if character not in clean["characters"]:
        raise ValueError("Персонаж не найден")
    messages = clean["characters"][character]["messages"]
    if (type(turn_index) is not int or not 0 <= turn_index < len(messages)
            or messages[turn_index]["role"] != "user"):
        raise ValueError("Выбери реплику игрока для отката")
    if turn_index == len(messages) - 1:
        raise ValueError("После этой реплики ещё нет ответа для отката")
    clean["characters"][character]["messages"] = messages[:turn_index + 1]
    encode_backup(clean)
    return clean
