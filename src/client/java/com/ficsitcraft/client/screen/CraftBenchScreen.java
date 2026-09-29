package com.ficsitcraft.client.screen;

import com.ficsitcraft.data.MachineType;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.data.Stack;
import com.ficsitcraft.screen.CraftBenchScreenHandler;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class CraftBenchScreen extends ListScreen<CraftBenchScreenHandler> {
	private final PlayerInventory playerInv;

	public CraftBenchScreen(CraftBenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.playerInv = inventory;
	}

	@Override
	protected List<Entry> entries() {
		long mask = handler.getMask();
		long time = client != null && client.world != null ? client.world.getTime() : 0;
		List<Entry> list = new ArrayList<>();
		for (SfRecipe r : Recipes.forMachine(MachineType.CRAFT_BENCH)) {
			if (!Milestones.isDone(mask, r.milestone()) && !isCreative()) continue;
			List<Ingredient> ins = new ArrayList<>();
			for (Stack s : r.inputs()) {
				ins.add(new Ingredient(s.ing().displayStack(time), s.ing().name(), s.count(), InvUtil.count(playerInv, s.ing()::test)));
			}
			list.add(new Entry(r.index(), r.outputStack(), r.output().getName(), ins));
		}
		return list;
	}

	@Override
	protected Text emptyText() {
		return Text.translatable("gui.ficsitcraft.nothing_unlocked");
	}
}
