#!/usr/bin/env python3
"""Generates blockstates, models, loot tables and tags for FICSIT Craft. Run from the project root."""
import json
import os

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'ficsitcraft')
D = os.path.join(RES, 'data')
NS = 'ficsitcraft'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def bs(name, obj):
    write(os.path.join(A, 'blockstates', name + '.json'), obj)


def bmodel(name, obj):
    write(os.path.join(A, 'models', 'block', name + '.json'), obj)


def imodel(name, obj):
    write(os.path.join(A, 'models', 'item', name + '.json'), obj)


def t(name):
    return NS + ':block/' + name


HROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

MACHINES = ['smelter', 'foundry', 'constructor', 'assembler', 'manufacturer',
            'miner_mk1', 'miner_mk2', 'biomass_burner', 'coal_generator']
NODES = ['iron', 'copper', 'limestone', 'coal', 'caterium', 'quartz']
PARTS = ['limestone', 'caterium_ore', 'raw_quartz', 'caterium_ingot', 'steel_ingot', 'concrete', 'iron_plate',
         'iron_rod', 'screw', 'wire', 'cable', 'copper_sheet', 'quickwire', 'quartz_crystal', 'silica', 'steel_beam',
         'steel_pipe', 'reinforced_iron_plate', 'rotor', 'modular_frame', 'encased_industrial_beam', 'stator', 'motor',
         'heavy_modular_frame', 'smart_plating', 'versatile_framework', 'automated_wiring', 'biomass', 'solid_biofuel']

DROPS_SELF = []
POLES = []


def machine_states(name, model_off, model_on):
    variants = {}
    for facing, y in HROT.items():
        for active in ('false', 'true'):
            m = {'model': NS + ':block/' + (model_on if active == 'true' else model_off)}
            if y:
                m['y'] = y
            variants['active=%s,facing=%s' % (active, facing)] = m
    bs(name, {'variants': variants})


def machines():
    for name in MACHINES:
        for on in (False, True):
            suffix = '_on' if on else ''
            bmodel(name + suffix, {
                'parent': 'minecraft:block/cube',
                'textures': {
                    'particle': t(name + '_side'),
                    'north': t(name + '_front'),
                    'south': t(name + '_back' + suffix),
                    'east': t(name + '_side' + suffix),
                    'west': t(name + '_side' + suffix),
                    'up': t(name + '_top'),
                    'down': t(name + '_bottom'),
                }
            })
        machine_states(name, name, name + '_on')
        imodel(name, {'parent': NS + ':block/' + name})
        DROPS_SELF.append(name)


def simple_facing(name, textures, prop_active=True):
    bmodel(name, {'parent': 'minecraft:block/cube', 'textures': textures})
    if prop_active:
        machine_states(name, name, name)
    else:
        variants = {}
        for facing, y in HROT.items():
            m = {'model': NS + ':block/' + name}
            if y:
                m['y'] = y
            variants['facing=' + facing] = m
        bs(name, {'variants': variants})
    imodel(name, {'parent': NS + ':block/' + name})
    DROPS_SELF.append(name)


def specials():
    simple_facing('hub', {'particle': t('hub_side'), 'north': t('hub_front'), 'south': t('hub_side'),
                          'east': t('hub_side'), 'west': t('hub_side'), 'up': t('hub_top'), 'down': t('smelter_bottom')},
                  prop_active=True)
    simple_facing('craft_bench', {'particle': t('craft_bench_side'), 'north': t('craft_bench_front'),
                                  'south': t('craft_bench_side'), 'east': t('craft_bench_side'),
                                  'west': t('craft_bench_side'), 'up': t('craft_bench_top'),
                                  'down': t('smelter_bottom')}, prop_active=True)
    s = t('storage_container_side')
    simple_facing('storage_container', {'particle': s, 'north': s, 'south': s, 'east': s, 'west': s,
                                        'up': t('storage_container_top'), 'down': t('smelter_bottom')})
    for name in ('splitter', 'merger'):
        s = t(name + '_side')
        simple_facing(name, {'particle': s, 'north': s, 'south': s, 'east': s, 'west': s,
                             'up': t(name + '_top'), 'down': t('smelter_bottom')})

    # railway buildings (drawn by the block entity renderer; models only serve particles + the fallback item)
    c = t('casing')
    for name in ('train_station', 'freight_platform', 'fluid_freight_platform', 'empty_platform'):
        simple_facing(name, {'particle': c, 'north': c, 'south': c, 'east': c, 'west': c, 'up': c, 'down': c})

    # test-only creative generator (drawn by the block entity renderer; the model only serves particles + the fallback item)
    c = t('creative_generator_side')
    simple_facing('creative_generator', {'particle': c, 'north': c, 'south': c, 'east': c, 'west': c, 'up': c, 'down': c})

    # invisible multi-block part (model only used for break particles)
    bmodel('machine_part', {'parent': 'minecraft:block/cube_all', 'textures': {'all': t('casing')}})
    bs('machine_part', {'variants': {'': {'model': NS + ':block/machine_part'}}})

    # Architecture
    bmodel('foundation', {'parent': 'minecraft:block/cube_bottom_top',
                          'textures': {'top': t('foundation_top'), 'bottom': t('foundation_top'),
                                       'side': t('foundation_side')}})
    bs('foundation', {'variants': {'': {'model': NS + ':block/foundation'}}})
    imodel('foundation', {'parent': NS + ':block/foundation'})
    DROPS_SELF.append('foundation')
    bmodel('concrete_wall', {'parent': 'minecraft:block/cube_all', 'textures': {'all': t('concrete_wall')}})
    bs('concrete_wall', {'variants': {'': {'model': NS + ':block/concrete_wall'}}})
    imodel('concrete_wall', {'parent': NS + ':block/concrete_wall'})
    DROPS_SELF.append('concrete_wall')


def nodes():
    for n in NODES:
        name = n + '_node'
        bmodel(name, {'parent': 'minecraft:block/cube_bottom_top',
                      'textures': {'top': t(name + '_top'), 'bottom': t(name + '_side'), 'side': t(name + '_side')}})
        bs(name, {'variants': {'': {'model': NS + ':block/' + name}}})
        imodel(name, {'parent': NS + ':block/' + name})


def face(tex, uv=None, cull=None):
    f = {'texture': tex}
    if uv:
        f['uv'] = uv
    if cull:
        f['cullface'] = cull
    return f


def box(frm, to, tex_all, uvs=None):
    faces = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        faces[d] = {'texture': tex_all}
        if uvs and d in uvs:
            faces[d]['uv'] = uvs[d]
    return {'from': frm, 'to': to, 'faces': faces}


def poles():
    for mk in (1, 2, 3):
        name = 'power_pole_mk%d' % mk
        height = 3 + mk
        tex = {'particle': t(name), 'pole': t(name), 'ins': t('power_pole_insulator')}
        shaft = box([6, 0, 6], [10, 16, 10], '#pole', {'up': [6, 6, 10, 10], 'down': [6, 6, 10, 10]})
        base = [shaft, box([4, 0, 4], [12, 3, 12], '#pole'), box([5, 3, 5], [11, 5, 11], '#pole')]
        top = [box([6, 0, 6], [10, 13, 10], '#pole', {'up': [6, 6, 10, 10], 'down': [6, 6, 10, 10]}),
               box([1, 10, 6.5], [15, 13, 9.5], '#pole')]
        # insulators on the crossbar (more for higher tiers)
        xs = {1: [2, 12], 2: [2, 7, 12], 3: [1.5, 5, 9, 12.5]}[mk]
        for x in xs:
            top.append(box([x, 13, 7], [x + 2, 16, 9], '#ins'))
        if mk >= 2:
            top.append(box([6.5, 6, 2], [9.5, 8, 14], '#pole'))
        bmodel(name + '_base', {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': tex, 'elements': base})
        bmodel(name + '_shaft', {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': tex, 'elements': [shaft]})
        bmodel(name + '_top', {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'textures': tex, 'elements': top})
        variants = {}
        for sec in range(6):
            part = 'base' if sec == 0 else ('top' if sec == height - 1 else 'shaft')
            variants['section=%d' % sec] = {'model': NS + ':block/' + name + '_' + part}
        bs(name, {'variants': variants})
        # item: a miniature full pole
        imodel(name, {
            'parent': 'minecraft:block/block',
            'textures': tex,
            'elements': [
                box([7, 0, 7], [9, 14, 9], '#pole'),
                box([6, 0, 6], [10, 2, 10], '#pole'),
                box([3, 12, 7], [13, 14, 9], '#pole'),
                box([3, 14, 7.5], [4.5, 16, 8.5], '#ins'),
                box([11.5, 14, 7.5], [13, 16, 8.5], '#ins'),
            ],
            'display': {
                'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
            }
        })
        POLES.append(name)


def slab_belt(name, top_tex, side):
    return {
        'parent': 'minecraft:block/block',
        'textures': {'particle': side, 'top': top_tex, 'side': side},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, 4, 16],
            'faces': {
                'up': face('#top', [0, 0, 16, 16]),
                'down': face('#side', [0, 12, 16, 16], 'down'),
                'north': face('#side', [0, 12, 16, 16], 'north'),
                'south': face('#side', [0, 12, 16, 16], 'south'),
                'east': face('#side', [0, 12, 16, 16], 'east'),
                'west': face('#side', [0, 12, 16, 16], 'west'),
            }
        }]
    }


def curve_belt(top_tex, side, mirror):
    """A quarter-disc belt (centre at the inner corner) so the outer corner is rounded."""
    import math
    els = []
    for x in range(16):
        depth = int(round(math.sqrt(max(0.0, 16.3 ** 2 - (x + 0.5) ** 2))))
        depth = max(1, min(16, depth))
        x0, x1 = (16 - x - 1, 16 - x) if mirror else (x, x + 1)
        els.append({
            'from': [x0, 0, 0], 'to': [x1, 4, depth],
            'faces': {
                'up': face('#top', [x0, 0, x1, depth]),
                'down': face('#side', [x0, 12, x1, 16], 'down'),
                'north': face('#side', [x0, 12, x1, 16], 'north'),
                'south': face('#side', [x0, 12, x1, 16]),
                'east': face('#side', [0, 12, depth, 16]),
                'west': face('#side', [0, 12, depth, 16]),
            }
        })
    return {'parent': 'minecraft:block/block', 'textures': {'particle': side, 'top': top_tex, 'side': side}, 'elements': els}


def belts():
    for mk in (1, 2, 3):
        name = 'conveyor_belt_mk%d' % mk
        top = t(name + '_top')
        side = t(name + '_side')
        bmodel(name, slab_belt(name, top, side))
        bmodel(name + '_turn_left', curve_belt(t(name + '_turn_left'), side, False))
        bmodel(name + '_turn_right', curve_belt(t(name + '_turn_right'), side, True))
        # ramp rising towards north: 4 steps
        steps = []
        for i in range(4):
            z0, z1, h = 12 - 4 * i, 16 - 4 * i, 4 + 4 * i
            steps.append({'from': [0, 0, z0], 'to': [16, h, z1], 'faces': {
                'up': face('#top', [0, z0, 16, z1]),
                'north': face('#side', [0, 16 - h, 16, 16]),
                'south': face('#side', [0, 16 - h, 16, 16], 'south' if i == 0 else None),
                'east': face('#side', [z0, 16 - h, z1, 16], 'east'),
                'west': face('#side', [z0, 16 - h, z1, 16], 'west'),
                'down': face('#side', [0, 12, 16, 16], 'down'),
            }})
        bmodel(name + '_ramp', {'parent': 'minecraft:block/block',
                                'textures': {'particle': side, 'top': top, 'side': side}, 'elements': steps})
        bmodel(name + '_vertical', {
            'parent': 'minecraft:block/block',
            'textures': {'particle': side, 'top': top, 'side': side},
            'elements': [
                {'from': [2, 0, 2], 'to': [14, 16, 3],
                 'faces': {'south': face('#top', [2, 0, 14, 16]), 'north': face('#side', [0, 0, 12, 16]),
                           'up': face('#side', [0, 12, 12, 13]), 'down': face('#side', [0, 12, 12, 13])}},
                {'from': [2, 0, 3], 'to': [3, 16, 14],
                 'faces': {'east': face('#side', [0, 0, 11, 16]), 'west': face('#side', [0, 0, 11, 16]),
                           'south': face('#side', [0, 0, 1, 16]), 'up': face('#side', [0, 0, 1, 11]),
                           'down': face('#side', [0, 0, 1, 11])}},
                {'from': [13, 0, 3], 'to': [14, 16, 14],
                 'faces': {'east': face('#side', [0, 0, 11, 16]), 'west': face('#side', [0, 0, 11, 16]),
                           'south': face('#side', [0, 0, 1, 16]), 'up': face('#side', [0, 0, 1, 11]),
                           'down': face('#side', [0, 0, 1, 11])}},
            ]
        })
        shapes = ['straight', 'turn_left', 'turn_right', 'ascending', 'descending']
        variants = {}
        for facing in ('north', 'east', 'south', 'west', 'up', 'down'):
            for shp in shapes:
                key = 'facing=%s,shape=%s' % (facing, shp)
                if facing in ('up', 'down'):
                    m = {'model': NS + ':block/' + name + '_vertical'}
                    if facing == 'down':
                        m['x'] = 180
                    variants[key] = m
                    continue
                y = HROT[facing]
                if shp == 'straight':
                    model = name
                elif shp in ('turn_left', 'turn_right'):
                    model = name + '_' + shp
                else:
                    model = name + '_ramp'
                    if shp == 'descending':
                        y = (y + 180) % 360
                m = {'model': NS + ':block/' + model}
                if y:
                    m['y'] = y
                variants[key] = m
        bs(name, {'variants': variants})
        imodel(name, {'parent': NS + ':block/' + name})
        DROPS_SELF.append(name)


def fluids():
    # ---- pipelines: multipart core + arms
    for mk in (1, 2):
        name = 'pipeline_mk%d' % mk
        glass = t(name + '_glass')
        ring = t(name + '_ring')
        bmodel(name + '_core', {'parent': 'minecraft:block/block', 'textures': {'particle': ring, 'glass': glass, 'ring': ring},
                                 'elements': [
                                     box([5, 5, 5], [11, 11, 11], '#glass'),
                                     box([4.8, 4.8, 4.8], [5.6, 11.2, 5.6], '#ring'),
                                     box([10.4, 4.8, 4.8], [11.2, 11.2, 5.6], '#ring'),
                                     box([4.8, 4.8, 10.4], [5.6, 11.2, 11.2], '#ring'),
                                     box([10.4, 4.8, 10.4], [11.2, 11.2, 11.2], '#ring'),
                                 ]})
        # arm pointing north (z 0..5); rotated by the blockstate for other sides
        bmodel(name + '_arm', {'parent': 'minecraft:block/block', 'textures': {'particle': ring, 'glass': glass, 'ring': ring},
                                'elements': [
                                    {'from': [6, 6, 0], 'to': [10, 10, 5], 'faces': {
                                        'up': face('#glass'), 'down': face('#glass'), 'east': face('#glass'), 'west': face('#glass')}},
                                    box([5.5, 5.5, 0], [10.5, 10.5, 1], '#ring'),
                                    box([5.5, 5.5, 4], [10.5, 10.5, 5], '#ring'),
                                ]})
        rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
        parts = [{'apply': {'model': NS + ':block/' + name + '_core'}}]
        for d, r in rot.items():
            m = {'model': NS + ':block/' + name + '_arm'}
            m.update(r)
            parts.append({'when': {d: 'true'}, 'apply': m})
        bs(name, {'multipart': parts})
        imodel(name, {'parent': 'minecraft:block/block', 'textures': {'particle': ring, 'glass': glass, 'ring': ring},
                      'elements': [
                          {'from': [6, 6, 0], 'to': [10, 10, 16], 'faces': {
                              'up': face('#glass'), 'down': face('#glass'), 'east': face('#glass'), 'west': face('#glass'),
                              'north': face('#ring'), 'south': face('#ring')}},
                          box([5.5, 5.5, 0], [10.5, 10.5, 1], '#ring'),
                          box([5.5, 5.5, 15], [10.5, 10.5, 16], '#ring'),
                          box([5.5, 5.5, 7.5], [10.5, 10.5, 8.5], '#ring'),
                      ],
                      'display': {'gui': {'rotation': [30, 45, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]}}})
        DROPS_SELF.append(name)

    # ---- pipeline pump: housing + pipe stubs, facing = flow direction (north = output)
    side = t('pipeline_pump_side')
    bmodel('pipeline_pump', {'parent': 'minecraft:block/block', 'textures': {
        'particle': side, 'side': side, 'out': t('pipeline_pump_out'), 'in': t('pipeline_pump_in'), 'ring': t('pipeline_mk1_ring')},
        'elements': [
            {'from': [2, 2, 3], 'to': [14, 14, 13], 'faces': {
                'up': face('#side'), 'down': face('#side'), 'east': face('#side'), 'west': face('#side'),
                'north': face('#out'), 'south': face('#in')}},
            box([5.5, 5.5, 0], [10.5, 10.5, 3], '#ring'),
            box([5.5, 5.5, 13], [10.5, 10.5, 16], '#ring'),
            box([6, 14, 6], [10, 16, 10], '#ring'),
        ]})
    variants = {}
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    for d, r in rot.items():
        for a in ('false', 'true'):
            m = {'model': NS + ':block/pipeline_pump'}
            m.update(r)
            variants['active=%s,facing=%s' % (a, d)] = m
    bs('pipeline_pump', {'variants': variants})
    imodel('pipeline_pump', {'parent': NS + ':block/pipeline_pump'})
    DROPS_SELF.append('pipeline_pump')

    # ---- water extractor (drawn by the building renderer; cube model for the item icon / particles)
    bmodel('water_extractor', {'parent': 'minecraft:block/cube', 'textures': {
        'particle': t('casing'), 'north': t('pipeline_pump_out'), 'south': t('casing_dark'), 'east': t('casing'),
        'west': t('casing'), 'up': t('vent'), 'down': t('frame')}})
    machine_states('water_extractor', 'water_extractor', 'water_extractor')
    imodel('water_extractor', {'parent': NS + ':block/water_extractor'})
    DROPS_SELF.append('water_extractor')

    # ---- fluid buffer (drawn by the building renderer; cube model for the item icon / particles)
    bmodel('fluid_buffer', {'parent': 'minecraft:block/cube', 'textures': {
        'particle': t('tank'), 'north': t('tank'), 'south': t('tank'), 'east': t('tank'),
        'west': t('tank'), 'up': t('vent'), 'down': t('frame')}})
    machine_states('fluid_buffer', 'fluid_buffer', 'fluid_buffer')
    imodel('fluid_buffer', {'parent': NS + ':block/fluid_buffer'})
    DROPS_SELF.append('fluid_buffer')

    imodel('zipline', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': NS + ':item/zipline'}})


def items():
    for p in PARTS:
        imodel(p, {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS + ':item/' + p}})
    imodel('build_gun', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': NS + ':item/build_gun'}})


def data():
    for name in DROPS_SELF:
        write(os.path.join(D, NS, 'loot_table', 'blocks', name + '.json'), {
            'type': 'minecraft:block',
            'pools': [{
                'rolls': 1,
                'entries': [{'type': 'minecraft:item', 'name': NS + ':' + name}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]
            }],
            'random_sequence': NS + ':blocks/' + name
        })
    for name in POLES:
        write(os.path.join(D, NS, 'loot_table', 'blocks', name + '.json'), {
            'type': 'minecraft:block',
            'pools': [{
                'rolls': 1,
                'entries': [{'type': 'minecraft:item', 'name': NS + ':' + name}],
                'conditions': [
                    {'condition': 'minecraft:survives_explosion'},
                    {'condition': 'minecraft:block_state_property', 'block': NS + ':' + name,
                     'properties': {'section': '0'}}
                ]
            }],
            'random_sequence': NS + ':blocks/' + name
        })
    write(os.path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'),
          {'replace': False, 'values': [NS + ':' + n for n in DROPS_SELF + POLES]})
    write(os.path.join(D, NS, 'recipe', 'build_gun.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'equipment',
        'pattern': ['IRI', 'ICI', ' I '],
        'key': {'I': {'item': 'minecraft:iron_ingot'}, 'R': {'item': 'minecraft:redstone'},
                'C': {'item': 'minecraft:copper_ingot'}},
        'result': {'id': NS + ':build_gun', 'count': 1}
    })
    write(os.path.join(D, NS, 'worldgen', 'placed_feature', 'resource_nodes.json'), {
        'feature': {'type': NS + ':resource_node', 'config': {}},
        'placement': [
            {'type': 'minecraft:rarity_filter', 'chance': 4},
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'},
            {'type': 'minecraft:biome'}
        ]
    })


if __name__ == '__main__':
    machines()
    specials()
    nodes()
    poles()
    belts()
    fluids()
    items()
    data()
    print('json generated, %d self-dropping blocks' % len(DROPS_SELF))
