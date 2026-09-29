package com.ficsitcraft.client.railway;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.network.RailActionPayload;
import com.ficsitcraft.network.RailNetPayload;
import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailCodec;
import com.ficsitcraft.train.Stop;
import com.ficsitcraft.train.Train;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client copy of the railway: graph, trains (extrapolated between server updates), signal lamps, placement state. */
public final class ClientRail {
	public static RailGraph graph = new RailGraph();
	public static final Map<UUID, CTrain> trains = new HashMap<>();
	/** key = nodeId * 2 + arrival side, value = open (green). */
	public static final Map<Long, Boolean> signals = new HashMap<>();
	public static V3 pendingPos;
	public static V3 pendingLeave;
	public static int graphVersion;

	private static byte[][] parts;
	private static int gotParts;

	public static final class CTrain {
		public Train base;
		public long recv;
		private Train posed;
		private double posedAge = Double.NaN;
		/** Distance (blocks) the train has rolled along its path direction; drives the wheel animation. */
		public double roll;
		/** Ticks the train has been standing still (the wheels settle into a neat position). */
		public int still;
		/** Smoothed deceleration in blocks/tick^2 (positive while slowing down), from consecutive snapshots. */
		public double decel;
		/** World time at which the train was first seen derailed (-1 while it is on the rails): drives the falling-over animation. */
		public long derailedAt = -1;
	}

	/** A switch that was thrown recently: the blades slide from the old to the new branch. */
	public record SwitchAnim(int from, int to, long start) {
	}

	public static final Map<Long, SwitchAnim> switchAnims = new HashMap<>();

	/** Distance after which the stepped-octagon wheel looks the same again (half a turn of a 0.45 block wheel). */
	private static final double WHEEL_HALF_TURN = Math.PI * 0.45;

	public record CarInfo(int kind, int items, int slots, String fluid, float amount) {
	}

	/** Everything the train screen needs. */
	public static final class TrainInfo {
		public Train state;
		public int stopIndex;
		public int autoState;
		public String status;
		public boolean driverIsMe;
		public boolean hasDriver;
		public final List<Stop> timetable = new ArrayList<>();
		public final List<String> stations = new ArrayList<>();
		public final List<CarInfo> cars = new ArrayList<>();
		public double powerDraw;
		public double goalDist;

		public boolean autopilot() {
			return state != null && state.autopilot;
		}
	}

	private ClientRail() {
	}

	public static void reset() {
		graph = new RailGraph();
		trains.clear();
		pending.clear();
		signals.clear();
		switchAnims.clear();
		pendingPos = null;
		graphVersion++;
	}

	// ------------------------------------------------------------------------------------------ poses

	/** Snapshots received since the last client tick (newest per train). */
	private static final Map<UUID, Train> pending = new LinkedHashMap<>();

	/**
	 * Called at the very start of every client tick. Snapshots are applied only here, stamped with the time the world
	 * will have when this tick is over. Applying them the moment the packet arrived made the extrapolation phase depend
	 * on when in a tick the packet landed, so cars (and the driver's camera, which is computed once per tick) jumped
	 * back and forth by up to one tick of travel.
	 */
	public static void applyPending(MinecraftClient client) {
		if (pending.isEmpty()) return;
		if (client.world == null) {
			pending.clear();
			return;
		}
		long recv = client.world.getTime() + 1;
		for (Train t : pending.values()) {
			CTrain c = trains.computeIfAbsent(t.id, k -> new CTrain());
			if (c.base != null && c.recv > 0) {
				long dt = Math.max(1, recv - c.recv);
				double dv = (Math.abs(c.base.speed) - Math.abs(t.speed)) / dt;
				c.decel = c.decel * 0.6 + dv * 0.4;
			}
			if (t.derailed && (c.base == null || !c.base.derailed)) c.derailedAt = recv;
			else if (!t.derailed) c.derailedAt = -1;
			c.base = t;
			c.recv = recv;
			c.posed = null;
		}
		pending.clear();
	}

	/** Per-tick bookkeeping of the visuals: wheel roll and braking state. */
	public static void tickVisuals(MinecraftClient client) {
		if (client.world == null) return;
		for (CTrain c : trains.values()) {
			if (c.base == null) continue;
			double v = c.base.speed;
			if (Math.abs(v) > 0.01) {
				c.roll += v;
				c.still = 0;
			} else {
				c.still++;
				c.decel *= 0.5;
				// ease the wheels into the nearest position where they look the same as when parked
				double target = Math.round(c.roll / WHEEL_HALF_TURN) * WHEEL_HALF_TURN;
				c.roll += (target - c.roll) * 0.25;
			}
		}
	}

	/** Roll distance for the current frame, in the direction the vehicle faces. */
	public static float rollOf(CTrain c, int facing, float tickDelta) {
		double r = c.roll + (Math.abs(c.base.speed) > 0.01 ? c.base.speed * tickDelta : 0);
		return (float) (r * facing);
	}

	private static Train copy(Train s) {
		Train t = new Train(s.id);
		t.name = s.name;
		t.vehicles.addAll(s.vehicles);
		t.path.addAll(s.path);
		t.tailOffset = s.tailOffset;
		t.speed = s.speed;
		t.driven = s.driven;
		t.autopilot = s.autopilot;
		t.docked = s.docked;
		t.powered = s.powered;
		t.derailed = s.derailed;
		return t;
	}

	public static boolean pathKnown(Train t) {
		if (t.path.isEmpty()) return false;
		for (Dir d : t.path) if (!graph.tracks.containsKey(d.track())) return false;
		return true;
	}

	/** The train as it is {@code tickDelta} ticks into the current tick, extrapolated from the last server update. */
	public static Train posed(CTrain c, ClientWorld world, float tickDelta) {
		double age = Math.max(0, Math.min(8.0, (world.getTime() - c.recv) + tickDelta));
		if (c.posed != null && c.posedAge == age) return c.posed;
		Train t = copy(c.base);
		if (pathKnown(t) && Math.abs(t.speed) > 1e-5 && age > 0) t.move(graph, RailGraph.SWITCH_STATE, t.speed * age);
		c.posed = t;
		c.posedAge = age;
		return t;
	}

	// ------------------------------------------------------------------------------------------ incoming

	public static void onPayload(RailNetPayload p, MinecraftClient client) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(p.data()));
			switch (p.type()) {
				case RailNetPayload.GRAPH -> {
					byte[] d = p.data();
					int part = d[0], total = d[1];
					if (parts == null || parts.length != total) {
						parts = new byte[total][];
						gotParts = 0;
					}
					if (parts[part] == null) gotParts++;
					byte[] chunk = new byte[d.length - 2];
					System.arraycopy(d, 2, chunk, 0, chunk.length);
					parts[part] = chunk;
					if (gotParts == total) {
						ByteArrayOutputStream bo = new ByteArrayOutputStream();
						for (byte[] c : parts) bo.write(c);
						parts = null;
						graph = RailCodec.decodeGraph(bo.toByteArray());
						graphVersion++;
					}
				}
				case RailNetPayload.SWITCH -> {
					RailNode n = graph.nodes.get(in.readLong());
					int side = in.readByte(), sel = in.readByte();
					if (n != null) {
						int before = n.sel(side);
						n.setSel(side, sel);
						if (before != n.sel(side) && client.world != null) {
							switchAnims.put(n.id * 2 + side, new SwitchAnim(before, n.sel(side), client.world.getTime()));
						}
					}
				}
				case RailNetPayload.SIGNALS -> {
					int n = in.readInt();
					for (int i = 0; i < n; i++) signals.put(in.readLong(), in.readBoolean());
				}
				case RailNetPayload.TRAINS -> {
					// held back until the next client tick starts (see applyPending), so a snapshot never lands mid-frame
					int n = in.readInt();
					for (int i = 0; i < n; i++) {
						Train t = RailCodec.readTrainState(in);
						pending.put(t.id, t);
					}
				}
				case RailNetPayload.TRAIN_REMOVE -> {
					UUID gone = new UUID(in.readLong(), in.readLong());
					trains.remove(gone);
					pending.remove(gone);
				}
				case RailNetPayload.PENDING -> {
					if (in.readBoolean()) {
						pendingPos = new V3(in.readDouble(), in.readDouble(), in.readDouble());
						pendingLeave = new V3(in.readDouble(), in.readDouble(), in.readDouble());
					} else {
						pendingPos = null;
						pendingLeave = null;
					}
				}
				case RailNetPayload.OPEN_TRAIN -> openTrain(client, in);
				case RailNetPayload.OPEN_STATION -> {
					long pos = in.readLong();
					String name = in.readUTF();
					client.setScreen(new StationScreen(pos, name));
				}
				case RailNetPayload.RIDE -> {
					UUID train = new UUID(in.readLong(), in.readLong());
					UUID vehicle = new UUID(in.readLong(), in.readLong());
					boolean on = in.readBoolean();
					if (on) TrainRide.start(client, train, vehicle);
					else TrainRide.stop(client);
				}
				case RailNetPayload.CLEAR -> reset();
				default -> {
				}
			}
		} catch (IOException e) {
			FicsitCraft.LOGGER.warn("Bad rail packet {}", p.type(), e);
		}
	}

	private static void openTrain(MinecraftClient client, DataInputStream in) throws IOException {
		TrainInfo info = new TrainInfo();
		info.state = RailCodec.readTrainState(in);
		info.stopIndex = in.readInt();
		info.autoState = in.readByte();
		info.status = in.readUTF();
		info.driverIsMe = in.readBoolean();
		info.hasDriver = in.readBoolean();
		int nt = in.readInt();
		for (int i = 0; i < nt; i++) info.timetable.add(new Stop(in.readUTF(), in.readByte(), in.readShort()));
		int ns = in.readInt();
		for (int i = 0; i < ns; i++) info.stations.add(in.readUTF());
		for (int i = 0; i < info.state.vehicles.size(); i++) {
			int kind = in.readByte();
			if (kind == 0) info.cars.add(new CarInfo(0, 0, 0, "", 0));
			else if (kind == 2) info.cars.add(new CarInfo(2, 0, 0, in.readUTF(), in.readFloat()));
			else info.cars.add(new CarInfo(1, in.readInt(), in.readInt(), "", 0));
		}
		info.powerDraw = in.readDouble();
		info.goalDist = in.readDouble();
		if (client.currentScreen instanceof TrainScreen ts && ts.trainId().equals(info.state.id)) ts.update(info);
		else client.setScreen(new TrainScreen(info));
	}

	// ------------------------------------------------------------------------------------------ outgoing

	public interface BodyWriter {
		void write(DataOutputStream o) throws IOException;
	}

	public static void send(int type, BodyWriter w) {
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			w.write(new DataOutputStream(bo));
			ClientPlayNetworking.send(new RailActionPayload(type, bo.toByteArray()));
		} catch (IOException ignored) {
		}
	}

	public static void writeUuid(DataOutputStream o, UUID id) throws IOException {
		o.writeLong(id.getMostSignificantBits());
		o.writeLong(id.getLeastSignificantBits());
	}

	public static void trainCmd(UUID train, int cmd, BodyWriter extra) {
		send(RailActionPayload.TRAIN_CMD, o -> {
			writeUuid(o, train);
			o.writeByte(cmd);
			if (extra != null) extra.write(o);
		});
	}
}
