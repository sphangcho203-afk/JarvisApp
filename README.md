# Jarvis Android

Jarvis is a phone-first Android AI assistant written in Kotlin. It combines a live sci-fi command HUD, direct foreground voice interaction, encrypted local configuration, cloud-model reasoning, device actions, memory, timers, and expandable automation modules.

## Current Version

**Phase 8.2: Cloud Cortex**  
Version: `0.8.2-cloud-cortex`

## Architecture

- **Voice input:** Android speech recognition
- **Device actions:** Android intents for apps, web, timers, and settings
- **Memory:** local operator memory on the phone
- **AI reasoning:** configurable HTTPS cloud API
- **API secret storage:** Android Keystore encryption
- **Local server brain:** removed
- **Termux pairing:** removed from the assistant command path

Jarvis will never route a normal question to `localhost`, `127.0.0.1`, or a Termux bridge. Cloud requests require an HTTPS OpenAI-compatible chat-completions endpoint configured inside the app.

## First Launch

Jarvis opens **Cloud Cortex Setup** when no cloud provider is configured. Enter:

1. The provider's HTTPS chat-completions endpoint
2. The provider model ID
3. The API key
4. An optional system prompt

Tap **Save and Test Connection**. The key is encrypted on the device and is not committed to GitHub or embedded in the APK.

## Voice Commands

Examples:

- `configure API`
- `open YouTube`
- `Spotify`
- `search for Android Kotlin voice assistant`
- `remember that favorite game is MLBB`
- `call me Seongja`
- `who am I`
- `what do you remember`
- `clear memory`
- `set a timer for five minutes`
- `timer status`
- normal questions for the configured cloud model

## Touch Controls

- **Tap:** recalibrate the Android speech recognizer
- **Long press:** disabled

## Voice Scope

Direct listening runs while the Jarvis activity is visible. Recognition stops when the app leaves the foreground. A future background listener must use a visible Android foreground service, persistent notification, and user-controlled kill switch.

## Build APK With GitHub Actions

1. Upload the project to the root of the GitHub repository.
2. Open **Actions → Build Jarvis APK**.
3. Run the workflow or push a commit.
4. Download `Jarvis-debug-apk` from the successful run.

## Security Boundary

- No API key is included in source code, Git history, or the APK.
- API configuration is encrypted with Android Keystore.
- Only HTTPS cloud endpoints are accepted.
- Localhost endpoints are rejected.
- Device actions remain limited to visible Android intents.
- Jarvis does not claim an action succeeded unless Android confirms it.
