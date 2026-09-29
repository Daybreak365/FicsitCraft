package com.ficsitcraft.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny ingredient: either a single item or an item tag. Tags are resolved lazily every time,
 * so datapack reloads are always respected.
 */
public final class Ing {
	private final Item item;
	private final TagKey<Item> tag;
	private final String labelKey;

	private Ing(Item item, TagKey<Item> tag, String labelKey) {
		this.item = item;
		this.tag = tag;
		this.labelKey = labelKey;
	}

	public static Ing of(ItemConvertible item) {
		return new Ing(item.asItem(), null, null);
	}

	public static Ing tag(TagKey<Item> tag, String labelKey) {
		return new Ing(null, tag, labelKey);
	}

	public boolean test(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return item != null ? stack.isOf(item) : stack.isIn(tag);
	}

	public List<ItemStack> displayStacks() {
		List<ItemStack> list = new ArrayList<>();
		if (item != null) {
			list.add(new ItemStack(item));
		} else {
			for (RegistryEntry<Item> e : Registries.ITEM.iterateEntries(tag)) list.add(new ItemStack(e.value()));
		}
		return list;
	}

	/** A stack that cycles through all matching items once per second (for GUIs). */
	public ItemStack displayStack(long time) {
		List<ItemStack> list = displayStacks();
		if (list.isEmpty()) return ItemStack.EMPTY;
		return list.get((int) ((time / 20) % list.size()));
	}

	public Text name() {
		if (item != null) return item.getName();
		return Text.translatable(labelKey);
	}

	public Item item() {
		return item;
	}
}
