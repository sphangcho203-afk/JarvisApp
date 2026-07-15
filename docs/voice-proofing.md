# Jarvis Voice Proofing Contract

Version 0.9.15 separates recognizer readiness from proven microphone activity.

## Required runtime behavior

1. Android recognizer readiness keeps the HUD in `READY`.
2. The HUD enters `LISTENING` only after one of these occurs:
   - `onBeginningOfSpeech`
   - meaningful RMS energy
   - a non-empty partial transcript
3. A dedicated on-device recognizer that produces no activity within its startup window is discarded.
4. Android's normal speech service is tried after a silent or unsupported dedicated recognizer.
5. API configuration remains reachable without speech through:
   - automatic first-launch setup
   - core tap while Cortex is unconfigured
   - the visible `API SETUP` button
   - typed command `configure APIs`
6. The shipped APK must contain the typed command field and API setup control.

Hardware recognition is accepted only after an on-device phrase produces visible speech activity and a transcript. A successful build alone does not prove microphone capture.
