package com.ficsitcraft.power;

import com.ficsitcraft.network.PowerEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Solves every power grid of a world once per tick.
 * <p>
 * Every loaded power node registers itself during its block entity tick; at the end of the world tick the
 * nodes are grouped into grids by following their power lines. If the consumption of a grid exceeds its
 * production the fuse blows (like in Satisfactory) until someone resets it at a generator or power pole.
 */
public final class PowerGridManager {
	private static final Map<ServerWorld, PowerGridManager> MANAGERS = new WeakHashMap<>();
	private static final double EPS = 1e-6;
	/** Players within this distance of any node of a grid are notified about its fuse. */
	private static final double NOTIFY_RANGE = 96;

	private final Map<BlockPos, PowerNodeBlockEntity> active = new HashMap<>();

	public static PowerGridManager get(ServerWorld world) {
		return MANAGERS.computeIfAbsent(world, w -> new PowerGridManager());
	}

	public static void onWorldTickEnd(ServerWorld world) {
		PowerGridManager m = MANAGERS.get(world);
		if (m != null) m.solve(world);
	}

	public void mark(PowerNodeBlockEntity node) {
		active.put(node.getPos(), node);
	}

	private void solve(ServerWorld world) {
		if (active.isEmpty()) return;
		Set<BlockPos> visited = new HashSet<>();
		for (PowerNodeBlockEntity start : active.values()) {
			if (!visited.add(start.getPos())) continue;
			List<PowerNodeBlockEntity> grid = new ArrayList<>();
			ArrayDeque<PowerNodeBlockEntity> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				PowerNodeBlockEntity n = queue.poll();
				if (n.isRemoved()) continue;
				grid.add(n);
				for (BlockPos p : n.getConnections()) {
					PowerNodeBlockEntity other = active.get(p);
					// lines are only valid if both ends know each other
					if (other != null && other.isConnectedTo(n.getPos()) && visited.add(p)) queue.add(other);
				}
			}
			solveGrid(world, grid);
		}
		active.clear();
	}

	private static void solveGrid(ServerWorld world, List<PowerNodeBlockEntity> grid) {
		boolean reset = false;
		boolean fuse = false;
		double capacity = 0;
		double demand = 0;
		double maxDemand = 0;
		int generators = 0;
		int consumers = 0;
		for (PowerNodeBlockEntity n : grid) {
			if (n.consumeFuseResetRequest()) reset = true;
			if (n.isGenerator()) {
				generators++;
				if (n.isFuseBlown()) fuse = true;
				capacity += n.getPowerCapacity();
			}
			double max = n.getMaxPowerDemand();
			if (max > 0) consumers++;
			maxDemand += max;
			demand += n.getPowerDemand();
		}

		boolean wasBlown = fuse;
		if (reset && fuse) {
			fuse = false;
			for (PowerNodeBlockEntity n : grid) if (n.isGenerator()) n.setFuseBlown(false);
		}
		if (!fuse && capacity > 0 && demand > capacity + EPS) {
			fuse = true;
			for (PowerNodeBlockEntity n : grid) if (n.isGenerator()) n.setFuseBlown(true);
		}

		if (fuse && !wasBlown) {
			notifyPlayers(world, grid, new PowerEventPayload(PowerEventPayload.FUSE_BLOWN, (float) demand, (float) capacity), true);
		} else if (!fuse && wasBlown) {
			notifyPlayers(world, grid, new PowerEventPayload(PowerEventPayload.RESTORED, (float) demand, (float) capacity), false);
		}

		boolean ok = !fuse && capacity > 0;
		double load = ok ? Math.min(1.0, demand / capacity) : 0;
		GridStats stats = new GridStats(capacity, demand, maxDemand, fuse, ok, load, generators, consumers, grid.size());
		for (PowerNodeBlockEntity n : grid) n.onGridUpdate(stats);
	}

	private static void notifyPlayers(ServerWorld world, List<PowerNodeBlockEntity> grid, PowerEventPayload payload, boolean blown) {
		// sound at every generator of the grid
		for (PowerNodeBlockEntity n : grid) {
			if (!n.isGenerator()) continue;
			BlockPos p = n.getPos();
			if (blown) {
				world.playSound(null, p, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
				world.playSound(null, p, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.8f);
			} else {
				world.playSound(null, p, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0f, 1.2f);
			}
		}
		double r2 = NOTIFY_RANGE * NOTIFY_RANGE;
		for (ServerPlayerEntity player : world.getPlayers()) {
			for (PowerNodeBlockEntity n : grid) {
				if (player.getBlockPos().getSquaredDistance(n.getPos()) <= r2) {
					ServerPlayNetworking.send(player, payload);
					break;
				}
			}
		}
	}
}
