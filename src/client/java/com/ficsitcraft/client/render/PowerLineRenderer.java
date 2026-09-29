package com.ficsitcraft.client.render;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws the sagging power lines between power nodes (each line is drawn by its "smaller" end). */
public class PowerLineRenderer<T extends PowerNodeBlockEntity> implements BlockEntityRenderer<T> {
	private static final Identifier TEXTURE = FicsitCraft.id("textures/misc/power_line.png");
	private static final int SEGMENTS = 16;
	private static final float HALF_WIDTH = 0.035f;

	public PowerLineRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public void render(T node, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		renderLines(node, matrices, vertexConsumers, light);
	}

	public static void renderLines(PowerNodeBlockEntity node, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		if (node.getConnections().isEmpty() || node.getWorld() == null) return;
		BlockPos pos = node.getPos();
		VertexConsumer vc = null;
		for (BlockPos other : node.getConnections()) {
			if (other.asLong() < pos.asLong()) continue; // drawn by the other end
			Vec3d otherOffset = node.getWorld().getBlockEntity(other) instanceof PowerNodeBlockEntity o
					? o.getConnectorOffset() : new Vec3d(0.5, 1.0, 0.5);
			if (vc == null) vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
			Vec3d a = node.getConnectorOffset();
			Vec3d b = new Vec3d(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ()).add(otherOffset);
			drawLine(vc, matrices.peek(), a, b, light);
		}
	}

	private static void drawLine(VertexConsumer vc, MatrixStack.Entry entry, Vec3d a, Vec3d b, int light) {
		// same curve as the zipline uses (PowerLineGeometry)
		Vec3d prev = a;
		for (int i = 1; i <= SEGMENTS; i++) {
			Vec3d cur = com.ficsitcraft.power.PowerLineGeometry.point(a, b, i / (double) SEGMENTS);
			segment(vc, entry, prev, cur, light);
			prev = cur;
		}
	}

	private static void segment(VertexConsumer vc, MatrixStack.Entry entry, Vec3d p0, Vec3d p1, int light) {
		Vec3d dir = p1.subtract(p0);
		if (dir.lengthSquared() < 1e-8) return;
		Vec3d up = new Vec3d(0, 1, 0);
		Vec3d side = dir.crossProduct(up);
		if (side.lengthSquared() < 1e-8) side = new Vec3d(1, 0, 0);
		side = side.normalize().multiply(HALF_WIDTH);
		Vec3d vert = dir.crossProduct(side).normalize().multiply(HALF_WIDTH);
		quad(vc, entry, p0, p1, side, light);
		quad(vc, entry, p0, p1, vert, light);
	}

	private static void quad(VertexConsumer vc, MatrixStack.Entry entry, Vec3d p0, Vec3d p1, Vec3d off, int light) {
		Matrix4f m = entry.getPositionMatrix();
		Vec3d n = off.normalize();
		vertex(vc, m, entry, p0.subtract(off), 0, 0, n, light);
		vertex(vc, m, entry, p0.add(off), 0, 1, n, light);
		vertex(vc, m, entry, p1.add(off), 1, 1, n, light);
		vertex(vc, m, entry, p1.subtract(off), 1, 0, n, light);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, MatrixStack.Entry entry, Vec3d p, float u, float v, Vec3d n, int light) {
		vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(entry, (float) n.x, (float) n.y, (float) n.z);
	}

	@Override
	public boolean rendersOutsideBoundingBox(T blockEntity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 160;
	}
}
