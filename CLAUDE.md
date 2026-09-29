# FICSIT Craft — notes for Claude

Fabric mod for Minecraft **1.21.1** that recreates Satisfactory (resource nodes, miners, machines, belts, pipes, power grid, HUB milestones, zipline, scanner, trains). Author/user: 새벽 — **talk to them in Korean**; they want features as Satisfactory-faithful as reasonable. `README.md` is the player-facing manual and is kept in sync with features; update it when behaviour changes.

Stack: Fabric Loom 1.10-SNAPSHOT, Gradle 8.14.3 (wrapper), Fabric Loader 0.16.14, Fabric API 0.116.17+1.21.1, **Yarn** 1.21.1+build.3 (not Mojang mappings), Java 21. Split source sets: `src/main` (common) and `src/client` (client only; may use `src/main`, never the reverse). Indentation is **tabs**.

## Most important caveat: never seen running in a game

Early development happened in a sandbox with no Maven access. Later a cloud session could reach the Fabric/Mojang repos and **`./gradlew build` succeeded (jar built, no compile errors)**; if `build` works in your environment, run it after every change. Nothing has been run in an actual game though. Older verification was: headless simulation tests, `javac` symbol checks, and manual API checks against the Yarn mapping repo (`FabricMC/yarn`, branch `1.21.1`, e.g. `mappings/net/minecraft/client/MinecraftClient.mapping`). Nothing has been seen in a running game either (rendering, mixins, sounds, networking, camera).

If a game can be run, do `./gradlew runClient` and check the items in "Unverified in-game" below. If the build cannot be run, use the yarn mapping repo to check every Minecraft API call you add or touch — do not guess method names.

## Commands

```bash
./gradlew build            # jar in build/libs/ficsitcraft-1.0.0.jar
./gradlew runClient        # dev client

sh tools/test/run.sh                  # javac + run the headless rail/train simulation (must print ALL PASSED)
python3 tools/test/assets_check.py    # textures/models/lang consistency, square-texture check (needs Pillow)
python3 tools/squarify.py --check     # must report 0 non-square textures
sh tools/test/check.sh                # javac without MC jars; reports only errors involving the mod's own classes
```

`check.sh` has one **known false positive**: `RailWorld.java:96 isRemoved()` (BlockEntity.isRemoved exists in real Minecraft). Anything else it reports is real. When you add a new mod class that the script should track, add its name to the `mine` regex in `check.sh`.

Run `run.sh`, `assets_check.py`, `squarify.py --check` and `check.sh` before declaring work done.

## Source layout

```
src/main/java/com/ficsitcraft
  data/        recipes, milestones, building costs
  power/       power nodes + per-tick grid solver (PowerGridManager); fuse logic
  block/ blockentity/   machines, belts, pipes, nodes, HUB, creative generator, station/platform blocks
  multiblock/ fluid/    multi-block buildings, pipe pressure/flow simulation
  screen/      server-side screen handlers
  progress/    world-wide HUB progress (PersistentState)
  worldgen/    resource node feature
  network/     payload records (S2C/C2S)
  registry/    ModBlocks, ModItems, ModBlockEntities, ModScreenHandlers, ModSounds, ModItemGroups, ModWorldGen
  rail/        PURE JAVA (no MC imports): Bezier, RailGraph, RailPlanner, RailPlacement, V3, Dir
  train/       PURE JAVA: Train, TrainSim, Router (Dijkstra), Stop, Vehicle, VehicleType, Goal
  railway/     Minecraft glue: RailWorld (PersistentState per dimension), RailNet (packets + commands), RailCodec, VehicleCargo
src/client/java/com/ficsitcraft/client
  railway/     ClientRail (snapshots, poses, wheel roll), RailRenderer, RailInteract (aim/hitbox/left-click), TrainRide (rider camera),
               TrainScreen (timetable UI), StationScreen, TrainAudio, TrainFx, RailClientNet
  building/    BuildingModel(s) (geometry JSON loader), BuildingMesh (draws the generated multi-block geometry, incl. wheel roll)
  render/      BuildingRenderer, ConveyorBeltRenderer, PipeFluidRenderer, PlacementHologram, PowerLineRenderer
  mixin/       MinecraftClientMixin (click routing for tracks/vehicles), BipedEntityModelMixin (zipline arm pose)
  hud/ screen/ scanner/ zipline/
src/main/resources/assets/ficsitcraft    textures, models, blockstates, lang (en_us + ko_kr), buildings/*.json, vehicles/*.json, sounds/, sounds.json
tools/         Python generators (Pillow, numpy, ffmpeg for sounds) and tests
```

The `rail/` and `train/` packages must stay free of Minecraft imports: `tools/test/RailSim.java` compiles them alone. Keep new simulation logic there when possible so it can be tested headless, and extend `RailSim.java` for new behaviour.

## Asset pipeline (all generated — edit the generators, not the outputs)

Textures, models, blockstates, loot tables, lang and building geometry are produced by scripts in `tools/`. Run from the project root, **in this order**:

`gen_textures.py` → `gen_json.py` → `gen_buildings.py` → `gen_trains.py` → `squarify.py` → `gen_lang.py`

- `gen_buildings.py` and `gen_trains.py` call `squarify.py` themselves; `gen_sounds.py` is separate (writes `sounds/*.ogg` + `sounds.json`).
- Before a **full** regenerate, delete `assets/ficsitcraft/models`, `assets/ficsitcraft/blockstates` and `data/ficsitcraft/loot_table`, otherwise stale files stay.
- **Resource paths must be lowercase** (see Unverified list; enforced by `assets_check.py`).
- **Block-atlas textures must be square.** Without an `.mcmeta`, Minecraft treats a block-atlas PNG as a square sprite of side `min(w,h)`; non-square PNGs are cropped or rejected (purple/black "missing"). This was the cause of the "broken/invisible freight car / fluid platform / fluid car" bug. `squarify.py` fixes this with nearest-neighbour scaling (cap 96 px); building faces are drawn with a STRETCH mapping so squaring does not change the look. Belt textures with `.mcmeta` animation are exempt.
- New user-facing strings need **both** `en_us` and `ko_kr` entries (`assets_check.py` enforces parity). Add them in `gen_lang.py`.
- Registered items/blocks need an item model, lang key, and (for blocks) blockstate/model/loot table; `assets_check.py` verifies this.

## Trains: design decisions worth knowing

- Tracks are free-form cubic Beziers in a `RailGraph`. `RailGraph.MIN_RADIUS = 4.5` (deliberately tight; 90° fits in ~7×7). `RailPlanner` rejects a piece when the start tangent or arrival tangent has `dot(chordDir) < 0.02`. Max 64 blocks per piece, cost = ceil(len/6) Railway items (refund on dismantle likewise). Switches and junctions arise where a track branches off another.
- `RailWorld` holds graph, trains and stations per dimension and is the only authority. `RailNet` handles all C2S actions (`RailActionPayload`) and pushes the full graph/snapshots to clients (`RailNetPayload`).
- **Timetable stops reference stations by NAME** (`Stop(String station, int mode, int seconds)`), matched case-insensitively. `RailWorld.registerStation` retargets stops on rename (only if no other station still uses the old name); `unregisterStation` prunes stops of a vanished name, fixes `stopIndex`, and turns autopilot off if the timetable becomes empty. Default new-station names come from `freeStationName()` ("Station N", lowest unused). `TrainScreen` shows stops of unknown stations in red.
- Dwell time: 1..`Stop.MAX_SECONDS` (3600) s, or mode Cargo (`WAIT_LOADED`): waits until no platform is busy with the train (`RailWorld` hook -> `RailBuildingBlockEntity.isBusyWith`), `seconds` is then the time limit (0 = none, UI default 120). `CMD_TIMETABLE` carries a trailing `quiet` boolean (no UI refresh reply) and preserves the train's current place in the timetable when the list is edited.
- Snapshot `TRAINS` packets do **not** carry the timetable; it only comes with `openTrainUi` replies.
- Client poses: `ClientRail.posed(ct, world, delta)` extrapolates from the last snapshot (`age = (world.getTime() - recv) + delta`, clamped 0..8). Incoming TRAINS packets are buffered in `pending` and applied at START_CLIENT_TICK so `recv` has a consistent phase (this was the fix for the rider camera jitter). The rider camera is `posed(delta=1)` applied at END_CLIENT_TICK by `TrainRide`.
- Left-click destroying vehicles: `MinecraftClientMixin` injects into `doAttack()Z` and `handleBlockBreaking(Z)V`; `RailInteract` does the oriented-box hit test (half width 1.45, half height 1.85, reach 5) and sends `DISMANTLE_VEHICLE`. Server rule: vehicle must be stopped and not driven; drops the vehicle item and cargo.
- Effects: wheels are 3 non-overlapping boxes per wheel (stepped octagon, R = 0.45) flagged `wheel` in the vehicle JSON; `BuildingMesh` rotates them about local X (`-roll / R`, forward is -Z in the matrix frame). `TrainAudio` uses looping `MovingSoundInstance`s (roll/motor/brake) for the car nearest the player; `TrainFx` spawns spark particles at wheel axles when `squealing` (speed > 0.06 and smoothed decel > 0.0035).
- Platforms: right-click opens `PlatformScreenHandler`/`PlatformScreen` (load/unload buttons via `onButtonClick`, status ints `RailBuildingBlockEntity.ST_*` synced through a PropertyDelegate; values are shorts so amounts are x10). Unloaded items are pushed to any storage next to either long side.
- Riding: `RailWorld.startRiding` sets `driver`; `driven` (manual control) is only true when the train has no autopilot or once the rider sends non-zero DRIVE_INPUT. Rider safety: `RailRiders` + `EntityMixin` (isInsideWall), client `ClientPlayerEntityMixin` (pushOutOfBlocks) and `InGameOverlayRendererMixin` (in-wall overlay). `common` mixin config `ficsitcraft.mixins.json` was added for this.
- Creative Generator: `CreativeGeneratorBlock` refuses placement unless the player is creative (`getPlacementState` returns null with a message); 3000 MW, extends `PowerNodeBlockEntity`; deliberately absent from recipes/build gun.

## Unverified in-game (check first when a game can be run)

- Platform GUI (layout of both variants), unloading really pushing items into belts/chests next to the platform, docking distance (car centre within 4.7 blocks of the platform centre); cargo timetable stops + hover tooltips; riding an autopilot train (passenger mode, takeover on W/S/Space) and that mixins (`isInsideWall`, `pushOutOfBlocks`, `getInWallBlockState`) really keep the rider from being pushed/suffocated.
- Build Gun: 3D item model orientation/size in the inventory, first-person and third-person hand (display transforms in `gen_buildings.py` `BUILD_GUN_DISPLAY` are derived by reasoning, tweak them in-game); wide UI (tabs, search box, hint text) layout.
- Creative Generator as a 3x3x4 multi-block (placement, hologram, power line anchor at the spire top).
- Wheel roll direction and phase; sound loops (volume/pitch balance, loop clicks); spark particle look.
- Rider camera still smooth while driving (jitter fix is analysis-based); a rare small correction jump may exist on timing changes.
- Left-click vehicle destroy through the mixin (hitbox alignment, no block-breaking crack animation while aiming at a train).
- Timetable UI layout (380 px wide, number field per stop, Enter/close commit).
- Freight car, fluid freight car, fluid freight platform visible in world and in inventory. The real cause was UPPERCASE letters in generated texture paths (`cargo_doorL`, `ladderB`, ...): resource identifiers only allow `[a-z0-9/._-]`, otherwise the whole model fails to load. `Store.save` now lowercases names and `assets_check.py` fails on any uppercase resource path/identifier.
- Creative Generator behaviour in survival (should refuse placement).

## Known performance limits (optimization not done yet)

Fine for small/medium networks. Bottlenecks at large scale: the whole rail graph is re-sent to clients on every change (should be incremental/chunked), linear scans in `rayTrack`/`blockRegion` (need a spatial index), no render culling for distant track/trains, Dijkstra per autopilot train (cache routes/limit by station pair).

## Working agreements

- Do not add features beyond what the user asks for; report honestly what was and was not verified.
- Keep `README.md` in sync when behaviour changes.
- Commit style: short imperative subject; the repo has no remote configured — the user pushes it.
