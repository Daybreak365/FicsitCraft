package com.ficsitcraft.item;

import com.ficsitcraft.screen.BuildGunScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class BuildGunItem extends Item {
	public BuildGunItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			user.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, inv, player) -> new BuildGunScreenHandler(syncId, inv),
					Text.translatable("gui.ficsitcraft.build_gun")));
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.build_gun").formatted(Formatting.GRAY));
	}
}
