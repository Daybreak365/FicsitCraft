package com.ficsitcraft.client.screen;

import com.ficsitcraft.data.Building;
import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.data.Cost;
import com.ficsitcraft.data.Milestone;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.screen.HubScreenHandler;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class HubScreen extends HandledScreen<HubScreenHandler> {
	private static final int LIST_X = 6;
	private static final int LIST_Y = 18;
	private static final int LIST_W = 114;
	private static final int ROW_H = 11;
	private static final int ROWS = 13;
	private static final int PANEL_X = 126;

	private final PlayerInventory playerInv;
	private int selected = 0;
	private int scroll = 0;
	private ButtonWidget submit;

	/** row: tier header (milestone == null) or milestone. */
	private record Row(int tier, Milestone milestone) {
	}

	private final List<Row> rows = new ArrayList<>();

	public HubScreen(HubScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.playerInv = inventory;
		backgroundWidth = 256;
		backgroundHeight = 256;
		playerInventoryTitleX = HubScreenHandler.INV_X;
		playerInventoryTitleY = HubScreenHandler.INV_Y - 11;
		for (int tier = 0; tier <= Milestones.MAX_TIER; tier++) {
			rows.add(new Row(tier, null));
			for (Milestone m : Milestones.ALL) if (m.tier() == tier) rows.add(new Row(tier, m));
		}
	}

	@Override
	protected void init() {
		super.init();
		submit = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.hub.submit"), b -> {
			if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, selected);
		}).dimensions(x + PANEL_X + 4, y + 146, 118, 16).build());
		select(firstAvailable());
	}

	private int firstAvailable() {
		long mask = handler.getMask();
		for (Milestone m : Milestones.ALL) if (Milestones.isAvailable(mask, m)) return m.index();
		return Milestones.ALL.size() - 1;
	}

	private void select(int index) {
		selected = index;
		if (client != null && client.interactionManager != null)
			client.interactionManager.clickButton(handler.syncId, HubScreenHandler.SELECT_OFFSET + index);
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		Milestone m = Milestones.get(selected);
		if (submit != null) submit.active = m != null && Milestones.isAvailable(handler.getMask(), m);
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		Gui.inset(ctx, x + LIST_X - 1, y + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 2);
		Gui.inset(ctx, x + PANEL_X, y + LIST_Y - 1, 124, 146);
		long mask = handler.getMask();

		for (int r = 0; r < ROWS; r++) {
			int i = scroll + r;
			if (i >= rows.size()) break;
			Row row = rows.get(i);
			int rx = x + LIST_X;
			int ry = y + LIST_Y + r * ROW_H;
			if (row.milestone() == null) {
				boolean open = Milestones.isTierAvailable(mask, row.tier());
				ctx.fill(rx, ry, rx + LIST_W, ry + ROW_H, 0xFF1E2024);
				Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.hub.tier", row.tier()), rx + 3, ry + 2,
						open ? Gui.ORANGE : Gui.DARK_GRAY);
				continue;
			}
			Milestone m = row.milestone();
			boolean done = Milestones.isDone(mask, m.index());
			boolean avail = Milestones.isAvailable(mask, m);
			boolean hover = mouseX >= rx && mouseX < rx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
			if (m.index() == selected) ctx.fill(rx, ry, rx + LIST_W, ry + ROW_H, 0xFF5A4630);
			else if (hover) ctx.fill(rx, ry, rx + LIST_W, ry + ROW_H, 0xFF3C4046);
			int color = done ? Gui.GREEN : avail ? Gui.WHITE : Gui.DARK_GRAY;
			String prefix = done ? "✔ " : avail ? "• " : "✖ ";
			String name = textRenderer.trimToWidth(prefix + m.name().getString(), LIST_W - 6);
			ctx.drawText(textRenderer, name, rx + 3, ry + 2, color, false);
		}
		// scrollbar
		if (rows.size() > ROWS) {
			int trackH = ROWS * ROW_H;
			int thumbH = Math.max(8, trackH * ROWS / rows.size());
			int thumbY = (trackH - thumbH) * scroll / Math.max(1, rows.size() - ROWS);
			ctx.fill(x + LIST_X + LIST_W - 2, y + LIST_Y + thumbY, x + LIST_X + LIST_W, y + LIST_Y + thumbY + thumbH, Gui.ORANGE);
		}

		drawDetails(ctx, mask, mouseX, mouseY);
		Gui.playerSlots(ctx, x + HubScreenHandler.INV_X, y + HubScreenHandler.INV_Y);
	}

	private List<ItemStack> unlockIcons(Milestone m) {
		List<ItemStack> icons = new ArrayList<>();
		for (Building b : Buildings.unlockedBy(m.index())) icons.add(new ItemStack(b.item()));
		for (SfRecipe r : Recipes.unlockedBy(m.index())) {
			ItemStack s = new ItemStack(r.output());
			if (icons.stream().noneMatch(o -> ItemStack.areItemsEqual(o, s))) icons.add(s);
		}
		return icons;
	}

	private void drawDetails(DrawContext ctx, long mask, int mouseX, int mouseY) {
		Milestone m = Milestones.get(selected);
		if (m == null) return;
		int px = x + PANEL_X + 4;
		int py = y + LIST_Y + 2;
		List<OrderedText> nameLines = textRenderer.wrapLines(m.name(), 116);
		for (OrderedText line : nameLines) {
			ctx.drawText(textRenderer, line, px, py, Gui.ORANGE, false);
			py += 10;
		}
		boolean done = Milestones.isDone(mask, m.index());
		Text state = done ? Text.translatable("gui.ficsitcraft.hub.completed")
				: Milestones.isAvailable(mask, m) ? Text.translatable("gui.ficsitcraft.hub.available")
				: Text.translatable(Milestones.lockReasonKey(mask, m));
		ctx.drawText(textRenderer, state, px, py, done ? Gui.GREEN : Milestones.isAvailable(mask, m) ? Gui.GRAY : Gui.RED, false);
		py += 12;

		boolean synced = handler.getSyncedSelection() == selected;
		for (int i = 0; i < m.cost().size(); i++) {
			Cost c = m.cost().get(i);
			ItemStack icon = new ItemStack(c.item());
			ctx.drawItem(icon, px, py);
			int deposited = synced ? handler.getDeposited(i) : 0;
			int have = InvUtil.count(playerInv, c.item());
			int remaining = Math.max(0, c.count() - deposited);
			int color = done || deposited + have >= c.count() ? Gui.GREEN : Gui.WHITE;
			String line = done ? c.count() + " / " + c.count() : (deposited + " / " + c.count());
			ctx.drawText(textRenderer, line, px + 20, py + 4, color, false);
			if (!done && remaining > 0) {
				String inv = "(" + have + ")";
				ctx.drawText(textRenderer, inv, px + 116 - textRenderer.getWidth(inv), py + 4,
						have >= remaining ? Gui.GREEN : Gui.DARK_GRAY, false);
			}
			py += 17;
		}

		// unlock icons
		int uy = y + LIST_Y + 108;
		ctx.drawText(textRenderer, Text.translatable("gui.ficsitcraft.hub.unlocks"), px, uy - 11, Gui.GRAY, false);
		List<ItemStack> icons = unlockIcons(m);
		for (int i = 0; i < icons.size() && i < 14; i++) {
			int ix = px + (i % 7) * 17;
			int iy = uy + (i / 7) * 17;
			ctx.drawItem(icons.get(i), ix, iy);
		}
		if (icons.isEmpty()) {
			int dy = uy;
			for (OrderedText line : textRenderer.wrapLines(m.description(), 116)) {
				ctx.drawText(textRenderer, line, px, dy, Gui.CYAN, false);
				dy += 9;
				if (dy > uy + 27) break;
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
		Milestone m = Milestones.get(selected);
		if (m == null) return;
		int px = x + PANEL_X + 4;
		// cost icon tooltips
		int py = y + LIST_Y + 2 + textRenderer.wrapLines(m.name(), 116).size() * 10 + 12;
		for (Cost c : m.cost()) {
			if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
				ctx.drawItemTooltip(textRenderer, new ItemStack(c.item()), mouseX, mouseY);
				return;
			}
			py += 17;
		}
		int uy = y + LIST_Y + 108;
		List<ItemStack> icons = unlockIcons(m);
		for (int i = 0; i < icons.size() && i < 14; i++) {
			int ix = px + (i % 7) * 17;
			int iy = uy + (i / 7) * 17;
			if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
				List<Text> lines = new ArrayList<>();
				lines.add(icons.get(i).getName().copy().formatted(Formatting.GOLD));
				lines.add(Text.translatable("gui.ficsitcraft.hub.unlock_hint").formatted(Formatting.GRAY));
				ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
				return;
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int r = 0; r < ROWS; r++) {
				int i = scroll + r;
				if (i >= rows.size()) break;
				Row row = rows.get(i);
				int rx = x + LIST_X;
				int ry = y + LIST_Y + r * ROW_H;
				if (row.milestone() != null && mouseX >= rx && mouseX < rx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H) {
					select(row.milestone().index());
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int max = Math.max(0, rows.size() - ROWS);
		scroll = (int) Math.max(0, Math.min(max, scroll - Math.signum(verticalAmount)));
		return true;
	}
}
