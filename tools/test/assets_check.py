#!/usr/bin/env python3
"""Checks that every texture referenced by the generated JSON exists, and that registered ids have models + lang keys."""
import glob, json, os, re, sys
R = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft')
bad = 0
def fail(msg):
    global bad
    bad += 1
    print('  MISSING', msg)
refs = set()
for f in glob.glob(R + '/**/*.json', recursive=True):
    if '/lang/' in f: continue
    for m in re.findall(r'"(ficsitcraft:(?:block|item)/[a-z0-9_/]+)"', open(f).read()):
        refs.add((m, f))
for ref, f in sorted(refs):
    ns, path = ref.split(':')
    if path.startswith('block/') and os.path.exists(os.path.join(R, 'models', path + '.json')): continue
    if path.startswith('item/') and os.path.exists(os.path.join(R, 'models', path + '.json')) and 'textures' not in f: continue
    if not os.path.exists(os.path.join(R, 'textures', path + '.png')) and not os.path.exists(os.path.join(R, 'models', path + '.json')):
        fail('%s (in %s)' % (ref, os.path.relpath(f, R)))
# ids from the Java registries
java = ''
for f in glob.glob(os.path.join(R, '..', '..', '..', 'java', '**', 'ModBlocks.java'), recursive=True) + \
        glob.glob(os.path.join(R, '..', '..', '..', 'java', '**', 'ModItems.java'), recursive=True):
    java += open(f).read()
ids = set(re.findall(r'register\("([a-z_0-9]+)"', java)) | set(re.findall(r'part\("([a-z_0-9]+)"', java))
lang = json.load(open(os.path.join(R, 'lang', 'en_us.json')))
ko = json.load(open(os.path.join(R, 'lang', 'ko_kr.json')))
for i in sorted(ids):
    if i in ('machine_part',): continue
    has_model = os.path.exists(os.path.join(R, 'models', 'item', i + '.json'))
    if not has_model: fail('item model ' + i)
    if 'item.ficsitcraft.' + i not in lang and 'block.ficsitcraft.' + i not in lang: fail('lang ' + i)
for k in lang:
    if k not in ko: fail('ko ' + k)
for k in ko:
    if k not in lang: fail('en ' + k)
# Minecraft only accepts square block-atlas sprites (tools/squarify.py); animated ones need an .mcmeta
try:
    from PIL import Image
    for base, _, files in os.walk(os.path.join(R, 'textures', 'block')):
        for fn in files:
            if not fn.endswith('.png'): continue
            p = os.path.join(base, fn)
            if os.path.exists(p + '.mcmeta'): continue
            w, h = Image.open(p).size
            if w != h: fail('non-square texture %s (%dx%d) - run tools/squarify.py' % (os.path.relpath(p, R), w, h))
except ImportError:
    print('  (Pillow missing: square-texture check skipped)')
# resource identifiers are [a-z0-9/._-] only: an uppercase letter in a texture path (or file name) makes the whole model fail to load
for base, dirs, files in os.walk(R):
    for n in dirs + files:
        if n != n.lower() and not n.endswith('.mcmeta'): fail('uppercase in resource path %s' % os.path.relpath(os.path.join(base, n), R))
for f in glob.glob(R + '/**/*.json', recursive=True):
    if '/lang/' in f: continue
    for m in re.findall(r'"(ficsitcraft:[^"]*[A-Z][^"]*)"', open(f).read()):
        fail('uppercase identifier %s (in %s)' % (m, os.path.relpath(f, R)))
print('assets ok' if bad == 0 else '%d problems' % bad)
sys.exit(1 if bad else 0)
