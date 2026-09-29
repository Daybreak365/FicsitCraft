package com.ficsitcraft.train;

import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailTrack;
import com.ficsitcraft.rail.V3;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A train: a chain of vehicles sitting on a path of track segments.
 * <p>
 * The path lists the track segments from the tail to the head (each with the direction it is driven in). The tail
 * end of the train is {@code tailOffset} blocks into the first segment; the head end is {@code length()} further.
 * "u" is the distance along the concatenated path. Driving forward pushes the head into new segments (chosen by the
 * switches / route), driving backwards pulls the tail into new segments.
 */
public final class Train {
	public static final double GAP = 0.6;

	public final UUID id;
	public String name = "";
	public final List<Vehicle> vehicles = new ArrayList<>();
	public final ArrayList<Dir> path = new ArrayList<>();
	public double tailOffset;
	/** Signed speed along the path direction, blocks per tick. */
	public double speed;

	// ---- control
	/** Manual command along the path direction, -1..1 (already accounts for which way the driven loco faces). */
	public double manualCmd;
	public boolean brake;
	public boolean driven;
	public UUID driver;
	public boolean autopilot;
	/** Crashed into another train: lies beside the track, cannot be driven until re-railed (or dismantled). */
	public boolean derailed;
	/** Autopilot travel direction along the path: +1 towards the head. */
	public int dirSign = 1;
	public final List<Stop> timetable = new ArrayList<>();
	public int stopIndex;

	// ---- runtime state (not persisted)
	public boolean powered;
	public double powerDraw;
	public int autoState;      // 0 plan, 1 en route, 2 dwell, 3 no route
	public int dwell;
	public int retry;
	public boolean docked;
	public double autoCmd;
	public double goalDist = Double.POSITIVE_INFINITY;
	public double limitDist = Double.POSITIVE_INFINITY;
	public String status = "";
	public Map<Long, Long> routeChoice = new HashMap<>();
	public Goal goal;
	public final Set<Long> reserved = new HashSet<>();
	public boolean dirty = true;

	public Train(UUID id) {
		this.id = id;
	}

	public Train() {
		this(UUID.randomUUID());
	}

	// ------------------------------------------------------------------------------------------------ geometry

	public double length() {
		double l = 0;
		for (Vehicle v : vehicles) l += v.type.length;
		return l + Math.max(0, vehicles.size() - 1) * GAP;
	}

	public double mass() {
		double m = 0;
		for (Vehicle v : vehicles) m += v.mass();
		return Math.max(1, m);
	}

	public int locoCount() {
		int n = 0;
		for (Vehicle v : vehicles) if (v.type.locomotive) n++;
		return n;
	}

	public double pathLength(RailGraph g) {
		double l = 0;
		for (Dir d : path) l += g.length(d.track());
		return l;
	}

	/** Distance from the tail end to the start of vehicle i. */
	public double vehicleStart(int i) {
		double u = 0;
		for (int k = 0; k < i; k++) u += vehicles.get(k).type.length + GAP;
		return u;
	}

	public double vehicleCenterU(int i) {
		return tailOffset + vehicleStart(i) + vehicles.get(i).type.length / 2;
	}

	public double headU() {
		return tailOffset + length();
	}

	/** Start of path segment k in u. */
	public double segStart(RailGraph g, int k) {
		double u = 0;
		for (int i = 0; i < k; i++) u += g.length(path.get(i).track());
		return u;
	}

	private record Sample(V3 pos, V3 tan) {
	}

	private Sample sample(RailGraph g, double u) {
		double acc = 0;
		int n = path.size();
		for (int k = 0; k < n; k++) {
			Dir d = path.get(k);
			RailTrack t = g.tracks.get(d.track());
			double len = t == null ? 0 : t.length();
			if (t != null && (u < acc + len || k == n - 1)) {
				double s = u - acc;
				double dist = d.forward() ? s : len - s;
				double c = Math.max(0, Math.min(len, dist));
				V3 tan = t.curve.tangentAtDist(c);
				V3 pos = t.curve.pointAtDist(c).add(tan.mul(dist - c));
				return new Sample(pos, d.forward() ? tan : tan.neg());
			}
			acc += len;
		}
		return new Sample(V3.ZERO, new V3(0, 0, 1));
	}

	public V3 pointAtU(RailGraph g, double u) {
		return sample(g, u).pos();
	}

	/** Unit tangent in the path direction. */
	public V3 tangentAtU(RailGraph g, double u) {
		return sample(g, u).tan();
	}

	// ------------------------------------------------------------------------------------------------ movement

	public interface Occupancy {
		void add(long track, double lo, double hi);
	}

	/** Reports the part of every track the train occupies, in the track's own (A to B) distance coordinates. */
	public void collectOccupancy(RailGraph g, Occupancy sink) {
		double lo = tailOffset, hi = tailOffset + length();
		double acc = 0;
		for (Dir d : path) {
			double len = g.length(d.track());
			double a = Math.max(lo, acc), b = Math.min(hi, acc + len);
			if (b >= a - 1e-9) {
				double s0 = a - acc, s1 = b - acc;
				if (d.forward()) sink.add(d.track(), s0, s1);
				else sink.add(d.track(), len - s1, len - s0);
			}
			acc += len;
		}
	}

	public boolean extendFront(RailGraph g, RailGraph.Chooser ch) {
		if (path.isEmpty()) return false;
		Dir n = g.next(path.get(path.size() - 1), ch);
		if (n == null) return false;
		path.add(n);
		return true;
	}

	public boolean extendBack(RailGraph g, RailGraph.Chooser ch) {
		if (path.isEmpty()) return false;
		Dir n = g.next(path.get(0).flip(), ch);
		if (n == null) return false;
		path.add(0, n.flip());
		tailOffset += g.length(n.track());
		return true;
	}

	/** Moves the train by ds (signed) along its path; returns the distance actually moved (less at a dead end). */
	public double move(RailGraph g, RailGraph.Chooser ch, double ds) {
		double moved = 0;
		double rem = Math.abs(ds);
		if (ds > 0) {
			while (rem > 1e-9) {
				double room = pathLength(g) - headU();
				if (room >= rem) {
					tailOffset += rem;
					moved += rem;
					rem = 0;
					break;
				}
				if (room > 0) {
					tailOffset += room;
					moved += room;
					rem -= room;
				}
				if (!extendFront(g, ch)) break;
			}
		} else if (ds < 0) {
			while (rem > 1e-9) {
				if (tailOffset >= rem) {
					tailOffset -= rem;
					moved -= rem;
					rem = 0;
					break;
				}
				if (tailOffset > 0) {
					rem -= tailOffset;
					moved -= tailOffset;
					tailOffset = 0;
				}
				if (!extendBack(g, ch)) break;
			}
		}
		trim(g);
		if (moved != 0) dirty = true;
		return moved;
	}

	public void trim(RailGraph g) {
		while (path.size() > 1) {
			double len = g.length(path.get(0).track());
			if (tailOffset < len) break;
			tailOffset -= len;
			path.remove(0);
		}
		while (path.size() > 1) {
			double total = pathLength(g);
			double lastLen = g.length(path.get(path.size() - 1).track());
			if (total - lastLen < headU() - 1e-9) break;
			path.remove(path.size() - 1);
		}
	}

	// ------------------------------------------------------------------------------------------------ chooser

	public RailGraph.Chooser chooser() {
		if (routeChoice.isEmpty()) return RailGraph.SWITCH_STATE;
		return (node, side, cands) -> {
			Long c = routeChoice.get(node.id);
			if (c != null && cands.contains(c)) return c;
			return cands.get(node.sel(side));
		};
	}

	// ------------------------------------------------------------------------------------------------ editing

	/** Adds a vehicle at the head (true) or tail end; false if the track ends before the vehicle fits. */
	public boolean addVehicle(RailGraph g, RailGraph.Chooser ch, Vehicle v, boolean atHead) {
		double need = v.type.length + (vehicles.isEmpty() ? 0 : GAP);
		if (atHead) {
			vehicles.add(v);
			while (pathLength(g) - headU() < -1e-9) {
				if (!extendFront(g, ch)) {
					vehicles.remove(vehicles.size() - 1);
					return false;
				}
			}
		} else {
			while (tailOffset < need) {
				if (!extendBack(g, ch)) return false;
			}
			tailOffset -= need;
			vehicles.add(0, v);
		}
		dirty = true;
		return true;
	}

	/** Splits the train in front of vehicle index; returns the head part (vehicles index..end) or null. This train keeps the tail part. */
	public Train split(RailGraph g, int index) {
		if (index <= 0 || index >= vehicles.size()) return null;
		double lenTail = 0;
		for (int i = 0; i < index; i++) lenTail += vehicles.get(i).type.length;
		lenTail += (index - 1) * GAP;
		double u0 = tailOffset, u1 = tailOffset + lenTail;
		double h0 = u1 + GAP, h1 = headU();

		Train head = new Train();
		head.name = name;
		head.dirSign = dirSign;
		for (int i = index; i < vehicles.size(); i++) head.vehicles.add(vehicles.get(i));
		head.subPath(g, this, h0, h1);
		List<Vehicle> keep = new ArrayList<>(vehicles.subList(0, index));
		List<Dir> tailPath = new ArrayList<>();
		double[] off = new double[1];
		Train tmp = new Train();
		tmp.subPath(g, this, u0, u1);
		tailPath.addAll(tmp.path);
		off[0] = tmp.tailOffset;

		vehicles.clear();
		vehicles.addAll(keep);
		path.clear();
		path.addAll(tailPath);
		tailOffset = off[0];
		speed = 0;
		head.speed = 0;
		autopilot = false;
		routeChoice.clear();
		reserved.clear();
		dirty = head.dirty = true;
		return head;
	}

	private void subPath(RailGraph g, Train src, double u0, double u1) {
		path.clear();
		double acc = 0;
		boolean first = true;
		for (Dir d : src.path) {
			double len = g.length(d.track());
			if (acc + len > u0 - 1e-9 && acc < u1 + 1e-9) {
				if (first) {
					tailOffset = Math.max(0, u0 - acc);
					first = false;
				}
				path.add(d);
			}
			acc += len;
		}
		if (path.isEmpty() && !src.path.isEmpty()) {
			path.add(src.path.get(src.path.size() - 1));
			tailOffset = 0;
		}
	}

	/**
	 * Couples {@code b} to the head end of this train if the two are close together on connected track. On success this
	 * train contains both and b should be discarded.
	 */
	public boolean coupleHeadTo(RailGraph g, Train b) {
		if (b == this || vehicles.isEmpty() || b.vehicles.isEmpty()) return false;
		RailGraph.Chooser ch = RailGraph.SWITCH_STATE;
		// find b's tail segment ahead of our head (up to a few tracks)
		List<Dir> bridge = new ArrayList<>();
		Dir cur = path.get(path.size() - 1);
		Dir target = b.path.get(0);
		boolean found = cur.equals(target);
		int hops = 0;
		while (!found && hops < 4) {
			cur = g.next(cur, ch);
			if (cur == null) return false;
			hops++;
			if (cur.equals(target)) found = true;
			else bridge.add(cur);
		}
		if (!found) return false;
		// distance between our head end and b's tail end along the path
		double startOfB = 0;
		double acc = pathLength(g);
		if (b.path.get(0).equals(path.get(path.size() - 1)) && bridge.isEmpty()) {
			startOfB = acc - g.length(cur.track());
		} else {
			double bridgeLen = 0;
			for (Dir d : bridge) bridgeLen += g.length(d.track());
			startOfB = acc + bridgeLen;
		}
		double gap = startOfB + b.tailOffset - headU();
		if (gap < -0.5 || gap > 3.5) return false;
		if (!b.path.get(0).equals(path.get(path.size() - 1)) || !bridge.isEmpty()) {
			path.addAll(bridge);
			path.add(b.path.get(0));
		}
		for (int i = 1; i < b.path.size(); i++) path.add(b.path.get(i));
		vehicles.addAll(b.vehicles);
		int guard = 0;
		while (pathLength(g) - headU() < -1e-9 && guard++ < 4) if (!extendFront(g, ch)) break;
		speed = 0;
		autopilot = false;
		dirty = true;
		return true;
	}

	// ------------------------------------------------------------------------------------------------ persistence

	public void write(DataOutput o) throws IOException {
		o.writeLong(id.getMostSignificantBits());
		o.writeLong(id.getLeastSignificantBits());
		o.writeUTF(name);
		o.writeInt(vehicles.size());
		for (Vehicle v : vehicles) {
			o.writeLong(v.id.getMostSignificantBits());
			o.writeLong(v.id.getLeastSignificantBits());
			o.writeByte(v.type.ordinal());
			o.writeByte(v.facing);
			o.writeFloat((float) v.load);
		}
		o.writeInt(path.size());
		for (Dir d : path) {
			o.writeLong(d.track());
			o.writeBoolean(d.forward());
		}
		o.writeDouble(tailOffset);
		o.writeDouble(speed);
		o.writeBoolean(autopilot);
		o.writeByte(dirSign);
		o.writeInt(stopIndex);
		o.writeInt(timetable.size());
		for (Stop s : timetable) {
			o.writeUTF(s.station());
			o.writeByte(s.mode());
			o.writeShort(s.seconds());
		}
		o.writeBoolean(derailed);
	}

	public static Train read(DataInput in) throws IOException {
		Train t = new Train(new UUID(in.readLong(), in.readLong()));
		t.name = in.readUTF();
		int nv = in.readInt();
		VehicleType[] types = VehicleType.values();
		for (int i = 0; i < nv; i++) {
			UUID vid = new UUID(in.readLong(), in.readLong());
			Vehicle v = new Vehicle(vid, types[in.readByte()]);
			v.facing = in.readByte();
			v.load = in.readFloat();
			t.vehicles.add(v);
		}
		int np = in.readInt();
		for (int i = 0; i < np; i++) t.path.add(new Dir(in.readLong(), in.readBoolean()));
		t.tailOffset = in.readDouble();
		t.speed = in.readDouble();
		t.autopilot = in.readBoolean();
		t.dirSign = in.readByte();
		t.stopIndex = in.readInt();
		int ns = in.readInt();
		for (int i = 0; i < ns; i++) t.timetable.add(new Stop(in.readUTF(), in.readByte(), in.readShort()));
		try {
			t.derailed = in.readBoolean();   // absent in worlds saved before derailing existed
		} catch (java.io.EOFException e) {
			t.derailed = false;
		}
		return t;
	}

	// ------------------------------------------------------------------------------------------------ switches ahead

	/** The next switch on the way of the train: where it is, and which way the train is travelling when it arrives. */
	public record SwitchAhead(RailNode node, int side, double dist, V3 travel) {
	}

	/**
	 * First switch (a node whose exit side holds several tracks) in front of the train along the way it follows now.
	 * @param m +1 = looking past the head, -1 = past the tail (driving backwards)
	 */
	public SwitchAhead nextSwitch(RailGraph g, int m, double max) {
		if (path.isEmpty()) return null;
		RailGraph.Chooser ch = chooser();
		Dir cur;
		double pos;
		if (m >= 0) {
			cur = path.get(path.size() - 1);
			pos = headU() - segStart(g, path.size() - 1);
		} else {
			Dir first = path.get(0);
			cur = first.flip();
			pos = g.length(first.track()) - tailOffset;
		}
		double dist = 0;
		for (int i = 0; i < 40 && dist <= max; i++) {
			RailTrack tr = g.tracks.get(cur.track());
			if (tr == null) return null;
			dist += tr.length() - pos;
			pos = 0;
			RailNode node = g.endNodeOf(cur);
			if (node == null) return null;
			int exit = 1 - g.endSideOf(cur);
			if (node.side(exit).size() > 1) {
				V3 travel = cur.forward() ? tr.curve.tangentAtDist(tr.length()) : tr.curve.tangentAtDist(0).neg();
				return new SwitchAhead(node, exit, dist, travel);
			}
			Dir nx = g.next(cur, ch);
			if (nx == null) return null;
			cur = nx;
		}
		return null;
	}
}
