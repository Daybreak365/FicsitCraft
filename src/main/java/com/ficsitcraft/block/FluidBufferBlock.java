package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.FluidBufferBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Fluid Buffer: a 400 m³ tank that stores fluid in a pipe network (pipes can attach on any side). */
public class FluidBufferBlock extends MachineBlock {
	public static final MapCodec<FluidBufferBlock> CODEC = createCodec(FluidBufferBlock::new);
	public static final double CAPACITY = 400;

	public FluidBufferBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FluidBufferBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) return validateTicker(type, ModBlockEntities.FLUID_BUFFER, FluidBufferBlockEntity::clientTick);
		return validateTicker(type, ModBlockEntities.FLUID_BUFFER, FluidBufferBlockEntity::serverTick);
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof FluidBufferBlockEntity be
				? (int) Math.floor(be.getAmount() / be.getCapacity() * 15) : 0;
	}
}
