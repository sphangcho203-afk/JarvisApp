# Jarvis Android

Jarvis is a phone-first Android AI assistant written in Kotlin. It combines a live sci-fi command HUD, direct foreground voice interaction, encrypted cloud-model configuration, deterministic Android actions, memory, timers, and a health-aware multi-provider reasoning mesh.

## Current Version

**Phase 9.2B: System Control Bridge**
Version: `0.9.2b-system-control`

## What Phase 9.2B Adds

- adds a one-time, user-enabled **Jarvis System Control** service limited to Android Quick Settings
- executes allow-listed Wi-Fi, mobile-data, hotspot, flight-mode, Bluetooth, location, Eye Comfort, dark-mode, Extra Dim, Do Not Disturb, battery-saver, and NFC tiles
- checks a visible tile state before pressing it and verifies the result when SystemUI exposes state information
- searches the first Quick Settings pages with bounded retries and reports genuine failure or unverified execution
- keeps direct Android APIs for flashlight, volume, brightness, rotation, media, telemetry, and app launching

## Phase 9.2A Foundation

- handles known phone actions locally before sending anything to the cloud

- understands natural phrases such as `can you open Instagram for me` before cloud routing
- returns typed, verified Android action states to the HUD
- discovers launchable apps dynamically with aliases and typo-tolerant matching
- answers time and date locally
- paginates long command-stream responses instead of permanently truncating them
- reads live battery state from Android's sticky battery broadcast
- reads validated network state and transport type
- controls the flashlight through the public camera torch API
- controls media playback and media volume
- controls brightness and auto-rotate after Android grants one-time system-settings permission
- opens official Android panels for protected connectivity controls
- searches Spotify and YouTube directly
- limits cloud failover to three nodes and a bounded request budget
- recovers from speech-recognizer stalls, cloud timeouts, and stuck speech output
- lets a tap cancel an active request and reset the voice array

## Cortex Mesh

The ten encrypted provider slots remain available:

- 6 Gemini project profiles
- 4 Groq project profiles
- fixed official HTTPS OpenAI-compatible endpoints
- model ID and API key per node
- Android Keystore AES-GCM encryption
- per-node connection tests
- automatic cooldown and failover

Jarvis does not call every provider for every command. Device actions stay local. General questions enter the cloud mesh only after the Android execution kernel declines the request.

## Voice Commands

Examples:

- `flashlight on`
- `flashlight off`
- `set media volume to 40 percent`
- `volume up`
- `pause music`
- `next track`
- `brightness to 60 percent`
- `auto rotate on`
- `battery status`
- `network status`
- `enable system control`
- `system control status`
- `turn on Wi-Fi`
- `turn off mobile data`
- `turn on hotspot`
- `turn off flight mode`
- `turn on Eye Comfort`
- `turn on Do Not Disturb`
- `turn on dark mode`
- `Bluetooth settings`
- `play Notion on Spotify`
- `search Jarvis interface on YouTube`
- `open YouTube`
- `remember that favorite game is MLBB`
- `set a timer for five minutes`
- `cortex status`

## Android Boundaries

Stock Android blocks ordinary apps from directly changing several protected radios. Phase 9.2B works through the same visible Quick Settings controls the operator can press, after the operator enables **Jarvis System Control** once in Accessibility settings. The bridge is restricted to Android SystemUI and an explicit allow-list of tiles.

The phone must be unlocked, and the requested tile should be placed on one of the first Quick Settings pages. Vendor labels and layouts can differ, so Jarvis reports when a tile cannot be found or when Android does not expose a verifiable final state. Lock-screen bypasses, payment approvals, account authentication, arbitrary taps inside apps, and hidden root behavior are outside this bridge.

Brightness and auto-rotate still use the one-time **Modify system settings** permission. Foreground voice listening remains active only while Jarvis is visible.

## Security

- API keys are not stored in source code, GitHub, or the APK.
- The complete cortex registry is encrypted with Android Keystore.
- Only the fixed official HTTPS Gemini and Groq gateways are used.
- Localhost and Termux brain routing remain removed.
- Android actions are executed by deterministic code, not by trusting free-form model text.

## Build APK With GitHub Actions

1. Apply the latest Phase 9.2B installer to the repository.
2. Push the generated commit to `main`.
3. Open **Actions → Build Jarvis APK**.
4. Download `Jarvis-debug-apk` from the successful run.
