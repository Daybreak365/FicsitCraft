package com.ficsitcraft.client.screen;

import com.ficsitcraft.fluid.SfFluid;
import com.ficsitcraft.screen.FluidMachineScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import static com.ficsitcraft.screen.FluidMachineScreenHandler.*;

/** Water Extractor / Pipeline Pump status panel. */
public class FluidMachineScreen extends HandledScreen<FluidMachineScreenHandler> {
	public FluidMachineScreen(FluidMachineScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 200;
		backgroundHeight = 120;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		SfFluid fluid = SfFluid.byOrdinal(handler.get(P_FLUID));
		double cap = handler.get(P_CAPACITY) / 100.0;
		double amt = handler.get(P_AMOUNT) / 100.0;
		// buffer tank
		ctx.fill(x + 10, y + 20, x + 30, y + 100, Gui.BORDER);
		int h = cap <= 0 ? 0 : (int) Math.round(78 * Math.min(1, amt / cap));
		if (h > 0) ctx.fill(x + 11, y + 99 - h, x + 29, y + 99, 0xFF000000 | fluid.color);
		// flow gauge
		double flow = handler.get(P_FLOW) / 10.0;
		Gui.inset(ctx, x + 38, y + 20, 154, 80);
		Gui.bar(ctx, x + 44, y + 62, 142, 8, flow / 300.0, Gui.CYAN);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		int f = handler.get(P_FLAGS);
		SfFluid fluid = SfFluid.byOrdinal(handler.get(P_FLUID));
		double flow = handler.get(P_FLOW) / 10.0;
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.fluid.fluid").append(": ").append(
				fluid == SfFluid.NONE ? Text.translatable("fluid.ficsitcraft.none") : fluid.displayName()), 44, 26, 0xFF000000 | (fluid == SfFluid.NONE ? 0xA0A4AA : fluid.color));
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.fluid.flow", Gui.fmt(flow)), 44, 38, Gui.WHITE);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.fluid.lift", handler.get(P_LIFT)), 44, 50, Gui.GRAY);

		Text status;
		int color;
		if ((f & F_NO_SOURCE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_source");
			color = Gui.RED;
		} else if ((f & F_FUSE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.fuse");
			color = Gui.RED;
		} else if ((f & F_POWERED) == 0 && (f & F_IDLE) == 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_power");
			color = Gui.RED;
		} else if ((f & F_WORKING) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.pumping");
			color = Gui.GREEN;
		} else {
			status = Text.translatable("gui.ficsitcraft.status.standby");
			color = Gui.GRAY;
		}
		Gui.text(ctx, textRenderer, status, 44, 76, color);
		Text power = Text.translatable("gui.ficsitcraft.power_usage", Gui.fmt(handler.get(P_POWER) / 10.0));
		Gui.text(ctx, textRenderer, power, 188 - textRenderer.getWidth(power), 76, Gui.CYAN);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.fluid.buffer",
				Gui.fmt(handler.get(P_AMOUNT) / 100.0), Gui.fmt(handler.get(P_CAPACITY) / 100.0)), 44, 88, Gui.DARK_GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
