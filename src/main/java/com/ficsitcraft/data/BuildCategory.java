package com.ficsitcraft.data;

/** Build Gun tab a building is listed under (lang key gui.ficsitcraft.cat.&lt;id&gt;). */
public enum BuildCategory {
	BASICS("basics"),
	PRODUCTION("production"),
	POWER("power"),
	LOGISTICS("logistics"),
	FLUIDS("fluids"),
	STRUCTURES("structures"),
	TRAINS("trains");

	public final String id;

	BuildCategory(String id) {
		this.id = id;
	}

	public String translationKey() {
		return "gui.ficsitcraft.cat." + id;
	}
}
