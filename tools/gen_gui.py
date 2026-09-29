#!/usr/bin/env python3
"""Generates GUI textures (pipe inspector dial + glass sphere). Run from the project root."""
import math
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft', 'textures', 'gui')
os.makedirs(OUT, exist_ok=True)
S = 100  # each element is 100x100, atlas 256x128
atlas = Image.new('RGBA', (256, 128), (0, 0, 0, 0))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gauge():
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    c = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            if d > 49.5:
                continue
            if d > 45:      # chrome rim
                t = (math.sin((x - y) / 18) + 1) / 2
                col = lerp((150, 155, 162, 255), (245, 247, 250, 255), t)
                if d > 48.5:
                    col = (60, 62, 66, 255)
            elif d > 43.5:  # inner rim shadow
                col = (120, 124, 130, 255)
            else:           # white dial face with a soft radial gradient
                t = min(1, d / 43.5)
                col = lerp((252, 252, 252, 255), (222, 224, 228, 255), t ** 2)
                # max-flow arc band (right side, 20..135 deg below horizontal)
                ang = math.degrees(math.atan2(y - c, x - c))
                if 30 <= d <= 36 and (ang > -15 and ang < 48):
                    col = (168, 172, 178, 255)
                # tick marks from -135 to +135 (0 at top)
                a2 = (math.degrees(math.atan2(x - c, -(y - c))))  # 0 = up, clockwise positive
                if 38 <= d <= 41 and -135 <= a2 <= 135 and abs((a2 + 135) % 27) < 1.2:
                    col = (150, 154, 160, 255)
            img.putpixel((x, y), col)
    return img


def sphere():
    """Glass sphere overlay: chrome rim + reflections; interior mostly transparent (fluid is drawn under it)."""
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    c = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            if d > 49.5:
                continue
            if d > 44:
                t = (math.sin((x + y) / 16) + 1) / 2
                col = lerp((140, 146, 152, 255), (240, 243, 246, 255), t)
                if d > 48.5:
                    col = (50, 52, 56, 255)
                img.putpixel((x, y), col)
            elif d > 42.5:
                img.putpixel((x, y), (40, 42, 46, 255))
            else:
                # dark glass tint at the edge, clear centre
                a = int(max(0, (d - 30) / 12.5) * 90)
                img.putpixel((x, y), (10, 14, 20, a))
    # specular highlight (top-left)
    for y in range(S):
        for x in range(S):
            dx, dy = x - (c - 16), y - (c - 24)
            e = (dx / 16) ** 2 + (dy / 7) ** 2
            if e < 1:
                r, g, b, a = img.getpixel((x, y))
                add = int((1 - e) * 170)
                img.putpixel((x, y), (255, 255, 255, min(255, a + add)))
    return img


atlas.paste(gauge(), (0, 0))
atlas.paste(sphere(), (100, 0))
atlas.save(os.path.join(OUT, 'pipe_inspector.png'))
print('gui textures generated')
