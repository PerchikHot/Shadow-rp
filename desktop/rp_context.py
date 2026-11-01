"""Pure RP context and endpoint rules shared by the desktop client and tests.

Pinned memory is user-authored. We never silently invent or summarize facts
about the player; long chats are bounded to avoid runaway token costs.
"""
from urllib.parse import urlsplit


def validate_endpoint(endpoint: str) -> str:
    value = endpoint.strip().rstrip("/")
    parsed = urlsplit(value)
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("API URL must not contain credentials, query or fragment")
    if not parsed.hostname or not parsed.netloc:
        raise ValueError("Invalid API endpoint")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("Invalid API port") from exc
    local = parsed.hostname.lower() in {"localhost", "127.0.0.1", "::1"}
    if parsed.scheme == "https":
        pass
    elif parsed.scheme == "http" and local and port is not None:
        pass
    else:
        raise ValueError("Use HTTPS or an explicit local HTTP port")
    if not parsed.path or parsed.path == "/":
        return value
    if parsed.path.endswith("/chat/completions"):
        raise ValueError("Enter the API base URL, not /chat/completions")
    return value


def build_rp_messages(description: str, scenario: str, memory: str,
                      messages: list[dict], limit: int = 30, *,
                      appearance: str = "", greeting: str = "",
                      protocol: str = "",
                      history_char_budget: int = 24000) -> list[dict]:
    """Build bounded RP context, preserving all six portable character-card fields.

    These fields are exported by Android; Windows must not silently ignore them.
    Pinned memory is explicitly user-authored, never generated or inferred.
    """
    if not 1 <= limit <= 60:
        raise ValueError("Invalid history limit")
    if type(history_char_budget) is not int or not 1000 <= history_char_budget <= 120000:
        raise ValueError("Invalid history character budget")
    system = (
        "You are a fictional roleplay character. Remain in character. "
        "Respect the established scene and continuity. Do not invent past "
        "meetings or personal facts about the player.\n"
        "CHARACTER:\n" + str(description)[:4000] + "\n"
        "APPEARANCE:\n" + str(appearance)[:4000] + "\n"
        "SCENARIO:\n" + str(scenario)[:4000] + "\n"
        "INITIAL GREETING:\n" + str(greeting)[:4000] + "\n"
        "CHARACTER RP RULES:\n" + str(protocol)[:4000] + "\n"
        "USER-PINNED MEMORY (facts explicitly provided by the player):\n"
        + str(memory)[:4000]
    )
    # Prefer the newest complete turns instead of sending up to 60 huge
    # messages. This bounds long-running roleplay costs on both local and
    # remote OpenAI-compatible models without modifying saved transcripts.
    candidates = []
    for item in messages[-limit:]:
        if not isinstance(item, dict):
            continue
        role, content = item.get("role"), item.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content.strip():
            candidates.append({"role": role, "content": content[:10000]})
    history = []
    remaining = history_char_budget
    for turn in reversed(candidates):
        content = turn["content"]
        if len(content) > remaining:
            if history:
                break  # Never send an incomplete older exchange.
            content = content[-remaining:]  # Always retain the newest turn.
        history.append({"role": turn["role"], "content": content})
        remaining -= len(content)
        if remaining == 0:
            break
    history.reverse()
    return [{"role": "system", "content": system}, *history]
