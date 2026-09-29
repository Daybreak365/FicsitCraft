package com.ficsitcraft.train;

import com.ficsitcraft.rail.Dir;
import com.ficsitcraft.rail.RailGraph;
import com.ficsitcraft.rail.RailNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Shortest-route search (Dijkstra over track directions) used by the autopilot. */
public final class Router {
	private Router() {
	}

	public record Route(List<Dir> dirs, Goal goal, double distance) {
		/** node id -> track to take there, for every node the route crosses. */
		public Map<Long, Long> choices(RailGraph g) {
			Map<Long, Long> m = new HashMap<>();
			for (int i = 0; i + 1 < dirs.size(); i++) {
				RailNode n = g.endNodeOf(dirs.get(i));
				if (n != null) m.put(n.id, dirs.get(i + 1).track());
			}
			return m;
		}
	}

	private record Entry(Dir dir, double cost) {
	}

	/** startPos: distance already covered on the start direction. */
	public static Route find(RailGraph g, Dir start, double startPos, List<Goal> goals) {
		if (goals.isEmpty() || !g.tracks.containsKey(start.track())) return null;
		Map<Dir, Double> best = new HashMap<>();
		Map<Dir, Dir> parent = new HashMap<>();
		PriorityQueue<Entry> pq = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
		best.put(start, -startPos);
		pq.add(new Entry(start, -startPos));
		double bestTotal = Double.POSITIVE_INFINITY;
		Dir bestDir = null;
		Goal bestGoal = null;
		int iterations = 0;
		while (!pq.isEmpty() && iterations++ < 30000) {
			Entry e = pq.poll();
			if (e.cost() > best.getOrDefault(e.dir(), Double.MAX_VALUE) + 1e-9) continue;
			if (e.cost() > bestTotal) break;
			for (Goal goal : goals) {
				if (goal.track() != e.dir().track() || goal.forward() != e.dir().forward()) continue;
				if (e.dir().equals(start) && goal.s() < startPos - 0.15) continue;
				double total = e.cost() + goal.s();
				if (total < bestTotal) {
					bestTotal = total;
					bestDir = e.dir();
					bestGoal = goal;
				}
			}
			double next = e.cost() + g.length(e.dir().track());
			for (Dir s : g.successors(e.dir())) {
				if (next < best.getOrDefault(s, Double.MAX_VALUE) - 1e-9) {
					best.put(s, next);
					parent.put(s, e.dir());
					pq.add(new Entry(s, next));
				}
			}
		}
		if (bestDir == null) return null;
		List<Dir> dirs = new ArrayList<>();
		Dir cur = bestDir;
		int guard = 0;
		while (cur != null && guard++ < 5000) {
			dirs.add(cur);
			if (cur.equals(start)) break;
			cur = parent.get(cur);
		}
		Collections.reverse(dirs);
		return new Route(dirs, bestGoal, Math.max(0, bestTotal));
	}
}
