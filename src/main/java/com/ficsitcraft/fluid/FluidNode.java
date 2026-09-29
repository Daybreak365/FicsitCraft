package com.ficsitcraft.fluid;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** A pipe segment or an in-line pump taking part in the pipe network simulation. */
public interface FluidNode {
	BlockPos getNodePos();

	SfFluid getFluid();

	void setFluid(SfFluid fluid);

	double getAmount();

	void setAmount(double amount);

	/** m³ this node holds when full. */
	double getCapacity();

	/** Maximum flow through this node in m³ per tick. */
	double getMaxFlow();

	/** Can fluid pass between this node and the neighbour on {@code side}? */
	boolean connectsTo(Direction side);

	/** All block positions occupied by this node (tanks span several blocks). */
	default java.util.List<BlockPos> getCells() {
		return java.util.List.of(getNodePos());
	}

	/** Cell-aware connection test; single-block nodes just use {@link #connectsTo(Direction)}. */
	default boolean connectsAt(BlockPos cell, Direction side) {
		return connectsTo(side);
	}

	/** Height (blocks) of the fluid column when full: 1 for pipes, the tank height for fluid buffers. */
	default double getHeadHeight() {
		return 1.0;
	}

	/**
	 * For one-way nodes (pumps): may fluid move from this node towards {@code side}? / into this node from {@code side}?
	 */
	default boolean canOutput(Direction side) {
		return true;
	}

	default boolean canInput(Direction side) {
		return true;
	}

	/** Extra pressure head (absolute block height) this node imposes this tick, or NaN. */
	default double getSourceHead() {
		return Double.NaN;
	}

	/** Called by the manager after each solve with the net volume moved through this node (m³/tick). */
	default void onFlowSolved(double throughput) {
	}

	default boolean isNodeRemoved() {
		return false;
	}
}
