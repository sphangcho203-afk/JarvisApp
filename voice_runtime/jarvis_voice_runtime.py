from __future__ import annotations

import asyncio
import io
import json
import os
import shutil
import subprocess
import wave
from collections.abc import AsyncIterator
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Protocol

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from openai import AsyncOpenAI

JARVIS_SYSTEM_INSTRUCTIONS = """
You are JARVIS, Seongja's private owner-bound intelligence.

VOICE AND PRESENCE
Speak with decisive executive confidence, precise technical language, restrained
dry wit, and a controlled British command-system presence. Address Seongja as
Sir or Boss when natural. Lead with the answer. Keep spoken replies compact
unless depth is requested.

TRUTH DISCIPLINE
Sound certain when the evidence is certain. Never fabricate knowledge, device
state, research, tool output, or completed actions. Do not use filler phrases
such as "I think", "I believe", or "maybe" when a direct statement is justified.
When a material uncertainty remains, state the exact uncertainty once, then give
the strongest verified conclusion or next action.

COMPLEXITY
Treat difficult work as structured, solvable engineering. Do not sound
overwhelmed. Break the task into clean stages, execute approved tools, verify the
result, and report the outcome directly.

DIALOGUE
Do not become preachy because the operator uses profanity, harsh wording, odd
questions, or unusual hypotheticals. Focus on the objective. Avoid corporate
boilerplate, repetitive disclaimers, and unnecessary lectures.
""".strip()

TTSProviderName = Literal["openai", "elevenlabs"]


@dataclass(frozen=True)
class VoiceRuntimeConfig:
    tts_provider: TTSProviderName
    assets_dir: Path
    openai_api_key: str | None
    openai_tts_model: str
    openai_tts_voice: str
    openai_transcribe_model: str
    elevenlabs_api_key: str | None
    elevenlabs_voice_id: str | None
    elevenlabs_model_id: str
    input_sample_rate_hz: int
    output_sample_rate_hz: int
    max_input_seconds: int

    @classmethod
    def from_env(cls) -> "VoiceRuntimeConfig":
        provider = os.getenv("JARVIS_TTS_PROVIDER", "openai").strip().lower()
        if provider not in {"openai", "elevenlabs"}:
            raise ValueError("JARVIS_TTS_PROVIDER must be 'openai' or 'elevenlabs'.")
        return cls(
            tts_provider=provider,  # type: ignore[arg-type]
            assets_dir=Path(os.getenv("JARVIS_ASSETS_DIR", "/assets")).expanduser(),
            openai_api_key=os.getenv("OPENAI_API_KEY") or None,
            openai_tts_model=os.getenv("OPENAI_TTS_MODEL", "gpt-4o-mini-tts").strip(),
            openai_tts_voice=os.getenv("OPENAI_TTS_VOICE", "onyx").strip(),
            openai_transcribe_model=os.getenv(
                "OPENAI_TRANSCRIBE_MODEL", "gpt-4o-mini-transcribe"
            ).strip(),
            elevenlabs_api_key=os.getenv("ELEVENLABS_API_KEY") or None,
            elevenlabs_voice_id=os.getenv("ELEVENLABS_VOICE_ID") or None,
            elevenlabs_model_id=os.getenv(
                "ELEVENLABS_MODEL_ID", "eleven_flash_v2_5"
            ).strip(),
            input_sample_rate_hz=int(os.getenv("JARVIS_INPUT_SAMPLE_RATE", "16000")),
            output_sample_rate_hz=int(os.getenv("JARVIS_OUTPUT_SAMPLE_RATE", "24000")),
            max_input_seconds=int(os.getenv("JARVIS_MAX_INPUT_SECONDS", "45")),
        )


def play_system_sfx(
    event_type: Literal["activation", "deactivation"],
    *,
    assets_dir: Path | None = None,
    blocking: bool = False,
) -> subprocess.Popen[bytes] | subprocess.CompletedProcess[bytes]:
    base = assets_dir or Path(os.getenv("JARVIS_ASSETS_DIR", "/assets")).expanduser()
    filename = {
        "activation": "activation.wav",
        "deactivation": "deactivation.wav",
    }[event_type]
    path = (base / filename).resolve()
    if path.suffix.lower() != ".wav":
        raise ValueError("Jarvis interface sounds must be .wav files.")
    if not path.is_file():
        raise FileNotFoundError(f"Missing Jarvis sound effect: {path}")

    ffplay = shutil.which("ffplay")
    if ffplay:
        command = [
            ffplay,
            "-nodisp",
            "-autoexit",
            "-loglevel",
            "quiet",
            str(path),
        ]
    else:
        termux_player = shutil.which("termux-media-player")
        if not termux_player:
            raise RuntimeError("No audio player found. Install ffplay or Termux:API.")
        command = [termux_player, "play", str(path)]

    if blocking:
        return subprocess.run(
            command,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    return subprocess.Popen(
        command,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


class StreamingTTS(Protocol):
    provider_name: str
    content_type: str
    sample_rate_hz: int

    async def stream(self, text: str) -> AsyncIterator[bytes]:
        ...


class OpenAIStreamingTTS:
    provider_name = "openai"
    content_type = "audio/pcm"
    sample_rate_hz = 24_000

    def __init__(self, config: VoiceRuntimeConfig) -> None:
        if not config.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for OpenAI TTS.")
        self._client = AsyncOpenAI(api_key=config.openai_api_key)
        self._model = config.openai_tts_model
        self._voice = config.openai_tts_voice

    async def stream(self, text: str) -> AsyncIterator[bytes]:
        instructions = (
            "Speak in a deep, smooth, commanding British baritone. "
            "Use controlled pacing, crisp diction, restrained dry wit, "
            "and quiet executive confidence. Never sound theatrical, rushed, "
            "uncertain, breathless, or overly cheerful."
        )
        async with self._client.audio.speech.with_streaming_response.create(
            model=self._model,
            voice=self._voice,
            input=text,
            instructions=instructions,
            response_format="pcm",
        ) as response:
            async for chunk in response.iter_bytes(chunk_size=4096):
                if chunk:
                    yield chunk


class ElevenLabsStreamingTTS:
    provider_name = "elevenlabs"
    content_type = "audio/pcm"
    sample_rate_hz = 24_000

    def __init__(self, config: VoiceRuntimeConfig) -> None:
        if not config.elevenlabs_api_key:
            raise ValueError("ELEVENLABS_API_KEY is required for ElevenLabs TTS.")
        if not config.elevenlabs_voice_id:
            raise ValueError("ELEVENLABS_VOICE_ID is required for ElevenLabs TTS.")

        from elevenlabs import VoiceSettings
        from elevenlabs.client import ElevenLabs

        self._voice_settings_type = VoiceSettings
        self._client = ElevenLabs(api_key=config.elevenlabs_api_key)
        self._voice_id = config.elevenlabs_voice_id
        self._model_id = config.elevenlabs_model_id

    async def stream(self, text: str) -> AsyncIterator[bytes]:
        queue: asyncio.Queue[bytes | BaseException | None] = asyncio.Queue()
        loop = asyncio.get_running_loop()

        def producer() -> None:
            try:
                response = self._client.text_to_speech.stream(
                    voice_id=self._voice_id,
                    output_format="pcm_24000",
                    text=text,
                    model_id=self._model_id,
                    voice_settings=self._voice_settings_type(
                        stability=0.72,
                        similarity_boost=0.82,
                        style=0.18,
                        use_speaker_boost=True,
                        speed=0.94,
                    ),
                )
                for chunk in response:
                    if chunk:
                        asyncio.run_coroutine_threadsafe(queue.put(chunk), loop).result()
            except BaseException as error:
                asyncio.run_coroutine_threadsafe(queue.put(error), loop).result()
            finally:
                asyncio.run_coroutine_threadsafe(queue.put(None), loop).result()

        producer_task = asyncio.create_task(asyncio.to_thread(producer))
        try:
            while True:
                item = await queue.get()
                if item is None:
                    break
                if isinstance(item, BaseException):
                    raise RuntimeError(f"ElevenLabs streaming failed: {item}") from item
                yield item
        finally:
            await producer_task


class FallbackStreamingTTS:
    provider_name = "streaming"
    content_type = "audio/pcm"
    sample_rate_hz = 24_000

    def __init__(self, backends: list[StreamingTTS]) -> None:
        if not backends:
            raise ValueError("At least one TTS backend is required.")
        self._backends = backends

    async def stream(self, text: str) -> AsyncIterator[bytes]:
        failures: list[str] = []
        for backend in self._backends:
            emitted = False
            try:
                self.provider_name = backend.provider_name
                async for chunk in backend.stream(text):
                    emitted = True
                    yield chunk
                return
            except Exception as error:
                failures.append(f"{backend.provider_name}: {error}")
                if emitted:
                    raise RuntimeError(
                        "TTS failed after audio playback had already started."
                    ) from error
        raise RuntimeError("Every TTS backend failed. " + " | ".join(failures))


class OpenAITranscriber:
    def __init__(self, config: VoiceRuntimeConfig) -> None:
        if not config.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for speech transcription.")
        self._client = AsyncOpenAI(api_key=config.openai_api_key)
        self._model = config.openai_transcribe_model

    async def transcribe(self, wav_bytes: bytes) -> str:
        transcript = await self._client.audio.transcriptions.create(
            model=self._model,
            file=("jarvis-input.wav", wav_bytes, "audio/wav"),
            response_format="json",
            prompt=(
                "The speaker is addressing JARVIS. Preserve names, API terms, "
                "Kotlin, Python, Android, Gemini, Groq, Tavily, Exa, and "
                "technical punctuation accurately."
            ),
        )
        return transcript.text.strip()


class PcmInputBuffer:
    def __init__(self, *, sample_rate_hz: int, max_seconds: int) -> None:
        self._sample_rate_hz = sample_rate_hz
        self._max_bytes = sample_rate_hz * 2 * max_seconds
        self._buffer = bytearray()

    @property
    def duration_seconds(self) -> float:
        return len(self._buffer) / (self._sample_rate_hz * 2)

    def append(self, chunk: bytes) -> None:
        if len(self._buffer) + len(chunk) > self._max_bytes:
            raise ValueError("Microphone turn exceeded the configured limit.")
        self._buffer.extend(chunk)

    def clear(self) -> None:
        self._buffer.clear()

    def to_wav_bytes(self) -> bytes:
        if not self._buffer:
            raise ValueError("No microphone audio was received.")
        output = io.BytesIO()
        with wave.open(output, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(self._sample_rate_hz)
            wav_file.writeframes(bytes(self._buffer))
        return output.getvalue()


class TextSegmenter:
    def __init__(self, *, min_chars: int = 48, force_flush_chars: int = 220) -> None:
        self._buffer = ""
        self._min_chars = min_chars
        self._force_flush_chars = force_flush_chars

    def feed(self, delta: str) -> list[str]:
        self._buffer += delta
        ready: list[str] = []
        while True:
            boundary = self._find_boundary()
            if boundary is None:
                break
            segment = self._buffer[:boundary].strip()
            self._buffer = self._buffer[boundary:].lstrip()
            if segment:
                ready.append(segment)
        if len(self._buffer) >= self._force_flush_chars:
            split_at = self._buffer.rfind(" ", 0, self._force_flush_chars)
            if split_at < self._min_chars:
                split_at = self._force_flush_chars
            ready.append(self._buffer[:split_at].strip())
            self._buffer = self._buffer[split_at:].lstrip()
        return ready

    def finish(self) -> list[str]:
        remaining = self._buffer.strip()
        self._buffer = ""
        return [remaining] if remaining else []

    def _find_boundary(self) -> int | None:
        if len(self._buffer) < self._min_chars:
            return None
        for index, character in enumerate(self._buffer):
            if index + 1 >= self._min_chars and character in ".!?\n":
                return index + 1
        return None


@dataclass(frozen=True)
class SpeechQueueItem:
    kind: Literal["segment", "turn_end", "shutdown"]
    text: str = ""


class WebSocketSender:
    def __init__(self, websocket: WebSocket) -> None:
        self._websocket = websocket
        self._lock = asyncio.Lock()

    async def json(self, payload: dict[str, object]) -> None:
        async with self._lock:
            await self._websocket.send_text(json.dumps(payload, ensure_ascii=False))

    async def bytes(self, payload: bytes) -> None:
        async with self._lock:
            await self._websocket.send_bytes(payload)


class VoiceSession:
    def __init__(
        self,
        *,
        websocket: WebSocket,
        config: VoiceRuntimeConfig,
        transcriber: OpenAITranscriber,
        tts: FallbackStreamingTTS,
    ) -> None:
        self._sender = WebSocketSender(websocket)
        self._config = config
        self._transcriber = transcriber
        self._tts = tts
        self._input = PcmInputBuffer(
            sample_rate_hz=config.input_sample_rate_hz,
            max_seconds=config.max_input_seconds,
        )
        self._segmenter = TextSegmenter()
        self._speech_queue: asyncio.Queue[SpeechQueueItem] = asyncio.Queue()

    async def run(self, websocket: WebSocket) -> None:
        worker = asyncio.create_task(self._speech_worker())
        try:
            await self._sender.json(
                {
                    "type": "ready",
                    "input_format": {
                        "encoding": "pcm_s16le",
                        "sample_rate_hz": self._config.input_sample_rate_hz,
                        "channels": 1,
                    },
                    "personality": JARVIS_SYSTEM_INSTRUCTIONS,
                }
            )
            while True:
                message = await websocket.receive()
                if message["type"] == "websocket.disconnect":
                    break
                binary = message.get("bytes")
                if binary is not None:
                    self._input.append(binary)
                    continue
                raw_text = message.get("text")
                if raw_text is None:
                    continue
                await self._handle_command(json.loads(raw_text))
        except WebSocketDisconnect:
            pass
        finally:
            await self._speech_queue.put(SpeechQueueItem(kind="shutdown"))
            await worker

    async def _handle_command(self, payload: dict[str, object]) -> None:
        message_type = str(payload.get("type", "")).strip()
        if message_type == "start_input":
            self._input.clear()
            await asyncio.to_thread(
                play_system_sfx,
                "activation",
                assets_dir=self._config.assets_dir,
                blocking=True,
            )
            await self._sender.json({"type": "state", "value": "listening"})
            return

        if message_type == "stop_input":
            await self._sender.json({"type": "state", "value": "transcribing"})
            transcript = await self._transcriber.transcribe(self._input.to_wav_bytes())
            await self._sender.json(
                {
                    "type": "transcript",
                    "text": transcript,
                    "duration_seconds": round(self._input.duration_seconds, 3),
                }
            )
            await self._sender.json({"type": "state", "value": "waiting_for_brain"})
            return

        if message_type == "speak_start":
            self._segmenter = TextSegmenter()
            await asyncio.to_thread(
                play_system_sfx,
                "deactivation",
                assets_dir=self._config.assets_dir,
            )
            await self._sender.json({"type": "state", "value": "speaking"})
            return

        if message_type == "speak_delta":
            for segment in self._segmenter.feed(str(payload.get("text", ""))):
                await self._speech_queue.put(SpeechQueueItem(kind="segment", text=segment))
            return

        if message_type == "speak_end":
            for segment in self._segmenter.finish():
                await self._speech_queue.put(SpeechQueueItem(kind="segment", text=segment))
            await self._speech_queue.put(SpeechQueueItem(kind="turn_end"))
            return

        if message_type == "speak":
            text = str(payload.get("text", "")).strip()
            if not text:
                raise ValueError("speak requires non-empty text.")
            await self._handle_command({"type": "speak_start"})
            for segment in self._segmenter.feed(text):
                await self._speech_queue.put(SpeechQueueItem(kind="segment", text=segment))
            for segment in self._segmenter.finish():
                await self._speech_queue.put(SpeechQueueItem(kind="segment", text=segment))
            await self._speech_queue.put(SpeechQueueItem(kind="turn_end"))
            return

        if message_type == "ping":
            await self._sender.json({"type": "pong"})
            return
        raise ValueError(f"Unknown voice command: {message_type}")

    async def _speech_worker(self) -> None:
        turn_started = False
        while True:
            item = await self._speech_queue.get()
            if item.kind == "shutdown":
                return
            if item.kind == "turn_end":
                if turn_started:
                    await self._sender.json({"type": "audio_end"})
                await self._sender.json({"type": "state", "value": "idle"})
                turn_started = False
                continue
            if not turn_started:
                await self._sender.json(
                    {
                        "type": "audio_start",
                        "provider": self._tts.provider_name,
                        "content_type": self._tts.content_type,
                        "sample_rate_hz": self._tts.sample_rate_hz,
                        "channels": 1,
                    }
                )
                turn_started = True
            await self._sender.json({"type": "audio_segment", "text": item.text})
            async for chunk in self._tts.stream(item.text):
                await self._sender.bytes(chunk)


def build_tts(config: VoiceRuntimeConfig) -> FallbackStreamingTTS:
    primary: list[StreamingTTS] = []
    secondary: list[StreamingTTS] = []
    if config.tts_provider == "openai":
        if config.openai_api_key:
            primary.append(OpenAIStreamingTTS(config))
        if config.elevenlabs_api_key and config.elevenlabs_voice_id:
            secondary.append(ElevenLabsStreamingTTS(config))
    else:
        if config.elevenlabs_api_key and config.elevenlabs_voice_id:
            primary.append(ElevenLabsStreamingTTS(config))
        if config.openai_api_key:
            secondary.append(OpenAIStreamingTTS(config))
    return FallbackStreamingTTS(primary + secondary)


def create_app(config: VoiceRuntimeConfig | None = None) -> FastAPI:
    resolved = config or VoiceRuntimeConfig.from_env()
    transcriber = OpenAITranscriber(resolved)
    tts = build_tts(resolved)
    app = FastAPI(title="Jarvis Voice Runtime", version="1.0.0")

    @app.get("/health")
    async def health() -> dict[str, object]:
        return {
            "status": "online",
            "tts_primary": resolved.tts_provider,
            "transcriber": resolved.openai_transcribe_model,
            "personality": "OWNER-BOUND CONFIDENT VOICE CORE",
        }

    @app.get("/personality")
    async def personality() -> dict[str, str]:
        return {"system_instructions": JARVIS_SYSTEM_INSTRUCTIONS}

    @app.websocket("/voice")
    async def voice_socket(websocket: WebSocket) -> None:
        await websocket.accept()
        session = VoiceSession(
            websocket=websocket,
            config=resolved,
            transcriber=transcriber,
            tts=tts,
        )
        try:
            await session.run(websocket)
        except Exception as error:
            try:
                await websocket.send_text(
                    json.dumps({"type": "error", "message": str(error)})
                )
            finally:
                await websocket.close(code=1011)

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "voice_runtime.jarvis_voice_runtime:app",
        host=os.getenv("JARVIS_VOICE_HOST", "127.0.0.1"),
        port=int(os.getenv("JARVIS_VOICE_PORT", "8766")),
        reload=False,
    )
