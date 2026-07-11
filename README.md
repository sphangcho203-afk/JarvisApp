# Jarvis Android

Jarvis is a phone-first Android AI assistant interface written in Kotlin. It combines a live sci-fi command HUD, direct voice interaction, local memory, device actions, a Termux bridge, and expandable automation modules.

## Current Version

**Phase 8: Neural Command Interface**  
Version: `0.8.0-neural-interface`

## What Phase 8 Adds

- Hands-free speech recognition while the Jarvis screen is open
- No hold-to-speak control
- Microphone-driven waveform and reactor response
- Real countdown timer with natural voice commands
- Live clock, battery, network, heap, command, voice, and timer telemetry
- Responsive geometric HUD with orbital rings, scanner sweep, perspective grid, event rail, and boot sequence
- Lightweight boot, processing, success, and timer-completion sounds
- Cleaner listener state handling when Jarvis pauses for TTS
- Screen-awake mode while the interface is active
- Long-press access to the secure Termux bridge console

## Voice Commands

Examples:

- `open YouTube`
- `open Spotify`
- `search for Android Kotlin voice assistant`
- `battery status`
- `system status`
- `remember that favorite game is MLBB`
- `call me Seongja`
- `who am I`
- `what do you remember`
- `clear memory`
- `set a timer for five minutes`
- `start a countdown for one minute and thirty seconds`
- `timer status`
- `cancel countdown`
- `pair code 123456`

## Touch Controls

Touch is optional and is not used for speaking.

- **Tap:** recalibrate the Android speech recognizer
- **Long press:** open the secure bridge setup console

## Voice Scope

Direct listening runs while the Jarvis activity is visible. The app intentionally stops recognition when it leaves the foreground. A true background listener requires an Android microphone foreground service, a persistent notification, and an explicit user-controlled safety switch.

## Build APK With GitHub Actions

1. Upload the contents of this folder to the root of a GitHub repository.
2. Open **Actions → Build Jarvis APK**.
3. Choose **Run workflow**.
4. Download the artifact named `Jarvis-debug-apk`.

The workflow uses JDK 17, Android SDK setup, Gradle 8.10.2, and `assembleDebug`.

## Security Boundary

- No API keys are included.
- The Termux cortex communicates through `127.0.0.1:8765`.
- Pairing uses a one-time six-digit code and a saved random token.
- Device actions remain limited to visible Android intents and allowlisted routes.
- Sensitive future memory should use Android Keystore-backed encryption rather than ordinary SharedPreferences.

Jarvis is becoming an assistant, not a permission-shaped wrecking ball. Keep each new capability explicit, logged, and reversible.
