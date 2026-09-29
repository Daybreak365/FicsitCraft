"""
Per-face texture painter for FICSIT Craft buildings.

Every face of every building box gets its OWN texture, sized to the face (32 px per block, rounded to a
multiple of 16 so mipmapping stays intact) and stretched exactly once over the face. Nothing is tiled, so
logos, stencils, vents, screens and weathering are placed deliberately per face instead of repeating per block.

Face coordinate convention (as seen from outside the face, u -> right, v -> down):
  front/back/left/right : v = down the building (top edge = top of the box)
  up                    : top edge = building front (north in the render layout), matching vanilla "up" UVs
"""
import hashlib
import math
import os

import numpy as np
from PIL import Image

PPB = 32  # pixels per block


def dim(blocks):
    return max(16, 16 * int(round(blocks * PPB / 16.0)))


def C(h):
    h = h.lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float32)


ORANGE = C('#F0922E')
ORANGE_D = C('#B8631A')
ORANGE_L = C('#FFBE6E')
STEEL = C('#9AA1A9')
STEEL_D = C('#5F666E')
STEEL_L = C('#CDD3D9')
GUN = C('#3A3F46')
DARK = C('#24272C')
DARK2 = C('#33373D')
BLACK = C('#121417')
WHITE = C('#E6E9EC')
OFFWHITE = C('#CDD2D6')
BLUE = C('#3FA7E0')
BLUE_D = C('#1D5E8A')
CYAN = C('#6FE3FF')
YELLOW = C('#F2C230')
YELLOW_D = C('#B08A12')
RED = C('#E0433A')
GREEN = C('#5CCB5F')
MOSS = C('#5E7A3A')
BRICK = C('#8A4330')
RUST = C('#7A4A2A')
GRIME = C('#3B3127')

# ---------------------------------------------------------------------------------------------------------- fonts
F57 = {
    'A': ".###.#...##...######...##...##...#", 'B': "####.#...##...#####.#...##...#####.",
    'C': ".#####....#....#....#....#.....####", 'D': "####.#...##...##...##...##...#####.",
    'E': "######....#....####.#....#....#####", 'F': "######....#....####.#....#....#....",
    'G': ".#####....#....#.####...##...#.####", 'H': "#...##...##...#######...##...##...#",
    'I': "#####..#....#....#....#....#..#####", 'J': "..###...#....#....#.#..#.#..#..##..",
    'K': "#...##..#.#.#..##...#.#..#..#.#...#", 'L': "#....#....#....#....#....#....#####",
    'M': "#...###.###.#.##.#.##...##...##...#", 'N': "#...###..##.#.##..###...##...##...#",
    'O': ".###.#...##...##...##...##...#.###.", 'P': "####.#...##...#####.#....#....#....",
    'Q': ".###.#...##...##...##.#.##..#..##.#", 'R': "####.#...##...#####.#.#..#..#.#...#",
    'S': ".#####....#.....###.....#....#####.", 'T': "#####..#....#....#....#....#....#..",
    'U': "#...##...##...##...##...##...#.###.", 'V': "#...##...##...##...##...#.#.#...#..",
    'W': "#...##...##...##.#.##.#.###.###...#", 'X': "#...##...#.#.#...#...#.#.#...##...#",
    'Y': "#...##...#.#.#...#....#....#....#..", 'Z': "#####....#...#...#...#...#....#####",
    '0': ".###.#...##..###.#.###..##...#.###.", '1': "..#...##....#....#....#....#...###.",
    '2': ".###.#...#....#...#...#...#...#####", '3': "####.....#....#.###.....#....#####.",
    '4': "...#...##..#.#.#..#.#####...#....#.", '5': "######....####.....#....##...#.###.",
    '6': ".###.#....#....####.#...##...#.###.", '7': "#####....#...#...#...#....#....#...",
    '8': ".###.#...##...#.###.#...##...#.###.", '9': ".###.#...##...#.####....#....#.###.",
    '-': "...............#####...............", '.': "..............................#....",
    '/': "....#...#....#...#....#...#....#...", ':': ".......#..........#.........#......",
    '%': "##..###..#...#...#...#...#..###..##", ' ': "." * 35, '>': "#.....#.....#.....#...#...#...#....",
    '<': "....#...#...#...#.....#.....#.....#", '!': "..#....#....#....#....#.........#..",
    '³': "###....#.###....####..............."[:35],
}
for k, v in list(F57.items()):
    F57[k] = (v + '.' * 35)[:35]


# ---------------------------------------------------------------------------------------------------------- canvas
class Cv:
    def __init__(self, fw, fh, seed, face):
        self.fw, self.fh = max(fw, 1e-3), max(fh, 1e-3)
        self.W, self.H = dim(fw), dim(fh)
        self.sx, self.sy = self.W / self.fw, self.H / self.fh
        self.face = face
        self.rng = np.random.default_rng(seed)
        self.a = np.zeros((self.H, self.W, 3), np.float32)
        self.alpha = np.full((self.H, self.W), 255, np.float32)
        # 0 = paintable surface, 1 = decal that should not weather (screens, glass, lamps)
        self.clean = np.zeros((self.H, self.W), np.float32)

    # ---- coordinate helpers (blocks along the face)
    @property
    def side(self):
        return self.face in ('front', 'back', 'left', 'right')

    def ub(self):
        return (np.arange(self.W) + 0.5)[None, :] / self.sx

    def vb(self):
        return (np.arange(self.H) + 0.5)[:, None] / self.sy

    def px_rect(self, cx, cy, w, h):
        """Normalised centre + size in blocks -> integer pixel rect (x0, y0, x1, y1), end-exclusive."""
        x0 = int(round(cx * self.W - w * self.sx / 2))
        y0 = int(round(cy * self.H - h * self.sy / 2))
        x1 = x0 + max(1, int(round(w * self.sx)))
        y1 = y0 + max(1, int(round(h * self.sy)))
        return x0, y0, x1, y1

    def fits(self, w, h, margin=0.9):
        return w <= self.fw * margin + 1e-6 and h <= self.fh * margin + 1e-6

    # ---- drawing
    def rect(self, x0, y0, x1, y1, c, a=1.0, clean=None):
        x0, x1 = max(0, int(x0)), min(self.W, int(x1))
        y0, y1 = max(0, int(y0)), min(self.H, int(y1))
        if x1 <= x0 or y1 <= y0:
            return
        r = self.a[y0:y1, x0:x1]
        r[:] = r * (1 - a) + np.asarray(c, np.float32) * a
        if clean is not None:
            self.clean[y0:y1, x0:x1] = clean

    def fill(self, c):
        self.a[:] = c

    def mul(self, x0, y0, x1, y1, f):
        x0, x1 = max(0, int(x0)), min(self.W, int(x1))
        y0, y1 = max(0, int(y0)), min(self.H, int(y1))
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] *= f

    def frame(self, x0, y0, x1, y1, c, t=1, a=1.0):
        self.rect(x0, y0, x1, y0 + t, c, a)
        self.rect(x0, y1 - t, x1, y1, c, a)
        self.rect(x0, y0, x0 + t, y1, c, a)
        self.rect(x1 - t, y0, x1, y1, c, a)

    def bevel(self, x0, y0, x1, y1, hi=1.25, lo=0.6):
        """Raised edge: highlight top/left, shadow bottom/right."""
        self.mul(x0, y0, x1, y0 + 1, hi)
        self.mul(x0, y0, x0 + 1, y1, (hi + 1) / 2)
        self.mul(x0, y1 - 1, x1, y1, lo)
        self.mul(x1 - 1, y0, x1, y1, (lo + 1) / 2)

    def inset(self, x0, y0, x1, y1):
        self.bevel(x0, y0, x1, y1, hi=0.6, lo=1.25)

    def vgrad(self, x0, y0, x1, y1, top, bot):
        x0, x1 = max(0, int(x0)), min(self.W, int(x1))
        y0, y1 = max(0, int(y0)), min(self.H, int(y1))
        if x1 <= x0 or y1 <= y0:
            return
        t = np.linspace(0, 1, y1 - y0, dtype=np.float32)[:, None, None]
        self.a[y0:y1, x0:x1] = np.asarray(top) * (1 - t) + np.asarray(bot) * t

    def disc(self, cx, cy, r, c, a=1.0):
        yy, xx = np.mgrid[0:self.H, 0:self.W]
        d = np.hypot((xx + 0.5 - cx) / 1.0, (yy + 0.5 - cy) / 1.0)
        m = (d <= r).astype(np.float32) * a
        self.a[:] = self.a * (1 - m[..., None]) + np.asarray(c) * m[..., None]

    def lownoise(self, scale_blocks, rng=None):
        """Smooth value noise in -1..1 with features of roughly `scale_blocks` size."""
        rng = rng or self.rng
        gw = max(2, int(math.ceil(self.fw / scale_blocks)) + 2)
        gh = max(2, int(math.ceil(self.fh / scale_blocks)) + 2)
        g = rng.uniform(0, 255, (gh, gw)).astype(np.uint8)
        im = Image.fromarray(g, 'L').resize((self.W, self.H), Image.BICUBIC)
        return np.asarray(im, np.float32) / 127.5 - 1

    def text(self, cx, cy, s, c, h=0.2, a=1.0, shadow=True):
        """Stencil text centred at normalised (cx, cy) with glyph height h blocks. Returns False if it can't fit."""
        s = s.upper()
        hp = h * self.sy
        big = hp >= 6.5
        gw, gh = (5, 7) if big else (3, 5)
        ky = max(1, int(round(hp / gh)))
        kx = max(1, int(round(h / gh * self.sx)))
        cw = (gw + 1) * kx
        tw = len(s) * cw - kx
        th = gh * ky
        x = int(round(cx * self.W - tw / 2))
        y = int(round(cy * self.H - th / 2))
        if tw > self.W or th > self.H:
            return False
        for ch in s:
            if big:
                glyph = F57.get(ch, F57[' '])
                rows = [glyph[i * 5:(i + 1) * 5] for i in range(7)]
            else:
                rows = F35.get(ch, F35[' '])
            for gy, row in enumerate(rows):
                for gx, bit in enumerate(row):
                    if bit in '#1':
                        px, py = x + gx * kx, y + gy * ky
                        if shadow:
                            self.rect(px + kx, py + ky, px + 2 * kx, py + 2 * ky, BLACK, 0.35 * a)
                        self.rect(px, py, px + kx, py + ky, c, a)
            x += cw
        return True

    def text_width(self, s, h):
        hp = h * self.sy
        gw = 5 if hp >= 6.5 else 3
        kx = max(1, int(round(h / (7 if hp >= 6.5 else 5) * self.sx)))
        return (len(s) * (gw + 1) * kx - kx) / self.sx

    def image(self):
        out = np.concatenate([np.clip(self.a, 0, 255), self.alpha[..., None]], axis=2).astype(np.uint8)
        return Image.fromarray(out, 'RGBA')


F35 = {
    'F': ["111", "100", "110", "100", "100"], 'I': ["111", "010", "010", "010", "111"],
    'C': ["011", "100", "100", "100", "011"], 'S': ["011", "100", "010", "001", "110"],
    'T': ["111", "010", "010", "010", "010"], 'H': ["101", "101", "111", "101", "101"],
    'U': ["101", "101", "101", "101", "111"], 'B': ["110", "101", "110", "101", "110"],
    'M': ["101", "111", "111", "101", "101"], 'K': ["101", "110", "100", "110", "101"],
    'A': ["010", "101", "111", "101", "101"], 'R': ["110", "101", "110", "101", "101"],
    'W': ["101", "101", "111", "111", "101"], 'O': ["111", "101", "101", "101", "111"],
    'N': ["101", "111", "111", "111", "101"], 'E': ["111", "100", "110", "100", "111"],
    'L': ["100", "100", "100", "100", "111"], 'P': ["110", "101", "110", "100", "100"],
    'D': ["110", "101", "101", "101", "110"], 'G': ["011", "100", "101", "101", "011"],
    'V': ["101", "101", "101", "101", "010"], 'X': ["101", "101", "010", "101", "101"],
    'Y': ["101", "101", "010", "010", "010"], 'Z': ["111", "001", "010", "100", "111"],
    'J': ["001", "001", "001", "101", "010"], 'Q': ["010", "101", "101", "110", "011"],
    '0': ["111", "101", "101", "101", "111"], '1': ["010", "110", "010", "010", "111"],
    '2': ["110", "001", "010", "100", "111"], '3': ["110", "001", "010", "001", "110"],
    '4': ["101", "101", "111", "001", "001"], '5': ["111", "100", "110", "001", "110"],
    '6': ["011", "100", "111", "101", "111"], '7': ["111", "001", "010", "010", "010"],
    '8': ["111", "101", "111", "101", "111"], '9': ["111", "101", "111", "001", "110"],
    '-': ["000", "000", "111", "000", "000"], '.': ["000", "000", "000", "000", "010"],
    '/': ["001", "001", "010", "100", "100"], ':': ["000", "010", "000", "010", "000"],
    '!': ["010", "010", "010", "000", "010"], ' ': ["000", "000", "000", "000", "000"],
    '%': ["101", "001", "010", "100", "101"], '>': ["100", "010", "001", "010", "100"],
    '<': ["001", "010", "100", "010", "001"], '³': ["110", "010", "110", "000", "000"],
}


# =================================================================================================================
# base materials: fn(cv) -> finish kind
# =================================================================================================================

def paint(color, seams=True, rivets=True, pitch=1.3):
    """Painted steel cladding with an irregular panel layout (panel seams are not aligned to blocks)."""

    def fn(cv):
        cv.fill(color)
        if not seams or (cv.fw < 0.45 and cv.fh < 0.45):
            return 'paint'
        # vertical seams
        n = max(1, int(round(cv.fw / pitch)))
        xs = [0.0]
        for i in range(1, n):
            xs.append(cv.fw * i / n + cv.rng.uniform(-0.12, 0.12))
        xs.append(cv.fw)
        ys = [0.0, cv.fh]
        if cv.fh > 1.7:
            ys = [0.0, cv.fh * cv.rng.uniform(0.38, 0.62), cv.fh]
        for i in range(len(xs) - 1):
            for j in range(len(ys) - 1):
                x0, x1 = int(xs[i] * cv.sx), int(xs[i + 1] * cv.sx)
                y0, y1 = int(ys[j] * cv.sy), int(ys[j + 1] * cv.sy)
                cv.mul(x0, y0, x1, y1, 1 + cv.rng.uniform(-0.035, 0.035))
                cv.bevel(x0, y0, x1, y1, 1.18, 0.62)
                if rivets and x1 - x0 > 10 and y1 - y0 > 10:
                    step = max(5, int(0.28 * cv.sx))
                    for x in range(x0 + 3, x1 - 3, step):
                        rivet(cv, x, y0 + 3, color)
                        rivet(cv, x, y1 - 4, color)
                    stepy = max(5, int(0.28 * cv.sy))
                    for y in range(y0 + 3 + stepy, y1 - 4, stepy):
                        rivet(cv, x0 + 3, y, color)
                        rivet(cv, x1 - 4, y, color)
        return 'paint'

    return fn


def rivet(cv, x, y, c):
    cv.rect(x, y, x + 1, y + 1, np.minimum(c * 1.45, 255))
    cv.rect(x + 1, y + 1, x + 2, y + 2, c * 0.5)


def brushed(color, seams=True):
    def fn(cv):
        cv.fill(color)
        streak = cv.rng.uniform(-1, 1, (cv.H, 1)).astype(np.float32)
        streak = np.repeat(streak, cv.W, axis=1) * 0.035 + cv.rng.uniform(-1, 1, (cv.H, cv.W)) * 0.015
        cv.a *= (1 + streak)[..., None]
        if seams and cv.fw > 0.6 and cv.fh > 0.6:
            n = max(1, int(round(cv.fw / 1.5)))
            for i in range(1, n):
                x = int((cv.fw * i / n + cv.rng.uniform(-0.1, 0.1)) * cv.sx)
                cv.mul(x, 0, x + 1, cv.H, 0.6)
                cv.mul(x + 1, 0, x + 2, cv.H, 1.2)
        cv.bevel(0, 0, cv.W, cv.H, 1.2, 0.6)
        return 'metal'

    return fn


def corrugated(color, pitch=0.19, frame=True):
    """Shipping-container style ribbed sheet metal with a heavy outer frame and corner castings."""

    def fn(cv):
        u = cv.ub()
        vertical_ribs = cv.side
        coord = u if vertical_ribs else cv.vb()
        f = 0.82 + 0.3 * (0.5 + 0.5 * np.sin(coord / pitch * 2 * math.pi))
        cv.a[:] = color * np.broadcast_to(f, (cv.H, cv.W))[..., None]
        if frame and cv.fw > 0.8 and cv.fh > 0.8:
            t = max(2, int(0.1 * cv.sy))
            tx = max(2, int(0.1 * cv.sx))
            fc = color * 0.72
            cv.rect(0, 0, cv.W, t, fc)
            cv.rect(0, cv.H - t, cv.W, cv.H, fc)
            cv.rect(0, 0, tx, cv.H, fc)
            cv.rect(cv.W - tx, 0, cv.W, cv.H, fc)
            cv.bevel(0, 0, cv.W, t)
            cv.bevel(0, cv.H - t, cv.W, cv.H)
            cs = max(3, int(0.16 * cv.sx))
            for (x, y) in ((0, 0), (cv.W - cs, 0), (0, cv.H - cs), (cv.W - cs, cv.H - cs)):
                cv.rect(x, y, x + cs, y + cs, GUN)
                cv.rect(x + cs // 3, y + cs // 3, x + cs - cs // 3, y + cs - cs // 3, BLACK)
                cv.bevel(x, y, x + cs, y + cs)
        return 'paint'

    return fn


def bricks(color=BRICK, soot=True):
    def fn(cv):
        bw, bh = 0.25, 0.125
        mortar = C('#4A3A33')
        cv.fill(mortar)
        rows = int(math.ceil(cv.fh / bh)) + 1
        for r in range(rows):
            y0 = int(r * bh * cv.sy)
            y1 = int((r + 1) * bh * cv.sy) - 1
            off = (bw / 2) if r % 2 else 0
            x = -off
            while x < cv.fw:
                x0p = int(x * cv.sx) + 1
                x1p = int((x + bw) * cv.sx)
                tone = color * cv.rng.uniform(0.78, 1.15)
                cv.rect(x0p, y0 + (1 if y1 - y0 > 2 else 0), x1p, y1 + 1, tone)
                cv.mul(x0p, y0 + 1, x1p, y0 + 2, 1.15)
                x += bw
        if soot:
            v = cv.vb() / cv.fh
            s = np.clip(1 - v * 1.6, 0, 1) * (0.55 + 0.45 * cv.lownoise(0.35))
            cv.a *= (1 - 0.55 * np.clip(s, 0, 1))[..., None]
        return 'rough'

    return fn


def floor(color=C('#7F868E')):
    """Diamond tread plate with weld seams every ~1.5 blocks and uneven dust."""

    def fn(cv):
        cv.fill(color)
        u, v = cv.ub(), cv.vb()
        a = ((u / 0.125 + v / 0.125) % 2 < 0.35) & ((np.floor(v / 0.125) % 2) == 0)
        b = ((u / 0.125 - v / 0.125) % 2 < 0.35) & ((np.floor(v / 0.125) % 2) == 1)
        m = (a | b).astype(np.float32)
        cv.a *= (1 + 0.28 * m)[..., None]
        cv.a *= (1 - 0.18 * np.roll(m, 1, axis=0))[..., None]
        for i in range(1, int(cv.fw / 1.5) + 1):
            x = int((i * 1.5 + cv.rng.uniform(-0.2, 0.2)) * cv.sx)
            if x < cv.W - 2:
                cv.rect(x, 0, x + 1, cv.H, color * 0.55)
                cv.rect(x + 1, 0, x + 2, cv.H, color * 1.2)
        for j in range(1, int(cv.fh / 1.7) + 1):
            y = int((j * 1.7 + cv.rng.uniform(-0.2, 0.2)) * cv.sy)
            if y < cv.H - 2:
                cv.rect(0, y, cv.W, y + 1, color * 0.55)
                cv.rect(0, y + 1, cv.W, y + 2, color * 1.2)
        return 'floor'

    return fn


def grating(c=GUN):
    def fn(cv):
        cv.fill(BLACK)
        u, v = cv.ub(), cv.vb()
        bar = ((u % 0.125) < 0.04) | ((v % 0.25) < 0.05)
        cv.a[:] = np.where(bar[..., None], c * 1.25, BLACK * 1.0)
        cv.a *= (1 + 0.25 * ((v % 0.25) < 0.02))[..., None]
        cv.bevel(0, 0, cv.W, cv.H, 1.3, 0.5)
        return 'metal'

    return fn


def hazard(a=YELLOW, b=BLACK, w=0.14):
    def fn(cv):
        u, v = cv.ub(), cv.vb()
        m = (np.floor((u + v) / w) % 2 == 0)
        cv.a[:] = np.where(m[..., None], a, b)
        cv.mul(0, 0, cv.W, 1, 1.25)
        cv.mul(0, cv.H - 1, cv.W, cv.H, 0.55)
        return 'paint'

    return fn


def cylinder(color, axis='y', soot=0.0, rings=1.0, cap=None):
    """Shaded pipe / tank.  `axis` is the world axis of the cylinder; faces across it get a round cap."""

    def fn(cv):
        f = cv.face
        along = {'y': ('front', 'back', 'left', 'right'), 'x': ('front', 'back', 'up', 'down'),
                 'z': ('left', 'right', 'up', 'down')}[axis]
        if f not in along:
            # end cap
            cv.fill(color * 0.55)
            cx, cy = cv.W / 2, cv.H / 2
            r = min(cv.W, cv.H) / 2 - 0.5
            yy, xx = np.mgrid[0:cv.H, 0:cv.W]
            d = np.hypot((xx + 0.5 - cx) / (cv.W / 2), (yy + 0.5 - cy) / (cv.H / 2))
            shade = np.clip(1.15 - 0.45 * d, 0.5, 1.2)
            m = d <= 1.0
            cv.a[:] = np.where(m[..., None], color * shade[..., None], cv.a)
            ring = (d > 0.78) & (d <= 0.9)
            cv.a[:] = np.where(ring[..., None], color * 0.6, cv.a)
            if cap == 'vent':
                hole = d < 0.55
                cv.a[:] = np.where(hole[..., None], BLACK * 1.0, cv.a)
            elif cap == 'glowhole':
                hole = d < 0.5
                cv.a[:] = np.where(hole[..., None], C('#2A1208'), cv.a)
            else:
                cv.disc(cx, cy, max(1, r * 0.18), color * 0.45)
            for k in range(6):
                ang = k * math.pi / 3 + 0.3
                x = cx + math.cos(ang) * r * 0.84
                y = cy + math.sin(ang) * r * 0.84
                rivet(cv, int(x), int(y), color)
            return 'metal'
        # along the cylinder: shade across the axis
        across_u = (axis == 'y') or (axis == 'z' and f in ('up', 'down'))
        t = (cv.ub() / cv.fw) if across_u else (cv.vb() / cv.fh)
        shade = 0.45 + 0.8 * np.sin(np.clip(t, 0, 1) * math.pi) ** 0.6
        shade = shade + 0.35 * np.exp(-((t - 0.3) / 0.06) ** 2)
        cv.a[:] = color * np.broadcast_to(shade, (cv.H, cv.W))[..., None]
        # flange rings every ~0.9 blocks along the axis
        if rings:
            length = cv.fh if across_u else cv.fw
            n = int(length / 0.9)
            for i in range(1, n + 1):
                p = (i * length / (n + 1))
                if across_u:
                    y = int(p * cv.sy)
                    cv.mul(0, y, cv.W, y + max(2, int(0.07 * cv.sy)), 0.62)
                    cv.mul(0, y, cv.W, y + 1, 1.5)
                else:
                    x = int(p * cv.sx)
                    cv.mul(x, 0, x + max(2, int(0.07 * cv.sx)), cv.H, 0.62)
                    cv.mul(x, 0, x + 1, cv.H, 1.5)
        if soot:
            v = cv.vb() / cv.fh
            s = np.clip(1 - v * 1.4, 0, 1) * soot * (0.6 + 0.4 * cv.lownoise(0.3))
            cv.a *= (1 - 0.7 * np.clip(s, 0, 1))[..., None]
        return 'metal'

    return fn


def glow(on=True):
    """Molten furnace window (on) or cooled soot-black grate with dying embers (off)."""

    def fn(cv):
        n = cv.lownoise(0.18, np.random.default_rng(7)) * 0.6 + cv.lownoise(0.07, np.random.default_rng(8)) * 0.4
        if on:
            hot = np.clip(0.55 + 0.6 * n, 0, 1)
            lo, mid, hi = C('#B8300C'), C('#FF8A1E'), C('#FFF0A0')
            col = np.where(hot[..., None] < 0.5, lo + (mid - lo) * (hot[..., None] * 2),
                           mid + (hi - mid) * ((hot[..., None] - 0.5) * 2))
            cv.a[:] = col
        else:
            ember = np.clip(n - 0.35, 0, 1) * 2.2
            cv.a[:] = C('#1E1714') * (1 - ember[..., None] * 0.3) + C('#7A2410') * ember[..., None]
        # window frame with bars
        fr = max(2, int(0.06 * cv.sx))
        cv.frame(0, 0, cv.W, cv.H, GUN, fr)
        cv.bevel(0, 0, cv.W, cv.H)
        for i in range(1, int(cv.fw / 0.35) + 1):
            x = int(i * cv.W / (int(cv.fw / 0.35) + 1))
            cv.rect(x - 1, 0, x + 1, cv.H, GUN)
            cv.mul(x - 1, 0, x, cv.H, 1.3)
        cv.clean[:] = 1
        return 'none'

    return fn


def vents(c=DARK2, pitch=0.12):
    def fn(cv):
        cv.fill(c * 0.8)
        v = cv.vb()
        slot = (v % pitch) < pitch * 0.45
        hi = ((v % pitch) >= pitch * 0.45) & ((v % pitch) < pitch * 0.45 + 1 / cv.sy)
        cv.a[:] = np.where(np.broadcast_to(slot, (cv.H, cv.W))[..., None], BLACK, cv.a)
        cv.a[:] = np.where(np.broadcast_to(hi, (cv.H, cv.W))[..., None], c * 1.35, cv.a)
        cv.frame(0, 0, cv.W, cv.H, c * 1.1, max(1, int(0.05 * cv.sx)))
        cv.bevel(0, 0, cv.W, cv.H)
        return 'metal'

    return fn


def fan_top(c=GUN, housing=DARK2):
    def fn(cv):
        cv.fill(housing)
        cx, cy = cv.W / 2, cv.H / 2
        yy, xx = np.mgrid[0:cv.H, 0:cv.W]
        dx, dy = (xx + 0.5 - cx) / (cv.W / 2), (yy + 0.5 - cy) / (cv.H / 2)
        d = np.hypot(dx, dy)
        ang = np.arctan2(dy, dx)
        blade = np.sin(ang * 5 + d * 3.2) > 0.1
        inner = d < 0.86
        col = np.where(blade[..., None], c * 1.45, BLACK * 1.2)
        cv.a[:] = np.where(inner[..., None], col, cv.a)
        cv.a[:] = np.where(((d >= 0.86) & (d < 0.95))[..., None], c * 0.9, cv.a)
        cv.a[:] = np.where((d < 0.22)[..., None], STEEL_L * 0.9, cv.a)
        cv.a[:] = np.where((d < 0.1)[..., None], BLACK * 1.0, cv.a)
        # guard grille
        grille = ((np.abs(xx + 0.5 - cx) % max(3, cv.W // 8)) < 1) & inner
        cv.a[:] = np.where(grille[..., None], STEEL * 0.8, cv.a)
        for k in range(4):
            x = cv.W * (0.08 if k % 2 == 0 else 0.92)
            y = cv.H * (0.08 if k < 2 else 0.92)
            rivet(cv, int(x) - 1, int(y) - 1, housing * 2)
        cv.bevel(0, 0, cv.W, cv.H)
        return 'metal'

    return fn


def drill():
    def fn(cv):
        if cv.face in ('up', 'down'):
            cv.fill(STEEL_D)
            cv.disc(cv.W / 2, cv.H / 2, min(cv.W, cv.H) * 0.42, STEEL)
            cv.disc(cv.W / 2, cv.H / 2, min(cv.W, cv.H) * 0.2, GUN)
            return 'metal'
        u, v = cv.ub() / cv.fw, cv.vb()
        spiral = np.sin((u * 2 * math.pi) + v / 0.18 * math.pi) > 0
        base = np.where(spiral[..., None], STEEL_L * 1.0, STEEL_D * 1.0)
        shade = 0.55 + 0.7 * np.sin(np.clip(u, 0, 1) * math.pi) ** 0.6
        cv.a[:] = base * np.broadcast_to(shade, (cv.H, cv.W))[..., None]
        return 'metal'

    return fn


def port(out=True):
    """Conveyor port: dark mouth with glowing chevrons (orange = output, blue = input)."""

    def fn(cv):
        rim = ORANGE if out else BLUE
        cv.fill(DARK2)
        t = max(2, int(0.07 * cv.sx))
        cv.frame(0, 0, cv.W, cv.H, rim, t)
        cv.frame(t, t, cv.W - t, cv.H - t, rim * 0.6, 1)
        m = max(3, int(0.14 * cv.sx))
        cv.rect(m, m, cv.W - m, cv.H - m, BLACK)
        cv.inset(m, m, cv.W - m, cv.H - m)
        # belt slot at the bottom of the mouth
        cv.rect(m + 1, int(cv.H * 0.72), cv.W - m - 1, int(cv.H * 0.8), C('#2A2D31'))
        # chevrons
        chev = CYAN if not out else ORANGE_L
        n = 3
        cy = cv.H * 0.42
        hh = cv.H * 0.14
        for i in range(n):
            x0 = cv.W * (0.3 + i * 0.16)
            for k in range(int(hh)):
                dx = k * 0.8 * (1 if out else -1)
                xs = x0 + dx if out else x0 + hh * 0.8 + dx
                cv.rect(xs, cy - k, xs + 2, cy - k + 1, chev, clean=1)
                cv.rect(xs, cy + k, xs + 2, cy + k + 1, chev, clean=1)
        cv.clean[:] = np.maximum(cv.clean, 0.6)
        return 'metal'

    return fn


def glass(tint=C('#78B6D6'), frame=GUN):
    def fn(cv):
        cv.vgrad(0, 0, cv.W, cv.H, tint * 0.8, tint * 0.42)
        u, v = cv.ub(), cv.vb()
        streak = (((u + v * 0.6) % 0.9) < 0.12).astype(np.float32)
        cv.a[:] = cv.a * (1 - 0.35 * streak[..., None]) + 255 * 0.35 * streak[..., None]
        t = max(2, int(0.06 * cv.sx))
        cv.frame(0, 0, cv.W, cv.H, frame, t)
        cv.bevel(0, 0, cv.W, cv.H)
        # level ticks
        for i in range(1, 8):
            y = int(cv.H * i / 8)
            cv.rect(t, y, t + max(2, cv.W // 5), y + 1, WHITE, 0.8)
        cv.clean[:] = 1
        return 'none'

    return fn


def screen(kind='text', c=CYAN, bg=C('#0E3550'), on=True, seed=0):
    """Glowing UI panel filling the face."""

    def fn(cv):
        _screen(cv, 0, 0, cv.W, cv.H, kind, c, bg, on, seed)
        return 'none'

    return fn


def _screen(cv, x0, y0, x1, y1, kind, c, bg, on, seed):
    x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
    rng = np.random.default_rng(seed + 11)
    bgc = bg if on else bg * 0.35
    cc = c if on else c * 0.3
    cv.rect(x0, y0, x1, y1, BLACK, clean=1)
    b = max(1, int(0.03 * cv.sx))
    ix0, iy0, ix1, iy1 = x0 + b, y0 + b, x1 - b, y1 - b
    cv.vgrad(ix0, iy0, ix1, iy1, bgc * 1.15, bgc * 0.7)
    W, H = ix1 - ix0, iy1 - iy0
    if W < 6 or H < 6:
        return
    # header bar
    hb = max(2, int(H * 0.12))
    cv.rect(ix0 + 1, iy0 + 1, ix1 - 1, iy0 + hb, cc * 0.55)
    cv.rect(ix0 + 2, iy0 + 2, ix0 + 2 + max(2, W // 3), iy0 + hb - 1, cc)
    body_y = iy0 + hb + 2
    if kind == 'text':
        y = body_y + 1
        while y < iy1 - 2:
            ln = int((W - 6) * rng.uniform(0.3, 0.95))
            cv.rect(ix0 + 3, y, ix0 + 3 + ln, y + 1, cc)
            y += 3
    elif kind == 'graph':
        gx0, gy0, gx1, gy1 = ix0 + 3, body_y + 1, ix1 - 3, iy1 - 3
        for gy in range(gy0, gy1, max(3, (gy1 - gy0) // 4)):
            cv.rect(gx0, gy, gx1, gy + 1, cc * 0.3)
        prev = None
        for x in range(gx0, gx1):
            t = (x - gx0) / max(1, gx1 - gx0)
            val = 0.55 + 0.25 * math.sin(t * 9 + seed) + 0.1 * math.sin(t * 23)
            y = int(gy1 - val * (gy1 - gy0))
            if prev is not None:
                cv.rect(x, min(prev, y), x + 1, max(prev, y) + 1, cc)
            cv.rect(x, y + 1, x + 1, gy1, cc * 0.25)
            prev = y
    elif kind == 'bars':
        n = max(3, W // 5)
        bw = max(1, (W - 6) // n - 1)
        for i in range(n):
            hgt = int((iy1 - body_y - 3) * rng.uniform(0.2, 0.95))
            x = ix0 + 3 + i * (bw + 1)
            cv.rect(x, iy1 - 2 - hgt, x + bw, iy1 - 2, cc if i % 3 else ORANGE_L * (1 if on else 0.3))
    elif kind == 'hub':
        # milestone list with progress bars + a big title
        cv.text((ix0 + W / 2) / cv.W, (iy0 + hb / 2 + 0.5) / cv.H, 'HUB', BLACK, h=hb * 0.8 / cv.sy, shadow=False)
        y = body_y + 2
        i = 0
        while y + 4 < iy1 - 1:
            cv.rect(ix0 + 3, y, ix0 + 6, y + 3, ORANGE if (i == 1 and on) else cc * 0.8)
            ln = int((W - 12) * rng.uniform(0.4, 0.9))
            cv.rect(ix0 + 8, y, ix0 + 8 + ln, y + 1, cc)
            prog = rng.uniform(0.1, 1.0)
            cv.rect(ix0 + 8, y + 2, ix1 - 4, y + 3, cc * 0.25)
            cv.rect(ix0 + 8, y + 2, ix0 + 8 + int((ix1 - 12 - ix0) * prog), y + 3, GREEN if prog > 0.95 else cc)
            y += 6
            i += 1
    # scanlines + glare
    for y in range(iy0, iy1, 2):
        cv.mul(ix0, y, ix1, y + 1, 0.9)
    for k in range(min(W, H) // 3):
        cv.rect(ix1 - 2 - k * 2, iy0 + 1 + k, ix1 - 1 - k * 2, iy0 + 2 + k, (255, 255, 255), 0.08)
    cv.clean[y0:y1, x0:x1] = 1


def solid(c):
    def fn(cv):
        cv.fill(c)
        cv.bevel(0, 0, cv.W, cv.H)
        return 'metal'

    return fn


def arm(c=YELLOW):
    """Industrial robot arm segment: yellow paint with dark joint bands and hydraulic line."""

    def fn(cv):
        cv.fill(c)
        if cv.side:
            n = max(1, int(cv.fh / 0.5))
            for i in range(n + 1):
                y = int(i * cv.H / max(1, n))
                cv.rect(0, y - 1, cv.W, y + max(2, int(0.06 * cv.sy)), GUN)
            cv.rect(int(cv.W * 0.7), 0, int(cv.W * 0.7) + max(1, int(0.03 * cv.sx)), cv.H, BLACK)
        else:
            cv.disc(cv.W / 2, cv.H / 2, min(cv.W, cv.H) * 0.3, GUN)
            cv.disc(cv.W / 2, cv.H / 2, min(cv.W, cv.H) * 0.12, STEEL_L)
        cv.bevel(0, 0, cv.W, cv.H)
        return 'paint'

    return fn


def strut(c=STEEL, band=YELLOW):
    """Structural column: steel I-beam look with yellow-black safety band near the bottom."""

    def fn(cv):
        cv.fill(c)
        if cv.side:
            t = cv.ub() / cv.fw
            shade = 0.75 + 0.35 * (np.abs(t - 0.5) < 0.3)
            cv.a *= np.broadcast_to(shade, (cv.H, cv.W))[..., None]
            cv.mul(int(cv.W * 0.2), 0, int(cv.W * 0.2) + 1, cv.H, 0.6)
            cv.mul(int(cv.W * 0.8), 0, int(cv.W * 0.8) + 1, cv.H, 0.6)
            if band is not None and cv.fh > 0.6:
                hb = min(0.3, cv.fh * 0.25)
                y0 = cv.H - int(hb * cv.sy) - int(0.05 * cv.sy)
                u, v = cv.ub(), cv.vb()
                m = (np.floor((u + v) / 0.1) % 2 == 0)
                reg = np.zeros((cv.H, cv.W), bool)
                reg[y0:cv.H - int(0.05 * cv.sy), :] = True
                cv.a[:] = np.where((reg & m)[..., None], band, np.where(reg[..., None], BLACK * 1.0, cv.a))
            # bolt plates
            for y in (int(cv.H * 0.1), int(cv.H * 0.5)):
                if cv.fh > 0.5:
                    rivet(cv, cv.W // 2 - 1, y, c)
        cv.bevel(0, 0, cv.W, cv.H, 1.3, 0.55)
        return 'metal'

    return fn


# =================================================================================================================
# decals: fn(cv) drawn after the base material
# =================================================================================================================

def logo(cx, cy, w=1.0, dark=False):
    def fn(cv):
        w2 = min(w, cv.fw * 0.9)
        h = w2 * 0.3
        if h > cv.fh * 0.9:
            h = cv.fh * 0.9
            w2 = h / 0.3
        x0, y0, x1, y1 = cv.px_rect(cx, cy, w2, h)
        plate = GUN if dark else ORANGE
        cv.rect(x0, y0, x1, y1, plate * 0.7)
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, plate)
        cv.bevel(x0, y0, x1, y1, 1.3, 0.55)
        inner = max(1, int((y1 - y0) * 0.15))
        cv.frame(x0 + inner, y0 + inner, x1 - inner, y1 - inner, plate * 0.72, 1)
        th = (h * 0.5)
        while th > 0.04 and cv.text_width('FICSIT', th) > w2 * 0.86:
            th *= 0.9
        cv.text(cx, cy, 'FICSIT', WHITE, h=th)
        # stars/bolts at both ends
        rivet(cv, x0 + 2, (y0 + y1) // 2, plate)
        rivet(cv, x1 - 4, (y0 + y1) // 2, plate)

    return fn


def text(cx, cy, s, c=WHITE, h=0.2, plate=None, pad=0.06):
    def fn(cv):
        hh = h
        while hh > 0.04 and cv.text_width(s, hh) > cv.fw * 0.92:
            hh *= 0.9
        if plate is not None:
            tw = cv.text_width(s, hh)
            x0, y0, x1, y1 = cv.px_rect(cx, cy, tw + pad * 2, hh + pad * 2)
            cv.rect(x0, y0, x1, y1, plate)
            cv.bevel(x0, y0, x1, y1)
        cv.text(cx, cy, s, c, h=hh, shadow=plate is None)

    return fn


def warn(cx, cy, s=0.3, c=YELLOW):
    def fn(cv):
        x0, y0, x1, y1 = cv.px_rect(cx, cy, s, s * 0.9)
        hgt = y1 - y0
        for k in range(hgt):
            half = (k + 1) * (x1 - x0) / (2 * hgt)
            mx = (x0 + x1) / 2
            cv.rect(mx - half, y0 + k, mx + half, y0 + k + 1, BLACK)
            if k >= 2 and half > 2:
                cv.rect(mx - half + 1.5, y0 + k, mx + half - 1.5, y0 + k + 1, c)
        mx = int((x0 + x1) / 2)
        sw = max(1, int((x1 - x0) * 0.1))
        cv.rect(mx - sw // 2, y0 + int(hgt * 0.35), mx - sw // 2 + sw, y0 + int(hgt * 0.7), BLACK)
        cv.rect(mx - sw // 2, y0 + int(hgt * 0.78), mx - sw // 2 + sw, y0 + int(hgt * 0.88), BLACK)

    return fn


def vent(cx, cy, w, h, c=DARK2):
    def fn(cv):
        if not cv.fits(w, h, 1.0):
            return
        x0, y0, x1, y1 = cv.px_rect(cx, cy, w, h)
        cv.rect(x0, y0, x1, y1, c * 0.8)
        cv.inset(x0, y0, x1, y1)
        pitch = max(3, int(0.1 * cv.sy))
        for y in range(y0 + 2, y1 - 2, pitch):
            cv.rect(x0 + 2, y, x1 - 2, y + max(1, pitch // 2), BLACK)
            cv.rect(x0 + 2, y + max(1, pitch // 2), x1 - 2, y + max(1, pitch // 2) + 1, c * 1.4)
        cv.frame(x0, y0, x1, y1, c * 1.2)
        # soot streak under the vent
        if cv.side:
            sh = int(min(cv.H - y1, (y1 - y0) * 1.2))
            for i in range(sh):
                cv.rect(x0 + 2, y1 + i, x1 - 2, y1 + i + 1, GRIME, 0.28 * (1 - i / max(1, sh)))

    return fn


def hatch(cx, cy, w, h, handle=True, label=None):
    def fn(cv):
        if not cv.fits(w, h, 1.0):
            return
        x0, y0, x1, y1 = cv.px_rect(cx, cy, w, h)
        cv.mul(x0, y0, x1, y1, 0.93)
        cv.frame(x0, y0, x1, y1, BLACK, 1, 0.55)
        cv.bevel(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 1.15, 0.75)
        # hinges
        hh = max(2, (y1 - y0) // 6)
        for y in (y0 + hh, y1 - 2 * hh):
            cv.rect(x0 - 1, y, x0 + 2, y + hh, GUN)
        if handle:
            hx = x1 - max(3, (x1 - x0) // 6)
            cv.rect(hx, (y0 + y1) // 2 - hh, hx + 2, (y0 + y1) // 2 + hh, STEEL_L)
            cv.rect(hx + 2, (y0 + y1) // 2 - hh, hx + 3, (y0 + y1) // 2 + hh, BLACK)
        if label:
            cv.text(cx, (y0 + (y1 - y0) * 0.25) / cv.H, label, WHITE, h=min(0.12, h * 0.15))

    return fn


def door(cx, w, h):
    """Man-door standing on the bottom edge."""

    def fn(cv):
        if not cv.fits(w, h, 1.0):
            return
        x0, _, x1, _ = cv.px_rect(cx, 0.5, w, h)
        y1 = cv.H - 1
        y0 = y1 - int(h * cv.sy)
        cv.rect(x0 - 2, y0 - 2, x1 + 2, y1, GUN)
        cv.rect(x0, y0, x1, y1, DARK2)
        cv.bevel(x0, y0, x1, y1, 1.2, 0.6)
        wy0, wy1 = y0 + (y1 - y0) // 6, y0 + (y1 - y0) // 3
        cv.rect(x0 + 2, wy0, x1 - 2, wy1, C('#5E8FA8'), clean=1)
        cv.rect(x1 - 4, (y0 + y1) // 2, x1 - 2, (y0 + y1) // 2 + 2, YELLOW)
        u, v = cv.ub(), cv.vb()
        m = (np.floor((u + v) / 0.08) % 2 == 0)
        reg = np.zeros((cv.H, cv.W), bool)
        reg[y1 - max(2, int(0.06 * cv.sy)):y1, x0:x1] = True
        cv.a[:] = np.where((reg & m)[..., None], YELLOW, np.where(reg[..., None], BLACK * 1.0, cv.a))

    return fn


def console(cx, cy, w, h, on=False, kind='text', seed=0):
    """Control station: recessed dark panel with a screen, dial gauges, buttons and status LEDs."""

    def fn(cv):
        ww, hh = min(w, cv.fw * 0.92), min(h, cv.fh * 0.92)
        x0, y0, x1, y1 = cv.px_rect(cx, cy, ww, hh)
        cv.rect(x0, y0, x1, y1, DARK)
        cv.inset(x0, y0, x1, y1)
        cv.frame(x0, y0, x1, y1, GUN, 1)
        W, H = x1 - x0, y1 - y0
        sx1 = x0 + int(W * 0.62)
        _screen(cv, x0 + 3, y0 + 3, sx1, y0 + int(H * 0.62), kind, CYAN, C('#0E3550'), on, seed)
        # gauges
        r = max(2, min(int(W * 0.09), int(H * 0.13)))
        for i, gx in enumerate((sx1 + (x1 - sx1) * 0.3, sx1 + (x1 - sx1) * 0.72)):
            gy = y0 + H * 0.22
            cv.disc(gx, gy, r + 1, STEEL_D)
            cv.disc(gx, gy, r, WHITE)
            ang = (-2.2 + (1.6 if on else 0.2) + i * 0.7)
            for k in range(r):
                cv.rect(gx + math.cos(ang) * k, gy + math.sin(ang) * k,
                        gx + math.cos(ang) * k + 1, gy + math.sin(ang) * k + 1, RED)
            cv.clean[int(gy - r):int(gy + r + 1), int(gx - r):int(gx + r + 1)] = 1
        # LEDs
        ly = y0 + int(H * 0.5)
        for i, col in enumerate((GREEN, YELLOW, RED)):
            lx = sx1 + 3 + i * max(3, (x1 - sx1 - 6) // 3)
            lit = on and (i == 0 or (i == 1 and seed % 2 == 0))
            cv.rect(lx, ly, lx + 2, ly + 2, col if lit else col * 0.3, clean=1)
        # buttons row
        by = y0 + int(H * 0.75)
        nb = max(2, W // 8)
        for i in range(nb):
            bx = x0 + 4 + i * (W - 8) // nb
            col = (ORANGE, STEEL_L, RED, GREEN)[i % 4]
            cv.rect(bx, by, bx + max(2, (W - 8) // nb - 2), by + max(2, H // 8), col)
            cv.bevel(bx, by, bx + max(2, (W - 8) // nb - 2), by + max(2, H // 8))

    return fn


def screen_d(cx, cy, w, h, kind='text', on=True, seed=0, c=CYAN):
    def fn(cv):
        x0, y0, x1, y1 = cv.px_rect(cx, cy, min(w, cv.fw * 0.95), min(h, cv.fh * 0.95))
        cv.rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GUN)
        _screen(cv, x0, y0, x1, y1, kind, c, C('#0E3550'), on, seed)

    return fn


def band(v0, v1, c, bevel=True):
    def fn(cv):
        y0, y1 = int(v0 * cv.H), max(int(v0 * cv.H) + 1, int(v1 * cv.H))
        cv.rect(0, y0, cv.W, y1, c)
        if bevel:
            cv.bevel(0, y0, cv.W, y1, 1.25, 0.6)

    return fn


def hband(v0, v1, w=0.14):
    def fn(cv):
        y0, y1 = int(v0 * cv.H), max(int(v0 * cv.H) + 1, int(v1 * cv.H))
        u, v = cv.ub(), cv.vb()
        m = (np.floor((u + v) / w) % 2 == 0)
        reg = np.zeros((cv.H, cv.W), bool)
        reg[y0:y1] = True
        cv.a[:] = np.where((reg & m)[..., None], YELLOW, np.where(reg[..., None], BLACK * 1.0, cv.a))
        cv.bevel(0, y0, cv.W, y1)

    return fn


def vpipe(u, w=0.12, c=STEEL, v0=0.0, v1=1.0):
    """Small pipe running vertically across the face with clamp brackets."""

    def fn(cv):
        x0 = int(u * cv.W - w * cv.sx / 2)
        x1 = x0 + max(2, int(w * cv.sx))
        y0, y1 = int(v0 * cv.H), int(v1 * cv.H)
        for x in range(x0, x1):
            t = (x - x0 + 0.5) / (x1 - x0)
            cv.rect(x, y0, x + 1, y1, c * (0.5 + 0.75 * math.sin(t * math.pi) ** 0.6))
        step = max(6, int(0.45 * cv.sy))
        for y in range(y0 + step // 2, y1 - 2, step):
            cv.rect(x0 - 1, y, x1 + 1, y + 2, GUN)

    return fn


def hpipe(v, h=0.12, c=STEEL, u0=0.0, u1=1.0):
    def fn(cv):
        y0 = int(v * cv.H - h * cv.sy / 2)
        y1 = y0 + max(2, int(h * cv.sy))
        x0, x1 = int(u0 * cv.W), int(u1 * cv.W)
        for y in range(y0, y1):
            t = (y - y0 + 0.5) / (y1 - y0)
            cv.rect(x0, y, x1, y + 1, c * (0.5 + 0.75 * math.sin(t * math.pi) ** 0.6))
        step = max(6, int(0.45 * cv.sx))
        for x in range(x0 + step // 2, x1 - 2, step):
            cv.rect(x, y0 - 1, x + 2, y1 + 1, GUN)

    return fn


def label(cx, cy, w=0.35, lines=3, c=OFFWHITE):
    def fn(cv):
        h = w * 0.55
        if not cv.fits(w, h, 1.0):
            return
        x0, y0, x1, y1 = cv.px_rect(cx, cy, w, h)
        cv.rect(x0, y0, x1, y1, c)
        cv.bevel(x0, y0, x1, y1, 1.1, 0.7)
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y0 + max(2, (y1 - y0) // 4), ORANGE)
        for i in range(lines):
            y = y0 + (y1 - y0) * (0.4 + 0.18 * i)
            cv.rect(x0 + 2, y, x0 + 2 + (x1 - x0 - 4) * (0.9 - 0.2 * (i % 2)), y + 1, DARK2)

    return fn


def gauge(cx, cy, r=0.1, val=0.6):
    def fn(cv):
        x, y = cx * cv.W, cy * cv.H
        rp = max(2, r * cv.sx)
        cv.disc(x, y, rp + 1, GUN)
        cv.disc(x, y, rp, WHITE)
        ang = -2.4 + val * 3.2
        for k in range(int(rp)):
            cv.rect(x + math.cos(ang) * k, y + math.sin(ang) * k, x + math.cos(ang) * k + 1,
                    y + math.sin(ang) * k + 1, RED)
        cv.clean[int(y - rp):int(y + rp + 1), int(x - rp):int(x + rp + 1)] = 1

    return fn


def leds(cx, cy, n=3, on=True):
    def fn(cv):
        s = max(2, int(0.05 * cv.sx))
        x = int(cx * cv.W - n * s)
        y = int(cy * cv.H)
        for i in range(n):
            col = (GREEN, YELLOW, RED, BLUE)[i % 4]
            cv.rect(x + i * 2 * s - 1, y - 1, x + i * 2 * s + s + 1, y + s + 1, BLACK)
            cv.rect(x + i * 2 * s, y, x + i * 2 * s + s, y + s, col if (on and i != 2) else col * 0.3, clean=1)

    return fn


def lamp_strip(v0, v1, c=C('#9FE8FF')):
    def fn(cv):
        y0, y1 = int(v0 * cv.H), max(int(v0 * cv.H) + 2, int(v1 * cv.H))
        cv.rect(0, y0 - 1, cv.W, y1 + 1, GUN)
        cv.vgrad(1, y0, cv.W - 1, y1, c * 1.0, c * 0.7)
        cv.clean[y0:y1] = 1

    return fn


def arrows(cx, cy, n=3, c=YELLOW, left=False):
    def fn(cv):
        hh = max(3, int(0.09 * cv.sy))
        for i in range(n):
            x0 = cx * cv.W + (i - n / 2) * hh * 1.6
            for k in range(hh):
                dx = (hh - k) if left else k
                cv.rect(x0 + dx * 0.8, cy * cv.H - k, x0 + dx * 0.8 + 2, cy * cv.H - k + 1, c)
                cv.rect(x0 + dx * 0.8, cy * cv.H + k, x0 + dx * 0.8 + 2, cy * cv.H + k + 1, c)

    return fn


def bolts_edge(margin=0.06, step=0.3):
    def fn(cv):
        m = int(margin * cv.sx)
        for x in range(m, cv.W - m, max(4, int(step * cv.sx))):
            rivet(cv, x, m, cv.a[m, x])
            rivet(cv, x, cv.H - m - 2, cv.a[min(cv.H - 1, cv.H - m - 2), x])

    return fn


def ring(cx, cy, r, c=YELLOW, t=0.05):
    def fn(cv):
        x, y = cx * cv.W, cy * cv.H
        yy, xx = np.mgrid[0:cv.H, 0:cv.W]
        d = np.hypot((xx + 0.5 - x) / cv.sx, (yy + 0.5 - y) / cv.sy)
        m = (np.abs(d - r) < t)
        cv.a[:] = np.where(m[..., None], c, cv.a)

    return fn


def custom(f):
    return f


# =================================================================================================================
# finishing (weathering) + face spec
# =================================================================================================================

def finish(cv, kind):
    if kind == 'none':
        cv.a[:] = cv.a * (1 + cv.rng.uniform(-0.012, 0.012, (cv.H, cv.W, 1)))
        return
    keep = cv.clean[..., None]
    orig = cv.a.copy()
    rgb = cv.a
    # large, soft tonal variation so big faces never look flat
    n = cv.lownoise(0.9) * 0.035 + cv.lownoise(0.3) * 0.025
    rgb *= (1 + n)[..., None]
    if cv.side:
        v = cv.vb() / cv.fh
        rgb *= (1.06 - 0.12 * v)[..., None]
        # grime creeping up from the bottom edge
        g = np.clip((v - 0.55) / 0.45, 0, 1) ** 1.6 * (0.55 + 0.45 * cv.lownoise(0.35))
        amt = {'paint': 0.34, 'metal': 0.3, 'rough': 0.25, 'floor': 0.2}.get(kind, 0.3)
        a = np.clip(g * amt, 0, 1)[..., None]
        rgb[:] = rgb * (1 - a) + GRIME * a
        # rain / oil streaks
        for _ in range(int(cv.fw * 2.2)):
            x = int(cv.rng.uniform(0, cv.W))
            y0 = int(cv.rng.uniform(0, cv.H * 0.6))
            ln = int(cv.rng.uniform(0.15, 0.6) * cv.H)
            for i in range(ln):
                if y0 + i < cv.H:
                    rgb[y0 + i, x] = rgb[y0 + i, x] * (1 - 0.12 * (1 - i / ln)) + GRIME * 0.12 * (1 - i / ln)
    else:
        # dust settling on horizontal faces
        d = np.clip(cv.lownoise(0.4) * 0.5 + 0.35, 0, 1)[..., None] * (0.16 if kind != 'floor' else 0.22)
        rgb[:] = rgb * (1 - d) + C('#A09584') * d
    if kind == 'paint':
        # chipped paint along the edges, exposing bare steel
        yy, xx = np.mgrid[0:cv.H, 0:cv.W]
        edge = np.minimum(np.minimum(xx, cv.W - 1 - xx), np.minimum(yy, cv.H - 1 - yy))
        chip = (cv.lownoise(0.08) > 0.45) & (edge < 3) | (cv.rng.uniform(0, 1, (cv.H, cv.W)) > 0.994)
        rgb[:] = np.where(chip[..., None], STEEL * 0.85, rgb)
        # a few scratches
        for _ in range(int(cv.fw * cv.fh * 1.5) + 1):
            x, y = cv.rng.uniform(0, cv.W), cv.rng.uniform(0, cv.H)
            ln = int(cv.rng.uniform(3, 10))
            dx, dy = cv.rng.choice([-1, 1]), cv.rng.uniform(-0.6, 0.6)
            for i in range(ln):
                xi, yi = int(x + dx * i), int(y + dy * i)
                if 0 <= xi < cv.W and 0 <= yi < cv.H:
                    rgb[yi, xi] = np.minimum(rgb[yi, xi] * 1.18 + 10, 255)
    if kind == 'rough':
        rgb *= (1 + cv.rng.uniform(-0.06, 0.06, (cv.H, cv.W, 1)))
    # per-pixel grain
    rgb *= (1 + cv.rng.uniform(-0.022, 0.022, (cv.H, cv.W, 1)))
    # clean decals (screens, glass, lamps) keep their original pixels
    cv.a[:] = rgb * (1 - keep) + orig * keep
    # outer edge definition
    cv.bevel(0, 0, cv.W, cv.H, 1.12, 0.72)


class F:
    """Face spec: base material + decals."""

    def __init__(self, base, *decals):
        self.base = base
        self.decals = list(decals)

    def __add__(self, other):
        extra = other.decals if isinstance(other, F) else list(other) if isinstance(other, (list, tuple)) else [other]
        return F(self.base, *(self.decals + extra))

    def paint(self, fw, fh, face, seed):
        cv = Cv(fw, fh, seed, face)
        kind = self.base(cv)
        for d in self.decals:
            d(cv)
        finish(cv, kind)
        return cv.image()


class Store:
    """Writes face textures, deduplicating identical images."""

    def __init__(self, tex_root):
        self.root = tex_root
        self.seen = {}
        self.count = 0

    def save(self, img, name):
        h = hashlib.sha1(img.tobytes() + bytes(str(img.size), 'ascii')).hexdigest()
        if h in self.seen:
            return self.seen[h]
        name = name.lower()
        path = os.path.join(self.root, name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        rid = 'ficsitcraft:block/bld/' + name.replace(os.sep, '/')
        self.seen[h] = rid
        self.count += 1
        return rid
