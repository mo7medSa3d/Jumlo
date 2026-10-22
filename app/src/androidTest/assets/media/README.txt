Synthetic offline fixtures, generated for Jumlo's packaged native runtime regression.
No third-party media content. Both files contain 0.4 seconds of generated content.

video-only.mp4: blue 160x90 H.264, 5 fps, no audio.
audio-only.m4a: 440 Hz AAC tone, 44.1 kHz, 64 kbps, no video.

Reproduce with FFmpeg:
ffmpeg -f lavfi -i color=c=blue:size=160x90:rate=5 -t 0.4 -an -c:v libx264 -pix_fmt yuv420p -movflags +faststart video-only.mp4
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=44100 -t 0.4 -vn -c:a aac -b:a 64k -movflags +faststart audio-only.m4a
