# Shadow RP

Shadow RP is an AI-powered roleplay chat application, **not** a Minecraft mod.

## Android
The Android application is built from the `app/` module using the
[Build Android APK](.github/workflows/android.yml) workflow.

## Windows
The previous Windows companion preview inspected Minecraft installations and
was unrelated to Shadow RP's intended roleplay chat experience. It must not be
presented as a Shadow RP desktop client. A proper Windows RP chat client and
its build pipeline are still pending.

## Project boundaries
Shadow Garden is a separate Minecraft Forge project developed locally on the
owner's laptop. Its files and builds do not belong in this repository.

## Current limitations
The existing `forge/` and `desktop/` directories are legacy, unrelated
work and are not part of the Shadow RP product. They are retained temporarily
to avoid destructive removal before the user has a separate backup.
