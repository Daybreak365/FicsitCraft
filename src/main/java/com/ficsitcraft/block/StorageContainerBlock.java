package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.StorageContainerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class StorageContainerBlock extends MachineBlock {
	public static final MapCodec<StorageContainerBlock> CODEC = createCodec(StorageContainerBlock::new);

	public StorageContainerBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new StorageContainerBlockEntity(pos, state);
	}
}
