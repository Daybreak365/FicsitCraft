#!/usr/bin/env python3
"""
Builds sounds/train_brake.ogg from a real recording instead of a synthesised squeal.

Source: "Braking Train.WAV" by jonsept, Freesound #449912, licence Creative Commons 0 (public domain dedication),
https://freesound.org/people/jonsept/sounds/449912/  (the 128 kbps preview is used, that is all we need).
The steadiest, most tonal squeal of the recording (30.0 - 36.5 s, ~1.5 kHz) is cut out, rumble below 450 Hz is removed
(the roll sound covers that), the ends are cross-faded so it loops seamlessly and the result is stored as mono OGG Vorbis.

Needs numpy + ffmpeg (or the imageio-ffmpeg package).  Run from the project root:  python3 tools/gen_brake_sample.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import wave

import numpy as np

URL = 'https://cdn.freesound.org/previews/449/449912_4185768-hq.mp3'
START, END = 30.0, 36.5
FADE = 0.6
SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft', 'sounds', 'train_brake.ogg')


def ffmpeg():
    exe = shutil.which('ffmpeg')
    if exe:
        return exe
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        sys.exit('ffmpeg not found (install ffmpeg or `pip install imageio-ffmpeg`)')


def main():
    ff = ffmpeg()
    with tempfile.TemporaryDirectory() as tmp:
        mp3 = os.path.join(tmp, 'src.mp3')
        req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=120) as r, open(mp3, 'wb') as f:
            f.write(r.read())
        wav = os.path.join(tmp, 'src.wav')
        subprocess.run([ff, '-y', '-loglevel', 'error', '-i', mp3, '-ac', '1', '-ar', str(SR), wav], check=True)
        with wave.open(wav) as w:
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768
        seg = x[int(START * SR):int(END * SR)]
        # high-pass 450 Hz (FFT, soft edge)
        spec = np.fft.rfft(seg)
        f = np.fft.rfftfreq(len(seg), 1 / SR)
        spec *= np.clip((f - 300) / 150, 0, 1)
        seg = np.fft.irfft(spec, len(seg))
        # seamless loop: blend the last FADE seconds into the first ones (equal power)
        c = int(FADE * SR)
        n = len(seg)
        fin = np.sin(np.linspace(0, np.pi / 2, c))
        fout = np.cos(np.linspace(0, np.pi / 2, c))
        loop = seg[:n - c].copy()
        loop[:c] = seg[:c] * fin + seg[n - c:] * fout
        loop *= 0.85 / (np.abs(loop).max() + 1e-9)
        pcm = os.path.join(tmp, 'loop.wav')
        with wave.open(pcm, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((loop * 32767).astype(np.int16).tobytes())
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        subprocess.run([ff, '-y', '-loglevel', 'error', '-i', pcm, '-c:a', 'libvorbis', '-q:a', '4', OUT], check=True)
    print('train_brake.ogg written (%.1f s loop, %d bytes)' % (len(loop) / SR, os.path.getsize(OUT)))


if __name__ == '__main__':
    main()
