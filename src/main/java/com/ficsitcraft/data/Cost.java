package com.ficsitcraft.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;

/** An exact item cost (milestones and buildings). */
public record Cost(Item item, int count) {
	public static Cost of(int count, ItemConvertible item) {
		return new Cost(item.asItem(), count);
	}
}
