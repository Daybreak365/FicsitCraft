#!/usr/bin/env python3
"""Makes every block-atlas texture square.

Minecraft treats a texture without an .mcmeta as a square sprite of side min(width, height): a 32x16 PNG shows only its
left 16x16 half (stretched over the whole face) and a 15x8 PNG is rejected outright ("not multiple of frame size").
The per-face building art is painted at the aspect ratio of each face, so before it goes into the atlas every such
image is resampled (nearest neighbour, i.e. texels keep their hard edges) to N x N with N = the longer side (at most
MAX_SIDE). The models
stretch each face texture over the whole face, so the picture on screen stays exactly the same.

Textures that already have an .mcmeta (the animated conveyor belts) and everything outside textures/block are left alone.
Run it after gen_buildings.py / gen_trains.py (it is idempotent).
"""
import os
import sys
from PIL import Image

# Longer sides are capped so the texture atlas stays small (thin 16x288 strips would otherwise become 288x288).
MAX_SIDE = 96

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft', 'textures', 'block')


def main(check_only=False):
    fixed = 0
    bad = []
    for base, _, files in os.walk(ROOT):
        for f in files:
            if not f.endswith('.png'):
                continue
            p = os.path.join(base, f)
            if os.path.exists(p + '.mcmeta'):
                continue
            im = Image.open(p)
            w, h = im.size
            if w == h:
                continue
            if check_only:
                bad.append((os.path.relpath(p, ROOT), w, h))
                continue
            n = min(max(w, h), MAX_SIDE)
            im.convert('RGBA').resize((n, n), Image.NEAREST).save(p)
            fixed += 1
    if check_only:
        for b in bad[:20]:
            print('not square:', *b)
        print(len(bad), 'non-square textures')
        return 1 if bad else 0
    print('squared', fixed, 'textures')
    return 0


if __name__ == '__main__':
    sys.exit(main('--check' in sys.argv))
