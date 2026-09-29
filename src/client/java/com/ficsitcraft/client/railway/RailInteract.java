package com.ficsitcraft.client.railway;

import com.ficsitcraft.item.RailSignalItem;
import com.ficsitcraft.item.RailwayItem;
import com.ficsitcraft.item.VehicleItem;
import com.ficsitcraft.network.RailActionPayload;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;

/** Aiming at trains, switches, signals and tracks (they are not blocks or entities, so we ray-cast against them ourselves). */
public final class RailInteract {
	public enum Kind {VEHICLE, SWITCH, SIGNAL, TRACK}

	public record Target(Kind kind, double dist, UUID train, UUID vehicle, long id, int side, VehicleType type) {
	}

	/** Half extents of a car's hit box (the length is the vehicle's own). */
	public static final double HIT_HALF_WIDTH = 1.45, HIT_HALF_HEIGHT = 1.85;
	private static long lastUse;

	private RailInteract() {
	}

	private static V3 v(Vec3d d) {
		return new V3(d.x, d.y, d.z);
	}

	/** Ray vs. oriented box; returns the distance or -1. */
	private static double rayBox(V3 o, V3 d, V3 c, V3 f, double hw, double hh, double hl, double max) {
		V3 up0 = new V3(0, 1, 0);
		V3 r = f.cross(up0);
		r = r.lengthSq() < 1e-8 ? new V3(1, 0, 0) : r.normalize();
		V3 u = r.cross(f).normalize();
		V3 rel = o.sub(c);
		double[] po = {rel.dot(r), rel.dot(u), rel.dot(f)};
		double[] pd = {d.dot(r), d.dot(u), d.dot(f)};
		double[] half = {hw, hh, hl};
		double tmin = 0, tmax = max;
		for (int k = 0; k < 3; k++) {
			if (Math.abs(pd[k]) < 1e-9) {
				if (Math.abs(po[k]) > half[k]) return -1;
			} else {
				double t1 = (-half[k] - po[k]) / pd[k], t2 = (half[k] - po[k]) / pd[k];
				if (t1 > t2) {
					double tmp = t1;
					t1 = t2;
					t2 = tmp;
				}
				tmin = Math.max(tmin, t1);
				tmax = Math.min(tmax, t2);
				if (tmin > tmax) return -1;
			}
		}
		return tmin;
	}

	/** Distance along the ray to the closest approach of a point, or -1 if it is farther than radius. */
	private static double raySphere(V3 o, V3 d, V3 p, double radius, double max) {
		V3 rel = p.sub(o);
		double along = rel.dot(d);
		if (along < 0 || along > max) return -1;
		double off = rel.sub(d.mul(along)).length();
		return off <= radius ? along : -1;
	}

	public static Target pick(MinecraftClient c, boolean tracks, double reach, float delta) {
		if (c.player == null || c.world == null) return null;
		V3 o = v(c.player.getCameraPosVec(delta)), d = v(c.player.getRotationVec(delta)).normalize();
		Target best = null;
		for (Map.Entry<UUID, ClientRail.CTrain> en : ClientRail.trains.entrySet()) {
			ClientRail.CTrain ct = en.getValue();
			if (ct.base == null || !ClientRail.pathKnown(ct.base)) continue;
			Train t = ClientRail.posed(ct, c.world, delta);
			for (int i = 0; i < t.vehicles.size(); i++) {
				Vehicle veh = t.vehicles.get(i);
				RailRenderer.VehiclePose p = RailRenderer.poseOf(t, i);
				V3 centre = p.center().add(new V3(0, t.derailed ? 1.7 : 1.8, 0));
				if (centre.distanceTo(o) > reach + veh.type.length) continue;
				// a car lying on its side is wider and lower: generous box
				double hit = t.derailed ? rayBox(o, d, centre, p.forward(), 3.0, 2.2, veh.type.length / 2, reach)
						: rayBox(o, d, centre, p.forward(), HIT_HALF_WIDTH, HIT_HALF_HEIGHT, veh.type.length / 2, reach);
				if (hit >= 0 && (best == null || hit < best.dist())) best = new Target(Kind.VEHICLE, hit, t.id, veh.id, 0, 0, veh.type);
			}
		}
		for (RailNode n : ClientRail.graph.nodes.values()) {
			if (n.pos.distanceTo(o) > reach + 4) continue;
			for (int s = 0; s < 2; s++) {
				if (n.isSwitch(s)) {
					double hit = raySphere(o, d, RailRenderer.leverPos(n, s), 0.85, reach);
					if (hit >= 0 && (best == null || hit < best.dist())) best = new Target(Kind.SWITCH, hit, null, null, n.id, s, null);
				}
				if (n.signal(s) != 0) {
					double hit = raySphere(o, d, RailRenderer.signalPos(n, s).add(new V3(0, 3.5, 0)), 1.0, reach);
					if (hit >= 0 && (best == null || hit < best.dist())) best = new Target(Kind.SIGNAL, hit, null, null, n.id, s, null);
				}
			}
		}
		if (tracks) {
			RailGraph.Hit h = ClientRail.graph.rayTrack(o, d, reach, 0.9);
			if (h != null) {
				V3 p = ClientRail.graph.tracks.get(h.track()).curve.point(h.t());
				double along = p.sub(o).dot(d);
				if (best == null || along < best.dist()) best = new Target(Kind.TRACK, along, null, null, h.track(), 0, null);
			}
		}
		return best;
	}

	private static double blockDist(MinecraftClient c) {
		if (c.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return c.player.getEyePos().distanceTo(hit.getPos());
		}
		return Double.MAX_VALUE;
	}

	/** Called from the use-key hook; true if the click was consumed by a train / switch. */
	public static boolean tryUse(MinecraftClient c) {
		if (c.player == null || c.world == null || c.currentScreen != null || c.player.isSpectator()) return false;
		if (TrainRide.isRiding()) return false;
		Item held = c.player.getMainHandStack().getItem();
		if (held instanceof RailwayItem || held instanceof VehicleItem || held instanceof RailSignalItem
				|| held instanceof com.ficsitcraft.item.CableItem || held instanceof com.ficsitcraft.item.ZiplineItem) return false;
		Target t = pick(c, false, 7.5, 1f);
		if (t == null || t.dist() > blockDist(c) + 0.3) return false;
		long now = c.world.getTime();
		if (now - lastUse < 8) return true;
		if (t.kind() == Kind.VEHICLE) {
			lastUse = now;
			if (c.player.isSneaking() && t.type() == VehicleType.FREIGHT_CAR) {
				RailClientNet.send2(RailActionPayload.OPEN_CAR, t.train(), t.vehicle());
			} else {
				RailClientNet.send2(RailActionPayload.INTERACT_VEHICLE, t.train(), t.vehicle());
			}
			return true;
		}
		if (t.kind() == Kind.SWITCH) {
			lastUse = now;
			ClientRail.send(RailActionPayload.TOGGLE_SWITCH, o -> {
				o.writeLong(t.id());
				o.writeByte(t.side());
			});
			return true;
		}
		return false;
	}

	/** Reach for hitting a train (a car is big, so a little more than the entity reach). */
	private static final double ATTACK_REACH = 5.0;
	private static long lastAttack;

	/** The vehicle under the crosshair, if it is nearer than any block in the way. */
	public static Target hoveredVehicle(MinecraftClient c, float delta) {
		if (c.player == null || c.world == null || c.currentScreen != null || c.player.isSpectator() || TrainRide.isRiding()) return null;
		Target t = pick(c, false, ATTACK_REACH, delta);
		if (t == null || t.kind() != Kind.VEHICLE || t.dist() > blockDist(c) + 0.3) return null;
		return t;
	}

	/** Called from the attack-key hook: a left click on a car breaks it like a boat or a minecart (drops the vehicle and its cargo). */
	public static boolean tryAttack(MinecraftClient c) {
		Target t = hoveredVehicle(c, 1f);
		if (t == null) return false;
		long now = c.world.getTime();
		if (now - lastAttack >= 5) {
			lastAttack = now;
			c.player.swingHand(Hand.MAIN_HAND);
			RailClientNet.send2(RailActionPayload.DISMANTLE_VEHICLE, t.train(), t.vehicle());
		}
		return true;
	}

	/** Holding the attack key while aiming at a car must not start breaking the block behind it. */
	public static boolean blocksBreaking(MinecraftClient c) {
		return hoveredVehicle(c, 1f) != null;
	}

	/** Dismantle key: vehicle, signal or track under the crosshair. */
	public static void dismantle(MinecraftClient c) {
		Target t = pick(c, true, 12, 1f);
		if (t == null) {
			c.player.sendMessage(Text.translatable("message.ficsitcraft.nothing_to_dismantle").formatted(Formatting.GRAY), true);
			return;
		}
		switch (t.kind()) {
			case VEHICLE -> RailClientNet.send2(RailActionPayload.DISMANTLE_VEHICLE, t.train(), t.vehicle());
			case SIGNAL -> ClientRail.send(RailActionPayload.DISMANTLE_SIGNAL, o -> {
				o.writeLong(t.id());
				o.writeByte(t.side());
			});
			case TRACK -> ClientRail.send(RailActionPayload.DISMANTLE_TRACK, o -> o.writeLong(t.id()));
			default -> {
			}
		}
	}
}
