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

## Transfer characters and stories between Android and Windows

Both clients use the same **Shadow RP JSON** archive:
`{"characters":{"Name":{"description":"...","scenario":"...","memory":"...","appearance":"...","greeting":"...","protocol":"...","messages":[{"role":"user","content":"..."}]}}}`.

- **Android:** Profile → Export characters and stories / Import characters and stories.
  The system file picker is used; no storage permission or cloud upload is needed.
  Import shows the number of matching character names and asks before overwriting
  those cards and chat histories. Other Android characters are retained.
- **Windows:** Export chats / Import chats. Windows import asks for confirmation
  and merges characters by name. Matching cards and their histories are replaced
  only after confirmation; unrelated Windows characters are kept. Export first.
  Windows preserves appearance, greeting and protocol fields when transferring
  the archive back to Android, even though its editor currently exposes only
  description, scenario and pinned memory.
- Import limits: 8 MiB, 200 characters, 10,000 messages per character,
  20,000 characters per message. Only user/assistant messages are portable.
  API keys, GGUF models, avatars, device settings and credentials are never
  included. This is **manual transfer**, not live synchronization.
- Android backup codec regression tests run in the APK workflow before build.

## Separation from Shadow Garden
Shadow Garden is a separate Minecraft Forge 1.20.1 project maintained locally
on the owner's laptop, not in this GitHub repository.

The old `forge/` directory and `desktop/shadow_launcher.py` are unrelated
legacy files, retained temporarily to avoid destructive deletion without backup.
Their workflows have been disabled. They are **not** Shadow RP deliverables.
