package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.PowerPoleBlockEntity;
import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.power.PowerNodeBlockEntity;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A tall, multi-block power pole (Mk.1 = 4 blocks, Mk.2 = 5, Mk.3 = 6).
 * Section 0 is the base and holds the block entity; the lines attach to the insulators on top.
 */
public class PowerPoleBlock extends BlockWithEntity {
	public static final int MAX_HEIGHT = 6;
	public static final IntProperty SECTION = IntProperty.of("section", 0, MAX_HEIGHT - 1);

	private static final VoxelShape SHAFT = Block.createCuboidShape(6, 0, 6, 10, 16, 10);
	private static final VoxelShape BASE = VoxelShapes.union(SHAFT, Block.createCuboidShape(4, 0, 4, 12, 3, 12));
	private static final VoxelShape TOP = VoxelShapes.union(SHAFT, Block.createCuboidShape(1, 10, 6.5, 15, 13, 9.5));

	private final int mk;

	public PowerPoleBlock(int mk, Settings settings) {
		super(settings);
		this.mk = mk;
		setDefaultState(getStateManager().getDefaultState().with(SECTION, 0));
	}

	public int getMk() {
		return mk;
	}

	/** Total height in blocks. */
	public int getHeight() {
		return 3 + mk;
	}

	public int getMaxConnections() {
		return switch (mk) {
			case 1 -> 4;
			case 2 -> 7;
			default -> 10;
		};
	}

	/** Position of the base block (the one holding the block entity) for any section of a pole. */
	public static BlockPos basePos(BlockState state, BlockPos pos) {
		return state.getBlock() instanceof PowerPoleBlock ? pos.down(state.get(SECTION)) : pos;
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(s -> new PowerPoleBlock(mk, s));
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(SECTION);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		int s = state.get(SECTION);
		if (s == 0) return BASE;
		if (s == getHeight() - 1) return TOP;
		return SHAFT;
	}

	// ------------------------------------------------------------------ multi-block placement

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		World world = ctx.getWorld();
		BlockPos pos = ctx.getBlockPos();
		for (int i = 1; i < getHeight(); i++) {
			BlockPos p = pos.up(i);
			if (world.isOutOfHeightLimit(p) || !world.getBlockState(p).canReplace(ctx)) {
				if (ctx.getPlayer() != null && world.isClient) {
					ctx.getPlayer().sendMessage(Text.translatable("message.ficsitcraft.pole_no_space", getHeight())
							.formatted(Formatting.RED), true);
				}
				return null;
			}
		}
		return getDefaultState();
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world.isClient) return;
		for (int i = 1; i < getHeight(); i++) {
			world.setBlockState(pos.up(i), getDefaultState().with(SECTION, i), Block.NOTIFY_ALL);
		}
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		int s = state.get(SECTION);
		if (!world.isClient && s > 0) {
			// Breaking any upper section breaks the base, which drops the pole item.
			BlockPos base = pos.down(s);
			if (world.getBlockState(base).isOf(this)) world.breakBlock(base, !player.isCreative(), player);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			int s = state.get(SECTION);
			if (s == 0 && world.getBlockEntity(pos) instanceof PowerNodeBlockEntity node) node.disconnectAll();
			if (!world.isClient) {
				BlockPos base = pos.down(s);
				for (int i = 0; i < getHeight(); i++) {
					BlockPos p = base.up(i);
					if (p.equals(pos)) continue;
					BlockState other = world.getBlockState(p);
					if (other.isOf(this) && other.get(SECTION) == i) {
						world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
					}
				}
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	// ------------------------------------------------------------------ interaction

	@Override
	public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
										  Hand hand, BlockHitResult hit) {
		// Let the cable handle power line placement (otherwise onUse would swallow the click).
		if (stack.getItem() instanceof CableItem || stack.getItem() instanceof com.ficsitcraft.item.ZiplineItem) {
			return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		}
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(basePos(state, pos)) instanceof PowerPoleBlockEntity pole) {
			player.openHandledScreen(pole);
		}
		return ActionResult.CONSUME;
	}

	// ------------------------------------------------------------------ block entity (base only)

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(SECTION) == 0 ? new PowerPoleBlockEntity(pos, state) : null;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient || state.get(SECTION) != 0) return null;
		return validateTicker(type, ModBlockEntities.POWER_POLE, PowerPoleBlockEntity::tick);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.power_pole", getMaxConnections(), getHeight()).formatted(Formatting.GRAY));
	}
}
