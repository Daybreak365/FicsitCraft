package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
	public static final ItemGroup BUILDINGS = Registry.register(Registries.ITEM_GROUP, FicsitCraft.id("buildings"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModBlocks.CONSTRUCTOR))
					.displayName(Text.translatable("itemGroup.ficsitcraft.buildings"))
					.entries((ctx, entries) -> {
						entries.add(ModItems.BUILD_GUN);
						entries.add(ModItems.ZIPLINE);
						for (Block b : ModBlocks.ALL) entries.add(b);
						for (Item i : ModItems.TOOLS) entries.add(i);
					})
					.build());

	public static final ItemGroup PARTS = Registry.register(Registries.ITEM_GROUP, FicsitCraft.id("parts"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModItems.REINFORCED_IRON_PLATE))
					.displayName(Text.translatable("itemGroup.ficsitcraft.parts"))
					.entries((ctx, entries) -> {
						for (Item i : ModItems.PARTS) entries.add(i);
					})
					.build());

	private ModItemGroups() {
	}

	public static void init() {
	}
}
