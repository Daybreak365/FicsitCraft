package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.RailBuildingBlockEntity;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailNet;
import com.ficsitcraft.railway.RailWorld;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Train Station, Freight Platform, Fluid Freight Platform and Empty Platform: 5x9 multi-block buildings with a piece of
 * railway running through the middle. Chain them end to end (and to ordinary track) to build stations.
 */
public class RailBuildingBlock extends MachineBlock {
	public enum Kind {
		STATION(20), FREIGHT(10), FLUID(10), EMPTY(0);

		public final double powerMW;

		Kind(double powerMW) {
			this.powerMW = powerMW;
		}
	}

	private final Kind kind;

	public RailBuildingBlock(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new RailBuildingBlock(kind, s));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RailBuildingBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.RAIL_BUILDING, RailBuildingBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (!(world instanceof ServerWorld sw)) return;
		Footprint fp = getFootprint();
		if (fp == null) return;
		Direction f = state.get(FACING);
		Vec3d a = fp.localPoint(f, fp.ax() + 0.5, 0.0, 0.0);
		Vec3d b = fp.localPoint(f, fp.ax() + 0.5, 0.0, fp.depth());
		V3 back = new V3(pos.getX() + a.x, pos.getY() + a.y, pos.getZ() + a.z);
		V3 front = new V3(pos.getX() + b.x, pos.getY() + b.y, pos.getZ() + b.z);
		V3 axis = new V3(f.getOffsetX(), 0, f.getOffsetZ());
		RailWorld rw = RailWorld.get(sw);
		long track = rw.createBuildingTrack(back, front, axis, pos.asLong());
		if (world.getBlockEntity(pos) instanceof RailBuildingBlockEntity be) {
			be.setTrack(track);
			if (kind == Kind.STATION) {
				String name = rw.freeStationName();
				be.setStationName(name);
			}
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw) {
			RailWorld.get(sw).removeBuildingTrack(sw, pos.asLong());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return kind == Kind.EMPTY ? ActionResult.PASS : ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof RailBuildingBlockEntity be) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
		switch (kind) {
			case STATION -> RailNet.openStation(sp, pos, be.getStationName());
			case FREIGHT -> {
				if (player.isSneaking()) {
					be.toggleMode(sp);
				} else {
					player.openHandledScreen((NamedScreenHandlerFactory) be);
				}
			}
			case FLUID -> {
				if (player.isSneaking()) be.toggleMode(sp);
				else be.showStatus(sp);
			}
			default -> {
				return ActionResult.PASS;
			}
		}
		return ActionResult.CONSUME;
	}
}
