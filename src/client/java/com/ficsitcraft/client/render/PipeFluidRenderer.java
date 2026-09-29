package com.ficsitcraft.client.render;

import com.ficsitcraft.block.PipeBlock;
import com.ficsitcraft.blockentity.PipeBlockEntity;
import com.ficsitcraft.fluid.SfFluid;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

/**
 * Draws the fluid inside a glass pipe: the level follows the fill amount (horizontal runs show a partially filled
 * channel, vertical runs fill from the bottom), coloured by the fluid type, using the animated vanilla fluid texture.
 */
public class PipeFluidRenderer implements BlockEntityRenderer<PipeBlockEntity> {
	private static final Identifier WATER = Identifier.of("minecraft", "block/water_still");
	private static final Identifier LAVA = Identifier.of("minecraft", "block/lava_still");
	private static final float IN = 6.25f / 16f;   // inner tube bounds
	private static final float OUT = 9.75f / 16f;
	private static final float CORE_IN = 5.4f / 16f;
	private static final float CORE_OUT = 10.6f / 16f;

	public PipeFluidRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public void render(PipeBlockEntity pipe, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		SfFluid fluid = pipe.getFluid();
		double amount = pipe.getDisplayAmount(tickDelta);
		if (fluid == SfFluid.NONE || amount < 0.002) return;
		float fill = (float) Math.min(1, amount / pipe.getCapacity());
		BlockState state = pipe.getCachedState();
		if (!(state.getBlock() instanceof PipeBlock)) return;

		Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
				.apply(fluid == SfFluid.LAVA ? LAVA : WATER);
		int color = fluid == SfFluid.LAVA ? 0xFFFFFF : fluid.color;
		int l = fluid == SfFluid.LAVA ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
		MatrixStack.Entry e = matrices.peek();

		// liquid surface height inside the core (a vertical column is "full" once any fluid stands in it)
		float coreTop = CORE_IN + (CORE_OUT - CORE_IN) * fill;
		box(vc, e, CORE_IN, CORE_IN, CORE_IN, CORE_OUT, coreTop, CORE_OUT, sprite, color, l);

		for (Direction d : Direction.values()) {
			if (!state.get(PipeBlock.PROPS.get(d))) continue;
			float x0 = IN, y0 = IN, z0 = IN, x1 = OUT, y1 = IN + (OUT - IN) * fill, z1 = OUT;
			switch (d) {
				case NORTH -> {
					z0 = 0;
					z1 = CORE_IN;
				}
				case SOUTH -> {
					z0 = CORE_OUT;
					z1 = 1;
				}
				case WEST -> {
					x0 = 0;
					x1 = CORE_IN;
				}
				case EAST -> {
					x0 = CORE_OUT;
					x1 = 1;
				}
				case DOWN -> {
					y0 = 0;
					y1 = CORE_IN;
					if (fill < 0.02) continue;
				}
				case UP -> {
					// fluid only rises into the upper arm when the segment is (nearly) full
					y0 = CORE_OUT;
					y1 = CORE_OUT + (1 - CORE_OUT) * Math.max(0, (fill - 0.85f) / 0.15f);
					if (y1 <= y0 + 0.001f) continue;
				}
			}
			box(vc, e, x0, y0, z0, x1, y1, z1, sprite, color, l);
		}
	}

	private static void box(VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float z0, float x1, float y1, float z1,
							Sprite s, int rgb, int light) {
		if (y1 <= y0) return;
		Matrix4f m = e.getPositionMatrix();
		float u0 = s.getMinU(), v0 = s.getMinV();
		float du = s.getMaxU() - u0, dv = s.getMaxV() - v0;
		// top
		quad(vc, m, e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0 + du * x0, v0 + dv * z0, u0 + du * x1, v0 + dv * z1, rgb, light, 0, 1, 0);
		// bottom
		quad(vc, m, e, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u0 + du * x0, v0 + dv * z0, u0 + du * x1, v0 + dv * z1, rgb, light, 0, -1, 0);
		// north / south
		quad(vc, m, e, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0 + du * x0, v0 + dv * (1 - y1), u0 + du * x1, v0 + dv * (1 - y0), rgb, light, 0, 0, -1);
		quad(vc, m, e, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0 + du * x0, v0 + dv * (1 - y1), u0 + du * x1, v0 + dv * (1 - y0), rgb, light, 0, 0, 1);
		// west / east
		quad(vc, m, e, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0 + du * z0, v0 + dv * (1 - y1), u0 + du * z1, v0 + dv * (1 - y0), rgb, light, -1, 0, 0);
		quad(vc, m, e, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0 + du * z0, v0 + dv * (1 - y1), u0 + du * z1, v0 + dv * (1 - y0), rgb, light, 1, 0, 0);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e,
							 float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
							 float u0, float v0, float u1, float v1, int rgb, int light, float nx, float ny, float nz) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF, a = 215;
		vc.vertex(m, ax, ay, az).color(r, g, b, a).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
		vc.vertex(m, bx, by, bz).color(r, g, b, a).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
		vc.vertex(m, cx, cy, cz).color(r, g, b, a).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
		vc.vertex(m, dx, dy, dz).color(r, g, b, a).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
	}
}
