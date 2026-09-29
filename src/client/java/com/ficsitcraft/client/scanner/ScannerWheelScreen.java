package com.ficsitcraft.client.scanner;

import com.ficsitcraft.block.NodeType;
import com.ficsitcraft.registry.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Radial resource picker shown while V is held. Point at a resource with the mouse and release V (or click).
 * Entry 0 = all resources, then every node type.
 */
public class ScannerWheelScreen extends Screen {
	private static final int OUTER = 78;
	private static final int INNER = 26;
	private int hovered = -2; // -2 = nothing, -1 = all, 0.. = node type

	public ScannerWheelScreen() {
		super(Text.translatable("gui.ficsitcraft.scan.title"));
	}

	private static int entries() {
		return NodeType.values().length + 1;
	}

	/** Entry index -> target (-1 = all). */
	private static int target(int entry) {
		return entry - 1;
	}

	private int entryAt(double mx, double my) {
		double dx = mx - width / 2.0, dy = my - height / 2.0;
		double d = Math.hypot(dx, dy);
		if (d < INNER * 0.7) return -1;
		double ang = Math.atan2(dx, -dy); // 0 = up, clockwise
		if (ang < 0) ang += Math.PI * 2;
		int n = entries();
		double step = Math.PI * 2 / n;
		return (int) Math.floor((ang + step / 2) / step) % n;
	}

	@Override
	public void tick() {
		if (client == null) return;
		// releasing the scanner key selects the hovered entry
		if (!ResourceScanner.keyDown(client)) {
			select();
		}
	}

	private void select() {
		if (client == null) return;
		int entry = hovered;
		close();
		ResourceScanner.wheelClosed();
		if (entry >= 0) ResourceScanner.scan(client, target(entry));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		hovered = entryAt(mouseX, mouseY);
		select();
		return true;
	}

	@Override
	public void close() {
		if (client != null) client.setScreen(null);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x55000000);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2, cy = height / 2;
		int e = entryAt(mouseX, mouseY);
		hovered = e < 0 ? -2 : e;
		int n = entries();

		// ring
		for (int dy = -OUTER; dy <= OUTER; dy++) {
			int half = (int) Math.sqrt(OUTER * OUTER - dy * dy);
			int in = Math.abs(dy) < INNER ? (int) Math.sqrt(INNER * INNER - dy * dy) : 0;
			for (int side = -1; side <= 1; side += 2) {
				int x0 = side < 0 ? cx - half : cx + in;
				int x1 = side < 0 ? cx - in : cx + half;
				if (x1 > x0) ctx.fill(x0, cy + dy, x1, cy + dy + 1, 0xC0181A1E);
			}
		}
		// hovered sector highlight
		if (hovered >= 0) {
			double step = Math.PI * 2 / n;
			double a0 = hovered * step - step / 2, a1 = hovered * step + step / 2;
			for (int dy = -OUTER; dy <= OUTER; dy++) {
				for (int dx = -OUTER; dx <= OUTER; dx++) {
					double d = Math.hypot(dx, dy);
					if (d < INNER || d > OUTER) continue;
					double ang = Math.atan2(dx, -dy);
					if (ang < a0 - 1e-9) ang += Math.PI * 2;
					if (ang > a1 + 1e-9) ang -= Math.PI * 2;
					if (ang >= a0 && ang <= a1) {
						// draw runs per row for speed
						int start = dx;
						while (dx <= OUTER) {
							double d2 = Math.hypot(dx, dy);
							double an = Math.atan2(dx, -dy);
							if (an < a0 - 1e-9) an += Math.PI * 2;
							if (an > a1 + 1e-9) an -= Math.PI * 2;
							if (d2 < INNER || d2 > OUTER || an < a0 || an > a1) break;
							dx++;
						}
						ctx.fill(cx + start, cy + dy, cx + dx, cy + dy + 1, 0x90FA9549);
					}
				}
			}
		}
		// separators + icons
		for (int i = 0; i < n; i++) {
			double step = Math.PI * 2 / n;
			double a = i * step;
			double ix = cx + Math.sin(a) * (INNER + OUTER) / 2.0;
			double iy = cy - Math.cos(a) * (INNER + OUTER) / 2.0;
			ItemStack icon = i == 0 ? new ItemStack(ModItems.BUILD_GUN) : new ItemStack(NodeType.values()[i - 1].resource());
			ctx.getMatrices().push();
			ctx.getMatrices().translate(ix, iy, 0);
			float s = i == hovered ? 1.5f : 1.2f;
			ctx.getMatrices().scale(s, s, 1f);
			ctx.drawItem(icon, -8, -8);
			ctx.getMatrices().pop();
			double sa = a + step / 2;
			for (int k = INNER; k < OUTER; k++) {
				ctx.fill((int) (cx + Math.sin(sa) * k), (int) (cy - Math.cos(sa) * k),
						(int) (cx + Math.sin(sa) * k) + 1, (int) (cy - Math.cos(sa) * k) + 1, 0x60FFFFFF);
			}
		}
		// centre label
		Text label = hovered >= 0 ? ResourceScanner.targetName(target(hovered)) : Text.translatable("gui.ficsitcraft.scan.pick");
		ctx.drawText(textRenderer, label, cx - textRenderer.getWidth(label) / 2, cy - 4, 0xFFFFFFFF, true);
		Text hint = Text.translatable("gui.ficsitcraft.scan.hint");
		ctx.drawText(textRenderer, hint, cx - textRenderer.getWidth(hint) / 2, cy + OUTER + 8, 0xFFBFC2C6, true);
	}
}
