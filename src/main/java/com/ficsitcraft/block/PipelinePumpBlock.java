package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.PipelinePumpBlockEntity;
import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** In-line Pipeline Pump Mk.1: pushes fluid from its back to its front with 20 blocks of head lift. 4 MW. */
public class PipelinePumpBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.FACING;
	public static final BooleanProperty ACTIVE = MachineBlock.ACTIVE;
	public static final MapCodec<PipelinePumpBlock> CODEC = createCodec(PipelinePumpBlock::new);
	public static final double HEAD_LIFT = 20;
	public static final double POWER_MW = 4;

	public PipelinePumpBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH).with(ACTIVE, false));
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// flow direction = where the player looks (also up/down for vertical lifts)
		return getDefaultState().with(FACING, ctx.getPlayerLookDirection());
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
	public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
										  Hand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof CableItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof NamedScreenHandlerFactory f) player.openHandledScreen(f);
		return ActionResult.CONSUME;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof PowerNodeBlockEntity node) node.disconnectAll();
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PipelinePumpBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.PIPELINE_PUMP, PipelinePumpBlockEntity::tick);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("block.ficsitcraft.pipeline_pump.desc").formatted(Formatting.GRAY));
	}
}
