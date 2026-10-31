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
                      messages: list[dict], limit: int = 30) -> list[dict]:
    """Keep persona, scenario and pinned memories while bounding chat history."""
    if not 1 <= limit <= 60:
        raise ValueError("Invalid history limit")
    system = (
        "You are a fictional roleplay character. Remain in character. "
        "Respect the established scene and continuity. Do not invent past "
        "meetings or personal facts about the player.\n"
        "CHARACTER:\n" + str(description)[:4000] + "\n"
        "SCENARIO:\n" + str(scenario)[:4000] + "\n"
        "USER-PINNED MEMORY (facts explicitly provided by the player):\n"
        + str(memory)[:4000]
    )
    history = []
    for item in messages[-limit:]:
        if not isinstance(item, dict):
            continue
        role, content = item.get("role"), item.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content.strip():
            history.append({"role": role, "content": content[:10000]})
    return [{"role": "system", "content": system}, *history]
