package com.ficsitcraft.client.screen;

import com.ficsitcraft.blockentity.PowerPoleBlockEntity;
import com.ficsitcraft.screen.PowerPoleScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import static com.ficsitcraft.screen.PowerPoleScreenHandler.*;

/** Satisfactory-style power pole info panel: stats, fuse and a 60 s power graph. */
public class PowerPoleScreen extends HandledScreen<PowerPoleScreenHandler> {
	private static final int GRAPH_X = 10;
	private static final int GRAPH_Y = 92;
	private static final int GRAPH_W = 216;
	private static final int GRAPH_H = 78;

	private ButtonWidget resetButton;

	public PowerPoleScreen(PowerPoleScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 236;
		backgroundHeight = 190;
	}

	@Override
	protected void init() {
		super.init();
		resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.reset_fuse"), b -> {
			if (client != null && client.interactionManager != null)
				client.interactionManager.clickButton(handler.syncId, BUTTON_RESET_FUSE);
		}).dimensions(x + backgroundWidth - 90, y + 60, 80, 18).build());
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		if (resetButton != null) resetButton.active = handler.isFuseBlown();
	}

	private void tile(DrawContext ctx, int tx, int ty, int w, Text label, String value, int color) {
		Gui.inset(ctx, tx, ty, w, 28);
		ctx.drawText(textRenderer, label, tx + 5, ty + 4, Gui.GRAY, false);
		ctx.drawText(textRenderer, value, tx + 5, ty + 15, color, false);
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		double cap = handler.mw(P_CAPACITY);
		double use = handler.mw(P_DEMAND);
		double max = handler.mw(P_MAX_DEMAND);
		boolean blown = handler.isFuseBlown();

		// stat tiles
		tile(ctx, x + 8, y + 18, 70, Text.translatable("gui.ficsitcraft.pole.capacity"), Gui.fmt(cap) + " MW", Gui.CYAN);
		tile(ctx, x + 83, y + 18, 70, Text.translatable("gui.ficsitcraft.pole.consumption"), Gui.fmt(use) + " MW",
				use > cap ? Gui.RED : Gui.ORANGE);
		tile(ctx, x + 158, y + 18, 70, Text.translatable("gui.ficsitcraft.pole.max_consumption"), Gui.fmt(max) + " MW",
				max > cap ? Gui.YELLOW : Gui.WHITE);

		// load bar
		double frac = cap <= 0 ? 0 : use / cap;
		Gui.bar(ctx, x + 8, y + 50, 138, 8, frac, blown || frac > 1 ? Gui.RED : frac > 0.9 ? Gui.YELLOW : Gui.GREEN);

		// fuse state
		Text state;
		int color;
		if (blown) {
			state = Text.translatable("gui.ficsitcraft.status.fuse");
			color = (System.currentTimeMillis() / 400) % 2 == 0 ? Gui.RED : 0xFF992020;
		} else if (handler.isPowered()) {
			state = Text.translatable("gui.ficsitcraft.pole.state_ok", Gui.fmt(frac * 100));
			color = Gui.GREEN;
		} else {
			state = Text.translatable("gui.ficsitcraft.pole.state_none");
			color = Gui.GRAY;
		}
		ctx.drawText(textRenderer, state, x + 8, y + 62, color, false);

		drawGraph(ctx);
	}

	private void drawGraph(DrawContext ctx) {
		int gx = x + GRAPH_X;
		int gy = y + GRAPH_Y;
		Gui.inset(ctx, gx - 2, gy - 2, GRAPH_W + 4, GRAPH_H + 4);
		int n = handler.sampleCount();
		double top = 1;
		for (int i = 0; i < n; i++) top = Math.max(top, Math.max(handler.demandSample(i), handler.capacitySample(i)));
		top = niceCeil(top * 1.15);

		// horizontal grid lines + labels
		for (int k = 0; k <= 4; k++) {
			int ly = gy + GRAPH_H - (int) Math.round(GRAPH_H * k / 4.0);
			ctx.fill(gx, ly, gx + GRAPH_W, ly + 1, 0x33FFFFFF);
		}
		ctx.drawText(textRenderer, Gui.fmt(top) + " MW", gx + 2, gy + 2, Gui.DARK_GRAY, false);

		int slots = PowerPoleBlockEntity.HISTORY;
		double step = GRAPH_W / (double) (slots - 1);
		int offset = slots - n; // newest sample is always at the right edge
		int prevCapY = -1;
		int prevX = -1;
		for (int i = 0; i < n; i++) {
			int px = gx + (int) Math.round((offset + i) * step);
			int nx = i + 1 < n ? gx + (int) Math.round((offset + i + 1) * step) : px + 1;
			double d = handler.demandSample(i);
			double c = handler.capacitySample(i);
			int dy = gy + GRAPH_H - (int) Math.round(GRAPH_H * Math.min(1, d / top));
			int cy = gy + GRAPH_H - (int) Math.round(GRAPH_H * Math.min(1, c / top));
			// consumption: filled area
			ctx.fill(px, dy, Math.max(nx, px + 1), gy + GRAPH_H, d > c ? 0x88FF4A3A : 0x66E8913A);
			ctx.fill(px, dy, Math.max(nx, px + 1), dy + 1, d > c ? Gui.RED : Gui.ORANGE);
			// capacity: stepped line
			ctx.fill(px, cy, Math.max(nx, px + 1), cy + 1, Gui.CYAN);
			if (prevX >= 0 && prevCapY != cy) ctx.fill(px, Math.min(prevCapY, cy), px + 1, Math.max(prevCapY, cy) + 1, Gui.CYAN);
			prevCapY = cy;
			prevX = px;
		}
		if (n == 0) {
			Gui.textCentered(ctx, textRenderer, Text.translatable("gui.ficsitcraft.pole.collecting"), gx + GRAPH_W / 2, gy + GRAPH_H / 2 - 4, Gui.GRAY);
		}
	}

	private static double niceCeil(double v) {
		double[] steps = {1, 2, 2.5, 5, 10};
		double mag = Math.pow(10, Math.floor(Math.log10(v)));
		for (double s : steps) if (s * mag >= v) return s * mag;
		return 10 * mag;
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		Text info = Text.translatable("gui.ficsitcraft.pole.lines", handler.get(P_LINES), handler.get(P_MAX_LINES));
		Gui.text(ctx, textRenderer, info, backgroundWidth - 8 - textRenderer.getWidth(info), 6, Gui.GRAY);
		// legend + counts
		int ly = GRAPH_Y - 12;
		ctx.fill(GRAPH_X, ly + 3, GRAPH_X + 6, ly + 5, Gui.CYAN);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.pole.capacity"), GRAPH_X + 9, ly, Gui.GRAY);
		ctx.fill(GRAPH_X + 70, ly + 3, GRAPH_X + 76, ly + 5, Gui.ORANGE);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.pole.consumption"), GRAPH_X + 79, ly, Gui.GRAY);
		Text counts = Text.translatable("gui.ficsitcraft.pole.counts", handler.get(P_GENERATORS), handler.get(P_CONSUMERS), handler.get(P_NODES));
		Gui.text(ctx, textRenderer, counts, 10, GRAPH_Y + GRAPH_H + 6, Gui.GRAY);
		Text axis = Text.translatable("gui.ficsitcraft.pole.axis");
		Gui.text(ctx, textRenderer, axis, backgroundWidth - 10 - textRenderer.getWidth(axis), GRAPH_Y + GRAPH_H + 6, Gui.DARK_GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
		// hover readout on the graph
		int gx = x + GRAPH_X;
		int gy = y + GRAPH_Y;
		int n = handler.sampleCount();
		if (n > 0 && mouseX >= gx && mouseX < gx + GRAPH_W && mouseY >= gy && mouseY < gy + GRAPH_H) {
			int slots = PowerPoleBlockEntity.HISTORY;
			int slot = (int) Math.round((mouseX - gx) / (GRAPH_W / (double) (slots - 1)));
			int i = slot - (slots - n);
			if (i >= 0 && i < n) {
				int secondsAgo = n - 1 - i;
				ctx.fill(mouseX, gy, mouseX + 1, gy + GRAPH_H, 0x66FFFFFF);
				ctx.drawTooltip(textRenderer, java.util.List.of(
						Text.translatable("gui.ficsitcraft.pole.seconds_ago", secondsAgo),
						Text.translatable("gui.ficsitcraft.pole.consumption").append(": " + Gui.fmt(handler.demandSample(i)) + " MW"),
						Text.translatable("gui.ficsitcraft.pole.capacity").append(": " + Gui.fmt(handler.capacitySample(i)) + " MW")),
						mouseX, mouseY);
			}
		}
	}
}
