package com.ficsitcraft.multiblock;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.block.MinerBlock;
import com.ficsitcraft.block.ResourceNodeBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Registry of multi-block building footprints plus shared placement / lookup helpers. */
public final class Multiblocks {
	private static final Map<Block, Footprint> FOOTPRINTS = new HashMap<>();

	private Multiblocks() {
	}

	public static void register(Block block, Footprint footprint) {
		FOOTPRINTS.put(block, footprint);
	}

	@Nullable
	public static Footprint get(Block block) {
		return FOOTPRINTS.get(block);
	}

	/** Where a building's controller pushes its output items. */
	public static BlockPos outputTarget(BlockState state, BlockPos pos, Direction facing) {
		Footprint fp = get(state.getBlock());
		return fp == null ? pos.offset(facing) : fp.outputTarget(pos, facing);
	}

	/** Power line attachment point: centre of the roof. */
	public static Vec3d connectorOffset(BlockState state) {
		Footprint fp = get(state.getBlock());
		if (fp == null || !state.contains(MachineBlock.FACING)) return new Vec3d(0.5, 1.0, 0.5);
		return fp.localPoint(state.get(MachineBlock.FACING), fp.width() / 2.0, fp.height() + 0.25, fp.depth() / 2.0);
	}

	/** Result of a placement check. */
	public enum Check {
		OK, BLOCKED, NEEDS_NODE, NEEDS_WATER
	}

	/** Can the building be placed with its controller at {@code pos}? (shared by server placement and the hologram) */
	public static Check canPlace(World world, Block block, BlockPos pos, Direction facing) {
		Footprint fp = get(block);
		if (block instanceof MinerBlock && !(world.getBlockState(pos.down()).getBlock() instanceof ResourceNodeBlock)) {
			return Check.NEEDS_NODE;
		}
		if (fp == null) return Check.OK;
		for (BlockPos p : fp.positions(pos, facing)) {
			if (world.isOutOfHeightLimit(p)) return Check.BLOCKED;
			BlockState s = world.getBlockState(p);
			if (!s.isReplaceable()) return Check.BLOCKED;
		}
		if (block instanceof com.ficsitcraft.block.WaterExtractorBlock
				&& com.ficsitcraft.block.WaterExtractorBlock.findSource(world, block, pos, facing) == com.ficsitcraft.fluid.SfFluid.NONE) {
			return Check.NEEDS_WATER;
		}
		return Check.OK;
	}

	/** Resolves any part of a multi-block building (or tall pole) to the position of its controller. */
	public static BlockPos resolveController(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof MachinePartBlock) return MachinePartBlock.controllerPos(state, pos);
		if (state.getBlock() instanceof com.ficsitcraft.block.PowerPoleBlock) {
			return com.ficsitcraft.block.PowerPoleBlock.basePos(state, pos);
		}
		return pos;
	}
}
