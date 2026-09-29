package com.ficsitcraft.client.railway;

import java.util.UUID;

/** Small helpers for the client -> server rail messages. */
public final class RailClientNet {
	private RailClientNet() {
	}

	public static void send2(int type, UUID a, UUID b) {
		ClientRail.send(type, o -> {
			ClientRail.writeUuid(o, a);
			ClientRail.writeUuid(o, b);
		});
	}
}
