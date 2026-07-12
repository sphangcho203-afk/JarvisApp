# Changelog

## 0.9.0 — Ten-Node Cortex Mesh

- Replaced the single cloud profile with six Gemini slots and four Groq slots.
- Added a secure ten-node provider registry encrypted with Android Keystore.
- Locked provider traffic to official HTTPS Gemini and Groq OpenAI-compatible endpoints.
- Added request classification for fast, general, reasoning, and coding tasks.
- Added mathematically weighted provider selection.
- Added Bayesian reliability scoring, latency utility, freshness balancing, and failure-streak penalties.
- Added automatic failover across healthy configured nodes.
- Added cooldown handling for HTTP 429, server failures, timeouts, and network errors.
- Added automatic node disablement for authentication failures.
- Added per-node and full-mesh connection testing.
- Added voice-accessible cortex status diagnostics.
- Updated the HUD and boot sequence for Phase 9 distributed-cortex operation.
- Removed the superseded single-provider cloud client and configuration store.

## 0.8.2 — Cloud Cortex

- Removed the local server brain and localhost inference path.
- Removed Termux pairing and bridge setup from normal assistant operation.
- Added an HTTPS cloud-model client.
- Added secure in-app endpoint, model, and API-key configuration.
- Added Android Keystore-backed AES-GCM secret storage.
- Added cloud connection testing and visible API errors.
- Disabled cleartext networking and rejected local endpoints.
- Preserved Android-native app launching, web navigation, timers, and memory controls.
- Disabled full-screen long-press actions.
- Included the Phase 8.1 speech-recognition compatibility repair.

## 0.8.0 — Neural Interface

- Rebuilt the main HUD into a responsive live command interface.
- Added microphone-reactive visual energy.
- Added a natural-language countdown controller.
- Added live device telemetry.
- Added lightweight interface tones.
- Added screen-awake behavior during active use.
