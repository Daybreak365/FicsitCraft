package com.ficsitcraft.power;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Shape of a hanging power line; shared by the renderer and the zipline so both follow the same curve. */
public final class PowerLineGeometry {
	private PowerLineGeometry() {
	}

	public static double sag(Vec3d a, Vec3d b) {
		return Math.min(1.5, a.distanceTo(b) * 0.04);
	}

	/** Point on the line from a to b at parameter t (0..1). */
	public static Vec3d point(Vec3d a, Vec3d b, double t) {
		double s = sag(a, b);
		return a.lerp(b, t).add(0, -s * 4 * t * (1 - t), 0);
	}

	/** Derivative of {@link #point} with respect to t. */
	public static Vec3d tangent(Vec3d a, Vec3d b, double t) {
		double s = sag(a, b);
		return b.subtract(a).add(0, -s * 4 * (1 - 2 * t), 0);
	}

	/** World-space attachment point of a power node. */
	public static Vec3d anchor(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof PowerNodeBlockEntity n) return Vec3d.of(pos).add(n.getConnectorOffset());
		return Vec3d.of(pos).add(0.5, 1.0, 0.5);
	}
}
