#!/usr/bin/env python3
"""Recreate Jumlo's refined iOS 27 Taptic-style synthesized UI sounds (CC0 1.0, pure Python math).
Produces 44.1 kHz 16-bit mono soft acoustic-glass/marimba waveforms under app/src/main/res/raw/.
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
        (0.0, 1240.0, 820.0, 0.018, 0.55, 0.14),
        (0.0, 460.0, 340.0, 0.022, 0.30, 0.06),
    ],
    "jumlo_toggle": [
        (0.0, 740.0, 920.0, 0.020, 0.48, 0.12),
        (0.022, 1120.0, 1260.0, 0.024, 0.42, 0.10),
    ],
    "jumlo_transition": [
        (0.0, 540.0, 760.0, 0.036, 0.40, 0.10),
        (0.014, 880.0, 1080.0, 0.034, 0.30, 0.08),
    ],
    "jumlo_start": [
        (0.0, 587.33, 592.0, 0.042, 0.44, 0.12),
        (0.036, 880.00, 884.0, 0.058, 0.40, 0.10),
    ],
    "jumlo_success": [
        (0.0, 587.33, 587.33, 0.072, 0.42, 0.12),
        (0.042, 739.99, 739.99, 0.082, 0.42, 0.10),
        (0.088, 1174.66, 1174.66, 0.115, 0.38, 0.08),
    ],
    "jumlo_error": [
        (0.0, 329.63, 308.0, 0.062, 0.46, 0.16),
        (0.068, 261.63, 242.0, 0.085, 0.42, 0.14),
    ],
    "jumlo_delete": [
        (0.0, 560.0, 390.0, 0.026, 0.44, 0.12),
        (0.026, 360.0, 260.0, 0.040, 0.34, 0.10),
    ],
}

for name, notes in sounds.items():
    total_dur = max(s + d for s, _, _, d, _, _ in notes) + 0.018
    raw = []
    for i in range(int(total_dur * rate)):
        t = i / rate
        sig = 0.0
        for start, f0, f1, dur, amp, warmth in notes:
            dt = t - start
            if dt < 0.0 or dt >= dur:
                continue
            frac = dt / dur
            phase = 2.0 * math.pi * (f0 * dt + 0.5 * (f1 - f0) * dt * frac)
            env = (1.0 - math.exp(-dt * 480.0)) * math.exp(-dt / (dur * 0.34)) * (1.0 - frac * frac)
            sig += amp * env * (
                math.sin(phase)
                + warmth * math.sin(2.0 * phase)
                + (warmth * 0.25) * math.sin(3.0 * phase)
            )
        raw.append(sig)
    peak = max((abs(x) for x in raw), default=1.0) or 1.0
    scale = 0.68 / peak
    frames = [struct.pack("<h", int(max(-0.92, min(0.92, x * scale)) * 32767)) for x in raw]
    with wave.open(str(root / f"{name}.wav"), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(b"".join(frames))

print("Generated", len(sounds), "refined iOS 27 44.1kHz Jumlo sound assets")
