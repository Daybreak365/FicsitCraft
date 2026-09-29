package com.ficsitcraft.blockentity;

import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Fuel-free power source for testing (see {@link com.ficsitcraft.block.CreativeGeneratorBlock}). */
public class CreativeGeneratorBlockEntity extends PowerNodeBlockEntity {
	public static final double MW = 3000;

	public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CREATIVE_GENERATOR, pos, state);
	}

	@Override
	public int getMaxConnections() {
		return 4;
	}

	@Override
	public boolean isGenerator() {
		return true;
	}

	@Override
	public double getPowerCapacity() {
		return MW;
	}

	@Override
	public Vec3d getConnectorOffset() {
		return new Vec3d(0.5, 1.0, 0.5);
	}

	public static void tick(World world, BlockPos pos, BlockState state, CreativeGeneratorBlockEntity be) {
		be.tickPower();
	}
}
