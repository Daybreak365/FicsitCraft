package com.ficsitcraft.client.building;

import com.ficsitcraft.multiblock.Footprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.function.Function;

/**
 * Emits the quads of a {@link BuildingModel}. Parts flagged {@link BuildingModel#STRETCH} map each face texture once
 * over the whole face (per-face painted building art); other parts tile their texture once per block like real blocks.
 */
public final class BuildingMesh {
	private BuildingMesh() {
	}

	/**
	 * Renders the model with the controller block at the current matrix origin.
	 *
	 * @param argb   -1 for normal colours, otherwise a tint + alpha applied to every face (hologram)
	 * @param time   animation time in ticks
	 */
	public static void render(BuildingModel model, Direction facing, boolean active, float time, MatrixStack matrices,
							  VertexConsumer vc, int light, int argb) {
		Footprint fp = model.footprint;
		Function<Identifier, Sprite> atlas = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		float yRot = switch (facing) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yRot));
		matrices.translate(-0.5, 0, -0.5);
		parts(model, active, time, matrices, vc, light, argb, atlas, fp.ax(), fp.az() + 1, 0f);
		matrices.pop();
	}

	/**
	 * Draws a model in its own local frame (used for rolling stock and trackside signals): local X is right, Z the forward
	 * direction (front = Z max), Y up, with {@code ox / oz} being the point that ends up at the current matrix origin.
	 * Forward ends up along -Z of the matrix, exactly like buildings facing north.
	 */
	public static void renderLocal(BuildingModel model, boolean active, float time, MatrixStack matrices, VertexConsumer vc,
								   int light, int argb, float ox, float oz) {
		renderLocal(model, active, time, matrices, vc, light, argb, ox, oz, 0f);
	}

	/** @param roll distance in blocks the vehicle has rolled forward; the wheels of the model turn accordingly */
	public static void renderLocal(BuildingModel model, boolean active, float time, MatrixStack matrices, VertexConsumer vc,
								   int light, int argb, float ox, float oz, float roll) {
		Function<Identifier, Sprite> atlas = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		parts(model, active, time, matrices, vc, light, argb, atlas, ox, oz, roll);
	}

	private static void parts(BuildingModel model, boolean active, float time, MatrixStack matrices, VertexConsumer vc, int light,
							  int argb, Function<Identifier, Sprite> atlas, float ax, float azp1, float roll) {
		for (BuildingModel.Part p : model.parts()) {
			// footprint-local (X right, Z forward) -> north layout relative to the controller cell
			float x0 = p.x0() - ax;
			float x1 = p.x1() - ax;
			float z0 = azp1 - p.z1();
			float z1 = azp1 - p.z0();
			boolean glow = (p.flags() & BuildingModel.ALWAYS_GLOW) != 0 || (active && (p.flags() & BuildingModel.GLOW_WHEN_ACTIVE) != 0);
			int l = glow ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
			int color = argb != -1 ? argb : (0xFF000000 | p.tint());
			matrices.push();
			if (active && (p.flags() & BuildingModel.SPIN_WHEN_ACTIVE) != 0) {
				float cx = (x0 + x1) / 2f;
				float cz = (z0 + z1) / 2f;
				matrices.translate(cx, 0, cz);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 12f));
				matrices.translate(-cx, 0, -cz);
			}
			if ((p.flags() & BuildingModel.WHEEL) != 0 && p.pivot() != null && roll != 0f) {
				// forward is -Z in this frame, so a wheel rolling forward turns by a negative angle around +X
				float py = p.pivot()[0];
				float pz = azp1 - p.pivot()[1];
				matrices.translate(0, py, pz);
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(-roll / p.pivot()[2]));
				matrices.translate(0, -py, -pz);
			}
			MatrixStack.Entry e = matrices.peek();
			Identifier[] tex = p.tex();
			Identifier[] act = p.activeTex();
			// DOWN, UP, FRONT(north), BACK(south), LEFT(west), RIGHT(east)
			for (int f = 0; f < 6; f++) {
				Identifier id = active && act[f] != null ? act[f] : tex[f];
				Sprite sprite = atlas.apply(id);
				face(vc, e, f, x0, p.y0(), z0, x1, p.y1(), z1, sprite, color, l, (p.flags() & BuildingModel.STRETCH) != 0);
			}
			matrices.pop();
		}
	}

	private static void face(VertexConsumer vc, MatrixStack.Entry e, int f, float x0, float y0, float z0, float x1, float y1, float z1,
							 Sprite s, int color, int light, boolean st) {
		// origin, S axis, T axis (texture "up"), lengths, normal
		switch (f) {
			case BuildingModel.DOWN -> tiled(vc, e, x0, y0, z1, 1, 0, 0, 0, 0, -1, x1 - x0, z1 - z0, 0, -1, 0, s, color, light, st);
			case BuildingModel.UP -> tiled(vc, e, x0, y1, z1, 1, 0, 0, 0, 0, -1, x1 - x0, z1 - z0, 0, 1, 0, s, color, light, st);
			case BuildingModel.FRONT -> tiled(vc, e, x1, y0, z0, -1, 0, 0, 0, 1, 0, x1 - x0, y1 - y0, 0, 0, -1, s, color, light, st);
			case BuildingModel.BACK -> tiled(vc, e, x0, y0, z1, 1, 0, 0, 0, 1, 0, x1 - x0, y1 - y0, 0, 0, 1, s, color, light, st);
			case BuildingModel.LEFT -> tiled(vc, e, x0, y0, z0, 0, 0, 1, 0, 1, 0, z1 - z0, y1 - y0, -1, 0, 0, s, color, light, st);
			default -> tiled(vc, e, x1, y0, z1, 0, 0, -1, 0, 1, 0, z1 - z0, y1 - y0, 1, 0, 0, s, color, light, st);
		}
	}

	private static void tiled(VertexConsumer vc, MatrixStack.Entry e, float ox, float oy, float oz,
							  float sx, float sy, float sz, float tx, float ty, float tz, float lenS, float lenT,
							  float nx, float ny, float nz, Sprite sprite, int color, int light, boolean stretch) {
		if (lenS <= 0 || lenT <= 0) return;
		Matrix4f m = e.getPositionMatrix();
		float du = sprite.getMaxU() - sprite.getMinU();
		float dv = sprite.getMaxV() - sprite.getMinV();
		float stepS = stretch ? lenS : 1f;
		float stepT = stretch ? lenT : 1f;
		for (float s0 = 0; s0 < lenS - 1e-4f; s0 += stepS) {
			float s1 = Math.min(lenS, s0 + stepS);
			for (float t0 = 0; t0 < lenT - 1e-4f; t0 += stepT) {
				float t1 = Math.min(lenT, t0 + stepT);
				float u0 = sprite.getMinU();
				float u1 = stretch ? sprite.getMaxU() : sprite.getMinU() + du * (s1 - s0);
				float vTop = stretch ? sprite.getMinV() : sprite.getMinV() + dv * (1f - (t1 - t0));
				float vBot = sprite.getMaxV();
				// bottom-left, bottom-right, top-right, top-left
				v(vc, m, e, ox + sx * s0 + tx * t0, oy + sy * s0 + ty * t0, oz + sz * s0 + tz * t0, u0, vBot, color, light, nx, ny, nz);
				v(vc, m, e, ox + sx * s1 + tx * t0, oy + sy * s1 + ty * t0, oz + sz * s1 + tz * t0, u1, vBot, color, light, nx, ny, nz);
				v(vc, m, e, ox + sx * s1 + tx * t1, oy + sy * s1 + ty * t1, oz + sz * s1 + tz * t1, u1, vTop, color, light, nx, ny, nz);
				v(vc, m, e, ox + sx * s0 + tx * t1, oy + sy * s0 + ty * t1, oz + sz * s0 + tz * t1, u0, vTop, color, light, nx, ny, nz);
			}
		}
	}

	private static void v(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e, float x, float y, float z, float u, float v,
						  int argb, int light, float nx, float ny, float nz) {
		vc.vertex(m, x, y, z)
				.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(e, nx, ny, nz);
	}
}
