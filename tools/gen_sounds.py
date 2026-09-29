#!/usr/bin/env python3
"""
Synthesises the train sounds (mono OGG Vorbis, all seamless loops) into assets/ficsitcraft/sounds and writes sounds.json.
Needs numpy + ffmpeg. Run from the project root:  python3 tools/gen_sounds.py

  train_roll    rail rumble + joint clacks of the bogies (played faster / louder the faster the train goes)
  train_motor   traction motor hum with an inverter whine (locomotives)
  train_brake   wheel squeal on the rails while braking (pitch falls as the train slows down)
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft')
SND = os.path.join(OUT, 'sounds')
rng = np.random.default_rng(20260929)


def periodic_noise(n, lo, hi, slope=0.0):
    """Circularly filtered noise: band limited to lo..hi Hz, spectrum ~ f^slope. Wraps around seamlessly."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    gain = np.zeros_like(f)
    band = (f >= lo) & (f <= hi)
    gain[band] = (f[band] / max(lo, 1.0)) ** slope
    # soft edges
    edge = np.minimum(1, np.minimum((f - lo) / (0.25 * lo + 1), (hi - f) / (0.25 * hi + 1))).clip(0, 1)
    x = np.fft.irfft(spec * gain * edge, n)
    return x / (np.abs(x).max() + 1e-9)


def circ_add(buf, sig, start):
    idx = (start + np.arange(len(sig))) % len(buf)
    np.add.at(buf, idx, sig)


def normalise(x, peak=0.9):
    return x / (np.abs(x).max() + 1e-9) * peak


def save(name, x):
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + '.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        os.makedirs(SND, exist_ok=True)
        dst = os.path.join(SND, name + '.ogg')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', '-ac', '1', dst], check=True)
    print('  %-12s %.2fs  %5.1f KB' % (name, len(x) / SR, os.path.getsize(dst) / 1024))


def train_roll():
    T = 2.0
    n = int(SR * T)
    t = np.arange(n) / SR
    # low rumble of the whole train + hiss of the wheels on the rails
    rumble = periodic_noise(n, 35, 320, slope=-0.8) * 0.75
    rumble *= 0.82 + 0.18 * np.sin(2 * np.pi * 1.0 * t + 0.7)
    hiss = periodic_noise(n, 1800, 7000, slope=-0.3) * 0.10
    # rail joint clacks: every 0.5 s the two axles of a bogie hit the gap 55 ms apart, the second bogie 190 ms later
    clacks = np.zeros(n)
    hits = []
    for k in range(4):
        base = 0.03 + k * 0.5
        for dt, amp in ((0.0, 1.0), (0.055, 0.8), (0.19, 0.9), (0.245, 0.7)):
            hits.append((base + dt, amp))
    for tt, amp in hits:
        m = int(SR * 0.16)
        tau = np.arange(m) / SR
        click = periodic_noise(m, 700, 3200, slope=0.0) * np.exp(-tau / 0.012)
        thump = np.sin(2 * np.pi * (95 - 220 * tau) * tau) * np.exp(-tau / 0.035)
        circ_add(clacks, amp * (0.55 * click + 0.75 * thump), int(tt * SR))
    x = rumble + hiss + 0.85 * clacks
    save('train_roll', normalise(x, 0.85))


def train_motor():
    T = 1.0
    n = int(SR * T)
    t = np.arange(n) / SR
    f0 = 84  # integer cycles per second -> seamless loop
    x = np.zeros(n)
    for h, a in ((1, 1.0), (2, 0.55), (3, 0.42), (4, 0.22), (6, 0.18), (8, 0.10)):
        x += a * np.sin(2 * np.pi * f0 * h * t + h * 0.9)
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 3 * t)
    whine = np.sin(2 * np.pi * 1176 * t) * 0.10 + np.sin(2 * np.pi * 1764 * t) * 0.05
    x = x / np.abs(x).max() * 0.7 + whine + periodic_noise(n, 60, 500, slope=-0.5) * 0.10
    save('train_motor', normalise(x, 0.7))


def train_brake():
    T = 1.5
    n = int(SR * T)
    t = np.arange(n) / SR
    wob = periodic_noise(n, 0.5, 7, slope=0.0)
    flutter = periodic_noise(n, 5, 30, slope=0.0)
    base = 3450 / T                       # 2300 Hz, an integer number of cycles in the loop
    phase = 2 * np.pi * base * t + 55 * np.cumsum(wob) / SR * 40
    # remove the drift of the cumulative sum so the phase wraps around cleanly
    drift = np.linspace(0, 1, n) * (np.cumsum(wob)[-1] / SR * 40 * 55)
    phase = 2 * np.pi * base * t + 55 * (np.cumsum(wob) / SR * 40) - drift
    x = np.zeros(n)
    for ratio, a in ((1.0, 1.0), (1.5, 0.45), (2.0, 0.5), (2.72, 0.28), (3.3, 0.16)):
        x += a * np.sin(ratio * phase + ratio * 0.4)
    x *= 0.65 + 0.35 * (0.5 + 0.5 * flutter)
    screech = periodic_noise(n, 1500, 6500, slope=0.0) * (0.35 + 0.25 * (0.5 + 0.5 * wob))
    grind = periodic_noise(n, 90, 900, slope=-0.4) * 0.35
    y = normalise(x, 0.55) + screech * 0.55 + grind * 0.4
    save('train_brake', normalise(y, 0.8))


def write_sounds_json():
    data = {}
    for name in ('train_roll', 'train_motor', 'train_brake'):
        data[name] = {'category': 'neutral', 'sounds': [{'name': 'ficsitcraft:' + name, 'stream': False}]}
    with open(os.path.join(OUT, 'sounds.json'), 'w') as fh:
        json.dump(data, fh, indent=2)
        fh.write('\n')


if __name__ == '__main__':
    print('synthesising train sounds')
    train_roll()
    train_motor()
    train_brake()
    write_sounds_json()
