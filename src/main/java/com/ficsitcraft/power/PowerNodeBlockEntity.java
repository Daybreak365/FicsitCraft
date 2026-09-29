package com.ficsitcraft.power;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for everything that can be wired into a power grid with power lines
 * (power poles, generators and powered machines).
 */
public abstract class PowerNodeBlockEntity extends BlockEntity {
	public static final int MAX_LINE_LENGTH = 40;

	protected final Set<BlockPos> connections = new LinkedHashSet<>();

	// Grid state, written by PowerGridManager at the end of each server tick
	protected boolean powered;
	protected double gridCapacity;
	protected double gridDemand;
	protected boolean gridFuseBlown;
	protected GridStats gridStats = GridStats.EMPTY;
	private boolean fuseResetRequested;

	protected PowerNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** How many power lines can be attached to this node. */
	public abstract int getMaxConnections();

	/** Power (MW) this node wants to consume this tick. */
	public double getPowerDemand() {
		return 0;
	}

	/** Power (MW) this node can produce this tick. */
	public double getPowerCapacity() {
		return 0;
	}

	public boolean isGenerator() {
		return false;
	}

	public boolean isFuseBlown() {
		return false;
	}

	public void setFuseBlown(boolean blown) {
	}

	/** Maximum power (MW) this node could consume when fully working (for grid statistics). */
	public double getMaxPowerDemand() {
		return 0;
	}

	/** Requests a fuse reset of the whole grid this node belongs to (from any pole, generator or machine). */
	public void requestFuseReset() {
		fuseResetRequested = true;
	}

	public boolean consumeFuseResetRequest() {
		boolean r = fuseResetRequested;
		fuseResetRequested = false;
		return r;
	}

	/** Called once per tick by the grid manager. */
	public void onGridUpdate(GridStats stats) {
		this.gridStats = stats;
		this.gridCapacity = stats.capacity();
		this.gridDemand = stats.demand();
		this.gridFuseBlown = stats.fuseBlown();
		this.powered = stats.ok();
	}

	public GridStats getGridStats() {
		return gridStats;
	}

	/** Where power lines attach, relative to the block origin. */
	public Vec3d getConnectorOffset() {
		return new Vec3d(0.5, 1.0, 0.5);
	}

	public boolean isPowered() {
		return powered;
	}

	public double getGridCapacity() {
		return gridCapacity;
	}

	public double getGridDemand() {
		return gridDemand;
	}

	public boolean isGridFuseBlown() {
		return gridFuseBlown;
	}

	// ------------------------------------------------------------ connections

	public Set<BlockPos> getConnections() {
		return Collections.unmodifiableSet(connections);
	}

	public boolean hasFreeConnection() {
		return connections.size() < getMaxConnections();
	}

	public boolean isConnectedTo(BlockPos other) {
		return connections.contains(other);
	}

	public void addConnection(BlockPos other) {
		if (connections.add(other.toImmutable())) sync();
	}

	public void removeConnection(BlockPos other) {
		if (connections.remove(other)) sync();
	}

	/** Removes all lines from and to this node. Returns the number of lines removed. */
	public int disconnectAll() {
		if (world == null) return 0;
		List<BlockPos> copy = new ArrayList<>(connections);
		for (BlockPos p : copy) {
			if (world.getBlockEntity(p) instanceof PowerNodeBlockEntity other) other.removeConnection(pos);
		}
		connections.clear();
		sync();
		return copy.size();
	}

	/** Register this node for the power solve of the current tick. Call from the server ticker. */
	protected void tickPower() {
		if (world instanceof ServerWorld sw) {
			// Drop dangling lines whose other end was removed while this chunk was loaded.
			if (sw.getTime() % 40 == 0 && !connections.isEmpty()) {
				boolean changed = connections.removeIf(p -> sw.isChunkLoaded(p) && !(sw.getBlockEntity(p) instanceof PowerNodeBlockEntity));
				if (changed) sync();
			}
			PowerGridManager.get(sw).mark(this);
		}
	}

	// ------------------------------------------------------------ sync & nbt

	public void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			BlockState state = getCachedState();
			world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		long[] arr = new long[connections.size()];
		int i = 0;
		for (BlockPos p : connections) arr[i++] = p.asLong();
		nbt.putLongArray("PowerLines", arr);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		connections.clear();
		for (long l : nbt.getLongArray("PowerLines")) connections.add(BlockPos.fromLong(l));
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
		return createNbt(lookup);
	}
}
