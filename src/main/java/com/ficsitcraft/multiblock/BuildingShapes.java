package com.ficsitcraft.multiblock;

import com.ficsitcraft.FicsitCraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collision / selection shapes of multi-block buildings built from the same boxes that are drawn
 * ({@code assets/ficsitcraft/buildings/<id>.json}): a cell of the footprint is solid only where a part of the model is, so the
 * empty space that looks free (the corridor a train drives through) can be walked through.
 */
public final class BuildingShapes {
	private record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
	}

	private static final Map<Block, List<Box>> BOXES = new HashMap<>();
	private static final Map<String, Map<Long, VoxelShape>> CELLS = new HashMap<>();

	private BuildingShapes() {
	}

	/** Shape of the block at {@code rel} (offset from the controller) or null if the building has no model geometry. */
	@Nullable
	public static synchronized VoxelShape get(Block controller, Direction facing, BlockPos rel) {
		Footprint fp = Multiblocks.get(controller);
		if (fp == null) return null;
		String key = Registries.BLOCK.getId(controller) + "/" + facing.getName();
		Map<Long, VoxelShape> cells = CELLS.get(key);
		if (cells == null) {
			List<Box> boxes = boxesOf(controller);
			if (boxes == null) return null;
			cells = new HashMap<>();
			for (int y = 0; y < fp.height(); y++) {
				for (int row = 0; row < fp.depth(); row++) {
					for (int col = 0; col < fp.width(); col++) {
						cells.put(fp.offset(facing, col, y, row).asLong(), cellShape(boxes, facing, col, y, row));
					}
				}
			}
			CELLS.put(key, cells);
		}
		return cells.get(rel.asLong());
	}

	@Nullable
	private static List<Box> boxesOf(Block block) {
		if (BOXES.containsKey(block)) return BOXES.get(block);
		List<Box> boxes = null;
		String name = Registries.BLOCK.getId(block).getPath();
		String path = "/assets/" + FicsitCraft.MOD_ID + "/buildings/" + name + ".json";
		try (InputStream in = BuildingShapes.class.getResourceAsStream(path)) {
			if (in != null) {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				boxes = new ArrayList<>();
				for (JsonElement e : root.getAsJsonArray("parts")) {
					JsonObject part = e.getAsJsonObject();
					JsonArray a = part.getAsJsonArray("from"), b = part.getAsJsonArray("to");
					boxes.add(new Box(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
							b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()));
				}
			}
		} catch (Exception ex) {
			FicsitCraft.LOGGER.error("Failed to read building geometry {}", path, ex);
		}
		BOXES.put(block, boxes);
		return boxes;
	}

	/** Union of the model boxes clipped to one cell, rotated from the building's local frame into the world. */
	private static VoxelShape cellShape(List<Box> boxes, Direction facing, int col, int y, int row) {
		Direction right = facing.rotateYClockwise();
		VoxelShape shape = VoxelShapes.empty();
		for (Box b : boxes) {
			double u0 = Math.max(b.x0, col) - col, u1 = Math.min(b.x1, col + 1) - col;       // along "right"
			double v0 = Math.max(b.z0, row) - row, v1 = Math.min(b.z1, row + 1) - row;       // along "forward"
			double y0 = Math.max(b.y0, y) - y, y1 = Math.min(b.y1, y + 1) - y;
			if (u1 - u0 < 1e-4 || v1 - v0 < 1e-4 || y1 - y0 < 1e-4) continue;
			double xa = worldAxis(right, facing, true, u0, v0), xb = worldAxis(right, facing, true, u1, v1);
			double za = worldAxis(right, facing, false, u0, v0), zb = worldAxis(right, facing, false, u1, v1);
			shape = VoxelShapes.union(shape, VoxelShapes.cuboid(Math.min(xa, xb), y0, Math.min(za, zb), Math.max(xa, xb), y1, Math.max(za, zb)));
		}
		return shape;
	}

	/** World X (or Z) fraction inside the cell of a point at (u along "right", v along "forward"). */
	private static double worldAxis(Direction right, Direction forward, boolean xAxis, double u, double v) {
		int r = xAxis ? right.getOffsetX() : right.getOffsetZ();
		if (r != 0) return r > 0 ? u : 1 - u;
		int f = xAxis ? forward.getOffsetX() : forward.getOffsetZ();
		return f > 0 ? v : 1 - v;
	}
}
