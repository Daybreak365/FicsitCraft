package com.ficsitcraft.item;

import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cable is both a crafting part and the tool used to string power lines:
 * right click a power connector (pole, generator, machine), then a second one.
 * Costs 1 cable per 10 blocks of line. Sneak + right click removes all lines of a connector.
 */
public class CableItem extends Item {
	public static final int BLOCKS_PER_CABLE = 10;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private record Pending(RegistryKey<World> dim, BlockPos pos, long time) {
	}

	public CableItem(Settings settings) {
		super(settings);
	}

	public static void cleanupPending() {
		// Selections simply expire after 60 s; nothing else to do.
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		World world = ctx.getWorld();
		// clicking any section of a tall power pole targets its base
		BlockPos pos = com.ficsitcraft.multiblock.Multiblocks.resolveController(world, ctx.getBlockPos());
		PlayerEntity player = ctx.getPlayer();
		if (!(world.getBlockEntity(pos) instanceof PowerNodeBlockEntity node) || player == null) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;

		if (player.isSneaking()) {
			int removed = node.disconnectAll();
			PENDING.remove(player.getUuid());
			player.sendMessage(Text.translatable("message.ficsitcraft.lines_removed", removed).formatted(Formatting.YELLOW), true);
			return ActionResult.CONSUME;
		}

		Pending pending = PENDING.get(player.getUuid());
		if (pending == null || !pending.dim().equals(world.getRegistryKey()) || world.getTime() - pending.time() > 1200) {
			if (!node.hasFreeConnection()) {
				player.sendMessage(Text.translatable("message.ficsitcraft.no_free_connection").formatted(Formatting.RED), true);
				return ActionResult.CONSUME;
			}
			PENDING.put(player.getUuid(), new Pending(world.getRegistryKey(), pos.toImmutable(), world.getTime()));
			player.sendMessage(Text.translatable("message.ficsitcraft.line_start").formatted(Formatting.AQUA), true);
			return ActionResult.CONSUME;
		}

		PENDING.remove(player.getUuid());
		BlockPos from = pending.pos();
		if (from.equals(pos)) {
			player.sendMessage(Text.translatable("message.ficsitcraft.line_cancel").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		if (!(world.getBlockEntity(from) instanceof PowerNodeBlockEntity start)) {
			player.sendMessage(Text.translatable("message.ficsitcraft.line_invalid").formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		if (start.isConnectedTo(pos)) {
			player.sendMessage(Text.translatable("message.ficsitcraft.line_exists").formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		if (!start.hasFreeConnection() || !node.hasFreeConnection()) {
			player.sendMessage(Text.translatable("message.ficsitcraft.no_free_connection").formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		double dist = Math.sqrt(from.getSquaredDistance(pos));
		if (dist > PowerNodeBlockEntity.MAX_LINE_LENGTH) {
			player.sendMessage(Text.translatable("message.ficsitcraft.line_too_long", PowerNodeBlockEntity.MAX_LINE_LENGTH).formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		int cost = Math.max(1, MathHelper.ceil(dist / BLOCKS_PER_CABLE));
		ItemStack stack = ctx.getStack();
		if (!player.isCreative()) {
			if (stack.getCount() < cost) {
				player.sendMessage(Text.translatable("message.ficsitcraft.line_need_cable", cost).formatted(Formatting.RED), true);
				return ActionResult.CONSUME;
			}
			stack.decrement(cost);
		}
		start.addConnection(pos);
		node.addConnection(from);
		world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1f, 1.2f);
		player.sendMessage(Text.translatable("message.ficsitcraft.line_done", String.format("%.1f", dist), cost).formatted(Formatting.GREEN), true);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.cable").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.cable2").formatted(Formatting.DARK_GRAY));
	}
}
