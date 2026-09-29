package com.ficsitcraft.train;

/** The kinds of rolling stock. Lengths are in blocks (the coupler gap is added by {@link Train#GAP}). */
public enum VehicleType {
	LOCOMOTIVE("locomotive", 9.0, 100, 0, true),
	FREIGHT_CAR("freight_car", 8.0, 30, 40, false),
	FLUID_CAR("fluid_freight_car", 8.0, 30, 40, false);

	public final String id;
	public final double length;
	public final double emptyMass;
	public final double cargoMass;
	public final boolean locomotive;

	VehicleType(String id, double length, double emptyMass, double cargoMass, boolean locomotive) {
		this.id = id;
		this.length = length;
		this.emptyMass = emptyMass;
		this.cargoMass = cargoMass;
		this.locomotive = locomotive;
	}
}
