package com.ficsitcraft.client.screen;

import com.ficsitcraft.block.MinerBlock;
import com.ficsitcraft.block.NodeType;
import com.ficsitcraft.block.Purity;
import com.ficsitcraft.blockentity.MinerBlockEntity;
import com.ficsitcraft.screen.MinerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public class MinerScreen extends HandledScreen<MinerScreenHandler> {
	public MinerScreen(MinerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 186;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	private MinerBlock block() {
		if (client != null && client.world != null && client.world.getBlockState(handler.getPos()).getBlock() instanceof MinerBlock m) return m;
		return null;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		Gui.panel(ctx, x, y, backgroundWidth, backgroundHeight);
		Gui.inset(ctx, x + 7, y + 17, 162, 22);
		int t = handler.getNodeType();
		if (t >= 0 && t < NodeType.values().length) {
			Gui.slot(ctx, x + 30, y + 44);
			ctx.drawItem(new ItemStack(NodeType.values()[t].resource()), x + 30, y + 44);
		}
		Slot out = handler.slots.get(0);
		Gui.slot(ctx, x + out.x, y + out.y);
		Gui.arrow(ctx, x + 102, y + 44, handler.getProgress());
		Gui.playerSlots(ctx, x + 8, y + 104);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		int t = handler.getNodeType();
		MinerBlock mb = block();
		if (t >= 0 && t < NodeType.values().length) {
			NodeType type = NodeType.values()[t];
			Purity purity = Purity.values()[Math.min(handler.getPurity(), Purity.values().length - 1)];
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.miner.node",
					Text.translatable("node.ficsitcraft." + type.id),
					Text.translatable("purity.ficsitcraft." + purity.asString())), 11, 21, Gui.ORANGE);
			double rate = mb == null ? 0 : mb.baseRate() * purity.multiplier;
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.miner.rate", Gui.fmt(rate)), 11, 30, Gui.GRAY);
		} else {
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.miner.no_node"), 11, 25, Gui.RED);
		}

		int st = handler.getStatus();
		Text status;
		int color;
		if ((st & MinerBlockEntity.STATUS_NO_NODE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_node");
			color = Gui.RED;
		} else if ((st & MinerBlockEntity.STATUS_FUSE) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.fuse");
			color = Gui.RED;
		} else if ((st & MinerBlockEntity.STATUS_OUTPUT_FULL) != 0) {
			status = Text.translatable("gui.ficsitcraft.status.output_full");
			color = Gui.YELLOW;
		} else if ((st & MinerBlockEntity.STATUS_POWERED) == 0) {
			status = Text.translatable("gui.ficsitcraft.status.no_power");
			color = Gui.RED;
		} else {
			status = Text.translatable("gui.ficsitcraft.status.working");
			color = Gui.GREEN;
		}
		Gui.text(ctx, textRenderer, status, 8, 76, color);
		if (mb != null) {
			Text power = Text.translatable("gui.ficsitcraft.power_usage", Gui.fmt(mb.powerMW()));
			Gui.text(ctx, textRenderer, power, 170 - textRenderer.getWidth(power), 76, Gui.CYAN);
		}
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
