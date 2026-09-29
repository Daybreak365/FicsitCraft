package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.WaterExtractorBlockEntity;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.Multiblocks;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Water Extractor: placed over water (or lava), pumps 120 m³/min into connected pipelines with 10 blocks of head lift.
 * 20 MW.
 */
public class WaterExtractorBlock extends MachineBlock {
	public static final MapCodec<WaterExtractorBlock> CODEC = createCodec(WaterExtractorBlock::new);
	public static final double RATE_PER_MIN = 120;
	public static final double HEAD_LIFT = 10;
	public static final double POWER_MW = 20;

	public WaterExtractorBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	/** Looks for a still water (preferred) or lava source in the ground layer of the footprint or right below it. */
	public static SfFluid findSource(World world, Block block, BlockPos pos, Direction facing) {
		Footprint fp = Multiblocks.get(block);
		java.util.List<BlockPos> cells = fp == null ? java.util.List.of(pos) : fp.positions(pos, facing);
		boolean lava = false;
		for (BlockPos c : cells) {
			if (c.getY() != pos.getY()) continue;
			for (BlockPos p : new BlockPos[]{c, c.down()}) {
				FluidState fs = world.getFluidState(p);
				if (!fs.isStill()) continue;
				if (fs.isIn(FluidTags.WATER)) return SfFluid.WATER;
				if (fs.isIn(FluidTags.LAVA)) lava = true;
			}
		}
		return lava ? SfFluid.LAVA : SfFluid.NONE;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		// remember the source before the building's parts displace the water
		if (!world.isClient && world.getBlockEntity(pos) instanceof WaterExtractorBlockEntity be) {
			SfFluid src = findSource(world, this, pos, state.get(FACING));
			// the controller itself already replaced one water block; count it as water if nothing else is found
			be.setSource(src);
		}
		super.onPlaced(world, pos, state, placer, itemStack);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new WaterExtractorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.WATER_EXTRACTOR, WaterExtractorBlockEntity::tick);
	}
}
