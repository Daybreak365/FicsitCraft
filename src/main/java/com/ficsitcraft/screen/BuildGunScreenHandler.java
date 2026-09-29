package com.ficsitcraft.screen;

import com.ficsitcraft.data.Building;
import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.progress.ProgressState;
import com.ficsitcraft.registry.ModScreenHandlers;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Build Gun menu: turns building materials into placeable buildings. */
public class BuildGunScreenHandler extends ListScreenHandler {
	public BuildGunScreenHandler(int syncId, PlayerInventory inv) {
		super(ModScreenHandlers.BUILD_GUN, syncId, inv);
	}

	@Override
	protected boolean onEntryClicked(PlayerEntity player, int index, int amount) {
		Building b = Buildings.get(index);
		if (b == null || player.getServer() == null) return false;
		if (!ProgressState.get(player.getServer()).isUnlocked(b) && !player.isCreative()) return false;
		int built = 0;
		for (int i = 0; i < amount; i++) {
			if (!InvUtil.hasCosts(player, b.cost(), 1)) break;
			InvUtil.removeCosts(player, b.cost(), 1);
			player.getInventory().offerOrDrop(b.stack());
			built++;
		}
		if (built == 0) {
			player.sendMessage(Text.translatable("message.ficsitcraft.missing_items").formatted(Formatting.RED), true);
		} else {
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.4f, 1.8f);
		}
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
