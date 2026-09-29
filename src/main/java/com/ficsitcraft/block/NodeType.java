package com.ficsitcraft.block;

import com.ficsitcraft.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;

import java.util.function.Supplier;

public enum NodeType {
	IRON("iron", () -> Items.RAW_IRON, 30),
	COPPER("copper", () -> Items.RAW_COPPER, 20),
	LIMESTONE("limestone", () -> ModItems.LIMESTONE, 24),
	COAL("coal", () -> Items.COAL, 14),
	CATERIUM("caterium", () -> ModItems.CATERIUM_ORE, 6),
	QUARTZ("quartz", () -> ModItems.RAW_QUARTZ, 6);

	public final String id;
	private final Supplier<Item> resource;
	public final int weight;

	NodeType(String id, Supplier<Item> resource, int weight) {
		this.id = id;
		this.resource = resource;
		this.weight = weight;
	}

	public Item resource() {
		return resource.get();
	}

	public static NodeType random(Random r) {
		int total = 0;
		for (NodeType t : values()) total += t.weight;
		int roll = r.nextInt(total);
		for (NodeType t : values()) {
			roll -= t.weight;
			if (roll < 0) return t;
		}
		return IRON;
	}
}
