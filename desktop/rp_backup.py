"""Validated Shadow RP chat backup format. Never import executable content."""
import json

MAX_BYTES = 8 * 1024 * 1024
MAX_CHARACTERS = 200
MAX_MESSAGES = 10000


def validate_backup(data):
    if not isinstance(data, dict) or not isinstance(data.get("characters"), dict):
        raise ValueError("Неверный формат архива Shadow RP")
    result = {}
    # Validate even standalone exports: Android rejects case-only duplicate
    # cards, so Windows must never create an archive it cannot import.
    _reject_ambiguous_import_names(data["characters"])
    for name, card in data["characters"].items():
        if (not isinstance(name, str) or not name.strip() or len(name) > 100
                or any(ord(char) < 32 or ord(char) == 127 for char in name)):
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


def _reject_ambiguous_import_names(characters):
    """Reject case-only duplicates inside one imported archive.

    They would otherwise create visually indistinguishable chats on Android
    and Windows, or make a later case-insensitive merge silently ambiguous.
    Existing local archives are not modified by this check.
    """
    seen = set()
    for name in characters:
        key = name.casefold()
        if key in seen:
            raise ValueError("Архив содержит персонажей с одинаковыми именами без учёта регистра")
        seen.add(key)


def merge_backups(existing, incoming):
    """Keep unrelated characters, replace colliding names only after UI confirmation.

    Validate the *merged* archive before writing, including the 8 MiB limit.
    No data is mutated in place if the incoming archive is invalid.
    """
    old = validate_backup(existing)["characters"]
    new = validate_backup(incoming)["characters"]
    _reject_ambiguous_import_names(new)
    # A name differing only in case must not create a second, visually
    # indistinguishable character. Use "import as copies" instead.
    existing_names = {name.casefold(): name for name in old}
    for name in new:
        previous = existing_names.get(name.casefold())
        if previous is not None and previous != name:
            raise ValueError("Имя персонажа отличается только регистром; импортируй как копию")
    merged = {"characters": {**old, **new}}
    encode_backup(merged)
    return merged


def export_character(data, name: str) -> bytes:
    """Portable one-character archive for Android/Windows transfer.

    Do not include unrelated characters, even if their private histories
    would fit in the archive. The ordinary importer merges this safely.
    """
    clean = validate_backup(data)
    if not isinstance(name, str) or name not in clean["characters"]:
        raise ValueError("Выбери существующего персонажа для экспорта")
    return encode_backup({"characters": {name: clean["characters"][name]}})


def merge_backups_as_copies(existing, incoming):
    """Import portable characters without overwriting an existing chat.

    Colliding names get deterministic "(импорт N)" suffixes. Reserve all
    incoming names before renaming so a generated suffix cannot shadow a
    different imported character. Validate the final 8 MiB archive first.
    """
    old = validate_backup(existing)["characters"]
    new = validate_backup(incoming)["characters"]
    _reject_ambiguous_import_names(new)
    result = dict(old)
    occupied = {name.casefold() for name in old}
    reserved = occupied | {name.casefold() for name in new}
    for name, card in new.items():
        if name.casefold() not in occupied:
            result[name] = card
            occupied.add(name.casefold())
            continue
        for index in range(2, MAX_CHARACTERS + 2):
            suffix = f" (импорт {index})"
            candidate = name[:100 - len(suffix)].rstrip() + suffix
            if candidate.casefold() not in reserved:
                result[candidate] = card
                occupied.add(candidate.casefold())
                reserved.add(candidate.casefold())
                break
        else:
            raise ValueError("Нет свободного имени для импортированного персонажа")
    merged = {"characters": result}
    encode_backup(merged)
    return merged
