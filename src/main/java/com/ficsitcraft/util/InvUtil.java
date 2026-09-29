package com.ficsitcraft.util;

import com.ficsitcraft.data.Cost;
import com.ficsitcraft.data.Stack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public final class InvUtil {
	private InvUtil() {
	}

	public static int count(PlayerInventory inv, Predicate<ItemStack> filter) {
		int n = 0;
		for (int i = 0; i < inv.main.size(); i++) {
			ItemStack s = inv.main.get(i);
			if (filter.test(s)) n += s.getCount();
		}
		ItemStack off = inv.offHand.get(0);
		if (filter.test(off)) n += off.getCount();
		return n;
	}

	public static int count(PlayerInventory inv, Item item) {
		return count(inv, s -> !s.isEmpty() && s.isOf(item));
	}

	public static void remove(PlayerInventory inv, Predicate<ItemStack> filter, int amount) {
		for (int i = 0; i < inv.main.size() && amount > 0; i++) {
			ItemStack s = inv.main.get(i);
			if (!filter.test(s)) continue;
			int take = Math.min(amount, s.getCount());
			s.decrement(take);
			amount -= take;
		}
		ItemStack off = inv.offHand.get(0);
		if (amount > 0 && filter.test(off)) off.decrement(Math.min(amount, off.getCount()));
		inv.markDirty();
	}

	public static boolean hasCosts(PlayerEntity player, List<Cost> costs, int multiplier) {
		if (player.isCreative()) return true;
		for (Cost c : costs) if (count(player.getInventory(), c.item()) < c.count() * multiplier) return false;
		return true;
	}

	public static void removeCosts(PlayerEntity player, List<Cost> costs, int multiplier) {
		if (player.isCreative()) return;
		for (Cost c : costs) remove(player.getInventory(), s -> !s.isEmpty() && s.isOf(c.item()), c.count() * multiplier);
	}

	public static boolean hasInputs(PlayerEntity player, List<Stack> inputs, int multiplier) {
		if (player.isCreative()) return true;
		for (Stack in : inputs) if (count(player.getInventory(), in.ing()::test) < in.count() * multiplier) return false;
		return true;
	}

	public static void removeInputs(PlayerEntity player, List<Stack> inputs, int multiplier) {
		if (player.isCreative()) return;
		for (Stack in : inputs) remove(player.getInventory(), in.ing()::test, in.count() * multiplier);
	}
}
