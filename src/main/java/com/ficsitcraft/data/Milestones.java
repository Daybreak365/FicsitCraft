package com.ficsitcraft.data;

import java.util.ArrayList;
import java.util.List;

import static com.ficsitcraft.registry.ModItems.*;

/**
 * HUB milestones. Tier 0 = HUB upgrades, Tier 1-2 unlock after HUB Upgrade 6,
 * Tier 3-4 unlock after Space Elevator "Project Assembly" Phase 1.
 */
public final class Milestones {
	public static final int HUB_1 = 0;
	public static final int HUB_2 = 1;
	public static final int HUB_3 = 2;
	public static final int HUB_4 = 3;
	public static final int HUB_5 = 4;
	public static final int HUB_6 = 5;
	public static final int BASE_BUILDING = 6;
	public static final int LOGISTICS = 7;
	public static final int PART_ASSEMBLY = 8;
	public static final int LOGISTICS_MK2 = 9;
	public static final int PROJECT_PHASE_1 = 10;
	public static final int COAL_POWER = 11;
	public static final int BASIC_STEEL = 12;
	public static final int ADVANCED_STEEL = 13;
	public static final int IMPROVED_LOGISTICS = 14;
	public static final int CATERIUM_QUARTZ = 15;
	public static final int PROJECT_PHASE_2 = 16;
	public static final int RAILWAY_TECH = 17;
	public static final int TRAIN_LOGISTICS = 18;

	public static final int MAX_TIER = 4;
	public static final List<Milestone> ALL = new ArrayList<>();

	private Milestones() {
	}

	public static Milestone get(int i) {
		return i >= 0 && i < ALL.size() ? ALL.get(i) : null;
	}

	private static void add(int expectedIndex, String id, int tier, Cost... cost) {
		if (ALL.size() != expectedIndex) throw new IllegalStateException("Milestone index mismatch for " + id);
		ALL.add(new Milestone(expectedIndex, id, tier, List.of(cost)));
	}

	public static void init() {
		if (!ALL.isEmpty()) return;
		add(HUB_1, "hub_upgrade_1", 0, Cost.of(10, IRON_ROD));
		add(HUB_2, "hub_upgrade_2", 0, Cost.of(20, IRON_ROD), Cost.of(10, IRON_PLATE));
		add(HUB_3, "hub_upgrade_3", 0, Cost.of(20, IRON_PLATE), Cost.of(20, IRON_ROD), Cost.of(20, WIRE));
		add(HUB_4, "hub_upgrade_4", 0, Cost.of(75, IRON_PLATE), Cost.of(20, CABLE), Cost.of(10, CONCRETE));
		add(HUB_5, "hub_upgrade_5", 0, Cost.of(75, IRON_ROD), Cost.of(50, CABLE), Cost.of(20, CONCRETE));
		add(HUB_6, "hub_upgrade_6", 0, Cost.of(100, IRON_ROD), Cost.of(100, IRON_PLATE), Cost.of(100, WIRE), Cost.of(50, CONCRETE));

		add(BASE_BUILDING, "base_building", 1, Cost.of(100, CONCRETE), Cost.of(100, IRON_PLATE));
		add(LOGISTICS, "logistics", 1, Cost.of(150, IRON_PLATE), Cost.of(150, IRON_ROD), Cost.of(300, SCREW));

		add(PART_ASSEMBLY, "part_assembly", 2, Cost.of(50, REINFORCED_IRON_PLATE), Cost.of(200, CABLE), Cost.of(200, SCREW));
		add(LOGISTICS_MK2, "logistics_mk2", 2, Cost.of(50, REINFORCED_IRON_PLATE), Cost.of(50, ROTOR), Cost.of(200, CONCRETE));
		add(PROJECT_PHASE_1, "project_phase_1", 2, Cost.of(50, SMART_PLATING));

		add(COAL_POWER, "coal_power", 3, Cost.of(150, REINFORCED_IRON_PLATE), Cost.of(50, ROTOR), Cost.of(300, CABLE));
		add(BASIC_STEEL, "basic_steel", 3, Cost.of(50, ROTOR), Cost.of(200, WIRE), Cost.of(20, MODULAR_FRAME));

		add(ADVANCED_STEEL, "advanced_steel", 4, Cost.of(100, STEEL_PIPE), Cost.of(100, STEEL_BEAM), Cost.of(20, VERSATILE_FRAMEWORK));
		add(IMPROVED_LOGISTICS, "improved_logistics", 4, Cost.of(200, STEEL_BEAM), Cost.of(100, STEEL_PIPE), Cost.of(50, MODULAR_FRAME));
		add(CATERIUM_QUARTZ, "caterium_quartz", 4, Cost.of(100, STEEL_BEAM), Cost.of(200, CONCRETE), Cost.of(50, ROTOR));
		add(PROJECT_PHASE_2, "project_phase_2", 4, Cost.of(1000, SMART_PLATING), Cost.of(1000, VERSATILE_FRAMEWORK), Cost.of(100, AUTOMATED_WIRING));
		add(RAILWAY_TECH, "railway_tech", 4, Cost.of(200, STEEL_BEAM), Cost.of(100, MOTOR), Cost.of(100, ENCASED_INDUSTRIAL_BEAM));
		add(TRAIN_LOGISTICS, "train_logistics", 4, Cost.of(100, HEAVY_MODULAR_FRAME), Cost.of(300, STEEL_PIPE), Cost.of(200, COPPER_SHEET));
	}

	// ---------------------------------------------------------------- progression rules (mask = completed milestones)

	public static boolean isDone(long mask, int milestone) {
		return milestone < 0 || (mask & (1L << milestone)) != 0;
	}

	public static boolean isTierAvailable(long mask, int tier) {
		if (tier <= 0) return true;
		if (tier <= 2) return isDone(mask, HUB_6);
		return isDone(mask, PROJECT_PHASE_1);
	}

	public static boolean isAvailable(long mask, Milestone m) {
		if (isDone(mask, m.index())) return false;
		if (!isTierAvailable(mask, m.tier())) return false;
		// HUB upgrades are sequential
		if (m.tier() == 0 && m.index() > 0) return isDone(mask, m.index() - 1);
		return true;
	}

	/** Required tier-unlock hint for GUIs. */
	public static String lockReasonKey(long mask, Milestone m) {
		if (m.tier() == 0) return "gui.ficsitcraft.hub.locked_previous";
		if (m.tier() <= 2) return "gui.ficsitcraft.hub.locked_hub6";
		return "gui.ficsitcraft.hub.locked_phase1";
	}
}
