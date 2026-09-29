package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.ConveyorBeltBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Conveyor Belt Mk.1-3. Horizontal belts are thin slabs; vertical ones (placed while sneaking)
 * act as conveyor lifts moving items up or down.
 */
public class ConveyorBeltBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final EnumProperty<BeltShape> SHAPE = EnumProperty.of("shape", BeltShape.class);

	private static final VoxelShape FLAT = Block.createCuboidShape(0, 0, 0, 16, 4, 16);
	private static final VoxelShape VERTICAL = VoxelShapes.union(
			Block.createCuboidShape(2, 0, 2, 14, 16, 3),
			Block.createCuboidShape(2, 0, 13, 14, 16, 14),
			Block.createCuboidShape(2, 0, 2, 3, 16, 14),
			Block.createCuboidShape(13, 0, 2, 14, 16, 14));
	/** Ramp shapes (4 steps) rising towards the given horizontal direction. */
	private static final Map<Direction, VoxelShape> RAMPS = new EnumMap<>(Direction.class);
	private static final Map<Direction, VoxelShape> TURNS_LEFT = new EnumMap<>(Direction.class);
	private static final Map<Direction, VoxelShape> TURNS_RIGHT = new EnumMap<>(Direction.class);

	static {
		for (Direction d : Direction.Type.HORIZONTAL) {
			VoxelShape shape = VoxelShapes.empty();
			for (int i = 0; i < 4; i++) {
				// in north orientation: step i spans z = [12 - 4i, 16 - 4i], height 4 + 4i
				shape = VoxelShapes.union(shape, rotatedBox(d, 0, 0, 12 - 4 * i, 16, 4 + 4 * i, 16 - 4 * i));
			}
			RAMPS.put(d, shape);
			// curves: quarter disc with a rounded outer corner, approximated by 4 columns
			int[] depth = {16, 15, 14, 11};
			VoxelShape left = VoxelShapes.empty();
			VoxelShape right = VoxelShapes.empty();
			for (int i = 0; i < 4; i++) {
				left = VoxelShapes.union(left, rotatedBox(d, 4 * i, 0, 0, 4 * i + 4, 4, depth[i]));
				right = VoxelShapes.union(right, rotatedBox(d, 12 - 4 * i, 0, 0, 16 - 4 * i, 4, depth[i]));
			}
			TURNS_LEFT.put(d, left);
			TURNS_RIGHT.put(d, right);
		}
	}

	/** Creates a box given in "facing north" coordinates, rotated to the given direction. */
	private static VoxelShape rotatedBox(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
		return switch (d) {
			case EAST -> Block.createCuboidShape(16 - z1, y0, x0, 16 - z0, y1, x1);
			case SOUTH -> Block.createCuboidShape(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case WEST -> Block.createCuboidShape(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.createCuboidShape(x0, y0, z0, x1, y1, z1);
		};
	}

	private final int mk;

	public ConveyorBeltBlock(int mk, Settings settings) {
		super(settings);
		this.mk = mk;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SHAPE, BeltShape.STRAIGHT));
	}

	public int getMk() {
		return mk;
	}

	/** Throughput in items per minute (Satisfactory values). */
	public int itemsPerMinute() {
		return switch (mk) {
			case 1 -> 60;
			case 2 -> 120;
			default -> 270;
		};
	}

	// ------------------------------------------------------------------ rail-like auto shaping

	private static boolean isBelt(BlockState state) {
		return state.getBlock() instanceof ConveyorBeltBlock;
	}

	/** Absolute direction the items come FROM for a horizontal belt state. */
	public static Direction inputSide(BlockState state) {
		Direction f = state.get(FACING);
		return switch (state.get(SHAPE)) {
			case TURN_LEFT -> f.rotateYCounterclockwise();
			case TURN_RIGHT -> f.rotateYClockwise();
			default -> f.getOpposite();
		};
	}

	/** True if the block at {@code pos} is a flat belt whose output points in direction {@code towards}. */
	private static boolean beltFlowsTowards(WorldAccess world, BlockPos pos, Direction towards) {
		BlockState s = world.getBlockState(pos);
		return isBelt(s) && s.get(FACING) == towards && s.get(SHAPE) != BeltShape.ASCENDING;
	}

	/** True if the block at {@code pos} is a horizontal belt that takes its input from {@code from} (absolute). */
	private static boolean beltWaitsFrom(WorldAccess world, BlockPos pos, Direction from) {
		BlockState s = world.getBlockState(pos);
		return isBelt(s) && s.get(FACING).getAxis() != Direction.Axis.Y && !s.get(SHAPE).isRamp() && inputSide(s) == from;
	}

	/** A machine, container or belt: something items can come from / go to. */
	private static boolean isEndpoint(WorldAccess world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return isBelt(s) || s.getBlock() instanceof com.ficsitcraft.multiblock.MachinePartBlock || world.getBlockEntity(pos) != null;
	}

	/**
	 * Rail-like auto shaping. Both ends of a belt snap to neighbouring belts when they are not connected yet:
	 * the input end turns towards a belt pointing at us, the output end turns towards a belt waiting for input
	 * from us. Returns the new (facing, shape) state.
	 */
	public static BlockState computeState(WorldAccess world, BlockPos pos, BlockState state) {
		Direction facing = state.get(FACING);
		if (facing.getAxis() == Direction.Axis.Y) return state.with(SHAPE, BeltShape.STRAIGHT);
		Direction in = inputSide(state);   // where items come from
		Direction out = facing;            // where items go to

		// ---- ramps (only for straight belts), like rails
		Direction back = facing.getOpposite();
		BlockPos front = pos.offset(facing);
		if (in == back) {
			BlockState frontUp = world.getBlockState(front.up());
			if (isBelt(frontUp) && frontUp.get(FACING) != back && !isBelt(world.getBlockState(front))) {
				return state.with(SHAPE, BeltShape.ASCENDING);
			}
			if (beltFlowsTowards(world, pos.offset(back).up(), facing) && !isEndpoint(world, pos.offset(back))) {
				return state.with(SHAPE, BeltShape.DESCENDING);
			}
		}

		// ---- input end
		boolean inConnected = beltFlowsTowards(world, pos.offset(in), in.getOpposite())
				|| (!isBelt(world.getBlockState(pos.offset(in))) && isEndpoint(world, pos.offset(in)));
		if (!inConnected) {
			Direction candidate = null;
			int count = 0;
			for (Direction d : Direction.Type.HORIZONTAL) {
				if (d == out) continue;
				if (beltFlowsTowards(world, pos.offset(d), d.getOpposite())) {
					candidate = d;
					count++;
				}
			}
			if (count == 1) in = candidate;
		}

		// ---- output end
		BlockPos outPos = pos.offset(out);
		BlockState belowOut = world.getBlockState(outPos.down());
		boolean outConnected = isEndpoint(world, outPos) || isBelt(world.getBlockState(outPos.up()))
				|| (isBelt(belowOut) && belowOut.get(SHAPE) == BeltShape.DESCENDING);
		if (!outConnected) {
			Direction candidate = null;
			int count = 0;
			for (Direction d : Direction.Type.HORIZONTAL) {
				if (d == in) continue;
				if (beltWaitsFrom(world, pos.offset(d), d.getOpposite())) {
					candidate = d;
					count++;
				}
			}
			if (count == 1) out = candidate;
		}

		BeltShape shape;
		if (in == out.getOpposite()) shape = BeltShape.STRAIGHT;
		else if (in == out.rotateYCounterclockwise()) shape = BeltShape.TURN_LEFT;
		else if (in == out.rotateYClockwise()) shape = BeltShape.TURN_RIGHT;
		else {
			out = facing;
			shape = BeltShape.STRAIGHT;
		}
		return state.with(FACING, out).with(SHAPE, shape);
	}

	/** Belts diagonally above/below don't get neighbor updates, so refresh them manually (like rails). */
	private static void refreshAround(World world, BlockPos pos) {
		for (Direction d : Direction.Type.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy += 2) {
				BlockPos p = pos.offset(d).up(dy);
				BlockState s = world.getBlockState(p);
				if (!isBelt(s)) continue;
				BlockState n = computeState(world, p, s);
				if (n != s) world.setBlockState(p, n, Block.NOTIFY_ALL);
			}
		}
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
												BlockPos pos, BlockPos neighborPos) {
		return computeState(world, pos, state);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && !oldState.isOf(this)) refreshAround(world, pos);
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new ConveyorBeltBlock(mk, s));
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, SHAPE);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		PlayerEntity player = ctx.getPlayer();
		if (player != null && player.isSneaking()) {
			return getDefaultState().with(FACING, player.getPitch() < 0 ? Direction.UP : Direction.DOWN);
		}
		Direction facing = ctx.getHorizontalPlayerFacing();
		return computeState(ctx.getWorld(), ctx.getBlockPos(), getDefaultState().with(FACING, facing));
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		Direction f = state.get(FACING);
		if (f.getAxis() == Direction.Axis.Y) return VERTICAL;
		return switch (state.get(SHAPE)) {
			case ASCENDING -> RAMPS.get(f);
			case DESCENDING -> RAMPS.get(f.getOpposite());
			case TURN_LEFT -> TURNS_LEFT.get(f);
			case TURN_RIGHT -> TURNS_RIGHT.get(f);
			default -> FLAT;
		};
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof ConveyorBeltBlockEntity belt) {
			belt.takeAll(player);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
		Direction dir = state.get(FACING);
		if (dir.getAxis() != Direction.Axis.Y && !state.get(SHAPE).isRamp()) {
			if (entity instanceof ItemEntity item && !world.isClient) {
				if (world.getBlockEntity(pos) instanceof ConveyorBeltBlockEntity belt) belt.acceptItemEntity(item);
			} else if (entity instanceof LivingEntity living && !living.isSneaking()) {
				double speed = 0.02 * mk;
				living.addVelocity(dir.getOffsetX() * speed, 0, dir.getOffsetZ() * speed);
			}
		}
		super.onSteppedOn(world, pos, state, entity);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		boolean removed = !state.isOf(newState.getBlock());
		if (removed && world.getBlockEntity(pos) instanceof ConveyorBeltBlockEntity belt) {
			belt.dropContents(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
		if (removed && !world.isClient) refreshAround(world, pos);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ConveyorBeltBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) return validateTicker(type, ModBlockEntities.CONVEYOR_BELT, ConveyorBeltBlockEntity::clientTick);
		return validateTicker(type, ModBlockEntities.CONVEYOR_BELT, ConveyorBeltBlockEntity::serverTick);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.belt_speed", itemsPerMinute()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.belt_lift").formatted(Formatting.DARK_GRAY));
	}
}
