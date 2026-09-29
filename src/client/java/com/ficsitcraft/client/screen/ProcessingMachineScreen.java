package com.ficsitcraft.client.screen;

import com.ficsitcraft.blockentity.ProcessingMachineBlockEntity;
import com.ficsitcraft.block.ProcessingMachineBlock;
import com.ficsitcraft.data.MachineType;
import com.ficsitcraft.data.SfRecipe;
import com.ficsitcraft.data.Stack;
import com.ficsitcraft.screen.ProcessingMachineScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class ProcessingMachineScreen extends HandledScreen<ProcessingMachineScreenHandler> {
	public ProcessingMachineScreen(ProcessingMachineScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 186;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> click(ProcessingMachineScreenHandler.BUTTON_PREV))
				.dimensions(x + 7, y + 17, 14, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> click(ProcessingMachineScreenHandler.BUTTON_NEXT))
				.dimensions(x + 155, y + 17, 14, 14).build());
	}

	private void click(int id) {
		if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
	}

	private MachineType machineType() {
		if (client != null && client.world != null
				&& client.world.getBlockState(handler.getPos()).getBlock() instanceof ProcessingMachineBlock b) {
			return b.getMachineType();
		}
		return MachineType.CONSTRUCTOR;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		Gui.inset(ctx, x + 22, y + 17, 132, 14);
		SfRecipe r = handler.getRecipe();
		long time = client != null && client.world != null ? client.world.getTime() : 0;
		for (int i = 0; i < ProcessingMachineBlockEntity.INPUTS; i++) {
			Slot s = handler.slots.get(i);
			if (!s.isEnabled()) continue;
			Gui.slot(ctx, x + s.x, y + s.y);
			if (!s.hasStack() && r != null) Gui.ghost(ctx, r.inputs().get(i).ing().displayStack(time), x + s.x, y + s.y);
		}
		Slot out = handler.slots.get(ProcessingMachineBlockEntity.OUTPUT);
		Gui.slot(ctx, x + out.x, y + out.y);
		if (!out.hasStack() && r != null) Gui.ghost(ctx, r.outputStack(), x + out.x, y + out.y);
		double frac = r == null ? 0 : handler.getProgress() / (double) r.timeTicks();
		Gui.arrow(ctx, x + 102, y + 44, frac);
		Gui.playerSlots(ctx, x + 8, y + 104);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		SfRecipe r = handler.getRecipe();
		Text name = r == null ? Text.translatable("gui.ficsitcraft.no_recipe") : r.output().getName();
		Gui.textCentered(ctx, textRenderer, name, 88, 20, r == null ? Gui.GRAY : Gui.ORANGE);

		// input amounts under the slots
		if (r != null) {
			for (int i = 0; i < r.inputs().size(); i++) {
				Slot s = handler.slots.get(i);
				Gui.textCentered(ctx, textRenderer, Text.literal(Gui.fmt(r.inputPerMinute(i)) + "/m"), s.x + 8, s.y + 19, Gui.GRAY);
			}
			Slot out = handler.slots.get(ProcessingMachineBlockEntity.OUTPUT);
			Gui.textCentered(ctx, textRenderer, Text.literal(Gui.fmt(r.outputPerMinute()) + "/m"), out.x + 8, out.y + 19, Gui.GRAY);
		}

		int st = handler.getStatus();
		Text status;
		int color;
		if (r == null) {
			status = Text.translatable("gui.ficsitcraft.status.select_recipe");
			color = Gui.GRAY;
		} else if ((st & ProcessingMachineBlockEntity.STATUS_FUSE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.fuse");
			color = Gui.RED;
		} else if ((st & ProcessingMachineBlockEntity.STATUS_OUTPUT_FULL) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.output_full");
			color = Gui.YELLOW;
		} else if ((st & ProcessingMachineBlockEntity.STATUS_NO_INPUT) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_input");
			color = Gui.YELLOW;
		} else if ((st & ProcessingMachineBlockEntity.STATUS_POWERED) == 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_power");
			color = Gui.RED;
		} else {
			status = Text.translatable("gui.ficsitcraft.status.working");
			color = Gui.GREEN;
		}
		Gui.text(ctx, textRenderer, status, 8, 76, color);
		Text power = Text.translatable("gui.ficsitcraft.power_usage", Gui.fmt(machineType().powerMW));
		Gui.text(ctx, textRenderer, power, 170 - textRenderer.getWidth(power), 76, Gui.CYAN);
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
		// tooltip for ghost ingredients
		SfRecipe r = handler.getRecipe();
		if (r == null || focusedSlot == null || focusedSlot.hasStack()) return;
		int idx = handler.slots.indexOf(focusedSlot);
		if (idx < 0 || idx >= r.inputs().size()) return;
		Stack in = r.inputs().get(idx);
		List<Text> lines = new ArrayList<>();
		lines.add(in.ing().name().copy().formatted(Formatting.GOLD));
		lines.add(Text.translatable("gui.ficsitcraft.per_cycle", in.count(), Gui.fmt(r.timeTicks() / 20.0)).formatted(Formatting.GRAY));
		ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
	}
}
