package com.ficsitcraft.rail;

/**
 * Cubic Bezier curve with an arc-length table. Track segments are Beziers whose end handles are collinear with the
 * node directions, so joined tracks are always tangent-continuous.
 */
public final class Bezier {
	private static final int N = 48;

	public final V3 p0, p1, p2, p3;
	public final double length;
	private final double[] cum = new double[N + 1];

	public Bezier(V3 p0, V3 p1, V3 p2, V3 p3) {
		this.p0 = p0;
		this.p1 = p1;
		this.p2 = p2;
		this.p3 = p3;
		V3 prev = p0;
		double sum = 0;
		cum[0] = 0;
		for (int i = 1; i <= N; i++) {
			V3 p = point(i / (double) N);
			sum += p.distanceTo(prev);
			cum[i] = sum;
			prev = p;
		}
		length = sum;
	}

	public V3 point(double t) {
		double u = 1 - t;
		double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
		return new V3(a * p0.x() + b * p1.x() + c * p2.x() + d * p3.x(),
				a * p0.y() + b * p1.y() + c * p2.y() + d * p3.y(),
				a * p0.z() + b * p1.z() + c * p2.z() + d * p3.z());
	}

	/** dP/dt. */
	public V3 deriv(double t) {
		double u = 1 - t;
		V3 a = p1.sub(p0), b = p2.sub(p1), c = p3.sub(p2);
		return a.mul(3 * u * u).add(b.mul(6 * u * t)).add(c.mul(3 * t * t));
	}

	/** d2P/dt2. */
	public V3 deriv2(double t) {
		V3 a = p2.sub(p1.mul(2)).add(p0);
		V3 b = p3.sub(p2.mul(2)).add(p1);
		return a.mul(6 * (1 - t)).add(b.mul(6 * t));
	}

	public V3 unitTangent(double t) {
		V3 d = deriv(t);
		return d.lengthSq() < 1e-12 ? p3.sub(p0).normalize() : d.normalize();
	}

	/** Curve parameter at arc distance s from p0 (clamped to the curve). */
	public double tAtDist(double s) {
		if (s <= 0) return 0;
		if (s >= length) return 1;
		int lo = 0, hi = N;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (cum[mid] <= s) lo = mid;
			else hi = mid;
		}
		double seg = cum[hi] - cum[lo];
		double f = seg < 1e-12 ? 0 : (s - cum[lo]) / seg;
		return (lo + f) / N;
	}

	public double distAtT(double t) {
		if (t <= 0) return 0;
		if (t >= 1) return length;
		double x = t * N;
		int i = (int) x;
		return cum[i] + (cum[i + 1] - cum[i]) * (x - i);
	}

	public V3 pointAtDist(double s) {
		return point(tAtDist(s));
	}

	public V3 tangentAtDist(double s) {
		return unitTangent(tAtDist(s));
	}

	/** De Casteljau split at parameter t: {left, right}. Both halves keep tangent continuity at the split point. */
	public Bezier[] split(double t) {
		V3 q0 = p0.lerp(p1, t), q1 = p1.lerp(p2, t), q2 = p2.lerp(p3, t);
		V3 r0 = q0.lerp(q1, t), r1 = q1.lerp(q2, t);
		V3 s = r0.lerp(r1, t);
		return new Bezier[]{new Bezier(p0, q0, r0, s), new Bezier(s, r1, q2, p3)};
	}

	/** Closest point to q: {distance, arcDistance, t}. */
	public double[] nearest(V3 q) {
		int best = 0;
		double bd = Double.MAX_VALUE;
		for (int i = 0; i <= N; i++) {
			double d = point(i / (double) N).sub(q).lengthSq();
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		double lo = Math.max(0, (best - 1) / (double) N), hi = Math.min(1, (best + 1) / (double) N);
		for (int it = 0; it < 24; it++) {
			double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
			if (point(m1).sub(q).lengthSq() < point(m2).sub(q).lengthSq()) hi = m2;
			else lo = m1;
		}
		double t = (lo + hi) / 2;
		return new double[]{point(t).distanceTo(q), distAtT(t), t};
	}

	/** Smallest turning radius along the curve (Infinity for a straight line). */
	public double minRadius() {
		double maxK = 0;
		for (int i = 0; i <= N; i++) {
			double t = i / (double) N;
			V3 d1 = deriv(t);
			double l = d1.length();
			if (l < 1e-9) return 0;
			double k = d1.cross(deriv2(t)).length() / (l * l * l);
			if (k > maxK) maxK = k;
		}
		return maxK < 1e-9 ? Double.POSITIVE_INFINITY : 1 / maxK;
	}

	/** Steepest gradient (rise over horizontal run). */
	public double maxSlope() {
		double m = 0;
		for (int i = 0; i <= N; i++) {
			V3 d = deriv(i / (double) N);
			double h = d.horizontalLength();
			double s = h < 1e-9 ? (Math.abs(d.y()) < 1e-9 ? 0 : 99) : Math.abs(d.y()) / h;
			if (s > m) m = s;
		}
		return m;
	}
}
