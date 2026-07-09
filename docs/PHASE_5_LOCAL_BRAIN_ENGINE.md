# Phase 5 — Local Brain Engine

Phase 5 upgrades Jarvis from basic phrase handling into a structured local cognition engine.

## New brain modules

- `InputNormalizer` — cleans language input.
- `IntentDetector` — detects meaning categories.
- `EntityExtractor` — extracts targets, names, modes, memory payloads, and search queries.
- `MemoryVault` — stores identity, profile, facts, and context locally.
- `ContextEngine` — tracks recent intent and continuity.
- `DecisionEngine` — decides response, confidence, mode, and action.
- `ActionRouter` — executes safe Android intents.
- `KnowledgeKernel` — provides limited offline answers and brain self-explanation.
- `LocalBrainEngine` — orchestration layer.

## Design upgrade

The HUD now displays:

- intent matrix
- confidence
- entities
- thought stream
- trace bus
- decision label
- local cortex core
- direct listening state

## Important

This is not omniscient. It is the local brain. Phase 6 can add cloud cortex for broad knowledge, live web research, and deeper reasoning without storing API keys inside the APK.
