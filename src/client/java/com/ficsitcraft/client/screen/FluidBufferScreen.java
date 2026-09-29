package com.ficsitcraft.client.screen;

import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.screen.FluidBufferScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.ficsitcraft.screen.FluidBufferScreenHandler.*;

/** Fluid Buffer UI in the same style as the pipe inspector: a big tank gauge plus info cards. */
public class FluidBufferScreen extends HandledScreen<FluidBufferScreenHandler> {
	private static final int W = 300;
	private static final int H = 200;
	private static final int BLUE = 0xFF2F8FD8;
	private static final int CARD_TEXT = 0xFF55585D;
	private static final int TX = 22, TY = 30, TW = 70, TH = 158;

	private double shown = -1;

	public FluidBufferScreen(FluidBufferScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = W;
		backgroundHeight = H;
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.buffer.flush"), b -> {
			if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, BUTTON_FLUSH);
		}).dimensions(x + W - 16 - 90, y + H - 30, 90, 18).build());
	}

	private double fraction() {
		double cap = handler.get(P_CAPACITY) / 10.0;
		return cap <= 0 ? 0 : handler.get(P_AMOUNT) / 10.0 / cap;
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		double f = fraction();
		shown = shown < 0 ? f : shown + (f - shown) * 0.2;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		ctx.fill(x, y, x + W, y + H, 0xFF1A1B1D);
		ctx.fill(x + 1, y + 1, x + W - 1, y + 16, 0xFF2A2C2F);
		ctx.fill(x + 5, y + 20, x + W - 5, y + H - 5, 0xFF303235);
		ctx.fill(x + 6, y + 21, x + W - 6, y + H - 6, 0xFF3A3C40);
		float time = (client != null && client.world != null ? client.world.getTime() : 0) + delta;

		// ---- tank gauge: chrome frame, dark glass, fluid with a moving surface
		int tx = x + TX, ty = y + TY;
		ctx.fill(tx - 4, ty - 4, tx + TW + 4, ty + TH + 4, 0xFF2A2C2F);
		ctx.fillGradient(tx - 3, ty - 3, tx + TW + 3, ty + TH + 3, 0xFFE8EAEE, 0xFF8E939A);
		ctx.fill(tx - 1, ty - 1, tx + TW + 1, ty + TH + 1, 0xFF3A3D42);
		ctx.fill(tx, ty, tx + TW, ty + TH, 0xFF16181B);
		SfFluid fluid = handler.getFluid();
		double f = Math.max(0, Math.min(1, shown < 0 ? fraction() : shown));
		if (fluid != SfFluid.NONE && f > 0.002) {
			int top = 0xFF000000 | lighten(fluid.color, 0.6);
			int bottom = 0xFF000000 | lighten(fluid.color, 0.3);
			int surfaceCol = 0xFF000000 | lighten(fluid.color, 0.85);
			double level = ty + TH - TH * f;
			double flowRate = (handler.get(P_INFLOW) + handler.get(P_OUTFLOW)) / 10.0;
			double amp = 0.5 + Math.min(2.0, flowRate / 120.0);
			for (int i = 0; i < TW; i++) {
				double wave = amp * Math.sin(time * 0.16 + i * 0.12) + amp * 0.4 * Math.sin(time * 0.29 - i * 0.2);
				int s = (int) Math.round(level + wave);
				int y0 = Math.max(ty, s);
				if (y0 >= ty + TH) continue;
				ctx.fillGradient(tx + i, y0, tx + i + 1, ty + TH, top, bottom);
				if (s >= ty) ctx.fill(tx + i, y0, tx + i + 1, y0 + 1, surfaceCol);
			}
		}
		// glass highlight + level marks
		ctx.fill(tx + 6, ty + 4, tx + 10, ty + TH - 4, 0x30FFFFFF);
		for (int k = 1; k < 4; k++) {
			int my = ty + TH - TH * k / 4;
			ctx.fill(tx + TW - 12, my, tx + TW, my + 1, 0xAAFFFFFF);
		}

		// ---- right side cards
		int cx0 = x + 110;
		int cx1 = x + W - 16;
		// fluid card (dark)
		ctx.fill(cx0, y + 28, cx1, y + 98, 0xFF202225);
		ctx.fill(cx0 + 8, y + 64, cx1 - 8, y + 65, 0xFFBFC2C6);
		ItemStack icon = fluid == SfFluid.WATER ? new ItemStack(Items.WATER_BUCKET)
				: fluid == SfFluid.LAVA ? new ItemStack(Items.LAVA_BUCKET) : new ItemStack(ModBlocks.FLUID_BUFFER);
		int mid = (cx0 + cx1) / 2;
		ctx.fill(mid - 11, y + 31, mid + 11, y + 51, 0xFF4A4D52);
		ctx.drawItem(icon, mid - 8, y + 33);
		// flow card (white)
		ctx.fill(cx0, y + 104, cx1, y + 160, 0xFFF3F3F3);
		ctx.fill((cx0 + cx1) / 2, y + 110, (cx0 + cx1) / 2 + 1, y + 154, 0xFFC8C8C8);
		arrow(ctx, (cx0 + (cx0 + cx1) / 2) / 2 - 10, y + 125, true);
		arrow(ctx, ((cx0 + cx1) / 2 + cx1) / 2 - 10, y + 125, false);
	}

	/** Arrow into (inflow, pointing down) or out of (outflow, pointing up) the tank. */
	private void arrow(DrawContext ctx, int ax, int ay, boolean down) {
		int c = 0xFF5E6166;
		ctx.fill(ax + 7, down ? ay - 8 : ay - 2, ax + 13, down ? ay + 2 : ay + 8, c);
		for (int i = 0; i < 9; i++) {
			int yy = down ? ay + 2 + i : ay - 2 - i;
			ctx.fill(ax + 1 + i, yy, ax + 19 - i, yy + 1, c);
		}
	}

	private static int lighten(int rgb, double t) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return ((int) (r + (255 - r) * t) << 16) | ((int) (g + (255 - g) * t) << 8) | (int) (b + (255 - b) * t);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(6, 4, 14, 12, 0xFFE0E0E0);
		ctx.fill(7, 6, 13, 11, 0xFF2A2C2F);
		ctx.drawText(textRenderer, title, 19, 5, 0xFFFFFFFF, false);

		// tank percentage label
		int pct = (int) Math.round(fraction() * 100);
		String p = pct + "%";
		ctx.drawText(textRenderer, p, TX + TW / 2 - textRenderer.getWidth(p) / 2, TY + 6, 0xFFFFFFFF, true);

		int cx0 = 110, cx1 = W - 16, mid = (cx0 + cx1) / 2;
		SfFluid fluid = handler.getFluid();
		Text name = fluid == SfFluid.NONE ? Text.translatable("fluid.ficsitcraft.none") : fluid.displayName();
		centered(ctx, name, mid, 54, 0xFFFFFFFF);
		centered(ctx, Text.translatable("gui.ficsitcraft.buffer.stored"), mid, 70, 0xFFBFC2C6);
		String amt = Gui.fmt(handler.get(P_AMOUNT) / 10.0);
		String rest = "/" + Gui.fmt(handler.get(P_CAPACITY) / 10.0) + " m³";
		int w = textRenderer.getWidth(amt) + textRenderer.getWidth(rest);
		ctx.drawText(textRenderer, amt, mid - w / 2, 83, 0xFF5DB4F0, false);
		ctx.drawText(textRenderer, rest, mid - w / 2 + textRenderer.getWidth(amt), 83, 0xFFE6E7E9, false);

		int half = (cx0 + cx1) / 2;
		int lmid = (cx0 + half) / 2, rmid = (half + cx1) / 2;
		String unit = " m³/" + Text.translatable("gui.ficsitcraft.pipe.min").getString();
		centered(ctx, Text.translatable("gui.ficsitcraft.buffer.inflow"), lmid, 108, CARD_TEXT);
		centered(ctx, Text.translatable("gui.ficsitcraft.buffer.outflow"), rmid, 108, CARD_TEXT);
		valueUnit(ctx, Gui.fmt(handler.get(P_INFLOW) / 10.0), unit, lmid, 146);
		valueUnit(ctx, Gui.fmt(handler.get(P_OUTFLOW) / 10.0), unit, rmid, 146);
	}

	private void centered(DrawContext ctx, Text t, int cx, int yy, int color) {
		ctx.drawText(textRenderer, t, cx - textRenderer.getWidth(t) / 2, yy, color, false);
	}

	private void valueUnit(DrawContext ctx, String value, String unit, int cx, int yy) {
		int w = textRenderer.getWidth(value) + textRenderer.getWidth(unit);
		int sx = cx - w / 2;
		ctx.drawText(textRenderer, value, sx, yy, BLUE, false);
		ctx.drawText(textRenderer, unit, sx + textRenderer.getWidth(value), yy, CARD_TEXT, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
