package com.ficsitcraft.registry;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.item.BuildGunItem;
import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.item.PartItem;
import com.ficsitcraft.item.RailSignalItem;
import com.ficsitcraft.item.RailwayItem;
import com.ficsitcraft.item.VehicleItem;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
	public static final List<Item> PARTS = new ArrayList<>();
	/** Items that are tools / buildings rather than parts (shown in the buildings tab). */
	public static final List<Item> TOOLS = new ArrayList<>();

	// Resources
	public static final Item LIMESTONE = part("limestone");
	public static final Item CATERIUM_ORE = part("caterium_ore");
	public static final Item RAW_QUARTZ = part("raw_quartz");

	// Ingots & basic
	public static final Item CATERIUM_INGOT = part("caterium_ingot");
	public static final Item STEEL_INGOT = part("steel_ingot");
	public static final Item CONCRETE = part("concrete");
	public static final Item IRON_PLATE = part("iron_plate");
	public static final Item IRON_ROD = part("iron_rod");
	public static final Item SCREW = part("screw");
	public static final Item WIRE = part("wire");
	public static final Item CABLE = register("cable", new CableItem(new Item.Settings()));
	public static final Item COPPER_SHEET = part("copper_sheet");
	public static final Item QUICKWIRE = part("quickwire");
	public static final Item QUARTZ_CRYSTAL = part("quartz_crystal");
	public static final Item SILICA = part("silica");
	public static final Item STEEL_BEAM = part("steel_beam");
	public static final Item STEEL_PIPE = part("steel_pipe");

	// Intermediate
	public static final Item REINFORCED_IRON_PLATE = part("reinforced_iron_plate");
	public static final Item ROTOR = part("rotor");
	public static final Item MODULAR_FRAME = part("modular_frame");
	public static final Item ENCASED_INDUSTRIAL_BEAM = part("encased_industrial_beam");
	public static final Item STATOR = part("stator");
	public static final Item MOTOR = part("motor");
	public static final Item HEAVY_MODULAR_FRAME = register("heavy_modular_frame", new PartItem(new Item.Settings().rarity(Rarity.UNCOMMON)));

	// Space Elevator parts
	public static final Item SMART_PLATING = register("smart_plating", new PartItem(new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item VERSATILE_FRAMEWORK = register("versatile_framework", new PartItem(new Item.Settings().rarity(Rarity.UNCOMMON)));
	public static final Item AUTOMATED_WIRING = register("automated_wiring", new PartItem(new Item.Settings().rarity(Rarity.UNCOMMON)));

	// Biomass
	public static final Item BIOMASS = part("biomass");
	public static final Item SOLID_BIOFUEL = part("solid_biofuel");

	// Tools
	public static final Item BUILD_GUN = register("build_gun", new BuildGunItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
	public static final Item ZIPLINE = register("zipline", new com.ficsitcraft.item.ZiplineItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	// Railway
	public static final Item RAILWAY = register("railway", new RailwayItem(new Item.Settings()));
	public static final Item LOCOMOTIVE = register("locomotive", new VehicleItem(VehicleType.LOCOMOTIVE, new Item.Settings().maxCount(4).rarity(Rarity.UNCOMMON)));
	public static final Item FREIGHT_CAR = register("freight_car", new VehicleItem(VehicleType.FREIGHT_CAR, new Item.Settings().maxCount(8)));
	public static final Item FLUID_FREIGHT_CAR = register("fluid_freight_car", new VehicleItem(VehicleType.FLUID_CAR, new Item.Settings().maxCount(8)));
	public static final Item RAIL_SIGNAL_BLOCK = register("rail_signal_block", new RailSignalItem(1, new Item.Settings()));
	public static final Item RAIL_SIGNAL_PATH = register("rail_signal_path", new RailSignalItem(2, new Item.Settings()));

	private ModItems() {
	}

	private static Item part(String name) {
		return register(name, new PartItem(new Item.Settings()));
	}

	private static Item register(String name, Item item) {
		Registry.register(Registries.ITEM, FicsitCraft.id(name), item);
		if (item instanceof RailwayItem || item instanceof VehicleItem || item instanceof RailSignalItem) TOOLS.add(item);
		else if (!(item instanceof BuildGunItem) && !(item instanceof com.ficsitcraft.item.ZiplineItem)) PARTS.add(item);
		return item;
	}

	public static void init() {
	}
}
