package com.ficsitcraft.client.zipline;

import com.ficsitcraft.item.ZiplineItem;
import com.ficsitcraft.network.ZiplinePayload;
import com.ficsitcraft.power.PowerLineGeometry;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side zipline riding, modelled on Satisfactory:
 * <ul>
 *   <li>Hold the Zipline and jump into a power line (or right-click near one) to grab it.</li>
 *   <li>W moves along the line in the direction you are looking, S the other way. There is no gravity slide.</li>
 *   <li>At a pole or building you automatically continue onto the connected line that best matches where you look.</li>
 *   <li>Space jumps off (keeping momentum), Sneak lets go.</li>
 * </ul>
 * While riding, the arm holding the zipline is raised to the cable (first and third person) and sparks fly from the
 * contact point; the camera stays wherever the player put it.
 */
public final class ZiplineClient {
	/** Distance from the cable down to the player's feet (hanging by the raised right arm). */
	public static final double HANG = 2.3;
	private static final double SPEED = 0.62;       // blocks per tick at full speed (~12 m/s)
	private static final double RESPONSE = 0.12;    // exponential speed easing per tick
	private static final double GRAB_RANGE = 1.6;   // jumping into a line
	private static final double USE_RANGE = 4.0;    // right-click grab
	private static final int GRAB_TICKS = 6;        // smooth pull-up to the line

	private static boolean attached;
	private static BlockPos from;
	private static BlockPos to;
	private static double t;         // position on the current segment (0 = from, 1 = to)
	private static double speed;     // signed along-line speed (blocks/tick), + = towards "to"
	private static int dirSign = 1;  // which way "forward" is along the line (hysteresis on the look direction)
	private static int grabCooldown;
	private static boolean useWasDown;
	private static int grabTicks;
	private static Vec3d grabStart;
	private static double sway;      // fore-aft pendulum swing (blocks)
	private static double swayVel;

	/** Entity ids of other players currently riding (from the server). */
	private static final Set<Integer> REMOTE_RIDERS = new HashSet<>();
	private static final Map<Integer, Vec3d> REMOTE_LAST = new HashMap<>();

	private ZiplineClient() {
	}

	public static boolean isAttached() {
		return attached;
	}

	/** True if the given entity should be drawn in the zipline pose. */
	public static boolean isRiding(Entity e) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player != null && e == c.player) return attached;
		return REMOTE_RIDERS.contains(e.getId());
	}

	public static void setRemote(int id, boolean riding) {
		if (riding) REMOTE_RIDERS.add(id);
		else {
			REMOTE_RIDERS.remove(id);
			REMOTE_LAST.remove(id);
		}
	}

	private record Hit(BlockPos a, BlockPos b, double t, double dist) {
	}

	/** Called at the end of every client tick. */
	public static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			attached = false;
			REMOTE_RIDERS.clear();
			return;
		}
		remoteSparks(world);
		if (grabCooldown > 0) grabCooldown--;
		boolean holding = ZiplineItem.isHolding(player);
		boolean useDown = client.options.useKey.isPressed();
		boolean usePressed = useDown && !useWasDown;
		useWasDown = useDown;

		if (!attached) {
			if (!holding || grabCooldown > 0 || player.isSpectator() || player.getAbilities().flying) return;
			Vec3d hand = player.getPos().add(0, HANG, 0);
			if (usePressed) {
				Hit h = findLine(world, player.getEyePos(), USE_RANGE, player.getRotationVec(1f));
				if (h != null) attach(client, h);
			} else if (!player.isOnGround() && player.getVelocity().y > -1.2) {
				Hit h = findLine(world, hand, GRAB_RANGE, null);
				if (h != null) attach(client, h);
			}
			return;
		}

		// ---- riding
		if (!holding || player.isSpectator() || !lineExists(world, from, to) || player.input.sneaking) {
			detach(client, false);
			return;
		}
		Vec3d a = PowerLineGeometry.anchor(world, from);
		Vec3d b = PowerLineGeometry.anchor(world, to);
		Vec3d tanRaw = PowerLineGeometry.tangent(a, b, t);
		double tanLen = Math.max(0.05, tanRaw.length());
		Vec3d tan = tanRaw.multiply(1 / tanLen);
		Vec3d look = player.getRotationVec(1f);

		// forward along the line = where you look, with hysteresis so looking sideways doesn't flip it
		double dot = tan.x * look.x + tan.z * look.z;
		if (dot > 0.3) dirSign = 1;
		else if (dot < -0.3) dirSign = -1;
		double input = player.input.movementForward; // +1 W, -1 S
		double target = input * dirSign * SPEED;
		double prevSpeed = speed;
		speed += (target - speed) * RESPONSE;
		if (Math.abs(target) < 1e-3 && Math.abs(speed) < 0.005) speed = 0;

		if (player.input.jumping) {
			Vec3d dir = tan.multiply(speed);
			detach(client, true);
			player.setVelocity(dir.x, 0.5, dir.z);
			return;
		}

		// arc-length stepping: dt = distance / |dP/dt| keeps the speed constant along the sagging curve
		t += speed / tanLen;
		if (t > 1 || t < 0) {
			BlockPos node = t > 1 ? to : from;
			BlockPos cameFrom = t > 1 ? from : to;
			Vec3d travel = t > 1 ? tan : tan.multiply(-1);
			if (!switchLine(world, node, cameFrom, travel, look)) {
				t = MathHelper.clamp(t, 0, 1);
				speed = 0;
			}
			a = PowerLineGeometry.anchor(world, from);
			b = PowerLineGeometry.anchor(world, to);
			tan = PowerLineGeometry.tangent(a, b, t).normalize();
		}

		// pendulum: the body lags behind when accelerating and swings forward when braking
		double accel = speed - prevSpeed;
		swayVel += -0.12 * sway - accel * 2.5;
		swayVel *= 0.90;
		sway = MathHelper.clamp(sway + swayVel, -0.6, 0.6);

		Vec3d contact = PowerLineGeometry.point(a, b, t);
		Vec3d target3 = contact.add(tan.x * sway * Math.signum(speed == 0 ? 1 : speed), -HANG + Math.abs(sway) * 0.15,
				tan.z * sway * Math.signum(speed == 0 ? 1 : speed));
		Vec3d pos;
		if (grabTicks > 0) {
			// ease from where the player grabbed to the hanging position
			double k = 1 - (grabTicks - 1) / (double) GRAB_TICKS;
			k = k * k * (3 - 2 * k);
			pos = grabStart.lerp(target3, k);
			grabTicks--;
		} else {
			pos = target3;
		}
		player.setPosition(pos.x, pos.y, pos.z);
		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
		player.setOnGround(false);

		// sparks at the contact point, more when faster
		int sparks = Math.abs(speed) > 0.05 ? 1 + (int) (Math.abs(speed) * 5) : (world.random.nextInt(6) == 0 ? 1 : 0);
		for (int i = 0; i < sparks; i++) {
			world.addParticle(ParticleTypes.ELECTRIC_SPARK, contact.x + (world.random.nextDouble() - 0.5) * 0.15,
					contact.y - 0.05, contact.z + (world.random.nextDouble() - 0.5) * 0.15,
					-tan.x * speed * 0.5 + (world.random.nextDouble() - 0.5) * 0.2, -0.05 - world.random.nextDouble() * 0.1,
					-tan.z * speed * 0.5 + (world.random.nextDouble() - 0.5) * 0.2);
		}
		if (Math.abs(speed) > 0.2 && world.getTime() % 5 == 0) {
			world.playSound(player, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.3f, 1.7f);
		}
		if (Math.abs(speed) > 0.1 && world.random.nextInt(14) == 0) {
			world.playSound(player, BlockPos.ofFloored(contact), SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.PLAYERS, 0.15f, 1.8f);
		}
	}

	/** Sparks for other players riding nearby. */
	private static void remoteSparks(ClientWorld world) {
		if (REMOTE_RIDERS.isEmpty()) return;
		for (PlayerEntity p : world.getPlayers()) {
			if (!REMOTE_RIDERS.contains(p.getId())) continue;
			Vec3d hand = p.getPos().add(0, HANG, 0);
			Vec3d last = REMOTE_LAST.put(p.getId(), hand);
			double moved = last == null ? 0 : last.distanceTo(hand);
			int n = moved > 0.05 ? 1 + (int) (moved * 5) : 0;
			for (int i = 0; i < n; i++) {
				world.addParticle(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y - 0.05, hand.z,
						(world.random.nextDouble() - 0.5) * 0.2, -0.08, (world.random.nextDouble() - 0.5) * 0.2);
			}
		}
	}

	/** At a node, continue on the connected line most aligned with the travel/look direction. */
	private static boolean switchLine(ClientWorld world, BlockPos node, BlockPos cameFrom, Vec3d travel, Vec3d look) {
		if (!(world.getBlockEntity(node) instanceof PowerNodeBlockEntity n)) return false;
		Vec3d here = PowerLineGeometry.anchor(world, node);
		Vec3d want = look.multiply(0.6).add(travel.normalize().multiply(0.4)).normalize();
		BlockPos best = null;
		double bestDot = 0.15;
		for (BlockPos other : n.getConnections()) {
			if (other.equals(cameFrom) || !lineExists(world, node, other)) continue;
			Vec3d there = PowerLineGeometry.anchor(world, other);
			Vec3d dir = PowerLineGeometry.tangent(here, there, 0).normalize();
			double d = dir.dotProduct(want);
			if (d > bestDot) {
				bestDot = d;
				best = other;
			}
		}
		if (best == null) return false;
		// keep moving at the same speed in the new line's "forward" direction
		double mag = Math.abs(speed);
		from = node;
		to = best;
		t = 0.0;
		speed = mag;
		dirSign = 1;
		return true;
	}

	private static boolean lineExists(ClientWorld world, BlockPos a, BlockPos b) {
		return world.getBlockEntity(a) instanceof PowerNodeBlockEntity na && na.isConnectedTo(b)
				&& world.getBlockEntity(b) instanceof PowerNodeBlockEntity;
	}

	private static void attach(MinecraftClient client, Hit h) {
		attached = true;
		from = h.a();
		to = h.b();
		t = h.t();
		speed = 0;
		sway = 0;
		swayVel = 0;
		grabTicks = GRAB_TICKS;
		grabStart = client.player.getPos();
		ClientPlayNetworking.send(new ZiplinePayload(true));
		client.player.playSound(SoundEvents.BLOCK_CHAIN_HIT, 0.8f, 1.2f);
		client.player.playSound(SoundEvents.BLOCK_COPPER_BULB_TURN_ON, 0.4f, 1.5f);
	}

	private static void detach(MinecraftClient client, boolean jumped) {
		attached = false;
		grabCooldown = jumped ? 8 : 14;
		speed = 0;
		if (client.player != null) client.player.removeStatusEffectInternal(StatusEffects.LEVITATION);
		ClientPlayNetworking.send(new ZiplinePayload(false));
	}

	/** Closest point of any power line to {@code origin} within {@code range} (optionally in front of {@code look}). */
	@Nullable
	private static Hit findLine(ClientWorld world, Vec3d origin, double range, @Nullable Vec3d look) {
		Hit best = null;
		ChunkPos center = new ChunkPos(BlockPos.ofFloored(origin));
		int r = 3; // lines are at most 40 blocks long
		for (int cx = center.x - r; cx <= center.x + r; cx++) {
			for (int cz = center.z - r; cz <= center.z + r; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				if (!(world.getChunk(cx, cz) instanceof WorldChunk chunk)) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof PowerNodeBlockEntity node)) continue;
					for (BlockPos other : node.getConnections()) {
						if (other.asLong() < be.getPos().asLong()) continue; // each line once
						if (!(world.getBlockEntity(other) instanceof PowerNodeBlockEntity)) continue;
						Vec3d a = PowerLineGeometry.anchor(world, be.getPos());
						Vec3d b = PowerLineGeometry.anchor(world, other);
						int samples = Math.max(8, (int) (a.distanceTo(b) * 2));
						for (int i = 0; i <= samples; i++) {
							double tt = i / (double) samples;
							Vec3d p = PowerLineGeometry.point(a, b, tt);
							double d = p.distanceTo(origin);
							if (d > range) continue;
							if (look != null && p.subtract(origin).normalize().dotProduct(look) < 0.6) continue;
							if (best == null || d < best.dist()) best = new Hit(be.getPos(), other, tt, d);
						}
					}
				}
			}
		}
		return best;
	}
}
