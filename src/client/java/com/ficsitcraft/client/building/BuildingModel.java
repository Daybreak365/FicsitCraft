package com.ficsitcraft.client.building;

import com.ficsitcraft.FicsitCraft;
import com.ficsitcraft.multiblock.Footprint;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Box geometry of a multi-block building, in footprint-local block units:
 * X = right (0..width), Y = up, Z = forward from the back edge (0..depth). The front face (Z max) is the output side.
 */
public final class BuildingModel {
	/** Face order used by {@link Part#tex}. */
	public static final int DOWN = 0, UP = 1, FRONT = 2, BACK = 3, LEFT = 4, RIGHT = 5;

	public static final int GLOW_WHEN_ACTIVE = 1;
	public static final int SPIN_WHEN_ACTIVE = 2;
	public static final int ALWAYS_GLOW = 4;
	/** Each face texture is stretched once over the whole face instead of tiled per block (per-face painted art). */
	public static final int STRETCH = 8;
	/** A box of a wheel: rolls around the axle (pivot) while the vehicle moves. */
	public static final int WHEEL = 16;

	/**
	 * @param pivot for {@link #WHEEL} parts {axle height, axle z, wheel radius} in model units, otherwise null
	 */
	public record Part(float x0, float y0, float z0, float x1, float y1, float z1,
					   Identifier[] tex, Identifier[] activeTex, int flags, int tint, float[] pivot) {
		public Part(float x0, float y0, float z0, float x1, float y1, float z1, Identifier[] tex, Identifier[] activeTex, int flags, int tint) {
			this(x0, y0, z0, x1, y1, z1, tex, activeTex, flags, tint, null);
		}
	}

	public final Footprint footprint;
	private final List<Part> parts;

	private BuildingModel(Footprint footprint, List<Part> parts) {
		this.footprint = footprint;
		this.parts = Collections.unmodifiableList(parts);
	}

	public List<Part> parts() {
		return parts;
	}

	/** Model z of every wheel axle (rear = 0, front = length), sorted; empty for models without wheels. */
	public List<Float> wheelAxles() {
		List<Float> out = new ArrayList<>();
		for (Part p : parts) {
			if (p.pivot() == null) continue;
			float z = p.pivot()[1];
			boolean known = false;
			for (float o : out) if (Math.abs(o - z) < 0.01f) known = true;
			if (!known) out.add(z);
		}
		Collections.sort(out);
		return out;
	}

	/** Builds a model from already-assembled parts (used by the JSON loader). */
	public static BuildingModel of(Footprint footprint, List<Part> parts) {
		return new BuildingModel(footprint, new ArrayList<>(parts));
	}

	public static Builder builder(Footprint fp) {
		return new Builder(fp);
	}

	public static Identifier tex(String name) {
		return name.contains(":") ? Identifier.of(name) : FicsitCraft.id("block/" + name);
	}

	public static final class Builder {
		private final Footprint fp;
		private final List<Part> parts = new ArrayList<>();

		private Builder(Footprint fp) {
			this.fp = fp;
		}

		public BoxBuilder box(double x0, double y0, double z0, double x1, double y1, double z1) {
			return new BoxBuilder(this, (float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);
		}

		/** Hazard-striped steel base plate covering the whole footprint. */
		public Builder base() {
			return box(0, 0, 0, fp.width(), 0.2, fp.depth()).all("frame").sides("hazard").done();
		}

		/** Conveyor output port at the front of the controller column. */
		public Builder outputPort(double y0, double y1) {
			return box(fp.ax() + 0.12, y0, fp.depth() - 0.18, fp.ax() + 0.88, y1, fp.depth() + 0.02).all("casing_dark").face(FRONT, "port").done();
		}

		/** Conveyor input port at the back of the controller column. */
		public Builder inputPort(double y0, double y1) {
			return box(fp.ax() + 0.12, y0, -0.02, fp.ax() + 0.88, y1, 0.18).all("casing_dark").face(BACK, "port").done();
		}

		/** Small power connector on the roof centre. */
		public Builder connector(double roof) {
			double cx = fp.width() / 2.0;
			double cz = fp.depth() / 2.0;
			box(cx - 0.12, roof, cz - 0.12, cx + 0.12, roof + 0.18, cz + 0.12).all("frame").done();
			return box(cx - 0.07, roof + 0.18, cz - 0.07, cx + 0.07, roof + 0.3, cz + 0.07).all("power_pole_insulator").done();
		}

		public BuildingModel build() {
			return new BuildingModel(fp, parts);
		}
	}

	public static final class BoxBuilder {
		private final Builder parent;
		private final float x0, y0, z0, x1, y1, z1;
		private final Identifier[] tex = new Identifier[6];
		private final Identifier[] activeTex = new Identifier[6];
		private int flags;
		private int tint = 0xFFFFFF;

		private BoxBuilder(Builder parent, float x0, float y0, float z0, float x1, float y1, float z1) {
			this.parent = parent;
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
		}

		public BoxBuilder all(String t) {
			Identifier id = tex(t);
			for (int i = 0; i < 6; i++) tex[i] = id;
			return this;
		}

		public BoxBuilder face(int face, String t) {
			tex[face] = tex(t);
			return this;
		}

		public BoxBuilder activeFace(int face, String t) {
			activeTex[face] = tex(t);
			return this;
		}

		public BoxBuilder sides(String t) {
			Identifier id = tex(t);
			tex[FRONT] = tex[BACK] = tex[LEFT] = tex[RIGHT] = id;
			return this;
		}

		public BoxBuilder top(String t) {
			tex[UP] = tex(t);
			return this;
		}

		public BoxBuilder glow() {
			flags |= GLOW_WHEN_ACTIVE;
			return this;
		}

		public BoxBuilder alwaysGlow() {
			flags |= ALWAYS_GLOW;
			return this;
		}

		public BoxBuilder spin() {
			flags |= SPIN_WHEN_ACTIVE;
			return this;
		}

		public BoxBuilder stretch() {
			flags |= STRETCH;
			return this;
		}

		public BoxBuilder tint(int rgb) {
			tint = rgb;
			return this;
		}

		public Builder done() {
			for (int i = 0; i < 6; i++) if (tex[i] == null) tex[i] = tex("casing");
			parent.parts.add(new Part(x0, y0, z0, x1, y1, z1, tex.clone(), activeTex.clone(), flags, tint));
			return parent;
		}
	}
}
