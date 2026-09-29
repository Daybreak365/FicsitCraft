package com.ficsitcraft.client.hud;

import com.ficsitcraft.network.PowerEventPayload;
import com.ficsitcraft.registry.ModBlocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Top-right notification shown when a nearby grid's fuse blows or power is restored. */
public class PowerToast implements Toast {
	private static final long DURATION = 6000L;

	private final boolean blown;
	private final Text title;
	private final Text detail;

	public PowerToast(PowerEventPayload p) {
		this.blown = p.kind() == PowerEventPayload.FUSE_BLOWN;
		this.title = Text.translatable(blown ? "toast.ficsitcraft.fuse_blown" : "toast.ficsitcraft.power_restored");
		this.detail = blown
				? Text.translatable("toast.ficsitcraft.fuse_blown.detail", fmt(p.demand()), fmt(p.capacity()))
				: Text.translatable("toast.ficsitcraft.power_restored.detail", fmt(p.demand()), fmt(p.capacity()));
	}

	private static String fmt(float v) {
		return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format("%.1f", v);
	}

	@Override
	public Visibility draw(DrawContext ctx, ToastManager manager, long startTime) {
		int w = getWidth();
		int h = getHeight();
		int accent = blown ? 0xFFFF4A3A : 0xFF6BE675;
		ctx.fill(0, 0, w, h, 0xF0181A1E);
		ctx.fill(0, 0, 3, h, accent);
		// blinking stripe while blown
		if (!blown || (startTime / 400) % 2 == 0) ctx.fill(3, 0, w, 2, accent);
		ctx.fill(3, h - 1, w, h, 0xFF2E3136);
		ctx.drawItem(new ItemStack(blown ? ModBlocks.COAL_GENERATOR : ModBlocks.POWER_POLE_MK1), 8, 8);
		ctx.drawText(manager.getClient().textRenderer, title, 30, 7, accent, false);
		ctx.drawText(manager.getClient().textRenderer, detail, 30, 18, 0xFFD8DADD, false);
		return startTime >= DURATION ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public int getWidth() {
		return 180;
	}
}
