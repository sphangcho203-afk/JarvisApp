# Phase 8 — Neural Command Interface

Phase 8 upgrades the phone interface from a mostly decorative HUD into a live Android control surface.

## Interface Systems

- Hardware-accelerated full-screen Canvas HUD
- Responsive geometric layout for portrait phones
- Perspective command grid
- Hexagonal civilization lattice
- Orbital cortex rings and rotating scanner sweep
- Live microphone energy array
- Mode-dependent reactor colors
- Boot sequence with staged subsystem progress
- Command transcript, response dock, trace bus, event rail, and telemetry panels

## Live Telemetry

The HUD displays values obtained at runtime rather than invented statistics:

- Local device time
- Battery percentage
- Active connection type
- App heap usage
- Voice recognition state
- Microphone RMS level
- Command count
- Countdown state and remaining time

## Countdown Engine

The timer uses `SystemClock.elapsedRealtime()` so normal wall-clock changes do not corrupt the countdown.

Supported examples:

- `set a timer for five minutes`
- `start a countdown for 30 seconds`
- `set timer for one hour and 30 minutes`
- `timer status`
- `cancel timer`

Maximum duration: 24 hours.

## Voice Loop Improvements

- Direct listening begins automatically when permission is granted.
- TTS temporarily pauses recognition to reduce feedback.
- Recognition resumes automatically after Jarvis finishes speaking.
- Intentional recognizer cancellation no longer appears as an error state.
- Actual `onRmsChanged` input drives the HUD waveform.

## Performance Decisions

The neural core uses layered translucent geometry instead of software blur. This keeps Android hardware acceleration enabled and reduces avoidable heat, frame drops, and battery drain.

## Current Boundary

The activity listens only while visible. Background microphone operation is deliberately not hidden inside this phase. That feature should be implemented as a user-controlled foreground service with a persistent notification and a clear stop control.
