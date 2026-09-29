package com.ficsitcraft.screen;

import com.ficsitcraft.progress.MaskDelegate;
import com.ficsitcraft.progress.ProgressState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;

/**
 * Base for GUIs that show a clickable list of unlocked entries and the player inventory
 * (Craft Bench and Build Gun). Button id = entryIndex * 2 + (shift ? 1 : 0).
 */
public abstract class ListScreenHandler extends ScreenHandler {
	public static final int INV_X = 35;
	public static final int INV_Y = 140;

	protected final PropertyDelegate mask;

	protected ListScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory inv) {
		super(type, syncId);
		MinecraftServer server = inv.player.getServer();
		if (inv.player.getWorld().isClient || server == null) {
			mask = MaskDelegate.client();
		} else {
			ProgressState ps = ProgressState.get(server);
			mask = MaskDelegate.server(ps::mask);
		}
		MachineSlots.addPlayerInventory(this::addSlot, inv, INV_X, INV_Y);
		addProperties(mask);
	}

	public long getMask() {
		return MaskDelegate.read(mask);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id < 0) return false;
		int index = id / 2;
		int amount = (id & 1) == 1 ? 5 : 1;
		return onEntryClicked(player, index, amount);
	}

	protected abstract boolean onEntryClicked(PlayerEntity player, int index, int amount);

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		// shift-click moves between main inventory and hotbar
		ItemStack result = ItemStack.EMPTY;
		var slot = slots.get(index);
		if (slot.hasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();
			boolean moved = index < 27 ? insertItem(stack, 27, 36, false) : insertItem(stack, 0, 27, false);
			if (!moved) return ItemStack.EMPTY;
			if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
			else slot.markDirty();
		}
		return result;
	}
}
