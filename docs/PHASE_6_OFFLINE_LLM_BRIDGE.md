# Phase 6 — Offline LLM Bridge

Phase 6 connects the Android Jarvis interface to a local `llama-server` process at `127.0.0.1:8080`.

## Pipeline

1. Voice/text input enters the Android app.
2. The app sends an OpenAI-compatible chat request to local llama.cpp.
3. llama.cpp runs Qwen2.5 3B Q4_K_M entirely on-device.
4. The response is constrained to JSON.
5. Android validates any tool request against a small allowlist.
6. Memory facts are saved in local SharedPreferences.
7. If the server is unavailable, Phase 5's local rule engine remains active.

## Termux commands

```bash
jarvis-brain-start
jarvis-brain-status
jarvis-brain-test
jarvis-brain-stop
```

## Current safe tools

- Open allowlisted apps
- Open Android Settings
- Save a memory fact
- Update operator identity
- Change visual mode

No external API, API key, or remote endpoint is configured.
