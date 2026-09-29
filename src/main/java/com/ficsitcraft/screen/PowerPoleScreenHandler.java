package com.ficsitcraft.screen;

import com.ficsitcraft.blockentity.PowerPoleBlockEntity;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Power pole GUI: grid statistics, power graph and fuse reset. */
public class PowerPoleScreenHandler extends ScreenHandler {
	public static final int BUTTON_RESET_FUSE = 0;

	public static final int P_CAPACITY = 0;
	public static final int P_DEMAND = 1;
	public static final int P_MAX_DEMAND = 2;
	public static final int P_FLAGS = 3;
	public static final int P_GENERATORS = 4;
	public static final int P_CONSUMERS = 5;
	public static final int P_NODES = 6;
	public static final int P_LINES = 7;
	public static final int P_MAX_LINES = 8;
	public static final int P_HEAD = 9;
	public static final int P_SAMPLES = 10;
	public static final int P_HISTORY = 11;
	public static final int PROPERTY_COUNT = P_HISTORY + PowerPoleBlockEntity.HISTORY * 2;

	private final PropertyDelegate props;
	@Nullable
	private final PowerPoleBlockEntity pole;
	private final BlockPos pos;

	public PowerPoleScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		super(ModScreenHandlers.POWER_POLE, syncId);
		this.props = new ArrayPropertyDelegate(PROPERTY_COUNT);
		this.pole = null;
		this.pos = pos;
		addProperties(props);
	}

	public PowerPoleScreenHandler(int syncId, PlayerInventory inv, PowerPoleBlockEntity pole, PropertyDelegate props) {
		super(ModScreenHandlers.POWER_POLE, syncId);
		this.props = props;
		this.pole = pole;
		this.pos = pole.getPos();
		addProperties(props);
	}

	public int get(int index) {
		return props.get(index);
	}

	public double mw(int index) {
		return props.get(index) / 10.0;
	}

	public boolean isFuseBlown() {
		return (props.get(P_FLAGS) & 1) != 0;
	}

	public boolean isPowered() {
		return (props.get(P_FLAGS) & 2) != 0;
	}

	/** i = 0 is the oldest sample. Returns MW. */
	public double demandSample(int i) {
		return sample(P_HISTORY, i);
	}

	public double capacitySample(int i) {
		return sample(P_HISTORY + PowerPoleBlockEntity.HISTORY, i);
	}

	public int sampleCount() {
		return Math.min(props.get(P_SAMPLES), PowerPoleBlockEntity.HISTORY);
	}

	private double sample(int base, int i) {
		int n = PowerPoleBlockEntity.HISTORY;
		int count = sampleCount();
		int start = Math.floorMod(props.get(P_HEAD) - count, n);
		return props.get(base + (start + i) % n) / 10.0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == BUTTON_RESET_FUSE && pole != null) {
			pole.requestFuseReset();
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
		return pole == null || (!pole.isRemoved() && player.squaredDistanceTo(pos.toCenterPos()) < 64 * 64);
	}
}
