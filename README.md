# Shadow RP

Android roleplay AI app — development in progress.


## Windows companion preview

The separate `Shadow RP Windows Companion` GitHub Actions workflow builds a
portable Windows EXE artifact. It can inspect a selected local `.minecraft`
folder, list existing JAR mods, detect a Forge 1.20.1 version directory, and
open the installed Minecraft Launcher if the Windows URI handler is available.
It does **not** install mods, contain the Shadow RP Forge mod, synchronize
Android chats, or modify Minecraft files. Those features remain unfinished.

Local development: `python desktop/shadow_launcher.py`.
Tests: `python -m unittest discover -s desktop -p "test_*.py"`.


## Forge 1.20.1 — first story milestone (experimental)

The `forge/` directory now contains a **separate Minecraft Forge 1.20.1 mod**,
not the Android companion or Windows EXE. Its dedicated Actions workflow
compiles a JAR with Java 17 and Forge 47.3.0. Do not install the JAR until the
Forge CI build passes. In-game compatibility and multiplayer testing are still
required.

After installing the JAR into a compatible Forge 1.20.1 instance, use
`/shadowrp start`, then `/shadowrp faction shadow` or
`/shadowrp faction light`. Defeat three hostile mobs to finish the first
trial and receive 50 experience points. Progress is saved server-side per
player and copied on respawn. `/shadowrp status` shows progress. NPCs,
dimensions and later quests are **not yet implemented**.
