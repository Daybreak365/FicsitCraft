package com.ficsitcraft.item;

import com.ficsitcraft.rail.V3;
import com.ficsitcraft.railway.RailWorld;
import com.ficsitcraft.train.VehicleType;
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

/** Locomotive / freight car / fluid freight car: right click next to a track to put it on the rails (next to a train it couples on). */
public class VehicleItem extends Item {
	private final VehicleType type;

	public VehicleItem(VehicleType type, Settings settings) {
		super(settings);
		this.type = type;
	}

	public VehicleType getVehicleType() {
		return type;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(ctx.getPlayer() instanceof ServerPlayerEntity p) || !(ctx.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;
		Vec3d h = ctx.getHitPos();
		Vec3d look = p.getRotationVector();
		String err = RailWorld.get(world).placeVehicle(world, type, new V3(h.x, h.y, h.z), look.x, look.z);
		if (err != null) {
			p.sendMessage(Text.translatable("message.ficsitcraft.vehicle_" + err).formatted(Formatting.RED), true);
			return ActionResult.CONSUME;
		}
		if (!p.isCreative()) ctx.getStack().decrement(1);
		world.playSound(null, BlockPos.ofFloored(h), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.9f, 0.7f);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType tooltipType) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft." + type.id).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.vehicle_place").formatted(Formatting.DARK_GRAY));
	}
}
