package com.ficsitcraft.worldgen;

import com.ficsitcraft.block.NodeType;
import com.ficsitcraft.block.Purity;
import com.ficsitcraft.block.ResourceNodeBlock;
import com.ficsitcraft.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/** Places a small cluster (1-3) of resource nodes of the same type on the surface. */
public class ResourceNodeFeature extends Feature<DefaultFeatureConfig> {
	public ResourceNodeFeature() {
		super(DefaultFeatureConfig.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		NodeType type = NodeType.random(random);
		ResourceNodeBlock block = ModBlocks.NODES.get(type);
		int count = 1 + random.nextInt(3);
		boolean placed = false;
		for (int i = 0; i < count * 3 && count > 0; i++) {
			BlockPos p = i == 0 ? origin : origin.add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
			p = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
			BlockPos ground = p.down();
			BlockState g = world.getBlockState(ground);
			if (!world.getFluidState(p).isEmpty() || !world.getFluidState(ground).isEmpty()) continue;
			if (!g.isSolidBlock(world, ground) || g.getBlock() instanceof ResourceNodeBlock) continue;
			world.setBlockState(ground, block.getDefaultState().with(ResourceNodeBlock.PURITY, Purity.random(random)), Block.NOTIFY_LISTENERS);
			BlockState above = world.getBlockState(p);
			if (!above.isAir() && above.isReplaceable()) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			placed = true;
			count--;
		}
		return placed;
	}
}
