package com.ficsitcraft.client.screen;

import com.ficsitcraft.blockentity.GeneratorBlockEntity;
import com.ficsitcraft.screen.GeneratorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import com.ficsitcraft.registry.ModItems;

public class GeneratorScreen extends HandledScreen<GeneratorScreenHandler> {
	private ButtonWidget resetButton;

	public GeneratorScreen(GeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 186;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.reset_fuse"), b -> {
			if (client != null && client.interactionManager != null)
				client.interactionManager.clickButton(handler.syncId, GeneratorScreenHandler.BUTTON_RESET_FUSE);
		}).dimensions(x + 104, y + 70, 66, 16).build());
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		if (resetButton != null) resetButton.active = (handler.getFlags() & GeneratorBlockEntity.FLAG_FUSE) != 0;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		Slot fuel = handler.slots.get(0);
		Gui.slot(ctx, x + fuel.x, y + fuel.y);
		if (!fuel.hasStack()) {
			ItemStack ghost = new ItemStack(handler.isCoal() ? Items.COAL : ModItems.SOLID_BIOFUEL);
			Gui.ghost(ctx, ghost, x + fuel.x, y + fuel.y);
		}
		// burn bar (vertical)
		double burn = handler.getBurnFraction();
		ctx.fill(x + 46, y + 40, x + 52, y + 64, Gui.BORDER);
		int h = (int) Math.round(22 * burn);
		if (h > 0) ctx.fill(x + 47, y + 63 - h, x + 51, y + 63, Gui.ORANGE);
		// water tank (coal generator)
		if (handler.isCoal()) {
			ctx.fill(x + 8, y + 20, x + 20, y + 64, Gui.BORDER);
			int wh = (int) Math.round(42 * handler.getWaterFraction());
			if (wh > 0) ctx.fill(x + 9, y + 63 - wh, x + 19, y + 63, 0xFF3A8CFF);
		}
		// grid usage bar
		Gui.inset(ctx, x + 60, y + 17, 110, 50);
		double cap = handler.getGridCapacity();
		double use = handler.getGridDemand();
		Gui.bar(ctx, x + 64, y + 54, 102, 8, cap <= 0 ? 0 : use / cap, use > cap ? Gui.RED : Gui.CYAN);
		Gui.playerSlots(ctx, x + 8, y + 104);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		int f = handler.getFlags();
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.grid"), 64, 21, Gui.ORANGE);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.grid.production", Gui.fmt(handler.getGridCapacity())), 64, 31, Gui.GRAY);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.grid.consumption", Gui.fmt(handler.getGridDemand())), 64, 41, Gui.GRAY);

		Text status;
		int color;
		if ((f & GeneratorBlockEntity.FLAG_FUSE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.fuse");
			color = Gui.RED;
		} else if ((f & GeneratorBlockEntity.FLAG_COAL) != 0 && (f & GeneratorBlockEntity.FLAG_WATER) == 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_water");
			color = Gui.RED;
		} else if ((f & GeneratorBlockEntity.FLAG_HAS_FUEL) == 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_fuel");
			color = Gui.YELLOW;
		} else if ((f & GeneratorBlockEntity.FLAG_RUNNING) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.generating", Gui.fmt(handler.getLoad() * 100));
			color = Gui.GREEN;
		} else {
			status = Text.translatable("gui.ficsitcraft.status.standby");
			color = Gui.GRAY;
		}
		Gui.text(ctx, textRenderer, status, 8, 74, color);
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
		if (handler.isCoal() && mouseX >= x + 8 && mouseX < x + 20 && mouseY >= y + 20 && mouseY < y + 64) {
			ctx.drawTooltip(textRenderer, Text.translatable("gui.ficsitcraft.water_tank",
					Gui.fmt(handler.getWaterFraction() * GeneratorBlockEntity.WATER_BUFFER), Gui.fmt(GeneratorBlockEntity.WATER_BUFFER),
					Gui.fmt(GeneratorBlockEntity.WATER_PER_MIN)), mouseX, mouseY);
		}
	}
}
