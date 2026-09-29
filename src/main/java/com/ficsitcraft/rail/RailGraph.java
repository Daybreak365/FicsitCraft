package com.ficsitcraft.rail;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rail network of one dimension: nodes, Bezier tracks, switches and signals. Pure data + traversal (no Minecraft types). */
public final class RailGraph {
	public static final double MAX_SLOPE = 0.30;
	public static final double MIN_RADIUS = 4.5;
	public static final double MIN_LENGTH = 3;
	public static final double MAX_LENGTH = 64;

	public final Map<Long, RailNode> nodes = new LinkedHashMap<>();
	public final Map<Long, RailTrack> tracks = new LinkedHashMap<>();
	private long nextId = 1;
	/** Incremented on every structural change (block regions and path caches are keyed on it). */
	public int revision;
	private final Map<Long, Set<Long>> regionCache = new HashMap<>();
	private int regionRevision = -1;

	/** Chooses the branch to take at a switch. */
	public interface Chooser {
		long choose(RailNode node, int exitSide, List<Long> candidates);
	}

	public static final Chooser SWITCH_STATE = (node, side, cands) -> cands.get(node.sel(side));

	// ------------------------------------------------------------------------------------------------- mutation

	public RailNode addNode(V3 pos, V3 dir, long owner) {
		RailNode n = new RailNode(nextId++, pos, dir);
		n.owner = owner;
		nodes.put(n.id, n);
		revision++;
		return n;
	}

	public boolean canAttach(long node, int side) {
		RailNode n = nodes.get(node);
		return n != null && n.side(side).size() < RailNode.MAX_PER_SIDE;
	}

	public RailTrack connect(long nodeA, int sideA, long nodeB, int sideB, V3 p1, V3 p2, long owner) {
		RailNode a = nodes.get(nodeA), b = nodes.get(nodeB);
		Bezier curve = new Bezier(a.pos, p1, p2, b.pos);
		RailTrack t = new RailTrack(nextId++, nodeA, sideA, nodeB, sideB, curve, owner);
		tracks.put(t.id, t);
		a.side(sideA).add(t.id);
		b.side(sideB).add(t.id);
		revision++;
		return t;
	}

	public void removeTrack(long id) {
		RailTrack t = tracks.remove(id);
		if (t == null) return;
		detach(t.nodeA, t.sideA, id);
		detach(t.nodeB, t.sideB, id);
		pruneNode(t.nodeA);
		pruneNode(t.nodeB);
		revision++;
	}

	private void detach(long nodeId, int side, long trackId) {
		RailNode n = nodes.get(nodeId);
		if (n != null) n.side(side).remove(Long.valueOf(trackId));
	}

	private void pruneNode(long nodeId) {
		RailNode n = nodes.get(nodeId);
		if (n != null && n.trackCount() == 0 && n.owner == RailNode.NO_OWNER) nodes.remove(nodeId);
	}

	/** Removes everything a building embedded (its tracks and the nodes that only it needed). */
	public void removeOwner(long owner) {
		List<Long> ids = new ArrayList<>();
		for (RailTrack t : tracks.values()) if (t.owner == owner) ids.add(t.id);
		for (long id : ids) removeTrack(id);
		List<Long> gone = new ArrayList<>();
		for (RailNode n : nodes.values()) {
			if (n.owner != owner) continue;
			if (n.trackCount() == 0 && !n.hasAnySignal()) gone.add(n.id);
			else n.owner = RailNode.NO_OWNER;
		}
		for (long id : gone) nodes.remove(id);
		revision++;
	}

	/**
	 * Splits a (non building) track at arc distance {@code dist} by inserting a node. Returns {node, trackA, trackB} or null.
	 * The new node's axis points along the track (A to B).
	 */
	public long[] split(long trackId, double dist) {
		RailTrack t = tracks.get(trackId);
		if (t == null || t.owner != RailNode.NO_OWNER) return null;
		if (dist < 1.0 || dist > t.length() - 1.0) return null;
		double u = t.curve.tAtDist(dist);
		Bezier[] halves = t.curve.split(u);
		V3 axis = t.curve.unitTangent(u);
		RailNode mid = addNode(halves[0].p3, axis, RailNode.NO_OWNER);
		RailNode a = nodes.get(t.nodeA), b = nodes.get(t.nodeB);
		int ia = a.side(t.sideA).indexOf(trackId), ib = b.side(t.sideB).indexOf(trackId);
		tracks.remove(trackId);
		RailTrack left = new RailTrack(nextId++, t.nodeA, t.sideA, mid.id, RailNode.BACK, halves[0], RailNode.NO_OWNER);
		RailTrack right = new RailTrack(nextId++, mid.id, RailNode.FRONT, t.nodeB, t.sideB, halves[1], RailNode.NO_OWNER);
		tracks.put(left.id, left);
		tracks.put(right.id, right);
		a.side(t.sideA).set(ia, left.id);
		b.side(t.sideB).set(ib, right.id);
		mid.back.add(left.id);
		mid.front.add(right.id);
		revision++;
		return new long[]{mid.id, left.id, right.id};
	}

	// ------------------------------------------------------------------------------------------------- queries

	public RailNode nearestNode(V3 p, double horizontal, double vertical) {
		RailNode best = null;
		double bd = Double.MAX_VALUE;
		for (RailNode n : nodes.values()) {
			double dx = n.pos.x() - p.x(), dz = n.pos.z() - p.z(), dy = Math.abs(n.pos.y() - p.y());
			double h = Math.sqrt(dx * dx + dz * dz);
			if (h > horizontal || dy > vertical) continue;
			double d = h + dy;
			if (d < bd) {
				bd = d;
				best = n;
			}
		}
		return best;
	}

	public record Hit(long track, double dist, double t, double distance) {
	}

	public Hit nearestTrack(V3 p, double maxDist) {
		Hit best = null;
		for (RailTrack t : tracks.values()) {
			if (!inRange(t, p, maxDist)) continue;
			double[] r = t.curve.nearest(p);
			if (r[0] <= maxDist && (best == null || r[0] < best.distance())) best = new Hit(t.id, r[1], r[2], r[0]);
		}
		return best;
	}

	private static boolean inRange(RailTrack t, V3 p, double r) {
		V3 mid = t.curve.point(0.5);
		return mid.distanceTo(p) <= t.length() / 2 + r + 2;
	}

	/** Track closest to a view ray (within {@code radius}); dist is the arc distance of the closest point. */
	public Hit rayTrack(V3 origin, V3 dir, double maxDist, double radius) {
		Hit best = null;
		double bestAlong = Double.MAX_VALUE;
		for (RailTrack t : tracks.values()) {
			if (t.curve.point(0.5).distanceTo(origin) > maxDist + t.length() / 2 + 2) continue;
			int n = Math.max(6, (int) (t.length() / 0.75));
			for (int i = 0; i <= n; i++) {
				double tt = i / (double) n;
				V3 p = t.curve.point(tt);
				V3 rel = p.sub(origin);
				double along = rel.dot(dir);
				if (along < 0 || along > maxDist) continue;
				double off = rel.sub(dir.mul(along)).length();
				if (off <= radius && along < bestAlong) {
					bestAlong = along;
					best = new Hit(t.id, t.curve.distAtT(tt), tt, off);
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------------------------------------- traversal

	/** Track + direction to continue on after reaching the end of {@code d}; null at a dead end. */
	public Dir next(Dir d, Chooser chooser) {
		RailTrack t = tracks.get(d.track());
		if (t == null) return null;
		RailNode n = nodes.get(t.endNode(d.forward()));
		if (n == null) return null;
		int exit = 1 - t.endSide(d.forward());
		List<Long> cands = n.side(exit);
		if (cands.isEmpty()) return null;
		long pick = cands.size() == 1 ? cands.get(0) : chooser.choose(n, exit, cands);
		RailTrack nt = tracks.get(pick);
		if (nt == null) return null;
		boolean forward = nt.nodeA == n.id && nt.sideA == exit;
		return new Dir(pick, forward);
	}

	/** All directions one can continue on after {@code d} (for route search). */
	public List<Dir> successors(Dir d) {
		List<Dir> out = new ArrayList<>();
		RailTrack t = tracks.get(d.track());
		if (t == null) return out;
		RailNode n = nodes.get(t.endNode(d.forward()));
		if (n == null) return out;
		int exit = 1 - t.endSide(d.forward());
		for (long id : n.side(exit)) {
			RailTrack nt = tracks.get(id);
			if (nt == null) continue;
			out.add(new Dir(id, nt.nodeA == n.id && nt.sideA == exit));
		}
		return out;
	}

	public RailNode endNodeOf(Dir d) {
		RailTrack t = tracks.get(d.track());
		return t == null ? null : nodes.get(t.endNode(d.forward()));
	}

	public int endSideOf(Dir d) {
		RailTrack t = tracks.get(d.track());
		return t == null ? 0 : t.endSide(d.forward());
	}

	public double length(long track) {
		RailTrack t = tracks.get(track);
		return t == null ? 0 : t.length();
	}

	/**
	 * The signalling block on the far side of a signal: all tracks reachable from the tracks on (node, side) without
	 * crossing a node that carries a signal. Cached per graph revision.
	 */
	public Set<Long> blockRegion(long nodeId, int side) {
		if (regionRevision != revision) {
			regionCache.clear();
			regionRevision = revision;
		}
		long key = nodeId * 2 + side;
		Set<Long> cached = regionCache.get(key);
		if (cached != null) return cached;
		Set<Long> seen = new HashSet<>();
		ArrayDeque<Long> q = new ArrayDeque<>();
		RailNode start = nodes.get(nodeId);
		if (start != null) for (long t : start.side(side)) if (seen.add(t)) q.add(t);
		while (!q.isEmpty()) {
			RailTrack t = tracks.get(q.poll());
			if (t == null) continue;
			for (int e = 0; e < 2; e++) {
				RailNode n = nodes.get(e == 0 ? t.nodeA : t.nodeB);
				int s = e == 0 ? t.sideA : t.sideB;
				if (n == null) continue;
				for (long o : n.side(s)) if (seen.add(o)) q.add(o);
				if (!n.hasAnySignal()) for (long o : n.side(1 - s)) if (seen.add(o)) q.add(o);
			}
		}
		regionCache.put(key, seen);
		return seen;
	}

	// ------------------------------------------------------------------------------------------------- serialisation

	public void write(DataOutput o) throws IOException {
		o.writeInt(2);
		o.writeLong(nextId);
		o.writeInt(nodes.size());
		for (RailNode n : nodes.values()) {
			o.writeLong(n.id);
			writeV(o, n.pos);
			writeV(o, n.dir);
			o.writeByte(n.signalFront);
			o.writeByte(n.signalBack);
			o.writeByte(n.selFront);
			o.writeByte(n.selBack);
			o.writeLong(n.owner);
		}
		o.writeInt(tracks.size());
		for (RailTrack t : tracks.values()) {
			o.writeLong(t.id);
			o.writeLong(t.nodeA);
			o.writeByte(t.sideA);
			o.writeLong(t.nodeB);
			o.writeByte(t.sideB);
			writeV(o, t.curve.p1);
			writeV(o, t.curve.p2);
			o.writeLong(t.owner);
		}
		// per node track order (switch indices depend on it)
		for (RailNode n : nodes.values()) {
			for (int s = 0; s < 2; s++) {
				List<Long> l = n.side(s);
				o.writeByte(l.size());
				for (long id : l) o.writeLong(id);
			}
		}
	}

	public static RailGraph read(DataInput in) throws IOException {
		RailGraph g = new RailGraph();
		int ver = in.readInt();
		if (ver != 2) throw new IOException("Unknown rail graph version " + ver);
		g.nextId = in.readLong();
		int nn = in.readInt();
		for (int i = 0; i < nn; i++) {
			long id = in.readLong();
			V3 pos = readV(in), dir = readV(in);
			RailNode n = new RailNode(id, pos, dir);
			n.signalFront = in.readByte();
			n.signalBack = in.readByte();
			n.selFront = in.readByte();
			n.selBack = in.readByte();
			n.owner = in.readLong();
			g.nodes.put(id, n);
		}
		int nt = in.readInt();
		for (int i = 0; i < nt; i++) {
			long id = in.readLong(), a = in.readLong();
			int sa = in.readByte();
			long b = in.readLong();
			int sb = in.readByte();
			V3 p1 = readV(in), p2 = readV(in);
			long owner = in.readLong();
			RailNode na = g.nodes.get(a), nb = g.nodes.get(b);
			if (na == null || nb == null) continue;
			g.tracks.put(id, new RailTrack(id, a, sa, b, sb, new Bezier(na.pos, p1, p2, nb.pos), owner));
		}
		for (RailNode n : g.nodes.values()) {
			for (int s = 0; s < 2; s++) {
				int c = in.readByte();
				for (int k = 0; k < c; k++) {
					long id = in.readLong();
					if (g.tracks.containsKey(id)) n.side(s).add(id);
				}
			}
		}
		g.revision = 1;
		return g;
	}

	private static void writeV(DataOutput o, V3 v) throws IOException {
		o.writeDouble(v.x());
		o.writeDouble(v.y());
		o.writeDouble(v.z());
	}

	private static V3 readV(DataInput in) throws IOException {
		return new V3(in.readDouble(), in.readDouble(), in.readDouble());
	}
}
