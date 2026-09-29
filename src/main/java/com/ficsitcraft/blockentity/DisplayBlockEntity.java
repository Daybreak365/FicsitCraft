package com.ficsitcraft.blockentity;

import com.ficsitcraft.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Data-less block entity that only exists so the multi-block HUB / Craft Bench can be rendered. */
public class DisplayBlockEntity extends BlockEntity {
	public DisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DISPLAY, pos, state);
	}
}
