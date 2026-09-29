package com.ficsitcraft.client.railway;

import com.ficsitcraft.client.screen.Gui;
import com.ficsitcraft.network.RailActionPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Rename a train station (timetables refer to stations by name). */
public class StationScreen extends Screen {
	private static final int W = 230, H = 92;
	private final long pos;
	private final String name;
	private TextFieldWidget field;

	public StationScreen(long pos, String name) {
		super(Text.translatable("gui.ficsitcraft.station.title"));
		this.pos = pos;
		this.name = name;
	}

	@Override
	protected void init() {
		int x = (width - W) / 2, y = (height - H) / 2;
		field = new TextFieldWidget(textRenderer, x + 12, y + 34, W - 24, 16, Text.translatable("gui.ficsitcraft.station.name"));
		field.setMaxLength(32);
		field.setText(name);
		addDrawableChild(field);
		setInitialFocus(field);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.station.done"), b -> done())
				.dimensions(x + W - 12 - 90, y + H - 28, 90, 18).build());
	}

	private void done() {
		String n = field.getText().trim();
		if (n.isEmpty()) n = name;
		final String fn = n;
		ClientRail.send(RailActionPayload.STATION_RENAME, o -> {
			o.writeLong(pos);
			o.writeUTF(fn);
		});
		close();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) {
			done();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Intentionally empty: Screen.render() calls this before the widgets, which would paint the dimmed background over the panel drawn in render(). */
	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x88000000);
		int x = (width - W) / 2, y = (height - H) / 2;
		Gui.panel(ctx, x, y, W, H);
		Gui.text(ctx, textRenderer, title, x + 12, y + 10, Gui.ORANGE);
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.station.hint"), x + 12, y + 22, Gui.GRAY);
		super.render(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
