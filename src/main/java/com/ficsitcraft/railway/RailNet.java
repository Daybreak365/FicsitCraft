package com.ficsitcraft.railway;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.network.RailActionPayload;
import com.ficsitcraft.network.RailNetPayload;
import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailTrack;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.registry.ModItems;
import com.ficsitcraft.train.Stop;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server side of the railway networking: state sync to clients and handling of client actions. */
public final class RailNet {
	private RailNet() {
	}

	private static void send(ServerPlayerEntity p, int type, byte[] data) {
		ServerPlayNetworking.send(p, new RailNetPayload(type, data));
	}

	private interface Writer {
		void write(DataOutputStream o) throws IOException;
	}

	private static byte[] bytes(Writer w) {
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			w.write(new DataOutputStream(bo));
			return bo.toByteArray();
		} catch (IOException e) {
			return new byte[0];
		}
	}

	// ------------------------------------------------------------------------------------------ outgoing

	public static void sendAll(ServerPlayerEntity p) {
		ServerWorld world = p.getServerWorld();
		RailWorld rw = RailWorld.get(world);
		send(p, RailNetPayload.CLEAR, new byte[0]);
		sendGraph(p, rw);
		send(p, RailNetPayload.SIGNALS, signalBytes(rw, rw.allSignalKeys(), true));
		sendTrains(p, rw, rw.trains);
	}

	public static void sendGraph(ServerPlayerEntity p, RailWorld rw) {
		try {
			for (byte[] chunk : RailCodec.chunks(RailCodec.encodeGraph(rw.graph))) send(p, RailNetPayload.GRAPH, chunk);
		} catch (IOException e) {
			FicsitCraft.LOGGER.error("Failed to sync the rail graph", e);
		}
	}

	private static byte[] signalBytes(RailWorld rw, Set<Long> keys, boolean refresh) {
		return bytes(o -> {
			List<Long> list = new ArrayList<>();
			for (long k : keys) {
				RailNode n = rw.graph.nodes.get(k >> 1);
				if (n == null) continue;
				list.add(k);
			}
			o.writeInt(list.size());
			for (long k : list) {
				RailNode n = rw.graph.nodes.get(k >> 1);
				boolean open = rw.sim.signalOpen(n, (int) (k & 1));
				o.writeLong(k);
				o.writeBoolean(open);
			}
		});
	}

	public static void sendTrains(ServerPlayerEntity p, RailWorld rw, List<Train> trains) {
		if (trains.isEmpty()) return;
		byte[] data = bytes(o -> {
			o.writeInt(trains.size());
			for (Train t : trains) RailCodec.writeTrainState(o, t);
		});
		send(p, RailNetPayload.TRAINS, data);
	}

	public static void sendTrainRemove(ServerWorld world, UUID id) {
		byte[] data = bytes(o -> {
			o.writeLong(id.getMostSignificantBits());
			o.writeLong(id.getLeastSignificantBits());
		});
		for (ServerPlayerEntity p : world.getPlayers()) send(p, RailNetPayload.TRAIN_REMOVE, data);
	}

	public static void sendSwitch(ServerWorld world, long node, int side, int sel) {
		byte[] data = bytes(o -> {
			o.writeLong(node);
			o.writeByte(side);
			o.writeByte(sel);
		});
		for (ServerPlayerEntity p : world.getPlayers()) send(p, RailNetPayload.SWITCH, data);
	}

	public static void sendPending(ServerPlayerEntity p, V3 pos, V3 leave) {
		byte[] data = bytes(o -> {
			o.writeBoolean(pos != null);
			if (pos != null) {
				o.writeDouble(pos.x());
				o.writeDouble(pos.y());
				o.writeDouble(pos.z());
				o.writeDouble(leave.x());
				o.writeDouble(leave.y());
				o.writeDouble(leave.z());
			}
		});
		send(p, RailNetPayload.PENDING, data);
	}

	public static void sendRide(ServerPlayerEntity p, UUID train, UUID vehicle, boolean on) {
		byte[] data = bytes(o -> {
			o.writeLong(train.getMostSignificantBits());
			o.writeLong(train.getLeastSignificantBits());
			o.writeLong(vehicle.getMostSignificantBits());
			o.writeLong(vehicle.getLeastSignificantBits());
			o.writeBoolean(on);
		});
		send(p, RailNetPayload.RIDE, data);
	}

	public static void openStation(ServerPlayerEntity p, BlockPos pos, String name) {
		byte[] data = bytes(o -> {
			o.writeLong(pos.asLong());
			o.writeUTF(name);
		});
		send(p, RailNetPayload.OPEN_STATION, data);
	}

	public static void openTrainUi(ServerPlayerEntity p, RailWorld rw, Train t) {
		byte[] data = bytes(o -> {
			RailCodec.writeTrainState(o, t);
			o.writeInt(t.stopIndex);
			o.writeByte(t.autoState);
			o.writeUTF(t.status);
			o.writeBoolean(t.driver != null && t.driver.equals(p.getUuid()));
			o.writeBoolean(t.driver != null);
			o.writeInt(t.timetable.size());
			for (Stop s : t.timetable) {
				o.writeUTF(s.station());
				o.writeByte(s.mode());
				o.writeShort(s.seconds());
			}
			List<String> names = rw.stationNames();
			o.writeInt(names.size());
			for (String n : names) o.writeUTF(n);
			for (Vehicle v : t.vehicles) {
				VehicleCargo c = VehicleCargo.of(v);
				if (c == null) {
					o.writeByte(0);
				} else if (v.type == VehicleType.FLUID_CAR) {
					o.writeByte(2);
					o.writeUTF(c.fluid.id);
					o.writeFloat((float) c.amount);
				} else {
					o.writeByte(1);
					int items = 0, slots = 0;
					for (ItemStack s : c.items) {
						if (s.isEmpty()) continue;
						items += s.getCount();
						slots++;
					}
					o.writeInt(items);
					o.writeInt(slots);
				}
			}
			o.writeDouble(t.powerDraw);
			o.writeDouble(t.goalDist == Double.POSITIVE_INFINITY ? -1 : t.goalDist);
		});
		send(p, RailNetPayload.OPEN_TRAIN, data);
	}

	// ------------------------------------------------------------------------------------------ per tick

	public static void tick(ServerWorld world, RailWorld rw, long now) {
		List<ServerPlayerEntity> players = world.getPlayers();
		if (rw.syncedRevision() != rw.graph.revision) {
			rw.setSyncedRevision(rw.graph.revision);
			rw.markDirty();
			for (ServerPlayerEntity p : players) sendGraph(p, rw);
		}
		if (players.isEmpty()) return;
		if (now % 10 == 0) {
			// signal display states: send only the changes
			Set<Long> keys = rw.allSignalKeys();
			Set<Long> changed = new HashSet<>();
			for (long k : keys) {
				RailNode n = rw.graph.nodes.get(k >> 1);
				if (n == null) continue;
				boolean open = rw.sim.signalOpen(n, (int) (k & 1));
				Boolean prev = rw.signalStates().put(k, open);
				if (prev == null || prev != open) changed.add(k);
			}
			rw.signalStates().keySet().removeIf(k -> !keys.contains(k));
			if (!changed.isEmpty()) {
				byte[] data = signalBytes(rw, changed, false);
				for (ServerPlayerEntity p : players) send(p, RailNetPayload.SIGNALS, data);
			}
		}
		if (rw.trains.isEmpty()) return;
		boolean keepalive = now % 20 == 0;
		for (ServerPlayerEntity p : players) {
			List<Train> near = new ArrayList<>();
			for (Train t : rw.trains) {
				if (t.path.isEmpty()) continue;
				if (!t.dirty && !keepalive) continue;
				V3 mid = t.pointAtU(rw.graph, t.tailOffset + t.length() / 2);
				double dx = mid.x() - p.getX(), dz = mid.z() - p.getZ();
				if (dx * dx + dz * dz < 380 * 380) near.add(t);
			}
			sendTrains(p, rw, near);
		}
		for (Train t : rw.trains) t.dirty = false;
	}

	// ------------------------------------------------------------------------------------------ incoming

	private static UUID uuid(java.io.DataInput in) throws IOException {
		return new UUID(in.readLong(), in.readLong());
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(RailActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity p = context.player();
			try {
				handle(p, payload.type(), new DataInputStream(new ByteArrayInputStream(payload.data())));
			} catch (IOException e) {
				FicsitCraft.LOGGER.warn("Bad rail action {}", payload.type(), e);
			}
		});
	}

	private static void msg(ServerPlayerEntity p, String key, Object... args) {
		p.sendMessage(Text.translatable("message.ficsitcraft." + key, args).formatted(Formatting.YELLOW), true);
	}

	private static void handle(ServerPlayerEntity p, int type, DataInputStream in) throws IOException {
		ServerWorld world = p.getServerWorld();
		RailWorld rw = RailWorld.get(world);
		switch (type) {
			case RailActionPayload.TOGGLE_SWITCH -> {
				long nodeId = in.readLong();
				int side = in.readByte();
				RailNode n = rw.graph.nodes.get(nodeId);
				if (n == null || side < 0 || side > 1 || n.side(side).size() < 2) return;
				if (p.squaredDistanceTo(n.pos.x(), n.pos.y(), n.pos.z()) > 20 * 20) return;
				n.setSel(side, (n.sel(side) + 1) % n.side(side).size());
				rw.markDirty();
				sendSwitch(world, nodeId, side, n.sel(side));
				world.playSound(null, BlockPos.ofFloored(n.pos.x(), n.pos.y(), n.pos.z()), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.8f, 1.1f);
			}
			case RailActionPayload.DISMANTLE_TRACK -> dismantleTrack(p, world, rw, in.readLong());
			case RailActionPayload.DISMANTLE_SIGNAL -> {
				long nodeId = in.readLong();
				int side = in.readByte();
				RailNode n = rw.graph.nodes.get(nodeId);
				if (n == null || side < 0 || side > 1 || n.signal(side) == 0) return;
				if (p.squaredDistanceTo(n.pos.x(), n.pos.y(), n.pos.z()) > 20 * 20) return;
				Item_give(p, n.signal(side) == 1 ? ModItems.RAIL_SIGNAL_BLOCK : ModItems.RAIL_SIGNAL_PATH);
				n.setSignal(side, 0);
				rw.graph.revision++;
				rw.markDirty();
			}
			case RailActionPayload.DISMANTLE_VEHICLE -> {
				Train t = rw.findTrain(uuid(in));
				UUID vid = uuid(in);
				if (t == null) return;
				dismantleVehicle(p, world, rw, t, vid);
			}
			case RailActionPayload.INTERACT_VEHICLE -> {
				Train t = rw.findTrain(uuid(in));
				UUID vid = uuid(in);
				if (t == null) return;
				int idx = indexOf(t, vid);
				if (idx < 0 || p.squaredDistanceTo(vec(rw.vehiclePos(t, idx))) > 20 * 20) return;
				openTrainUi(p, rw, t);
			}
			case RailActionPayload.OPEN_CAR -> {
				Train t = rw.findTrain(uuid(in));
				UUID vid = uuid(in);
				if (t == null) return;
				int idx = indexOf(t, vid);
				if (idx < 0 || p.squaredDistanceTo(vec(rw.vehiclePos(t, idx))) > 20 * 20) return;
				openCar(p, rw, t, t.vehicles.get(idx));
			}
			case RailActionPayload.TRAIN_CMD -> trainCommand(p, world, rw, in);
			case RailActionPayload.DRIVE_STEER -> {
				Train t = rw.findTrain(uuid(in));
				int steer = in.readByte();
				if (t == null || !p.getUuid().equals(t.driver) || t.derailed || (t.autopilot && !t.driven)) return;
				steerSwitch(world, rw, t, steer);
			}
			case RailActionPayload.DRIVE_INPUT -> {
				Train t = rw.findTrain(uuid(in));
				float throttle = in.readFloat();
				boolean brake = in.readBoolean();
				if (t == null || !p.getUuid().equals(t.driver) || t.derailed) return;
				UUID[] ride = rw.riders.get(p.getUuid());
				int idx = ride == null ? -1 : indexOf(t, ride[1]);
				int facing = idx < 0 ? 1 : t.vehicles.get(idx).facing;
				if (!t.driven) {
					// passenger of an autopilot train: only real input takes the controls
					if (Math.abs(throttle) < 0.001f && !brake) return;
					t.driven = true;
					t.autoCmd = 0;
					t.dirty = true;
				}
				t.manualCmd = Math.max(-1, Math.min(1, throttle)) * facing;
				t.brake = brake;
			}
			case RailActionPayload.STATION_RENAME -> {
				long pos = in.readLong();
				String name = in.readUTF();
				if (name.length() > 32) name = name.substring(0, 32);
				BlockPos bp = BlockPos.fromLong(pos);
				if (p.squaredDistanceTo(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5) > 64 * 64) return;
				if (world.getBlockEntity(bp) instanceof com.ficsitcraft.blockentity.RailBuildingBlockEntity be) be.setStationName(name);
			}
			default -> {
			}
		}
	}

	private static net.minecraft.util.math.Vec3d vec(V3 v) {
		return new net.minecraft.util.math.Vec3d(v.x(), v.y(), v.z());
	}

	private static int indexOf(Train t, UUID vid) {
		for (int i = 0; i < t.vehicles.size(); i++) if (t.vehicles.get(i).id.equals(vid)) return i;
		return -1;
	}

	private static void Item_give(ServerPlayerEntity p, net.minecraft.item.Item item) {
		if (!p.isCreative()) p.getInventory().offerOrDrop(new ItemStack(item));
	}

	private static void dismantleTrack(ServerPlayerEntity p, ServerWorld world, RailWorld rw, long id) {
		RailTrack t = rw.graph.tracks.get(id);
		if (t == null || t.owner != RailNode.NO_OWNER) return;
		V3 mid = t.curve.point(0.5);
		if (p.squaredDistanceTo(mid.x(), mid.y(), mid.z()) > (t.length() / 2 + 20) * (t.length() / 2 + 20)) return;
		if (rw.isOccupied(id)) {
			msg(p, "rail_occupied");
			return;
		}
		int refund = Math.max(1, (int) Math.ceil(t.length() / com.ficsitcraft.rail.RailPlanner.UNIT));
		rw.graph.removeTrack(id);
		rw.markDirty();
		if (!p.isCreative()) p.getInventory().offerOrDrop(new ItemStack(ModItems.RAILWAY, refund));
		world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 0.9f, 0.9f);
	}

	/** A / D while driving: select the left / right branch of the next switch in front of the train. */
	private static void steerSwitch(ServerWorld world, RailWorld rw, Train t, int steer) {
		if (steer == 0) return;
		int m = Math.abs(t.speed) > 1e-4 ? (t.speed > 0 ? 1 : -1) : (t.manualCmd < 0 ? -1 : 1);
		Train.SwitchAhead sw = t.nextSwitch(rw.graph, m, 300);
		if (sw == null) return;
		int idx = rw.graph.steerBranch(sw.node(), sw.side(), sw.travel(), steer < 0 ? -1 : 1);
		if (sw.node().sel(sw.side()) == idx) return;
		sw.node().setSel(sw.side(), idx);
		rw.markDirty();
		sendSwitch(world, sw.node().id, sw.side(), idx);
		world.playSound(null, BlockPos.ofFloored(sw.node().pos.x(), sw.node().pos.y(), sw.node().pos.z()), SoundEvents.BLOCK_LEVER_CLICK,
				SoundCategory.BLOCKS, 0.8f, 1.1f);
	}

	private static void dismantleVehicle(ServerPlayerEntity p, ServerWorld world, RailWorld rw, Train t, UUID vid) {
		int idx = indexOf(t, vid);
		if (idx < 0) return;
		V3 at = rw.vehiclePos(t, idx);
		if (p.squaredDistanceTo(at.x(), at.y(), at.z()) > 20 * 20) return;
		if (Math.abs(t.speed) > 0.02) {
			msg(p, "train_moving");
			return;
		}
		Vehicle v = t.vehicles.get(idx);
		if (t.driver != null) {
			msg(p, "train_driven");
			return;
		}
		if (t.vehicles.size() == 1) {
			rw.dropTrainToPlayer(world, p, t);
			return;
		}
		Train head = null;
		if (idx < t.vehicles.size() - 1) head = t.split(rw.graph, idx + 1);
		// now the vehicle is the last one of t
		double len = v.type.length + Train.GAP;
		t.vehicles.remove(t.vehicles.size() - 1);
		if (t.vehicles.isEmpty()) {
			rw.trains.remove(t);
			rw.sim.trains.remove(t);
			sendTrainRemove(world, t.id);
		} else {
			t.trim(rw.graph);
			t.dirty = true;
		}
		if (head != null) {
			for (Vehicle hv : head.vehicles) VehicleCargo.ensure(hv);
			rw.addTrain(head);
		}
		rw.dropVehicleToPlayer(world, p, v, at);
		rw.markDirty();
		world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 0.9f, 0.8f);
	}

	public static void openCar(ServerPlayerEntity p, RailWorld rw, Train t, Vehicle v) {
		VehicleCargo c = VehicleCargo.of(v);
		if (c == null || v.type != VehicleType.FREIGHT_CAR) return;
		VehicleCargo.View view = new VehicleCargo.View(c, () -> rw.findTrain(t.id) != null && p.isAlive()
				&& p.squaredDistanceTo(vec(rw.vehiclePos(t, Math.max(0, indexOf(t, v.id))))) < 24 * 24, rw::markDirty);
		p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, player) ->
				new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, inv, view, 4),
				Text.translatable("item.ficsitcraft.freight_car")));
	}

	private static void trainCommand(ServerPlayerEntity p, ServerWorld world, RailWorld rw, DataInputStream in) throws IOException {
		Train t = rw.findTrain(uuid(in));
		int cmd = in.readByte();
		if (t == null) return;
		boolean quiet = false;
		V3 near = rw.vehiclePos(t, 0);
		if (p.squaredDistanceTo(near.x(), near.y(), near.z()) > 400 * 400) return;
		switch (cmd) {
			case RailActionPayload.CMD_DRIVE -> {
				UUID vid = uuid(in);
				int idx = indexOf(t, vid);
				if (idx < 0 || t.vehicles.get(idx).type != VehicleType.LOCOMOTIVE) return;
				V3 at = rw.vehiclePos(t, idx);
				if (p.squaredDistanceTo(at.x(), at.y(), at.z()) > 24 * 24) return;
				if (t.derailed) {
					msg(p, "train_derailed");
					return;
				}
				if (t.driver != null && !t.driver.equals(p.getUuid())) {
					msg(p, "train_driven");
					return;
				}
				rw.startRiding(world, p, t, t.vehicles.get(idx));
			}
			case RailActionPayload.CMD_STOP_DRIVE -> rw.stopRiding(world, p.getUuid());
			case RailActionPayload.CMD_RERAIL -> {
				if (t.derailed && Math.abs(t.speed) < 0.01) {
					t.derailed = false;
					t.speed = 0;
					t.brake = true;
					t.dirty = true;
					rw.markDirty();
					msg(p, "rerailed");
				}
			}
			case RailActionPayload.CMD_AUTOPILOT -> {
				boolean on = in.readBoolean();
				if (on && t.derailed) {
					msg(p, "train_derailed");
					on = false;
				}
				if (on && (t.timetable.isEmpty() || t.locoCount() == 0)) {
					msg(p, "no_timetable");
					on = false;
				}
				t.autopilot = on;
				t.autoState = 0;
				t.routeChoice.clear();
				t.goal = null;
				t.brake = false;
				if (!on) t.autoCmd = 0;
			}
			case RailActionPayload.CMD_TIMETABLE -> {
				int n = Math.min(64, in.readInt());
				Stop before = t.stopIndex < t.timetable.size() ? t.timetable.get(t.stopIndex) : null;
				List<Stop> fresh = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					String st = in.readUTF();
					int mode = in.readByte();
					int sec = in.readShort();
					// time stops wait at least 1 s; a cargo stop's limit may be 0 (no limit)
					fresh.add(new Stop(st, mode == Stop.WAIT_LOADED ? Stop.WAIT_LOADED : Stop.WAIT_SECONDS,
							Math.max(mode == Stop.WAIT_LOADED ? 0 : 1, Math.min(Stop.MAX_SECONDS, sec))));
				}
				quiet = in.readBoolean();
				// keep the train's place in the schedule: editing a stop's dwell time must not restart the route
				int keep = -1;
				if (before != null) {
					if (t.stopIndex < fresh.size() && fresh.get(t.stopIndex).station().equalsIgnoreCase(before.station())) keep = t.stopIndex;
					else for (int i = 0; i < fresh.size(); i++) {
						if (fresh.get(i).station().equalsIgnoreCase(before.station())) {
							keep = i;
							break;
						}
					}
				}
				t.timetable.clear();
				t.timetable.addAll(fresh);
				if (keep >= 0) {
					t.stopIndex = keep;
				} else {
					t.stopIndex = 0;
					t.autoState = 0;
					t.goal = null;
					t.dwell = 0;
				}
				if (t.timetable.isEmpty()) t.autopilot = false;
			}
			case RailActionPayload.CMD_RENAME -> {
				String name = in.readUTF();
				t.name = name.length() > 32 ? name.substring(0, 32) : name;
			}
			case RailActionPayload.CMD_DECOUPLE -> {
				int idx = in.readInt();
				if (Math.abs(t.speed) > 0.02) {
					msg(p, "train_moving");
					return;
				}
				Train head = t.split(rw.graph, idx);
				if (head != null) {
					rw.addTrain(head);
					head.name = t.name.isEmpty() ? "" : t.name + " 2";
					for (Vehicle v : head.vehicles) VehicleCargo.ensure(v);
				}
			}
			case RailActionPayload.CMD_COUPLE -> {
				if (Math.abs(t.speed) > 0.02) {
					msg(p, "train_moving");
					return;
				}
				boolean done = false;
				for (Train o : new ArrayList<>(rw.trains)) {
					if (o == t || Math.abs(o.speed) > 0.02) continue;
					if (t.coupleHeadTo(rw.graph, o)) {
						rw.removeTrain(world, o);
						done = true;
						break;
					}
					if (o.coupleHeadTo(rw.graph, t)) {
						rw.removeTrain(world, t);
						t = o;
						done = true;
						break;
					}
				}
				msg(p, done ? "coupled" : "nothing_to_couple");
			}
			case RailActionPayload.CMD_STOP_INDEX -> {
				int idx = in.readInt();
				if (idx >= 0 && idx < t.timetable.size()) {
					t.stopIndex = idx;
					t.autoState = 0;
					t.routeChoice.clear();
				}
			}
			case RailActionPayload.CMD_HORN -> {
				V3 h = t.pointAtU(rw.graph, t.headU());
				world.playSound(null, h.x(), h.y() + 2, h.z(), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 3.0f, 0.5f);
				world.playSound(null, h.x(), h.y() + 2, h.z(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.NEUTRAL, 3.0f, 0.42f);
			}
			default -> {
			}
		}
		t.dirty = true;
		rw.markDirty();
		if (cmd != RailActionPayload.CMD_DRIVE && cmd != RailActionPayload.CMD_STOP_DRIVE && cmd != RailActionPayload.CMD_HORN
				&& cmd != RailActionPayload.CMD_COUPLE && cmd != RailActionPayload.CMD_RENAME && !quiet) {
			openTrainUi(p, rw, t);
		}
	}
}
