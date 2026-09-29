package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.block.WaterExtractorBlock;
import com.ficsitcraft.fluid.FluidEndpoint;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.Multiblocks;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.screen.FluidMachineScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WaterExtractorBlockEntity extends PowerNodeBlockEntity implements FluidEndpoint, ExtendedScreenHandlerFactory<BlockPos> {
	private static final double BUFFER = 2.0;

	private SfFluid source = SfFluid.NONE;
	private double buffer;
	private double flow;
	private boolean wantsPower;
	private final List<PipeBlockEntity> outlets = new ArrayList<>();
	private long outletsScanned = -100;

	private final PropertyDelegate properties = FluidMachineScreenHandler.delegate(
			() -> (powered ? FluidMachineScreenHandler.F_POWERED : 0) | (flow > 1e-4 ? FluidMachineScreenHandler.F_WORKING : 0)
					| (gridFuseBlown ? FluidMachineScreenHandler.F_FUSE : 0) | (!wantsPower ? FluidMachineScreenHandler.F_IDLE : 0)
					| (source == SfFluid.NONE ? FluidMachineScreenHandler.F_NO_SOURCE : 0),
			() -> flow * 1200, () -> WaterExtractorBlock.HEAD_LIFT, () -> buffer, () -> BUFFER, () -> source,
			() -> WaterExtractorBlock.POWER_MW, () -> gridCapacity, () -> gridDemand);

	public WaterExtractorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WATER_EXTRACTOR, pos, state);
	}

	public void setSource(SfFluid source) {
		this.source = source;
		markDirty();
	}

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public double getMaxPowerDemand() {
		return WaterExtractorBlock.POWER_MW;
	}

	@Override
	public double getPowerDemand() {
		return wantsPower ? WaterExtractorBlock.POWER_MW : 0;
	}

	@Override
	public Vec3d getConnectorOffset() {
		return Multiblocks.connectorOffset(getCachedState());
	}

	private void scanOutlets(World world) {
		outlets.clear();
		BlockState state = getCachedState();
		Footprint fp = Multiblocks.get(state.getBlock());
		List<BlockPos> cells = fp == null ? List.of(pos) : fp.positions(pos, state.get(MachineBlock.FACING));
		Set<BlockPos> own = new HashSet<>(cells);
		for (BlockPos c : cells) {
			for (Direction d : Direction.values()) {
				BlockPos n = c.offset(d);
				if (own.contains(n)) continue;
				if (world.getBlockEntity(n) instanceof PipeBlockEntity pipe && pipe.connectsTo(d.getOpposite())) outlets.add(pipe);
			}
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, WaterExtractorBlockEntity be) {
		be.tickPower();
		if (world.getTime() - be.outletsScanned >= 20) {
			be.scanOutlets(world);
			be.outletsScanned = world.getTime();
		}
		double perTick = WaterExtractorBlock.RATE_PER_MIN / 1200.0;
		be.wantsPower = be.source != SfFluid.NONE && be.buffer < BUFFER - 1e-6;
		if (be.powered && be.wantsPower) be.buffer = Math.min(BUFFER, be.buffer + perTick);

		// push into connected pipes, and pressurise them with the extractor's head lift
		double moved = 0;
		int remaining = be.outlets.size();
		for (PipeBlockEntity pipe : be.outlets) {
			if (pipe.isRemoved()) {
				remaining--;
				continue;
			}
			if (be.buffer > 1e-6 && be.powered) pipe.applyBoost(pos.getY() + WaterExtractorBlock.HEAD_LIFT);
			if (pipe.getAmount() > 1e-6 && pipe.getFluid() != be.source) {
				remaining--;
				continue;
			}
			double q = Math.min(be.buffer / Math.max(1, remaining), Math.min(pipe.getMaxFlow(), pipe.getCapacity() - pipe.getAmount()));
			if (q > 1e-9) {
				if (pipe.getAmount() <= 1e-6) pipe.setFluid(be.source);
				pipe.setAmount(pipe.getAmount() + q);
				be.buffer -= q;
				moved += q;
			}
			remaining--;
		}
		be.flow = be.flow * 0.9 + moved * 0.1;
		boolean active = be.powered && be.flow > 1e-3;
		if (state.get(MachineBlock.ACTIVE) != active) world.setBlockState(pos, state.with(MachineBlock.ACTIVE, active), 3);
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.writeNbt(nbt, lookup);
		nbt.putString("Source", source.id);
		nbt.putDouble("Buffer", buffer);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		super.readNbt(nbt, lookup);
		source = SfFluid.byId(nbt.getString("Source"));
		buffer = nbt.getDouble("Buffer");
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
		return new FluidMachineScreenHandler(syncId, inv, this, properties);
	}
}
