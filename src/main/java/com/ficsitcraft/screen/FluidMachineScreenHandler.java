package com.ficsitcraft.screen;

import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Status GUI shared by the Water Extractor and the Pipeline Pump. */
public class FluidMachineScreenHandler extends ScreenHandler {
	public static final int P_FLAGS = 0, P_FLOW = 1, P_LIFT = 2, P_AMOUNT = 3, P_CAPACITY = 4, P_FLUID = 5, P_POWER = 6,
			P_GRID_CAP = 7, P_GRID_DEMAND = 8, COUNT = 9;
	public static final int F_POWERED = 1, F_WORKING = 2, F_FUSE = 4, F_IDLE = 8, F_NO_SOURCE = 16;

	private final PropertyDelegate props;
	@Nullable
	private final PowerNodeBlockEntity machine;
	private final BlockPos pos;

	public FluidMachineScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		super(ModScreenHandlers.FLUID_MACHINE, syncId);
		this.props = new ArrayPropertyDelegate(COUNT);
		this.machine = null;
		this.pos = pos;
		addProperties(props);
	}

	public FluidMachineScreenHandler(int syncId, PlayerInventory inv, PowerNodeBlockEntity machine, PropertyDelegate props) {
		super(ModScreenHandlers.FLUID_MACHINE, syncId);
		this.props = props;
		this.machine = machine;
		this.pos = machine.getPos();
		addProperties(props);
	}

	/** Builds the server-side property delegate (values scaled x10 / x100 to fit in shorts). */
	public static PropertyDelegate delegate(IntSupplier flags, DoubleSupplier flowPerMin, DoubleSupplier lift, DoubleSupplier amount,
											DoubleSupplier capacity, Supplier<SfFluid> fluid, DoubleSupplier powerMW,
											DoubleSupplier gridCap, DoubleSupplier gridDemand) {
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return switch (index) {
					case P_FLAGS -> flags.getAsInt();
					case P_FLOW -> clamp(flowPerMin.getAsDouble() * 10);
					case P_LIFT -> clamp(lift.getAsDouble());
					case P_AMOUNT -> clamp(amount.getAsDouble() * 100);
					case P_CAPACITY -> clamp(capacity.getAsDouble() * 100);
					case P_FLUID -> fluid.get().ordinal();
					case P_POWER -> clamp(powerMW.getAsDouble() * 10);
					case P_GRID_CAP -> clamp(gridCap.getAsDouble() * 10);
					case P_GRID_DEMAND -> clamp(gridDemand.getAsDouble() * 10);
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return COUNT;
			}
		};
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(32000, Math.round(v)));
	}

	public int get(int i) {
		return props.get(i);
	}

	public BlockPos getPos() {
		return pos;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return machine == null || (!machine.isRemoved() && player.squaredDistanceTo(pos.toCenterPos()) < 12 * 12);
	}
}
