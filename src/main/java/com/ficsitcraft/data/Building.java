package com.ficsitcraft.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

/** A building obtainable from the Build Gun. */
public record Building(int index, Item item, int amount, List<Cost> cost, int milestone) {
	public ItemStack stack() {
		return new ItemStack(item, amount);
	}
}
