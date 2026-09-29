package com.ficsitcraft.train;

import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;
import com.ficsitcraft.rail.RailTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The train simulation: acceleration / braking physics, block + path signals, collision avoidance, timetable autopilot.
 * Runs one step per game tick over all trains of one dimension. No Minecraft dependency.
 */
public final class TrainSim {
	public static final double MAX_SPEED = 1.1;        // blocks / tick (22 blocks/s)
	public static final double BRAKE = 0.0075;          // service braking, blocks / tick^2
	public static final double EMERGENCY = BRAKE * 2.4;
	public static final double TRACTION = 1.2;          // per locomotive, acceleration = TRACTION / total mass
	public static final double LOCO_MW = 110;
	public static final double SIGNAL_MARGIN = 3.0;
	public static final double TRAIN_MARGIN = 3.5;
	private static final double DRAG0 = 0.00008, DRAG1 = 0.0004, GRAVITY = 0.0075;

	public interface Stations {
		List<Goal> goals(String station);
	}

	public interface Hooks {
		/** True when the platforms of the station finished loading / unloading this train. */
		boolean loadingDone(Train train, String station);
	}

	public record Occ(Train train, double lo, double hi) {
	}

	public RailGraph graph;
	public final List<Train> trains = new ArrayList<>();
	/** Trains in unloaded areas: they don't move but still block the track. */
	public final List<Train> frozen = new ArrayList<>();
	public Stations stations = name -> List.of();
	public Hooks hooks = (t, s) -> true;
	private final Map<Long, List<Occ>> occ = new HashMap<>();
	private final Map<Long, Train> reservedBy = new HashMap<>();

	public TrainSim(RailGraph graph) {
		this.graph = graph;
	}

	public void replaceGraph(RailGraph g) {
		this.graph = g;
	}

	// ------------------------------------------------------------------------------------------------ occupancy

	private void rebuild() {
		occ.clear();
		reservedBy.clear();
		for (Train t : trains) {
			addOcc(t);
			t.reserved.removeIf(id -> t.path.stream().anyMatch(d -> d.track() == id));
			for (long id : t.reserved) reservedBy.putIfAbsent(id, t);
		}
		for (Train t : frozen) addOcc(t);
	}

	private void addOcc(Train t) {
		t.collectOccupancy(graph, (track, lo, hi) -> occ.computeIfAbsent(track, k -> new ArrayList<>()).add(new Occ(t, lo, hi)));
	}

	private void refreshOcc(Train t) {
		for (List<Occ> l : occ.values()) l.removeIf(o -> o.train() == t);
		addOcc(t);
		t.reserved.removeIf(id -> t.path.stream().anyMatch(d -> d.track() == id));
	}

	/** Trains occupying a track (for the game layer: signals display, platform docking). */
	public List<Occ> occupants(long track) {
		return occ.getOrDefault(track, List.of());
	}

	public boolean isTrackFree(long track) {
		List<Occ> l = occ.get(track);
		return (l == null || l.isEmpty()) && !reservedBy.containsKey(track);
	}

	// ------------------------------------------------------------------------------------------------ tick

	public void tick() {
		rebuild();
		for (Train t : trains) {
			if (t.vehicles.isEmpty() || t.path.isEmpty()) continue;
			step(t);
		}
	}

	private static final class Look {
		double limit = Double.POSITIVE_INFINITY;
		double goal = Double.POSITIVE_INFINITY;
		double speedCap = Double.POSITIVE_INFINITY;
	}

	private void step(Train t) {
		RailGraph.Chooser ch = t.chooser();
		t.powerDraw = 0;
		double cmd = controlCommand(t);
		int m = Math.abs(t.speed) > 1e-4 ? (t.speed > 0 ? 1 : -1) : (cmd > 0 ? 1 : cmd < 0 ? -1 : 0);
		if (m == 0 && !(t.autopilot && t.autoState == 1)) {
			t.speed = 0;
			t.limitDist = Double.POSITIVE_INFINITY;
			return;
		}
		if (m == 0) m = t.dirSign >= 0 ? 1 : -1;
		double vm = t.speed * m;
		Look look = lookahead(t, m, vm, ch);
		t.limitDist = look.limit;
		t.goalDist = look.goal;

		double cmdm = cmd * m;
		double vCmd = Math.max(0, cmdm) * MAX_SPEED;
		double vAllow = look.limit == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY
				: Math.min(Math.sqrt(2 * BRAKE * Math.max(0, look.limit)), Math.max(0, look.limit));
		double vLimit = Math.min(vAllow, look.speedCap);
		double target = Math.min(vCmd, vLimit);

		double mass = t.mass();
		double aMax = t.powered ? t.locoCount() * TRACTION / mass : 0;
		double drag = DRAG0 + DRAG1 * vm * vm;
		double resist = Math.max(drag + GRAVITY * slopeAlong(t, m), -0.0004);
		double fraction = 0;

		if (vm > vLimit + 1e-9) {
			vm = Math.max(vLimit, vm - EMERGENCY);
		} else if (vm > vCmd + 1e-9) {
			double dec = (t.brake || cmdm < 0) ? BRAKE * 1.4 : 0.0006 + drag;
			vm = Math.max(vCmd, vm - dec);
		} else if (vm < target - 1e-9) {
			double a = aMax - resist;
			vm = Math.max(0, Math.min(target, vm + a));
			if (aMax > 0) fraction = Math.min(1, (Math.max(a, 0) + Math.max(resist, 0)) / aMax);
		} else {
			// cruising on the target: traction just balances the resistance
			if (aMax > 0) {
				fraction = Math.max(0, Math.min(1, resist / aMax));
				if (aMax < resist) vm = Math.max(0, vm - (resist - aMax));
			} else {
				vm = Math.max(0, vm - Math.max(resist, 0));
			}
		}
		if (aMax > 0 && cmdm > 0 && vm > 0 && fraction > 0) t.powerDraw = t.locoCount() * LOCO_MW * (0.05 + 0.95 * fraction);
		if (t.brake || (cmdm <= 0 && vm > 0)) t.powerDraw = 0;

		double ds = Math.min(vm, Math.max(0, look.limit));
		if (ds < 1e-6 && vm < 0.004) {
			vm = 0;
			ds = 0;
		}
		double moved = t.move(graph, ch, m * ds);
		if (Math.abs(moved) < ds - 1e-9) vm = 0;
		t.speed = vm < 1e-5 ? 0 : m * vm;

		// autopilot arrival: snap onto the stop mark and start dwelling
		if (t.autopilot && !t.driven && t.autoState == 1 && look.goal < Double.POSITIVE_INFINITY) {
			double remaining = look.goal - Math.abs(moved);
			if (remaining < 0.06 && vm < 0.08) {
				if (remaining > 1e-6) t.move(graph, ch, m * remaining);
				t.speed = 0;
				t.autoState = 2;
				t.dwell = 0;
				t.docked = true;
				t.status = "docked";
			}
		}
		refreshOcc(t);
	}

	private double slopeAlong(Train t, int m) {
		double u = t.tailOffset + t.length() / 2;
		return m * t.tangentAtU(graph, u).y();
	}

	// ------------------------------------------------------------------------------------------------ control

	private double controlCommand(Train t) {
		if (t.autopilot && !t.driven) {
			autopilotUpdate(t);
			return t.autoCmd;
		}
		t.autoState = 0;
		t.autoCmd = 0;
		return t.driven ? t.manualCmd : 0;
	}

	private void autopilotUpdate(Train t) {
		if (t.timetable.isEmpty() || t.locoCount() == 0) {
			t.autoCmd = 0;
			t.status = "no_timetable";
			return;
		}
		if (t.stopIndex >= t.timetable.size()) t.stopIndex = 0;
		Stop stop = t.timetable.get(t.stopIndex);
		switch (t.autoState) {
			case 0, 3 -> {
				t.autoCmd = 0;
				if (t.autoState == 3 && t.retry-- > 0) return;
				List<Goal> goals = stations.goals(stop.station());
				if (goals.isEmpty()) {
					t.autoState = 3;
					t.retry = 40;
					t.status = "no_station";
					return;
				}
				boolean stopped = Math.abs(t.speed) < 0.01;
				Router.Route r = planRoute(t, t.dirSign, goals);
				if (r == null && stopped) {
					r = planRoute(t, -t.dirSign, goals);
					if (r != null) t.dirSign = -t.dirSign;
				}
				if (r == null) {
					t.autoState = 3;
					t.retry = 40;
					t.status = "no_route";
					return;
				}
				t.routeChoice = new HashMap<>(r.choices(graph));
				t.goal = r.goal();
				t.autoState = 1;
				t.docked = false;
				t.status = "en_route";
			}
			case 1 -> {
				t.autoCmd = t.dirSign;
				t.docked = false;
			}
			case 2 -> {
				t.autoCmd = 0;
				t.dwell++;
				boolean done;
				if (stop.mode() == Stop.WAIT_LOADED) {
					boolean platformsDone = t.dwell >= 40 && hooks.loadingDone(t, stop.station());
					boolean timedOut = stop.seconds() > 0 && t.dwell >= stop.seconds() * 20;
					done = platformsDone || timedOut;
					t.status = done || platformsDone ? "docked" : "waiting_cargo";
				} else {
					done = t.dwell >= Math.max(1, stop.seconds()) * 20;
					t.status = "waiting_time";
				}
				if (done) {
					t.stopIndex = (t.stopIndex + 1) % t.timetable.size();
					t.autoState = 0;
					t.docked = false;
					t.goal = null;
				}
			}
			default -> t.autoState = 0;
		}
	}

	private Router.Route planRoute(Train t, int dirSign, List<Goal> goals) {
		Dir start;
		double pos;
		if (dirSign >= 0) {
			start = t.path.get(t.path.size() - 1);
			pos = t.headU() - t.segStart(graph, t.path.size() - 1);
		} else {
			Dir first = t.path.get(0);
			start = first.flip();
			pos = graph.length(first.track()) - t.tailOffset;
		}
		return Router.find(graph, start, pos, goals);
	}

	// ------------------------------------------------------------------------------------------------ lookahead

	private Look lookahead(Train t, int m, double vm, RailGraph.Chooser ch) {
		Look r = new Look();
		int n = t.path.size();
		Dir cur;
		double pos;
		if (m > 0) {
			cur = t.path.get(n - 1);
			pos = t.headU() - t.segStart(graph, n - 1);
		} else {
			Dir f = t.path.get(0);
			cur = f.flip();
			pos = graph.length(f.track()) - t.tailOffset;
		}
		double reserveDist = vm * vm / (2 * BRAKE) + 8;
		double lookMax = Math.max(reserveDist * 1.3, 60) + 30;
		Goal goal = t.autopilot && !t.driven && t.autoState == 1 ? t.goal : null;
		double dist = 0;
		for (int iter = 0; iter < 14; iter++) {
			RailTrack tr = graph.tracks.get(cur.track());
			if (tr == null) {
				r.limit = Math.min(r.limit, dist);
				break;
			}
			double len = tr.length();
			double radius = tr.radius();
			if (radius < 1e9) {
				double vc = 0.14 * Math.sqrt(radius);
				r.speedCap = Math.min(r.speedCap, Math.sqrt(vc * vc + 2 * BRAKE * Math.max(0, dist)));
			}
			double obs = Double.POSITIVE_INFINITY;
			List<Occ> list = occ.get(cur.track());
			if (list != null) {
				for (Occ o : list) {
					if (o.train() == t) continue;
					double lo = cur.forward() ? o.lo() : len - o.hi();
					double hi = cur.forward() ? o.hi() : len - o.lo();
					if (hi < pos - 1e-6) continue;
					obs = Math.min(obs, Math.max(lo, pos) - pos);
				}
			}
			double goalHere = Double.POSITIVE_INFINITY;
			if (goal != null && goal.track() == cur.track() && goal.forward() == cur.forward() && goal.s() >= pos - 0.1) {
				goalHere = Math.max(0, goal.s() - pos);
			}
			if (obs < Double.POSITIVE_INFINITY && (goalHere == Double.POSITIVE_INFINITY || obs - TRAIN_MARGIN < goalHere)) {
				r.limit = Math.min(r.limit, dist + obs - TRAIN_MARGIN);
				return r;
			}
			if (goalHere < Double.POSITIVE_INFINITY) {
				r.goal = dist + goalHere;
				r.limit = Math.min(r.limit, dist + goalHere);
				return r;
			}
			dist += len - pos;
			pos = 0;
			if (dist > lookMax) return r;
			RailNode node = graph.endNodeOf(cur);
			int side = graph.endSideOf(cur);
			if (node != null && node.signal(side) != 0 && dist <= reserveDist) {
				if (!signalGreen(t, node, side, ch)) {
					r.limit = Math.min(r.limit, dist - SIGNAL_MARGIN);
					return r;
				}
			}
			Dir nx = graph.next(cur, ch);
			if (nx == null) {
				r.limit = Math.min(r.limit, dist);
				return r;
			}
			cur = nx;
		}
		return r;
	}

	// ------------------------------------------------------------------------------------------------ signals

	private boolean freeFor(Train t, long track) {
		Train res = reservedBy.get(track);
		if (res != null && res != t) return false;
		List<Occ> l = occ.get(track);
		if (l != null) for (Occ o : l) if (o.train() != t) return false;
		return true;
	}

	/** Would a train arriving over {@code side} be allowed through this signal right now? Grants path reservations. */
	private boolean signalGreen(Train t, RailNode node, int side, RailGraph.Chooser ch) {
		int exit = 1 - side;
		if (node.signal(side) == 1) {
			for (long tr : graph.blockRegion(node.id, exit)) if (!freeFor(t, tr)) return false;
			return true;
		}
		List<Long> route = pathRoute(node, exit, ch);
		for (long tr : route) if (!freeFor(t, tr)) return false;
		for (long tr : route) {
			t.reserved.add(tr);
			reservedBy.put(tr, t);
		}
		return true;
	}

	/** The tracks from a signal to the next signal along the given chooser. */
	private List<Long> pathRoute(RailNode node, int exit, RailGraph.Chooser ch) {
		List<Long> out = new ArrayList<>();
		List<Long> cands = node.side(exit);
		if (cands.isEmpty()) return out;
		long pick = cands.size() == 1 ? cands.get(0) : ch.choose(node, exit, cands);
		RailTrack first = graph.tracks.get(pick);
		if (first == null) return out;
		Dir cur = new Dir(pick, first.nodeA == node.id && first.sideA == exit);
		out.add(pick);
		double total = first.length();
		for (int i = 0; i < 10 && total < 300; i++) {
			RailNode e = graph.endNodeOf(cur);
			if (e == null || e.signal(graph.endSideOf(cur)) != 0) break;
			Dir nx = graph.next(cur, ch);
			if (nx == null) break;
			out.add(nx.track());
			total += graph.length(nx.track());
			cur = nx;
		}
		return out;
	}

	/** Display state of a signal (true = green) for a train that would arrive over {@code side}, without reserving anything. */
	public boolean signalOpen(RailNode node, int side) {
		int exit = 1 - side;
		if (node.signal(side) == 1) {
			for (long tr : graph.blockRegion(node.id, exit)) if (!isTrackFree(tr)) return false;
			return true;
		}
		for (long tr : pathRoute(node, exit, RailGraph.SWITCH_STATE)) if (!isTrackFree(tr)) return false;
		return true;
	}

	public Set<Long> reservedTracks() {
		return reservedBy.keySet();
	}
}
