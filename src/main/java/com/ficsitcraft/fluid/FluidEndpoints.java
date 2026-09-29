package com.ficsitcraft.fluid;

import com.ficsitcraft.multiblock.Multiblocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public final class FluidEndpoints {
	private FluidEndpoints() {
	}

	/** The fluid building (controller) occupying {@code pos}, if any. */
	@Nullable
	public static FluidEndpoint find(World world, BlockPos pos) {
		BlockPos c = Multiblocks.resolveController(world, pos);
		return world.getBlockEntity(c) instanceof FluidEndpoint ep && ep.acceptsPipes() ? ep : null;
	}

	/** Same check usable from block-state updates (only needs block entity access). */
	public static boolean isEndpoint(WorldAccess world, BlockPos pos) {
		BlockPos c = pos;
		var state = world.getBlockState(pos);
		if (state.getBlock() instanceof com.ficsitcraft.multiblock.MachinePartBlock) {
			c = com.ficsitcraft.multiblock.MachinePartBlock.controllerPos(state, pos);
		}
		BlockEntity be = world.getBlockEntity(c);
		return be instanceof FluidEndpoint ep && ep.acceptsPipes();
	}
}
