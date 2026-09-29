package com.ficsitcraft.multiblock;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Size of a multi-block building.
 * <p>
 * Local grid: {@code col} 0..w-1 from left to right, {@code row} 0..d-1 from back to front (front = FACING = output side),
 * {@code y} 0..h-1. The controller block (the one the player places, holding the block entity) sits at
 * (ax, 0, az).
 */
public record Footprint(int width, int depth, int height, int ax, int az) {

	/** Offset of a local cell relative to the controller, rotated to the given facing. */
	public BlockPos offset(Direction facing, int col, int y, int row) {
		int dx = col - ax;       // right
		int dz = az - row;       // north-layout: forward = -z
		return new BlockPos(rotX(facing, dx, dz), y, rotZ(facing, dx, dz));
	}

	public BlockPos toWorld(BlockPos controller, Direction facing, int col, int y, int row) {
		return controller.add(offset(facing, col, y, row));
	}

	/** All world positions of the building (controller included). */
	public List<BlockPos> positions(BlockPos controller, Direction facing) {
		List<BlockPos> list = new ArrayList<>(width * depth * height);
		for (int y = 0; y < height; y++)
			for (int row = 0; row < depth; row++)
				for (int col = 0; col < width; col++)
					list.add(toWorld(controller, facing, col, y, row));
		return list;
	}

	/** The block in front of the output port (front row, controller column, ground level). */
	public BlockPos outputTarget(BlockPos controller, Direction facing) {
		return toWorld(controller, facing, ax, 0, depth - 1).offset(facing);
	}

	/** A point given in local block units (x right from the left edge, z forward from the back edge), in world offset. */
	public Vec3d localPoint(Direction facing, double x, double y, double z) {
		double dx = x - ax - 0.5;       // relative to controller centre
		double dz = (az + 0.5) - z;     // north-layout
		return new Vec3d(rotXd(facing, dx, dz) + 0.5, y, rotZd(facing, dx, dz) + 0.5);
	}

	public boolean isSingle() {
		return width == 1 && depth == 1 && height == 1;
	}

	// north layout -> facing: north (x,z), east (-z,x), south (-x,-z), west (z,-x)
	private static int rotX(Direction f, int x, int z) {
		return switch (f) {
			case EAST -> -z;
			case SOUTH -> -x;
			case WEST -> z;
			default -> x;
		};
	}

	private static int rotZ(Direction f, int x, int z) {
		return switch (f) {
			case EAST -> x;
			case SOUTH -> -z;
			case WEST -> -x;
			default -> z;
		};
	}

	private static double rotXd(Direction f, double x, double z) {
		return switch (f) {
			case EAST -> -z;
			case SOUTH -> -x;
			case WEST -> z;
			default -> x;
		};
	}

	private static double rotZd(Direction f, double x, double z) {
		return switch (f) {
			case EAST -> x;
			case SOUTH -> -z;
			case WEST -> -x;
			default -> z;
		};
	}
}
