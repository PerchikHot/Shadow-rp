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
