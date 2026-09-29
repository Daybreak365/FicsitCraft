package com.ficsitcraft.train;

/**
 * One timetable entry.
 * <ul>
 * <li>mode 0 ({@link #WAIT_SECONDS}): wait {@code seconds}, then leave.</li>
 * <li>mode 1 ({@link #WAIT_LOADED}): wait until every platform with a car docked at the station is done (loading: car full,
 * unloading: car empty). {@code seconds} is the time limit after which the train leaves anyway, 0 = no limit.</li>
 * </ul>
 */
public record Stop(String station, int mode, int seconds) {
	public static final int WAIT_SECONDS = 0, WAIT_LOADED = 1;
	public static final int MAX_SECONDS = 3600;
}
