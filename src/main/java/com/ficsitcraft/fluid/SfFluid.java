package com.ficsitcraft.fluid;

import net.minecraft.text.Text;

/** Fluids that can travel through FICSIT pipelines. Amounts are in m³. */
public enum SfFluid {
	NONE("none", 0x000000),
	WATER("water", 0x3A8CFF),
	LAVA("lava", 0xFF6A10);

	public final String id;
	/** Colour of the fluid as seen through the pipe windows. */
	public final int color;

	SfFluid(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public Text displayName() {
		return Text.translatable("fluid.ficsitcraft." + id);
	}

	public static SfFluid byOrdinal(int i) {
		return i >= 0 && i < values().length ? values()[i] : NONE;
	}

	public static SfFluid byId(String id) {
		for (SfFluid f : values()) if (f.id.equals(id)) return f;
		return NONE;
	}
}
