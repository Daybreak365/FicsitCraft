package com.ficsitcraft.rail;

/**
 * Shared (server + client preview) logic of the two-click track placement: snapping to nodes, splitting an existing
 * track, or starting/ending in free space.
 */
public final class RailPlacement {
	public static final double SNAP_H = 1.8, SNAP_V = 1.6, SPLIT_DIST = 1.4;

	/**
	 * A resolved click. {@code leave} is the direction the NEW track leaves this point in (for an end anchor in free
	 * space it is null: the planner then draws a smooth arc).
	 */
	public record Anchor(V3 pos, V3 leave, long node, int side, long splitTrack, double splitDist) {
		public boolean isNode() {
			return node >= 0;
		}

		public boolean isSplit() {
			return splitTrack >= 0;
		}
	}

	public record Result(boolean valid, String problem, RailPlanner.Plan plan, Anchor a, Anchor b) {
	}

	private RailPlacement() {
	}

	private static V3 snapDir(double lx, double lz) {
		double yaw = Math.atan2(lz, lx);
		double step = Math.toRadians(15);
		yaw = Math.round(yaw / step) * step;
		return new V3(Math.cos(yaw), 0, Math.sin(yaw));
	}

	/**
	 * @param hit    where the player clicked (top of the block)
	 * @param lookX/Z horizontal look direction of the player
	 * @param isEnd  false for the first click
	 * @param from   for the end click: the start anchor position, else null
	 */
	public static Anchor resolve(RailGraph g, V3 hit, double lookX, double lookZ, boolean isEnd, V3 from) {
		V3 look = new V3(lookX, 0, lookZ);
		look = look.length() < 1e-6 ? new V3(1, 0, 0) : look.normalize();

		RailNode n = g.nearestNode(hit, SNAP_H, SNAP_V);
		if (n != null) {
			// pick the free side: for a start click prefer the side pointing the way the player looks;
			// for an end click prefer the side facing the start anchor
			V3 want = isEnd && from != null ? from.sub(n.pos) : look;
			int prefer = n.dir.dot(want) >= 0 ? RailNode.FRONT : RailNode.BACK;
			int side = -1;
			if (n.side(prefer).size() < RailNode.MAX_PER_SIDE) side = prefer;
			else if (n.side(1 - prefer).size() < RailNode.MAX_PER_SIDE) side = 1 - prefer;
			if (side >= 0) return new Anchor(n.pos, n.leaveDir(side), n.id, side, -1, 0);
			return null;
		}

		RailGraph.Hit h = g.nearestTrack(hit, SPLIT_DIST);
		if (h != null) {
			RailTrack t = g.tracks.get(h.track());
			if (t != null && t.owner == RailNode.NO_OWNER && h.dist() > 1.5 && h.dist() < t.length() - 1.5) {
				V3 p = t.curve.pointAtDist(h.dist());
				V3 tan = t.curve.tangentAtDist(h.dist());
				V3 leave;
				if (isEnd && from != null) leave = tan.dot(from.sub(p)) >= 0 ? tan : tan.neg();
				else leave = tan.dot(look) >= 0 ? tan : tan.neg();
				return new Anchor(p, leave, -1, 0, t.id, h.dist());
			}
		}

		V3 pos = new V3(Math.round(hit.x() * 2) / 2.0, hit.y(), Math.round(hit.z() * 2) / 2.0);
		if (isEnd) return new Anchor(pos, null, -1, 0, -1, 0);
		return new Anchor(pos, snapDir(look.x(), look.z()), -1, 0, -1, 0);
	}

	public static Result plan(RailGraph g, Anchor a, Anchor b) {
		if (a == null || b == null) return new Result(false, "no_anchor", null, a, b);
		if (a.isNode() && b.isNode() && a.node() == b.node()) return new Result(false, "same_node", null, a, b);
		if (a.isSplit() && b.isSplit() && a.splitTrack() == b.splitTrack()) return new Result(false, "same_track", null, a, b);
		RailPlanner.Plan p = RailPlanner.plan(a.pos(), a.leave(), b.pos(), b.leave());
		if (p.valid() && (a.isNode() && b.isNode())) {
			// do not build the same connection twice
			for (long tid : g.nodes.get(a.node()).side(a.side())) {
				RailTrack t = g.tracks.get(tid);
				if (t != null && (t.nodeA == b.node() || t.nodeB == b.node())) return new Result(false, "exists", p, a, b);
			}
		}
		return new Result(p.valid(), p.problem(), p, a, b);
	}
}
