package com.ficsitcraft.client.screen;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.screen.PipeScreenHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static com.ficsitcraft.screen.PipeScreenHandler.*;

/**
 * Pipeline inspector, modelled on Satisfactory's pipe UI:
 * flow dial (left), glass sphere with the fill level (right), a white "Flow / Max flow" card and a dark card with
 * the fluid and the volume of the whole pipe run.
 */
public class PipeScreen extends HandledScreen<PipeScreenHandler> {
	private static final Identifier TEX = FicsitCraft.id("textures/gui/pipe_inspector.png");
	private static final int W = 300;
	private static final int H = 212;
	private static final int BLUE = 0xFF2F8FD8;
	private static final int CARD_TEXT = 0xFF55585D;

	private double shownFlow;
	private double shownFill;
	private boolean first = true;

	public PipeScreen(PipeScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = W;
		backgroundHeight = H;
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		double flow = handler.get(P_FLOW) / 10.0;
		double cap = handler.get(P_CAPACITY) / 10.0;
		double fill = cap <= 0 ? 0 : handler.get(P_AMOUNT) / 10.0 / cap;
		if (first) {
			shownFlow = flow;
			shownFill = fill;
			first = false;
		}
		shownFlow += (flow - shownFlow) * 0.25;
		shownFill += (fill - shownFill) * 0.2;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		// window
		ctx.fill(x, y, x + W, y + H, 0xFF1A1B1D);
		ctx.fill(x + 1, y + 1, x + W - 1, y + 16, 0xFF2A2C2F);
		ctx.fill(x + 5, y + 20, x + W - 5, y + H - 5, 0xFF303235);
		ctx.fill(x + 6, y + 21, x + W - 6, y + H - 6, 0xFF3A3C40);

		// dial and sphere wells
		ctx.fill(x + 16, y + 26, x + W - 16, y + 136, 0xFF2E3033);
		ctx.fill(x + 17, y + 27, x + W - 17, y + 135, 0xFF36383C);

		int gx = x + 36;
		int gy = y + 31;
		int sx = x + W - 36 - 100;
		int sy = y + 31;
		float time = (client != null && client.world != null ? client.world.getTime() : 0) + delta;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		// ---- flow dial
		ctx.drawTexture(TEX, gx, gy, 0, 0, 100, 100, 256, 128);
		drawNeedle(ctx, gx + 50, gy + 50, handler.get(P_MAX_FLOW) <= 0 ? 0 : shownFlow / handler.get(P_MAX_FLOW));
		// ---- fluid sphere: fluid first, then the glass/rim overlay
		drawSphereFluid(ctx, sx + 50, sy + 50, 42, time);
		ctx.drawTexture(TEX, sx, sy, 100, 0, 100, 100, 256, 128);
		RenderSystem.disableBlend();

		// ---- bottom cards
		int cy = y + 142;
		int ch = 62;
		// white card: flow | max flow
		ctx.fill(x + 16, cy, x + 150, cy + ch, 0xFFF3F3F3);
		ctx.fill(x + 83, cy + 6, x + 84, cy + ch - 6, 0xFFC8C8C8);
		arrow(ctx, x + 49 - 10, cy + 22, false);
		arrow(ctx, x + 116 - 15, cy + 22, true);
		// dark card: fluid
		ctx.fill(x + 156, cy, x + W - 16, cy + ch, 0xFF202225);
		ctx.fill(x + 164, cy + 33, x + W - 24, cy + 34, 0xFFBFC2C6);
		SfFluid fluid = handler.getFluid();
		ItemStack icon = fluid == SfFluid.WATER ? new ItemStack(Items.WATER_BUCKET)
				: fluid == SfFluid.LAVA ? new ItemStack(Items.LAVA_BUCKET) : new ItemStack(ModBlocks.PIPELINE_MK1);
		int mid = x + 156 + (W - 16 - 156) / 2;
		ctx.fill(mid - 11, cy + 2, mid + 11, cy + 22, 0xFF4A4D52);
		ctx.drawItem(icon, mid - 8, cy + 4);
	}

	/** Dial needle: 0 at -135° (bottom-left), full scale at +135° (bottom-right). */
	private void drawNeedle(DrawContext ctx, int cx, int cy, double frac) {
		frac = Math.max(0, Math.min(1.05, frac));
		double ang = Math.toRadians(-135 + 270 * frac);
		double dx = Math.sin(ang);
		double dy = -Math.cos(ang);
		for (double t = -6; t <= 33; t += 0.5) {
			int px = (int) Math.round(cx + dx * t);
			int py = (int) Math.round(cy + dy * t);
			int w = t < 20 ? 1 : 0;
			ctx.fill(px - w, py - w, px + 1 + w, py + 1 + w, 0xFF45484D);
		}
		for (int yy = -5; yy <= 5; yy++) {
			int half = (int) Math.round(Math.sqrt(25 - yy * yy));
			ctx.fill(cx - half, cy + yy, cx + half + 1, cy + yy + 1, yy < -1 ? 0xFF6A6E74 : 0xFF3E4146);
		}
	}

	/** Fills the sphere up to the current level; the surface ripples more when fluid is flowing (sloshing). */
	private void drawSphereFluid(DrawContext ctx, int cx, int cy, int r, float time) {
		// dark empty interior
		for (int dx = -r; dx <= r; dx++) {
			int half = (int) Math.floor(Math.sqrt(r * r - dx * dx));
			ctx.fill(cx + dx, cy - half, cx + dx + 1, cy + half, 0xFF16181B);
		}
		SfFluid fluid = handler.getFluid();
		double fill = Math.max(0, Math.min(1, shownFill));
		if (fluid == SfFluid.NONE || fill < 0.002) return;
		int base = fluid.color;
		int top = 0xFF000000 | lighten(base, 0.62);
		int bottom = 0xFF000000 | lighten(base, 0.38);
		int surfaceColor = 0xFF000000 | lighten(base, 0.85);
		double maxFlow = Math.max(1, handler.get(P_MAX_FLOW));
		double amp = 0.6 + Math.min(2.2, shownFlow / maxFlow * 6);
		double level = cy + r - 2.0 * r * fill;
		for (int dx = -r; dx <= r; dx++) {
			int half = (int) Math.floor(Math.sqrt(r * r - dx * dx));
			int colTop = cy - half;
			int colBottom = cy + half;
			double wave = amp * Math.sin(time * 0.18 + dx * 0.09) + amp * 0.4 * Math.sin(time * 0.31 - dx * 0.17);
			int surf = (int) Math.round(level + wave);
			int y0 = Math.max(colTop, surf);
			if (y0 >= colBottom) continue;
			ctx.fillGradient(cx + dx, y0, cx + dx + 1, colBottom, top, bottom);
			if (surf >= colTop) ctx.fill(cx + dx, y0, cx + dx + 1, y0 + 1, surfaceColor);
		}
	}

	private static int lighten(int rgb, double t) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		r = (int) (r + (255 - r) * t);
		g = (int) (g + (255 - g) * t);
		b = (int) (b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** Grey arrow icon (single for flow, double for max flow). */
	private void arrow(DrawContext ctx, int ax, int ay, boolean dbl) {
		int n = dbl ? 2 : 1;
		for (int k = 0; k < n; k++) {
			int ox = ax + k * 16;
			ctx.fill(ox, ay - 3, ox + (dbl ? 6 : 12), ay + 3, 0xFF5E6166);
			int tx = ox + (dbl ? 6 : 12);
			for (int i = 0; i < 9; i++) ctx.fill(tx + i, ay - 8 + i, tx + i + 1, ay + 8 - i, 0xFF5E6166);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		// title bar
		ctx.fill(6, 4, 14, 12, 0xFFE0E0E0);
		ctx.fill(7, 6, 13, 11, 0xFF2A2C2F);
		ctx.drawText(textRenderer, title, 19, 5, 0xFFFFFFFF, false);

		// dial labels
		Text flowLabel = Text.translatable("gui.ficsitcraft.pipe.flow");
		ctx.drawText(textRenderer, flowLabel, 86 - textRenderer.getWidth(flowLabel) / 2, 56, 0xFF9A9DA2, false);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(86, 104, 0);
		ctx.getMatrices().scale(0.9f, 0.9f, 1f);
		String brand = "FICSIT";
		ctx.drawText(textRenderer, brand, -textRenderer.getWidth(brand) / 2, 0, 0xFF6E7176, false);
		ctx.getMatrices().pop();

		int cy = 142;
		// white card texts
		centered(ctx, Text.translatable("gui.ficsitcraft.pipe.flow"), 49, cy + 5, CARD_TEXT);
		centered(ctx, Text.translatable("gui.ficsitcraft.pipe.max_flow"), 116, cy + 5, CARD_TEXT);
		valueUnit(ctx, Gui.fmt(handler.get(P_FLOW) / 10.0), " m³/" + Text.translatable("gui.ficsitcraft.pipe.min").getString(), 49, cy + 44);
		valueUnit(ctx, Integer.toString(handler.get(P_MAX_FLOW)), " m³/" + Text.translatable("gui.ficsitcraft.pipe.min").getString(), 116, cy + 44);

		// dark card texts
		int mid = 156 + (W - 16 - 156) / 2;
		SfFluid fluid = handler.getFluid();
		Text name = fluid == SfFluid.NONE ? Text.translatable("fluid.ficsitcraft.none") : fluid.displayName();
		centered(ctx, name, mid, cy + 23, 0xFFFFFFFF);
		centered(ctx, Text.translatable("gui.ficsitcraft.pipe.volume"), mid, cy + 37, 0xFFBFC2C6);
		String amt = Gui.fmt(handler.get(P_AMOUNT) / 10.0);
		String rest = "/" + Gui.fmt(handler.get(P_CAPACITY) / 10.0) + " m³";
		int w = textRenderer.getWidth(amt) + textRenderer.getWidth(rest);
		int sx = mid - w / 2;
		ctx.drawText(textRenderer, amt, sx, cy + 49, 0xFF5DB4F0, false);
		ctx.drawText(textRenderer, rest, sx + textRenderer.getWidth(amt), cy + 49, 0xFFE6E7E9, false);
	}

	private void centered(DrawContext ctx, Text t, int cx, int y, int color) {
		ctx.drawText(textRenderer, t, cx - textRenderer.getWidth(t) / 2, y, color, false);
	}

	private void valueUnit(DrawContext ctx, String value, String unit, int cx, int y) {
		int w = textRenderer.getWidth(value) + textRenderer.getWidth(unit);
		int sx = cx - w / 2;
		ctx.drawText(textRenderer, value, sx, y, BLUE, false);
		ctx.drawText(textRenderer, unit, sx + textRenderer.getWidth(value), y, CARD_TEXT, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
