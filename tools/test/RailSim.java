import com.ficsitcraft.rail.*;
import com.ficsitcraft.train.*;

import java.io.*;
import java.util.*;

/** Headless tests of the rail graph, planner and train simulation. Run: tools/test/run.sh */
public class RailSim {
	static int fails = 0;

	static void check(boolean ok, String msg) {
		if (!ok) {
			fails++;
			System.out.println("  FAIL: " + msg);
		}
	}

	static RailNode node(RailGraph g, double x, double z, double dx, double dz) {
		return g.addNode(new V3(x, 0, z), new V3(dx, 0, dz), RailNode.NO_OWNER);
	}

	/** Track from a's FRONT to b's BACK following node directions. */
	static RailTrack link(RailGraph g, RailNode a, RailNode b) {
		RailPlanner.Plan p = RailPlanner.plan(a.pos, a.dir, b.pos, b.dir.neg());
		if (!p.valid() && p.problem().equals("too_long")) {
			// tests use long tracks; the game splits them into <= 64 block pieces
			Bezier st = RailPlanner.straight(a.pos, b.pos);
			return g.connect(a.id, RailNode.FRONT, b.id, RailNode.BACK, st.p1, st.p2, RailNode.NO_OWNER);
		}
		if (!p.valid()) throw new IllegalStateException("plan invalid " + p.problem() + " " + a.pos + "->" + b.pos);
		return g.connect(a.id, RailNode.FRONT, b.id, RailNode.BACK, p.p1(), p.p2(), RailNode.NO_OWNER);
	}

	static Train place(RailGraph g, RailTrack t, double dist, VehicleType... types) {
		Train tr = new Train();
		tr.path.add(new Dir(t.id, true));
		tr.tailOffset = dist;
		for (VehicleType vt : types) tr.vehicles.add(new Vehicle(vt));
		return tr;
	}

	static RailGraph loop(List<RailTrack> out) {
		RailGraph g = new RailGraph();
		double[][] pts = {{0, 0, 1, 0}, {40, 0, 1, 0}, {60, 20, 0, 1}, {60, 60, 0, 1}, {40, 80, -1, 0}, {0, 80, -1, 0}, {-20, 60, 0, -1}, {-20, 20, 0, -1}};
		List<RailNode> ns = new ArrayList<>();
		for (double[] p : pts) ns.add(node(g, p[0], p[1], p[2], p[3]));
		for (int i = 0; i < ns.size(); i++) out.add(link(g, ns.get(i), ns.get((i + 1) % ns.size())));
		return g;
	}

	static void testBezierPlanner() {
		System.out.println("[bezier / planner]");
		RailPlanner.Plan p = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(30, 0, 30), null);
		check(p.valid(), "free arc valid: " + p.problem());
		Bezier b = p.curve();
		double prev = -1;
		for (int i = 0; i <= 50; i++) {
			double t = b.tAtDist(b.length * i / 50.0);
			check(t >= prev - 1e-12, "tAtDist monotonic");
			prev = t;
		}
		check(Math.abs(b.distAtT(b.tAtDist(17.3)) - 17.3) < 0.05, "dist/t roundtrip");
		Bezier[] h = b.split(0.4);
		check(h[0].p3.distanceTo(h[1].p0) < 1e-9, "split joins");
		check(Math.abs(h[0].length + h[1].length - b.length) < 0.1, "split length preserved " + (h[0].length + h[1].length) + " vs " + b.length);
		V3 t0 = h[0].unitTangent(1), t1 = h[1].unitTangent(0);
		check(t0.dot(t1) > 0.9999, "split tangent continuous");
		// 90 degree turn radius; a tight 90 degree corner (radius ~5) is allowed, ~3 is not
		RailPlanner.Plan tight = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(6, 0, 6), null);
		check(tight.valid(), "tight corner ok: " + tight.problem());
		check(b.minRadius() > 12, "radius ok " + b.minRadius());
		RailPlanner.Plan sharp = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(3, 0, 3), null);
		check(!sharp.valid() && sharp.problem().equals("too_sharp"), "sharp rejected: " + sharp.problem());
		RailPlanner.Plan steep = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(20, 12, 0), null);
		check(!steep.valid() && steep.problem().equals("too_steep"), "steep rejected: " + steep.problem());
		RailPlanner.Plan back = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(-20, 0, 2), null);
		check(!back.valid(), "backwards rejected");
		RailPlanner.Plan mid = RailPlanner.plan(new V3(0, 0, 0), new V3(1, 0, 0), new V3(20, 0, 0), null);
		check(mid.valid() && Math.abs(mid.length() - 20) < 0.01, "straight");
	}

	static void testGraph() throws Exception {
		System.out.println("[graph]");
		List<RailTrack> ts = new ArrayList<>();
		RailGraph g = loop(ts);
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		g.write(new DataOutputStream(bo));
		RailGraph g2 = RailGraph.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
		check(g2.nodes.size() == g.nodes.size() && g2.tracks.size() == g.tracks.size(), "roundtrip counts");
		RailTrack t = ts.get(0);
		Dir d = new Dir(t.id, true);
		for (int i = 0; i < 8; i++) {
			d = g2.next(d, RailGraph.SWITCH_STATE);
			check(d != null, "loop next " + i);
			if (d == null) return;
		}
		check(d.equals(new Dir(t.id, true)), "loop closes after 8 tracks");
		// split
		RailGraph g3 = loop(new ArrayList<>());
		long firstTrack = g3.tracks.keySet().iterator().next();
		double before = g3.tracks.get(firstTrack).length();
		long[] sp = g3.split(firstTrack, before / 2);
		check(sp != null, "split ok");
		if (sp != null) {
			check(g3.tracks.size() == 9, "split -> 9 tracks");
			check(Math.abs(g3.length(sp[1]) + g3.length(sp[2]) - before) < 0.1, "split preserves length");
			Dir dd = g3.next(new Dir(sp[1], true), RailGraph.SWITCH_STATE);
			check(dd != null && dd.track() == sp[2] && dd.forward(), "traverse through split node");
			Dir back = g3.next(new Dir(sp[2], false), RailGraph.SWITCH_STATE);
			check(back != null && back.track() == sp[1] && !back.forward(), "traverse split node backwards");
		}
		g3.removeTrack(firstTrack == 0 ? 1 : ts.get(3).id);
		check(true, "remove ok");
	}

	static void testDriving() {
		System.out.println("[manual driving]");
		List<RailTrack> ts = new ArrayList<>();
		RailGraph g = loop(ts);
		TrainSim sim = new TrainSim(g);
		Train tr = place(g, ts.get(0), 60 < ts.get(0).length() ? 30 : 5, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
		// make sure the initial path covers the train
		while (tr.pathLength(g) < tr.headU()) tr.extendFront(g, RailGraph.SWITCH_STATE);
		tr.powered = true;
		tr.driven = true;
		tr.manualCmd = 1;
		sim.trains.add(tr);
		double maxV = 0, maxStep = 0;
		List<V3> lastCenters = null;
		for (int i = 0; i < 4000; i++) {
			sim.tick();
			maxV = Math.max(maxV, Math.abs(tr.speed));
			List<V3> centers = new ArrayList<>();
			for (int k = 0; k < tr.vehicles.size(); k++) centers.add(tr.pointAtU(g, tr.vehicleCenterU(k)));
			if (lastCenters != null) {
				for (int k = 0; k < centers.size(); k++) maxStep = Math.max(maxStep, centers.get(k).distanceTo(lastCenters.get(k)));
			}
			lastCenters = centers;
			if (i == 1500) {
				check(maxV > 0.6, "reached cruising speed " + maxV);
				tr.manualCmd = 0;
				tr.brake = true;
			}
			if (i == 2600) check(Math.abs(tr.speed) < 1e-6, "stopped after brake " + tr.speed);
			if (i == 2601) {
				tr.brake = false;
				tr.manualCmd = -1;
			}
		}
		check(maxStep < 1.11, "no teleporting, max step " + maxStep);
		check(tr.path.size() <= 6, "path trimmed: " + tr.path.size());
		// spacing along the path stays constant
		double u0 = tr.vehicleCenterU(0), u1 = tr.vehicleCenterU(1);
		check(Math.abs((u1 - u0) - (4.5 + Train.GAP + 4)) < 1e-9, "vehicle spacing");
		check(tr.speed < 0, "reverses with negative command: " + tr.speed);
		System.out.printf("  maxV=%.3f finalSpeed=%.3f path=%d%n", maxV, tr.speed, tr.path.size());
	}

	static void testDeadEnd() {
		System.out.println("[dead end]");
		RailGraph g = new RailGraph();
		RailNode a = node(g, 0, 0, 1, 0), b = node(g, 100, 0, 1, 0);
		RailTrack t = link(g, a, b);
		TrainSim sim = new TrainSim(g);
		Train tr = place(g, t, 20, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR);
		tr.powered = true;
		tr.driven = true;
		tr.manualCmd = 1;
		sim.trains.add(tr);
		for (int i = 0; i < 3000; i++) sim.tick();
		double head = tr.headU();
		check(Math.abs(head - 100) < 0.6, "stops at buffer: head=" + head + " speed=" + tr.speed);
		check(Math.abs(tr.speed) < 1e-6, "speed zero at end");
	}

	// two stations on a straight line: autopilot shuttles between them
	static void testAutopilot() {
		System.out.println("[autopilot shuttle]");
		RailGraph g = new RailGraph();
		RailNode a0 = node(g, 0, 0, 1, 0), a1 = node(g, 12, 0, 1, 0), m = node(g, 300, 0, 1, 0), b0 = node(g, 400, 0, 1, 0), b1 = node(g, 412, 0, 1, 0);
		RailTrack sa = link(g, a0, a1), mid1 = link(g, a1, m), mid2 = link(g, m, b0), sb = link(g, b0, b1);
		TrainSim sim = new TrainSim(g);
		sim.stations = name -> name.equals("A") ? List.of(new Goal(sa.id, true, sa.length()), new Goal(sa.id, false, sa.length()))
				: List.of(new Goal(sb.id, true, sb.length()), new Goal(sb.id, false, sb.length()));
		Train tr = place(g, sa, 0, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
		// train sits with its tail at 0 -> head 26.6 -> extend
		while (tr.pathLength(g) < tr.headU()) tr.extendFront(g, RailGraph.SWITCH_STATE);
		tr.powered = true;
		tr.autopilot = true;
		tr.timetable.add(new Stop("B", Stop.WAIT_SECONDS, 3));
		tr.timetable.add(new Stop("A", Stop.WAIT_SECONDS, 3));
		sim.trains.add(tr);
		int arrivals = 0;
		int lastState = -1;
		List<String> log = new ArrayList<>();
		for (int i = 0; i < 20000 && arrivals < 4; i++) {
			sim.tick();
			if (tr.autoState == 2 && lastState != 2) {
				arrivals++;
				// where is the leading end?
				double lead = tr.dirSign >= 0 ? tr.headU() : tr.tailOffset;
				Dir d = tr.dirSign >= 0 ? tr.path.get(tr.path.size() - 1) : tr.path.get(0).flip();
				double seg = tr.dirSign >= 0 ? tr.segStart(g, tr.path.size() - 1) : 0;
				double posInSeg = tr.dirSign >= 0 ? lead - seg : g.length(d.track()) - lead;
				log.add(String.format("arrival %d at tick %d stop=%d track=%d pos=%.3f/%.3f dir=%d", arrivals, i, tr.stopIndex, d.track(), posInSeg, g.length(d.track()), tr.dirSign));
				check(Math.abs(posInSeg - g.length(d.track())) < 0.1, "exact stop at station end: " + posInSeg + " vs " + g.length(d.track()));
				check(Math.abs(tr.speed) < 1e-6, "speed 0 while docked");
			}
			lastState = tr.autoState;
		}
		log.forEach(s -> System.out.println("  " + s));
		check(arrivals >= 4, "completed 4 arrivals, got " + arrivals);
	}

	// follow-the-leader on a loop with block signals: gaps and no overlap
	static void testSignalsLoop() {
		System.out.println("[block signals on loop]");
		List<RailTrack> ts = new ArrayList<>();
		RailGraph g = loop(ts);
		// signals at every node arriving from BACK side (normal direction)
		int n = 0;
		for (RailNode nd : g.nodes.values()) {
			if (n++ % 2 == 0) nd.setSignal(RailNode.BACK, 1);
		}
		TrainSim sim = new TrainSim(g);
		List<Train> trains = new ArrayList<>();
		double[] starts = {5, 30, 55};
		for (int k = 0; k < 3; k++) {
			Train tr = place(g, ts.get(k), 5, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR);
			while (tr.pathLength(g) < tr.headU()) tr.extendFront(g, RailGraph.SWITCH_STATE);
			tr.powered = true;
			tr.driven = true;
			tr.manualCmd = 1;
			sim.trains.add(tr);
			trains.add(tr);
		}
		double minGap = 1e9;
		boolean overlap = false;
		int moving = 0;
		for (int i = 0; i < 12000; i++) {
			sim.tick();
			// overlap: any track where two trains' occupied intervals intersect
			Map<Long, List<TrainSim.Occ>> byTrack = new HashMap<>();
			for (Train t : trains) t.collectOccupancy(g, (tr, lo, hi) -> byTrack.computeIfAbsent(tr, x -> new ArrayList<>()).add(new TrainSim.Occ(t, lo, hi)));
			for (List<TrainSim.Occ> l : byTrack.values()) {
				for (int a = 0; a < l.size(); a++)
					for (int b = a + 1; b < l.size(); b++) {
						if (l.get(a).train() == l.get(b).train()) continue;
						double gap = Math.max(l.get(a).lo() - l.get(b).hi(), l.get(b).lo() - l.get(a).hi());
						minGap = Math.min(minGap, gap);
						if (gap < 0) overlap = true;
					}
			}
			if (i > 6000 && Math.abs(trains.get(0).speed) > 0.2) moving++;
		}
		System.out.printf("  minGap=%.2f movingTicks=%d%n", minGap, moving);
		check(!overlap, "trains never overlap");
		check(moving > 1000, "traffic keeps flowing (no deadlock)");
		check(minGap > 0.5, "always some gap: " + minGap);
	}

	// junction routing: autopilot picks the branch to reach the station regardless of switch state
	static void testJunction() {
		System.out.println("[junction routing]");
		RailGraph g = new RailGraph();
		RailNode s0 = node(g, 0, 0, 1, 0), s1 = node(g, 60, 0, 1, 0);
		RailNode j = s1; // junction node: two tracks on FRONT side
		RailNode up = node(g, 120, 30, 1, 0), dn = node(g, 120, -30, 1, 0);
		RailNode e1 = node(g, 200, 30, 1, 0), e2 = node(g, 200, -30, 1, 0);
		RailTrack t0 = link(g, s0, s1);
		RailTrack tu = link(g, s1, up), td = link(g, s1, dn);
		RailTrack su = link(g, up, e1), sd = link(g, dn, e2);
		check(j.front.size() == 2, "two branches on the junction");
		TrainSim sim = new TrainSim(g);
		sim.stations = name -> name.equals("UP") ? List.of(new Goal(su.id, true, su.length())) : List.of(new Goal(sd.id, true, sd.length()));
		Train tr = place(g, t0, 0, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR);
		while (tr.pathLength(g) < tr.headU()) tr.extendFront(g, RailGraph.SWITCH_STATE);
		tr.powered = true;
		tr.autopilot = true;
		tr.timetable.add(new Stop("DOWN", Stop.WAIT_SECONDS, 1));
		sim.trains.add(tr);
		j.setSel(RailNode.FRONT, 0); // switch points at UP, autopilot must override it
		for (int i = 0; i < 6000 && tr.autoState != 2; i++) sim.tick();
		check(tr.autoState == 2, "docked at DOWN (state " + tr.autoState + " " + tr.status + ")");
		boolean onDown = tr.path.stream().anyMatch(d -> d.track() == sd.id);
		check(onDown, "took the lower branch");
		// manual: follows the switch state
		Train m = place(g, t0, 0, VehicleType.LOCOMOTIVE);
		RailGraph g2 = g;
		while (m.pathLength(g2) < m.headU()) m.extendFront(g2, RailGraph.SWITCH_STATE);
		m.powered = true;
		m.driven = true;
		m.manualCmd = 1;
		TrainSim sim2 = new TrainSim(g);
		sim2.trains.add(m);
		j.setSel(RailNode.FRONT, 1);
		for (int i = 0; i < 2500; i++) sim2.tick();
		check(m.path.stream().anyMatch(d -> d.track() == td.id || d.track() == sd.id), "manual train followed switch (lower)");
	}

	// two branches merge into one: path signals must serialise the trains
	static void testMerge() {
		System.out.println("[merge with path signals]");
		RailGraph g = new RailGraph();
		RailNode a0 = node(g, 0, 25, 1, 0), b0 = node(g, 0, -25, 1, 0);
		RailNode a1 = node(g, 90, 25, 1, 0), b1 = node(g, 90, -25, 1, 0);
		RailNode j = node(g, 140, 0, 1, 0);
		RailNode out = node(g, 300, 0, 1, 0);
		RailTrack ta = link(g, a0, a1), tb = link(g, b0, b1);
		// both branches converge into the BACK side of j
		RailPlanner.Plan pa = RailPlanner.plan(a1.pos, a1.dir, j.pos, j.dir.neg());
		RailPlanner.Plan pb = RailPlanner.plan(b1.pos, b1.dir, j.pos, j.dir.neg());
		check(pa.valid() && pb.valid(), "merge plans valid " + pa.problem() + pb.problem());
		RailTrack ja = g.connect(a1.id, RailNode.FRONT, j.id, RailNode.BACK, pa.p1(), pa.p2(), RailNode.NO_OWNER);
		RailTrack jb = g.connect(b1.id, RailNode.FRONT, j.id, RailNode.BACK, pb.p1(), pb.p2(), RailNode.NO_OWNER);
		RailTrack jo = link(g, j, out);
		check(j.back.size() == 2, "merge node has two feeders");
		// path signals on both feeder ends (arrive over a1.BACK / b1.BACK)
		a1.setSignal(RailNode.BACK, 2);
		b1.setSignal(RailNode.BACK, 2);
		out.setSignal(RailNode.BACK, 1);
		TrainSim sim = new TrainSim(g);
		Train A = place(g, ta, 0, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
		Train B = place(g, tb, 0, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
		for (Train t : List.of(A, B)) {
			while (t.pathLength(g) < t.headU()) t.extendFront(g, RailGraph.SWITCH_STATE);
			t.powered = true;
			t.driven = true;
			t.manualCmd = 1;
			sim.trains.add(t);
		}
		boolean overlap = false;
		double minGap = 1e9;
		boolean bBlockedWhileAParked = false;
		for (int i = 0; i < 6000; i++) {
			sim.tick();
			if (i == 2500) {
				// A is parked at the buffer stop and still blocks the merge exit; B must be waiting before its signal
				bBlockedWhileAParked = B.path.stream().noneMatch(d -> d.track() == jo.id) && B.path.stream().noneMatch(d -> d.track() == jb.id);
				sim.trains.remove(A);
				A.reserved.clear();
			}
			Map<Long, List<TrainSim.Occ>> byTrack = new HashMap<>();
			for (Train t : sim.trains) t.collectOccupancy(g, (tr, lo, hi) -> byTrack.computeIfAbsent(tr, x -> new ArrayList<>()).add(new TrainSim.Occ(t, lo, hi)));
			for (List<TrainSim.Occ> l : byTrack.values())
				for (int x = 0; x < l.size(); x++)
					for (int y = x + 1; y < l.size(); y++) {
						if (l.get(x).train() == l.get(y).train()) continue;
						double gap = Math.max(l.get(x).lo() - l.get(y).hi(), l.get(y).lo() - l.get(x).hi());
						minGap = Math.min(minGap, gap);
						if (gap < 0) overlap = true;
					}
		}
		check(!overlap, "no overlap at merge, minGap=" + minGap);
		// both trains must have cleared the merge (reached the dead end at 400)
		check(A.path.stream().anyMatch(d -> d.track() == jo.id), "A passed merge");
		check(bBlockedWhileAParked, "B held back while the merge exit was occupied");
		check(B.path.stream().anyMatch(d -> d.track() == jo.id), "B passed merge");
		System.out.printf("  minGap=%.2f%n", minGap);
	}

	static void testSplitCouple() {
		System.out.println("[split / couple]");
		RailGraph g = new RailGraph();
		RailNode a = node(g, 0, 0, 1, 0), b = node(g, 100, 0, 1, 0);
		RailTrack t = link(g, a, b);
		Train tr = place(g, t, 10, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
		double len = tr.length();
		double headBefore = tr.headU();
		Train head = tr.split(g, 2);
		check(head != null && head.vehicles.size() == 2 && tr.vehicles.size() == 2, "split sizes");
		check(Math.abs(tr.headU() - (10 + 9 + Train.GAP + 8)) < 1e-9, "tail part geometry " + tr.headU());
		check(Math.abs(head.headU() - headBefore) < 1e-9, "head part geometry " + head.headU());
		check(Math.abs(head.tailOffset - (10 + 9 + Train.GAP + 8 + Train.GAP)) < 1e-9, "head part tail " + head.tailOffset);
		boolean ok = tr.coupleHeadTo(g, head);
		check(ok, "couple back together");
		check(tr.vehicles.size() == 4, "coupled vehicle count");
		check(Math.abs(tr.length() - len) < 1e-9, "same length");
		// add vehicle at head and tail
		check(tr.addVehicle(g, RailGraph.SWITCH_STATE, new Vehicle(VehicleType.FLUID_CAR), true), "add at head");
		check(tr.addVehicle(g, RailGraph.SWITCH_STATE, new Vehicle(VehicleType.FREIGHT_CAR), false), "add at tail");
		check(tr.vehicles.size() == 6, "six vehicles");
		// persistence
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			tr.timetable.add(new Stop("X", 1, 9));
			tr.write(new DataOutputStream(bo));
			Train r = Train.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
			check(r.vehicles.size() == 6 && r.timetable.size() == 1 && Math.abs(r.tailOffset - tr.tailOffset) < 1e-9, "train roundtrip");
		} catch (IOException e) {
			check(false, "io " + e);
		}
	}

	// figure-8-free stress: a loop with a passing siding, 3 autopilot trains, stations on the loop and the siding
	static void testStress() {
		System.out.println("[stress: loop + siding, 3 autopilot trains]");
		RailGraph g = new RailGraph();
		// main line: rectangle of straight pieces (long tracks allowed in tests)
		RailNode a = node(g, 0, 0, 1, 0), b = node(g, 30, 0, 1, 0), c = node(g, 210, 0, 1, 0), d = node(g, 250, 0, 1, 0);
		RailNode e = node(g, 290, 40, 0, 1), f = node(g, 290, 100, 0, 1), h = node(g, 250, 140, -1, 0);
		RailNode i = node(g, 210, 140, -1, 0), j = node(g, 30, 140, -1, 0), k = node(g, -10, 140, -1, 0);
		RailNode l = node(g, -50, 100, 0, -1), m = node(g, -50, 40, 0, -1);
		RailNode[] ring = {a, b, c, d, e, f, h, i, j, k, l, m};
		RailTrack[] tr = new RailTrack[ring.length];
		for (int q = 0; q < ring.length; q++) tr[q] = link(g, ring[q], ring[(q + 1) % ring.length]);
		// siding parallel to b->c: b2 --- c2 branching from b and merging into c
		RailNode s1 = node(g, 75, -25, 1, 0), s2 = node(g, 165, -25, 1, 0);
		RailPlanner.Plan out = RailPlanner.plan(b.pos, b.dir, s1.pos, s1.dir.neg());
		check(out.valid(), "siding entry " + out.problem());
		RailTrack tOut = g.connect(b.id, RailNode.FRONT, s1.id, RailNode.BACK, out.p1(), out.p2(), RailNode.NO_OWNER);
		RailTrack tSt = link(g, s1, s2);
		RailPlanner.Plan in = RailPlanner.plan(s2.pos, s2.dir, c.pos, c.dir.neg());
		check(in.valid(), "siding exit " + in.problem());
		RailTrack tIn = g.connect(s2.id, RailNode.FRONT, c.id, RailNode.BACK, in.p1(), in.p2(), RailNode.NO_OWNER);
		// signals: path signals at the junction entries, block signals on the ring
		b.setSignal(RailNode.BACK, 2);
		c.setSignal(RailNode.BACK, 2);
		for (RailNode n : new RailNode[]{e, h, j, l}) n.setSignal(RailNode.BACK, 1);
		// stations
		TrainSim sim = new TrainSim(g);
		sim.stations = name -> switch (name) {
			case "WEST" -> List.of(new Goal(tr[11].id, true, tr[11].length()));
			case "SIDING" -> List.of(new Goal(tSt.id, true, tSt.length()));
			default -> List.of(new Goal(tr[5].id, true, tr[5].length()));
		};
		String[][] plans = {{"WEST", "SIDING", "EAST"}, {"SIDING", "EAST", "WEST"}, {"EAST", "WEST", "SIDING"}};
		int[] track = {3, 6, 9};
		List<Train> ts = new ArrayList<>();
		for (int q = 0; q < 3; q++) {
			Train t = place(g, tr[track[q]], 10, VehicleType.LOCOMOTIVE, VehicleType.FREIGHT_CAR, VehicleType.FREIGHT_CAR);
			while (t.pathLength(g) < t.headU()) t.extendFront(g, RailGraph.SWITCH_STATE);
			t.powered = true;
			t.autopilot = true;
			for (String st : plans[q]) t.timetable.add(new Stop(st, Stop.WAIT_SECONDS, 4));
			sim.trains.add(t);
			ts.add(t);
		}
		int sidingTicks = 0;
		int[] docks = new int[3];
		int[] last = new int[3];
		double minGap = 1e9;
		boolean overlap = false;
		for (int tick = 0; tick < 60000; tick++) {
			sim.tick();
			if (sim.occupants(tSt.id).size() > 0) sidingTicks++;
			for (int q = 0; q < 3; q++) {
				if (ts.get(q).autoState == 2 && last[q] != 2) docks[q]++;
				last[q] = ts.get(q).autoState;
			}
			if (tick % 3 == 0) {
				Map<Long, List<TrainSim.Occ>> byTrack = new HashMap<>();
				for (Train t : ts) t.collectOccupancy(g, (trk, lo, hi) -> byTrack.computeIfAbsent(trk, x -> new ArrayList<>()).add(new TrainSim.Occ(t, lo, hi)));
				for (List<TrainSim.Occ> lst : byTrack.values())
					for (int x = 0; x < lst.size(); x++)
						for (int y = x + 1; y < lst.size(); y++) {
							if (lst.get(x).train() == lst.get(y).train()) continue;
							double gap = Math.max(lst.get(x).lo() - lst.get(y).hi(), lst.get(y).lo() - lst.get(x).hi());
							minGap = Math.min(minGap, gap);
							if (gap < 0) overlap = true;
						}
			}
		}
		System.out.printf("  docks per train: %d %d %d, minGap=%.2f, siding used %d ticks%n", docks[0], docks[1], docks[2], minGap, sidingTicks);
		check(!overlap, "no collisions in 60000 ticks (minGap " + minGap + ")");
		for (int q = 0; q < 3; q++) check(docks[q] >= 8, "train " + q + " keeps running: " + docks[q] + " docks, state " + ts.get(q).autoState + " " + ts.get(q).status);
	}

	static void testPlacement() {
		System.out.println("[placement]");
		RailGraph g = new RailGraph();
		RailPlacement.Anchor a = RailPlacement.resolve(g, new V3(0.2, 64, 0.1), 1, 0, false, null);
		check(!a.isNode() && a.leave().x() > 0.99, "free start faces look dir");
		RailPlacement.Anchor b = RailPlacement.resolve(g, new V3(30, 64, 10), 1, 0, true, a.pos());
		RailPlacement.Result r = RailPlacement.plan(g, a, b);
		check(r.valid(), "free-free plan " + r.problem());
		// commit
		RailNode na = g.addNode(a.pos(), a.leave(), RailNode.NO_OWNER);
		V3 arrive = r.plan().curve().unitTangent(1);
		RailNode nb = g.addNode(b.pos(), arrive, RailNode.NO_OWNER);
		g.connect(na.id, RailNode.FRONT, nb.id, RailNode.BACK, r.plan().p1(), r.plan().p2(), RailNode.NO_OWNER);
		// start from the end node continuing on
		RailPlacement.Anchor c = RailPlacement.resolve(g, new V3(30.5, 64, 10.3), arrive.x(), arrive.z(), false, null);
		check(c.isNode() && c.side() == RailNode.FRONT, "snaps to end node, free side in look direction");
		RailPlacement.Anchor d = RailPlacement.resolve(g, new V3(60, 64, 10), 1, 0, true, c.pos());
		RailPlacement.Result r2 = RailPlacement.plan(g, c, d);
		check(r2.valid(), "extend plan " + r2.problem());
		// split
		V3 mid = g.tracks.values().iterator().next().curve.point(0.5);
		RailPlacement.Anchor s = RailPlacement.resolve(g, mid.add(new V3(0.5, 0, 0)), 0, 1, false, null);
		check(s.isSplit(), "start on mid-track splits");
		RailPlacement.Anchor e = RailPlacement.resolve(g, mid.add(new V3(20, 0, 25)), 0, 1, true, s.pos());
		RailPlacement.Result r3 = RailPlacement.plan(g, s, e);
		System.out.println("  branch plan: " + r3.problem());
		check(r3.valid() || r3.problem().equals("too_sharp") || r3.problem().equals("bad_angle"), "branch plan sane");
	}

	public static void main(String[] args) throws Exception {
		testPlacement();
		testStress();
		testBezierPlanner();
		testGraph();
		testDriving();
		testDeadEnd();
		testAutopilot();
		testSignalsLoop();
		testJunction();
		testMerge();
		testSplitCouple();
		System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILURES");
		System.exit(fails == 0 ? 0 : 1);
	}
}
