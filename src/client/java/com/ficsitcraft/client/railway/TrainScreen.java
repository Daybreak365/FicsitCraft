package com.ficsitcraft.client.railway;

import com.ficsitcraft.client.screen.Gui;
import com.ficsitcraft.network.RailActionPayload;
import com.ficsitcraft.train.Stop;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import com.ficsitcraft.train.VehicleType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Train menu: vehicles (open cargo, split the train), drive / autopilot / horn / couple, and the timetable editor.
 * Everything is authoritative on the server; this screen just sends commands and shows the state that comes back.
 */
public class TrainScreen extends Screen {
	private static final int W = 380, H = 236;
	private static final int ROW = 15, CAR_ROWS = 10, STOP_ROWS = 6;

	private ClientRail.TrainInfo info;
	private final List<Stop> stops = new ArrayList<>();
	private int carScroll, stopScroll;
	private int addStation, addMode;
	private String addSecs = "30";
	private TextFieldWidget nameField, addField;
	/** Dwell-time fields of the visible timetable rows (index = row on screen). */
	private final List<TextFieldWidget> rowFields = new ArrayList<>();
	private int sendSerial;

	public TrainScreen(ClientRail.TrainInfo info) {
		super(Text.translatable("gui.ficsitcraft.train.title"));
		this.info = info;
		this.stops.addAll(info.timetable);
	}

	public UUID trainId() {
		return info.state.id;
	}

	public void update(ClientRail.TrainInfo fresh) {
		this.info = fresh;
		stops.clear();
		stops.addAll(fresh.timetable);
		carScroll = Math.min(carScroll, Math.max(0, fresh.state.vehicles.size() - CAR_ROWS));
		stopScroll = Math.min(stopScroll, Math.max(0, stops.size() - STOP_ROWS));
		String keep = nameField != null ? nameField.getText() : null;
		clearAndInit();
		if (keep != null && nameField != null && nameField.isFocused()) nameField.setText(keep);
	}

	private int px() {
		return (width - W) / 2;
	}

	private int py() {
		return (height - H) / 2;
	}

	private Train train() {
		return info.state;
	}

	private void cmd(int c, ClientRail.BodyWriter extra) {
		ClientRail.trainCmd(train().id, c, extra);
	}

	private void sendTimetable() {
		sendTimetable(false);
	}

	/** Sends the whole timetable. {@code quiet}: the server must not answer with a fresh menu (used when the screen is closing). */
	private void sendTimetable(boolean quiet) {
		flush();
		sendSerial++;
		cmd(RailActionPayload.CMD_TIMETABLE, o -> {
			o.writeInt(stops.size());
			for (Stop s : stops) {
				o.writeUTF(s.station());
				o.writeByte(s.mode());
				o.writeShort(s.seconds());
			}
			o.writeBoolean(quiet);
		});
	}

	/** Copies the numbers typed into the dwell-time fields into the local timetable. */
	private void flush() {
		for (int r = 0; r < rowFields.size(); r++) {
			int i = stopScroll + r;
			if (i >= stops.size()) break;
			Stop s = stops.get(i);
			int secs = parseSecs(rowFields.get(r).getText(), s.seconds(), s.mode());
			if (secs != s.seconds()) stops.set(i, new Stop(s.station(), s.mode(), secs));
		}
		if (addField != null) addSecs = addField.getText();
	}

	/** Time stops need at least 1 s; a cargo stop's time limit may be 0 (= wait as long as the platforms need). */
	private static int parseSecs(String text, int fallback, int mode) {
		try {
			return Math.max(mode == Stop.WAIT_LOADED ? 0 : 1, Math.min(Stop.MAX_SECONDS, Integer.parseInt(text.trim())));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private boolean stationKnown(String name) {
		for (String n : info.stations) if (n.equalsIgnoreCase(name)) return true;
		return false;
	}

	private static final int DEFAULT_TIME = 30, DEFAULT_CARGO_LIMIT = 120;

	private static Tooltip modeTip(int mode) {
		return Tooltip.of(Text.translatable(mode == Stop.WAIT_LOADED ? "gui.ficsitcraft.train.mode_cargo_tip" : "gui.ficsitcraft.train.mode_time_tip"));
	}

	private static Tooltip limitTip(int mode) {
		return Tooltip.of(Text.translatable(mode == Stop.WAIT_LOADED ? "gui.ficsitcraft.train.limit_cargo_tip" : "gui.ficsitcraft.train.limit_time_tip"));
	}

	private static String modeLabel(int mode) {
		return Text.translatable(mode == Stop.WAIT_LOADED ? "gui.ficsitcraft.train.mode_loaded_short" : "gui.ficsitcraft.train.mode_time_short").getString();
	}

	@Override
	protected void init() {
		int x = px(), y = py();
		Train t = train();

		nameField = new TextFieldWidget(textRenderer, x + 10, y + 20, 150, 14, Text.translatable("gui.ficsitcraft.train.name"));
		nameField.setMaxLength(32);
		nameField.setText(t.name);
		nameField.setPlaceholder(Text.translatable("gui.ficsitcraft.train.name"));
		nameField.setChangedListener(s -> {
		});
		addDrawableChild(nameField);

		// ---- cars
		int cy = y + 52;
		for (int r = 0; r < CAR_ROWS; r++) {
			int i = carScroll + r;
			if (i >= t.vehicles.size()) break;
			Vehicle v = t.vehicles.get(i);
			int ry = cy + r * ROW;
			if (v.type == VehicleType.FREIGHT_CAR) {
				final int idx = i;
				addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.open"),
						b -> RailClientNet.send2(RailActionPayload.OPEN_CAR, t.id, t.vehicles.get(idx).id)).dimensions(x + 96, ry, 30, ROW - 2).build());
			}
			if (i > 0) {
				final int idx = i;
				addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.split"),
						b -> cmd(RailActionPayload.CMD_DECOUPLE, o -> o.writeInt(idx))).dimensions(x + 128, ry, 32, ROW - 2).build());
			}
		}

		// ---- controls
		int rx = x + 172;
		UUID firstLoco = null;
		for (Vehicle v : t.vehicles) if (v.type == VehicleType.LOCOMOTIVE) {
			firstLoco = v.id;
			break;
		}
		final UUID loco = firstLoco;
		ButtonWidget drive = ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.drive"),
				b -> cmd(RailActionPayload.CMD_DRIVE, o -> ClientRail.writeUuid(o, loco))).dimensions(rx, y + 20, 74, 16).build();
		drive.active = loco != null && !t.derailed && (!info.hasDriver || info.driverIsMe);
		addDrawableChild(drive);
		ButtonWidget auto = ButtonWidget.builder(Text.translatable(t.autopilot ? "gui.ficsitcraft.train.autopilot_on" : "gui.ficsitcraft.train.autopilot_off"),
				b -> cmd(RailActionPayload.CMD_AUTOPILOT, o -> o.writeBoolean(!t.autopilot))).dimensions(rx + 78, y + 20, 84, 16).build();
		auto.active = !t.derailed;
		addDrawableChild(auto);
		if (t.derailed) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.rerail"),
					b -> cmd(RailActionPayload.CMD_RERAIL, null)).dimensions(rx, y + 2, 162, 16).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.horn"),
				b -> cmd(RailActionPayload.CMD_HORN, null)).dimensions(rx, y + 38, 74, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.couple"),
				b -> cmd(RailActionPayload.CMD_COUPLE, null)).dimensions(rx + 78, y + 38, 84, 14).build());

		// ---- timetable rows
		rowFields.clear();
		int ty = y + 98;
		for (int r = 0; r < STOP_ROWS; r++) {
			int i = stopScroll + r;
			if (i >= stops.size()) break;
			final int idx = i;
			Stop st = stops.get(i);
			int ry = ty + r * ROW;
			// mode toggle: fixed time <-> wait until loaded
			ButtonWidget modeBtn = ButtonWidget.builder(Text.literal(modeLabel(st.mode())), b -> {
				flush();
				Stop cur = stops.get(idx);
				boolean toCargo = cur.mode() != Stop.WAIT_LOADED;
				stops.set(idx, new Stop(cur.station(), toCargo ? Stop.WAIT_LOADED : Stop.WAIT_SECONDS, toCargo ? DEFAULT_CARGO_LIMIT : DEFAULT_TIME));
				sendTimetable();
			}).dimensions(rx + 64, ry, 34, ROW - 2).build();
			modeBtn.setTooltip(modeTip(st.mode()));
			addDrawableChild(modeBtn);
			TextFieldWidget f = new TextFieldWidget(textRenderer, rx + 100, ry, 30, ROW - 2, Text.translatable("gui.ficsitcraft.train.seconds"));
			f.setMaxLength(4);
			f.setTextPredicate(v -> v.matches("\\d{0,4}"));
			f.setText(Integer.toString(st.seconds()));
			f.setTooltip(limitTip(st.mode()));
			rowFields.add(f);
			addDrawableChild(f);
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
				flush();
				cmd(RailActionPayload.CMD_STOP_INDEX, o -> o.writeInt(idx));
			}).dimensions(rx + 140, ry, 12, ROW - 2).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> move(idx, -1)).dimensions(rx + 154, ry, 14, ROW - 2).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> move(idx, 1)).dimensions(rx + 169, ry, 14, ROW - 2).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> {
				flush();
				stops.remove(idx);
				sendTimetable();
			}).dimensions(rx + 188, ry, 14, ROW - 2).build());
		}
		// add row
		int ay = y + H - 42;
		if (!info.stations.isEmpty()) {
			addStation = Math.floorMod(addStation, info.stations.size());
			addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
				flush();
				addStation--;
				clearAndInit();
			}).dimensions(rx, ay, 12, 14).build());
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
				flush();
				addStation++;
				clearAndInit();
			}).dimensions(rx + 132, ay, 12, 14).build());
		}
		ButtonWidget addModeBtn = ButtonWidget.builder(Text.literal(modeLabel(addMode)), b -> {
			flush();
			addMode = 1 - addMode;
			addSecs = Integer.toString(addMode == Stop.WAIT_LOADED ? DEFAULT_CARGO_LIMIT : DEFAULT_TIME);
			clearAndInit();
		}).dimensions(rx + 148, ay, 54, 14).build();
		addModeBtn.setTooltip(modeTip(addMode));
		addDrawableChild(addModeBtn);
		addField = new TextFieldWidget(textRenderer, rx, ay + 16, 44, 14, Text.translatable("gui.ficsitcraft.train.seconds"));
		addField.setMaxLength(4);
		addField.setTextPredicate(v -> v.matches("\\d{0,4}"));
		addField.setText(addSecs);
		addField.setTooltip(limitTip(addMode));
		addDrawableChild(addField);
		ButtonWidget add = ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.add_stop"), b -> {
			flush();
			if (info.stations.isEmpty() || stops.size() >= 64) return;
			int secs = parseSecs(addSecs, DEFAULT_TIME, addMode);
			addSecs = Integer.toString(secs);
			stops.add(new Stop(info.stations.get(Math.floorMod(addStation, info.stations.size())), addMode, secs));
			sendTimetable();
		}).dimensions(rx + 70, ay + 16, 132, 14).build();
		add.active = !info.stations.isEmpty();
		addDrawableChild(add);

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ficsitcraft.train.close"), b -> close())
				.dimensions(x + 10, y + H - 26, 60, 16).build());
	}

	private void move(int idx, int d) {
		flush();
		int j = idx + d;
		if (j < 0 || j >= stops.size()) return;
		Stop s = stops.remove(idx);
		stops.add(j, s);
		sendTimetable();
	}

	@Override
	public void close() {
		flush();
		if (nameField != null && !nameField.getText().equals(train().name)) {
			final String n = nameField.getText();
			cmd(RailActionPayload.CMD_RENAME, o -> o.writeUTF(n));
		}
		if (!stops.equals(info.timetable)) sendTimetable(true);
		super.close();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		flush();
		int serial = sendSerial;
		boolean r = super.mouseClicked(mouseX, mouseY, button);
		// clicking away from an edited field commits it (a button that already sent the timetable counts as a commit)
		if (serial == sendSerial && !stops.equals(info.timetable)) sendTimetable();
		return r;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) { // Enter
			for (TextFieldWidget f : rowFields) {
				if (f.isFocused()) {
					sendTimetable();
					return true;
				}
			}
			if (addField != null && addField.isFocused()) {
				flush();
				addField.setFocused(false);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		flush();
		int x = px();
		if (mouseX < x + 168) {
			int max = Math.max(0, train().vehicles.size() - CAR_ROWS);
			int n = Math.max(0, Math.min(max, carScroll - (int) Math.signum(vertical)));
			if (n != carScroll) {
				carScroll = n;
				clearAndInit();
			}
		} else {
			int max = Math.max(0, stops.size() - STOP_ROWS);
			int n = Math.max(0, Math.min(max, stopScroll - (int) Math.signum(vertical)));
			if (n != stopScroll) {
				stopScroll = n;
				clearAndInit();
			}
		}
		return true;
	}

	/** Intentionally empty: Screen.render() calls this before the widgets, which would paint the dimmed background over the panel drawn in render(). */
	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x88000000);
		int x = px(), y = py();
		Train t = train();
		Gui.panel(ctx, x, y, W, H);
		Gui.text(ctx, textRenderer, title, x + 10, y + 7, Gui.ORANGE);

		// ---- left: cars
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.train.cars", t.vehicles.size()), x + 10, y + 40, Gui.GRAY);
		Gui.inset(ctx, x + 8, y + 50, 154, CAR_ROWS * ROW + 4);
		for (int r = 0; r < CAR_ROWS; r++) {
			int i = carScroll + r;
			if (i >= t.vehicles.size()) break;
			Vehicle v = t.vehicles.get(i);
			ClientRail.CarInfo ci = i < info.cars.size() ? info.cars.get(i) : null;
			int ry = y + 52 + r * ROW;
			int color = v.type == VehicleType.LOCOMOTIVE ? Gui.ORANGE : v.type == VehicleType.FLUID_CAR ? Gui.CYAN : Gui.WHITE;
			ctx.fill(x + 10, ry, x + 12, ry + ROW - 3, color);
			String name = Text.translatable("item.ficsitcraft." + v.type.id).getString();
			ctx.drawText(textRenderer, Text.literal((i + 1) + " " + shorten(name, 11)), x + 15, ry + 1, color, false);
			if (ci != null && ci.kind() == 1) {
				ctx.drawText(textRenderer, Text.literal(ci.slots() + "/36"), x + 68, ry + 1, Gui.GRAY, false);
			} else if (ci != null && ci.kind() == 2) {
				String f = ci.amount() > 0.5 ? ci.fluid() : "";
				ctx.drawText(textRenderer, Text.literal(Math.round(ci.amount()) + ""), x + 68, ry + 1, Gui.GRAY, false);
			}
		}

		// ---- right: status
		int rx = x + 172;
		double kmh = Math.abs(t.speed) * 20 * 3.6;
		Text state = Text.translatable("gui.ficsitcraft.train.status_" + (info.status.isEmpty() ? "idle" : info.status));
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.train.speed", String.format("%.0f", kmh)), rx, y + 56, Gui.YELLOW);
		Gui.text(ctx, textRenderer, state, rx + 62, y + 56, info.autoState == 3 ? Gui.RED : Gui.GREEN);
		String power = t.powered ? Gui.fmt(info.powerDraw) + " MW" : Text.translatable("gui.ficsitcraft.train.no_power").getString();
		Gui.text(ctx, textRenderer, Text.literal(power), rx, y + 67, t.powered ? Gui.CYAN : Gui.RED);
		if (info.autoState == 1 && info.goalDist >= 0) {
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.train.distance", String.format("%.0f", info.goalDist)), rx + 62, y + 67, Gui.GRAY);
		}

		// ---- timetable
		Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.train.timetable"), rx, y + 86, Gui.GRAY);
		Text help = Text.translatable("gui.ficsitcraft.train.mode_help");
		Gui.text(ctx, textRenderer, help, rx + 204 - textRenderer.getWidth(help), y + 86, Gui.DARK_GRAY);
		Gui.inset(ctx, rx - 2, y + 96, 206, STOP_ROWS * ROW + 4);
		if (stops.isEmpty()) {
			Gui.text(ctx, textRenderer, Text.translatable("gui.ficsitcraft.train.no_stops"), rx + 4, y + 102, Gui.DARK_GRAY);
		}
		for (int r = 0; r < STOP_ROWS; r++) {
			int i = stopScroll + r;
			if (i >= stops.size()) break;
			Stop s = stops.get(i);
			int ry = y + 98 + r * ROW;
			boolean current = info.autopilot() && i == info.stopIndex;
			if (current) ctx.fill(rx - 1, ry - 1, rx + 100, ry + ROW - 3, 0x552F8FD8);
			// a station that no longer exists is shown in red
			int col = !stationKnown(s.station()) ? Gui.RED : current ? Gui.WHITE : Gui.GRAY;
			ctx.drawText(textRenderer, Text.literal((i + 1) + " " + shorten(s.station(), 9)), rx + 2, ry + 1, col, false);
			ctx.drawText(textRenderer, Text.literal(s.mode() == Stop.WAIT_LOADED && s.seconds() == 0 ? "∞" : "s"), rx + 133, ry + 1, Gui.DARK_GRAY, false);
		}
		// add row label
		int ay = y + H - 42;
		String pick = info.stations.isEmpty() ? Text.translatable("gui.ficsitcraft.train.no_stations").getString()
				: info.stations.get(Math.floorMod(addStation, info.stations.size()));
		Gui.textCentered(ctx, textRenderer, Text.literal(shorten(pick, 20)), rx + 72, ay + 3, Gui.WHITE);
		Gui.text(ctx, textRenderer, Text.translatable(addMode == Stop.WAIT_LOADED ? "gui.ficsitcraft.train.add_limit" : "gui.ficsitcraft.train.add_wait"), rx + 48, ay + 19, Gui.GRAY);
		super.render(ctx, mouseX, mouseY, delta);
	}

	private static String shorten(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
