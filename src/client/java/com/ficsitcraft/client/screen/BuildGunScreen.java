package com.ficsitcraft.client.screen;

import com.ficsitcraft.data.Building;
import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.data.Cost;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.screen.BuildGunScreenHandler;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class BuildGunScreen extends ListScreen<BuildGunScreenHandler> {
	private final PlayerInventory playerInv;

	public BuildGunScreen(BuildGunScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.playerInv = inventory;
	}

	@Override
	protected List<Entry> entries() {
		long mask = handler.getMask();
		List<Entry> list = new ArrayList<>();
		for (Building b : Buildings.ALL) {
			if (!Milestones.isDone(mask, b.milestone()) && !isCreative()) continue;
			List<Ingredient> ins = new ArrayList<>();
			for (Cost c : b.cost()) {
				ins.add(new Ingredient(new ItemStack(c.item()), c.item().getName(), c.count(), InvUtil.count(playerInv, c.item())));
			}
			list.add(new Entry(b.index(), b.stack(), b.item().getName(), ins));
		}
		return list;
	}

	@Override
	protected Text emptyText() {
		return Text.translatable("gui.ficsitcraft.nothing_unlocked");
	}
}
