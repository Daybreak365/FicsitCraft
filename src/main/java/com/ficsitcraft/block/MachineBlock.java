package com.ficsitcraft.block;

import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.MachinePartBlock;
import com.ficsitcraft.multiblock.Multiblocks;
import com.ficsitcraft.registry.ModBlocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
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
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Common base for FICSIT buildings: horizontal facing (the front is the output side), an ACTIVE flag
 * for the "working" textures, GUI opening, inventory dropping and power line cleanup.
 */
public abstract class MachineBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	protected MachineBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH).with(ACTIVE, false));
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// The front (output) faces away from the player, so you place machines "in the direction of flow".
		Direction facing = ctx.getHorizontalPlayerFacing();
		Multiblocks.Check check = Multiblocks.canPlace(ctx.getWorld(), this, ctx.getBlockPos(), facing);
		if (check != Multiblocks.Check.OK) {
			if (ctx.getPlayer() != null && ctx.getWorld().isClient) {
				String key = switch (check) {
					case NEEDS_NODE -> "message.ficsitcraft.miner_needs_node";
					case NEEDS_WATER -> "message.ficsitcraft.extractor_needs_water";
					default -> "message.ficsitcraft.no_space";
				};
				ctx.getPlayer().sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
			}
			return null;
		}
		return getDefaultState().with(FACING, facing);
	}

	@Nullable
	public Footprint getFootprint() {
		return Multiblocks.get(this);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		Footprint fp = getFootprint();
		if (world.isClient || fp == null) return;
		MachinePartBlock part = (MachinePartBlock) ModBlocks.MACHINE_PART;
		for (BlockPos p : fp.positions(pos, state.get(FACING))) {
			if (p.equals(pos)) continue;
			world.setBlockState(p, part.stateFor(p, pos), Block.NOTIFY_ALL);
		}
	}

	private void removeParts(World world, BlockPos pos, BlockState state) {
		Footprint fp = getFootprint();
		if (fp == null || world.isClient) return;
		for (BlockPos p : fp.positions(pos, state.get(FACING))) {
			if (p.equals(pos)) continue;
			BlockState s = world.getBlockState(p);
			if (s.getBlock() instanceof MachinePartBlock && MachinePartBlock.controllerPos(s, p).equals(pos)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
			}
		}
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
		// multi-block buildings are drawn by their block entity renderer
		return getFootprint() != null ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
	}

	@Override
	public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
											 Hand hand, BlockHitResult hit) {
		// Let the cable item handle power line placement instead of opening the GUI.
		if (stack.getItem() instanceof CableItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof NamedScreenHandlerFactory factory) {
			player.openHandledScreen(factory);
			return ActionResult.CONSUME;
		}
		return ActionResult.PASS;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof PowerNodeBlockEntity node) node.disconnectAll();
			if (be instanceof Inventory inv) {
				ItemScatterer.spawn(world, pos, inv);
				world.updateComparators(pos, this);
			}
			if (be instanceof DropsContents drops) drops.dropContents(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
		if (!state.isOf(newState.getBlock())) removeParts(world, pos, state);
	}

	@Override
	public boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		String key = getTranslationKey() + ".desc";
		tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
	}

	/** Implemented by block entities with non-inventory contents that must drop when broken. */
	public interface DropsContents {
		void dropContents(World world, BlockPos pos);
	}
}
