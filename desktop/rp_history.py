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
    # Optional speaker filters work in the existing Windows search field.
    # "role:player маяк" searches only player lines, while
    # "role:character маяк" searches only the character's responses.
    needle = query.strip().casefold()
    role_filter = None
    aliases = {
        "role:player": "user", "role:user": "user",
        "role:игрок": "user", "role:character": "assistant",
        "role:assistant": "assistant", "role:персонаж": "assistant",
    }
    first, separator, remainder = needle.partition(" ")
    if first in aliases:
        role_filter = aliases[first]
        needle = remainder.strip() if separator else ""
    elif first.startswith("role:"):
        raise ValueError("Неизвестный фильтр участника")
    results = []
    for index in range(len(messages) - 1, -1, -1):
        message = messages[index]
        if not isinstance(message, dict):
            continue
        role, content = message.get("role"), message.get("content")
        if role not in ("user", "assistant") or not isinstance(content, str):
            continue
        if role_filter is not None and role != role_filter:
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

def branch_from_user_turn(data: dict, character: str, turn_index: int):
    """Create a separate playable timeline; never truncate the original chat.

    Only the selected user's turn and its predecessors are copied. Pinned
    memories can mention events later in the original timeline, so the branch
    deliberately starts with empty pinned memory; the original is untouched.
    """
    clean = validate_backup(data)
    if character not in clean["characters"]:
        raise ValueError("Персонаж не найден")
    messages = clean["characters"][character]["messages"]
    if (type(turn_index) is not int or not 0 <= turn_index < len(messages)
            or messages[turn_index]["role"] != "user"):
        raise ValueError("Выбери реплику игрока для новой ветки")
    if turn_index == len(messages) - 1:
        raise ValueError("Для новой ветки выбери более раннюю реплику")
    occupied = {name.casefold() for name in clean["characters"]}
    new_name = None
    for number in range(2, 202):
        suffix = f" (ветка {number})"
        candidate = character[:100 - len(suffix)].rstrip() + suffix
        if candidate.casefold() not in occupied:
            new_name = candidate
            break
    if new_name is None:
        raise ValueError("Не удалось выбрать имя новой ветки")
    source = clean["characters"][character]
    copy = {**source, "memory": "", "messages": [dict(m) for m in messages[:turn_index + 1]]}
    clean["characters"][new_name] = copy
    encode_backup(clean)
    return clean, new_name
