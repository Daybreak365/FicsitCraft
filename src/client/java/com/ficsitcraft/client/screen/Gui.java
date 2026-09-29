package com.ficsitcraft.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** FICSIT-styled flat GUI drawing helpers (no textures needed). */
public final class Gui {
	public static final int BG = 0xFF26292E;
	public static final int BG_LIGHT = 0xFF33373D;
	public static final int BORDER = 0xFF16181B;
	public static final int ORANGE = 0xFFFA9549;
	public static final int ORANGE_DARK = 0xFFB8601F;
	public static final int SLOT = 0xFF1B1D21;
	public static final int SLOT_EDGE = 0xFF4A4F57;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFA0A4AA;
	public static final int DARK_GRAY = 0xFF5E636B;
	public static final int RED = 0xFFFF5A5A;
	public static final int GREEN = 0xFF6BE675;
	public static final int YELLOW = 0xFFFFD84A;
	public static final int CYAN = 0xFF5FD7FF;

	private Gui() {
	}

	public static void panel(DrawContext ctx, int x, int y, int w, int h) {
		ctx.fill(x, y, x + w, y + h, BORDER);
		ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
		// orange FICSIT header stripe
		ctx.fill(x + 1, y + 1, x + w - 1, y + 3, ORANGE);
	}

	public static void inset(DrawContext ctx, int x, int y, int w, int h) {
		ctx.fill(x, y, x + w, y + h, SLOT_EDGE);
		ctx.fill(x + 1, y + 1, x + w, y + h, BORDER);
		ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG_LIGHT);
	}

	/** Slot background; (x, y) is the item position (slot.x, slot.y) in screen coords. */
	public static void slot(DrawContext ctx, int x, int y) {
		ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
		ctx.fill(x - 1, y - 1, x + 16, y + 16, BORDER);
		ctx.fill(x, y, x + 16, y + 16, SLOT);
	}

	public static void playerSlots(DrawContext ctx, int x, int y) {
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++) slot(ctx, x + col * 18, y + row * 18);
		for (int col = 0; col < 9; col++) slot(ctx, x + col * 18, y + 58);
	}

	public static void bar(DrawContext ctx, int x, int y, int w, int h, double fraction, int color) {
		ctx.fill(x, y, x + w, y + h, BORDER);
		int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
		if (fw > 0) ctx.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
	}

	/** A right-pointing progress arrow. */
	public static void arrow(DrawContext ctx, int x, int y, double fraction) {
		int w = 22;
		ctx.fill(x, y + 5, x + w - 6, y + 11, DARK_GRAY);
		for (int i = 0; i < 8; i++) ctx.fill(x + w - 8 + i, y + i, x + w - 7 + i, y + 16 - i, DARK_GRAY);
		int fw = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
		if (fw > 0) {
			ctx.fill(x, y + 5, x + Math.min(fw, w - 6), y + 11, ORANGE);
			for (int i = 0; i < 8 && w - 8 + i < fw; i++) ctx.fill(x + w - 8 + i, y + i, x + w - 7 + i, y + 16 - i, ORANGE);
		}
	}

	/** Draws a "ghost" item (required ingredient) in an empty slot. */
	public static void ghost(DrawContext ctx, ItemStack stack, int x, int y) {
		ctx.drawItem(stack, x, y);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 200);
		ctx.fill(x, y, x + 16, y + 16, 0xA01B1D21);
		ctx.getMatrices().pop();
	}

	public static void text(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color) {
		ctx.drawText(tr, text, x, y, color, false);
	}

	public static void textCentered(DrawContext ctx, TextRenderer tr, Text text, int cx, int y, int color) {
		ctx.drawText(tr, text, cx - tr.getWidth(text) / 2, y, color, false);
	}

	public static String fmt(double v) {
		if (Math.abs(v - Math.round(v)) < 0.05) return Long.toString(Math.round(v));
		return String.format("%.1f", v);
	}
}
