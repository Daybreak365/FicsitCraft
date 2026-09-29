package com.ficsitcraft.screen;

import com.ficsitcraft.block.PipeBlock;
import com.ficsitcraft.blockentity.PipeBlockEntity;
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

/** Satisfactory-style pipeline inspector: flow gauge, fill sphere, flow / max flow and the run's fluid volume. */
public class PipeScreenHandler extends ScreenHandler {
	public static final int P_FLOW = 0;        // m³/min x10
	public static final int P_MAX_FLOW = 1;    // m³/min
	public static final int P_AMOUNT = 2;      // m³ x10 (whole pipe run)
	public static final int P_CAPACITY = 3;    // m³ x10 (whole pipe run)
	public static final int P_FLUID = 4;
	public static final int P_FILL = 5;        // this block's fill x1000
	public static final int COUNT = 6;

	private final PropertyDelegate props;
	@Nullable
	private final PipeBlockEntity pipe;
	private final BlockPos pos;
	private final double[] section = new double[2];
	private int refresh;

	public PipeScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		super(ModScreenHandlers.PIPE, syncId);
		this.props = new ArrayPropertyDelegate(COUNT);
		this.pipe = null;
		this.pos = pos;
		addProperties(props);
	}

	public PipeScreenHandler(int syncId, PlayerInventory inv, PipeBlockEntity pipe) {
		super(ModScreenHandlers.PIPE, syncId);
		this.pipe = pipe;
		this.pos = pipe.getPos();
		this.props = new PropertyDelegate() {
			@Override
			public int get(int index) {
				return switch (index) {
					case P_FLOW -> clamp(pipe.getFlowPerMinute() * 10);
					case P_MAX_FLOW -> clamp(pipe.getMaxFlow() * 1200);
					case P_AMOUNT -> clamp(section[0] * 10);
					case P_CAPACITY -> clamp(section[1] * 10);
					case P_FLUID -> pipe.getFluid().ordinal();
					case P_FILL -> clamp(pipe.getAmount() / pipe.getCapacity() * 1000);
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
		updateSection();
		addProperties(props);
	}

	private static int clamp(double v) {
		return (int) Math.max(0, Math.min(32000, Math.round(v)));
	}

	private void updateSection() {
		if (pipe == null || pipe.getWorld() == null) return;
		double[] s = PipeBlockEntity.sectionVolume(pipe.getWorld(), pipe.getPos());
		section[0] = s[0];
		section[1] = s[1];
	}

	@Override
	public void sendContentUpdates() {
		if (pipe != null && ++refresh >= 5) {
			refresh = 0;
			updateSection();
		}
		super.sendContentUpdates();
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
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return pipe == null || (!pipe.isRemoved() && pipe.getCachedState().getBlock() instanceof PipeBlock
				&& player.squaredDistanceTo(pos.toCenterPos()) < 10 * 10);
	}
}
