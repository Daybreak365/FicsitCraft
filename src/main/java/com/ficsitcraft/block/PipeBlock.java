package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.PipeBlockEntity;
import com.ficsitcraft.fluid.FluidEndpoints;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Pipeline Mk.1 / Mk.2. Connects automatically to other pipes, pipeline pumps (on their in/out side) and fluid
 * buildings. The glass walls show the fluid inside and its colour.
 */
public class PipeBlock extends BlockWithEntity {
	public static final Map<Direction, BooleanProperty> PROPS = ConnectingBlock.FACING_PROPERTIES;
	private static final VoxelShape CORE = Block.createCuboidShape(5, 5, 5, 11, 11, 11);
	private static final VoxelShape[] ARMS = new VoxelShape[6];

	static {
		for (Direction d : Direction.values()) {
			double x0 = 6, y0 = 6, z0 = 6, x1 = 10, y1 = 10, z1 = 10;
			switch (d) {
				case NORTH -> z0 = 0;
				case SOUTH -> z1 = 16;
				case WEST -> x0 = 0;
				case EAST -> x1 = 16;
				case DOWN -> y0 = 0;
				case UP -> y1 = 16;
			}
			ARMS[d.ordinal()] = Block.createCuboidShape(x0, y0, z0, x1, y1, z1);
		}
	}

	private final int mk;

	public PipeBlock(int mk, Settings settings) {
		super(settings);
		this.mk = mk;
		BlockState s = getStateManager().getDefaultState();
		for (BooleanProperty p : PROPS.values()) s = s.with(p, false);
		setDefaultState(s);
	}

	public int getMk() {
		return mk;
	}

	/** m³ held by one pipe block. */
	public double capacity() {
		return mk == 1 ? 1.0 : 2.0;
	}

	/** m³ per tick (Mk.1 300 m³/min, Mk.2 600 m³/min). */
	public double maxFlow() {
		return mk == 1 ? 300.0 / 1200.0 : 600.0 / 1200.0;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new PipeBlock(mk, s));
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(PROPS.values().toArray(new BooleanProperty[0]));
	}

	public static boolean canConnect(WorldAccess world, BlockPos pos, Direction dir) {
		BlockPos n = pos.offset(dir);
		BlockState s = world.getBlockState(n);
		if (s.getBlock() instanceof PipeBlock) return true;
		if (s.getBlock() instanceof PipelinePumpBlock) return s.get(PipelinePumpBlock.FACING).getAxis() == dir.getAxis();
		return FluidEndpoints.isEndpoint(world, n);
	}

	private BlockState withConnections(WorldAccess world, BlockPos pos, BlockState state) {
		for (Direction d : Direction.values()) state = state.with(PROPS.get(d), canConnect(world, pos, d));
		return state;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return withConnections(ctx.getWorld(), ctx.getBlockPos(), getDefaultState());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
												BlockPos pos, BlockPos neighborPos) {
		return state.with(PROPS.get(direction), canConnect(world, pos, direction));
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		VoxelShape shape = CORE;
		for (Direction d : Direction.values()) if (state.get(PROPS.get(d))) shape = VoxelShapes.union(shape, ARMS[d.ordinal()]);
		return shape;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
			if (player.isSneaking()) {
				int n = PipeBlockEntity.flushNetwork(world, pos);
				player.sendMessage(Text.translatable("message.ficsitcraft.pipe_flushed", n).formatted(Formatting.YELLOW), true);
			} else {
				player.openHandledScreen(pipe);
			}
		}
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PipeBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) return validateTicker(type, ModBlockEntities.PIPE, PipeBlockEntity::clientTick);
		return validateTicker(type, ModBlockEntities.PIPE, PipeBlockEntity::serverTick);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.pipe", mk == 1 ? 300 : 600, (int) capacity()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.pipe2").formatted(Formatting.DARK_GRAY));
	}
}
