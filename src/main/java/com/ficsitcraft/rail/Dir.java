package com.ficsitcraft.rail;

/** A track traversed in a direction: forward = from node A to node B. */
public record Dir(long track, boolean forward) {
	public Dir flip() {
		return new Dir(track, !forward);
	}
}
