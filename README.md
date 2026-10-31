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

After installing the JAR into a compatible Forge 1.20.1 instance, the
**experimental first chapter** can be played as follows:

1. Run `/shadowrp start` and choose `/shadowrp faction shadow` or
   `/shadowrp faction light`.
2. Defeat three hostile mobs, then use `/shadowrp guide` to summon the
   persistent Garden Keeper and `/shadowrp oath` to take the faction oath.
3. Bring the Keeper **8 wheat and 4 oak logs** and right-click to deliver.
4. For the Garden of Shadows, defeat **5 skeletons**; for the Garden of
   Light, defeat **5 zombies**. Return to the Keeper for the Defender rank.

**Sneak + right-click** the Garden Keeper to view the current objective without handing over items or claiming a reward. This is a read-only alternative to the command.\n\n`/shadowrp journal` displays the next objective, live inventory counts for
the supply quest, and scouting progress. `/shadowrp status` gives a short
summary. Progress is stored server-side per player and copied on respawn;
rewards are intended to be one-time. After the oath, faction switching is
blocked. Newly summoned Keepers are faction-aligned: the Garden of Shadows and Garden of Light have separately named villagers. A player cannot turn in quests to the opposite faction's Keeper; `/shadowrp locate` searches their own faction. Existing unaligned Keepers from older builds remain usable for save compatibility. The guide is a persistent named villager, **not** a custom NPC model.

**Important limitations:** GitHub Actions compiles the Forge JAR, but the
chapter has not been playtested in a running Minecraft client or on a
multiplayer server. New dimensions, faction bases, custom character models
and full story chapters are not yet implemented. The Android APK is a
separate companion, not an Android build of the Forge mod.
