package com.ficsitcraft.client.railway;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/** Players that currently sit in a locomotive cab (the local player, plus the others as announced by the server). */
public final class SeatedRiders {
	private static final Set<Integer> REMOTE = new HashSet<>();

	private SeatedRiders() {
	}

	public static void setRemote(int entityId, boolean seated) {
		if (seated) REMOTE.add(entityId);
		else REMOTE.remove(entityId);
	}

	public static void clear() {
		REMOTE.clear();
	}

	public static boolean isSeated(Entity e) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.player != null && e == c.player) return TrainRide.isRiding();
		return REMOTE.contains(e.getId());
	}
}
