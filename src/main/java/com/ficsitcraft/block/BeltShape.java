package com.ficsitcraft.block;

import net.minecraft.util.StringIdentifiable;

/** Rail-like automatic shapes of a horizontal conveyor belt. */
public enum BeltShape implements StringIdentifiable {
	/** input from the back */
	STRAIGHT("straight"),
	/** input from the left side (relative to the flow), curving towards the front */
	TURN_LEFT("turn_left"),
	/** input from the right side */
	TURN_RIGHT("turn_right"),
	/** ramp rising towards the front: output goes one block up */
	ASCENDING("ascending"),
	/** ramp falling towards the front: input comes from one block up at the back */
	DESCENDING("descending");

	private final String name;

	BeltShape(String name) {
		this.name = name;
	}

	@Override
	public String asString() {
		return name;
	}

	public boolean isRamp() {
		return this == ASCENDING || this == DESCENDING;
	}

	public boolean isTurn() {
		return this == TURN_LEFT || this == TURN_RIGHT;
	}
}
