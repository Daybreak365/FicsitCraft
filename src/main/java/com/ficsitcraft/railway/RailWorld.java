package com.ficsitcraft.railway;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailPlacement;
import com.ficsitcraft.rail.RailTrack;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.registry.ModItems;
import com.ficsitcraft.blockentity.RailBuildingBlockEntity;
import com.ficsitcraft.train.Goal;
import com.ficsitcraft.train.Stop;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.TrainSim;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The railway of one dimension: track graph, trains, station registry and the per-tick simulation glue
 * (chunk loading, power, syncing). Saved with the world.
 */
public final class RailWorld extends PersistentState {
	private static final PersistentState.Type<RailWorld> TYPE = new PersistentState.Type<>(RailWorld::new, RailWorld::fromNbt, null);

	public record Station(String name, long track) {
	}

	/** A powered rail building that locomotives draw their power through. */
	public static final class Tap {
		long track;
		boolean powered;
		long lastTick;
		double extra;
	}

	public RailGraph graph = new RailGraph();
	public final TrainSim sim = new TrainSim(graph);
	public final List<Train> trains = new ArrayList<>();
	public final Map<Long, Station> stations = new HashMap<>();
	private final Map<Long, Tap> taps = new HashMap<>();
	private final Map<Long, RailBuildingBlockEntity> platforms = new HashMap<>();
	/** player -> {train, loco vehicle} currently driving (not saved). */
	public final Map<UUID, UUID[]> riders = new HashMap<>();

	private boolean occStale;
	private int syncedRevision = -1;
	private int validatedRevision = -1;
	private int compRevision = -1;
	private final Map<Long, Integer> components = new HashMap<>();
	private final Map<Long, Boolean> signalStates = new HashMap<>();

	public RailWorld() {
		wire();
	}

	private void wire() {
		sim.stations = name -> {
			List<Goal> goals = new ArrayList<>();
			for (Station s : stations.values()) {
				if (!s.name().equalsIgnoreCase(name)) continue;
				RailTrack t = graph.tracks.get(s.track());
				if (t == null) continue;
				goals.add(new Goal(t.id, true, t.length()));
				goals.add(new Goal(t.id, false, t.length()));
			}
			return goals;
		};
		sim.hooks = (train, station) -> {
			for (RailBuildingBlockEntity p : platforms.values()) {
				if (!p.isRemoved() && p.isBusyWith(train.id)) return false;
			}
			return true;
		};
	}

	public static RailWorld get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, "ficsitcraft_rails");
	}

	// ------------------------------------------------------------------------------------------------ persistence

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		try {
			nbt.putByteArray("Graph", RailCodec.encodeGraph(graph));
			NbtList list = new NbtList();
			for (Train t : trains) {
				ByteArrayOutputStream bo = new ByteArrayOutputStream();
				t.write(new DataOutputStream(bo));
				NbtCompound tn = new NbtCompound();
				tn.putByteArray("Core", bo.toByteArray());
				NbtList cargo = new NbtList();
				for (Vehicle v : t.vehicles) {
					NbtCompound cn = new NbtCompound();
					VehicleCargo c = VehicleCargo.of(v);
					if (c != null) c.write(cn, lookup);
					cargo.add(cn);
				}
				tn.put("Cargo", cargo);
				list.add(tn);
			}
			nbt.put("Trains", list);
		} catch (IOException e) {
			FicsitCraft.LOGGER.error("Failed to save the railway", e);
		}
		NbtList st = new NbtList();
		for (Map.Entry<Long, Station> e : stations.entrySet()) {
			NbtCompound s = new NbtCompound();
			s.putLong("Pos", e.getKey());
			s.putString("Name", e.getValue().name());
			s.putLong("Track", e.getValue().track());
			st.add(s);
		}
		nbt.put("Stations", st);
		return nbt;
	}

	private static RailWorld fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		RailWorld w = new RailWorld();
		try {
			if (nbt.contains("Graph")) {
				w.graph = RailCodec.decodeGraph(nbt.getByteArray("Graph"));
				w.rewire();
			}
			NbtList list = nbt.getList("Trains", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound tn = list.getCompound(i);
				Train t = Train.read(new DataInputStream(new ByteArrayInputStream(tn.getByteArray("Core"))));
				NbtList cargo = tn.getList("Cargo", NbtElement.COMPOUND_TYPE);
				for (int k = 0; k < t.vehicles.size(); k++) {
					Vehicle v = t.vehicles.get(k);
					if (v.type == VehicleType.LOCOMOTIVE) continue;
					v.cargo = k < cargo.size() ? VehicleCargo.read(cargo.getCompound(k), lookup) : new VehicleCargo();
					v.load = ((VehicleCargo) v.cargo).fill(v.type);
				}
				w.trains.add(t);
			}
		} catch (IOException e) {
			FicsitCraft.LOGGER.error("Failed to load the railway", e);
		}
		NbtList st = nbt.getList("Stations", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < st.size(); i++) {
			NbtCompound s = st.getCompound(i);
			w.stations.put(s.getLong("Pos"), new Station(s.getString("Name"), s.getLong("Track")));
		}
		return w;
	}

	/** The simulation holds a reference to the graph; call after replacing it. */
	private void rewire() {
		// TrainSim.graph is final, so rebuild the sim contents through reflection-free copy
		sim.replaceGraph(graph);
	}

	// ------------------------------------------------------------------------------------------------ registries

	public void registerStation(long pos, String name, long track) {
		Station prev = stations.get(pos);
		Station now = new Station(name, track);
		if (now.equals(prev)) return;
		stations.put(pos, now);
		// a renamed station keeps its place in every timetable that referred to the old name
		if (prev != null && !prev.name().equalsIgnoreCase(name)) retargetStops(prev.name(), name);
		markDirty();
	}

	public void unregisterStation(long pos) {
		Station gone = stations.remove(pos);
		if (gone == null) return;
		pruneStops(gone.name());
		markDirty();
	}

	private boolean stationNameInUse(String name) {
		for (Station s : stations.values()) if (s.name().equalsIgnoreCase(name)) return true;
		return false;
	}

	/** Timetable entries of a station that was renamed (and no other station still carries the old name) follow the new name. */
	private void retargetStops(String oldName, String newName) {
		if (stationNameInUse(oldName)) return;
		for (Train t : trains) {
			boolean changed = false;
			for (int i = 0; i < t.timetable.size(); i++) {
				Stop st = t.timetable.get(i);
				if (st.station().equalsIgnoreCase(oldName)) {
					t.timetable.set(i, new Stop(newName, st.mode(), st.seconds()));
					changed = true;
				}
			}
			if (changed) t.dirty = true;
		}
	}

	/** Timetable entries of a station that no longer exists (dismantled) are removed. */
	private void pruneStops(String name) {
		if (stationNameInUse(name)) return;
		for (Train t : trains) {
			boolean changed = false;
			for (int i = t.timetable.size() - 1; i >= 0; i--) {
				if (!t.timetable.get(i).station().equalsIgnoreCase(name)) continue;
				t.timetable.remove(i);
				if (i < t.stopIndex) t.stopIndex--;
				else if (i == t.stopIndex) {
					t.autoState = 0;
					t.docked = false;
					t.goal = null;
					t.dwell = 0;
				}
				changed = true;
			}
			if (!changed) continue;
			if (t.timetable.isEmpty()) {
				t.autopilot = false;
				t.stopIndex = 0;
			} else if (t.stopIndex >= t.timetable.size()) {
				t.stopIndex = 0;
			}
			t.dirty = true;
		}
	}

	/** The lowest "Station N" that is not taken yet. */
	public String freeStationName() {
		for (int i = 1; ; i++) {
			String n = "Station " + i;
			if (!stationNameInUse(n)) return n;
		}
	}

	public List<String> stationNames() {
		List<String> names = new ArrayList<>();
		for (Station s : stations.values()) if (!names.contains(s.name())) names.add(s.name());
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	public void reportTap(long pos, long track, boolean powered, long now) {
		Tap t = taps.computeIfAbsent(pos, k -> new Tap());
		t.track = track;
		t.powered = powered;
		t.lastTick = now;
	}

	public void unregisterTap(long pos) {
		taps.remove(pos);
	}

	public double tapExtra(long pos) {
		Tap t = taps.get(pos);
		return t == null ? 0 : t.extra;
	}

	public void registerPlatform(long pos, RailBuildingBlockEntity be) {
		platforms.put(pos, be);
	}

	public void unregisterPlatform(long pos) {
		platforms.remove(pos);
	}

	// ------------------------------------------------------------------------------------------------ lookups

	public Train findTrain(UUID id) {
		for (Train t : trains) if (t.id.equals(id)) return t;
		return null;
	}

	public boolean isOccupied(long track) {
		for (Train t : trains) for (Dir d : t.path) if (d.track() == track) return true;
		return false;
	}

	public V3 vehiclePos(Train t, int index) {
		return t.pointAtU(graph, t.vehicleCenterU(index));
	}

	public record Docked(Train train, Vehicle vehicle, int index) {
	}

	/** A stopped vehicle of the given type whose centre is within {@code radius} of the point. */
	public Docked dockedNear(V3 point, double radius, VehicleType type) {
		Docked best = null;
		double bd = radius;
		for (Train t : trains) {
			if (Math.abs(t.speed) > 0.02) continue;
			for (int i = 0; i < t.vehicles.size(); i++) {
				Vehicle v = t.vehicles.get(i);
				if (v.type != type) continue;
				double d = vehiclePos(t, i).distanceTo(point);
				if (d < bd) {
					bd = d;
					best = new Docked(t, v, i);
				}
			}
		}
		return best;
	}

	private int componentOf(long track) {
		if (compRevision != graph.revision) {
			components.clear();
			compRevision = graph.revision;
			int next = 1;
			for (long id : graph.tracks.keySet()) {
				if (components.containsKey(id)) continue;
				int c = next++;
				ArrayList<Long> stack = new ArrayList<>();
				stack.add(id);
				components.put(id, c);
				while (!stack.isEmpty()) {
					RailTrack t = graph.tracks.get(stack.remove(stack.size() - 1));
					if (t == null) continue;
					for (long nid : new long[]{t.nodeA, t.nodeB}) {
						RailNode n = graph.nodes.get(nid);
						if (n == null) continue;
						for (int s = 0; s < 2; s++) {
							for (long o : n.side(s)) if (components.putIfAbsent(o, c) == null) stack.add(o);
						}
					}
				}
			}
		}
		return components.getOrDefault(track, 0);
	}

	// ------------------------------------------------------------------------------------------------ building tracks

	/** Creates the track a station / platform embeds between its two ends. Returns the track id or -1. */
	public long createBuildingTrack(V3 back, V3 front, V3 axis, long owner) {
		RailNode a = attachNode(back, axis, owner);
		RailNode b = attachNode(front, axis, owner);
		// the embedded track leaves the back node in +axis and the front node in -axis
		int sideA = sideFor(a, axis);
		int sideB = sideFor(b, axis.neg());
		if (!graph.canAttach(a.id, sideA) || !graph.canAttach(b.id, sideB)) return -1;
		RailTrack t = graph.connect(a.id, sideA, b.id, sideB, back.lerp(front, 1 / 3.0), back.lerp(front, 2 / 3.0), owner);
		markDirty();
		return t.id;
	}

	private RailNode attachNode(V3 pos, V3 axis, long owner) {
		RailNode n = graph.nearestNode(pos, 0.6, 0.6);
		if (n != null && Math.abs(n.dir.dot(axis)) > 0.98) return n;
		return graph.addNode(pos, axis, owner);
	}

	/** Side of the node a track leaving in {@code dir} hangs on. */
	private static int sideFor(RailNode n, V3 dir) {
		return n.dir.dot(dir) >= 0 ? RailNode.FRONT : RailNode.BACK;
	}

	public void removeBuildingTrack(ServerWorld world, long owner) {
		unregisterStation(owner);
		unregisterTap(owner);
		unregisterPlatform(owner);
		graph.removeOwner(owner);
		markDirty();
		validateTrains(world);
	}

	/** Trains that lost their track (a building was removed) are dismantled into items. */
	public void validateTrains(ServerWorld world) {
		List<Train> gone = new ArrayList<>();
		for (Train t : trains) {
			boolean ok = !t.path.isEmpty();
			for (Dir d : t.path) if (!graph.tracks.containsKey(d.track())) ok = false;
			if (!ok) gone.add(t);
		}
		for (Train t : gone) dropTrain(world, t);
		validatedRevision = graph.revision;
	}

	public void dropTrain(ServerWorld world, Train t) {
		V3 p = t.path.isEmpty() ? V3.ZERO : t.pointAtU(graph, t.tailOffset);
		for (Vehicle v : t.vehicles) dropVehicleItems(world, v, p);
		removeTrain(world, t);
	}

	public void dropVehicleItems(ServerWorld world, Vehicle v, V3 at) {
		spawn(world, at, new ItemStack(itemFor(v.type)));
		VehicleCargo c = VehicleCargo.of(v);
		if (c != null) for (ItemStack s : c.items) if (!s.isEmpty()) spawn(world, at, s.copy());
	}

	private static void spawn(ServerWorld world, V3 at, ItemStack stack) {
		ItemEntity e = new ItemEntity(world, at.x(), at.y() + 1, at.z(), stack);
		world.spawnEntity(e);
	}

	public static Item itemFor(VehicleType type) {
		return switch (type) {
			case LOCOMOTIVE -> ModItems.LOCOMOTIVE;
			case FREIGHT_CAR -> ModItems.FREIGHT_CAR;
			case FLUID_CAR -> ModItems.FLUID_FREIGHT_CAR;
		};
	}

	public void removeTrain(ServerWorld world, Train t) {
		trains.remove(t);
		sim.trains.remove(t);
		sim.frozen.remove(t);
		riders.values().removeIf(r -> r[0].equals(t.id));
		occStale = true;
		RailNet.sendTrainRemove(world, t.id);
		markDirty();
	}

	public void addTrain(Train t) {
		trains.add(t);
		t.dirty = true;
		markDirty();
	}

	// ------------------------------------------------------------------------------------------------ tick

	public static void onWorldTick(ServerWorld world) {
		RailWorld rw = get(world);
		rw.tick(world);
	}

	private boolean chunkLoaded(ServerWorld world, V3 p) {
		return world.getChunkManager().isChunkLoaded(((int) Math.floor(p.x())) >> 4, ((int) Math.floor(p.z())) >> 4);
	}

	private boolean active(ServerWorld world, Train t) {
		return chunkLoaded(world, t.pointAtU(graph, t.tailOffset)) && chunkLoaded(world, t.pointAtU(graph, t.headU()));
	}

	private void tick(ServerWorld world) {
		long now = world.getTime();
		if (validatedRevision != graph.revision) validateTrains(world);
		if (!trains.isEmpty()) {
			sim.trains.clear();
			sim.frozen.clear();
			Map<Integer, Boolean> compPowered = new HashMap<>();
			for (Train t : trains) {
				if (t.vehicles.isEmpty() || t.path.isEmpty()) continue;
				if (active(world, t)) {
					sim.trains.add(t);
					int c = componentOf(t.path.get(t.path.size() - 1).track());
					t.powered = compPowered.computeIfAbsent(c, this::componentPowered);
				} else {
					sim.frozen.add(t);
					t.speed = 0;
				}
			}
			sim.tick();
			// power draw of all locomotives goes to one live building of the same network
			for (Tap tap : taps.values()) tap.extra = 0;
			Map<Integer, Double> draw = new HashMap<>();
			for (Train t : sim.trains) {
				if (t.powerDraw <= 0) continue;
				draw.merge(componentOf(t.path.get(t.path.size() - 1).track()), t.powerDraw, Double::sum);
			}
			for (Map.Entry<Integer, Double> e : draw.entrySet()) {
				Tap best = null;
				for (Tap tap : taps.values()) {
					if (componentOf(tap.track) != e.getKey()) continue;
					if (best == null || tap.lastTick > best.lastTick) best = tap;
				}
				if (best != null) best.extra = e.getValue();
			}
			if (now % 20 == 0) {
				updateLoads();
				markDirty();
			}
			maintainRiders(world);
			markDirtyIfMoving();
		}
		if (trains.isEmpty() && occStale) {
			// forget the occupancy of the last train so signals turn green again
			sim.trains.clear();
			sim.frozen.clear();
			sim.tick();
			occStale = false;
		}
		RailNet.tick(world, this, now);
	}

	private boolean componentPowered(int comp) {
		for (Tap tap : taps.values()) if (tap.powered && componentOf(tap.track) == comp) return true;
		return false;
	}

	private void markDirtyIfMoving() {
		for (Train t : trains) {
			if (Math.abs(t.speed) > 0) {
				markDirty();
				return;
			}
		}
	}

	private void updateLoads() {
		for (Train t : trains) {
			for (Vehicle v : t.vehicles) {
				VehicleCargo c = VehicleCargo.of(v);
				if (c == null) continue;
				double f = c.fill(v.type);
				if (Math.abs(f - v.load) > 0.005) {
					v.load = f;
					t.dirty = true;
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ riders

	private void maintainRiders(ServerWorld world) {
		if (riders.isEmpty()) return;
		List<UUID> drop = new ArrayList<>();
		for (Map.Entry<UUID, UUID[]> e : riders.entrySet()) {
			ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(e.getKey());
			Train t = findTrain(e.getValue()[0]);
			if (p == null || t == null || p.getServerWorld() != world || p.isSpectator() || !p.isAlive()) {
				drop.add(e.getKey());
				continue;
			}
			// client-driven movement: keep the anti-cheat quiet (same trick as the zipline)
			p.noClip = true;
			// late joiners / players who just came into range learn about the seated rider
			if (world.getTime() % 40 == 0) broadcastSeat(p, true);
			p.fallDistance = 0;
			p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.LEVITATION, 5, 0, false, false, false));
		}
		for (UUID id : drop) stopRiding(world, id);
	}

	/** Tells everyone who can see the player whether they sit in a cab (seated pose for the other clients). */
	private static void broadcastSeat(ServerPlayerEntity p, boolean seated) {
		com.ficsitcraft.network.TrainSeatPayload payload = new com.ficsitcraft.network.TrainSeatPayload(p.getId(), seated);
		for (ServerPlayerEntity other : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(p)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other, payload);
		}
	}

	public void startRiding(ServerWorld world, ServerPlayerEntity p, Train t, Vehicle loco) {
		stopRiding(world, p.getUuid());
		if (t.driver != null && !t.driver.equals(p.getUuid())) return;
		// Riding an autopilot train only makes the player a passenger: the autopilot keeps running until they touch the
		// throttle / brake (RailNet DRIVE_INPUT sets driven then).
		t.driven = !t.autopilot;
		t.driver = p.getUuid();
		RailRiders.add(p.getUuid());
		broadcastSeat(p, true);
		t.manualCmd = 0;
		t.brake = false;
		riders.put(p.getUuid(), new UUID[]{t.id, loco.id});
		t.dirty = true;
		RailNet.sendRide(p, t.id, loco.id, true);
	}

	public void stopRiding(ServerWorld world, UUID player) {
		UUID[] r = riders.remove(player);
		RailRiders.remove(player);
		if (r == null) return;
		Train t = findTrain(r[0]);
		if (t != null && player.equals(t.driver)) {
			t.driven = false;
			t.driver = null;
			t.manualCmd = 0;
			t.brake = !t.autopilot;
			t.dirty = true;
		}
		ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(player);
		if (p != null) {
			broadcastSeat(p, false);
			p.noClip = false;
			p.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION);
			RailNet.sendRide(p, r[0], r[1], false);
		}
	}

	// ------------------------------------------------------------------------------------------------ helpers for signals

	public Map<Long, Boolean> signalStates() {
		return signalStates;
	}

	public Set<Long> allSignalKeys() {
		Set<Long> keys = new HashSet<>();
		for (RailNode n : graph.nodes.values()) {
			if (n.signalFront != 0) keys.add(n.id * 2);
			if (n.signalBack != 0) keys.add(n.id * 2 + 1);
		}
		return keys;
	}

	int syncedRevision() {
		return syncedRevision;
	}

	void setSyncedRevision(int r) {
		syncedRevision = r;
	}

	// ------------------------------------------------------------------------------------------------ player actions

	public void dropTrainToPlayer(ServerWorld world, ServerPlayerEntity p, Train t) {
		V3 at = t.path.isEmpty() ? V3.ZERO : t.pointAtU(graph, t.tailOffset);
		for (Vehicle v : t.vehicles) dropVehicleToPlayer(world, p, v, at);
		removeTrain(world, t);
	}

	public void dropVehicleToPlayer(ServerWorld world, ServerPlayerEntity p, Vehicle v, V3 at) {
		if (!p.isCreative()) p.getInventory().offerOrDrop(new ItemStack(itemFor(v.type)));
		VehicleCargo c = VehicleCargo.of(v);
		if (c != null) for (ItemStack s : c.items) if (!s.isEmpty()) p.getInventory().offerOrDrop(s.copy());
	}

	/** Places a vehicle on the track next to {@code hit} or couples it to a nearby train. Returns an error key or null. */
	public String placeVehicle(ServerWorld world, VehicleType type, V3 hit, double lookX, double lookZ) {
		RailGraph.Hit h = graph.nearestTrack(hit, 3.2);
		if (h == null) return "no_track_here";
		RailTrack tr = graph.tracks.get(h.track());
		V3 tan = tr.curve.tangentAtDist(h.dist());
		boolean fwd = tan.x() * lookX + tan.z() * lookZ >= 0;
		Vehicle v = new Vehicle(type);
		VehicleCargo.ensure(v);
		double len = type.length;
		for (Train t : trains) {
			if (t.driver != null || Math.abs(t.speed) > 0.02) continue;
			V3 headP = t.pointAtU(graph, t.headU()), tailP = t.pointAtU(graph, t.tailOffset);
			double dh = headP.distanceTo(hit), dt = tailP.distanceTo(hit);
			boolean atHead = dh <= dt;
			if (Math.min(dh, dt) > len + 4) continue;
			V3 endTan = t.tangentAtU(graph, atHead ? t.headU() : t.tailOffset);
			v.facing = endTan.x() * lookX + endTan.z() * lookZ >= 0 ? 1 : -1;
			if (!t.addVehicle(graph, RailGraph.SWITCH_STATE, v, atHead)) return "no_room";
			t.autopilot = false;
			t.autoState = 0;
			markDirty();
			return null;
		}
		Train t = new Train();
		t.path.add(new Dir(tr.id, fwd));
		double center = fwd ? h.dist() : tr.length() - h.dist();
		t.tailOffset = center - len / 2;
		while (t.tailOffset < 0) if (!t.extendBack(graph, RailGraph.SWITCH_STATE)) return "no_room";
		t.vehicles.add(v);
		while (t.pathLength(graph) - t.headU() < -1e-9) if (!t.extendFront(graph, RailGraph.SWITCH_STATE)) return "no_room";
		t.trim(graph);
		if (overlapsOthers(t)) return "occupied";
		addTrain(t);
		return null;
	}

	private boolean overlapsOthers(Train t) {
		List<double[]> mine = new ArrayList<>();
		List<Long> ids = new ArrayList<>();
		t.collectOccupancy(graph, (track, lo, hi) -> {
			ids.add(track);
			mine.add(new double[]{lo, hi});
		});
		for (Train o : trains) {
			if (o == t) continue;
			boolean[] hit = {false};
			o.collectOccupancy(graph, (track, lo, hi) -> {
				for (int i = 0; i < ids.size(); i++) {
					if (ids.get(i) == track && mine.get(i)[0] < hi + 0.3 && lo < mine.get(i)[1] + 0.3) hit[0] = true;
				}
			});
			if (hit[0]) return true;
		}
		return false;
	}

	/** Builds the track of a resolved placement. Returns the id of the end node, -1 if it could not be built, -2 if a train is in the way. */
	public long commitTrack(RailPlacement.Result r) {
		RailPlacement.Anchor a = r.a(), b = r.b();
		if (a.isSplit() && isOccupied(a.splitTrack())) return -2;
		if (b.isSplit() && isOccupied(b.splitTrack())) return -2;
		long nodeA, nodeB;
		int sideA, sideB;
		if (a.isNode()) {
			nodeA = a.node();
			sideA = a.side();
		} else if (a.isSplit()) {
			long[] sp = graph.split(a.splitTrack(), a.splitDist());
			if (sp == null) return -1;
			nodeA = sp[0];
			sideA = graph.nodes.get(nodeA).dir.dot(a.leave()) >= 0 ? RailNode.FRONT : RailNode.BACK;
		} else {
			nodeA = graph.addNode(a.pos(), a.leave(), RailNode.NO_OWNER).id;
			sideA = RailNode.FRONT;
		}
		if (b.isNode()) {
			nodeB = b.node();
			sideB = b.side();
		} else if (b.isSplit()) {
			long[] sp = graph.split(b.splitTrack(), b.splitDist());
			if (sp == null) return -1;
			nodeB = sp[0];
			sideB = graph.nodes.get(nodeB).dir.dot(b.leave()) >= 0 ? RailNode.FRONT : RailNode.BACK;
		} else {
			V3 arrive = r.plan().curve().unitTangent(1);
			nodeB = graph.addNode(b.pos(), arrive, RailNode.NO_OWNER).id;
			sideB = RailNode.BACK;
		}
		if (!graph.canAttach(nodeA, sideA) || !graph.canAttach(nodeB, sideB)) return -1;
		graph.connect(nodeA, sideA, nodeB, sideB, r.plan().p1(), r.plan().p2(), RailNode.NO_OWNER);
		markDirty();
		return nodeB;
	}

	/** Places a signal on the node near {@code hit} (splitting a track if needed). Returns an error key or null. */
	public String placeSignal(V3 hit, double lookX, double lookZ, int type) {
		RailNode n = graph.nearestNode(hit, 2.0, 1.6);
		if (n == null) {
			RailGraph.Hit h = graph.nearestTrack(hit, 2.5);
			if (h == null) return "no_track_here";
			RailTrack t = graph.tracks.get(h.track());
			if (t == null || t.owner != RailNode.NO_OWNER) return "cant_signal_here";
			if (isOccupied(t.id)) return "rail_occupied";
			long[] sp = graph.split(t.id, Math.max(1.0, Math.min(t.length() - 1.0, h.dist())));
			if (sp == null) return "cant_signal_here";
			n = graph.nodes.get(sp[0]);
		}
		int side = n.dir.dot(new V3(lookX, 0, lookZ)) >= 0 ? RailNode.BACK : RailNode.FRONT;
		if (n.side(side).isEmpty()) return "signal_needs_track";
		if (n.signal(side) == type) return "signal_exists";
		n.setSignal(side, type);
		graph.revision++;
		markDirty();
		return null;
	}
}
