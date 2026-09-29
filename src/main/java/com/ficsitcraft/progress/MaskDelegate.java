package com.ficsitcraft.progress;

import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

import java.util.function.LongSupplier;

/**
 * Syncs the 64-bit milestone mask to the client in 16-bit chunks
 * (screen handler properties are sent as shorts).
 */
public final class MaskDelegate {
	public static final int SIZE = 4;

	private MaskDelegate() {
	}

	public static PropertyDelegate server(LongSupplier mask) {
		return new PropertyDelegate() {
			@Override
			public int get(int index) {
				return (int) ((mask.getAsLong() >>> (16 * index)) & 0xFFFF);
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return SIZE;
			}
		};
	}

	public static PropertyDelegate client() {
		return new ArrayPropertyDelegate(SIZE);
	}

	public static long read(PropertyDelegate d) {
		long m = 0;
		for (int i = 0; i < SIZE; i++) m |= ((long) (d.get(i) & 0xFFFF)) << (16 * i);
		return m;
	}
}
