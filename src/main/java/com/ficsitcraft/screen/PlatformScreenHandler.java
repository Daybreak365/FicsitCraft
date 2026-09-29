package com.ficsitcraft.screen;

import com.ficsitcraft.block.RailBuildingBlock;
import com.ficsitcraft.blockentity.RailBuildingBlockEntity;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * GUI of the Freight Platform (36 item slots) and the Fluid Freight Platform (tank gauge): load / unload switch and a live
 * status line. The fluid platform has no slots, the freight platform shows its inventory and the player inventory.
 */
public class PlatformScreenHandler extends ScreenHandler {
	public static final int BUTTON_LOAD = 0, BUTTON_UNLOAD = 1;
	public static final int P_UNLOAD = 0, P_STATUS = 1, P_TANK = 2, P_FLUID = 3, P_CAR_PCT = 4, P_CAR_AMOUNT = 5, P_CAR_FLUID = 6,
			P_POWERED = 7, COUNT = 8;
	public static final int SLOT_X = 8, SLOT_Y = 58, INV_Y = 164;
	public static final int SLOTS = 36;

	private final PropertyDelegate props;
	@Nullable
	private final RailBuildingBlockEntity platform;
	private final BlockPos pos;
	private final boolean fluid;

	/** Client constructor: the kind of platform is read from the block at {@code pos}. */
	public PlatformScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
		this(syncId, inv, null, new ArrayPropertyDelegate(COUNT), pos,
				inv.player.getWorld().getBlockState(pos).getBlock() instanceof RailBuildingBlock b && b.getKind() == RailBuildingBlock.Kind.FLUID);
	}

	/** Server constructor. */
	public PlatformScreenHandler(int syncId, PlayerInventory inv, RailBuildingBlockEntity be, PropertyDelegate props) {
		this(syncId, inv, be, props, be.getPos(), be.getKind() == RailBuildingBlock.Kind.FLUID);
	}

	private PlatformScreenHandler(int syncId, PlayerInventory inv, @Nullable RailBuildingBlockEntity be, PropertyDelegate props,
								  BlockPos pos, boolean fluid) {
		super(ModScreenHandlers.PLATFORM, syncId);
		this.props = props;
		this.platform = be;
		this.pos = pos;
		this.fluid = fluid;
		if (!fluid) {
			Inventory inventory = be != null ? be : new SimpleInventory(SLOTS);
			for (int row = 0; row < 4; row++)
				for (int col = 0; col < 9; col++)
					addSlot(new Slot(inventory, col + row * 9, SLOT_X + col * 18, SLOT_Y + row * 18));
			MachineSlots.addPlayerInventory(this::addSlot, inv, SLOT_X, INV_Y);
		}
		addProperties(props);
	}

	public boolean isFluid() {
		return fluid;
	}

	public int get(int i) {
		return props.get(i);
	}

	public BlockPos getPos() {
		return pos;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (platform == null || (id != BUTTON_LOAD && id != BUTTON_UNLOAD)) return false;
		platform.setUnload(id == BUTTON_UNLOAD);
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		if (fluid) return ItemStack.EMPTY;
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();
			boolean moved = index < SLOTS ? insertItem(stack, SLOTS, slots.size(), true) : insertItem(stack, 0, SLOTS, false);
			if (!moved) return ItemStack.EMPTY;
			if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
			else slot.markDirty();
		}
		return result;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return platform == null || (!platform.isRemoved() && player.squaredDistanceTo(pos.toCenterPos()) < 12 * 12);
	}
}
