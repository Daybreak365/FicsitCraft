package com.ficsitcraft.rail;

/** Minimal immutable 3D vector. The rail / train core deliberately has no Minecraft dependency so it can be unit-tested headless. */
public record V3(double x, double y, double z) {
	public static final V3 ZERO = new V3(0, 0, 0);

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 mul(double k) {
		return new V3(x * k, y * k, z * k);
	}

	public double dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public V3 cross(V3 o) {
		return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public double lengthSq() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSq());
	}

	public double horizontalLength() {
		return Math.sqrt(x * x + z * z);
	}

	public double distanceTo(V3 o) {
		return sub(o).length();
	}

	public V3 normalize() {
		double l = length();
		return l < 1e-9 ? new V3(0, 0, 1) : mul(1 / l);
	}

	public V3 lerp(V3 o, double t) {
		return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
	}

	public V3 withY(double ny) {
		return new V3(x, ny, z);
	}

	public V3 neg() {
		return new V3(-x, -y, -z);
	}
}
