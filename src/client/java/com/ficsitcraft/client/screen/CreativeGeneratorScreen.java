package com.ficsitcraft.client.screen;

import com.ficsitcraft.screen.CreativeGeneratorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.ficsitcraft.screen.CreativeGeneratorScreenHandler.*;

/**
 * Easter egg window of the Creative Generator: a pulsing energy core, the live grid numbers, an output switch, the fuse
 * reset and a rotating motivational message from FICSIT. Clicking the core changes the message.
 */
public class CreativeGeneratorScreen extends HandledScreen<CreativeGeneratorScreenHandler> {
	private static final int QUOTES = 8;
	private static final int CORE_X = 56, CORE_Y = 62, CORE_R = 34;

	private int quote = (int) (System.nanoTime() >>> 10 & 0xFFFF) % QUOTES;
	private int ticks;
	private ButtonWidget toggle;

	public CreativeGeneratorScreen(CreativeGeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 220;
		backgroundHeight = 156;
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.cg.reset"),
				b -> click(BUTTON_RESET_FUSE)).dimensions(x + 108, y + 92, 104, 16).build());
		toggle = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> click(BUTTON_TOGGLE)).dimensions(x + 108, y + 72, 104, 16).build());
	}

	private void click(int id) {
		if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		ticks++;
		if (toggle != null) {
			toggle.setMessage(Text.translatable(handler.get(P_ENABLED) == 1 ? "gui.ficsitcraft.cg.on" : "gui.ficsitcraft.cg.off"));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double dx = mouseX - (x + CORE_X), dy = mouseY - (y + CORE_Y);
		if (button == 0 && dx * dx + dy * dy <= CORE_R * CORE_R) {
			quote = (quote + 1) % QUOTES;
			if (client != null && client.player != null) client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.7f + (quote % 4) * 0.2f);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static void disc(DrawContext ctx, int cx, int cy, double r, int argb) {
		int ir = (int) Math.ceil(r);
		for (int dy = -ir; dy <= ir; dy++) {
			double w = Math.sqrt(Math.max(0, r * r - dy * dy));
			if (w < 0.5) continue;
			ctx.fill(cx - (int) Math.round(w), cy + dy, cx + (int) Math.round(w), cy + dy + 1, argb);
		}
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		boolean on = handler.get(P_ENABLED) == 1;
		boolean fuse = handler.get(P_FUSE) == 1;
		double time = (ticks + delta) / 20.0;
		int cx = x + CORE_X, cy = y + CORE_Y;
		ctx.fill(cx - CORE_R - 4, cy - CORE_R - 4, cx + CORE_R + 4, cy + CORE_R + 4, Gui.BORDER);
		ctx.fill(cx - CORE_R - 3, cy - CORE_R - 3, cx + CORE_R + 3, cy + CORE_R + 3, 0xFF120A22);
		if (on && !fuse) {
			double pulse = 0.5 + 0.5 * Math.sin(time * 3.0);
			// halo rings breathing outwards
			for (int i = 5; i >= 0; i--) {
				double r = CORE_R * (0.35 + 0.65 * ((i + time * 0.8) % 6) / 6.0);
				int a = (int) (60 * (1 - (r / CORE_R)));
				disc(ctx, cx, cy, r, (Math.max(8, a) << 24) | 0xB04CFF);
			}
			disc(ctx, cx, cy, 14 + pulse * 3, 0xFF6A2BB8);
			disc(ctx, cx, cy, 9 + pulse * 2, 0xFF5FE0FF);
			disc(ctx, cx, cy, 4 + pulse, 0xFFFFFFFF);
			// orbiting sparks
			for (int i = 0; i < 6; i++) {
				double ang = time * (1.4 + i * 0.13) + i * 1.047;
				double rr = 18 + (i % 3) * 5;
				int sx = cx + (int) Math.round(Math.cos(ang) * rr), sy = cy + (int) Math.round(Math.sin(ang) * rr * 0.8);
				ctx.fill(sx - 1, sy - 1, sx + 1, sy + 1, i % 2 == 0 ? 0xFFFFF2A0 : 0xFFB04CFF);
			}
		} else {
			disc(ctx, cx, cy, 12, fuse ? 0xFF5A1A1A : 0xFF33373D);
			disc(ctx, cx, cy, 5, fuse ? 0xFFFF5A5A : 0xFF5E636B);
		}
		Gui.inset(ctx, x + 8, y + 116, backgroundWidth - 16, 32);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, 0xFFD9A8FF);
		boolean on = handler.get(P_ENABLED) == 1;
		boolean fuse = handler.get(P_FUSE) == 1;
		Text out = Text.translatable("gui.ficsitcraft.cg.output", on ? "3000" : "0");
		Gui.text(ctx, textRenderer, out, 108, 22, on ? Gui.CYAN : Gui.DARK_GRAY);
		double demand = handler.get(P_DEMAND) / 10.0;
		double cap = handler.get(P_CAPACITY) / 10.0;
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.cg.load", Gui.fmt(demand), Gui.fmt(cap)), 108, 34, Gui.WHITE);
		Gui.bar(ctx, 108, 46, 104, 6, cap <= 0 ? 0 : demand / cap, demand > cap ? Gui.RED : Gui.GREEN);
		Gui.text(ctx, textRenderer, Text.translatable(fuse ? "gui.ficsitcraft.cg.fuse_blown" : "gui.ficsitcraft.cg.fuse_ok"), 108, 56, fuse ? Gui.RED : Gui.GREEN);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.cg.counts", handler.get(P_GENERATORS), handler.get(P_CONSUMERS)), 108, 110 - 1, Gui.GRAY);
		// the message from FICSIT
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("gui.ficsitcraft.cg.quote" + quote), backgroundWidth - 24);
		for (int i = 0; i < Math.min(3, lines.size()); i++) ctx.drawText(textRenderer, lines.get(i), 12, 120 + i * 9, 0xFFFFD84A, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
