package com.ficsitcraft.item;

import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Block signal (type 1) and path signal (type 2). Right click a track (or a track end) to put a signal there; it governs the
 * trains running in the direction you were looking along the track.
 */
public class RailSignalItem extends Item {
	private final int signalType;

	public RailSignalItem(int signalType, Settings settings) {
		super(settings);
		this.signalType = signalType;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(ctx.getPlayer() instanceof ServerPlayerEntity p) || !(ctx.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;
		Vec3d h = ctx.getHitPos();
		Vec3d look = p.getRotationVector();
		String err = RailWorld.get(world).placeSignal(new V3(h.x, h.y, h.z), look.x, look.z, signalType);
		if (err != null) {
			p.sendMessage(Text.translatable("message.ficsitcraft." + err).formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		if (!p.isCreative()) ctx.getStack().decrement(1);
		world.playSound(null, BlockPos.ofFloored(h), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.signal_" + (signalType == 1 ? "block" : "path")).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.signal_place").formatted(Formatting.DARK_GRAY));
	}
}
