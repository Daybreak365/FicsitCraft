package com.ficsitcraft.client;

import com.ficsitcraft.client.render.ConveyorBeltRenderer;
import com.ficsitcraft.client.render.PowerLineRenderer;
import com.ficsitcraft.client.render.BuildingRenderer;
import com.ficsitcraft.client.render.PlacementHologram;
import com.ficsitcraft.client.building.BuildingModels;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.ficsitcraft.client.screen.BuildGunScreen;
import com.ficsitcraft.client.screen.CraftBenchScreen;
import com.ficsitcraft.client.screen.GeneratorScreen;
import com.ficsitcraft.client.screen.HubScreen;
import com.ficsitcraft.client.screen.MinerScreen;
import com.ficsitcraft.client.screen.ProcessingMachineScreen;
import com.ficsitcraft.client.screen.PowerPoleScreen;
import com.ficsitcraft.client.hud.PowerToast;
import com.ficsitcraft.network.PowerEventPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class FicsitCraftClient implements ClientModInitializer {
	public static net.minecraft.client.option.KeyBinding dismantleKey;
	public static net.minecraft.client.option.KeyBinding hornKey;
	public static net.minecraft.client.option.KeyBinding menuKey;

	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.PROCESSING_MACHINE, ProcessingMachineScreen::new);
		HandledScreens.register(ModScreenHandlers.MINER, MinerScreen::new);
		HandledScreens.register(ModScreenHandlers.GENERATOR, GeneratorScreen::new);
		HandledScreens.register(ModScreenHandlers.HUB, HubScreen::new);
		HandledScreens.register(ModScreenHandlers.POWER_POLE, PowerPoleScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(com.ficsitcraft.network.MilestonePayload.ID, (payload, context) -> {
			context.client().getToastManager().add(new com.ficsitcraft.client.hud.MilestoneToast(payload.milestone()));
			context.client().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f));
		});
		ClientPlayNetworking.registerGlobalReceiver(com.ficsitcraft.network.ZiplineStatePayload.ID,
				(payload, context) -> com.ficsitcraft.client.zipline.ZiplineClient.setRemote(payload.entityId(), payload.riding()));
		ClientPlayNetworking.registerGlobalReceiver(PowerEventPayload.ID, (payload, context) -> {
			MinecraftClient client = context.client();
			client.getToastManager().add(new PowerToast(payload));
			if (payload.kind() == PowerEventPayload.FUSE_BLOWN) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.0f));
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 0.25f));
			} else {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 0.8f));
			}
		});
		HandledScreens.register(ModScreenHandlers.CRAFT_BENCH, CraftBenchScreen::new);
		HandledScreens.register(ModScreenHandlers.BUILD_GUN, BuildGunScreen::new);

		BlockEntityRendererFactories.register(ModBlockEntities.CONVEYOR_BELT, ConveyorBeltRenderer::new);
		BuildingModels.init();
		com.ficsitcraft.client.scanner.ResourceScanner.register();
		BlockEntityRendererFactories.register(ModBlockEntities.PROCESSING_MACHINE, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.MINER, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.GENERATOR, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.STORAGE_CONTAINER, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.DISPLAY, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.WATER_EXTRACTOR, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.CREATIVE_GENERATOR, BuildingRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.PIPELINE_PUMP, PowerLineRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.PIPE, com.ficsitcraft.client.render.PipeFluidRenderer::new);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(PlacementHologram::render);
		HandledScreens.register(ModScreenHandlers.FLUID_BUFFER, com.ficsitcraft.client.screen.FluidBufferScreen::new);
		BlockEntityRendererFactories.register(ModBlockEntities.FLUID_BUFFER, BuildingRenderer::new);
		// station / platforms are invisible blocks (multi-block footprint) drawn by this renderer
		BlockEntityRendererFactories.register(ModBlockEntities.RAIL_BUILDING, BuildingRenderer::new);
		HandledScreens.register(ModScreenHandlers.PIPE, com.ficsitcraft.client.screen.PipeScreen::new);
		HandledScreens.register(ModScreenHandlers.CREATIVE_GENERATOR, com.ficsitcraft.client.screen.CreativeGeneratorScreen::new);
		HandledScreens.register(ModScreenHandlers.PLATFORM, com.ficsitcraft.client.screen.PlatformScreen::new);
		HandledScreens.register(ModScreenHandlers.FLUID_MACHINE, com.ficsitcraft.client.screen.FluidMachineScreen::new);
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), ModBlocks.PIPELINE_MK1, ModBlocks.PIPELINE_MK2);
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PIPELINE_PUMP, RenderLayer.getCutout());
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
				com.ficsitcraft.client.zipline.ZiplineClient::tick);
		BlockEntityRendererFactories.register(ModBlockEntities.POWER_POLE, PowerLineRenderer::new);

		initRailway();
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
				ModBlocks.CONVEYOR_BELT_MK1, ModBlocks.CONVEYOR_BELT_MK2, ModBlocks.CONVEYOR_BELT_MK3,
				ModBlocks.POWER_POLE_MK1, ModBlocks.POWER_POLE_MK2, ModBlocks.POWER_POLE_MK3,
				ModBlocks.SMELTER, ModBlocks.FOUNDRY, ModBlocks.CONSTRUCTOR, ModBlocks.ASSEMBLER, ModBlocks.MANUFACTURER,
				ModBlocks.MINER_MK1, ModBlocks.MINER_MK2, ModBlocks.BIOMASS_BURNER, ModBlocks.COAL_GENERATOR,
				ModBlocks.SPLITTER, ModBlocks.MERGER, ModBlocks.HUB, ModBlocks.CRAFT_BENCH, ModBlocks.STORAGE_CONTAINER,
				ModBlocks.TRAIN_STATION, ModBlocks.FREIGHT_PLATFORM, ModBlocks.FLUID_FREIGHT_PLATFORM, ModBlocks.EMPTY_PLATFORM,
				ModBlocks.CREATIVE_GENERATOR);
	}

	/** Trains: network receiver, world rendering, cab controls and the dismantle / horn / menu keys. */
	private static void initRailway() {
		ClientPlayNetworking.registerGlobalReceiver(com.ficsitcraft.network.RailNetPayload.ID,
				(payload, context) -> com.ficsitcraft.client.railway.ClientRail.onPayload(payload, context.client()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			com.ficsitcraft.client.railway.ClientRail.reset();
			com.ficsitcraft.client.railway.TrainAudio.reset();
			com.ficsitcraft.client.railway.TrainRide.stop(client);
		});
		WorldRenderEvents.AFTER_TRANSLUCENT.register(com.ficsitcraft.client.railway.RailRenderer::render);
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(com.ficsitcraft.client.railway.TrainRide::renderHud);
		dismantleKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
				"key.ficsitcraft.dismantle", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_X, "category.ficsitcraft"));
		hornKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
				"key.ficsitcraft.horn", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_H, "category.ficsitcraft"));
		menuKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
				"key.ficsitcraft.train_menu", net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_G, "category.ficsitcraft"));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> {
			com.ficsitcraft.client.railway.ClientRail.applyPending(client);
			com.ficsitcraft.client.railway.ClientRail.tickVisuals(client);
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			com.ficsitcraft.client.railway.TrainRide.tick(client);
			com.ficsitcraft.client.railway.TrainAudio.tick(client);
			com.ficsitcraft.client.railway.TrainFx.tick(client);
			while (dismantleKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null && !com.ficsitcraft.client.railway.TrainRide.isRiding()) {
					com.ficsitcraft.client.railway.RailInteract.dismantle(client);
				}
			}
		});
	}
}
