package com.ficsitcraft.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.ficsitcraft.data.MachineType.*;
import static com.ficsitcraft.registry.ModItems.*;

/**
 * All production recipes. Numbers follow Satisfactory 1.0 (amounts per cycle, cycle time in seconds).
 * Vanilla raw iron / raw copper / coal / iron ingot / copper ingot are used as the base resources.
 */
public final class Recipes {
	public static final List<SfRecipe> ALL = new ArrayList<>();

	private Recipes() {
	}

	public static SfRecipe get(int index) {
		return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
	}

	public static List<SfRecipe> forMachine(MachineType type) {
		List<SfRecipe> list = new ArrayList<>();
		for (SfRecipe r : ALL) {
			if (type == CRAFT_BENCH ? r.handcraft() : r.machine() == type) list.add(r);
		}
		return list;
	}

	/** All recipes unlocked by the given milestone. */
	public static List<SfRecipe> unlockedBy(int milestone) {
		List<SfRecipe> list = new ArrayList<>();
		for (SfRecipe r : ALL) if (r.milestone() == milestone) list.add(r);
		return list;
	}

	// ---------------------------------------------------------------- builder

	private static final class B {
		final String id;
		MachineType machine;
		boolean hand;
		final List<Stack> in = new ArrayList<>();
		Item out;
		int outCount;
		double seconds;
		int milestone = -1;

		B(String id) {
			this.id = id;
		}

		B machine(MachineType m) {
			machine = m;
			return this;
		}

		B hand() {
			hand = true;
			return this;
		}

		B in(int count, ItemConvertible item) {
			in.add(new Stack(Ing.of(item), count));
			return this;
		}

		B in(int count, Ing ing) {
			in.add(new Stack(ing, count));
			return this;
		}

		B out(int count, ItemConvertible item) {
			out = item.asItem();
			outCount = count;
			return this;
		}

		B time(double s) {
			seconds = s;
			return this;
		}

		B ms(int m) {
			milestone = m;
			return this;
		}

		void add() {
			ALL.add(new SfRecipe(ALL.size(), id, machine, hand, Collections.unmodifiableList(in), out, outCount,
					(int) Math.round(seconds * 20), milestone));
		}
	}

	private static B r(String id) {
		return new B(id);
	}

	public static void init() {
		if (!ALL.isEmpty()) return;
		Ing leaves = Ing.tag(ItemTags.LEAVES, "ingredient.ficsitcraft.leaves");
		Ing wood = Ing.tag(ItemTags.LOGS, "ingredient.ficsitcraft.wood");

		// ---- Smelter
		r("iron_ingot").machine(SMELTER).in(1, Items.RAW_IRON).out(1, Items.IRON_INGOT).time(2).ms(Milestones.HUB_2).add();
		r("copper_ingot").machine(SMELTER).in(1, Items.RAW_COPPER).out(1, Items.COPPER_INGOT).time(2).ms(Milestones.HUB_2).add();
		r("caterium_ingot").machine(SMELTER).in(3, CATERIUM_ORE).out(1, CATERIUM_INGOT).time(4).ms(Milestones.CATERIUM_QUARTZ).add();

		// ---- Foundry
		r("steel_ingot").machine(FOUNDRY).in(3, Items.RAW_IRON).in(3, Items.COAL).out(3, STEEL_INGOT).time(4).ms(Milestones.BASIC_STEEL).add();

		// ---- Constructor
		r("iron_plate").machine(CONSTRUCTOR).hand().in(3, Items.IRON_INGOT).out(2, IRON_PLATE).time(6).add();
		r("iron_rod").machine(CONSTRUCTOR).hand().in(1, Items.IRON_INGOT).out(1, IRON_ROD).time(4).add();
		r("screw").machine(CONSTRUCTOR).hand().in(1, IRON_ROD).out(4, SCREW).time(6).ms(Milestones.HUB_1).add();
		r("wire").machine(CONSTRUCTOR).hand().in(1, Items.COPPER_INGOT).out(2, WIRE).time(4).ms(Milestones.HUB_2).add();
		r("cable").machine(CONSTRUCTOR).hand().in(2, WIRE).out(1, CABLE).time(2).ms(Milestones.HUB_2).add();
		r("concrete").machine(CONSTRUCTOR).hand().in(3, LIMESTONE).out(1, CONCRETE).time(4).ms(Milestones.HUB_3).add();
		r("biomass_leaves").machine(CONSTRUCTOR).hand().in(10, leaves).out(5, BIOMASS).time(5).ms(Milestones.HUB_3).add();
		r("biomass_wood").machine(CONSTRUCTOR).hand().in(4, wood).out(20, BIOMASS).time(4).ms(Milestones.HUB_3).add();
		r("solid_biofuel").machine(CONSTRUCTOR).hand().in(8, BIOMASS).out(4, SOLID_BIOFUEL).time(4).ms(Milestones.HUB_5).add();
		r("copper_sheet").machine(CONSTRUCTOR).hand().in(2, Items.COPPER_INGOT).out(1, COPPER_SHEET).time(6).ms(Milestones.PART_ASSEMBLY).add();
		r("steel_beam").machine(CONSTRUCTOR).hand().in(4, STEEL_INGOT).out(1, STEEL_BEAM).time(4).ms(Milestones.BASIC_STEEL).add();
		r("steel_pipe").machine(CONSTRUCTOR).hand().in(3, STEEL_INGOT).out(2, STEEL_PIPE).time(6).ms(Milestones.BASIC_STEEL).add();
		r("quickwire").machine(CONSTRUCTOR).hand().in(1, CATERIUM_INGOT).out(5, QUICKWIRE).time(5).ms(Milestones.CATERIUM_QUARTZ).add();
		r("quartz_crystal").machine(CONSTRUCTOR).hand().in(5, RAW_QUARTZ).out(3, QUARTZ_CRYSTAL).time(8).ms(Milestones.CATERIUM_QUARTZ).add();
		r("silica").machine(CONSTRUCTOR).hand().in(3, RAW_QUARTZ).out(5, SILICA).time(8).ms(Milestones.CATERIUM_QUARTZ).add();

		// ---- Assembler
		r("reinforced_iron_plate").machine(ASSEMBLER).hand().in(6, IRON_PLATE).in(12, SCREW).out(1, REINFORCED_IRON_PLATE).time(12).ms(Milestones.HUB_3).add();
		r("rotor").machine(ASSEMBLER).hand().in(5, IRON_ROD).in(25, SCREW).out(1, ROTOR).time(15).ms(Milestones.HUB_5).add();
		r("modular_frame").machine(ASSEMBLER).hand().in(3, REINFORCED_IRON_PLATE).in(12, IRON_ROD).out(2, MODULAR_FRAME).time(60).ms(Milestones.PART_ASSEMBLY).add();
		r("smart_plating").machine(ASSEMBLER).hand().in(1, REINFORCED_IRON_PLATE).in(1, ROTOR).out(1, SMART_PLATING).time(30).ms(Milestones.PART_ASSEMBLY).add();
		r("versatile_framework").machine(ASSEMBLER).hand().in(1, MODULAR_FRAME).in(12, STEEL_BEAM).out(2, VERSATILE_FRAMEWORK).time(24).ms(Milestones.BASIC_STEEL).add();
		r("encased_industrial_beam").machine(ASSEMBLER).hand().in(3, STEEL_BEAM).in(6, CONCRETE).out(1, ENCASED_INDUSTRIAL_BEAM).time(10).ms(Milestones.ADVANCED_STEEL).add();
		r("stator").machine(ASSEMBLER).hand().in(3, STEEL_PIPE).in(8, WIRE).out(1, STATOR).time(12).ms(Milestones.ADVANCED_STEEL).add();
		r("motor").machine(ASSEMBLER).hand().in(2, ROTOR).in(2, STATOR).out(1, MOTOR).time(12).ms(Milestones.ADVANCED_STEEL).add();
		r("automated_wiring").machine(ASSEMBLER).hand().in(1, STATOR).in(20, CABLE).out(1, AUTOMATED_WIRING).time(24).ms(Milestones.ADVANCED_STEEL).add();

		// ---- Equipment (Craft Bench only)
		r("zipline").hand().in(5, REINFORCED_IRON_PLATE).in(10, CABLE).in(2, ROTOR).out(1, ZIPLINE).time(1).ms(Milestones.LOGISTICS_MK2).add();

		// ---- Manufacturer
		// Satisfactory uses 120 screws; a Minecraft slot holds at most 64, so the recipe is scaled to 60 screws.
		r("heavy_modular_frame").machine(MANUFACTURER).hand().in(5, MODULAR_FRAME).in(20, STEEL_PIPE).in(5, ENCASED_INDUSTRIAL_BEAM)
				.in(60, SCREW).out(1, HEAVY_MODULAR_FRAME).time(30).ms(Milestones.ADVANCED_STEEL).add();
	}
}
