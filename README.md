# Shadow RP

Shadow RP — AI roleplay chat for Android and Windows. **Not a Minecraft mod.**

## Android
The Android app is in `app/`. Its APK is built by
[Android workflow](.github/workflows/android.yml).

## Windows RP chat (new)
The actual desktop chat client is `desktop/rp_chat.py`, built into
`ShadowRP.exe` by [Windows AI Chat workflow](.github/workflows/rp-windows.yml).

Features:
- Character cards, editable scenario, manually pinned memory and per-character chat history.
- Local JSON history in `%APPDATA%\\ShadowRP\\chats.json`.
- Optional OpenAI-compatible chat-completions API (including localhost).
- Model, API base URL and key supplied in the UI; API key is not stored.
- Network errors do not delete user messages; HTTP redirects are blocked for API keys.\n- Only the last 30 dialogue messages are sent, alongside the persistent character, scenario and pinned memory.\n- Pinned memory is edited by the user; no unverified personal facts are generated or saved automatically.

Run from source: `python desktop/rp_chat.py`.
An AI endpoint must be configured to receive AI responses; no model is bundled.
The Windows workflow must pass before the EXE can be treated as a verified build.

## Separation from Shadow Garden
Shadow Garden is a separate Minecraft Forge 1.20.1 project maintained locally
on the owner's laptop, not in this GitHub repository.

The old `forge/` directory and `desktop/shadow_launcher.py` are unrelated
legacy files, retained temporarily to avoid destructive deletion without backup.
Their workflows have been disabled. They are **not** Shadow RP deliverables.
