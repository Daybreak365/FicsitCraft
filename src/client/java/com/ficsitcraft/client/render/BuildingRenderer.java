package com.ficsitcraft.client.render;

import com.ficsitcraft.block.MachineBlock;
import com.ficsitcraft.client.building.BuildingMesh;
import com.ficsitcraft.client.building.BuildingModel;
import com.ficsitcraft.client.building.BuildingModels;
import com.ficsitcraft.multiblock.Footprint;
import com.ficsitcraft.power.PowerNodeBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Draws multi-block buildings (and their power lines, if any). */
public class BuildingRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
	public BuildingRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public void render(T be, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		BlockState state = be.getCachedState();
		BuildingModel model = BuildingModels.get(state.getBlock());
		if (model != null && be.getWorld() != null && state.contains(MachineBlock.FACING)) {
			Direction facing = state.get(MachineBlock.FACING);
			boolean active = state.contains(MachineBlock.ACTIVE) && state.get(MachineBlock.ACTIVE);
			float time = be.getWorld().getTime() + tickDelta;
			Footprint fp = model.footprint;
			// light sampled just above the roof so the building isn't dark inside its own (invisible) parts
			BlockPos probe = be.getPos().add(fp.offset(facing, fp.width() / 2, 0, fp.depth() / 2)).up(fp.height());
			int l = Math.max(light, WorldRenderer.getLightmapCoordinates(be.getWorld(), probe));
			BuildingMesh.render(model, facing, active, time, matrices,
					consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)), l, -1);
		}
		if (be instanceof com.ficsitcraft.blockentity.FluidBufferBlockEntity tank && model != null && state.contains(MachineBlock.FACING)) {
			renderTankLevel(tank, model, state.get(MachineBlock.FACING), tickDelta, matrices, consumers, light);
		}
		if (be instanceof PowerNodeBlockEntity node) PowerLineRenderer.renderLines(node, matrices, consumers, light);
	}

	/** Coloured fluid column in the tank's front and back windows. */
	private static void renderTankLevel(com.ficsitcraft.blockentity.FluidBufferBlockEntity tank, BuildingModel model, Direction facing,
										float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		com.ficsitcraft.fluid.SfFluid fluid = tank.getFluid();
		double frac = tank.getDisplayAmount(delta) / tank.getCapacity();
		if (fluid == com.ficsitcraft.fluid.SfFluid.NONE || frac < 0.003) return;
		boolean lava = fluid == com.ficsitcraft.fluid.SfFluid.LAVA;
		double top = 0.55 + (3.4 - 0.55) * Math.min(1, frac);
		String tex = lava ? "minecraft:block/lava_still" : "minecraft:block/water_still";
		BuildingModel level = BuildingModel.builder(model.footprint)
				.box(1.22, 0.55, 2.72, 1.78, top, 2.74).all(tex).tint(lava ? 0xFFFFFF : fluid.color).done()
				.box(1.22, 0.55, 0.26, 1.78, top, 0.28).all(tex).tint(lava ? 0xFFFFFF : fluid.color).done()
				.build();
		int l = lava ? net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		BuildingMesh.render(level, facing, false, 0, matrices,
				consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)), l, -1);
	}

	@Override
	public boolean rendersOutsideBoundingBox(T blockEntity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 128;
	}
}
