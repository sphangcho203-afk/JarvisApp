# Jarvis Android

Jarvis is a phone-first Android AI assistant written in Kotlin. It combines a live sci-fi command HUD, direct foreground voice interaction, encrypted cloud-model configuration, device actions, memory, timers, and a health-aware multi-provider reasoning mesh.

## Current Version

**Phase 9.0: Ten-Node Cortex Mesh**
Version: `0.9.0-cortex-mesh`

## Cortex Mesh

The single cloud endpoint has been replaced by ten encrypted provider slots:

- 6 Gemini project profiles
- 4 Groq project profiles
- fixed official HTTPS OpenAI-compatible endpoints
- model ID and API key per node
- Android Keystore AES-GCM encryption
- per-node connection tests
- automatic cooldown for rate limits and temporary failures
- automatic failover to the next eligible node
- health metrics for latency, successes, failures, and status

Jarvis does not call every provider for every command. It classifies the request, scores eligible nodes, selects one, and uses another only when necessary.

## Routing Mathematics

Each node receives a bounded utility score using:

- task suitability
- Bayesian reliability estimate
- exponential latency utility
- node freshness for balanced use
- configured priority
- recent failure-streak penalty

This gives deterministic, inspectable routing instead of blind key rotation.

## First Launch

Jarvis opens **Cortex Mesh Setup** when no node is configured.

For each node you want to use:

1. Enter a model ID currently available in that provider console.
2. Enter the matching API key.
3. Leave the node enabled.
4. Tap **Save + Test** for that node, or test every configured node together.

Model availability changes over time, so model IDs are intentionally not hardcoded into the APK.

## Voice Commands

Examples:

- `configure APIs`
- `cortex status`
- `open YouTube`
- `Spotify`
- `search for Android Kotlin voice assistant`
- `remember that favorite game is MLBB`
- `what do you remember`
- `set a timer for five minutes`
- normal questions routed through the cortex mesh

## Local and Cloud Boundaries

Local Android commands such as opening apps, memory controls, and timers do not consume cloud requests. General questions, coding, and reasoning requests enter the cloud mesh.

Localhost, Termux brain endpoints, cleartext HTTP, and pairing-code routing remain removed.

## Security

- Keys are not stored in source code, GitHub, or the APK.
- The complete registry is encrypted with Android Keystore.
- Only the fixed official HTTPS Gemini and Groq gateways are used.
- Authentication failures automatically disable the affected node.
- Rate-limited nodes enter cooldown instead of being hammered repeatedly.
- Use only API projects and credentials you own and operate within each provider's terms.

## Build APK With GitHub Actions

1. Apply the Phase 9 patch to the repository.
2. Push the generated commit to `main`.
3. Open **Actions → Build Jarvis APK**.
4. Download `Jarvis-debug-apk` from the successful run.
