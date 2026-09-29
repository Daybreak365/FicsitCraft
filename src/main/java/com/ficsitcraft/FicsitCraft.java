package com.ficsitcraft;

import com.ficsitcraft.data.Buildings;
import com.ficsitcraft.data.Milestones;
import com.ficsitcraft.data.Recipes;
import com.ficsitcraft.item.CableItem;
import com.ficsitcraft.power.PowerGridManager;
import com.ficsitcraft.registry.ModBlockEntities;
import com.ficsitcraft.registry.ModBlocks;
import com.ficsitcraft.registry.ModItemGroups;
import com.ficsitcraft.registry.ModItems;
import com.ficsitcraft.registry.ModScreenHandlers;
import com.ficsitcraft.registry.ModWorldGen;
import com.ficsitcraft.network.PowerEventPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FicsitCraft implements ModInitializer {
	public static final String MOD_ID = "ficsitcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String STARTER_TAG = "ficsitcraft.starter_kit";

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModScreenHandlers.init();
		ModItemGroups.init();
		com.ficsitcraft.registry.ModSounds.init();
		ModWorldGen.init();

		Recipes.init();
		Milestones.init();
		Buildings.init();

		PayloadTypeRegistry.playS2C().register(PowerEventPayload.ID, PowerEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.ficsitcraft.network.MilestonePayload.ID, com.ficsitcraft.network.MilestonePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.ficsitcraft.network.ZiplinePayload.ID, com.ficsitcraft.network.ZiplinePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.ficsitcraft.network.ZiplineStatePayload.ID, com.ficsitcraft.network.ZiplineStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.ficsitcraft.network.TrainSeatPayload.ID, com.ficsitcraft.network.TrainSeatPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.ficsitcraft.network.RailNetPayload.ID, com.ficsitcraft.network.RailNetPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.ficsitcraft.network.RailActionPayload.ID, com.ficsitcraft.network.RailActionPayload.CODEC);
		com.ficsitcraft.railway.RailNet.registerServer();
		ServerTickEvents.END_WORLD_TICK.register(com.ficsitcraft.railway.RailWorld::onWorldTick);
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				(player, origin, destination) -> com.ficsitcraft.railway.RailNet.sendAll(player));
		com.ficsitcraft.item.ZiplineItem.registerServer();
		ServerTickEvents.END_WORLD_TICK.register(com.ficsitcraft.fluid.FluidNetworkManager::onWorldTickEnd);
		FicsitCommands.register();
		ServerTickEvents.END_WORLD_TICK.register(PowerGridManager::onWorldTickEnd);
		ServerTickEvents.END_SERVER_TICK.register(server -> CableItem.cleanupPending());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> com.ficsitcraft.railway.RailNet.sendAll(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			com.ficsitcraft.item.RailwayItem.forget(handler.player.getUuid());
			for (net.minecraft.server.world.ServerWorld w : server.getWorlds()) {
				com.ficsitcraft.railway.RailWorld.get(w).stopRiding(w, handler.player.getUuid());
			}
		});

		// FICSIT onboarding: every pioneer gets a HUB, a Craft Bench and a Build Gun on first join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (player.getCommandTags().contains(STARTER_TAG)) return;
			player.addCommandTag(STARTER_TAG);
			player.getInventory().offerOrDrop(new ItemStack(ModBlocks.HUB));
			player.getInventory().offerOrDrop(new ItemStack(ModBlocks.CRAFT_BENCH));
			player.getInventory().offerOrDrop(new ItemStack(ModItems.BUILD_GUN));
			player.sendMessage(Text.translatable("message.ficsitcraft.welcome").formatted(Formatting.GOLD), false);
		});

		LOGGER.info("FICSIT Craft initialized. Welcome, Pioneer.");
	}
}
