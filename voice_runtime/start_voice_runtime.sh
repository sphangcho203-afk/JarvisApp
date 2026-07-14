#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${JARVIS_VOICE_VENV:-$ROOT_DIR/.venv-voice}"
ASSETS_DIR="${JARVIS_ASSETS_DIR:-$ROOT_DIR/voice_runtime/assets}"

cd "$ROOT_DIR"
mkdir -p "$ASSETS_DIR"

if [[ ! -x "$VENV_DIR/bin/python" ]]; then
  python -m venv "$VENV_DIR"
fi

"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install -r "$ROOT_DIR/voice_runtime/requirements.txt"

if [[ ! -f "$ASSETS_DIR/activation.wav" || ! -f "$ASSETS_DIR/deactivation.wav" ]]; then
  "$VENV_DIR/bin/python" -m voice_runtime.generate_sfx "$ASSETS_DIR"
fi

export JARVIS_ASSETS_DIR="$ASSETS_DIR"
export JARVIS_VOICE_HOST="127.0.0.1"
export JARVIS_VOICE_PORT="8766"
export JARVIS_TTS_PROVIDER="${JARVIS_TTS_PROVIDER:-openai}"
export OPENAI_TTS_MODEL="${OPENAI_TTS_MODEL:-gpt-4o-mini-tts}"
export OPENAI_TTS_VOICE="${OPENAI_TTS_VOICE:-onyx}"
export OPENAI_TRANSCRIBE_MODEL="${OPENAI_TRANSCRIBE_MODEL:-gpt-4o-mini-transcribe}"
export ELEVENLABS_MODEL_ID="${ELEVENLABS_MODEL_ID:-eleven_flash_v2_5}"

exec "$VENV_DIR/bin/python" -m voice_runtime.jarvis_voice_runtime
