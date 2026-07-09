# Jarvis Security Notes

Jarvis should be powerful, but not reckless. We are building an assistant, not a cyberpunk liability dispenser.

## Rules

1. Never store secrets in plain text.
2. Never expose private notes without authentication.
3. Keep dangerous actions behind confirmation.
4. Log sensitive commands.
5. Prefer local-first processing where possible.
6. Ask permission before accessing contacts, messages, files, or notifications.

## Sensitive Features To Guard

- Private database lookup
- Device lock/unlock commands
- Computer sync
- Contacts and messages
- Location
- Microphone background behavior

## Android Reality Check

Android does not allow normal apps to constantly read fingerprints from arbitrary screen touches. Fingerprint authentication must use official Android biometric APIs. Any design involving fingerprints must be built around system-approved biometric prompts, not fantasy spy-movie sensors. Tragic, but laws of software still exist.
