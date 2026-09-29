package com.ficsitcraft.item;

import com.ficsitcraft.network.ZiplinePayload;
import com.ficsitcraft.network.ZiplineStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zipline: while held, jump at a power line (or right-click it) to hang on, W/S to slide along it in the direction
 * you look, automatic transfer at poles, Space to jump off, Sneak to let go. Movement is simulated on the client
 * (like all player movement); the server only keeps the rider from being treated as "flying".
 */
public class ZiplineItem extends Item {
	private static final Set<UUID> RIDERS = new HashSet<>();

	public ZiplineItem(Settings settings) {
		super(settings);
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(ZiplinePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (payload.attached()) {
				RIDERS.add(player.getUuid());
			} else {
				RIDERS.remove(player.getUuid());
				player.removeStatusEffect(StatusEffects.LEVITATION);
				player.fallDistance = 0;
			}
			// let everyone who can see this player know, so they can show the arm pose and sparks
			ZiplineStatePayload state = new ZiplineStatePayload(player.getId(), payload.attached());
			for (ServerPlayerEntity other : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
				if (other != player) ServerPlayNetworking.send(other, state);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RIDERS.isEmpty()) return;
			RIDERS.removeIf(id -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				if (p == null) return true;
				// A hidden, zero-strength levitation keeps the anti-fly check quiet while hanging on the line.
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 5, 0, false, false, false));
				p.fallDistance = 0;
				return false;
			});
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RIDERS.remove(handler.player.getUuid()));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ficsitcraft.zipline").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ficsitcraft.zipline2").formatted(Formatting.DARK_GRAY));
	}

	public static boolean isHolding(PlayerEntity player) {
		return player.getMainHandStack().getItem() instanceof ZiplineItem || player.getOffHandStack().getItem() instanceof ZiplineItem;
	}
}
