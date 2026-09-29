package com.ficsitcraft.rail;

/** Turns two endpoints + tangents into a smooth, validated track segment (shared by the placement preview and the server). */
public final class RailPlanner {
	/** Blocks of track per Railway item. */
	public static final double UNIT = 6.0;

	public record Plan(boolean valid, String problem, V3 p1, V3 p2, double length, int cost, Bezier curve) {
	}

	private RailPlanner() {
	}

	/**
	 * @param aLeave direction the track leaves A in
	 * @param bLeave direction the track leaves B in (pointing back towards A), or null for a free end (smooth arc)
	 */
	public static Plan plan(V3 a, V3 aLeave, V3 b, V3 bLeave) {
		V3 chord = b.sub(a);
		double c = chord.length();
		if (c < 1.0) return bad("too_short");
		V3 t0 = aLeave.normalize();
		V3 chordDir = chord.normalize();
		V3 chordH = new V3(chord.x(), 0, chord.z());
		if (chordH.length() < 0.5) return bad("bad_angle");
		chordH = chordH.normalize();
		V3 arrive; // direction of travel when reaching B
		if (bLeave == null) {
			V3 t0h = new V3(t0.x(), 0, t0.z());
			t0h = t0h.length() < 1e-6 ? chordH : t0h.normalize();
			double k = 2 * t0h.dot(chordH);
			arrive = chordH.mul(k).sub(t0h).normalize();
		} else {
			arrive = bLeave.normalize().neg();
		}
		if (t0.dot(chordDir) < 0.02 || arrive.dot(chordDir) < 0.02) return bad("bad_angle");
		double turn = Math.acos(Math.max(-1, Math.min(1, t0.dot(arrive))));
		double cos = Math.cos(turn / 4);
		double h = Math.min(0.62 * c, c / (3 * cos * cos));
		V3 p1 = a.add(t0.mul(h));
		V3 p2 = b.sub(arrive.mul(h));
		Bezier curve = new Bezier(a, p1, p2, b);
		int cost = Math.max(1, (int) Math.ceil(curve.length / UNIT));
		String problem = null;
		if (curve.length < RailGraph.MIN_LENGTH) problem = "too_short";
		else if (curve.length > RailGraph.MAX_LENGTH) problem = "too_long";
		else if (curve.maxSlope() > RailGraph.MAX_SLOPE) problem = "too_steep";
		else if (curve.minRadius() < RailGraph.MIN_RADIUS) problem = "too_sharp";
		return new Plan(problem == null, problem, p1, p2, curve.length, cost, curve);
	}

	private static Plan bad(String why) {
		return new Plan(false, why, V3.ZERO, V3.ZERO, 0, 0, null);
	}

	/** A straight track (used by station platforms). */
	public static Bezier straight(V3 a, V3 b) {
		return new Bezier(a, a.lerp(b, 1 / 3.0), a.lerp(b, 2 / 3.0), b);
	}
}
