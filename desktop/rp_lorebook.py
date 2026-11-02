"""Player-authored conditional lorebook, shared with Android Shadow RP."""
import re

MAX_TEXT = 8000
MAX_ENTRIES = 40
MAX_CONTEXT = 1600


def parse_lorebook(raw):
    if not isinstance(raw, str) or len(raw) > MAX_TEXT:
        raise ValueError("Лорбук больше 8000 символов")
    entries, seen = [], set()
    for line in raw.splitlines():
        if not line.strip():
            continue
        if "=>" not in line:
            raise ValueError("Формат записи: ключ => факт")
        keyword, fact = (part.strip() for part in line.split("=>", 1))
        if (not 2 <= len(keyword) <= 60 or not 1 <= len(fact) <= 300
                or any(ord(ch) < 32 or ord(ch) == 127 for ch in keyword + fact)):
            raise ValueError("Некорректная запись лорбука")
        folded = keyword.casefold()
        if folded in seen:
            raise ValueError("Повторяющийся ключ лорбука")
        seen.add(folded)
        entries.append((keyword, fact))
        if len(entries) > MAX_ENTRIES:
            raise ValueError("В лорбуке больше 40 записей")
    return entries


def resolve_lorebook(raw, messages):
    entries = parse_lorebook(raw)
    recent = " ".join(str(msg.get("content", ""))[-2000:]
                      for msg in messages[-8:]
                      if isinstance(msg, dict) and msg.get("role") in ("user", "assistant"))[-4000:]
    if not recent.strip():
        return ""
    lines, remaining = [], MAX_CONTEXT
    for keyword, fact in entries:
        pattern = r"(?<!\w)" + re.escape(keyword) + r"(?!\w)"
        if not re.search(pattern, recent, re.IGNORECASE):
            continue
        line = f"{keyword}: {fact}"
        if len(line) + 1 > remaining:
            continue
        lines.append(line)
        remaining -= len(line) + 1
    return "\n".join(lines)
