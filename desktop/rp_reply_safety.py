"""Reject stale desktop AI replies after edits, imports or history changes.

The Android client performs the same snapshot check before saving a reply.
"""
CARD_FIELDS = ("description", "scenario", "memory", "appearance", "greeting", "protocol", "lorebook")


def card_context(card: dict) -> tuple:
    return tuple(card.get(field, "") for field in CARD_FIELDS)


def can_apply_reply(snapshot: list[dict], current: list[dict] | None,
                    expected_context: tuple, actual_context: tuple) -> bool:
    return (bool(snapshot) and snapshot[-1].get("role") == "user"
            and current == snapshot and actual_context == expected_context)
