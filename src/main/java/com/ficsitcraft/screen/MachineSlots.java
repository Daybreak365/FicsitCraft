package com.ficsitcraft.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MachineSlots {
	private MachineSlots() {
	}

	/** Adds the 27 + 9 player slots at the given top-left position. */
	public static void addPlayerInventory(SlotAdder adder, PlayerInventory inv, int x, int y) {
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				adder.add(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
		for (int col = 0; col < 9; col++)
			adder.add(new Slot(inv, col, x + col * 18, y + 58));
	}

	@FunctionalInterface
	public interface SlotAdder {
		Slot add(Slot slot);
	}

	/** A slot that only accepts items passing a filter and can be hidden. */
	public static class FilteredSlot extends Slot {
		private final Predicate<ItemStack> filter;
		private final BooleanSupplier enabled;

		public FilteredSlot(Inventory inventory, int index, int x, int y, Predicate<ItemStack> filter, BooleanSupplier enabled) {
			super(inventory, index, x, y);
			this.filter = filter;
			this.enabled = enabled;
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return filter.test(stack);
		}

		@Override
		public boolean isEnabled() {
			return enabled.getAsBoolean();
		}
	}

	/** Output-only slot. */
	public static class OutputSlot extends Slot {
		public OutputSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}
	}

	/** Shift-click helper: machine slots [0, machineSlots) and the player inventory after them. */
	public static ItemStack quickMove(ScreenHandler handler, PlayerEntity player, int machineSlots, int index, Mover mover) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = handler.slots.get(index);
		if (slot.hasStack()) {
			ItemStack stack = slot.getStack();
			result = stack.copy();
			if (index < machineSlots) {
				if (!mover.insert(stack, machineSlots, handler.slots.size(), true)) return ItemStack.EMPTY;
			} else {
				if (!mover.insert(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
			else slot.markDirty();
			if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
			slot.onTakeItem(player, stack);
		}
		return result;
	}

	@FunctionalInterface
	public interface Mover {
		boolean insert(ItemStack stack, int start, int end, boolean fromLast);
	}
}
