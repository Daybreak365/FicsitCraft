package com.ficsitcraft.client.hud;

import com.ficsitcraft.data.Building;
import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.data.Milestone;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.registry.ModBlocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** "Milestone completed" notification with the unlocked buildings/recipes as icons. */
public class MilestoneToast implements Toast {
	private static final long DURATION = 7000L;
	private static final int ORANGE = 0xFFFA9549;

	private final Milestone milestone;
	private final List<ItemStack> icons = new ArrayList<>();
	private final Text extra;

	public MilestoneToast(int index) {
		this.milestone = Milestones.get(index);
		if (milestone != null) {
			for (Building b : Buildings.unlockedBy(index)) icons.add(new ItemStack(b.item()));
			for (SfRecipe r : Recipes.unlockedBy(index)) {
				ItemStack s = new ItemStack(r.output());
				if (icons.stream().noneMatch(o -> ItemStack.areItemsEqual(o, s))) icons.add(s);
			}
		}
		if (index == Milestones.HUB_6) extra = Text.translatable("toast.ficsitcraft.tiers_1_2");
		else if (index == Milestones.PROJECT_PHASE_1) extra = Text.translatable("toast.ficsitcraft.tiers_3_4");
		else if (index == Milestones.PROJECT_PHASE_2) extra = Text.translatable("toast.ficsitcraft.victory");
		else extra = null;
	}

	@Override
	public Visibility draw(DrawContext ctx, ToastManager manager, long startTime) {
		int w = getWidth();
		int h = getHeight();
		TextRenderer tr = manager.getClient().textRenderer;
		ctx.fill(0, 0, w, h, 0xF0181A1E);
		ctx.fill(0, 0, 3, h, ORANGE);
		// progress stripe that fills while the toast is visible
		int stripe = (int) Math.min(w - 3, (w - 3) * startTime / (double) DURATION);
		ctx.fill(3, 0, w, 2, 0xFF3A2A1A);
		ctx.fill(3, 0, 3 + stripe, 2, ORANGE);
		ctx.drawItem(new ItemStack(ModBlocks.HUB), 8, 8);
		ctx.drawText(tr, Text.translatable("toast.ficsitcraft.milestone"), 30, 6, ORANGE, false);
		if (milestone != null) ctx.drawText(tr, milestone.name(), 30, 17, 0xFFFFFFFF, false);
		if (extra != null) {
			ctx.drawText(tr, tr.trimToWidth(extra, w - 12).getString(), 8, 31, 0xFF5FD7FF, false);
		} else {
			int x = 8;
			for (int i = 0; i < icons.size() && i < 10; i++) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(x, 29, 0);
				ctx.getMatrices().scale(0.75f, 0.75f, 1f);
				ctx.drawItem(icons.get(i), 0, 0);
				ctx.getMatrices().pop();
				x += 14;
			}
		}
		return startTime >= DURATION ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public int getWidth() {
		return 170;
	}

	@Override
	public int getHeight() {
		return 44;
	}
}
