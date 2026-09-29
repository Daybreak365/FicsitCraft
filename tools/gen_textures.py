#!/usr/bin/env python3
"""
Procedural pixel-art texture generator for FICSIT Craft.
Run from the project root:  python3 tools/gen_textures.py
Requires Pillow. All textures are 16x16 (belt top is an animated 16x(16*N) strip).
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft', 'textures')
ITEM = os.path.join(ROOT, 'item')
BLOCK = os.path.join(ROOT, 'block')
MISC = os.path.join(ROOT, 'misc')
for d in (ITEM, BLOCK, MISC):
    os.makedirs(d, exist_ok=True)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


# ---------------------------------------------------------------- palette
ORANGE = hexc('#E8913A')
ORANGE_D = hexc('#B5642200'[:7])
ORANGE_L = hexc('#FFB866')
DARK = hexc('#2E3136')
DARK2 = hexc('#3B3F45')
MID = hexc('#5B6068')
LIGHT = hexc('#9EA4AB')
STEEL = hexc('#B9C0C7')
WHITE = hexc('#E9ECEF')
BLACK = hexc('#15171A')
COPPER = hexc('#D9803F')
COPPER_L = hexc('#F2AE72')
GOLD = hexc('#E8C14A')
GOLD_L = hexc('#FFE58A')
CONCRETE = hexc('#B5B3AD')
BLUE = hexc('#4FB6F0')
GREEN = hexc('#5BAE3C')
T = (0, 0, 0, 0)


class Img:
    def __init__(self, w=16, h=16, fill=T):
        self.im = Image.new('RGBA', (w, h), fill)
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.px(x, y0, c)
            self.px(x, y1, c)
        for y in range(y0, y1 + 1):
            self.px(x0, y, c)
            self.px(x1, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def thick(self, x0, y0, x1, y1, c, w=2):
        for i in range(w):
            self.line(x0 + i, y0, x1 + i, y1, c)

    def noise(self, rng, amount=0.08, only_opaque=True):
        for y in range(self.h):
            for x in range(self.w):
                c = self.get(x, y)
                if only_opaque and c[3] == 0:
                    continue
                self.px(x, y, shade(c, 1 + rng.uniform(-amount, amount)))

    def outline(self, c=BLACK):
        """Adds a 1px dark outline around opaque pixels (item style)."""
        src = self.im.copy()
        for y in range(self.h):
            for x in range(self.w):
                if src.getpixel((x, y))[3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and src.getpixel((nx, ny))[3] > 0:
                        self.px(x, y, c)
                        break

    def save(self, path):
        self.im.save(path)


def save_item(name, img):
    img.save(os.path.join(ITEM, name + '.png'))


def save_block(name, img):
    img.save(os.path.join(BLOCK, name + '.png'))


def rng_for(name):
    return random.Random(hash(name) & 0xFFFFFFFF if False else sum(map(ord, name)) * 7919)


# ================================================================ ITEMS

def blob(img, rng, color, cx=8, cy=9, r=5.2, spots=None, spot_color=None):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2) / (r * r) + ((y - cy) ** 2) / ((r * 0.85) ** 2)
            d += rng.uniform(-0.18, 0.18)
            if d < 1:
                light = 1.15 - 0.35 * ((x - cx + (y - cy)) / (2 * r) + 0.5)
                img.px(x, y, shade(color, light))
    if spots:
        for _ in range(spots):
            x, y = rng.randint(cx - 3, cx + 3), rng.randint(cy - 3, cy + 3)
            if img.get(x, y)[3]:
                img.px(x, y, spot_color)
                if rng.random() < 0.5 and img.get(x + 1, y)[3]:
                    img.px(x + 1, y, shade(spot_color, 0.85))


def item_ore(name, color, spots=0, spot_color=None):
    rng = rng_for(name)
    img = Img()
    blob(img, rng, color, spots=spots, spot_color=spot_color)
    img.outline(shade(color, 0.35))
    save_item(name, img)


def item_ingot(name, color):
    img = Img()
    hi = shade(color, 1.25)
    lo = shade(color, 0.7)
    # isometric ingot
    for i in range(6):
        img.rect(3 + i, 6 + i, 12 + i - 2, 6 + i, color)
    img.line(4, 6, 11, 6, hi)
    for i in range(6):
        img.px(3 + i, 6 + i, hi)
    img.rect(3, 12, 13, 13, lo)
    img.rect(8, 7, 11, 8, hi)
    img.outline(shade(color, 0.35))
    save_item(name, img)


def item_plate(name, color, rivet=DARK2, cross=None, panel=None):
    img = Img()
    img.rect(2, 3, 13, 12, color)
    img.line(2, 3, 13, 3, shade(color, 1.25))
    img.line(2, 3, 2, 12, shade(color, 1.15))
    img.line(3, 12, 13, 12, shade(color, 0.7))
    img.line(13, 4, 13, 12, shade(color, 0.75))
    if cross:
        img.rect(2, 7, 13, 8, cross)
        img.rect(7, 3, 8, 12, cross)
    if panel:
        img.rect(5, 5, 10, 10, panel)
        img.rect(6, 6, 9, 7, shade(panel, 1.3))
    for x, y in ((3, 4), (12, 4), (3, 11), (12, 11)):
        img.px(x, y, rivet)
    img.outline(shade(color, 0.3))
    save_item(name, img)


def item_rod(name, color, w=2, hollow=False):
    img = Img()
    for i in range(11):
        x, y = 3 + i, 13 - i
        for k in range(w):
            img.px(x + k, y, color if k < w - 1 else shade(color, 0.7))
            img.px(x + k, y - 1, shade(color, 1.25) if k == 0 else color)
    if hollow:
        img.px(14, 2, DARK)
        img.px(3, 13, DARK)
    img.outline(shade(color, 0.3))
    save_item(name, img)


def item_screw():
    img = Img()
    # head
    img.rect(9, 2, 13, 5, LIGHT)
    img.line(10, 3, 12, 3, DARK)
    img.px(11, 2, WHITE)
    # shaft with thread
    for i in range(8):
        x, y = 9 - i, 6 + i
        img.px(x, y, STEEL)
        img.px(x + 1, y, MID if i % 2 == 0 else STEEL)
    img.outline(DARK)
    save_item('screw', img)


def item_coil(name, color, core=DARK2, turns=4):
    img = Img()
    img.rect(3, 4, 12, 11, color)
    for k in range(turns):
        y = 4 + k * 2
        img.line(3, y, 12, y, shade(color, 1.3))
    img.rect(6, 6, 9, 9, core)
    img.rect(7, 7, 8, 8, BLACK)
    img.line(12, 5, 14, 3, color)
    img.outline(shade(color, 0.35))
    save_item(name, img)


def item_cable():
    img = Img()
    c = hexc('#2A2C30')
    for y in range(3, 13):
        for x in range(3, 13):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 22:
                img.px(x, y, c)
    for r in (4.6, 3.2):
        for a in range(0, 360, 12):
            import math
            x = int(round(7.5 + r * math.cos(math.radians(a))))
            y = int(round(7.5 + r * math.sin(math.radians(a))))
            img.px(x, y, hexc('#4A4E55'))
    img.rect(7, 7, 8, 8, BLACK)
    img.line(12, 9, 14, 12, c)
    img.px(14, 13, COPPER)
    img.px(15, 13, COPPER_L)
    img.outline(BLACK)
    save_item('cable', img)


def item_crystal(name, color, count=3):
    img = Img()
    rng = rng_for(name)
    bases = [(5, 13, 7), (9, 13, 10), (12, 13, 6)][:count]
    for bx, by, h in bases:
        for i in range(h):
            y = by - i
            wdt = 1 if i > h - 3 else 2
            for k in range(-wdt, wdt + 1):
                img.px(bx + k, y, shade(color, 1.3 if k < 0 else (1.0 if k == 0 else 0.75)))
        img.px(bx, by - h, WHITE)
    img.outline(shade(color, 0.3))
    save_item(name, img)


def item_powder(name, color):
    img = Img()
    rng = rng_for(name)
    for y in range(6, 14):
        half = (y - 5) * 0.8 + 1
        for x in range(int(8 - half), int(8 + half) + 1):
            img.px(x, y, shade(color, rng.uniform(0.85, 1.12)))
    img.outline(shade(color, 0.5))
    save_item(name, img)


def item_beam(name, color):
    img = Img()
    hi = shade(color, 1.25)
    lo = shade(color, 0.65)
    for i in range(10):
        x, y = 3 + i, 12 - i
        img.px(x, y - 2, hi)
        img.px(x + 1, y - 2, hi)
        img.px(x + 1, y - 1, lo)
        img.px(x + 1, y, color)
        img.px(x + 2, y, color)
        img.px(x + 2, y + 1, lo)
        img.px(x + 3, y + 1, lo)
    img.outline(shade(color, 0.3))
    save_item(name, img)


def item_rotor():
    img = Img()
    img.rect(3, 6, 12, 9, LIGHT)
    img.line(3, 6, 12, 6, WHITE)
    img.line(3, 9, 12, 9, MID)
    for x in (4, 7, 10):
        img.rect(x, 3, x + 1, 12, ORANGE)
        img.px(x, 3, ORANGE_L)
    img.rect(1, 7, 2, 8, STEEL)
    img.rect(13, 7, 14, 8, STEEL)
    img.outline(DARK)
    save_item('rotor', img)


def item_frame(name, color, joint, thick=1, diag=None):
    img = Img()
    for k in range(thick + 1):
        img.frame(2 + k, 2 + k, 13 - k, 13 - k, color if k == 0 else shade(color, 0.8))
    if diag:
        img.line(3, 3, 12, 12, diag)
        img.line(4, 3, 12, 11, diag)
        img.line(12, 3, 3, 12, diag)
    else:
        img.line(3, 3, 12, 12, color)
        img.line(12, 3, 3, 12, color)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        img.rect(x, y, x + 1, y + 1, joint)
    img.rect(7, 7, 8, 8, joint)
    img.outline(shade(color, 0.3))
    save_item(name, img)


def item_encased_beam():
    img = Img()
    img.rect(2, 4, 13, 12, CONCRETE)
    img.line(2, 4, 13, 4, shade(CONCRETE, 1.15))
    img.line(2, 12, 13, 12, shade(CONCRETE, 0.7))
    img.rect(4, 6, 11, 10, DARK2)
    img.rect(4, 6, 11, 6, MID)
    img.rect(4, 10, 11, 10, MID)
    img.rect(7, 6, 8, 10, MID)
    img.outline(shade(CONCRETE, 0.4))
    save_item('encased_industrial_beam', img)


def item_stator():
    img = Img()
    import math
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.0 <= d <= 6.2:
                img.px(x, y, STEEL if d > 5 else COPPER)
            elif d < 3.0:
                img.px(x, y, T)
    for a in range(0, 360, 45):
        x = int(round(7.5 + 4.2 * math.cos(math.radians(a))))
        y = int(round(7.5 + 4.2 * math.sin(math.radians(a))))
        img.px(x, y, COPPER_L)
    img.outline(DARK)
    save_item('stator', img)


def item_motor():
    img = Img()
    img.rect(2, 4, 11, 12, ORANGE)
    for y in range(5, 12, 2):
        img.line(3, y, 10, y, shade(ORANGE, 0.75))
    img.line(2, 4, 11, 4, ORANGE_L)
    img.rect(12, 6, 13, 10, LIGHT)
    img.rect(14, 8, 15, 8, STEEL)
    img.rect(4, 13, 9, 13, DARK2)
    img.outline(DARK)
    save_item('motor', img)


def item_hmf():
    img = Img()
    for k in range(3):
        img.frame(1 + k, 1 + k, 14 - k, 14 - k, shade(DARK2, 1.3 - k * 0.15))
    img.rect(4, 4, 11, 11, MID)
    img.rect(6, 6, 9, 9, ORANGE)
    img.rect(7, 7, 8, 8, ORANGE_L)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        img.rect(x, y, x + 1, y + 1, ORANGE)
    img.outline(BLACK)
    save_item('heavy_modular_frame', img)


def item_automated_wiring():
    img = Img()
    img.rect(3, 3, 12, 12, DARK2)
    img.frame(3, 3, 12, 12, MID)
    for i, c in enumerate((hexc('#E84A4A'), BLUE, GOLD, GREEN)):
        img.line(4, 5 + i * 2, 11, 5 + i * 2, c)
    img.rect(6, 1, 9, 2, STEEL)
    img.rect(6, 13, 9, 14, STEEL)
    img.outline(BLACK)
    save_item('automated_wiring', img)


def item_biomass():
    rng = rng_for('biomass')
    img = Img()
    blob(img, rng, hexc('#4F8F2E'), r=5.5, spots=10, spot_color=hexc('#7FC24A'))
    img.outline(hexc('#223F12'))
    save_item('biomass', img)


def item_biofuel():
    img = Img()
    c = hexc('#3E6B2A')
    for (x0, y0) in ((2, 7), (7, 7), (4, 2), (9, 2)):
        img.rect(x0, y0, x0 + 4, y0 + 4, c)
        img.line(x0, y0, x0 + 4, y0, shade(c, 1.35))
        img.line(x0 + 4, y0 + 1, x0 + 4, y0 + 4, shade(c, 0.7))
    img.outline(hexc('#172A0F'))
    save_item('solid_biofuel', img)


def item_concrete():
    img = Img()
    img.rect(3, 5, 12, 13, CONCRETE)
    img.rect(3, 5, 12, 6, shade(CONCRETE, 1.15))
    img.rect(5, 8, 10, 10, hexc('#8E8C86'))
    img.px(4, 12, shade(CONCRETE, 0.8))
    img.px(11, 7, shade(CONCRETE, 0.85))
    img.outline(shade(CONCRETE, 0.4))
    save_item('concrete', img)


def item_build_gun():
    img = Img()
    img.rect(2, 5, 12, 8, ORANGE)
    img.line(2, 5, 12, 5, ORANGE_L)
    img.rect(12, 6, 15, 7, STEEL)
    img.rect(5, 9, 7, 13, DARK2)
    img.rect(8, 9, 9, 10, MID)
    img.rect(3, 3, 8, 4, DARK2)
    img.px(4, 3, BLUE)
    img.px(5, 3, BLUE)
    img.rect(9, 6, 10, 7, BLUE)
    img.outline(BLACK)
    save_item('build_gun', img)


def items():
    item_ore('limestone', hexc('#D6CCB0'), 6, hexc('#B8AD8E'))
    item_ore('caterium_ore', hexc('#8A6A3E'), 12, GOLD)
    item_crystal('raw_quartz', hexc('#E3A6D6'))
    item_ingot('caterium_ingot', GOLD)
    item_ingot('steel_ingot', hexc('#7F8791'))
    item_concrete()
    item_plate('iron_plate', LIGHT)
    item_rod('iron_rod', LIGHT)
    item_screw()
    item_coil('wire', COPPER)
    item_cable()
    item_plate('copper_sheet', COPPER, rivet=COPPER_L)
    item_coil('quickwire', GOLD, core=hexc('#6B5217'))
    item_crystal('quartz_crystal', hexc('#B37BE8'))
    item_powder('silica', hexc('#E6E6EE'))
    item_beam('steel_beam', hexc('#6C737C'))
    item_rod('steel_pipe', hexc('#8E959E'), w=3, hollow=True)
    item_plate('reinforced_iron_plate', LIGHT, rivet=DARK, cross=MID)
    item_rotor()
    item_frame('modular_frame', LIGHT, ORANGE)
    item_encased_beam()
    item_stator()
    item_motor()
    item_hmf()
    item_plate('smart_plating', STEEL, rivet=DARK, panel=BLUE)
    item_frame('versatile_framework', STEEL, ORANGE, thick=1, diag=ORANGE)
    item_automated_wiring()
    item_biomass()
    item_biofuel()
    item_build_gun()


# ================================================================ BLOCKS

def base_metal(rng, color=DARK2, amount=0.05):
    img = Img(fill=color)
    img.noise(rng, amount)
    return img


def bevel(img, light, dark):
    img.line(0, 0, 15, 0, light)
    img.line(0, 0, 0, 15, light)
    img.line(0, 15, 15, 15, dark)
    img.line(15, 0, 15, 15, dark)


def machine_side(name, accent, pattern):
    rng = rng_for(name + '_side')
    img = base_metal(rng, ORANGE, 0.05)
    bevel(img, ORANGE_L, shade(ORANGE, 0.6))
    # inner dark panel
    img.rect(2, 2, 13, 13, DARK2)
    img.frame(2, 2, 13, 13, shade(ORANGE, 0.55))
    pattern(img, accent)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.px(x, y, MID)
    return img


def pat_furnace(img, accent):
    img.rect(4, 6, 11, 11, BLACK)
    img.rect(5, 8, 10, 11, accent)
    img.rect(6, 9, 9, 11, shade(accent, 1.3))
    img.line(4, 4, 11, 4, MID)


def pat_gear(img, accent):
    import math
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ang = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 45
            if d < 3.5 or (d < 5.2 and ang < 20):
                img.px(x, y, accent)
    img.rect(7, 7, 8, 8, DARK)


def pat_arms(img, accent):
    img.line(4, 12, 7, 6, accent)
    img.line(5, 12, 8, 6, accent)
    img.line(11, 12, 8, 6, accent)
    img.line(12, 12, 9, 6, accent)
    img.rect(6, 4, 9, 6, LIGHT)
    img.rect(4, 11, 11, 12, MID)


def pat_manufacturer(img, accent):
    for y in (4, 7, 10):
        img.rect(4, y, 11, y + 1, accent)
        img.px(4, y, WHITE)
    img.rect(10, 4, 11, 11, shade(accent, 0.7))


def pat_drill(img, accent):
    for i in range(6):
        img.line(7 - i // 2, 4 + i, 8 + i // 2, 4 + i, accent)
    img.rect(6, 10, 9, 12, MID)


def pat_burner(img, accent):
    img.rect(4, 5, 11, 12, BLACK)
    for x in range(5, 11):
        h = 3 + (x * 7) % 4
        img.line(x, 12, x, 12 - h, accent if x % 2 else shade(accent, 1.3))


def pat_coal(img, accent):
    img.rect(4, 4, 11, 12, DARK)
    img.rect(5, 8, 10, 12, accent)
    for x in (5, 8):
        img.rect(x, 3, x + 1, 5, LIGHT)


def pat_crate(img, accent):
    img.line(3, 3, 12, 12, accent)
    img.line(12, 3, 3, 12, accent)
    img.frame(3, 3, 12, 12, accent)


def machine_top(name):
    rng = rng_for(name + '_top')
    img = base_metal(rng, MID, 0.05)
    bevel(img, LIGHT, DARK)
    img.rect(3, 3, 12, 12, DARK2)
    for y in range(4, 12, 2):
        img.line(4, y, 11, y, BLACK)
    img.frame(2, 2, 13, 13, ORANGE)
    return img


def machine_bottom(name):
    rng = rng_for(name + '_bottom')
    img = base_metal(rng, DARK, 0.06)
    bevel(img, MID, BLACK)
    img.frame(3, 3, 12, 12, DARK2)
    return img


def port(name, kind):
    """Output (front) or input side with a conveyor port."""
    rng = rng_for(name + kind)
    img = base_metal(rng, MID, 0.05)
    bevel(img, LIGHT, DARK)
    img.rect(3, 4, 12, 12, BLACK)
    img.frame(2, 3, 13, 13, ORANGE)
    arrow = ORANGE_L if kind == 'out' else hexc('#6FD0FF')
    # arrow pointing "up" (out of the machine surface, as a symbol)
    img.rect(7, 6, 8, 11, arrow)
    img.line(5, 8, 7, 6, arrow)
    img.line(10, 8, 8, 6, arrow)
    img.line(5, 1, 10, 1, arrow if kind == 'out' else MID)
    return img


def control_panel(name, on):
    rng = rng_for(name + '_panel')
    img = base_metal(rng, ORANGE, 0.05)
    bevel(img, ORANGE_L, shade(ORANGE, 0.6))
    img.rect(2, 2, 13, 13, DARK2)
    screen = hexc('#58D26A') if on else hexc('#23372A')
    img.rect(3, 3, 12, 7, BLACK)
    img.rect(4, 4, 11, 6, screen)
    if on:
        img.line(4, 5, 7, 5, hexc('#B7FFB9'))
    img.rect(4, 9, 5, 10, hexc('#FF4A4A') if not on else hexc('#5A1C1C'))
    img.rect(7, 9, 8, 10, hexc('#FFD84A') if on else hexc('#5A4E1C'))
    img.rect(10, 9, 11, 10, hexc('#6BE675') if on else hexc('#1E4A22'))
    img.rect(4, 12, 11, 12, BLACK)  # input slot
    return img


MACHINES = {
    'smelter': (hexc('#FF7A1A'), pat_furnace),
    'foundry': (hexc('#FF4A1A'), pat_furnace),
    'constructor': (LIGHT, pat_gear),
    'assembler': (STEEL, pat_arms),
    'manufacturer': (BLUE, pat_manufacturer),
    'miner_mk1': (LIGHT, pat_drill),
    'miner_mk2': (GOLD, pat_drill),
    'biomass_burner': (hexc('#FF8A2A'), pat_burner),
    'coal_generator': (hexc('#FF5A1A'), pat_coal),
}


def glow(img, accent):
    """brighten accent-coloured pixels for the 'on' texture"""
    out = Img()
    out.im = img.im.copy()
    for y in range(16):
        for x in range(16):
            c = out.get(x, y)
            if abs(c[0] - accent[0]) < 40 and abs(c[1] - accent[1]) < 40 and abs(c[2] - accent[2]) < 40:
                out.px(x, y, shade(c, 1.35))
    return out


def blocks():
    for name, (accent, pattern) in MACHINES.items():
        side = machine_side(name, shade(accent, 0.75), pattern)
        save_block(name + '_side', side)
        side_on = machine_side(name, accent, pattern)
        save_block(name + '_side_on', glow(side_on, accent))
        save_block(name + '_top', machine_top(name))
        save_block(name + '_bottom', machine_bottom(name))
        save_block(name + '_front', port(name, 'out'))
        save_block(name + '_back', control_panel(name, False))
        save_block(name + '_back_on', control_panel(name, True))

    # storage container
    rng = rng_for('storage')
    img = base_metal(rng, ORANGE, 0.06)
    bevel(img, ORANGE_L, shade(ORANGE, 0.6))
    img.rect(2, 2, 13, 13, shade(ORANGE, 0.85))
    pat_crate(img, shade(ORANGE, 0.6))
    save_block('storage_container_side', img)
    top = machine_top('storage')
    save_block('storage_container_top', top)

    # HUB
    rng = rng_for('hub')
    hub = base_metal(rng, DARK2, 0.05)
    bevel(hub, MID, BLACK)
    hub.rect(2, 2, 13, 10, BLACK)
    hub.rect(3, 3, 12, 9, hexc('#1C4C6E'))
    hub.line(4, 5, 10, 5, BLUE)
    hub.line(4, 7, 8, 7, BLUE)
    hub.rect(2, 12, 13, 13, ORANGE)
    hub.rect(6, 12, 9, 13, ORANGE_L)
    save_block('hub_front', hub)
    hs = base_metal(rng_for('hubs'), DARK2, 0.05)
    bevel(hs, MID, BLACK)
    hs.rect(0, 12, 15, 13, ORANGE)
    hs.rect(3, 3, 12, 9, MID)
    for y in range(4, 9, 2):
        hs.line(4, y, 11, y, DARK)
    save_block('hub_side', hs)
    save_block('hub_top', machine_top('hub'))

    # Craft bench
    cb = base_metal(rng_for('cbtop'), LIGHT, 0.05)
    bevel(cb, WHITE, MID)
    cb.frame(1, 1, 14, 14, ORANGE)
    cb.rect(4, 4, 7, 7, MID)
    cb.rect(9, 9, 12, 12, MID)
    cb.line(9, 4, 12, 7, DARK)
    save_block('craft_bench_top', cb)
    cbs = base_metal(rng_for('cbs'), ORANGE, 0.05)
    bevel(cbs, ORANGE_L, shade(ORANGE, 0.6))
    cbs.rect(0, 0, 15, 2, LIGHT)
    cbs.rect(2, 3, 3, 15, DARK2)
    cbs.rect(12, 3, 13, 15, DARK2)
    cbs.rect(4, 6, 11, 9, DARK2)
    cbs.rect(7, 7, 8, 8, STEEL)
    save_block('craft_bench_side', cbs)
    cbf = Img()
    cbf.im = cbs.im.copy()
    cbf.rect(4, 11, 11, 13, BLUE)
    save_block('craft_bench_front', cbf)

    # Splitter / merger
    for name, arrows in (('splitter', ((7, 12, 7, 3), (7, 8, 2, 8), (8, 8, 13, 8))),
                         ('merger', ((7, 12, 7, 3), (2, 8, 7, 8), (13, 8, 8, 8)))):
        img = base_metal(rng_for(name), DARK2, 0.05)
        bevel(img, MID, BLACK)
        img.frame(1, 1, 14, 14, ORANGE)
        for x0, y0, x1, y1 in arrows:
            img.thick(x0, y0, x1, y1, ORANGE_L)
        # arrow head at the output (top of texture = north = front)
        img.line(5, 5, 7, 3, ORANGE_L)
        img.line(10, 5, 8, 3, ORANGE_L)
        save_block(name + '_top', img)
        s = base_metal(rng_for(name + 's'), MID, 0.05)
        bevel(s, LIGHT, DARK)
        s.rect(4, 4, 11, 11, BLACK)
        s.frame(3, 3, 12, 12, ORANGE)
        save_block(name + '_side', s)

    # Power poles
    for mk, col in ((1, LIGHT), (2, STEEL), (3, hexc('#D6DCE2'))):
        img = base_metal(rng_for('pole%d' % mk), col, 0.04)
        for x in range(0, 16, 4):
            img.line(x, 0, x, 15, shade(col, 0.8))
        img.rect(0, 0, 15, 1, ORANGE)
        img.rect(0, 14, 15, 15, DARK2)
        for m in range(mk):
            img.rect(6, 4 + m * 3, 9, 5 + m * 3, ORANGE)
        save_block('power_pole_mk%d' % mk, img)
    ins = Img(fill=hexc('#3E6E9E'))
    ins.noise(rng_for('ins'), 0.08)
    for y in range(0, 16, 3):
        ins.line(0, y, 15, y, hexc('#6FA6D6'))
    save_block('power_pole_insulator', ins)

    # Conveyor belts: animated tops + side rails
    speeds = {1: 4, 2: 2, 3: 1}
    for mk, frametime in speeds.items():
        frames = 16
        strip = Image.new('RGBA', (16, 16 * frames))
        rail = {1: LIGHT, 2: hexc('#6FB0E0'), 3: hexc('#E0C050')}[mk]
        for f in range(frames):
            img = Img(fill=hexc('#26282C'))
            img.rect(0, 0, 1, 15, rail)
            img.rect(14, 0, 15, 15, rail)
            img.px(1, 0, shade(rail, 0.7))
            for y in range(16):
                # moving chevrons (texture "up" = north = belt direction)
                yy = (y + f) % 16
                if yy % 8 in (0, 1):
                    k = yy % 8
                    img.line(3, y, 12, y, hexc('#3A3D42'))
                if yy % 8 == 4:
                    img.px(7, y, ORANGE)
                    img.px(8, y, ORANGE)
                if yy % 8 == 5:
                    img.px(6, y, ORANGE)
                    img.px(9, y, ORANGE)
                if yy % 8 == 6:
                    img.px(5, y, shade(ORANGE, 0.8))
                    img.px(10, y, shade(ORANGE, 0.8))
            strip.paste(img.im, (0, 16 * f))
        strip.save(os.path.join(BLOCK, 'conveyor_belt_mk%d_top.png' % mk))
        with open(os.path.join(BLOCK, 'conveyor_belt_mk%d_top.png.mcmeta' % mk), 'w') as fh:
            fh.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)
        # curved belt (input from the left/west, output north), animated like the straight one
        import math
        arc_len = math.pi / 2 * 8
        turn = Image.new('RGBA', (16, 16 * frames))
        for f in range(frames):
            img = Img()  # transparent: the outer corner is cut round
            for y in range(16):
                for x in range(16):
                    r = math.hypot(x + 0.5, y + 0.5)
                    if r >= 16.3:
                        continue
                    if r >= 15.2:
                        img.px(x, y, shade(rail, 0.7))
                        continue
                    if r < 2 or r >= 14:
                        img.px(x, y, rail if r >= 14 else shade(rail, 0.8))
                        continue
                    img.px(x, y, hexc('#26282C'))
                    ang = math.atan2(x + 0.5, y + 0.5)  # 0 at the west input, pi/2 at the north output
                    d = ang / (math.pi / 2) * arc_len
                    k = int(round(-d * 1.0 + f + 1000)) % 8
                    off = abs(r - 8)
                    if k in (0, 1) and off <= 5:
                        img.px(x, y, hexc('#3A3D42'))
                    if (k == 4 and off <= 1) or (k == 5 and 1 < off <= 2) or (k == 6 and 2 < off <= 3):
                        img.px(x, y, ORANGE if k < 6 else shade(ORANGE, 0.8))
            turn.paste(img.im, (0, 16 * f))
        turn.save(os.path.join(BLOCK, 'conveyor_belt_mk%d_turn_left.png' % mk))
        turn.transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(BLOCK, 'conveyor_belt_mk%d_turn_right.png' % mk))
        for n in ('turn_left', 'turn_right'):
            with open(os.path.join(BLOCK, 'conveyor_belt_mk%d_%s.png.mcmeta' % (mk, n)), 'w') as fh:
                fh.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)

        side = Img(fill=rail)
        side.noise(rng_for('rail%d' % mk), 0.05)
        side.line(0, 0, 15, 0, shade(rail, 1.2))
        side.rect(0, 12, 15, 15, DARK2)
        for x in range(1, 16, 4):
            side.px(x, 13, STEEL)
        save_block('conveyor_belt_mk%d_side' % mk, side)

    # Foundation / concrete wall
    f = Img(fill=CONCRETE)
    f.noise(rng_for('foundation'), 0.05)
    f.frame(0, 0, 15, 15, shade(CONCRETE, 0.8))
    f.line(0, 0, 15, 0, shade(CONCRETE, 1.1))
    f.px(1, 1, DARK2)
    f.px(14, 1, DARK2)
    f.px(1, 14, DARK2)
    f.px(14, 14, DARK2)
    save_block('foundation_top', f)
    fs = Img(fill=CONCRETE)
    fs.noise(rng_for('foundations'), 0.05)
    fs.rect(0, 0, 15, 1, ORANGE)
    fs.rect(0, 2, 15, 2, shade(ORANGE, 0.6))
    fs.frame(0, 0, 15, 15, shade(CONCRETE, 0.75))
    save_block('foundation_side', fs)
    w = Img(fill=hexc('#C9C7C1'))
    w.noise(rng_for('wall'), 0.04)
    w.line(0, 7, 15, 7, shade(CONCRETE, 0.8))
    w.line(0, 15, 15, 15, shade(CONCRETE, 0.8))
    w.line(7, 0, 7, 7, shade(CONCRETE, 0.85))
    w.line(15, 8, 15, 15, shade(CONCRETE, 0.85))
    save_block('concrete_wall', w)

    # Resource nodes
    nodes = {
        'iron': (hexc('#6A5A52'), hexc('#B77A60'), hexc('#D8A58C')),
        'copper': (hexc('#5E5850'), hexc('#D67A3A'), hexc('#6FC2A0')),
        'limestone': (hexc('#9F9886'), hexc('#DCD3BA'), hexc('#F0EAD8')),
        'coal': (hexc('#4A4A4A'), hexc('#1A1A1A'), hexc('#303030')),
        'caterium': (hexc('#6E5C44'), hexc('#E8C14A'), hexc('#FFE58A')),
        'quartz': (hexc('#6A5F6E'), hexc('#E3A6D6'), hexc('#FFFFFF')),
    }
    for name, (rock, ore, hi) in nodes.items():
        rng = rng_for(name + 'node')
        top = Img(fill=rock)
        top.noise(rng, 0.12)
        # ore veins / clusters
        for _ in range(9):
            cx, cy = rng.randint(1, 14), rng.randint(1, 14)
            for dx in range(-1, 2):
                for dy in range(-1, 2):
                    if rng.random() < 0.65:
                        top.px(cx + dx, cy + dy, shade(ore, rng.uniform(0.85, 1.1)))
            top.px(cx, cy, hi)
        # FICSIT marker ring
        top.frame(0, 0, 15, 15, shade(rock, 0.7))
        save_block(name + '_node_top', top)
        side = Img(fill=rock)
        side.noise(rng_for(name + 'nodes'), 0.12)
        for _ in range(5):
            cx, cy = rng.randint(1, 14), rng.randint(0, 7)
            side.px(cx, cy, ore)
            side.px(cx + 1, cy, shade(ore, 0.85))
        side.rect(0, 0, 15, 0, shade(ore, 0.9))
        save_block(name + '_node_side', side)

    # power line texture
    pl = Img(fill=hexc('#1E1F22'))
    pl.noise(rng_for('pl'), 0.1)
    for x in range(0, 16, 4):
        pl.line(x, 0, x, 15, hexc('#2C2E32'))
    pl.save(os.path.join(MISC, 'power_line.png'))


def building_materials():
    """Generic 16x16 materials used by the multi-block building renderer (tiled per block)."""
    # orange casing panel
    img = base_metal(rng_for('casing'), ORANGE, 0.04)
    bevel(img, ORANGE_L, shade(ORANGE, 0.6))
    img.line(0, 8, 15, 8, shade(ORANGE, 0.75))
    img.line(0, 9, 15, 9, shade(ORANGE, 1.1))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (2, 6), (13, 6)):
        img.px(x, y, shade(ORANGE, 0.55))
    save_block('casing', img)
    # dark casing
    img = base_metal(rng_for('casing_dark'), DARK2, 0.05)
    bevel(img, MID, BLACK)
    img.frame(2, 2, 13, 13, shade(DARK2, 0.8))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.px(x, y, LIGHT)
    save_block('casing_dark', img)
    # steel frame / grating
    img = Img(fill=hexc('#4A4F57'))
    img.noise(rng_for('frame'), 0.05)
    for i in range(0, 16, 4):
        img.line(i, 0, i, 15, hexc('#7C838C'))
        img.line(0, i, 15, i, hexc('#7C838C'))
    img.frame(0, 0, 15, 15, hexc('#2A2D32'))
    save_block('frame', img)
    # vent
    img = base_metal(rng_for('vent'), MID, 0.04)
    bevel(img, LIGHT, DARK)
    for y in range(3, 14, 2):
        img.line(2, y, 13, y, BLACK)
        img.line(2, y + 1, 13, y + 1, shade(MID, 1.2))
    save_block('vent', img)
    # pipe (vertical)
    img = Img()
    cols = [shade(STEEL, f) for f in (0.55, 0.7, 0.85, 1.0, 1.1, 1.15, 1.1, 1.0, 0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.62, 0.55)]
    for x in range(16):
        for y in range(16):
            img.px(x, y, cols[x])
    img.rect(0, 0, 15, 1, ORANGE)
    img.rect(0, 14, 15, 15, shade(STEEL, 0.5))
    img.noise(rng_for('pipe'), 0.03)
    save_block('pipe', img)
    # hazard stripes
    img = Img(fill=hexc('#E8C030'))
    for y in range(16):
        for x in range(16):
            if ((x + y) // 4) % 2 == 0:
                img.px(x, y, hexc('#202020'))
    save_block('hazard', img)
    # conveyor port
    img = base_metal(rng_for('port'), DARK2, 0.04)
    img.frame(0, 0, 15, 15, ORANGE)
    img.frame(1, 1, 14, 14, shade(ORANGE, 0.7))
    img.rect(3, 3, 12, 12, BLACK)
    img.rect(4, 11, 11, 12, hexc('#26282C'))
    for x in (5, 8, 11):
        img.px(x - 1, 7, ORANGE_L)
        img.px(x, 6, ORANGE_L)
    save_block('port', img)
    # glow (furnace / lights)
    img = Img(fill=hexc('#FF8A2A'))
    img.noise(rng_for('glow'), 0.12)
    for _ in range(18):
        r = rng_for('glowspots')
    rr = rng_for('glowspots2')
    for _ in range(20):
        img.px(rr.randint(0, 15), rr.randint(0, 15), hexc('#FFE08A'))
    save_block('glow', img)
    img = Img(fill=hexc('#5FD7FF'))
    img.noise(rng_for('glowb'), 0.08)
    img.line(2, 4, 12, 4, WHITE)
    img.line(2, 7, 9, 7, WHITE)
    img.line(2, 10, 11, 10, WHITE)
    save_block('screen', img)
    # tank
    img = Img(fill=hexc('#C8CCD2'))
    img.noise(rng_for('tank'), 0.04)
    img.rect(0, 3, 15, 4, hexc('#8A9098'))
    img.rect(0, 11, 15, 12, hexc('#8A9098'))
    img.rect(6, 5, 9, 10, BLUE)
    save_block('tank', img)
    # drill
    img = Img(fill=hexc('#7A8088'))
    for y in range(16):
        for x in range(16):
            if (x + y * 2) % 8 < 3:
                img.px(x, y, hexc('#C0C6CC'))
    save_block('drill', img)


def creative_generator():
    """Test-only creative generator: dark cabinet with a violet/cyan energy core and a lightning bolt."""
    VIOLET = hexc('#B04CFF')
    CYAN = hexc('#5FE0FF')
    side = base_metal(rng_for('cg_side'), DARK2, 0.05)
    bevel(side, MID, BLACK)
    side.frame(1, 1, 14, 14, shade(VIOLET, 0.55))
    side.rect(2, 2, 13, 13, hexc('#1B1030'))
    bolt = [(9, 2), (8, 3), (7, 4), (6, 5), (5, 7), (7, 7), (8, 7), (7, 9), (6, 10), (6, 11), (5, 12), (4, 13)]
    for x, y in bolt:
        side.px(x, y, hexc('#FFF2A0'))
        side.px(x + 1, y, hexc('#FFC93A'))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        side.px(x, y, CYAN)
    for y in range(4, 12, 2):
        side.px(11, y, VIOLET)
        side.px(12, y, CYAN)
    save_block('creative_generator_side', side)
    top = base_metal(rng_for('cg_top'), DARK2, 0.05)
    bevel(top, MID, BLACK)
    top.frame(2, 2, 13, 13, shade(VIOLET, 0.7))
    top.frame(4, 4, 11, 11, VIOLET)
    top.rect(5, 5, 10, 10, hexc('#241442'))
    top.rect(6, 6, 9, 9, CYAN)
    top.rect(7, 7, 8, 8, WHITE)
    save_block('creative_generator_top', top)


def fluid_textures():
    # glass pipe wall: mostly transparent with steel seams and a subtle sheen
    for mk, seam in ((1, LIGHT), (2, hexc('#E0B84A'))):
        img = Img(fill=(170, 200, 220, 70))
        for x in range(16):
            img.px(x, 0, seam)
            img.px(x, 15, seam)
        for y in range(1, 15):
            img.px(3, y, (230, 240, 255, 110))
            img.px(4, y, (230, 240, 255, 60))
        save_block('pipeline_mk%d_glass' % mk, img)
        ring = base_metal(rng_for('ring%d' % mk), seam, 0.05)
        ring.frame(0, 0, 15, 15, shade(seam, 0.6))
        ring.rect(5, 5, 10, 10, DARK)
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            ring.px(x, y, DARK2)
        save_block('pipeline_mk%d_ring' % mk, ring)
    # pipeline pump
    side = base_metal(rng_for('pumpside'), ORANGE, 0.05)
    bevel(side, ORANGE_L, shade(ORANGE, 0.6))
    side.rect(3, 3, 12, 12, DARK2)
    for y in range(4, 12, 2):
        side.line(4, y, 11, y, MID)
    side.rect(6, 6, 9, 9, BLUE)
    save_block('pipeline_pump_side', side)
    for name, col in (('pipeline_pump_out', ORANGE_L), ('pipeline_pump_in', hexc('#6FD0FF'))):
        img = base_metal(rng_for(name), MID, 0.05)
        bevel(img, LIGHT, DARK)
        img.rect(4, 4, 11, 11, BLACK)
        img.frame(3, 3, 12, 12, col)
        save_block(name, img)
    # zipline item: a handle with a pulley wheel
    z = Img()
    for i in range(7):
        z.px(4 + i, 11 - i // 2, DARK2)
    z.rect(3, 9, 5, 14, ORANGE)
    z.rect(3, 9, 3, 14, ORANGE_L)
    import math
    for a in range(0, 360, 20):
        z.px(int(round(11 + 3 * math.cos(math.radians(a)))), int(round(5 + 3 * math.sin(math.radians(a)))), STEEL)
    z.rect(10, 4, 12, 6, MID)
    z.px(11, 5, BLACK)
    z.line(0, 2, 15, 2, BLACK)
    z.outline(BLACK)
    save_item('zipline', z)


def icon():
    img = Image.new('RGBA', (128, 128), DARK)
    small = Img(fill=DARK)
    small.rect(1, 1, 14, 14, ORANGE)
    small.rect(3, 3, 12, 12, DARK2)
    small.rect(5, 5, 10, 6, ORANGE_L)
    small.rect(5, 5, 6, 10, ORANGE_L)
    small.rect(5, 8, 9, 8, ORANGE_L)
    img = small.im.resize((128, 128), Image.NEAREST)
    img.save(os.path.join(ROOT, '..', 'icon.png'))


if __name__ == '__main__':
    items()
    blocks()
    building_materials()
    creative_generator()
    fluid_textures()
    icon()
    print('textures generated')
