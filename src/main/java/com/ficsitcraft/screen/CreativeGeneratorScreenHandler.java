package com.ficsitcraft.screen;

import com.ficsitcraft.blockentity.CreativeGeneratorBlockEntity;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The Creative Generator's little "prosperity engine" window: output switch, fuse reset and the grid numbers. */
public class CreativeGeneratorScreenHandler extends ScreenHandler {
	public static final int BUTTON_RESET_FUSE = 0, BUTTON_TOGGLE = 1;
	public static final int P_ENABLED = 0, P_CAPACITY = 1, P_DEMAND = 2, P_FUSE = 3, P_GENERATORS = 4, P_CONSUMERS = 5, COUNT = 6;

	private final PropertyDelegate props;
	@Nullable
	private final CreativeGeneratorBlockEntity generator;
	private final BlockPos pos;

	public CreativeGeneratorScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		super(ModScreenHandlers.CREATIVE_GENERATOR, syncId);
		this.props = new ArrayPropertyDelegate(COUNT);
		this.generator = null;
		this.pos = pos;
		addProperties(props);
	}

	public CreativeGeneratorScreenHandler(int syncId, PlayerInventory inv, CreativeGeneratorBlockEntity be, PropertyDelegate props) {
		super(ModScreenHandlers.CREATIVE_GENERATOR, syncId);
		this.props = props;
		this.generator = be;
		this.pos = be.getPos();
		addProperties(props);
	}

	public int get(int i) {
		return props.get(i);
	}

	public BlockPos getPos() {
		return pos;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (generator == null) return false;
		if (id == BUTTON_RESET_FUSE) {
			generator.requestFuseReset();
			return true;
		}
		if (id == BUTTON_TOGGLE) {
			generator.setEnabled(!generator.isEnabled());
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
		return generator == null || (!generator.isRemoved() && player.squaredDistanceTo(pos.toCenterPos()) < 12 * 12);
	}
}
