from __future__ import annotations

import argparse
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44_100


def _write_wav(path: Path, samples: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frames = bytearray()
    for sample in samples:
        bounded = max(-1.0, min(1.0, sample))
        frames.extend(struct.pack("<h", int(bounded * 32_767)))

    with wave.open(str(path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(frames)


def _activation() -> list[float]:
    duration = 0.72
    frame_count = int(SAMPLE_RATE * duration)
    output: list[float] = []
    for index in range(frame_count):
        time_s = index / SAMPLE_RATE
        attack = min(1.0, time_s / 0.06)
        release = min(1.0, (duration - time_s) / 0.18)
        envelope = max(0.0, attack * release)
        phase = 2.0 * math.pi * (
            95.0 * time_s + (95.0 / (2.0 * duration)) * time_s * time_s
        )
        shimmer_frequency = 620.0 + 180.0 * time_s
        tone = (
            0.44 * math.sin(phase)
            + 0.22 * math.sin(phase * 2.01)
            + 0.18 * math.sin(2.0 * math.pi * shimmer_frequency * time_s)
        )
        output.append(tone * envelope * 0.55)
    return output


def _deactivation() -> list[float]:
    duration = 0.34
    frame_count = int(SAMPLE_RATE * duration)
    output: list[float] = []
    for index in range(frame_count):
        time_s = index / SAMPLE_RATE
        envelope = math.exp(-8.0 * time_s)
        phase = 2.0 * math.pi * (
            880.0 * time_s - (500.0 / (2.0 * duration)) * time_s * time_s
        )
        click = (
            0.35
            * math.sin(2.0 * math.pi * 1_600.0 * time_s)
            * math.exp(-28.0 * time_s)
        )
        tone = 0.65 * math.sin(phase) + 0.28 * math.sin(phase * 0.5) + click
        output.append(tone * envelope * 0.62)
    return output


def generate(destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    _write_wav(destination / "activation.wav", _activation())
    _write_wav(destination / "deactivation.wav", _deactivation())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    generate(args.destination.expanduser().resolve())


if __name__ == "__main__":
    main()
