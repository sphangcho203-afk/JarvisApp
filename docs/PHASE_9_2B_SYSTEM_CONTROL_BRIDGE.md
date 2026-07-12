# Phase 9.2B — System Control Bridge

## Mission

Give Jarvis practical control over protected phone toggles without pretending an ordinary Android app has system privileges. The bridge performs the same visible Quick Settings tile action the operator could perform, after one explicit setup.

## One-Time Setup

1. Say `enable system control`.
2. In Android Accessibility settings, enable **Jarvis System Control**.
3. Place the controls Jarvis should use on the first Quick Settings pages.
4. Return to Jarvis and say `system control status`.

The service is configured to receive Android SystemUI events only. It does not subscribe to normal application windows.

## Supported Allow-Listed Tiles

- Wi-Fi / WLAN
- mobile data
- hotspot / personal hotspot
- airplane mode / flight mode
- Bluetooth
- location / GPS
- Eye Comfort / Night Light / Night Shield / blue-light filter
- dark mode
- Extra Dim
- Do Not Disturb
- battery saver / power saving
- NFC

Flashlight, media, volume, brightness, and auto-rotate remain handled by direct Android APIs rather than the accessibility bridge.

## Execution Flow

```text
Speech
  → local command normalization
  → explicit SystemToggle allow-list
  → open Android Quick Settings
  → locate matching SystemUI tile
  → read visible state when available
  → click only when a change is needed
  → verify, retry, or report unverified execution
  → close Quick Settings
  → HUD + spoken result
```

## Safety Boundary

- no arbitrary coordinates or unrestricted click commands
- no reading messages, passwords, notifications, or ordinary app content
- no typing inside other apps
- no lock-screen bypass
- no payment, purchase, or account-confirmation automation
- no root, hidden APIs, or privilege escalation
- queue and command timeouts are bounded

## Practical Limits

The phone must be unlocked. A requested tile must be present and exposed to accessibility on one of the first Quick Settings pages. Android vendors may rename or redesign tiles, and some versions do not expose a reliable on/off state. In that case Jarvis reports `EXECUTED_UNVERIFIED` rather than fabricating success.
