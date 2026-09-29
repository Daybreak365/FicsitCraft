package com.ficsitcraft.client.railway;

import com.ficsitcraft.network.RailActionPayload;
import com.ficsitcraft.rail.V3;
import com.ficsitcraft.train.Train;
import com.ficsitcraft.train.Vehicle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Driving a locomotive from the cab (client driven, like the zipline): W raises the throttle lever, S lowers it (and
 * reverses), Space is the brake, Shift gets out, H is the horn, G opens the train menu.
 */
public final class TrainRide {
	private static boolean riding;
	private static UUID trainId, vehicleId;
	private static float throttle;
	private static boolean brake;
	private static float lastThrottle = 9;
	private static boolean lastBrake;
	private static int sendTimer;
	private static Perspective saved;
	private static boolean sneakWas, hornWas, menuWas, dismWas;

	private TrainRide() {
	}

	public static boolean isRiding() {
		return riding;
	}

	public static UUID trainId() {
		return trainId;
	}

	public static void start(MinecraftClient client, UUID train, UUID vehicle) {
		riding = true;
		trainId = train;
		vehicleId = vehicle;
		throttle = 0;
		brake = false;
		lastThrottle = 9;
		saved = client.options.getPerspective();
		client.options.setPerspective(Perspective.FIRST_PERSON);
		sneakWas = true;
		client.setScreen(null);
		if (client.player != null) client.player.sendMessage(Text.translatable("hud.ficsitcraft.train_controls").formatted(Formatting.AQUA), true);
	}

	public static void stop(MinecraftClient client) {
		if (!riding) return;
		riding = false;
		ClientPlayerEntity p = client.player;
		ClientRail.CTrain ct = ClientRail.trains.get(trainId);
		if (p != null && ct != null && client.world != null && ClientRail.pathKnown(ct.base)) {
			Train t = ClientRail.posed(ct, client.world, 1f);
			int idx = indexOf(t, vehicleId);
			if (idx >= 0) {
				RailRenderer.VehiclePose pose = RailRenderer.poseOf(t, idx);
				V3 right = pose.forward().cross(new V3(0, 1, 0)).normalize();
				V3 out = pose.center().add(right.mul(3.2)).add(new V3(0, 0.2, 0));
				p.setPosition(out.x(), out.y(), out.z());
			}
		}
		if (saved != null) client.options.setPerspective(saved);
		saved = null;
	}

	private static int indexOf(Train t, UUID vid) {
		for (int i = 0; i < t.vehicles.size(); i++) if (t.vehicles.get(i).id.equals(vid)) return i;
		return -1;
	}

	public static void tick(MinecraftClient client) {
		ClientPlayerEntity p = client.player;
		if (!riding || p == null || client.world == null) return;
		ClientRail.CTrain ct = ClientRail.trains.get(trainId);
		if (ct == null || ct.base == null || !ClientRail.pathKnown(ct.base)) {
			if (ct == null) stop(client);
			return;
		}
		// ---- input
		boolean w = client.options.forwardKey.isPressed(), s = client.options.backKey.isPressed();
		boolean space = client.options.jumpKey.isPressed();
		boolean sneak = client.options.sneakKey.isPressed();
		if (space) {
			throttle = 0;
			brake = true;
		} else {
			brake = false;
			if (w) throttle = Math.min(1f, throttle + 0.02f);
			if (s) throttle = Math.max(-1f, throttle - 0.03f);
		}
		if (client.currentScreen == null) {
			if (sneak && !sneakWas) {
				ClientRail.trainCmd(trainId, RailActionPayload.CMD_STOP_DRIVE, null);
			}
			boolean horn = com.ficsitcraft.client.FicsitCraftClient.hornKey != null && com.ficsitcraft.client.FicsitCraftClient.hornKey.isPressed();
			if (horn && !hornWas) ClientRail.trainCmd(trainId, RailActionPayload.CMD_HORN, null);
			hornWas = horn;
			boolean menu = com.ficsitcraft.client.FicsitCraftClient.menuKey != null && com.ficsitcraft.client.FicsitCraftClient.menuKey.isPressed();
			if (menu && !menuWas) RailClientNet.send2(RailActionPayload.INTERACT_VEHICLE, trainId, vehicleId);
			menuWas = menu;
		}
		sneakWas = sneak;
		if (--sendTimer <= 0 || Math.abs(throttle - lastThrottle) > 0.001f || brake != lastBrake) {
			sendTimer = 10;
			lastThrottle = throttle;
			lastBrake = brake;
			float th = throttle;
			boolean br = brake;
			ClientRail.send(RailActionPayload.DRIVE_INPUT, o -> {
				ClientRail.writeUuid(o, trainId);
				o.writeFloat(th);
				o.writeBoolean(br);
			});
		}

		// ---- keep the player in the cab. One tick ahead so the camera's tick interpolation lines up with the train model.
		Train t = ClientRail.posed(ct, client.world, 1f);
		int idx = indexOf(t, vehicleId);
		if (idx < 0) return;
		RailRenderer.VehiclePose pose = RailRenderer.poseOf(t, idx);
		V3 seat = pose.center().add(pose.forward().mul(2.6)).add(new V3(0, 1.27, 0));
		p.setPosition(seat.x(), seat.y(), seat.z());
		p.setVelocity(0, 0, 0);
		p.fallDistance = 0;
		p.setOnGround(true);
	}

	public static void renderHud(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden || client.world == null || client.player == null) return;
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		if (RailRenderer.previewText != null) {
			ctx.drawCenteredTextWithShadow(client.textRenderer, RailRenderer.previewText, w / 2, h / 2 + 14, 0xFFC8E8FF);
		}
		if (!riding) return;
		ClientRail.CTrain ct = ClientRail.trains.get(trainId);
		if (ct == null || ct.base == null) return;
		Train t = ct.base;
		double kmh = Math.abs(t.speed) * 20 * 3.6;
		int idx = indexOf(t, vehicleId);
		double dir = idx < 0 ? 1 : t.vehicles.get(idx).facing;
		boolean reversing = t.speed * dir < -0.001;
		int x = w / 2 - 80, y = h - 62;
		ctx.fill(x - 4, y - 4, x + 164, y + 40, 0x99101418);
		ctx.drawTextWithShadow(client.textRenderer, Text.literal(String.format("%3.0f km/h%s", kmh, reversing ? "  R" : "")), x, y, 0xFFFFD84A);
		String power = t.powered ? "" : "  " + Text.translatable("hud.ficsitcraft.no_power").getString();
		ctx.drawTextWithShadow(client.textRenderer, Text.literal(power), x + 70, y, 0xFFFF5A5A);
		// throttle lever
		int bx = x, by = y + 14, bw = 160;
		ctx.fill(bx, by, bx + bw, by + 8, 0xFF26292E);
		int mid = bx + bw / 2;
		ctx.fill(mid, by - 1, mid + 1, by + 9, 0xFFA0A4AA);
		int len = (int) (Math.abs(throttle) * bw / 2);
		if (throttle >= 0) ctx.fill(mid, by + 1, mid + len, by + 7, 0xFF6BE675);
		else ctx.fill(mid - len, by + 1, mid, by + 7, 0xFFFF9A4A);
		if (brake) ctx.drawTextWithShadow(client.textRenderer, Text.translatable("hud.ficsitcraft.brake"), bx, by + 12, 0xFFFF5A5A);
		String hint = t.autopilot ? Text.translatable("hud.ficsitcraft.autopilot_paused").getString() : "";
		ctx.drawTextWithShadow(client.textRenderer, Text.literal(hint), bx + 40, by + 12, 0xFFA0A4AA);
	}
}
