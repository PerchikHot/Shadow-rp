"""Crash-resistant local Shadow RP persistence with validated recovery.

Only user-authored cards, scenes and dialogue are stored. API keys stay out
of the archive. Never replace a good backup with invalid primary data.
"""
from __future__ import annotations
import os
from pathlib import Path
from rp_backup import decode_backup, encode_backup, MAX_BYTES

DEFAULT = {"characters": {"Новый персонаж": {
    "description": "", "scenario": "", "memory": "", "messages": []}}}


def _read_valid(path: Path):
    with path.open("rb") as stream:
        raw = stream.read(MAX_BYTES + 1)
    return decode_backup(raw), raw


def load_data(path: Path):
    """Return (data, recovered_from_backup); never silently erase a bad file."""
    try:
        return _read_valid(path)[0], False
    except (OSError, ValueError, UnicodeError):
        pass
    try:
        return _read_valid(path.with_suffix(".bak"))[0], True
    except (OSError, ValueError, UnicodeError):
        return DEFAULT.copy() | {"characters": {
            name: card.copy() | {"messages": []}
            for name, card in DEFAULT["characters"].items()}}, False


def _atomic_write(path: Path, data: bytes):
    temp = path.with_name(path.name + ".tmp")
    try:
        with temp.open("wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        temp.replace(path)
    finally:
        try:
            temp.unlink(missing_ok=True)
        except OSError:
            pass


def save_data(path: Path, data):
    """Validate before touching disk; retain last known-good main as backup."""
    raw = encode_backup(data)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        _, previous = _read_valid(path)
    except (OSError, ValueError, UnicodeError):
        previous = None
    if previous is not None:
        _atomic_write(path.with_suffix(".bak"), previous)
    _atomic_write(path, raw)
