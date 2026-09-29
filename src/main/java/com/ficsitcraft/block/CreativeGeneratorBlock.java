package com.ficsitcraft.block;

import com.ficsitcraft.blockentity.CreativeGeneratorBlockEntity;
import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import com.ficsitcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Test-only power source: 3000 MW without any fuel. It is not craftable and not part of the build gun, it can only be
 * taken from the creative inventory and only be placed by a player in creative mode.
 */
public class CreativeGeneratorBlock extends MachineBlock {
	public CreativeGeneratorBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<? extends BlockWithEntity> getCodec() {
		return createCodec(CreativeGeneratorBlock::new);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		PlayerEntity p = ctx.getPlayer();
		if (p == null || !p.isCreative()) {
			if (p != null && ctx.getWorld().isClient) {
				p.sendMessage(Text.translatable("message.ficsitcraft.creative_only").formatted(Formatting.RED), true);
			}
			return null;
		}
		return super.getPlacementState(ctx);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.creative_generator", (int) CreativeGeneratorBlockEntity.MW).formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.creative_generator_use").formatted(Formatting.GRAY));
	}

	@Override
	public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
										  Hand hand, BlockHitResult hit) {
		// power lines are handled by the cable item
		if (stack.getItem() instanceof CableItem || stack.getItem() instanceof com.ficsitcraft.item.ZiplineItem) {
			return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		}
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	/** Right click: the generator's window (output switch, fuse reset, grid numbers). */
	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof CreativeGeneratorBlockEntity be) player.openHandledScreen(be);
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CreativeGeneratorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.CREATIVE_GENERATOR, CreativeGeneratorBlockEntity::tick);
	}
}
