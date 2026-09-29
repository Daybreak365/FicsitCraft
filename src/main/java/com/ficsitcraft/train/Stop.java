package com.ficsitcraft.train;

/** One timetable entry. mode 0: wait {@code seconds}; mode 1: wait until the platforms are done loading / unloading. */
public record Stop(String station, int mode, int seconds) {
	public static final int WAIT_SECONDS = 0, WAIT_LOADED = 1;
	public static final int MAX_SECONDS = 3600;
}
