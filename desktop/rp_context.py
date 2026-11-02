"""Pure RP context and endpoint rules shared by the desktop client and tests.

Pinned memory is user-authored. We never silently invent or summarize facts
about the player; long chats are bounded to avoid runaway token costs.
"""
from urllib.parse import urlsplit


def validate_endpoint(endpoint: str) -> str:
    if not isinstance(endpoint, str):
        raise ValueError("API URL must be text")
    value = endpoint.strip().rstrip("/")
    # urllib silently strips CR/LF/TAB in some Python versions. Reject them
    # before parsing so a pasted endpoint cannot change its actual host/path.
    if (not value or len(value) > 2048 or
            any(ord(char) <= 32 or ord(char) == 127 for char in value)):
        raise ValueError("API URL contains whitespace or control characters")
    parsed = urlsplit(value)
    # Do not allow encoded path separators or dot-segment traversal. A proxy
    # may normalize these differently from the local client.
    if (any(segment in (".", "..") for segment in parsed.path.split("/")) or
            "%2f" in parsed.path.lower() or "%5c" in parsed.path.lower() or
            "%2e" in parsed.path.lower() or
            "\\" in parsed.path):
        raise ValueError("Unsafe API path")
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
    for item in messages:
        if not isinstance(item, dict):
            continue
        role, content = item.get("role"), item.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content.strip():
            candidates.append({"role": role, "content": content[-10000:]})
    history = []
    remaining = history_char_budget
    for turn in reversed(candidates[-limit:]):
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
    # A budget or turn-count cutoff can otherwise start with a character
    # reply while silently omitting the player's question immediately before
    # it. Drop only that orphaned oldest reply; never drop the latest turn.
    # Saved history remains untouched and the next model call stays coherent.
    if len(history) > 1 and history[0]["role"] == "assistant":
        preceding = len(candidates) - len(history) - 1
        if preceding >= 0 and candidates[preceding]["role"] == "user":
            history.pop(0)
    return [{"role": "system", "content": system}, *history]
