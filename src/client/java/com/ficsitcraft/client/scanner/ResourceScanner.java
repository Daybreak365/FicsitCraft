package com.ficsitcraft.client.scanner;

import com.ficsitcraft.block.NodeType;
import com.ficsitcraft.block.Purity;
import com.ficsitcraft.block.ResourceNodeBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Satisfactory-style resource scanner.
 * <ul>
 *   <li>Tap <b>V</b>: scan for the last selected resource (default: all).</li>
 *   <li>Hold <b>V</b>: radial wheel to pick a resource; release (or click) to scan.</li>
 * </ul>
 * A pulse wave expands from the player; every resource node it reaches gets a light beam and an on-screen marker
 * (icon, purity, distance) that stays visible through walls for 30 seconds.
 */
public final class ResourceScanner {
	public static final double RADIUS = 100;
	private static final double WAVE_SPEED = 2.2;   // blocks per tick
	private static final int MARK_TICKS = 600;      // 30 s
	private static final int MAX_RESULTS = 40;
	private static final int HOLD_TICKS = 6;

	public static KeyBinding key;

	/** -1 = all resources, otherwise a {@link NodeType} ordinal. */
	static int selected = -1;
	private static int holdTicks;
	private static boolean wasDown;
	private static boolean ignoreUntilRelease;
	private static long cooldownUntil;

	private static long scanStart = Long.MIN_VALUE;
	private static Vec3d origin = Vec3d.ZERO;
	private static int scanTarget = -1;
	private static final List<Found> results = new ArrayList<>();
	private static int revealedCount;

	// captured each frame for projecting world positions onto the HUD
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJ = new Matrix4f();
	private static Vec3d cameraPos = Vec3d.ZERO;
	private static boolean matricesValid;

	private record Found(BlockPos pos, NodeType type, Purity purity, double dist) {
	}

	private ResourceScanner() {
	}

	public static int color(NodeType t) {
		return switch (t) {
			case IRON -> 0xE0A080;
			case COPPER -> 0xF08A3A;
			case LIMESTONE -> 0xEDE4C8;
			case COAL -> 0xB0B0B0;
			case CATERIUM -> 0xF6CF4E;
			case QUARTZ -> 0xEFA6E0;
		};
	}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.ficsitcraft.scan", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V, "category.ficsitcraft"));
		ClientTickEvents.END_CLIENT_TICK.register(ResourceScanner::tick);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(ResourceScanner::renderWorld);
		WorldRenderEvents.END.register(ctx -> {
			VIEW.set(ctx.positionMatrix());
			PROJ.set(ctx.projectionMatrix());
			cameraPos = ctx.camera().getPos();
			matricesValid = true;
		});
		HudRenderCallback.EVENT.register(ResourceScanner::renderHud);
	}

	/** Raw physical key state, so holding V behaves the same while the wheel screen is open. */
	static boolean keyDown(MinecraftClient client) {
		InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		if (bound.getCategory() != InputUtil.Type.KEYSYM || bound.getCode() == InputUtil.UNKNOWN_KEY.getCode()) return key.isPressed();
		return InputUtil.isKeyPressed(client.getWindow().getHandle(), bound.getCode());
	}

	/** Called by the wheel when it closes, so the still-held key doesn't reopen it. */
	static void wheelClosed() {
		ignoreUntilRelease = true;
		holdTicks = 0;
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			results.clear();
			return;
		}
		boolean down = client.currentScreen == null && keyDown(client);
		if (ignoreUntilRelease) {
			if (!keyDown(client)) ignoreUntilRelease = false;
			down = false;
		}
		if (down) {
			holdTicks++;
			if (holdTicks == HOLD_TICKS) client.setScreen(new ScannerWheelScreen());
		} else {
			if (wasDown && holdTicks > 0 && holdTicks < HOLD_TICKS) scan(client, selected);
			holdTicks = 0;
		}
		wasDown = down;

		// ping when the wave reaches new nodes
		if (scanStart != Long.MIN_VALUE) {
			double r = (client.world.getTime() - scanStart) * WAVE_SPEED;
			int revealed = 0;
			for (Found f : results) if (f.dist() <= r) revealed++;
			if (revealed > revealedCount) {
				Found f = results.get(revealed - 1);
				float pitch = 1.2f + f.purity().ordinal() * 0.25f;
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, pitch, 0.6f));
				revealedCount = revealed;
			}
		}
	}

	/** Starts a scan for the given target (-1 = all). */
	static void scan(MinecraftClient client, int target) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;
		long now = world.getTime();
		if (now < cooldownUntil) return;
		cooldownUntil = now + 20;
		selected = target;
		scanTarget = target;
		origin = client.player.getPos();
		scanStart = now;
		revealedCount = 0;
		results.clear();

		BlockPos center = client.player.getBlockPos();
		ChunkPos cc = new ChunkPos(center);
		int cr = (int) Math.ceil(RADIUS / 16.0);
		for (int cx = cc.x - cr; cx <= cc.x + cr; cx++) {
			for (int cz = cc.z - cr; cz <= cc.z + cr; cz++) {
				if (!world.isChunkLoaded(cx, cz) || !(world.getChunk(cx, cz) instanceof WorldChunk chunk)) continue;
				ChunkSection[] sections = chunk.getSectionArray();
				for (int i = 0; i < sections.length; i++) {
					ChunkSection section = sections[i];
					if (section == null || section.isEmpty()) continue;
					if (!section.hasAny(s -> s.getBlock() instanceof ResourceNodeBlock)) continue;
					int baseY = world.sectionIndexToCoord(i) * 16;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState s = section.getBlockState(x, y, z);
								if (!(s.getBlock() instanceof ResourceNodeBlock node)) continue;
								if (target >= 0 && node.getType().ordinal() != target) continue;
								BlockPos p = new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z);
								double d = Math.sqrt(p.getSquaredDistance(origin));
								if (d <= RADIUS) results.add(new Found(p, node.getType(), s.get(ResourceNodeBlock.PURITY), d));
							}
						}
					}
				}
			}
		}
		results.sort(Comparator.comparingDouble(Found::dist));
		if (results.size() > MAX_RESULTS) results.subList(MAX_RESULTS, results.size()).clear();

		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.8f, 0.7f));
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f));
		if (results.isEmpty()) {
			client.player.sendMessage(Text.translatable("message.ficsitcraft.scan_none", targetName(target)), true);
		}
	}

	static Text targetName(int target) {
		return target < 0 ? Text.translatable("gui.ficsitcraft.scan.all")
				: Text.translatable("node.ficsitcraft." + NodeType.values()[target].id);
	}

	private static float age(ClientWorld world, float delta) {
		return world.getTime() - scanStart + delta;
	}

	// ------------------------------------------------------------------ world effects

	private static void renderWorld(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || scanStart == Long.MIN_VALUE) return;
		float delta = ctx.tickCounter().getTickDelta(false);
		float age = age(world, delta);
		if (age > MARK_TICKS + 40) return;
		MatrixStack matrices = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
		Vec3d cam = ctx.camera().getPos();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer vc = immediate.getBuffer(RenderLayer.getLightning());
		matrices.push();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.peek().getPositionMatrix();

		// ---- expanding pulse: a glowing cylinder band + a bright ground ring
		double r = age * WAVE_SPEED;
		if (r < RADIUS + 8) {
			float fade = (float) Math.max(0, 1 - r / (RADIUS + 8));
			int c = scanTarget < 0 ? 0x5FD7FF : color(NodeType.values()[scanTarget]);
			band(vc, m, origin, r, origin.y - 4, origin.y + 10, c, (int) (70 * fade), 0);
			band(vc, m, origin, r, origin.y - 0.2, origin.y + 0.6, c, (int) (200 * fade), (int) (40 * fade));
			if (r > 3) band(vc, m, origin, r - 3, origin.y - 4, origin.y + 10, c, (int) (25 * fade), 0);
		}

		// ---- beams above every node the wave has reached
		for (Found f : results) {
			if (f.dist() > r) continue;
			float since = (float) (age - f.dist() / WAVE_SPEED);
			float alpha = since < 10 ? since / 10f : since > MARK_TICKS - 60 ? Math.max(0, (MARK_TICKS - since) / 60f) : 1f;
			if (alpha <= 0) continue;
			double x = f.pos().getX() + 0.5, z = f.pos().getZ() + 0.5;
			double y0 = f.pos().getY() + 1.0;
			double w = 0.12 + 0.08 * f.purity().ordinal();
			int c = color(f.type());
			beam(vc, m, x, y0, z, y0 + 40, w, c, (int) (140 * alpha));
			beam(vc, m, x, y0, z, y0 + 40, w * 2.5, c, (int) (35 * alpha));
		}
		matrices.pop();
		immediate.draw();
	}

	/** Vertical cylinder band of radius r between y0 and y1 (alpha top/bottom). */
	private static void band(VertexConsumer vc, Matrix4f m, Vec3d o, double r, double y0, double y1, int rgb, int aBottom, int aTop) {
		if (r <= 0.1 || (aBottom <= 0 && aTop <= 0)) return;
		int seg = 96;
		int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			float x0 = (float) (o.x + Math.cos(a0) * r), z0 = (float) (o.z + Math.sin(a0) * r);
			float x1 = (float) (o.x + Math.cos(a1) * r), z1 = (float) (o.z + Math.sin(a1) * r);
			// both windings so the band is visible from inside and outside
			vc.vertex(m, x0, (float) y0, z0).color(cr, cg, cb, aBottom);
			vc.vertex(m, x1, (float) y0, z1).color(cr, cg, cb, aBottom);
			vc.vertex(m, x1, (float) y1, z1).color(cr, cg, cb, aTop);
			vc.vertex(m, x0, (float) y1, z0).color(cr, cg, cb, aTop);
			vc.vertex(m, x0, (float) y1, z0).color(cr, cg, cb, aTop);
			vc.vertex(m, x1, (float) y1, z1).color(cr, cg, cb, aTop);
			vc.vertex(m, x1, (float) y0, z1).color(cr, cg, cb, aBottom);
			vc.vertex(m, x0, (float) y0, z0).color(cr, cg, cb, aBottom);
		}
	}

	/** Cross-shaped light beam that fades out towards the top. */
	private static void beam(VertexConsumer vc, Matrix4f m, double x, double y0, double z, double y1, double w, int rgb, int a) {
		int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
		float fx = (float) x, fz = (float) z, b = (float) y0, t = (float) y1, hw = (float) w;
		for (int k = 0; k < 2; k++) {
			float dx = k == 0 ? hw : 0, dz = k == 0 ? 0 : hw;
			vc.vertex(m, fx - dx, b, fz - dz).color(cr, cg, cb, a);
			vc.vertex(m, fx + dx, b, fz + dz).color(cr, cg, cb, a);
			vc.vertex(m, fx + dx, t, fz + dz).color(cr, cg, cb, 0);
			vc.vertex(m, fx - dx, t, fz - dz).color(cr, cg, cb, 0);
			vc.vertex(m, fx - dx, t, fz - dz).color(cr, cg, cb, 0);
			vc.vertex(m, fx + dx, t, fz + dz).color(cr, cg, cb, 0);
			vc.vertex(m, fx + dx, b, fz + dz).color(cr, cg, cb, a);
			vc.vertex(m, fx - dx, b, fz - dz).color(cr, cg, cb, a);
		}
	}

	// ------------------------------------------------------------------ HUD markers

	private static void renderHud(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null || !matricesValid || scanStart == Long.MIN_VALUE || client.options.hudHidden) return;
		float delta = counter.getTickDelta(false);
		float age = age(world, delta);
		if (age > MARK_TICKS) return;
		double r = age * WAVE_SPEED;
		TextRenderer tr = client.textRenderer;
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();

		// scanning banner while the wave travels
		if (r < RADIUS) {
			Text banner = Text.translatable("gui.ficsitcraft.scan.scanning", targetName(scanTarget));
			int bw = tr.getWidth(banner) + 16;
			ctx.fill(sw / 2 - bw / 2, 22, sw / 2 + bw / 2, 36, 0xA0101418);
			ctx.fill(sw / 2 - bw / 2, 35, sw / 2 - bw / 2 + (int) (bw * r / RADIUS), 36, 0xFF5FD7FF);
			ctx.drawText(tr, banner, sw / 2 - tr.getWidth(banner) / 2, 25, 0xFFFFFFFF, false);
		}

		Vec3d playerPos = client.player.getPos();
		for (Found f : results) {
			if (f.dist() > r) continue;
			float since = (float) (age - f.dist() / WAVE_SPEED);
			if (since > MARK_TICKS) continue;
			Vec3d p = Vec3d.ofCenter(f.pos()).add(0, 1.2, 0).subtract(cameraPos);
			Vector4f v = new Vector4f((float) p.x, (float) p.y, (float) p.z, 1f);
			VIEW.transform(v);
			PROJ.transform(v);
			if (v.w <= 0.01f) continue; // behind the camera
			float nx = v.x / v.w, ny = v.y / v.w;
			if (nx < -1.2f || nx > 1.2f || ny < -1.2f || ny > 1.2f) continue;
			int sx = (int) ((nx + 1) / 2 * sw);
			int sy = (int) ((1 - ny) / 2 * sh);
			int c = 0xFF000000 | color(f.type());
			// pop-in scale
			float pop = Math.min(1f, since / 6f);
			int rad = (int) (11 * pop);
			if (rad < 2) continue;
			for (int dy = -rad; dy <= rad; dy++) {
				int half = (int) Math.sqrt(rad * rad - dy * dy);
				ctx.fill(sx - half, sy + dy, sx + half + 1, sy + dy + 1, 0xC0101418);
			}
			for (int dy = -rad; dy <= rad; dy++) {
				int half = (int) Math.sqrt(rad * rad - dy * dy);
				int inner = (int) Math.sqrt(Math.max(0, (rad - 1.5) * (rad - 1.5) - dy * dy));
				if (half > inner) {
					ctx.fill(sx - half, sy + dy, sx - inner, sy + dy + 1, c);
					ctx.fill(sx + inner + 1, sy + dy, sx + half + 1, sy + dy + 1, c);
				} else if (Math.abs(dy) == rad) {
					ctx.fill(sx - half, sy + dy, sx + half + 1, sy + dy + 1, c);
				}
			}
			if (pop >= 1f) ctx.drawItem(new ItemStack(f.type().resource()), sx - 8, sy - 8);
			String dist = (int) Math.round(Math.sqrt(f.pos().getSquaredDistance(playerPos))) + "m";
			ctx.drawText(tr, dist, sx - tr.getWidth(dist) / 2, sy + rad + 2, 0xFFFFFFFF, true);
			String pur = switch (f.purity()) {
				case IMPURE -> "§c" + Text.translatable("purity.ficsitcraft.impure").getString();
				case NORMAL -> "§e" + Text.translatable("purity.ficsitcraft.normal").getString();
				case PURE -> "§a" + Text.translatable("purity.ficsitcraft.pure").getString();
			};
			ctx.getMatrices().push();
			ctx.getMatrices().translate(sx, sy + rad + 11, 0);
			ctx.getMatrices().scale(0.75f, 0.75f, 1f);
			ctx.drawText(tr, pur, -tr.getWidth(pur) / 2, 0, 0xFFFFFFFF, true);
			ctx.getMatrices().pop();
		}
	}
}
