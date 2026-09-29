package com.ficsitcraft.client.railway;

import com.ficsitcraft.registry.ModSounds;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Sounds of moving trains. Every train that is near the player runs up to three looping sound instances that follow its
 * car closest to the player: rail rumble + clacks (pitch = speed), the motor hum of a powered locomotive and the wheel
 * squeal while braking (its pitch falls as the train slows down).
 */
public final class TrainAudio {
	private static final double HEAR = 72;
	private static final double MAX_SPEED = 0.9;

	private static final Map<UUID, Loop> ROLL = new HashMap<>();
	private static final Map<UUID, Loop> MOTOR = new HashMap<>();
	private static final Map<UUID, Loop> BRAKE = new HashMap<>();

	/** True while a train squeals (the sparks effect uses the same condition). */
	public static boolean squealing(ClientRail.CTrain c) {
		return c.base != null && Math.abs(c.base.speed) > 0.06 && c.decel > 0.0035;
	}

	/** 0..1 how hard the wheels are grinding. */
	public static double squealIntensity(ClientRail.CTrain c) {
		double d = Math.min(1, c.decel / 0.008);
		double s = Math.min(1, Math.abs(c.base.speed) / 0.5);
		return Math.max(0.3, d) * Math.max(0.35, s);
	}

	private TrainAudio() {
	}

	private static final class Loop extends MovingSoundInstance {
		boolean stopped;
		int quiet;

		Loop(SoundEvent event) {
			super(event, SoundCategory.NEUTRAL, Random.create());
			this.repeat = true;
			this.repeatDelay = 0;
			this.volume = 0.001f;
			this.pitch = 1f;
		}

		void set(V3 at, float volume, float pitch) {
			this.x = at.x();
			this.y = at.y();
			this.z = at.z();
			this.volume = Math.max(0.001f, volume);
			this.pitch = Math.max(0.5f, Math.min(2f, pitch));
			this.quiet = 0;
		}

		@Override
		public void tick() {
			if (stopped) setDone();
		}
	}

	private static void drive(MinecraftClient c, Map<UUID, Loop> map, UUID id, SoundEvent ev, boolean on, V3 at, float volume, float pitch) {
		Loop l = map.get(id);
		if (!on) {
			if (l != null && ++l.quiet > 6) {
				l.stopped = true;
				map.remove(id);
			}
			return;
		}
		if (l == null || l.isDone()) {
			l = new Loop(ev);
			l.set(at, volume, pitch);
			map.put(id, l);
			c.getSoundManager().play(l);
		} else {
			l.set(at, volume, pitch);
		}
	}

	private static void stopAll(Map<UUID, Loop> map, boolean onlyMissing) {
		Iterator<Map.Entry<UUID, Loop>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Loop> e = it.next();
			if (onlyMissing && ClientRail.trains.containsKey(e.getKey())) continue;
			e.getValue().stopped = true;
			it.remove();
		}
	}

	public static void reset() {
		stopAll(ROLL, false);
		stopAll(MOTOR, false);
		stopAll(BRAKE, false);
	}

	public static void tick(MinecraftClient c) {
		if (c.world == null || c.player == null) {
			reset();
			return;
		}
		stopAll(ROLL, true);
		stopAll(MOTOR, true);
		stopAll(BRAKE, true);
		V3 me = new V3(c.player.getX(), c.player.getY(), c.player.getZ());
		boolean inCab = TrainRide.isRiding();
		for (Map.Entry<UUID, ClientRail.CTrain> en : ClientRail.trains.entrySet()) {
			ClientRail.CTrain ct = en.getValue();
			UUID id = en.getKey();
			if (ct.base == null || !ClientRail.pathKnown(ct.base)) continue;
			double speed = Math.abs(ct.base.speed);
			Train t = ClientRail.posed(ct, c.world, 0f);
			// the car nearest to the listener is the sound source
			V3 src = null;
			double best = Double.MAX_VALUE;
			for (int i = 0; i < t.vehicles.size(); i++) {
				V3 p = RailRenderer.poseOf(t, i).center();
				double d = p.distanceTo(me);
				if (d < best) {
					best = d;
					src = p;
				}
			}
			boolean near = src != null && best < HEAR;
			float sf = (float) Math.min(1, speed / MAX_SPEED);
			float cab = inCab && id.equals(TrainRide.trainId()) ? 0.7f : 1f;
			drive(c, ROLL, id, ModSounds.TRAIN_ROLL, near && speed > 0.02, src, cab * (0.15f + 2.85f * sf), 0.55f + 0.95f * sf);
			boolean motor = near && ct.base.powered && speed > 0.02 && hasLoco(ct.base);
			drive(c, MOTOR, id, ModSounds.TRAIN_MOTOR, motor, src, cab * (0.2f + 1.6f * sf), 0.7f + 0.9f * sf);
			boolean squeal = near && squealing(ct);
			float intensity = squeal ? (float) squealIntensity(ct) : 0;
			drive(c, BRAKE, id, ModSounds.TRAIN_BRAKE, squeal, src, cab * (0.4f + 1.9f * intensity), 0.7f + 0.8f * (float) Math.min(1, speed / 1.0));
		}
	}

	private static boolean hasLoco(Train t) {
		for (Vehicle v : t.vehicles) if (v.type == VehicleType.LOCOMOTIVE) return true;
		return false;
	}
}
