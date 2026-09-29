package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.MinerBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Miner Mk.1 / Mk.2 - must be placed directly on top of a resource node. */
public class MinerBlock extends MachineBlock {
	private final int mk;

	public MinerBlock(int mk, Settings settings) {
		super(settings);
		this.mk = mk;
	}

	public int getMk() {
		return mk;
	}

	/** Items per minute on a NORMAL node. */
	public double baseRate() {
		return mk == 1 ? 60 : 120;
	}

	public double powerMW() {
		return mk == 1 ? 5 : 12;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new MinerBlock(mk, s));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MinerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.MINER, MinerBlockEntity::tick);
	}
}
