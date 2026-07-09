# Jarvis Security Notes

Jarvis should stay local-first by default.

## Rules

- Do not commit API keys.
- Do not commit GitHub tokens.
- Do not store private secrets as plain text long-term.
- Ask permission before enabling always-listening behavior.
- Keep memory local unless the user explicitly chooses sync.
- Use Android permissions only when needed.

## Current Memory

Phase 3 memory uses Android SharedPreferences. This is fine for harmless preferences and labels, but not for sensitive secrets.

For Phase 4, sensitive memory should move to Android Keystore-backed encryption.
