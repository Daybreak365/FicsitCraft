package com.ficsitcraft.power;

/** Result of solving one power grid for one tick. */
public record GridStats(double capacity, double demand, double maxDemand, boolean fuseBlown, boolean ok, double load,
						int generators, int consumers, int nodes) {
	public static final GridStats EMPTY = new GridStats(0, 0, 0, false, false, 0, 0, 0, 0);
}
