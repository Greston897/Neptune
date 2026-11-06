#!/usr/bin/env python3
"""Render six original, deterministic ambient PCM sessions. No third-party music or samples.
Run once as an asset-authoring tool; the app plays the resulting WAV files through Media3.
"""
from pathlib import Path
import wave
import shutil
import subprocess
import numpy as np

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/audio'
ROOT.mkdir(parents=True, exist_ok=True)
SR = 22050
DURATION = 36
rng = np.random.default_rng(897)
sessions = [
    ('afterhours', [130.81, 164.81, 196.0, 246.94], 0.75),
    ('weightless', [146.83, 174.61, 220.0, 261.63], 0.62),
    ('north', [110.0, 164.81, 220.0, 277.18], 0.54),
    ('slowmotion', [123.47, 146.83, 185.0, 220.0], 0.68),
    ('orbit', [98.0, 146.83, 196.0, 246.94], 0.82),
    ('home', [130.81, 164.81, 196.0, 261.63], 0.58),
]
for index, (name, notes, beat) in enumerate(sessions):
    t = np.arange(SR * DURATION, dtype=np.float64) / SR
    left = np.zeros_like(t)
    right = np.zeros_like(t)
    # Slowly breathing chord pad with independent detuning and stereo phase.
    for j, frequency in enumerate(notes):
        envelope = 0.6 + 0.4 * np.sin(2 * np.pi * t / (9 + j * 2) + j)
        amp = 0.047 * envelope
        left += amp * np.sin(2 * np.pi * frequency * t + 0.13 * np.sin(t * 0.7 + j))
        right += amp * np.sin(2 * np.pi * (frequency * 1.0012) * t + 0.13 * np.sin(t * 0.65 + j))
    # Soft mallet melody, sparse enough for focus; all notes are synthesized from sine partials.
    step = beat * 2
    for k in range(int(DURATION / step)):
        start = k * step + 0.15
        local = t - start
        active = local >= 0
        lt = np.maximum(local, 0)
        freq = notes[(k * 3 + index) % len(notes)] * (2 if k % 4 == 3 else 1)
        env = np.exp(-lt * 2.1) * (1 - np.exp(-lt * 90)) * active
        tone = (np.sin(2 * np.pi * freq * lt) + 0.22 * np.sin(2 * np.pi * freq * 2 * lt)) * env * 0.075
        pan = 0.35 + 0.3 * np.sin(k * 1.8)
        left += tone * (1 - pan)
        right += tone * pan
        # A short delayed reflection, not a copyrighted sample.
        delay = int(0.29 * SR)
        left[delay:] += tone[:-delay] * 0.18
        right[delay:] += tone[:-delay] * 0.24
    # Very quiet rounded pulse and filtered air, no harsh drum transients.
    for k in range(int(DURATION / (beat * 4))):
        lt = np.maximum(t - k * beat * 4, 0)
        active = (t >= k * beat * 4)
        pulse = np.sin(2 * np.pi * (48 * lt + 18 * (1 - np.exp(-lt * 20)) / 20)) * np.exp(-lt * 12) * active * 0.045
        left += pulse; right += pulse
    fade = np.minimum(np.minimum(t / 2.0, (DURATION - t) / 3.0), 1.0)
    stereo = np.stack([left * fade, right * fade], axis=1)
    stereo = np.tanh(stereo * 1.35) * 0.82
    pcm = (np.clip(stereo, -1, 1) * 32767).astype('<i2')
    with wave.open(str(ROOT / f'{name}.wav'), 'wb') as output:
        output.setnchannels(2); output.setsampwidth(2); output.setframerate(SR)
        output.writeframes(pcm.tobytes())
    wav_path = ROOT / f'{name}.wav'
    ffmpeg = shutil.which('ffmpeg')
    if ffmpeg is None:
        try:
            import imageio_ffmpeg
            ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError:
            raise SystemExit('Install ffmpeg or imageio-ffmpeg to encode the distribution assets.')
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav_path),
                    '-c:a', 'aac', '-b:a', '96k', str(ROOT / f'{name}.m4a')], check=True)
    wav_path.unlink()
    print(name, len(pcm) / SR, 'seconds')
