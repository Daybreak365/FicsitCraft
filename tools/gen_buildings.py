#!/usr/bin/env python3
"""
FICSIT Craft building art pipeline (run from the project root, after gen_json.py).

For every building this writes
  * one texture PER FACE of every box (textures/block/bld/<building>/...), painted at the face's real size
    and stretched once over it - no block-sized tiling, so no repeated logos or seams
  * the geometry (assets/ficsitcraft/buildings/<building>.json) that the in-world renderer + hologram load
  * a 3D miniature item model (models/item/<building>.json) built from the same boxes and textures

Geometry uses footprint-local block units: X = right (0..W), Y = up, Z = forward from the back edge (0..D);
the front face (Z = D) is the output side, the back face usually carries the control console.
"""
import json
import os
import shutil
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
from bldart import *  # noqa: E402,F401,F403

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft')
TEX = os.path.join(ROOT, 'textures', 'block', 'bld')
GEO = os.path.join(ROOT, 'buildings')
ITEM_MODELS = os.path.join(ROOT, 'models', 'item')

FACES = ('down', 'up', 'front', 'back', 'left', 'right')
INSULATOR = 'ficsitcraft:block/power_pole_insulator'


# ----------------------------------------------------------------------------------------------- face spec shortcuts
def OR(*d):
    return F(paint(ORANGE), *d)


def WH(*d):
    return F(paint(WHITE), *d)


def GN(*d):
    return F(paint(GUN), *d)


def DK(*d):
    return F(paint(DARK2), *d)


def ST(*d):
    return F(brushed(STEEL), *d)


def YL(*d):
    return F(paint(YELLOW), *d)


FLOOR = F(floor())
HAZ = F(hazard())
GRATE = F(grating())
UNDER = F(solid(DARK))
STRUT = F(strut())
STRUT_PLAIN = F(strut(band=None))


class Building:
    def __init__(self, store, name, w, d, h, ax, az):
        self.store = store
        self.name, self.w, self.d, self.h, self.ax, self.az = name, w, d, h, ax, az
        self.parts = []

    def box(self, x0, y0, z0, x1, y1, z1, all=None, flags=(), tint=None, active=None, **faces):
        spec = {f: all for f in FACES}
        if 'down' not in faces and all is not None:
            spec['down'] = UNDER
        side = faces.pop('sides', None)
        if side is not None:
            for f in ('front', 'back', 'left', 'right'):
                spec[f] = side
        spec.update(faces)
        idx = len(self.parts)
        dims = {'front': (x1 - x0, y1 - y0), 'back': (x1 - x0, y1 - y0), 'left': (z1 - z0, y1 - y0),
                'right': (z1 - z0, y1 - y0), 'up': (x1 - x0, z1 - z0), 'down': (x1 - x0, z1 - z0)}

        def render(face, fs, tag):
            if isinstance(fs, str):
                return fs
            if fs is None:
                fs = GN()
            seed = zlib.crc32(('%s/%d/%s/%s' % (self.name, idx, face, tag)).encode())
            img = fs.paint(dims[face][0], dims[face][1], face, seed)
            return self.store.save(img, '%s/p%02d_%s%s' % (self.name, idx, face, tag))

        tex = {f: render(f, spec[f], '') for f in FACES}
        part = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'tex': tex}
        if active:
            part['active'] = {f: render(f, fs, '_on') for f, fs in active.items()}
        if flags:
            part['flags'] = list(flags)
        if tint:
            part['tint'] = tint
        self.parts.append(part)
        return self

    def export(self):
        with open(os.path.join(GEO, self.name + '.json'), 'w') as f:
            json.dump({'footprint': [self.w, self.d, self.h, self.ax, self.az], 'parts': self.parts}, f, indent=1)
        self.export_item_model()

    def export_item_model(self):
        """Miniature of the building as a vanilla item model: same boxes, same per-face textures, scaled to 16^3."""
        xs = [p['from'][0] for p in self.parts] + [p['to'][0] for p in self.parts]
        ys = [p['from'][1] for p in self.parts] + [p['to'][1] for p in self.parts]
        zs = [p['from'][2] for p in self.parts] + [p['to'][2] for p in self.parts]
        minx, maxx, miny, maxy, minz, maxz = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
        s = 16.0 / max(maxx - minx, maxy - miny, maxz - minz)
        ox = (16 - (maxx - minx) * s) / 2
        oz = (16 - (maxz - minz) * s) / 2
        textures, ids = {}, {}

        def ref(tid):
            if tid not in ids:
                ids[tid] = 't%d' % len(ids)
                textures[ids[tid]] = tid
            return '#' + ids[tid]

        elements = []
        for p in self.parts:
            (x0, y0, z0), (x1, y1, z1) = p['from'], p['to']
            # item model space: x east, z south; the building front (local Z max) faces north (z = 0),
            # exactly like the in-world render layout, so the per-face textures line up the same way.
            e = {'from': [ox + (x0 - minx) * s, (y0 - miny) * s, oz + (maxz - z1) * s],
                 'to': [ox + (x1 - minx) * s, (y1 - miny) * s, oz + (maxz - z0) * s]}
            if min(e['to'][i] - e['from'][i] for i in range(3)) < 0.05:
                continue
            e['from'] = [round(v, 3) for v in e['from']]
            e['to'] = [round(v, 3) for v in e['to']]
            t = p['tex']
            full = [0, 0, 16, 16]
            e['faces'] = {
                'north': {'texture': ref(t['front']), 'uv': full},
                'south': {'texture': ref(t['back']), 'uv': full},
                'west': {'texture': ref(t['left']), 'uv': full},
                'east': {'texture': ref(t['right']), 'uv': full},
                'up': {'texture': ref(t['up']), 'uv': full},
                'down': {'texture': ref(t['down']), 'uv': [0, 16, 16, 0]},
            }
            elements.append(e)
        textures['particle'] = self.parts[1]['tex']['front'] if len(self.parts) > 1 else textures['t0']
        model = {
            'parent': 'minecraft:block/block',
            'textures': textures,
            'elements': elements,
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.3, 0.3, 0.3]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.55, 0.55, 0.55]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
            }
        }
        with open(os.path.join(ITEM_MODELS, self.name + '.json'), 'w') as f:
            json.dump(model, f, indent=1)


# ------------------------------------------------------------------------------------------------ shared sub-parts
def base_plate(b, y=0.2, deco=()):
    b.box(0, 0, 0, b.w, y, b.d, all=HAZ, up=F(floor(), *deco))


def ports(b, inp=True, out=True, y1=1.0):
    if out:
        b.box(b.ax + 0.1, 0.2, b.d - 0.22, b.ax + 0.9, y1, b.d + 0.02, all=GN(), up=GRATE, front=F(port(True)))
    if inp:
        b.box(b.ax + 0.1, 0.2, -0.02, b.ax + 0.9, y1, 0.22, all=GN(), up=GRATE, back=F(port(False)))


def connector(b, roof, x=None, z=None):
    x = b.w / 2 if x is None else x
    z = b.d / 2 if z is None else z
    b.box(x - 0.14, roof, z - 0.14, x + 0.14, roof + 0.18, z + 0.14, all=F(solid(GUN)), up=F(solid(STEEL_D)))
    b.box(x - 0.07, roof + 0.18, z - 0.07, x + 0.07, roof + 0.32, z + 0.07, all=INSULATOR)


def glow_window(b, x0, y0, z0, x1, y1, z1, face, frame=GN()):
    b.box(x0, y0, z0, x1, y1, z1, all=frame, **{face: F(glow(False))}, flags=['glow'], active={face: F(glow(True))})


# =================================================================================================================
def smelter(st):
    b = Building(st, 'smelter', 2, 3, 3, 0, 0)
    base_plate(b)
    body_side = lambda flip: OR(
        text(0.5, 0.1, 'SMELTER', WHITE, 0.16),
        vent(0.2 if flip else 0.8, 0.88, 0.45, 0.2),
        warn(0.86 if flip else 0.14, 0.1, 0.22),
        label(0.8 if flip else 0.2, 0.88, 0.3))
    b.box(0.1, 0.2, 0.3, 1.9, 2.1, 2.75,
          all=OR(),
          up=GN(vent(0.3, 0.3, 0.5, 0.5), hatch(0.72, 0.72, 0.4, 0.4)),
          front=OR(logo(0.5, 0.14, 1.3), vent(0.78, 0.62, 0.55, 0.35), label(0.8, 0.88, 0.28), text(0.5, 0.36, 'OUT', ORANGE_L, 0.1)),
          back=OR(console(0.5, 0.42, 1.4, 1.0, on=False, kind='bars', seed=1), text(0.5, 0.06, 'CONTROL', WHITE, 0.1), leds(0.5, 0.9, 3, False)),
          left=body_side(False), right=body_side(True),
          active={'back': OR(console(0.5, 0.42, 1.4, 1.0, on=True, kind='bars', seed=1), text(0.5, 0.06, 'CONTROL', WHITE, 0.1), leds(0.5, 0.9, 3, True))})
    b.box(0.05, 0.2, 0.25, 0.15, 0.5, 2.8, all=GRATE)
    b.box(1.85, 0.2, 0.25, 1.95, 0.5, 2.8, all=GRATE)
    # heat shields (ribbed, soot-stained) + furnace windows on both sides
    shield = F(bricks(C('#5B5049'), soot=True))
    for x0, x1, face in ((1.9, 1.98, 'right'), (0.02, 0.1, 'left')):
        glow_window(b, x0, 0.6, 0.7, x1, 1.7, 2.3, face, frame=shield)
    # hopper + chimney
    b.box(0.2, 2.1, 1.5, 0.95, 2.35, 2.55, all=GN(hband(0.0, 0.35)), up=GRATE)
    b.box(0.3, 2.35, 1.6, 0.85, 2.6, 2.45, all=OR(text(0.5, 0.5, 'ORE', DARK, 0.1)), up=F(vents()))
    b.box(1.1, 2.1, 0.45, 1.75, 3.0, 1.1, all=F(cylinder(STEEL_D, 'y', soot=1.0, cap='glowhole')))
    b.box(1.02, 2.8, 0.37, 1.83, 2.92, 1.18, all=GN(), up=GN())
    ports(b)
    connector(b, 2.1, x=0.6, z=0.8)
    b.export()


def constructor(st):
    b = Building(st, 'constructor', 3, 3, 3, 1, 0)
    base_plate(b)
    lower = lambda *d: OR(hband(0.0, 0.1), *d)
    b.box(0.15, 0.2, 0.15, 2.85, 1.35, 2.85,
          all=lower(),
          up=F(floor(), ring(0.5, 0.5, 0.75, YELLOW, 0.04)),
          left=lower(text(0.5, 0.35, 'CONSTRUCTOR', WHITE, 0.14), vent(0.25, 0.72, 0.6, 0.3), vent(0.75, 0.72, 0.6, 0.3)),
          right=lower(logo(0.5, 0.4, 1.2), hatch(0.18, 0.66, 0.35, 0.5), label(0.84, 0.72, 0.3)),
          front=lower(vent(0.15, 0.6, 0.4, 0.5), vent(0.85, 0.6, 0.4, 0.5), text(0.5, 0.22, 'OUTPUT', ORANGE_L, 0.09)),
          back=lower(console(0.28, 0.58, 1.2, 0.8, on=False, kind='graph', seed=2), vent(0.82, 0.6, 0.5, 0.5)),
          active={'back': lower(console(0.28, 0.58, 1.2, 0.8, on=True, kind='graph', seed=2), vent(0.82, 0.6, 0.5, 0.5))})
    hous = lambda *d: GN(band(0.9, 1.0, ORANGE), *d)
    b.box(0.55, 1.35, 0.5, 2.45, 2.35, 2.5,
          all=hous(),
          up=GN(hatch(0.5, 0.5, 0.9, 0.9, handle=False), bolts_edge()),
          left=hous(logo(0.5, 0.35, 1.3, dark=False), vent(0.5, 0.72, 1.2, 0.2)),
          right=hous(text(0.5, 0.3, 'PRESS', YELLOW, 0.16), warn(0.5, 0.62, 0.3)),
          back=hous(vent(0.5, 0.5, 1.4, 0.55)))
    b.box(0.75, 1.55, 2.5, 2.25, 2.15, 2.56, all=GN(), front=F(screen('graph', seed=3)), flags=['alwaysGlow'])
    b.box(1.2, 2.35, 1.2, 1.8, 2.95, 1.8, all=F(cylinder(STEEL, 'y')), sides=F(arm(YELLOW)), flags=['spin'])
    b.box(1.05, 2.35, 1.05, 1.95, 2.45, 1.95, all=GN(hband(0, 1)))
    for x in (0.2, 2.55):
        for z in (0.2, 2.55):
            b.box(x, 1.35, z, x + 0.25, 2.7, z + 0.25, all=STRUT)
    b.box(0.2, 2.7, 0.2, 2.8, 2.82, 0.45, all=GN(), up=GRATE)
    b.box(0.2, 2.7, 2.55, 2.8, 2.82, 2.8, all=GN(), up=GRATE)
    glow_window(b, 2.85, 0.45, 0.8, 2.92, 1.15, 2.2, 'right')
    ports(b)
    connector(b, 2.35, x=0.7, z=1.0)
    b.export()


def assembler(st):
    b = Building(st, 'assembler', 3, 5, 4, 1, 0)
    base_plate(b)
    low = lambda *d: OR(hband(0.0, 0.12), *d)
    b.box(0.2, 0.2, 0.2, 2.8, 1.15, 4.8,
          all=low(),
          up=F(solid(C('#2B2E33')), custom(_work_surface)),
          left=low(text(0.3, 0.45, 'ASSEMBLER', WHITE, 0.15), vent(0.72, 0.5, 0.9, 0.4), label(0.92, 0.55, 0.25)),
          right=low(logo(0.25, 0.45, 1.3), hatch(0.62, 0.55, 0.5, 0.6), vent(0.86, 0.55, 0.5, 0.4)),
          front=low(text(0.5, 0.35, 'OUTPUT', ORANGE_L, 0.1), vent(0.15, 0.6, 0.4, 0.4), vent(0.85, 0.6, 0.4, 0.4)),
          back=low(console(0.25, 0.55, 1.1, 0.75, on=False, kind='bars', seed=4), vent(0.8, 0.55, 0.6, 0.5)),
          active={'back': low(console(0.25, 0.55, 1.1, 0.75, on=True, kind='bars', seed=4), vent(0.8, 0.55, 0.6, 0.5))})
    for z in (0.3, 4.4):
        for x in (0.2, 2.5):
            b.box(x, 1.15, z, x + 0.3, 3.45, z + 0.3, all=F(strut(C('#8C939B'))))
    roof = lambda *d: OR(band(0.0, 0.18, GUN), *d)
    b.box(0.15, 3.45, 0.25, 2.85, 3.85, 4.75,
          all=roof(),
          up=GN(vent(0.25, 0.2, 0.35, 0.8), vent(0.25, 0.8, 0.35, 0.8), vent(0.75, 0.2, 0.35, 0.8),
                vent(0.75, 0.8, 0.35, 0.8), hband(0.46, 0.54, 0.1)),
          left=roof(logo(0.5, 0.6, 1.6)), right=roof(text(0.5, 0.6, 'ASSEMBLER', WHITE, 0.14)),
          front=roof(text(0.5, 0.6, 'FICSIT', WHITE, 0.12)), back=roof(leds(0.5, 0.55, 4)))
    b.box(0.2, 3.3, 0.35, 2.8, 3.45, 4.65, all=F(strut(C('#6E757D'), band=None)))
    # robot arms (spin while working)
    b.box(0.75, 1.15, 1.35, 1.25, 1.35, 1.85, all=GN(hband(0, 1)))
    b.box(0.85, 1.35, 1.45, 1.15, 2.7, 1.75, all=F(arm()), flags=['spin'])
    b.box(0.55, 2.55, 1.05, 1.45, 2.8, 2.25, all=F(arm()), flags=['spin'])
    b.box(1.75, 1.15, 2.95, 2.25, 1.35, 3.45, all=GN(hband(0, 1)))
    b.box(1.85, 1.35, 3.05, 2.15, 2.5, 3.35, all=F(arm()), flags=['spin'])
    b.box(1.55, 2.35, 2.65, 2.45, 2.6, 3.85, all=F(arm()), flags=['spin'])
    b.box(0.9, 3.85, 2.2, 2.1, 4.0, 2.8, all=GN(), up=F(glow(True)), flags=['glow'])
    ports(b)
    connector(b, 3.85, x=0.6, z=4.2)
    b.export()


def _work_surface(cv):
    """Assembler table top: rubber mat with a yellow safety border and two jig plates."""
    m = max(2, int(0.08 * cv.sx))
    cv.frame(m, m, cv.W - m, cv.H - m, YELLOW, max(1, m // 2))
    u, v = cv.ub(), cv.vb()
    grid = ((u % 0.25) < 0.02) | ((v % 0.25) < 0.02)
    cv.a[:] = np.where(grid[..., None], cv.a * 1.25, cv.a)
    for cy in (0.3, 0.68):
        x0, y0, x1, y1 = cv.px_rect(0.5, cy, 1.1, 0.8)
        cv.rect(x0, y0, x1, y1, STEEL)
        cv.bevel(x0, y0, x1, y1)
        cv.frame(x0 + 2, y0 + 2, x1 - 2, y1 - 2, STEEL_D)


def foundry(st):
    b = Building(st, 'foundry', 3, 3, 3, 1, 0)
    base_plate(b)
    base = lambda *d: GN(band(0.0, 0.12, ORANGE), *d)
    b.box(0.15, 0.2, 0.15, 2.85, 0.95, 2.85,
          all=base(),
          up=F(floor(C('#6A6F75')), ring(0.28, 0.5, 0.55, C('#8A3A20'), 0.05), ring(0.72, 0.5, 0.55, C('#8A3A20'), 0.05)),
          left=base(text(0.5, 0.5, 'FOUNDRY', ORANGE_L, 0.18), vent(0.12, 0.55, 0.25, 0.4)),
          right=base(logo(0.45, 0.52, 1.3), warn(0.88, 0.52, 0.3)),
          front=base(vent(0.18, 0.55, 0.55, 0.4), vent(0.82, 0.55, 0.55, 0.4)),
          back=base(console(0.28, 0.55, 1.1, 0.6, on=False, kind='graph', seed=5), vent(0.8, 0.55, 0.6, 0.45)),
          active={'back': base(console(0.28, 0.55, 1.1, 0.6, on=True, kind='graph', seed=5), vent(0.8, 0.55, 0.6, 0.45))})
    for i, x0 in enumerate((0.3, 1.65)):
        brick = lambda *d: F(bricks(), band(0.3, 0.36, STEEL_D), band(0.72, 0.78, STEEL_D), *d)
        b.box(x0, 0.95, 0.65, x0 + 1.05, 2.55, 2.35,
              all=brick(),
              up=F(cylinder(C('#4A4E54'), 'y', cap='glowhole')),
              left=brick(text(0.5, 0.15, 'HOT', YELLOW, 0.14, plate=BLACK)) if i == 0 else brick(),
              right=brick(text(0.5, 0.15, 'HOT', YELLOW, 0.14, plate=BLACK)) if i == 1 else brick(),
              back=brick(warn(0.5, 0.15, 0.3)))
        b.box(x0 - 0.05, 2.35, 0.6, x0 + 1.1, 2.55, 2.4, all=GN(bolts_edge(0.25, 0.15)), up=F(cylinder(C('#4A4E54'), 'y', cap='glowhole')))
        b.box(x0 + 0.2, 1.25, 2.35, x0 + 0.85, 2.05, 2.41, all=GN(), front=F(glow(False)), flags=['glow'],
              active={'front': F(glow(True))})
    b.box(1.3, 0.95, 1.15, 1.7, 3.0, 1.55, all=F(cylinder(STEEL_D, 'y', soot=1.2, cap='vent')))
    ports(b)
    connector(b, 0.95, x=1.5, z=0.35)
    b.export()


def manufacturer(st):
    b = Building(st, 'manufacturer', 5, 6, 4, 2, 0)
    base_plate(b)
    hall = lambda *d: WH(band(0.8, 0.92, ORANGE), band(0.92, 1.0, GUN), *d)
    b.box(0.2, 0.2, 0.2, 4.8, 2.3, 5.8,
          all=hall(),
          up=F(floor(C('#8C939A')), hband(0.0, 0.03), hband(0.97, 1.0)),
          left=hall(logo(0.2, 0.2, 1.6), text(0.62, 0.2, 'MANUFACTURER', DARK2, 0.2), door(0.88, 0.55, 1.2),
                    vent(0.45, 0.58, 1.0, 0.45), vent(0.66, 0.58, 1.0, 0.45)),
          right=hall(text(0.35, 0.2, 'FICSIT INC.', DARK2, 0.2), hatch(0.2, 0.55, 1.0, 0.8, label='SERVICE'),
                     vent(0.55, 0.55, 1.2, 0.5), label(0.84, 0.5, 0.45), warn(0.84, 0.25, 0.3)),
          front=hall(text(0.5, 0.12, 'OUTPUT', ORANGE, 0.14), vent(0.18, 0.55, 1.0, 0.6), vent(0.82, 0.55, 1.0, 0.6)),
          back=hall(console(0.3, 0.45, 1.8, 1.1, on=False, kind='graph', seed=6), vent(0.78, 0.45, 1.3, 0.8)),
          active={'back': hall(console(0.3, 0.45, 1.8, 1.1, on=True, kind='graph', seed=6), vent(0.78, 0.45, 1.3, 0.8))})
    up = lambda *d: GN(band(0.0, 0.08, ORANGE), *d)
    b.box(0.7, 2.3, 0.7, 4.3, 3.45, 5.3,
          all=up(),
          up=ST(custom(_roof_hatches)),
          left=up(vent(0.2, 0.55, 0.9, 0.5), vent(0.5, 0.55, 0.9, 0.5), vent(0.8, 0.55, 0.9, 0.5)),
          right=up(logo(0.5, 0.5, 1.8)),
          back=up(vent(0.5, 0.5, 2.6, 0.5)),
          front=up(text(0.5, 0.2, 'MANUFACTURER', WHITE, 0.14)))
    for x0, x1, face in ((0.13, 0.2, 'left'), (4.8, 4.87, 'right')):
        b.box(x0, 0.7, 1.0, x1, 1.0, 5.0, all=GN(), **{face: F(solid(GUN), lamp_strip(0.2, 0.8))}, flags=['glow'])
    b.box(1.2, 2.5, 5.3, 3.8, 3.2, 5.36, all=GN(), front=F(screen('bars', seed=7)), flags=['alwaysGlow'])
    for x in (0.95, 3.55):
        b.box(x, 3.45, 1.2, x + 0.5, 3.95, 1.7, all=F(cylinder(STEEL, 'y', rings=0)), up=F(fan_top()))
    for x in (0.3, 4.4):
        for z in (0.3, 5.4):
            b.box(x, 2.3, z, x + 0.3, 3.9, z + 0.3, all=STRUT)
    ports(b)
    connector(b, 3.45, x=2.5, z=3.0)
    b.export()


def _roof_hatches(cv):
    for cx, cy in ((0.3, 0.3), (0.7, 0.3), (0.3, 0.7), (0.7, 0.7)):
        hatch(cx, cy, 0.9, 0.9, handle=False)(cv)
    hband(0.48, 0.52, 0.1)(cv)


def miner(st, mk):
    col = ORANGE if mk == 1 else YELLOW
    name = 'miner_mk%d' % mk
    b = Building(st, name, 3, 3, 5, 1, 1)
    for x in (0.05, 2.55):
        for z in (0.05, 2.55):
            b.box(x, 0, z, x + 0.4, 1.3, z + 0.4, all=F(strut(C('#7E858D'))), up=GN())
    b.box(0, 1.3, 0, 3, 1.7, 3, all=HAZ, up=F(floor(), ring(0.5, 0.5, 0.95, YELLOW, 0.04)))
    tw = lambda *d: F(paint(col), band(0.0, 0.06, GUN), *d)
    b.box(0.7, 1.7, 0.7, 2.3, 4.2, 2.3,
          all=tw(),
          up=GN(vent(0.5, 0.5, 1.2, 1.2)),
          left=tw(text(0.5, 0.12, 'MINER', WHITE, 0.16), text(0.5, 0.26, 'MK.%d' % mk, DARK, 0.2, plate=WHITE),
                  vent(0.5, 0.62, 1.1, 0.7), hband(0.9, 0.96)),
          right=tw(logo(0.5, 0.14, 1.3), hatch(0.5, 0.52, 0.9, 0.9, label='DRILL'), hband(0.9, 0.96)),
          front=tw(text(0.5, 0.12, 'MK.%d' % mk, WHITE, 0.2), vent(0.5, 0.5, 1.1, 0.5), warn(0.5, 0.8, 0.35)),
          back=tw(console(0.5, 0.35, 1.3, 0.9, on=False, kind='bars', seed=8 + mk), hband(0.9, 0.96)),
          active={'back': tw(console(0.5, 0.35, 1.3, 0.9, on=True, kind='bars', seed=8 + mk), hband(0.9, 0.96))})
    b.box(0.55, 4.2, 0.55, 2.45, 4.7, 2.45, all=GN(hband(0.0, 0.25)), up=F(fan_top()))
    b.box(2.3, 2.2, 1.2, 2.36, 3.4, 1.8, all=GN(), right=F(glow(False)), flags=['glow'], active={'right': F(glow(True))})
    b.box(1.3, 0.3, 1.3, 1.7, 1.3, 1.7, all=F(drill()), flags=['spin'])
    b.box(1.05, 0.0, 1.05, 1.95, 0.35, 1.95, all=F(drill()), flags=['spin'])
    b.box(0.3, 1.7, 0.3, 0.55, 3.5, 0.55, all=STRUT)
    b.box(2.45, 1.7, 2.45, 2.7, 3.5, 2.7, all=STRUT)
    b.box(1.15, 0.15, 2.45, 1.85, 1.0, 3.02, all=GN(), up=GRATE, front=F(port(True)))
    connector(b, 4.7)
    b.export()


def biomass_burner(st):
    green = C('#6B8E3D')
    b = Building(st, 'biomass_burner', 3, 3, 3, 1, 0)
    base_plate(b)
    body = lambda *d: F(paint(green), band(0.0, 0.08, GUN), band(0.9, 1.0, GUN), *d)
    b.box(0.3, 0.2, 0.7, 2.7, 2.0, 2.3, all=body(), up=GN(),
          left=body(text(0.5, 0.2, 'BIOMASS', WHITE, 0.17), vent(0.5, 0.6, 1.2, 0.5)),
          right=body(logo(0.5, 0.22, 1.3), hatch(0.5, 0.62, 0.9, 0.7, label='ASH')))
    b.box(0.7, 0.2, 0.3, 2.3, 2.0, 2.7, all=body(), up=GN(),
          front=body(text(0.5, 0.12, 'BURNER', WHITE, 0.15), leds(0.5, 0.85, 3)),
          back=body(console(0.5, 0.45, 1.3, 1.0, on=False, kind='graph', seed=10)),
          active={'back': body(console(0.5, 0.45, 1.3, 1.0, on=True, kind='graph', seed=10))})
    b.box(0.5, 0.2, 0.5, 2.5, 2.0, 2.5, all=body(), up=GN(vent(0.25, 0.25, 0.5, 0.5), vent(0.75, 0.75, 0.5, 0.5),
                                                            hatch(0.75, 0.25, 0.5, 0.5, handle=False)))
    b.box(0.9, 0.5, 2.7, 2.1, 1.5, 2.76, all=GN(), front=F(glow(False)), flags=['glow'], active={'front': F(glow(True))})
    b.box(0.25, 1.95, 0.25, 2.75, 2.1, 2.75, all=GN(hband(0, 1)), up=F(floor(C('#666B71'))))
    b.box(1.15, 2.0, 1.15, 1.85, 3.0, 1.85, all=F(cylinder(STEEL_D, 'y', soot=1.0, cap='vent')))
    ports(b, out=False)
    connector(b, 2.1, x=0.7, z=0.7)
    b.export()


def coal_generator(st):
    b = Building(st, 'coal_generator', 3, 5, 6, 1, 0)
    base_plate(b)
    body = lambda *d: GN(band(0.0, 0.08, ORANGE), hband(0.92, 1.0), *d)
    b.box(0.2, 0.2, 0.2, 2.8, 2.6, 3.4, all=body(),
          up=F(floor(C('#5F646A')), hatch(0.3, 0.75, 0.5, 0.5), vent(0.7, 0.8, 0.6, 0.3)),
          left=body(text(0.5, 0.12, 'COAL GENERATOR', ORANGE_L, 0.14), vent(0.15, 0.55, 0.35, 0.9), vent(0.85, 0.55, 0.35, 0.9)),
          right=body(logo(0.5, 0.12, 1.5), vent(0.15, 0.55, 0.35, 0.9), label(0.87, 0.35, 0.35), gauge(0.87, 0.7, 0.12)),
          front=body(text(0.5, 0.2, '75 MW', WHITE, 0.2, plate=DARK), warn(0.5, 0.55, 0.45), vent(0.5, 0.82, 1.6, 0.25)),
          back=body(console(0.5, 0.45, 1.8, 1.2, on=False, kind='graph', seed=11)),
          active={'back': body(console(0.5, 0.45, 1.8, 1.2, on=True, kind='graph', seed=11))})
    tank = lambda *d: WH(band(0.18, 0.3, BLUE), band(0.92, 1.0, GUN), *d)
    b.box(0.4, 0.2, 3.5, 2.6, 1.9, 4.8, all=tank(),
          up=WH(ring(0.5, 0.5, 0.45, BLUE, 0.04), hatch(0.5, 0.5, 0.45, 0.45, handle=False)),
          left=tank(text(0.5, 0.55, 'H2O', BLUE_D, 0.22), gauge(0.85, 0.6, 0.1, 0.8)),
          right=tank(text(0.5, 0.55, 'WATER', BLUE_D, 0.18)),
          front=tank(text(0.5, 0.55, 'WATER IN', BLUE_D, 0.14), vpipe(0.15, 0.1, BLUE, 0.3, 1.0)))
    b.box(0.85, 2.6, 0.6, 2.15, 6.0, 1.9, all=F(bricks(C('#7C4131'))), up=F(cylinder(C('#3B3B3B'), 'y', cap='vent')),
          front=F(bricks(C('#7C4131')), band(0.62, 0.66, STEEL_D), text(0.5, 0.9, 'COAL', WHITE, 0.16)))
    for y in (3.6, 5.0):
        b.box(0.78, y, 0.53, 2.22, y + 0.2, 1.97, all=GN(bolts_edge(0.25, 0.2)), up=GN())
    for x0, x1, face in ((0.13, 0.2, 'left'), (2.8, 2.87, 'right')):
        glow_window(b, x0, 0.8, 0.8, x1, 2.0, 2.8, face)
    b.box(2.2, 2.6, 2.2, 2.6, 3.1, 3.2, all=F(cylinder(STEEL, 'z', rings=0)))
    b.box(1.3, 1.9, 3.9, 1.7, 2.6, 4.3, all=F(cylinder(BLUE, 'y', rings=0)))
    ports(b, out=False)
    connector(b, 2.6, x=2.3, z=0.6)
    b.export()


def water_extractor(st):
    b = Building(st, 'water_extractor', 3, 3, 3, 1, 1)
    for x in (0.1, 2.55):
        for z in (0.1, 2.55):
            b.box(x, 0, z, x + 0.35, 1.1, z + 0.35, all=F(strut(C('#7E858D'))), up=GN())
    b.box(0, 1.1, 0, 3, 1.4, 3, all=HAZ, up=F(floor(), ring(0.5, 0.5, 1.05, BLUE, 0.05)))
    b.box(1.2, -0.6, 1.2, 1.8, 1.1, 1.8, all=F(cylinder(BLUE_D, 'y')))
    b.box(1.0, -0.8, 1.0, 2.0, -0.5, 2.0, all=F(drill()), flags=['spin'])
    body = lambda *d: WH(band(0.0, 0.12, BLUE), band(0.88, 1.0, GUN), *d)
    b.box(0.5, 1.4, 0.5, 2.5, 2.4, 2.5, all=body(),
          up=WH(hband(0, 0.05), bolts_edge()),
          left=body(text(0.5, 0.4, 'WATER', BLUE_D, 0.16), text(0.5, 0.65, 'EXTRACTOR', BLUE_D, 0.11)),
          right=body(logo(0.5, 0.45, 1.3)),
          front=body(text(0.5, 0.4, '120 M3', DARK2, 0.14), gauge(0.5, 0.7, 0.1, 0.7)),
          back=body(console(0.5, 0.5, 1.4, 0.7, on=True, kind='text', seed=12)))
    b.box(0.9, 2.4, 0.9, 2.1, 2.9, 2.1, all=F(paint(BLUE), vent(0.5, 0.5, 0.9, 0.3)), up=F(fan_top(BLUE_D, C('#20557A'))))
    b.box(1.3, 2.9, 1.3, 1.7, 3.0, 1.7, all=GN(), flags=['spin'])
    for (x0, z0, x1, z1, face) in ((2.5, 1.15, 3.0, 1.85, 'right'), (0.0, 1.15, 0.5, 1.85, 'left'),
                                   (1.15, 2.5, 1.85, 3.0, 'front')):
        axis = 'x' if face in ('left', 'right') else 'z'
        b.box(x0, 1.6, z0, x1, 2.3, z1, all=F(cylinder(BLUE, axis, rings=0)), **{face: F(port(False))})
    connector(b, 2.9, x=0.8, z=0.8)
    b.export()


def fluid_buffer(st):
    b = Building(st, 'fluid_buffer', 3, 3, 4, 1, 1)
    base_plate(b, 0.3)
    shell = lambda *d: F(cylinder(WHITE, 'y', rings=1), band(0.1, 0.16, BLUE), band(0.86, 0.92, BLUE), *d)
    cap = F(cylinder(OFFWHITE, 'y'), ring(0.5, 0.5, 0.55, YELLOW, 0.05))
    b.box(0.3, 0.3, 0.75, 2.7, 3.6, 2.25, all=shell(), up=cap,
          left=shell(text(0.5, 0.3, 'FLUID', BLUE_D, 0.16), text(0.5, 0.42, 'BUFFER', BLUE_D, 0.16)),
          right=shell(logo(0.5, 0.35, 1.2), text(0.5, 0.55, '400 M3', DARK2, 0.12)))
    b.box(0.75, 0.3, 0.3, 2.25, 3.6, 2.7, all=shell(), up=cap)
    b.box(0.55, 0.3, 0.55, 2.45, 3.6, 2.45, all=shell(), up=cap)
    b.box(0.25, 3.6, 0.25, 2.75, 3.85, 2.75, all=GN(hband(0, 1)), up=GN(ring(0.5, 0.5, 0.9, YELLOW, 0.05),
                                                                        custom(_heli_h)))
    b.box(1.2, 3.85, 1.2, 1.8, 4.0, 1.8, all=GN(), up=F(solid(STEEL_D)))
    b.box(1.2, 0.55, 2.68, 1.8, 3.4, 2.72, all=GN(), front=F(glass()), back=F(glass()))
    b.box(1.2, 0.55, 0.28, 1.8, 3.4, 0.32, all=GN(), front=F(glass()), back=F(glass()))
    b.box(2.7, 0.3, 1.35, 2.8, 3.6, 1.65, all=F(strut(STEEL, band=None)))
    b.box(0.2, 0.3, 1.35, 0.3, 3.6, 1.65, all=F(strut(STEEL, band=None)))
    b.export()


def _heli_h(cv):
    cv.text(0.5, 0.5, 'H', YELLOW, 0.8, shadow=False)


def hub(st):
    b = Building(st, 'hub', 3, 4, 3, 1, 0)
    b.box(0, 0, 0, 3, 0.3, 4, all=HAZ, up=F(floor(), text(0.5, 0.15, 'LANDING ZONE', YELLOW, 0.14)))
    term = lambda *d: DK(*d)
    b.box(0.9, 0.3, 0.2, 2.1, 1.3, 0.8, all=term(),
          back=term(vent(0.5, 0.5, 0.9, 0.5), text(0.5, 0.15, 'HUB-01', WHITE, 0.09)),
          front=term(label(0.5, 0.5, 0.5)),
          left=term(vent(0.5, 0.5, 0.35, 0.5)), right=term(vent(0.5, 0.5, 0.35, 0.5)),
          up=DK(leds(0.5, 0.5, 4)))
    b.box(0.8, 1.3, 0.25, 2.2, 2.1, 0.45, all=term(), back=F(screen('hub', seed=13)), flags=['alwaysGlow'])
    cargo = lambda *d: F(corrugated(C('#8F969E')), *d)
    b.box(0.3, 0.3, 1.4, 2.7, 2.5, 3.8, all=cargo(),
          up=OR(),
          left=cargo(band(0.62, 0.7, ORANGE, False), text(0.5, 0.32, 'HUB', WHITE, 0.45), text(0.5, 0.82, 'FICSIT CARGO', WHITE, 0.1)),
          right=cargo(band(0.62, 0.7, ORANGE, False), text(0.5, 0.32, 'HUB', WHITE, 0.45), label(0.8, 0.82, 0.35)),
          back=cargo(custom(_container_doors)),
          front=cargo(band(0.62, 0.7, ORANGE, False), text(0.5, 0.88, 'FICSIT', WHITE, 0.12)))
    b.box(0.2, 2.5, 1.3, 2.8, 2.75, 3.9, all=OR(bolts_edge(0.3, 0.3)),
          up=OR(logo(0.5, 0.5, 2.0), hband(0.0, 0.04), hband(0.96, 1.0)),
          front=OR(text(0.5, 0.5, 'FICSIT', WHITE, 0.14)), back=OR(text(0.5, 0.5, 'HUB', WHITE, 0.14)))
    b.box(0.5, 0.8, 1.34, 1.3, 1.9, 1.4, all=GN(), back=F(screen('text', seed=14)), flags=['alwaysGlow'])
    b.box(1.7, 0.8, 1.34, 2.5, 1.9, 1.4, all=GN(), back=F(screen('graph', seed=15)), flags=['alwaysGlow'])
    b.box(1.45, 2.75, 2.45, 1.55, 3.0, 2.55, all=F(solid(STEEL)))
    b.export()


def _container_doors(cv):
    """Cargo container back doors: two leaves with vertical lock bars and a handle each."""
    mid = cv.W // 2
    cv.rect(mid - 1, 0, mid + 1, cv.H, BLACK)
    for i, x in enumerate((int(cv.W * 0.2), int(cv.W * 0.35), int(cv.W * 0.65), int(cv.W * 0.8))):
        cv.rect(x - 1, int(cv.H * 0.08), x + 1, int(cv.H * 0.92), STEEL_L)
        cv.rect(x - 2, int(cv.H * 0.5), x + 3, int(cv.H * 0.5) + 2, STEEL_D)
    cv.text(0.5, 0.25, 'FICSIT', WHITE, 0.14)
    warn(0.5, 0.72, 0.28)(cv)


def craft_bench(st):
    b = Building(st, 'craft_bench', 2, 1, 2, 0, 0)
    b.box(0.05, 0.85, 0.05, 1.95, 1.05, 0.95, all=OR(hband(0.0, 0.2)),
          up=F(solid(C('#2E4A3A')), custom(_cutting_mat)),
          front=OR(text(0.5, 0.6, 'CRAFT BENCH', WHITE, 0.09)))
    for x in (0.1, 1.7):
        for z in (0.1, 0.7):
            b.box(x, 0, z, x + 0.2, 0.85, z + 0.2, all=STRUT_PLAIN)
    b.box(0.1, 1.05, 0.75, 1.9, 1.9, 0.9, all=OR(),
          front=F(screen('text', seed=16)), back=OR(logo(0.5, 0.4, 1.2), vent(0.5, 0.8, 1.2, 0.15)))
    b.box(0.4, 0.3, 0.2, 1.6, 0.4, 0.8, all=GN(), up=GRATE)
    b.export()


def _cutting_mat(cv):
    u, v = cv.ub(), cv.vb()
    grid = ((u % 0.125) < 0.02) | ((v % 0.125) < 0.02)
    cv.a[:] = np.where(grid[..., None], cv.a * 1.5, cv.a)
    # tools lying on the bench
    x0, y0, x1, y1 = cv.px_rect(0.25, 0.5, 0.35, 0.08)
    cv.rect(x0, y0, x1, y1, STEEL_L)
    cv.rect(x1 - 3, y0 - 2, x1, y1 + 2, STEEL)
    x0, y0, x1, y1 = cv.px_rect(0.7, 0.45, 0.3, 0.1)
    cv.rect(x0, y0, x1, y1, ORANGE)
    cv.rect(x1, y0 + 1, x1 + 6, y1 - 1, STEEL_L)


def storage_container(st):
    b = Building(st, 'storage_container', 2, 3, 2, 0, 0)
    b.box(0, 0, 0, 2, 0.15, 3, all=HAZ, up=FLOOR)
    box = lambda *d: F(corrugated(ORANGE), *d)
    b.box(0.05, 0.15, 0.05, 1.95, 1.8, 2.95, all=box(),
          up=OR(hatch(0.5, 0.5, 1.4, 2.4, handle=False), text(0.5, 0.5, 'FICSIT', WHITE, 0.18)),
          left=box(text(0.5, 0.4, 'STORAGE', WHITE, 0.2), label(0.8, 0.72, 0.4)),
          right=box(logo(0.5, 0.42, 1.8), text(0.5, 0.72, '48 SLOTS', DARK, 0.1)),
          front=box(custom(_container_doors)),
          back=box(text(0.5, 0.4, 'NO. 07', WHITE, 0.18), warn(0.5, 0.72, 0.3)))
    b.box(0.2, 1.8, 0.3, 1.8, 1.9, 2.7, all=GN(), up=GRATE)
    b.export()


def main():
    if os.path.isdir(TEX):
        shutil.rmtree(TEX)
    for d in (TEX, GEO, ITEM_MODELS):
        os.makedirs(d, exist_ok=True)
    st = Store(TEX)
    smelter(st)
    constructor(st)
    assembler(st)
    foundry(st)
    manufacturer(st)
    miner(st, 1)
    miner(st, 2)
    biomass_burner(st)
    coal_generator(st)
    water_extractor(st)
    fluid_buffer(st)
    hub(st)
    craft_bench(st)
    storage_container(st)
    print('building art generated: %d unique face textures' % st.count)


if __name__ == '__main__':
    main()
    import squarify  # block-atlas textures must be square (see squarify.py)
    squarify.main()
