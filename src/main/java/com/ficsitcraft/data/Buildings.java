package com.ficsitcraft.data;

import net.minecraft.item.ItemConvertible;

import java.util.ArrayList;
import java.util.List;

import static com.ficsitcraft.registry.ModBlocks.*;
import static com.ficsitcraft.registry.ModItems.*;

public final class Buildings {
	public static final List<Building> ALL = new ArrayList<>();

	private Buildings() {
	}

	public static Building get(int i) {
		return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
	}

	public static List<Building> unlockedBy(int milestone) {
		List<Building> list = new ArrayList<>();
		for (Building b : ALL) if (b.milestone() == milestone) list.add(b);
		return list;
	}

	private static void add(ItemConvertible block, int amount, int milestone, Cost... cost) {
		ALL.add(new Building(ALL.size(), block.asItem(), amount, List.of(cost), milestone));
	}

	public static void init() {
		if (!ALL.isEmpty()) return;
		// Always available
		add(HUB, 1, -1, Cost.of(10, IRON_PLATE));
		add(CRAFT_BENCH, 1, -1, Cost.of(3, IRON_PLATE));
		// Tier 0
		add(STORAGE_CONTAINER, 1, Milestones.HUB_1, Cost.of(10, IRON_PLATE), Cost.of(10, IRON_ROD));
		add(SMELTER, 1, Milestones.HUB_2, Cost.of(5, IRON_ROD), Cost.of(8, WIRE));
		add(CONVEYOR_BELT_MK1, 2, Milestones.HUB_2, Cost.of(1, IRON_PLATE));
		add(CONSTRUCTOR, 1, Milestones.HUB_3, Cost.of(2, REINFORCED_IRON_PLATE), Cost.of(8, CABLE));
		add(BIOMASS_BURNER, 1, Milestones.HUB_3, Cost.of(15, IRON_PLATE), Cost.of(15, IRON_ROD), Cost.of(25, WIRE));
		add(POWER_POLE_MK1, 1, Milestones.HUB_3, Cost.of(3, WIRE), Cost.of(1, IRON_ROD), Cost.of(1, CONCRETE));
		add(MINER_MK1, 1, Milestones.HUB_4, Cost.of(10, IRON_PLATE), Cost.of(10, CONCRETE));
		// Tier 1
		add(FOUNDATION, 2, Milestones.BASE_BUILDING, Cost.of(1, CONCRETE));
		add(CONCRETE_WALL, 2, Milestones.BASE_BUILDING, Cost.of(1, CONCRETE));
		add(SPLITTER, 1, Milestones.LOGISTICS, Cost.of(2, IRON_PLATE), Cost.of(2, CABLE));
		add(MERGER, 1, Milestones.LOGISTICS, Cost.of(2, IRON_PLATE), Cost.of(2, IRON_ROD));
		// Tier 2
		add(ASSEMBLER, 1, Milestones.PART_ASSEMBLY, Cost.of(8, REINFORCED_IRON_PLATE), Cost.of(4, ROTOR), Cost.of(10, CABLE));
		add(CONVEYOR_BELT_MK2, 2, Milestones.LOGISTICS_MK2, Cost.of(1, REINFORCED_IRON_PLATE));
		add(POWER_POLE_MK2, 1, Milestones.LOGISTICS_MK2, Cost.of(2, REINFORCED_IRON_PLATE), Cost.of(6, CABLE), Cost.of(2, CONCRETE));
		// Tier 3
		add(PIPELINE_MK1, 4, Milestones.COAL_POWER, Cost.of(1, COPPER_SHEET));
		add(WATER_EXTRACTOR, 1, Milestones.COAL_POWER, Cost.of(20, COPPER_SHEET), Cost.of(10, REINFORCED_IRON_PLATE), Cost.of(10, ROTOR));
		add(PIPELINE_PUMP, 1, Milestones.COAL_POWER, Cost.of(2, COPPER_SHEET), Cost.of(2, ROTOR));
		add(FLUID_BUFFER, 1, Milestones.COAL_POWER, Cost.of(10, COPPER_SHEET), Cost.of(10, REINFORCED_IRON_PLATE), Cost.of(20, CONCRETE));
		add(COAL_GENERATOR, 1, Milestones.COAL_POWER, Cost.of(20, REINFORCED_IRON_PLATE), Cost.of(10, ROTOR), Cost.of(30, CABLE));
		add(FOUNDRY, 1, Milestones.BASIC_STEEL, Cost.of(10, MODULAR_FRAME), Cost.of(10, ROTOR), Cost.of(20, CONCRETE));
		// Tier 4
		add(MANUFACTURER, 1, Milestones.ADVANCED_STEEL, Cost.of(20, MOTOR), Cost.of(64, CABLE), Cost.of(20, ENCASED_INDUSTRIAL_BEAM));
		add(MINER_MK2, 1, Milestones.IMPROVED_LOGISTICS, Cost.of(8, ENCASED_INDUSTRIAL_BEAM), Cost.of(20, STEEL_PIPE), Cost.of(10, MODULAR_FRAME));
		add(CONVEYOR_BELT_MK3, 2, Milestones.IMPROVED_LOGISTICS, Cost.of(1, STEEL_BEAM));
		add(PIPELINE_MK2, 4, Milestones.IMPROVED_LOGISTICS, Cost.of(1, STEEL_PIPE), Cost.of(1, COPPER_SHEET));
		add(POWER_POLE_MK3, 1, Milestones.IMPROVED_LOGISTICS, Cost.of(2, STEEL_PIPE), Cost.of(8, CABLE), Cost.of(2, CONCRETE));
		// Railway (Tier 4)
		add(RAILWAY, 4, Milestones.RAILWAY_TECH, Cost.of(1, STEEL_BEAM), Cost.of(1, CONCRETE));
		add(TRAIN_STATION, 1, Milestones.RAILWAY_TECH, Cost.of(6, HEAVY_MODULAR_FRAME), Cost.of(20, CABLE), Cost.of(30, CONCRETE));
		add(EMPTY_PLATFORM, 1, Milestones.RAILWAY_TECH, Cost.of(12, STEEL_BEAM), Cost.of(12, CONCRETE));
		add(LOCOMOTIVE, 1, Milestones.RAILWAY_TECH, Cost.of(4, HEAVY_MODULAR_FRAME), Cost.of(8, MOTOR), Cost.of(20, STEEL_BEAM));
		add(FREIGHT_CAR, 1, Milestones.RAILWAY_TECH, Cost.of(8, MODULAR_FRAME), Cost.of(12, STEEL_BEAM), Cost.of(12, STEEL_PIPE));
		add(RAIL_SIGNAL_BLOCK, 1, Milestones.RAILWAY_TECH, Cost.of(4, STEEL_PIPE), Cost.of(4, CABLE));
		add(FREIGHT_PLATFORM, 1, Milestones.TRAIN_LOGISTICS, Cost.of(2, HEAVY_MODULAR_FRAME), Cost.of(16, STEEL_BEAM), Cost.of(10, CABLE));
		add(FLUID_FREIGHT_PLATFORM, 1, Milestones.TRAIN_LOGISTICS, Cost.of(2, HEAVY_MODULAR_FRAME), Cost.of(20, STEEL_PIPE), Cost.of(20, COPPER_SHEET));
		add(FLUID_FREIGHT_CAR, 1, Milestones.TRAIN_LOGISTICS, Cost.of(8, MODULAR_FRAME), Cost.of(20, STEEL_PIPE), Cost.of(20, COPPER_SHEET));
		add(RAIL_SIGNAL_PATH, 1, Milestones.TRAIN_LOGISTICS, Cost.of(4, STEEL_PIPE), Cost.of(1, MOTOR), Cost.of(8, CABLE));
	}
}
