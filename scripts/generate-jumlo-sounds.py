#!/usr/bin/env python3
"""Recreate Jumlo's refined synthesized UI sounds (CC0 1.0, pure Python math).
Produces 44.1 kHz 16-bit mono acoustic-glass/wood tactile waveforms under app/src/main/res/raw/.
"""
from pathlib import Path
import math
import struct
import wave

root = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
root.mkdir(parents=True, exist_ok=True)
rate = 44100

# (start_s, start_freq_hz, end_freq_hz, duration_s, amplitude, warmth)
sounds = {
    "jumlo_tap": [
        (0.0, 1380.0, 920.0, 0.022, 0.14, 0.20),
        (0.0, 520.0, 380.0, 0.028, 0.08, 0.08),
    ],
    "jumlo_toggle": [
        (0.0, 720.0, 860.0, 0.026, 0.12, 0.16),
        (0.032, 1080.0, 1180.0, 0.030, 0.10, 0.14),
    ],
    "jumlo_start": [
        (0.0, 523.25, 528.0, 0.055, 0.11, 0.18),
        (0.048, 783.99, 788.0, 0.080, 0.10, 0.15),
    ],
    "jumlo_success": [
        (0.0, 523.25, 523.25, 0.095, 0.10, 0.18),
        (0.052, 659.25, 659.25, 0.105, 0.10, 0.16),
        (0.108, 783.99, 783.99, 0.125, 0.10, 0.14),
        (0.165, 1046.50, 1046.50, 0.170, 0.09, 0.12),
    ],
    "jumlo_error": [
        (0.0, 349.23, 330.0, 0.085, 0.11, 0.22),
        (0.092, 277.18, 255.0, 0.125, 0.10, 0.22),
    ],
    "jumlo_delete": [
        (0.0, 620.0, 440.0, 0.032, 0.10, 0.16),
        (0.034, 410.0, 290.0, 0.055, 0.08, 0.14),
    ],
}

for name, notes in sounds.items():
    total_dur = max(s + d for s, _, _, d, _, _ in notes) + 0.03
    frames = []
    for i in range(int(total_dur * rate)):
        t = i / rate
        sig = 0.0
        for start, f0, f1, dur, amp, warmth in notes:
            dt = t - start
            if dt < 0.0 or dt >= dur:
                continue
            frac = dt / dur
            # Smooth pitch glide integrated phase
            phase = 2.0 * math.pi * (f0 * dt + 0.5 * (f1 - f0) * dt * frac)
            # Soft fast attack + silky exponential release + fade-out tail (zero click)
            env = (1.0 - math.exp(-dt * 420.0)) * math.exp(-dt / (dur * 0.36)) * (1.0 - frac * frac)
            sig += amp * env * (
                math.sin(phase)
                + warmth * math.sin(2.0 * phase)
                + (warmth * 0.35) * math.sin(3.0 * phase)
            )
        sample = max(-0.92, min(0.92, sig))
        frames.append(struct.pack("<h", int(sample * 32767)))
    with wave.open(str(root / f"{name}.wav"), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(b"".join(frames))

print("Generated", len(sounds), "refined 44.1kHz Jumlo sound assets")
