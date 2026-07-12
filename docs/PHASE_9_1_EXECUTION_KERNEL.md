# Phase 9.1 — Android Execution Kernel Recovery

## Mission

Phase 9.1 separates reasoning from execution. Cloud models answer questions and help with planning. Android code performs phone actions through explicit, testable APIs.

## Execution Order

1. speech is converted to text
2. countdown and configuration commands are checked
3. the deterministic Android action router is checked
4. only unresolved requests enter the Cortex Mesh
5. Android reports actual execution results to the HUD and speech layer

This prevents a cloud model from claiming that it changed a phone setting when no Android action occurred.

## Direct Android Actions

- flashlight on, off, and toggle
- media play, pause, next, previous, and stop
- media volume up, down, mute, unmute, and exact percentage
- screen brightness after one-time system-settings permission
- auto-rotate after one-time system-settings permission
- app launching
- web search
- Spotify search
- YouTube search
- live battery status
- live network status
- timers and existing local memory commands

## Protected Controls

Android reserves some controls for the user or privileged system apps. Jarvis opens the smallest official control panel for:

- Wi-Fi and mobile data
- Bluetooth
- location
- airplane mode
- hotspot and tethering
- NFC
- Do Not Disturb
- battery saver

The result message explicitly says when a tap or confirmation is still required.

## Telemetry Repair

Battery percentage is calculated from the current sticky `ACTION_BATTERY_CHANGED` level and scale values. This matches Android's live system battery state more reliably across vendor implementations. Network status is read from the active network and its validated capabilities.

## Stall Recovery

- speech-start watchdog: 7 seconds
- speech-result watchdog: 7 seconds
- cloud request watchdog: 40 seconds
- cloud routing budget: 36 seconds
- maximum provider attempts: 3
- speech-output watchdog: 25 seconds
- tap during processing: cancel request and hard-reset recognition
- late cloud responses: discarded by request generation ID

## Honest Capability Contract

Jarvis must not say a device action succeeded unless the Android execution kernel returned success. Unsupported or protected actions are reported as unavailable or confirmation-required.
