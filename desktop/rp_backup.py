"""Validated Shadow RP chat backup format. Never import executable content."""
import json

MAX_BYTES = 8 * 1024 * 1024
MAX_CHARACTERS = 200
MAX_MESSAGES = 10000


def validate_backup(data):
    if not isinstance(data, dict) or not isinstance(data.get("characters"), dict):
        raise ValueError("Неверный формат архива Shadow RP")
    result = {}
    for name, card in data["characters"].items():
        if not isinstance(name, str) or not name.strip() or len(name) > 100:
            raise ValueError("Некорректное имя персонажа")
        if not isinstance(card, dict):
            raise ValueError("Некорректная карточка персонажа")
        messages = card.get("messages", [])
        if not isinstance(messages, list) or len(messages) > MAX_MESSAGES:
            raise ValueError("Слишком длинная история")
        clean = []
        for msg in messages:
            if (not isinstance(msg, dict) or msg.get("role") not in ("user", "assistant")
                    or not isinstance(msg.get("content"), str)
                    or len(msg["content"]) > 20000):
                raise ValueError("Некорректное сообщение")
            clean.append({"role": msg["role"], "content": msg["content"]})
        entry = {"messages": clean}
        for field in ("description", "scenario", "memory", "appearance", "greeting", "protocol"):
            value = card.get(field, "")
            if not isinstance(value, str) or len(value) > 30000:
                raise ValueError("Некорректное поле " + field)
            if field in ("description", "scenario", "memory") or field in card:
                entry[field] = value
        result[name] = entry
        if len(result) > MAX_CHARACTERS:
            raise ValueError("Слишком много персонажей")
    return {"characters": result}


def decode_backup(raw):
    if len(raw) > MAX_BYTES:
        raise ValueError("Архив больше 8 МБ")
    return validate_backup(json.loads(raw.decode("utf-8")))


def encode_backup(data):
    raw = json.dumps(validate_backup(data), ensure_ascii=False, indent=2).encode("utf-8")
    if len(raw) > MAX_BYTES:
        raise ValueError("Архив больше 8 МБ")
    return raw


def merge_backups(existing, incoming):
    """Keep unrelated characters, replace colliding names only after UI confirmation.

    Validate the *merged* archive before writing, including the 8 MiB limit.
    No data is mutated in place if the incoming archive is invalid.
    """
    old = validate_backup(existing)["characters"]
    new = validate_backup(incoming)["characters"]
    merged = {"characters": {**old, **new}}
    encode_backup(merged)
    return merged
