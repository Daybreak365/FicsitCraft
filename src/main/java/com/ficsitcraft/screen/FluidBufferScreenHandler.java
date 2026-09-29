package com.ficsitcraft.screen;

import com.ficsitcraft.blockentity.FluidBufferBlockEntity;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Fluid Buffer GUI: stored fluid, level, inflow / outflow and a flush button. */
public class FluidBufferScreenHandler extends ScreenHandler {
	public static final int P_AMOUNT = 0, P_CAPACITY = 1, P_FLUID = 2, P_INFLOW = 3, P_OUTFLOW = 4, COUNT = 5;
	public static final int BUTTON_FLUSH = 0;

	private final PropertyDelegate props;
	@Nullable
	private final FluidBufferBlockEntity buffer;
	private final BlockPos pos;

	public FluidBufferScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		super(ModScreenHandlers.FLUID_BUFFER, syncId);
		this.props = new ArrayPropertyDelegate(COUNT);
		this.buffer = null;
		this.pos = pos;
		addProperties(props);
	}

	public FluidBufferScreenHandler(int syncId, PlayerInventory inv, FluidBufferBlockEntity buffer, PropertyDelegate props) {
		super(ModScreenHandlers.FLUID_BUFFER, syncId);
		this.props = props;
		this.buffer = buffer;
		this.pos = buffer.getPos();
		addProperties(props);
	}

	public int get(int i) {
		return props.get(i);
	}

	public SfFluid getFluid() {
		return SfFluid.byOrdinal(props.get(P_FLUID));
	}

	public BlockPos getPos() {
		return pos;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == BUTTON_FLUSH && buffer != null) {
			buffer.flush();
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return buffer == null || (!buffer.isRemoved() && player.squaredDistanceTo(pos.toCenterPos()) < 12 * 12);
	}
}
