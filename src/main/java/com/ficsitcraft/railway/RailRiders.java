package com.ficsitcraft.railway;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players currently sitting in a locomotive cab (the server adds them in {@link RailWorld#startRiding}, the client adds the
 * local player when the ride starts). Mixins use it to keep riders from being pushed out of / suffocating in blocks: the
 * cab moves through terrain that the client-driven position does not care about.
 */
public final class RailRiders {
	private static final Set<UUID> RIDING = ConcurrentHashMap.newKeySet();

	private RailRiders() {
	}

	public static void add(UUID player) {
		RIDING.add(player);
	}

	public static void remove(UUID player) {
		RIDING.remove(player);
	}

	public static boolean isRiding(UUID player) {
		return !RIDING.isEmpty() && RIDING.contains(player);
	}
}
