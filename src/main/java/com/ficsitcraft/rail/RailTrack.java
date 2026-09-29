package com.ficsitcraft.rail;

/** A curved track between two node sides. Geometry runs from node A to node B. */
public final class RailTrack {
	public final long id;
	public final long nodeA, nodeB;
	public final int sideA, sideB;
	public final Bezier curve;
	/** Block position of the building that embeds this track (station / platform), or NO_OWNER. */
	public final long owner;

	public RailTrack(long id, long nodeA, int sideA, long nodeB, int sideB, Bezier curve, long owner) {
		this.id = id;
		this.nodeA = nodeA;
		this.sideA = sideA;
		this.nodeB = nodeB;
		this.sideB = sideB;
		this.curve = curve;
		this.owner = owner;
	}

	private double radius = -1;

	/** Tightest turning radius of this track (cached). */
	public double radius() {
		if (radius < 0) radius = curve.minRadius();
		return radius;
	}

	public double length() {
		return curve.length;
	}

	/** Node reached when traversing in the given direction. */
	public long endNode(boolean forward) {
		return forward ? nodeB : nodeA;
	}

	/** Side of the end node this track hangs on when traversing in the given direction. */
	public int endSide(boolean forward) {
		return forward ? sideB : sideA;
	}

	public long startNode(boolean forward) {
		return forward ? nodeA : nodeB;
	}
}
