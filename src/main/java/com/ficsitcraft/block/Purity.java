package com.ficsitcraft.block;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.random.Random;

public enum Purity implements StringIdentifiable {
	IMPURE("impure", 0.5, 1),
	NORMAL("normal", 1.0, 2),
	PURE("pure", 2.0, 3);

	private final String name;
	/** Miner output multiplier (Satisfactory: 30 / 60 / 120 per minute for a Miner Mk.1). */
	public final double multiplier;
	/** Items gained per hand-mining click. */
	public final int handYield;

	Purity(String name, double multiplier, int handYield) {
		this.name = name;
		this.multiplier = multiplier;
		this.handYield = handYield;
	}

	@Override
	public String asString() {
		return name;
	}

	public static Purity random(Random r) {
		int roll = r.nextInt(100);
		if (roll < 35) return IMPURE;
		if (roll < 85) return NORMAL;
		return PURE;
	}
}
