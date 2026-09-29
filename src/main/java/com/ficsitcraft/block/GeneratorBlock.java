package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.GeneratorBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlock extends MachineBlock {
	public enum Kind {
		BIOMASS(30),
		COAL(75);

		public final double mw;

		Kind(double mw) {
			this.mw = mw;
		}
	}

	private final Kind kind;

	public GeneratorBlock(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new GeneratorBlock(kind, s));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GeneratorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.GENERATOR, GeneratorBlockEntity::tick);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(ACTIVE)) return;
		// smoke rises from the chimney on the roof of the (multi-block) building
		com.ficsitcraft.multiblock.Footprint fp = com.ficsitcraft.multiblock.Multiblocks.get(this);
		double y = pos.getY() + (fp == null ? 1.05 : fp.height() + 0.05);
		net.minecraft.util.math.Vec3d chimney = fp == null ? new net.minecraft.util.math.Vec3d(0.5, 0, 0.5)
				: fp.localPoint(state.get(FACING), fp.width() / 2.0, 0, kind == Kind.COAL ? 1.25 : fp.depth() / 2.0);
		double x = pos.getX() + chimney.x + (random.nextDouble() - 0.5) * 0.4;
		double z = pos.getZ() + chimney.z + (random.nextDouble() - 0.5) * 0.4;
		world.addParticle(kind == Kind.COAL ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, 0.05, 0);
	}
}
