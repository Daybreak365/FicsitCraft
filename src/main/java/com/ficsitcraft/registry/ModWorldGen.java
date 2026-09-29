package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.worldgen.ResourceNodeFeature;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModWorldGen {
	public static final ResourceNodeFeature RESOURCE_NODE = Registry.register(Registries.FEATURE, FicsitCraft.id("resource_node"), new ResourceNodeFeature());
	public static final RegistryKey<PlacedFeature> RESOURCE_NODES_PLACED = RegistryKey.of(RegistryKeys.PLACED_FEATURE, FicsitCraft.id("resource_nodes"));

	private ModWorldGen() {
	}

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.TOP_LAYER_MODIFICATION, RESOURCE_NODES_PLACED);
	}
}
