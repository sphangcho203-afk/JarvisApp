# Jarvis Phase 2 Upgrade

Phase 2 turns the starter shell into a more useful local assistant core.

## Added

- Persistent callsign memory using SharedPreferences.
- Command counter and last-command telemetry.
- Battery diagnostics.
- Expanded app launcher aliases.
- Web routing commands.
- Improved HUD with mode/signal/command telemetry.
- More stable voice result handling.
- Phase 2 docs and version bump to 0.2.0.

## Commands

Try saying:

- `Jarvis wake up`
- `system status`
- `battery status`
- `open YouTube`
- `open Spotify`
- `open Discord`
- `search for Android Kotlin voice assistant`
- `set callsign to Seongja`
- `who am I`
- `mission brief`
- `sleep mode`

## Security boundary

This version does not silently control private data, send messages, or perform destructive phone actions. Anything like secret database access, fingerprint authentication, device lock behavior, or phone-computer sync must be added behind explicit permission checks and audit logs.
