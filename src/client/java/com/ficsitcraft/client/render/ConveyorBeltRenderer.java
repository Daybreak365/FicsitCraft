package com.ficsitcraft.client.render;

import com.ficsitcraft.block.BeltShape;
import com.ficsitcraft.blockentity.ConveyorBeltBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Renders the items travelling on a conveyor belt, following straights, curves and ramps. */
public class ConveyorBeltRenderer implements BlockEntityRenderer<ConveyorBeltBlockEntity> {
	private static final double SURFACE = 0.30;
	private final ItemRenderer itemRenderer;

	public ConveyorBeltRenderer(BlockEntityRendererFactory.Context ctx) {
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(ConveyorBeltBlockEntity belt, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
					   int light, int overlay) {
		if (belt.getItems().isEmpty()) return;
		Direction dir = belt.getFacing();
		boolean vertical = dir.getAxis() == Direction.Axis.Y;
		BeltShape shape = vertical ? BeltShape.STRAIGHT : belt.getShape();
		int seed = (int) belt.getPos().asLong();
		for (ConveyorBeltBlockEntity.BeltItem it : belt.getItems()) {
			float p = MathHelper.lerp(tickDelta, it.prevProgress, it.progress);
			matrices.push();
			if (vertical) {
				matrices.translate(0.5, 0.5 + dir.getOffsetY() * (p - 0.5), 0.5);
				matrices.scale(0.45f, 0.45f, 0.45f);
			} else {
				// local coordinates for a belt flowing north (towards -z); x/z in [0,1]
				double lx;
				double lz;
				double y = SURFACE;
				float yawOffset = 0; // extra rotation along curves
				switch (shape) {
					case TURN_LEFT -> {
						double a = p * Math.PI / 2;
						lx = 0.5 * Math.sin(a);
						lz = 0.5 * Math.cos(a);
						yawOffset = (1 - p) * 90f;
					}
					case TURN_RIGHT -> {
						double a = p * Math.PI / 2;
						lx = 1 - 0.5 * Math.sin(a);
						lz = 0.5 * Math.cos(a);
						yawOffset = -(1 - p) * 90f;
					}
					case ASCENDING -> {
						lx = 0.5;
						lz = 1 - p;
						y = SURFACE + p;
					}
					case DESCENDING -> {
						lx = 0.5;
						lz = 1 - p;
						y = SURFACE + 1 - p;
					}
					default -> {
						lx = 0.5;
						lz = 1 - p;
					}
				}
				double wx;
				double wz;
				switch (dir) {
					case EAST -> {
						wx = 1 - lz;
						wz = lx;
					}
					case SOUTH -> {
						wx = 1 - lx;
						wz = 1 - lz;
					}
					case WEST -> {
						wx = lz;
						wz = 1 - lx;
					}
					default -> {
						wx = lx;
						wz = lz;
					}
				}
				matrices.translate(wx, y, wz);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-dir.asRotation() + yawOffset));
				if (shape.isRamp()) {
					float tilt = shape == BeltShape.ASCENDING ? 45f : -45f;
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
				}
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
				matrices.scale(0.45f, 0.45f, 0.45f);
			}
			itemRenderer.renderItem(it.stack, ModelTransformationMode.FIXED, light, overlay, matrices, vertexConsumers, belt.getWorld(), seed++);
			matrices.pop();
		}
	}
}
