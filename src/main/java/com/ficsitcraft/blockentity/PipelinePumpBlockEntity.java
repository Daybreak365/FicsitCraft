package com.ficsitcraft.blockentity;

import com.ficsitcraft.block.PipelinePumpBlock;
import com.ficsitcraft.fluid.FluidNetworkManager;
import com.ficsitcraft.fluid.FluidNode;
import com.ficsitcraft.fluid.SfFluid;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * In-line pump. It is a pipe node that only lets fluid through from back to front (check valve). While powered it
 * sucks on its inlet and pushes out of its outlet with a head of {@code y + 20}.
 */
public class PipelinePumpBlockEntity extends PowerNodeBlockEntity
		implements FluidNode, FluidNetworkManager.PumpHead, ExtendedScreenHandlerFactory<BlockPos> {
	private static final double CAPACITY = 0.5;
	private static final double MAX_FLOW = 300.0 / 1200.0;

	private SfFluid fluid = SfFluid.NONE;
	private double amount;
	private double flow;
	private boolean wantsPower;

	private final PropertyDelegate properties = FluidMachineScreenHandler.delegate(
			() -> (powered ? 1 : 0) | (flow > 1e-4 ? 2 : 0) | (gridFuseBlown ? 4 : 0) | (!wantsPower ? 8 : 0),
			() -> flow * 1200, () -> PipelinePumpBlock.HEAD_LIFT, () -> amount, () -> CAPACITY, () -> fluid,
			() -> PipelinePumpBlock.POWER_MW, () -> gridCapacity, () -> gridDemand);

	public PipelinePumpBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PIPELINE_PUMP, pos, state);
	}

	private Direction facing() {
		return getCachedState().get(PipelinePumpBlock.FACING);
	}

	// ------------------------------------------------------------------ power

	@Override
	public int getMaxConnections() {
		return 2;
	}

	@Override
	public double getMaxPowerDemand() {
		return PipelinePumpBlock.POWER_MW;
	}

	@Override
	public double getPowerDemand() {
		return wantsPower ? PipelinePumpBlock.POWER_MW : 0;
	}

	@Override
	public Vec3d getConnectorOffset() {
		return new Vec3d(0.5, 0.95, 0.5);
	}

	public static void tick(World world, BlockPos pos, BlockState state, PipelinePumpBlockEntity be) {
		be.tickPower();
		if (world instanceof ServerWorld sw) FluidNetworkManager.get(sw).mark(be);
		// the pump wants power while there is fluid to move at its inlet or in itself
		Direction back = be.facing().getOpposite();
		boolean inletHasFluid = world.getBlockEntity(pos.offset(back)) instanceof FluidNode n && n.getAmount() > 1e-4;
		be.wantsPower = inletHasFluid || be.amount > 1e-4;
		boolean active = be.powered && be.flow > 1e-4;
		if (state.get(PipelinePumpBlock.ACTIVE) != active) world.setBlockState(pos, state.with(PipelinePumpBlock.ACTIVE, active), 3);
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
		return CAPACITY;
	}

	@Override
	public double getMaxFlow() {
		return MAX_FLOW;
	}

	@Override
	public boolean connectsTo(Direction side) {
		return side.getAxis() == facing().getAxis();
	}

	@Override
	public boolean canOutput(Direction side) {
		return side == facing();
	}

	@Override
	public boolean canInput(Direction side) {
		return side == facing().getOpposite();
	}

	@Override
	public double getSourceHead() {
		return powered ? pos.getY() + PipelinePumpBlock.HEAD_LIFT : Double.NaN;
	}

	@Override
	public Double inletHead(Direction side) {
		// suction: while running, the inlet sees a low head so fluid is drawn in (up to the pump's lift below it)
		return powered && side == facing().getOpposite() && amount < CAPACITY * 0.95 ? (double) pos.getY() - 1 : null;
	}

	@Override
	public void onFlowSolved(double throughput) {
		flow = flow * 0.9 + throughput * 0.1;
	}

	@Override
	public boolean isNodeRemoved() {
		return isRemoved();
	}

	// ------------------------------------------------------------------ nbt & gui

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
