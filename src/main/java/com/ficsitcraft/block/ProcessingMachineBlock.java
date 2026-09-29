package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.ProcessingMachineBlockEntity;
import com.ficsitcraft.data.MachineType;
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

/** Smelter, Foundry, Constructor, Assembler and Manufacturer. */
public class ProcessingMachineBlock extends MachineBlock {
	private final MachineType type;

	public ProcessingMachineBlock(MachineType type, Settings settings) {
		super(settings);
		this.type = type;
	}

	public MachineType getMachineType() {
		return type;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new ProcessingMachineBlock(type, s));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ProcessingMachineBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.PROCESSING_MACHINE, ProcessingMachineBlockEntity::tick);
	}
}
