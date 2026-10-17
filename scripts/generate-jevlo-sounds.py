#!/usr/bin/env python3
"""Recreate Jevlo's original synthesized UI sounds (no samples, third-party assets, or ML).
License: dedicated to the public domain under CC0 1.0 by the author of this project output.
Produces low-amplitude 22.05 kHz 16-bit mono waveforms under app/src/main/res/raw/.
"""
from pathlib import Path
import math
import wave
import struct

root=Path(__file__).resolve().parents[1]/'app/src/main/res/raw'
root.mkdir(parents=True,exist_ok=True)
rate=22050
sounds={
    'jevlo_tap':[(0,1000,.038,.11)],
    'jevlo_toggle':[(0,600,.035,.13),(.042,950,.028,.09)],
    'jevlo_start':[(0,495,.047,.12),(.056,790,.06,.10)],
    'jevlo_success':[(0,525,.052,.11),(.060,660,.058,.11),(.132,988,.095,.11)],
    'jevlo_error':[(0,370,.095,.10),(.115,275,.135,.09)],
    'jevlo_delete':[(0,520,.033,.10),(.039,390,.055,.08)]
}
for name,notes in sounds.items():
    duration=max((start+duration for start,frequency,duration,amp in notes),default=.1)
    frames=[]
    for i in range(int((duration+.025)*rate)):
        time=i/rate
        signal=0.
        for start,frequency,seconds,amp in notes:
            dt=time-start
            if dt<0 or dt>=seconds:
                continue
            attack=(1-math.exp(-dt*230))*math.exp(-dt/(seconds*.43))
            signal+=amp*attack*(math.sin(2*math.pi*frequency*dt)+.11*math.sin(2*math.pi*frequency*2*dt))
        frames.append(struct.pack('<h',int(max(-.9,min(.9,signal))*32767)))
    with wave.open(str(root/(name+'.wav')),'wb') as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(b''.join(frames))
print('generated',len(sounds),'Jevlo sound assets')
