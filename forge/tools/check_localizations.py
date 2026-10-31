"""Fail the Forge build when a story/NPC message lacks a translation.

Both bundled languages must expose identical keys and compatible formatting
arguments. This catches broken quest prompts before shipping the JAR.
"""
import json
import re
from pathlib import Path

LANG_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/assets/shadowrp/lang"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?s")


def check() -> None:
    languages = {}
    for name in ("en_us", "ru_ru"):
        path = LANG_DIR / f"{name}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or not data:
            raise ValueError(f"{name}: empty or malformed translations")
        if any(not isinstance(k, str) or not isinstance(v, str) or not v.strip()
               for k, v in data.items()):
            raise ValueError(f"{name}: blank or invalid translation")
        languages[name] = data

    en, ru = languages["en_us"], languages["ru_ru"]
    if en.keys() != ru.keys():
        raise ValueError(
            f"Missing Russian keys: {sorted(en.keys() - ru.keys())}; "
            f"missing English keys: {sorted(ru.keys() - en.keys())}"
        )
    for key in en:
        if len(PLACEHOLDER.findall(en[key])) != len(PLACEHOLDER.findall(ru[key])):
            raise ValueError(f"Placeholder mismatch: {key}")
    print(f"Validated {len(en)} Shadow RP quest and NPC messages in both languages")


if __name__ == "__main__":
    check()
