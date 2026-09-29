# FICSIT Craft — Satisfactory in Minecraft

A Fabric mod for Minecraft **1.21.1** that brings in Satisfactory's core production loop:
resource nodes → miners → smelters/constructors/assemblers → conveyor belts → power grid → HUB milestones (Tier 0–4) → Space Elevator.

## Build and run

Requirements: **JDK 21** (Minecraft 1.21.1 requires Java 21; JDK 17 won't work). You don't need to install Gradle; the wrapper downloads it.

- Install JDK 21 from https://adoptium.net/temurin/releases/?version=21.
- Gradle finds an installed JDK 21 automatically (`gradle/gradle-daemon-jvm.properties`).
- If it doesn't, set `JAVA_HOME` to the JDK 21 folder.

```bash
./gradlew runClient      # launch a dev client
./gradlew build          # jar is created at build/libs/ficsitcraft-1.0.0.jar
```

To install, put `ficsitcraft-1.0.0.jar` and **Fabric API** (0.116.x+1.21.1) in `.minecraft/mods` of an instance that has Fabric Loader.

> The first build downloads Minecraft, the Yarn mappings and Fabric API, so it takes a few minutes.

## How to play

1. **On first join** you get a HUB, a Craft Bench and a Build Gun. Place the HUB and the Craft Bench.
2. **Resource nodes**: infinite nodes (iron, copper, limestone, coal, caterium, quartz) are placed on the surface. They're unbreakable. Each one has a purity (impure/normal/pure).
   - **Right-click** a node to hand-mine it (a pure node gives more per click).
   - Iron, copper and coal nodes give vanilla `raw_iron`, `raw_copper` and `coal`, so you can also smelt early on in a vanilla furnace.
3. **Craft Bench**: hand-craft the parts you've unlocked (shift-click crafts ×5).
4. **HUB**: pick a milestone and click **Submit** to deposit items from your inventory. You can deposit in several trips.
   - Tier 0 (HUB Upgrades 1–6) is sequential. Upgrade 6 opens Tiers 1–2.
   - Delivering **Space Elevator Phase 1** (50 Smart Plating) opens Tiers 3–4.
   - Delivering **Phase 2** (1000 Smart Plating, 1000 Versatile Framework, 100 Automated Wiring) is the ending.
   - Completing a milestone pops up a notification at the top right with the unlocked items.
5. **Build Gun** (right-click): turns materials into unlocked buildings. The window has category tabs (Basics, Production, Power, Logistics, Fluids, Structures, Trains) with a count per tab and a name search box; shift-click builds 5. The gun is a 3D model and is held like a tool pointing forward.
6. **Machines**: pick a recipe with the `<` `>` buttons. The front of a machine (the side facing away from you when you placed it) is the **output**, and it pushes items into a belt or machine placed there. Inputs are accepted from the other five sides, including hoppers.
7. **Conveyor belts**: Mk.1/2/3 = 60/120/270 items/min.
   - **Curves and ramps connect automatically, like rails.** Both ends of a belt snap on their own: the input end turns toward a belt pointing at it, and an unconnected output end turns toward a belt waiting for input.
   - Curves have a rounded outer corner.
   - If there's a belt one block higher in front, the belt becomes an up ramp. If a higher belt behind flows into it, it becomes a down ramp.
   - Place while sneaking to build a vertical lift (look up or down while placing).
   - Right-click a belt to take the items off it. Dropped items that land on a belt are picked up.
   - Splitter: input at the back, round-robin output to left/front/right. Merger: input on three sides, output at the front.
8. **Power**:
   - Right-click two connection points in turn with **Cable** to string a power line (1 cable per 10 blocks, max 40 blocks).
   - Connection points are machines, generators and power poles. Sneak + right-click removes all lines from that point.
   - Biomass Burner = 30 MW (leaves, logs, biomass, solid biofuel). Coal Generator = 75 MW (coal; needs a water source block next to it).
   - Fuel burns in proportion to grid load.
   - **Fuse**: if consumption exceeds capacity, the whole grid stops. An alert sound plays and a notification pops up at the top right.
   - Reset the fuse from a generator's GUI or by **right-clicking a power pole**.
   - The pole GUI shows capacity, consumption, max consumption, load and building counts, plus a 60-second power graph.
9. Operator commands: `/ficsit unlockall`, `/ficsit reset`, `/ficsit unlock <n>`, `/ficsit status`.

### Fluids (pipes)
- **Water Extractor**: a 3×3×3 building placed over water (or lava). It supplies 120 m³/min to pipelines at 20 MW.
- **Pipelines Mk.1/Mk.2**: 300/600 m³/min, 1/2 m³ per block.
  - They connect automatically to other pipes, pumps and fluid buildings.
  - The fluid is visible through the glass walls, coloured by type (water blue, lava orange).
- **Pipeline Pump**: an in-line pump. Place it facing the direction of flow; it can also face up or down. It adds 20 blocks of head lift and draws 4 MW. It only lets fluid through one way.
- **Coal Generators** need a steady **45 m³/min of water** at full load through a pipeline (20 m³ internal tank).
- Physics:
  - Pressure head = height + fill level. Fluid drains downhill and levels out between connected pipes, but can't climb on its own.
  - The pressure field inside full pipe regions is solved, so connected vessels level out.
  - **Head lift** limits how high a pump can push (extractor 10 blocks, pump 20 blocks).
  - Each pipe connection carries **flow momentum**, so levels overshoot and swing before settling (**sloshing**).
- Right-click a pipe for its contents and flow rate. Sneak + right-click empties the whole connected network (flush).
- **Fluid Buffer**: a 3×3×4 tank holding 400 m³. Pipes can attach on any side.
  - Its level acts as pressure head (base height + fill × 4), so it fills from above its level and drains below it.
  - Its GUI shows the level gauge, stored volume, inflow/outflow and a flush button. The windows on the tank show the level too.

### Trains (Tier 4: "Railway Technology" and "Train Logistics")
A Satisfactory-style railway with free-form curved tracks, stations, freight platforms, signals, a timetable and an autopilot.

- **Tracks (Railway)**: right-click the ground to start, right-click again to finish.
  - The track is a smooth curve. A free end automatically bends into an arc; the tangent at a snapped end continues the track it snaps to.
  - It snaps to existing track ends (blue marker) and can **branch off the middle** of an existing track (that creates a junction/switch).
  - After each piece the end becomes the next start, so you can lay a whole line by clicking along it. Sneak + right-click cancels.
  - A preview shows the piece (blue = OK, red = not possible: too steep, too sharp, too long, ...) and its cost: 1 Railway per 6 blocks, max 64 blocks per piece. Curves may be tight: the smallest radius is 4.5 blocks, so a 90 degree corner fits in about 7x7 blocks.
  - **X** dismantles the track, signal or vehicle under the crosshair (a track with a train on it can't be removed). A **left click** on a locomotive / car breaks it like a boat or minecart (the train must stand still).
- **Switches**: where a track end has several branches, a lever stand appears beside it. Right-click the lever to change the branch that trains without a route take. The autopilot chooses its own branches.
- **Train Station** (5x9): the named stop of the railway. It embeds a piece of track, connects to power poles and gives the network its power: **locomotives draw their power (up to 110 MW each) through a powered station or platform on the same network**. Right-click to rename. Overloading the grid blows the fuse like everything else.
- **Freight Platform / Fluid Freight Platform / Empty Platform** (5x9): chain them end to end next to the station and to ordinary track. The corridor the train drives through is open: you can walk through and along it, only the visible parts of the buildings are solid. A stopped freight car (or fluid freight car) whose centre is on a platform is loaded / unloaded. **Right-click** a platform to open its window: choose *Load train* (platform to car) or *Unload train* (car to platform) and read what it is doing right now (no power, no car docked, transferring, done, waiting for items, platform/tank full ...). Loading counts as finished as soon as the platform / tank runs empty after it has handed over cargo, or when the car is full. The freight platform window also holds its 36 item slots, the fluid platform window shows the tank and the docked car's fluid. Unloaded items are pushed into belts, chests or hoppers next to either long side of the freight platform (any row); pipes connect to the fluid platform.
- **Locomotive / Freight Car / Fluid Freight Car**: right-click next to a track to put it on the rails. Next to a train end it couples on. Right-click a vehicle to open the train menu; sneak + right-click a freight car to open its 36 slots. Fluid cars carry 800 m³.
- **Driving**: menu -> *Drive* (or **G** while driving). **W/S** raise/lower the throttle lever (below 0 = reverse), **Space** brakes, **H** honks, **Shift** gets you out. Trains accelerate according to the number of locomotives and the load, slow down for tight curves and steep hills, and brake before dead ends.
- **Signals**: *Block Signal* splits the line into blocks - a train only enters a free block. *Path Signal* reserves the whole route through a junction so crossing trains don't block each other. Right-click a track (or a track end) while looking the way the signal should guard. Lamps show red/green.
- **Timetable and Autopilot**: in the train menu add stops (station names) with any wait time from 1 to 3600 seconds (type it into the number field, Enter or a click elsewhere applies it) or the **Cargo** mode: the train waits until every platform with a car docked at that station has finished (loading ends when the platform runs empty or the car is full - so a belt that keeps feeding the platform cannot hold the train forever - unloading ends when the car is empty) and leaves after the stop's time limit at the latest (0 = no limit, default 120 s). Hover the mode buttons for an explanation. The wait time / limit and the mode of every stop already in the list can be changed later in place. Stops of stations that no longer exist are shown in red; renaming a station renames it in the timetables, dismantling it removes its stops. Then switch the Autopilot on. You can sit in the cab of a train that is on autopilot: it keeps running as a passenger ride until you use W / S / Space, which takes the controls (autopilot pauses; leaving the cab resumes it). The cab passes through blocks without pushing you out or suffocating you. The train finds the shortest route (through junctions, and reversing at terminal stations if that is shorter), stops exactly at the station and shuttles forever. Trains never drive through each other.
- **Effects**: the wheels of all cars roll, moving trains rumble and clack (pitch and volume follow the speed, locomotives add a motor hum) and hard braking makes the wheels squeal with sparks flying off the rails. Sounds are synthesised by `tools/gen_sounds.py`.
- Notes: a train only moves while its area is loaded; power is taken from the network's last known state if the station is in an unloaded chunk.

### Creative Generator (testing)
A fuel-free 3000 MW power source, a 3x3x4 multi-block building with a glowing energy core and tesla spire (Buildings tab). It is not craftable, not part of the build gun and can only be placed by a player in creative mode. Right-click opens its little window: a pulsing energy core (click it for a message from FICSIT), the live grid numbers, an output ON/OFF switch and the fuse reset. Connect it with power lines like any generator; right-click resets the fuse and shows the grid state.

### Resource scanner (V)
- **Tap V**: scans for the resource you last selected (default: all).
- **Hold V**: opens a radial wheel. Point at a resource and release V (or click).
- A glowing pulse wave expands out to 100 m around you.
- Each node the wave reaches gets a light beam in its resource colour. You also get an on-screen marker, visible through walls, showing the icon, purity and distance for 30 seconds.
- You can change the key under Controls → FICSIT Craft.

### Zipline
- Hold the Zipline and jump toward a power line (or right-click near one) to grab it.
- **W/S** moves along the line in the direction you're looking. You switch automatically to the connected line at a pole.
- **Space** jumps off (keeping your momentum). **Sneak** lets go.
- The Zipline is a 3D handle with a pulley wheel. While riding, the arm holding it is raised to the cable in first person (the hand with the pulley at the top of the screen) and in third person (also for other players), electric sparks fly from the contact point and your body swings with acceleration. The camera stays in the view you chose.
- Craft it at the Craft Bench after Logistics Mk.2.

### Buildings and placement
- Machines are **multi-block buildings**, like in Satisfactory. Size in blocks (W×D×H):
  - Smelter 2×3×3, Constructor 3×3×3, Foundry 3×3×3, Assembler 3×5×4, Manufacturer 5×6×4
  - Miner 3×3×5 (centred on the resource node)
  - Biomass Burner 3×3×3, Coal Generator 3×5×6
  - HUB 3×4×3, Craft Bench 2×1×2, Storage Container 2×3×2
- While you hold a building, a **hologram preview** shows where it will go: blue if it can be placed, red if it can't.
  - The orange marker on the ground is the output port.
  - The building extends forward (the direction you're looking) from the block you place, and the front face is the output.
- Right-click any part of a building to open its GUI. Belts can feed in from any side except the front.
- Breaking any part removes the whole building and drops the building item.
- **Building art**: every face of every building is painted as its own texture at the face's real size and stretched once over it. No per-block tiling, so logos, stencils, panel seams, vents, consoles and weathering (grime, streaks, chipped paint) appear exactly once, where they belong. Each building has its own character:
  - Smelter: heat shields and furnace windows.
  - Foundry: brick crucibles.
  - Coal Generator: a brick stack and a water tank.
  - Manufacturer: a white hall.
  - HUB: a cargo container with a milestone terminal.
- Furnace windows, screens and fans glow or spin while the building runs, and control consoles light up.
- **Building items are 3D models**: in the inventory and in your hand they show a small version of the building, so you can tell which building it is at a glance.

## Content summary

| Category | Content |
|---|---|
| Resources | 6 resource node types × 3 purities, world generation |
| Production | Miner Mk.1/2, Smelter, Foundry, Constructor, Assembler, Manufacturer |
| Logistics | Conveyor Belt Mk.1–3 (automatic curves/ramps, vertical lifts), Splitter, Merger, Storage Container |
| Power | Biomass Burner, Coal Generator, tall Power Poles Mk.1–3 (4/5/6 blocks), power lines, fuse |
| Progression | HUB (17 milestones, Tier 0–4, split deposits), Craft Bench, Build Gun |
| Trains | Curved tracks, switches, block/path signals, Train Station, Freight / Fluid / Empty Platforms, Locomotive, Freight Car, Fluid Freight Car, timetable + autopilot |
| Parts | 29 parts, 30 production recipes (Satisfactory 1.0 numbers) |

## Differences from Satisfactory

- Stack sizes follow Minecraft (64), so the Heavy Modular Frame screw cost is reduced from 120 to 60.
- There's no overclocking or MAM.
- Instead of a water extractor, the Coal Generator needs a water source block next to it.
- Tiers 3–4 open when Phase 1 is delivered, from the HUB list (there's no separate Space Elevator building).

## Code structure

```
src/main/java/com/ficsitcraft
  data/        recipes, milestones, building costs (edit here to rebalance)
  power/       power network nodes + per-tick grid solver (PowerGridManager)
  block/ blockentity/   machines, belts, logistics, nodes
  screen/      screen handlers (server logic)
  progress/    world-wide HUB progress (PersistentState)
  worldgen/    resource node feature
src/client/java/...     GUIs, belt item renderer, power line renderer
tools/          texture / JSON / lang generator scripts (Python + Pillow)
tools/test/     headless tests of the rail + train simulation (run.sh), asset checks
src/main/java/com/ficsitcraft/rail, train   pure-Java track graph + train simulation (no Minecraft dependency)
src/main/java/com/ficsitcraft/railway       world state, networking, cargo
```

To change textures or languages, edit `tools/gen_textures.py`, `tools/gen_json.py` or `tools/gen_lang.py` and run it from the project root.

Run order for everything: `gen_textures.py` -> `gen_json.py` -> `gen_buildings.py` -> `gen_trains.py` -> `squarify.py` -> `gen_lang.py`. (`squarify.py` is essential: Minecraft only loads square block textures, a 32x16 PNG shows up cropped or as the purple/black missing texture.) Test the train simulation without Minecraft with `tools/test/run.sh`.

`tools/gen_buildings.py` generates three things together, so they always match:
- A texture for every building face (`textures/block/bld/<building>/`, painter in `tools/bldart.py`)
- The building geometry (`assets/ficsitcraft/buildings/*.json`), which the renderer and hologram read
- The 3D item models

If you regenerate the JSON, run it in order: `gen_json.py` → `gen_buildings.py`.
