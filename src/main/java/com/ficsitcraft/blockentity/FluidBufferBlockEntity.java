package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.FluidBufferBlock;
import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.fluid.FluidEndpoint;
import com.ficsitcraft.fluid.FluidNetworkManager;
import com.ficsitcraft.fluid.FluidNode;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.Multiblocks;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.FluidBufferScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fluid Buffer tank. It is one big node of the pipe simulation: its head is {@code baseY + fill × tankHeight}, so it
 * fills from pipes above its level and drains into pipes below it, just like a real tank.
 */
public class FluidBufferBlockEntity extends BlockEntity implements FluidNode, FluidEndpoint, ExtendedScreenHandlerFactory<BlockPos> {
	private SfFluid fluid = SfFluid.NONE;
	private double amount;
	private double inAcc;
	private double outAcc;
	private double inflow;   // m³/tick (smoothed)
	private double outflow;
	private List<BlockPos> cells;

	// sync / client smoothing
	private double syncedAmount = -1;
	private SfFluid syncedFluid = SfFluid.NONE;
	private long lastSync;
	private double display;
	private double prevDisplay;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case FluidBufferScreenHandler.P_AMOUNT -> clamp(amount * 10);
				case FluidBufferScreenHandler.P_CAPACITY -> clamp(getCapacity() * 10);
				case FluidBufferScreenHandler.P_FLUID -> fluid.ordinal();
				case FluidBufferScreenHandler.P_INFLOW -> clamp(inflow * 1200 * 10);
				case FluidBufferScreenHandler.P_OUTFLOW -> clamp(outflow * 1200 * 10);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return FluidBufferScreenHandler.COUNT;
		}
	};

	public FluidBufferBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FLUID_BUFFER, pos, state);
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(32000, Math.round(v)));
	}

	public void flush() {
		amount = 0;
		fluid = SfFluid.NONE;
		markDirty();
		syncedAmount = -1;
	}

	public double getDisplayAmount(float delta) {
		return prevDisplay + (display - prevDisplay) * delta;
	}

	// ------------------------------------------------------------------ FluidNode

	@Override
	public BlockPos getNodePos() {
		return pos;
	}

	@Override
	public List<BlockPos> getCells() {
		if (cells == null) {
			BlockState state = getCachedState();
			Footprint fp = Multiblocks.get(state.getBlock());
			cells = fp == null || !state.contains(MachineBlock.FACING) ? List.of(pos) : fp.positions(pos, state.get(MachineBlock.FACING));
		}
		return cells;
	}

	@Override
	public double getHeadHeight() {
		Footprint fp = Multiblocks.get(getCachedState().getBlock());
		return fp == null ? 1 : fp.height();
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
		double a = Math.max(0, amount);
		double delta = a - this.amount;
		if (delta > 0) inAcc += delta;
		else outAcc -= delta;
		this.amount = a;
		markDirty();
	}

	@Override
	public double getCapacity() {
		return FluidBufferBlock.CAPACITY;
	}

	@Override
	public double getMaxFlow() {
		return 600.0 / 1200.0;
	}

	@Override
	public boolean connectsTo(Direction side) {
		return true;
	}

	@Override
	public boolean isNodeRemoved() {
		return isRemoved();
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(World world, BlockPos pos, BlockState state, FluidBufferBlockEntity be) {
		if (world instanceof ServerWorld sw) FluidNetworkManager.get(sw).mark(be);
		be.inflow = be.inflow * 0.9 + be.inAcc * 0.1;
		be.outflow = be.outflow * 0.9 + be.outAcc * 0.1;
		be.inAcc = 0;
		be.outAcc = 0;
		long t = world.getTime();
		boolean changed = be.syncedFluid != be.fluid || Math.abs(be.syncedAmount - be.amount) > be.getCapacity() * 0.005;
		if (changed && t - be.lastSync >= 5) {
			be.syncedAmount = be.amount;
			be.syncedFluid = be.fluid;
			be.lastSync = t;
			world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, FluidBufferBlockEntity be) {
		be.prevDisplay = be.display;
		be.display += (be.amount - be.display) * 0.2;
	}

	// ------------------------------------------------------------------ nbt, sync, gui

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putString("Fluid", fluid.id);
		nbt.putDouble("Amount", amount);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		fluid = SfFluid.byId(nbt.getString("Fluid"));
		amount = nbt.getDouble("Amount");
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

	@Override
	public Text getDisplayName() {
		return getCachedState().getBlock().getName();
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
		return new FluidBufferScreenHandler(syncId, inv, this, properties);
	}
}
