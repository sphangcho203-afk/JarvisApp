# Phase 8.2 — Cloud Cortex

Phase 8.2 removes local inference servers and Termux pairing from Jarvis's normal command path.

## Request Flow

1. Android speech recognition produces text.
2. Deterministic Android controls handle app launches, web navigation, timers, and memory commands.
3. All remaining conversational requests go to the configured HTTPS cloud endpoint.
4. No localhost fallback is attempted when a cloud request fails.

## Configuration

The setup screen stores the endpoint, model ID, API key, and system prompt. Values are encrypted with an AES-GCM key generated inside Android Keystore.

## Endpoint Rules

- HTTPS is required.
- `localhost`, `127.0.0.1`, `0.0.0.0`, and `::1` are rejected.
- The endpoint must return an OpenAI-compatible chat-completions response.

## Failure Behavior

When the cloud API is missing or unavailable, Jarvis reports the actual error. It does not show pairing, call Termux, or silently fall back to a fake local intelligence layer.
