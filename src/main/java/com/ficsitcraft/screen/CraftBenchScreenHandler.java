package com.ficsitcraft.screen;

import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.progress.ProgressState;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.registry.ModScreenHandlers;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CraftBenchScreenHandler extends ListScreenHandler {
	private final ScreenHandlerContext context;

	public CraftBenchScreenHandler(int syncId, PlayerInventory inv) {
		this(syncId, inv, ScreenHandlerContext.EMPTY);
	}

	public CraftBenchScreenHandler(int syncId, PlayerInventory inv, ScreenHandlerContext context) {
		super(ModScreenHandlers.CRAFT_BENCH, syncId, inv);
		this.context = context;
	}

	@Override
	protected boolean onEntryClicked(PlayerEntity player, int index, int amount) {
		SfRecipe r = Recipes.get(index);
		if (r == null || !r.handcraft() || player.getServer() == null) return false;
		if (!ProgressState.get(player.getServer()).isUnlocked(r) && !player.isCreative()) return false;
		int crafted = 0;
		for (int i = 0; i < amount; i++) {
			if (!InvUtil.hasInputs(player, r.inputs(), 1)) break;
			InvUtil.removeInputs(player, r.inputs(), 1);
			player.getInventory().offerOrDrop(r.outputStack());
			crafted++;
		}
		if (crafted == 0) {
			player.sendMessage(Text.translatable("message.ficsitcraft.missing_items").formatted(Formatting.RED), true);
		} else {
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.3f, 1.6f);
		}
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, ModBlocks.CRAFT_BENCH);
	}
}
