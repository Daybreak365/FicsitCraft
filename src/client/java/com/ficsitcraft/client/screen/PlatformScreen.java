package com.ficsitcraft.client.screen;

import com.ficsitcraft.blockentity.RailBuildingBlockEntity;
import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.screen.PlatformScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.ficsitcraft.screen.PlatformScreenHandler.*;

/** Freight / Fluid Freight Platform: load-unload switch, what the platform is doing right now, cargo and tank. */
public class PlatformScreen extends HandledScreen<PlatformScreenHandler> {
	private static final int BTN_Y = 18, BTN_W = 78, BTN_H = 16;

	public PlatformScreen(PlatformScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = handler.isFluid() ? 152 : 248;
		playerInventoryTitleX = SLOT_X;
		playerInventoryTitleY = INV_Y - 10;
	}

	private boolean unload() {
		return handler.get(P_UNLOAD) == 1;
	}

	private int buttonX(boolean unloadButton) {
		return x + 8 + (unloadButton ? BTN_W + 4 : 0);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseY >= y + BTN_Y && mouseY < y + BTN_Y + BTN_H) {
			for (int i = 0; i < 2; i++) {
				int bx = buttonX(i == 1);
				if (mouseX >= bx && mouseX < bx + BTN_W) {
					if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, i);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private Text statusText() {
		int st = handler.get(P_STATUS);
		String suffix = handler.isFluid() ? "_fluid" : "_item";
		return switch (st) {
			case RailBuildingBlockEntity.ST_NO_POWER -> Text.translatable("gui.ficsitcraft.platform.status_no_power");
			case RailBuildingBlockEntity.ST_NO_CAR -> Text.translatable("gui.ficsitcraft.platform.status_no_car" + suffix);
			case RailBuildingBlockEntity.ST_WORKING -> Text.translatable(unload() ? "gui.ficsitcraft.platform.status_unloading" : "gui.ficsitcraft.platform.status_loading");
			case RailBuildingBlockEntity.ST_DONE -> Text.translatable(unload() ? "gui.ficsitcraft.platform.status_done_unload" : "gui.ficsitcraft.platform.status_done_load");
			case RailBuildingBlockEntity.ST_WAIT_INPUT -> Text.translatable("gui.ficsitcraft.platform.status_wait" + suffix);
			case RailBuildingBlockEntity.ST_PLATFORM_FULL -> Text.translatable("gui.ficsitcraft.platform.status_full" + suffix);
			default -> Text.translatable("gui.ficsitcraft.platform.status_mismatch");
		};
	}

	private int statusColor() {
		return switch (handler.get(P_STATUS)) {
			case RailBuildingBlockEntity.ST_NO_POWER, RailBuildingBlockEntity.ST_MISMATCH -> Gui.RED;
			case RailBuildingBlockEntity.ST_NO_CAR -> Gui.GRAY;
			case RailBuildingBlockEntity.ST_WORKING -> Gui.CYAN;
			case RailBuildingBlockEntity.ST_DONE -> Gui.GREEN;
			default -> Gui.YELLOW;
		};
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		// load / unload switch
		for (int i = 0; i < 2; i++) {
			boolean selected = (i == 1) == unload();
			int bx = buttonX(i == 1);
			int by = y + BTN_Y;
			boolean hover = mouseX >= bx && mouseX < bx + BTN_W && mouseY >= by && mouseY < by + BTN_H;
			ctx.fill(bx, by, bx + BTN_W, by + BTN_H, Gui.BORDER);
			ctx.fill(bx + 1, by + 1, bx + BTN_W - 1, by + BTN_H - 1, selected ? 0xFF6A4A24 : hover ? 0xFF3C4148 : Gui.BG_LIGHT);
			if (selected) ctx.fill(bx + 1, by + BTN_H - 3, bx + BTN_W - 1, by + BTN_H - 1, Gui.ORANGE);
			Text label = Text.translatable(i == 1 ? "gui.ficsitcraft.platform.unload" : "gui.ficsitcraft.platform.load");
			Gui.textCentered(ctx, textRenderer, label, bx + BTN_W / 2, by + 4, selected ? Gui.WHITE : Gui.GRAY);
		}
		if (handler.isFluid()) {
			drawTank(ctx);
		} else {
			Gui.inset(ctx, x + SLOT_X - 2, y + SLOT_Y - 2, 9 * 18 + 3, 4 * 18 + 3);
			Gui.playerSlots(ctx, x + SLOT_X, y + INV_Y);
			for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) Gui.slot(ctx, x + SLOT_X + col * 18, y + SLOT_Y + row * 18);
		}
	}

	private void drawTank(DrawContext ctx) {
		SfFluid fluid = SfFluid.byOrdinal(handler.get(P_FLUID));
		double amount = handler.get(P_TANK) / 10.0;
		int gx = x + 12, gy = y + 56, gw = 26, gh = 80;
		ctx.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, Gui.SLOT_EDGE);
		ctx.fill(gx, gy, gx + gw, gy + gh, Gui.SLOT);
		int h = (int) Math.round(gh * Math.min(1, amount / RailBuildingBlockEntity.TANK));
		if (h > 0) ctx.fill(gx, gy + gh - h, gx + gw, gy + gh, 0xFF000000 | fluid.color);
		for (int i = 1; i < 4; i++) ctx.fill(gx, gy + gh * i / 4, gx + 5, gy + gh * i / 4 + 1, Gui.GRAY);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		// what the current mode does
		String descKey = (handler.isFluid() ? "gui.ficsitcraft.platform.desc_fluid_" : "gui.ficsitcraft.platform.desc_item_") + (unload() ? "unload" : "load");
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(descKey), backgroundWidth - 16);
		int ly = 38;
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			ctx.drawText(textRenderer, lines.get(i), 8, ly, Gui.GRAY, false);
			ly += 9;
		}
		if (handler.isFluid()) {
			SfFluid fluid = SfFluid.byOrdinal(handler.get(P_FLUID));
			double amount = handler.get(P_TANK) / 10.0;
			int tx = 46;
			Text name = amount > 0.05 ? fluid.displayName() : Text.translatable("gui.ficsitcraft.empty");
			Gui.text(ctx, textRenderer, name, tx, 58, amount > 0.05 ? 0xFF000000 | fluid.color : Gui.GRAY);
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.platform.tank", Gui.fmt(amount), Gui.fmt(RailBuildingBlockEntity.TANK)), tx, 70, Gui.WHITE);
			if (handler.get(P_CAR_PCT) >= 0) {
				double carAmount = handler.get(P_CAR_AMOUNT) / 10.0;
				SfFluid cf = SfFluid.byOrdinal(handler.get(P_CAR_FLUID));
				Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.platform.car_fluid", Gui.fmt(carAmount), "800"), tx, 88, Gui.CYAN);
				if (carAmount > 0.05) Gui.text(ctx, textRenderer, cf.displayName(), tx, 99, 0xFF000000 | cf.color);
				Gui.bar(ctx, tx, 111, 118, 6, carAmount / 800.0, Gui.CYAN);
			} else {
				Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.platform.no_car_short"), tx, 88, Gui.DARK_GRAY);
			}
			drawStatus(ctx, tx, 124, backgroundWidth - tx - 6);
		} else {
			drawStatus(ctx, 8, 134, backgroundWidth - 16);
			if (handler.get(P_CAR_PCT) >= 0) {
				Text car = Text.translatable("gui.ficsitcraft.platform.car_items", handler.get(P_CAR_PCT));
				Gui.text(ctx, textRenderer, car, backgroundWidth - 8 - textRenderer.getWidth(car), 6, Gui.CYAN);
			}
			Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
		}
	}

	private void drawStatus(DrawContext ctx, int sx, int sy, int maxWidth) {
		List<OrderedText> lines = textRenderer.wrapLines(statusText(), Math.max(60, maxWidth));
		int color = statusColor();
		for (int i = 0; i < Math.min(2, lines.size()); i++) ctx.drawText(textRenderer, lines.get(i), sx, sy + i * 9, color, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
