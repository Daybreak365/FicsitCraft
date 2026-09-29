package com.ficsitcraft.client.screen;

import com.ficsitcraft.data.BuildCategory;
import com.ficsitcraft.data.Building;
import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.data.Cost;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.registry.ModItems;
import com.ficsitcraft.screen.BuildGunScreenHandler;
import com.ficsitcraft.screen.ListScreenHandler;
import com.ficsitcraft.util.InvUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Build Gun: wide window with category tabs on the left, a name search and the scrollable building list. */
public class BuildGunScreen extends ListScreen<BuildGunScreenHandler> {
	private static final int TAB_X = 8;
	private static final int TAB_W = 86;
	private static final int TAB_H = 13;

	private final PlayerInventory playerInv;
	/** null = all categories. */
	private BuildCategory category;
	private TextFieldWidget search;
	private String query = "";

	public BuildGunScreen(BuildGunScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.playerInv = inventory;
		backgroundWidth = BuildGunScreenHandler.WIDTH;
		listX = 100;
		listW = 230;
	}

	@Override
	protected int invX() {
		return BuildGunScreenHandler.INV_X;
	}

	@Override
	protected void init() {
		super.init();
		search = new TextFieldWidget(textRenderer, x + backgroundWidth - 8 - 132, y + 4, 132, 12, Text.translatable("gui.ficsitcraft.search"));
		search.setPlaceholder(Text.translatable("gui.ficsitcraft.search"));
		search.setMaxLength(32);
		search.setDrawsBackground(true);
		search.setText(query);
		search.setChangedListener(q -> {
			query = q.trim().toLowerCase(Locale.ROOT);
			scroll = 0;
		});
		addDrawableChild(search);
	}

	/** Buildings the player may build right now (unlocked, or everything in creative). */
	private List<Building> available() {
		long mask = handler.getMask();
		List<Building> list = new ArrayList<>();
		for (Building b : Buildings.ALL) {
			if (!Milestones.isDone(mask, b.milestone()) && !isCreative()) continue;
			list.add(b);
		}
		return list;
	}

	@Override
	protected List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		for (Building b : available()) {
			if (category != null && b.category() != category) continue;
			if (!query.isEmpty() && !b.item().getName().getString().toLowerCase(Locale.ROOT).contains(query)) continue;
			List<Ingredient> ins = new ArrayList<>();
			for (Cost c : b.cost()) {
				ins.add(new Ingredient(new ItemStack(c.item()), c.item().getName(), c.count(), InvUtil.count(playerInv, c.item())));
			}
			list.add(new Entry(b.index(), b.stack(), b.item().getName(), ins));
		}
		return list;
	}

	@Override
	protected Text emptyText() {
		return Text.translatable(available().isEmpty() ? "gui.ficsitcraft.nothing_unlocked" : "gui.ficsitcraft.nothing_found");
	}

	/** Number of available buildings in a tab (null = all). */
	private int count(BuildCategory c) {
		int n = 0;
		for (Building b : available()) if (c == null || b.category() == c) n++;
		return n;
	}

	private ItemStack tabIcon(BuildCategory c) {
		if (c == null) return new ItemStack(ModItems.BUILD_GUN);
		for (Building b : Buildings.ALL) if (b.category() == c) return b.stack();
		return ItemStack.EMPTY;
	}

	private BuildCategory tabAt(int i) {
		return i == 0 ? null : BuildCategory.values()[i - 1];
	}

	private int tabCount() {
		return BuildCategory.values().length + 1;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		super.drawBackground(ctx, delta, mouseX, mouseY);
		// tabs
		Gui.inset(ctx, x + TAB_X - 1, y + LIST_Y - 1, TAB_W + 2, tabCount() * TAB_H + 2);
		for (int i = 0; i < tabCount(); i++) {
			BuildCategory c = tabAt(i);
			int tx = x + TAB_X;
			int ty = y + LIST_Y + i * TAB_H;
			boolean selected = c == category;
			boolean hover = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H;
			ctx.fill(tx, ty, tx + TAB_W, ty + TAB_H, selected ? 0xFF5A4630 : hover ? 0xFF4A4036 : (i % 2 == 0 ? 0xFF2C3036 : 0xFF30343A));
			if (selected) ctx.fill(tx, ty, tx + 2, ty + TAB_H, Gui.ORANGE);
			var m = ctx.getMatrices();
			m.push();
			m.translate(tx + 4, ty + 0.5f, 0);
			m.scale(0.75f, 0.75f, 1f);
			ctx.drawItem(tabIcon(c), 0, 0);
			m.pop();
			Text name = Text.translatable(c == null ? "gui.ficsitcraft.cat.all" : c.translationKey());
			String label = textRenderer.trimToWidth(name.getString(), TAB_W - 32);
			ctx.drawText(textRenderer, label, tx + 19, ty + 3, selected ? Gui.WHITE : Gui.GRAY, false);
			String n = Integer.toString(count(c));
			ctx.drawText(textRenderer, n, tx + TAB_W - 3 - textRenderer.getWidth(n), ty + 3, selected ? Gui.ORANGE : Gui.DARK_GRAY, false);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		Gui.text(ctx, textRenderer, title, 8, 6, Gui.WHITE);
		Gui.text(ctx, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, Gui.GRAY);
		// hint to the left of the inventory
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("gui.ficsitcraft.shift_hint"), invX() - 14);
		int ly = ListScreenHandler.INV_Y + 4;
		for (OrderedText line : lines) {
			ctx.drawText(textRenderer, line, 8, ly, Gui.DARK_GRAY, false);
			ly += 10;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (search != null && search.isFocused() && !search.isMouseOver(mouseX, mouseY)) search.setFocused(false);
		if (button == 0) {
			for (int i = 0; i < tabCount(); i++) {
				int tx = x + TAB_X;
				int ty = y + LIST_Y + i * TAB_H;
				if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H) {
					category = tabAt(i);
					scroll = 0;
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// typing in the search box must not trigger the inventory key / hotbar keys
		if (search != null && search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			search.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
