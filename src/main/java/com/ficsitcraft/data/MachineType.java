package com.ficsitcraft.data;

public enum MachineType {
	CRAFT_BENCH(4, 0),
	SMELTER(1, 4),
	FOUNDRY(2, 16),
	CONSTRUCTOR(1, 4),
	ASSEMBLER(2, 15),
	MANUFACTURER(4, 55);

	/** Number of input slots the machine exposes. */
	public final int inputs;
	/** Power consumption in MW while producing. */
	public final double powerMW;

	MachineType(int inputs, double powerMW) {
		this.inputs = inputs;
		this.powerMW = powerMW;
	}
}
