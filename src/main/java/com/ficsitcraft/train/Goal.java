package com.ficsitcraft.train;

/** A stopping point: the train's leading end must come to rest {@code s} blocks into the track, driving in the given direction. */
public record Goal(long track, boolean forward, double s) {
}
