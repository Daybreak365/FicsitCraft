package com.ficsitcraft.train;

import java.util.UUID;

/** One locomotive or wagon of a train. */
public final class Vehicle {
	public final UUID id;
	public final VehicleType type;
	/** +1: the vehicle's front points the way the train's path runs (towards the head), -1: reversed. */
	public int facing = 1;
	/** Cargo fill 0..1 (only affects the mass). */
	public double load;
	/** Cargo payload managed by the game layer (item stacks / fluid tank). */
	public Object cargo;

	public Vehicle(UUID id, VehicleType type) {
		this.id = id;
		this.type = type;
	}

	public Vehicle(VehicleType type) {
		this(UUID.randomUUID(), type);
	}

	public double mass() {
		return type.emptyMass + type.cargoMass * Math.max(0, Math.min(1, load));
	}
}
