#!/bin/sh
# Syntax/consistency check without the Minecraft jars: reports errors that involve the mod's own classes.
cd "$(dirname "$0")/../.."
OUT=${TMPDIR:-/tmp}/ficsit_jc
rm -rf "$OUT"; mkdir -p "$OUT"
javac -d "$OUT" -proc:none -Xmaxerrs 20000 $(find src/main/java src/client/java -name "*.java") > "$OUT/log.txt" 2>&1
python3 - "$OUT/log.txt" <<'PY'
import re, sys
txt = open(sys.argv[1]).read()
blocks = re.split(r'\n(?=\S+\.java:\d+: error:)', txt)
mine = re.compile(r'\b(RailWorld|RailNet|RailCodec|VehicleCargo|Train|TrainSim|RailGraph|RailNode|RailTrack|RailPlacement|RailPlanner|Vehicle|V3|Bezier|Dir|Router|Stop|Goal|VehicleType|RailBuildingBlockEntity|RailBuildingBlock|RailNetPayload|RailActionPayload|RailwayItem|VehicleItem|RailSignalItem|ClientRail|TrackRenderer|TrainRenderer|TrainRide|TrainScreen|StationScreen|RailClientNet|TrainAudio|TrainFx|RailInteract|RailRenderer|BuildingMesh|BuildingModel|BuildingModels|CreativeGeneratorBlock|CreativeGeneratorBlockEntity|ModSounds)\b')
out = []
for b in blocks:
    if 'error:' not in b:
        continue
    first = b.split('\n')[0]
    if 'cannot find symbol' in first:
        loc = re.search(r'location:.*', b)
        sym = re.search(r'symbol:.*', b)
        l = loc.group(0) if loc else ''
        s = sym.group(0) if sym else ''
        kind = s.split()[1] if len(s.split()) > 1 else ''
        name = s.split()[2] if len(s.split()) > 2 else ''
        if kind == 'class':
            if mine.search(name):
                out.append(first + '\n   ' + s + '\n   ' + l)
        elif re.search(r'of type (RailWorld|RailBuildingBlockEntity|RailBuildingBlock|Train|TrainSim|Vehicle|RailGraph|RailNode|RailTrack|VehicleCargo|V3|Bezier|BuildingModel|CTrain|ClientRail\.CTrain)\b', l) \
                or re.search(r'class (RailNet|RailCodec|RailPlacement|RailPlanner|Train|TrainSim|Vehicle|RailGraph|RailNode|RailTrack|VehicleCargo|V3|Bezier|Router|ClientRail|CTrain|TrainAudio|TrainFx|RailInteract|BuildingMesh|BuildingModel|BuildingModels|Stop)\b', l):
            out.append(first + '\n   ' + s + '\n   ' + l)
    elif any(k in first for k in ('does not exist', 'cannot access', 'static import only', 'does not override', 'is not abstract and does not', 'incompatible types', 'cannot be applied', 'cannot be dereferenced', 'no suitable', 'unreported exception', 'not a functional interface', 'reference to', 'bad operand', 'cannot infer', 'invalid method reference', 'unexpected type', 'enhanced for', 'is not a statement')):
        continue
    else:
        out.append(first + '\n' + '\n'.join(b.split('\n')[1:3]))
print(len(out), 'issues')
print('\n'.join(out[:80]))
PY
