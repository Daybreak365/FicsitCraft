package com.ficsitcraft.client.render;

import com.ficsitcraft.block.PowerPoleBlock;
import com.ficsitcraft.client.building.BuildingMesh;
import com.ficsitcraft.client.building.BuildingModel;
import com.ficsitcraft.client.building.BuildingModels;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.multiblock.Multiblocks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * While holding a multi-block building (or a tall power pole), shows a translucent hologram of where it
 * would be built. Blue = OK, red = blocked / invalid (e.g. a miner not on a resource node).
 */
public final class PlacementHologram {
	private static final int OK_COLOR = 0x7055C8FF;
	private static final int BAD_COLOR = 0x80FF4A3A;
	private static final Map<Block, BuildingModel> POLE_MODELS = new HashMap<>();

	private PlacementHologram() {
	}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null || client.options.hudHidden) return;
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof BlockItem bi)) return;
		Block block = bi.getBlock();

		BuildingModel model;
		Footprint fp;
		if (block instanceof PowerPoleBlock pole) {
			model = POLE_MODELS.computeIfAbsent(block, b -> BuildingModels.pole(pole));
			fp = model.footprint;
		} else {
			model = BuildingModels.get(block);
			fp = Multiblocks.get(block);
			if (model == null || fp == null) return;
		}
		if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

		// same position rule as vanilla block placement
		BlockPos pos = hit.getBlockPos();
		BlockState clicked = world.getBlockState(pos);
		if (!clicked.isReplaceable()) pos = pos.offset(hit.getSide());
		Direction facing = player.getHorizontalFacing();

		boolean ok;
		if (block instanceof PowerPoleBlock) {
			ok = true;
			for (int i = 0; i < fp.height(); i++) {
				BlockPos p = pos.up(i);
				if (world.isOutOfHeightLimit(p) || !world.getBlockState(p).isReplaceable()) ok = false;
			}
		} else {
			ok = Multiblocks.canPlace(world, block, pos, facing) == Multiblocks.Check.OK;
		}

		MatrixStack matrices = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
		Vec3d cam = ctx.camera().getPos();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		matrices.push();
		matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
		BuildingMesh.render(model, facing, false, 0, matrices,
				immediate.getBuffer(RenderLayer.getEntityTranslucent(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)),
				LightmapTextureManager.MAX_LIGHT_COORDINATE, ok ? OK_COLOR : BAD_COLOR);
		immediate.draw();

		// footprint outline
		BlockPos min = null;
		BlockPos max = null;
		for (int y = 0; y < fp.height(); y += Math.max(1, fp.height() - 1)) {
			for (int row = 0; row < fp.depth(); row += Math.max(1, fp.depth() - 1)) {
				for (int col = 0; col < fp.width(); col += Math.max(1, fp.width() - 1)) {
					BlockPos o = fp.offset(facing, col, y, row);
					min = min == null ? o : new BlockPos(Math.min(min.getX(), o.getX()), Math.min(min.getY(), o.getY()), Math.min(min.getZ(), o.getZ()));
					max = max == null ? o : new BlockPos(Math.max(max.getX(), o.getX()), Math.max(max.getY(), o.getY()), Math.max(max.getZ(), o.getZ()));
				}
			}
		}
		if (min != null) {
			Box box = new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).expand(0.01);
			float r = ok ? 0.35f : 1f;
			float g = ok ? 0.8f : 0.3f;
			float b = ok ? 1f : 0.25f;
			WorldRenderer.drawBox(matrices, immediate.getBuffer(RenderLayer.getLines()), box, r, g, b, 0.9f);
			// output direction marker
			if (!(block instanceof PowerPoleBlock) && fp.depth() > 0) {
				BlockPos out = fp.offset(facing, fp.ax(), 0, fp.depth() - 1).offset(facing);
				Box arrow = new Box(out.getX() + 0.3, 0.02, out.getZ() + 0.3, out.getX() + 0.7, 0.12, out.getZ() + 0.7);
				WorldRenderer.drawBox(matrices, immediate.getBuffer(RenderLayer.getLines()), arrow, 1f, 0.6f, 0.2f, 0.9f);
			}
			immediate.draw();
		}
		matrices.pop();
	}
}
