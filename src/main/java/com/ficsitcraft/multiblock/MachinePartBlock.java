package com.ficsitcraft.multiblock;

import com.ficsitcraft.item.CableItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import com.mojang.serialization.MapCodec;

/**
 * Invisible structural block filling the volume of a multi-block building.
 * Its state stores the offset to the controller; it forwards interaction, breaking and item transfer to it.
 */
public class MachinePartBlock extends Block {
	public static final int H_RANGE = 7;   // offsets -7..7
	public static final IntProperty OX = IntProperty.of("ox", 0, H_RANGE * 2);
	public static final IntProperty OY = IntProperty.of("oy", 0, 7);
	public static final IntProperty OZ = IntProperty.of("oz", 0, H_RANGE * 2);
	public static final MapCodec<MachinePartBlock> CODEC = createCodec(MachinePartBlock::new);

	public MachinePartBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(OX, H_RANGE).with(OY, 0).with(OZ, H_RANGE));
	}

	@Override
	public MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(OX, OY, OZ);
	}

	/** State for a part at {@code part} belonging to the controller at {@code controller}. */
	public BlockState stateFor(BlockPos part, BlockPos controller) {
		int dx = controller.getX() - part.getX();
		int dy = part.getY() - controller.getY();
		int dz = controller.getZ() - part.getZ();
		return getDefaultState().with(OX, dx + H_RANGE).with(OY, dy).with(OZ, dz + H_RANGE);
	}

	public static BlockPos controllerPos(BlockState state, BlockPos pos) {
		return pos.add(state.get(OX) - H_RANGE, -state.get(OY), state.get(OZ) - H_RANGE);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		BlockState c = world.getBlockState(controllerPos(state, pos));
		return c.getBlock() instanceof MachinePartBlock ? ItemStack.EMPTY : c.getBlock().getPickStack(world, controllerPos(state, pos), c);
	}

	@Override
	public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
										  Hand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof CableItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		BlockPos c = controllerPos(state, pos);
		BlockState cs = world.getBlockState(c);
		if (cs.getBlock() instanceof MachinePartBlock || cs.isAir()) return ActionResult.PASS;
		return cs.onUse(world, player, hit.withBlockPos(c));
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient) {
			BlockPos c = controllerPos(state, pos);
			BlockState cs = world.getBlockState(c);
			if (!cs.isAir() && !(cs.getBlock() instanceof MachinePartBlock)) world.breakBlock(c, !player.isCreative(), player);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
		if (world.isClient || state.isOf(newState.getBlock())) return;
		// A part destroyed by anything else (explosion, command) takes the whole building down.
		BlockPos c = controllerPos(state, pos);
		BlockState cs = world.getBlockState(c);
		if (Multiblocks.get(cs.getBlock()) != null) world.breakBlock(c, true);
	}

	@Override
	public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
		return 1.0f;
	}
}
