package com.ficsitcraft.fluid;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pipe network simulation, solved once per server tick.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li><b>Head</b>: every node has a hydraulic head {@code H = y + fill} (in blocks). Fluid flows from high head to
 *   low head, so partially filled pipes drain downhill and level out horizontally, but cannot climb on their own.</li>
 *   <li><b>Pressure / head lift</b>: pumps and water extractors are pressure sources with an absolute head of
 *   {@code pumpY + headLift}. Inside full pipe regions the pressure field is solved (Laplace relaxation between
 *   free surfaces and pumps), so connected vessels level out and fluid is pushed upwards only until it reaches the
 *   pump's head lift — like Satisfactory.</li>
 *   <li><b>Inertia / sloshing</b>: every pipe connection keeps a flow velocity that is accelerated by the head
 *   difference and slowed by friction. Because fluid has momentum, levels overshoot and oscillate ("slosh") before
 *   settling, and flow keeps going for a moment after a pump stops.</li>
 *   <li><b>Throughput</b>: the flow of a connection is capped by the slower pipe (Mk.1 300 m³/min, Mk.2 600 m³/min).</li>
 * </ul>
 */
public final class FluidNetworkManager {
	private static final Map<ServerWorld, FluidNetworkManager> MANAGERS = new WeakHashMap<>();
	/** m³/tick² gained per block of head difference. */
	private static final double ACCEL = 0.02;
	/** Fraction of velocity lost per tick (low = more sloshing). */
	private static final double FRICTION = 0.03;
	/** Relaxation sweeps of the pressure solve inside full pipe regions. */
	private static final int SWEEPS = 40;
	/** A node counts as "full" (transmits pressure) above this fill level. */
	private static final double FULL = 0.94;

	private final Map<BlockPos, FluidNode> active = new HashMap<>();
	/** Flow velocity per connection (key: lower node pos + direction). Positive = towards the neighbour. */
	private Map<Long, Double> velocity = new HashMap<>();

	public static FluidNetworkManager get(ServerWorld world) {
		return MANAGERS.computeIfAbsent(world, w -> new FluidNetworkManager());
	}

	public static void onWorldTickEnd(ServerWorld world) {
		FluidNetworkManager m = MANAGERS.get(world);
		if (m != null) m.solve(world);
	}

	public void mark(FluidNode node) {
		for (BlockPos cell : node.getCells()) active.put(cell, node);
	}

	private static long key(BlockPos pos, Direction dir) {
		return pos.asLong() * 8 + dir.ordinal();
	}

	private static boolean linked(FluidNode a, BlockPos cell, Direction dir, FluidNode b) {
		return a.connectsAt(cell, dir) && b.connectsAt(cell.offset(dir), dir.getOpposite());
	}

	/** One connection between two nodes through a specific cell face. */
	private record Link(FluidNode a, BlockPos cell, Direction dir, FluidNode b) {
	}

	private static double fill(FluidNode n) {
		return n.getCapacity() <= 0 ? 0 : Math.min(1.0, n.getAmount() / n.getCapacity());
	}

	private void solve(ServerWorld world) {
		if (active.isEmpty()) {
			velocity.clear();
			return;
		}
		List<FluidNode> nodes = new ArrayList<>();
		for (FluidNode n : new java.util.LinkedHashSet<>(active.values())) if (!n.isNodeRemoved()) nodes.add(n);

		// ---- connections (each cell face once) and neighbour lists (linked + compatible fluid)
		List<Link> linkList = new ArrayList<>();
		Map<FluidNode, List<FluidNode>> links = new HashMap<>();
		for (FluidNode n : nodes) links.put(n, new ArrayList<>(6));
		for (FluidNode n : nodes) {
			for (BlockPos cell : n.getCells()) {
				for (Direction d : Direction.values()) {
					BlockPos np = cell.offset(d);
					FluidNode m = active.get(np);
					if (m == null || m == n || m.isNodeRemoved()) continue;
					if (cell.asLong() > np.asLong()) continue; // canonical direction: each face once
					if (!linked(n, cell, d, m)) continue;
					linkList.add(new Link(n, cell, d, m));
					if (compatible(n, m)) {
						links.get(n).add(m);
						links.get(m).add(n);
					}
				}
			}
		}

		// ---- 1. heads: surfaces and pumps are fixed; inside full pipe regions solve the pressure field
		//         (discrete Laplace relaxation) so the whole column is pushed, not just the surface.
		Map<FluidNode, Double> head = new HashMap<>();
		List<FluidNode> free = new ArrayList<>();
		for (FluidNode n : nodes) {
			double f = fill(n);
			double h = n.getNodePos().getY() + f * n.getHeadHeight();
			double src = n.getSourceHead();
			if (!Double.isNaN(src) && n.getAmount() > 1e-6 && src > h) {
				head.put(n, src);
			} else {
				head.put(n, h);
				if (f >= FULL) free.add(n);
			}
		}
		for (int sweep = 0; sweep < SWEEPS; sweep++) {
			for (FluidNode n : free) {
				List<FluidNode> nb = links.get(n);
				if (nb.isEmpty()) continue;
				double sum = 0;
				for (FluidNode m : nb) sum += head.get(m);
				head.put(n, Math.max(n.getNodePos().getY() + n.getHeadHeight(), sum / nb.size()));
			}
		}

		// ---- 2. inertial flow along every connection (velocity integration => momentum => sloshing)
		List<long[]> keys = new ArrayList<>();
		List<FluidNode[]> pairs = new ArrayList<>();
		List<double[]> flows = new ArrayList<>();
		for (Link l : linkList) {
			FluidNode n = l.a();
			FluidNode m = l.b();
			Direction d = l.dir();
			if (!compatible(n, m)) continue;
			long k = key(l.cell(), d);
			double v = velocity.getOrDefault(k, 0.0);
			double hn = headSeenFrom(n, d, head.get(n));
			double hm = headSeenFrom(m, d.getOpposite(), head.get(m));
			v = (v + ACCEL * (hn - hm)) * (1 - FRICTION);
			// one-way nodes (pumps are check valves)
			if (v > 0 && !(n.canOutput(d) && m.canInput(d.getOpposite()))) v = 0;
			if (v < 0 && !(m.canOutput(d.getOpposite()) && n.canInput(d))) v = 0;
			double max = Math.min(n.getMaxFlow(), m.getMaxFlow());
			v = Math.max(-max, Math.min(max, v));
			keys.add(new long[]{k});
			pairs.add(new FluidNode[]{n, m});
			flows.add(new double[]{v});
		}

		// Apply all flows simultaneously (fluid is incompressible: a full pipe may pass on what it receives in the
		// same tick). Scale down only where a node would run dry or overflow.
		for (int it = 0; it < 6; it++) {
			Map<FluidNode, Double> out = new HashMap<>();
			Map<FluidNode, Double> in = new HashMap<>();
			for (int i = 0; i < pairs.size(); i++) {
				double v = flows.get(i)[0];
				FluidNode s = v >= 0 ? pairs.get(i)[0] : pairs.get(i)[1];
				FluidNode t = v >= 0 ? pairs.get(i)[1] : pairs.get(i)[0];
				out.merge(s, Math.abs(v), Double::sum);
				in.merge(t, Math.abs(v), Double::sum);
			}
			boolean changed = false;
			for (int i = 0; i < pairs.size(); i++) {
				double v = flows.get(i)[0];
				if (Math.abs(v) < 1e-12) continue;
				FluidNode s = v >= 0 ? pairs.get(i)[0] : pairs.get(i)[1];
				FluidNode t = v >= 0 ? pairs.get(i)[1] : pairs.get(i)[0];
				double so = scaleOut(s, out.getOrDefault(s, 0.0), in.getOrDefault(s, 0.0));
				double si = scaleIn(t, out.getOrDefault(t, 0.0), in.getOrDefault(t, 0.0));
				double f = Math.min(so, si);
				if (f < 1 - 1e-9) {
					flows.get(i)[0] = v * f;
					changed = true;
				}
			}
			if (!changed) break;
		}

		Map<FluidNode, Double> throughput = new HashMap<>();
		Map<Long, Double> newVelocity = new HashMap<>();
		for (int i = 0; i < pairs.size(); i++) {
			double v = flows.get(i)[0];
			FluidNode src = v >= 0 ? pairs.get(i)[0] : pairs.get(i)[1];
			FluidNode dst = v >= 0 ? pairs.get(i)[1] : pairs.get(i)[0];
			double q = Math.min(Math.abs(v), Math.min(src.getAmount(), Math.max(0, dst.getCapacity() - dst.getAmount())));
			if (q > 1e-9) {
				if (dst.getAmount() <= 1e-6) dst.setFluid(src.getFluid());
				dst.setAmount(dst.getAmount() + q);
				src.setAmount(src.getAmount() - q);
				if (src.getAmount() < 1e-6) {
					src.setAmount(0);
					src.setFluid(SfFluid.NONE);
				}
				throughput.merge(src, q, Double::sum);
				throughput.merge(dst, q, Double::sum);
			}
			double actual = Math.signum(v) * q;
			if (Math.abs(actual) > 1e-7) newVelocity.put(keys.get(i)[0], actual);
		}
		velocity = newVelocity;

		// ---- 3. feed consumer buildings (coal generators, ...)
		for (FluidNode n : nodes) {
			if (n.getAmount() <= 1e-6) continue;
			outer:
			for (BlockPos cell : n.getCells()) {
				for (Direction d : Direction.values()) {
					if (!n.connectsAt(cell, d) || !n.canOutput(d)) continue;
					BlockPos p = cell.offset(d);
					if (active.containsKey(p)) continue;
					FluidEndpoint ep = FluidEndpoints.find(world, p);
					if (ep == null) continue;
					double accepted = ep.offerFluid(n.getFluid(), Math.min(n.getAmount(), n.getMaxFlow()));
					if (accepted > 0) {
						n.setAmount(n.getAmount() - accepted);
						if (n.getAmount() < 1e-6) {
							n.setAmount(0);
							n.setFluid(SfFluid.NONE);
						}
						throughput.merge(n, accepted * 2, Double::sum);
					}
					if (n.getAmount() <= 1e-6) break outer;
				}
			}
		}

		for (FluidNode n : nodes) n.onFlowSolved(throughput.getOrDefault(n, 0.0) / 2.0);
		active.clear();
	}

	private static boolean compatible(FluidNode a, FluidNode b) {
		return a.getAmount() <= 1e-6 || b.getAmount() <= 1e-6 || a.getFluid() == b.getFluid();
	}

	/** How much of a node's planned outflow can happen: it may pass on what it holds plus what flows in. */
	private static double scaleOut(FluidNode n, double out, double in) {
		double allowed = n.getAmount() + in;
		return out <= allowed + 1e-12 ? 1.0 : allowed / out;
	}

	/** How much of a node's planned inflow fits: free space plus what flows out in the same tick. */
	private static double scaleIn(FluidNode n, double out, double in) {
		double allowed = n.getCapacity() - n.getAmount() + out;
		return in <= allowed + 1e-12 ? 1.0 : Math.max(0, allowed) / in;
	}

	/** Pumps suck on their inlet side: the head they present there is low while they run. */
	private static double headSeenFrom(FluidNode node, Direction towardsOther, double normalHead) {
		if (node instanceof PumpHead p) {
			Double inlet = p.inletHead(towardsOther);
			if (inlet != null) return inlet;
		}
		return normalHead;
	}

	/** Implemented by pumps to present a suction head on their inlet. */
	public interface PumpHead {
		/** Head presented on {@code side}, or null to use the normal head. */
		Double inletHead(Direction side);
	}
}
