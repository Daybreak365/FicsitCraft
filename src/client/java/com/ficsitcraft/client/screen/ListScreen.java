package com.ficsitcraft.client.screen;

import com.ficsitcraft.screen.ListScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Shared UI for the Craft Bench and the Build Gun: a scrollable list of craftable entries. */
public abstract class ListScreen<T extends ListScreenHandler> extends HandledScreen<T> {
	protected static final int LIST_X = 8;
	protected static final int LIST_Y = 18;
	protected static final int LIST_W = 206;
	protected static final int ROW_H = 22;
	protected static final int ROWS = 5;

	protected int scroll;
	protected int listX = LIST_X;
	protected int listW = LIST_W;

	/** One clickable row. */
	protected record Entry(int index, ItemStack output, Text name, List<Ingredient> inputs) {
	}

	/** An ingredient icon with a required amount. */
	protected record Ingredient(ItemStack icon, Text name, int count, int have) {
	}

	protected ListScreen(T handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 232;
		backgroundHeight = 222;
		playerInventoryTitleX = invX();
		playerInventoryTitleY = ListScreenHandler.INV_Y - 11;
	}

	/** Left edge of the player inventory slots inside the window. */
	protected int invX() {
		return ListScreenHandler.INV_X;
	}

	/** Entries currently available to the player. */
	protected abstract List<Entry> entries();

	protected abstract Text emptyText();

	private List<Entry> cached = new ArrayList<>();

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		cached = entries();
		int max = Math.max(0, cached.size() - ROWS);
		if (scroll > max) scroll = max;
	}

	@Override
	protected void init() {
		super.init();
		cached = entries();
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		Gui.inset(ctx, x + listX - 1, y + LIST_Y - 1, listW + 2, ROWS * ROW_H + 2);
		// scrollbar
		int sbX = x + listX + listW + 3;
		ctx.fill(sbX, y + LIST_Y - 1, sbX + 6, y + LIST_Y + ROWS * ROW_H + 1, Gui.BORDER);
		if (cached.size() > ROWS) {
			int trackH = ROWS * ROW_H;
			int thumbH = Math.max(10, trackH * ROWS / cached.size());
			int thumbY = (trackH - thumbH) * scroll / Math.max(1, cached.size() - ROWS);
			ctx.fill(sbX + 1, y + LIST_Y + thumbY, sbX + 5, y + LIST_Y + thumbY + thumbH, Gui.ORANGE);
		}
		for (int row = 0; row < ROWS; row++) {
			int i = scroll + row;
			if (i >= cached.size()) break;
			Entry e = cached.get(i);
			int rx = x + listX;
			int ry = y + LIST_Y + row * ROW_H;
			boolean hover = mouseX >= rx && mouseX < rx + listW && mouseY >= ry && mouseY < ry + ROW_H;
			boolean affordable = e.inputs().stream().allMatch(in -> in.have() >= in.count()) || isCreative();
			ctx.fill(rx, ry, rx + listW, ry + ROW_H - 1, hover ? 0xFF4A4036 : (row % 2 == 0 ? 0xFF2C3036 : 0xFF30343A));
			if (hover) ctx.fill(rx, ry, rx + 2, ry + ROW_H - 1, Gui.ORANGE);
			ctx.drawItem(e.output(), rx + 3, ry + 3);
			ctx.drawItemInSlot(textRenderer, e.output(), rx + 3, ry + 3);
			int nameWidth = listW - 22 - e.inputs().size() * 20 - 4;
			String name = textRenderer.trimToWidth(e.name().getString(), nameWidth);
			ctx.drawText(textRenderer, name, rx + 22, ry + 7, affordable ? Gui.WHITE : Gui.GRAY, false);
			// inputs from the right
			int ix = rx + listW - 20;
			for (int k = e.inputs().size() - 1; k >= 0; k--) {
				Ingredient in = e.inputs().get(k);
				ctx.drawItem(in.icon(), ix, ry + 3);
				String c = (in.have() >= in.count() || isCreative() ? "" : "§c") + in.count();
				ctx.drawItemInSlot(textRenderer, in.icon(), ix, ry + 3, c);
				ix -= 20;
			}
		}
		if (cached.isEmpty()) {
			Gui.textCentered(ctx, textRenderer, emptyText(), x + listX + listW / 2, y + LIST_Y + 40, Gui.GRAY);
		}
		Gui.playerSlots(ctx, x + invX(), y + ListScreenHandler.INV_Y);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		Text hint = Text.translatable("gui.ficsitcraft.shift_hint");
		Gui.text(ctx, textRenderer, hint, backgroundWidth - 8 - textRenderer.getWidth(hint), 6, Gui.DARK_GRAY);
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
	}

	protected boolean isCreative() {
		return client != null && client.player != null && client.player.isCreative();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
		// tooltips for list icons
		for (int row = 0; row < ROWS; row++) {
			int i = scroll + row;
			if (i >= cached.size()) break;
			Entry e = cached.get(i);
			int rx = x + listX;
			int ry = y + LIST_Y + row * ROW_H;
			if (mouseY < ry + 3 || mouseY >= ry + 19) continue;
			if (mouseX >= rx + 3 && mouseX < rx + 19) {
				ctx.drawItemTooltip(textRenderer, e.output(), mouseX, mouseY);
				return;
			}
			int ix = rx + listW - 20;
			for (int k = e.inputs().size() - 1; k >= 0; k--) {
				Ingredient in = e.inputs().get(k);
				if (mouseX >= ix && mouseX < ix + 16) {
					List<Text> lines = new ArrayList<>();
					lines.add(in.name().copy().formatted(Formatting.GOLD));
					lines.add(Text.translatable("gui.ficsitcraft.have_need", in.have(), in.count())
							.formatted(in.have() >= in.count() ? Formatting.GREEN : Formatting.RED));
					ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
					return;
				}
				ix -= 20;
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int rx = x + listX;
			for (int row = 0; row < ROWS; row++) {
				int i = scroll + row;
				if (i >= cached.size()) break;
				int ry = y + LIST_Y + row * ROW_H;
				if (mouseX >= rx && mouseX < rx + listW && mouseY >= ry && mouseY < ry + ROW_H) {
					int id = cached.get(i).index() * 2 + (Screen.hasShiftDown() ? 1 : 0);
					if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int max = Math.max(0, cached.size() - ROWS);
		scroll = (int) Math.max(0, Math.min(max, scroll - Math.signum(verticalAmount)));
		return true;
	}
}
