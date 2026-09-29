package com.ficsitcraft.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * A Satisfactory production recipe.
 *
 * @param index     global index in {@link Recipes#ALL}
 * @param id        stable string id (used for translation keys and saving)
 * @param machine   machine that runs the recipe automatically (null = craft bench only)
 * @param handcraft true if the recipe can also be crafted at the Craft Bench
 * @param inputs    ordered inputs (slot i of the machine = input i)
 * @param output    output item
 * @param outCount  output amount per cycle
 * @param timeTicks cycle duration in ticks (20 ticks = 1 s)
 * @param milestone index of the milestone that unlocks it (-1 = available from the start)
 */
public record SfRecipe(int index, String id, MachineType machine, boolean handcraft, List<Stack> inputs,
					   Item output, int outCount, int timeTicks, int milestone) {

	public ItemStack outputStack() {
		return new ItemStack(output, outCount);
	}

	/** Output per minute when running at 100%. */
	public double outputPerMinute() {
		return outCount * (1200.0 / timeTicks);
	}

	public double inputPerMinute(int i) {
		return inputs.get(i).count() * (1200.0 / timeTicks);
	}
}
