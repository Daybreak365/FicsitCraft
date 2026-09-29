package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.PipeBlock;
import com.ficsitcraft.fluid.FluidNetworkManager;
import com.ficsitcraft.fluid.FluidNode;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class PipeBlockEntity extends BlockEntity implements FluidNode, net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos> {
	private SfFluid fluid = SfFluid.NONE;
	private double amount;
	private double flow;          // smoothed m³/tick through this segment
	private double boostHead = Double.NaN;
	private long boostTime = -1;

	// sync throttling
	private SfFluid syncedFluid = SfFluid.NONE;
	private double syncedAmount;
	private long lastSync;

	// client-side smoothing
	private double displayAmount;
	private double prevDisplayAmount;

	public PipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PIPE, pos, state);
	}

	private PipeBlock block() {
		return getCachedState().getBlock() instanceof PipeBlock p ? p : null;
	}

	// ------------------------------------------------------------------ FluidNode

	@Override
	public BlockPos getNodePos() {
		return pos;
	}

	@Override
	public SfFluid getFluid() {
		return fluid;
	}

	@Override
	public void setFluid(SfFluid fluid) {
		this.fluid = fluid;
	}

	@Override
	public double getAmount() {
		return amount;
	}

	@Override
	public void setAmount(double amount) {
		this.amount = Math.max(0, amount);
		markDirty();
	}

	@Override
	public double getCapacity() {
		PipeBlock b = block();
		return b == null ? 1 : b.capacity();
	}

	@Override
	public double getMaxFlow() {
		PipeBlock b = block();
		return b == null ? 0.25 : b.maxFlow();
	}

	@Override
	public boolean connectsTo(Direction side) {
		BlockState s = getCachedState();
		return s.getBlock() instanceof PipeBlock && s.get(PipeBlock.PROPS.get(side));
	}

	/** Pumps / extractors pushing into this pipe raise its head for this tick. */
	public void applyBoost(double head) {
		if (world == null) return;
		if (boostTime != world.getTime() || Double.isNaN(boostHead) || head > boostHead) boostHead = head;
		boostTime = world.getTime();
	}

	@Override
	public double getSourceHead() {
		return world != null && boostTime == world.getTime() ? boostHead : Double.NaN;
	}

	@Override
	public void onFlowSolved(double throughput) {
		flow = flow * 0.9 + throughput * 0.1;
	}

	@Override
	public boolean isNodeRemoved() {
		return isRemoved();
	}

	public double getFlowPerMinute() {
		return flow * 1200;
	}

	public double getDisplayAmount(float tickDelta) {
		return prevDisplayAmount + (displayAmount - prevDisplayAmount) * tickDelta;
	}

	public Text describe() {
		if (fluid == SfFluid.NONE || amount < 1e-4) {
			return Text.translatable("message.ficsitcraft.pipe_empty").formatted(Formatting.GRAY);
		}
		return Text.translatable("message.ficsitcraft.pipe_info", fluid.displayName(), String.format("%.2f", amount),
				String.format("%.0f", getCapacity()), String.format("%.0f", getFlowPerMinute())).formatted(Formatting.AQUA);
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(World world, BlockPos pos, BlockState state, PipeBlockEntity be) {
		if (world instanceof ServerWorld sw) FluidNetworkManager.get(sw).mark(be);
		long t = world.getTime();
		double threshold = be.getCapacity() * 0.03;
		boolean changed = be.syncedFluid != be.fluid || Math.abs(be.syncedAmount - be.amount) > threshold;
		if (changed && t - be.lastSync >= 4) {
			be.syncedFluid = be.fluid;
			be.syncedAmount = be.amount;
			be.lastSync = t;
			world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, PipeBlockEntity be) {
		be.prevDisplayAmount = be.displayAmount;
		be.displayAmount += (be.amount - be.displayAmount) * 0.35;
	}

	/**
	 * Total fluid volume and capacity of the pipe run containing {@code start} (connected pipes only; pumps and
	 * buildings end a run). Returns {amount, capacity}.
	 */
	public static double[] sectionVolume(World world, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		double amount = 0;
		double capacity = 0;
		while (!queue.isEmpty() && seen.size() < 2048) {
			BlockPos p = queue.poll();
			if (!(world.getBlockEntity(p) instanceof PipeBlockEntity pipe)) continue;
			amount += pipe.getAmount();
			capacity += pipe.getCapacity();
			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (pipe.connectsTo(d) && world.getBlockEntity(n) instanceof PipeBlockEntity && seen.add(n)) queue.add(n);
			}
		}
		return new double[]{amount, capacity};
	}

	/** Empties every pipe connected to {@code start}. Returns the number of flushed segments. */
	public static int flushNetwork(World world, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		int count = 0;
		while (!queue.isEmpty() && count < 4096) {
			BlockPos p = queue.poll();
			if (!(world.getBlockEntity(p) instanceof FluidNode node)) continue;
			node.setAmount(0);
			node.setFluid(SfFluid.NONE);
			if (node instanceof PipeBlockEntity pipe) pipe.lastSync = -100;
			count++;
			for (Direction d : Direction.values()) {
				BlockPos n = p.offset(d);
				if (node.connectsTo(d) && seen.add(n)) queue.add(n);
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ inspector GUI

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
		return pos;
	}

	@Override
	public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv,
														 net.minecraft.entity.player.PlayerEntity player) {
		return new com.ficsitcraft.screen.PipeScreenHandler(syncId, inv, this);
	}

	// ------------------------------------------------------------------ nbt & sync

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putString("Fluid", fluid.id);
		nbt.putDouble("Amount", amount);
		nbt.putDouble("Flow", flow);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		fluid = SfFluid.byId(nbt.getString("Fluid"));
		amount = nbt.getDouble("Amount");
		flow = nbt.getDouble("Flow");
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
