package com.ficsitcraft.progress;

import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.data.Building;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** World-wide (shared by all pioneers, like a Satisfactory save) HUB progression. */
public class ProgressState extends PersistentState {
	private static final PersistentState.Type<ProgressState> TYPE =
			new PersistentState.Type<>(ProgressState::new, ProgressState::fromNbt, null);

	private long completed;
	/** Items already deposited into not-yet-completed milestones (milestone index -> amount per cost entry). */
	private final java.util.Map<Integer, int[]> deposits = new java.util.HashMap<>();

	public static ProgressState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "ficsitcraft_progress");
	}

	/** Server-side helper; returns null on the client. */
	public static ProgressState get(World world) {
		MinecraftServer server = world.getServer();
		return server == null ? null : get(server);
	}

	public long mask() {
		return completed;
	}

	public boolean isDone(int milestone) {
		return Milestones.isDone(completed, milestone);
	}

	public boolean isUnlocked(SfRecipe recipe) {
		return isDone(recipe.milestone());
	}

	public boolean isUnlocked(Building building) {
		return isDone(building.milestone());
	}

	public void complete(int milestone) {
		completed |= 1L << milestone;
		deposits.remove(milestone);
		markDirty();
	}

	public void reset() {
		completed = 0;
		deposits.clear();
		markDirty();
	}

	public int getDeposited(int milestone, int costIndex) {
		int[] d = deposits.get(milestone);
		return d == null || costIndex >= d.length ? 0 : d[costIndex];
	}

	public void addDeposit(int milestone, int costIndex, int amount, int costEntries) {
		int[] d = deposits.computeIfAbsent(milestone, k -> new int[costEntries]);
		if (costIndex < d.length) d[costIndex] += amount;
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		nbt.putLong("Completed", completed);
		NbtCompound dep = new NbtCompound();
		deposits.forEach((k, v) -> dep.putIntArray(Integer.toString(k), v));
		nbt.put("Deposits", dep);
		return nbt;
	}

	private static ProgressState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		ProgressState s = new ProgressState();
		s.completed = nbt.getLong("Completed");
		NbtCompound dep = nbt.getCompound("Deposits");
		for (String key : dep.getKeys()) {
			try {
				s.deposits.put(Integer.parseInt(key), dep.getIntArray(key));
			} catch (NumberFormatException ignored) {
			}
		}
		return s;
	}
}
