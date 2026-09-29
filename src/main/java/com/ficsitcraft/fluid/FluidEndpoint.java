package com.ficsitcraft.fluid;

/**
 * A building that pipelines can connect to (any block of a multi-block building counts).
 * Producers push fluid themselves; consumers are fed by the pipe network through {@link #offerFluid}.
 */
public interface FluidEndpoint {
	/** Whether pipelines should visually connect to this building. */
	default boolean acceptsPipes() {
		return true;
	}

	/**
	 * Offers fluid from a pipe. Returns the amount (m³) actually accepted.
	 */
	default double offerFluid(SfFluid fluid, double amount) {
		return 0;
	}
}
