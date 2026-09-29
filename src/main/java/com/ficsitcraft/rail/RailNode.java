package com.ficsitcraft.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * A junction point of the rail graph. {@code dir} is the node's axis: tracks that leave in +dir hang on the FRONT side,
 * tracks that leave in -dir on the BACK side. A train that arrives over one side continues over the other one; if that
 * side holds several tracks the node is a switch and {@link #sel} picks the branch.
 */
public final class RailNode {
	public static final int FRONT = 0, BACK = 1;
	public static final long NO_OWNER = Long.MIN_VALUE;
	public static final int MAX_PER_SIDE = 3;

	public final long id;
	public final V3 pos;
	public final V3 dir;
	public final List<Long> front = new ArrayList<>();
	public final List<Long> back = new ArrayList<>();
	/** Selected branch per side (switch position). */
	public int selFront, selBack;
	/** Signal governing trains that ARRIVE over this side: 0 none, 1 block, 2 path. */
	public int signalFront, signalBack;
	/** Block position (asLong) of the station / platform that created this node, or NO_OWNER. */
	public long owner = NO_OWNER;

	public RailNode(long id, V3 pos, V3 dir) {
		this.id = id;
		this.pos = pos;
		this.dir = dir.normalize();
	}

	public List<Long> side(int s) {
		return s == FRONT ? front : back;
	}

	public int sel(int s) {
		List<Long> l = side(s);
		int v = s == FRONT ? selFront : selBack;
		return l.isEmpty() ? 0 : Math.min(Math.max(v, 0), l.size() - 1);
	}

	public void setSel(int s, int v) {
		if (s == FRONT) selFront = v;
		else selBack = v;
	}

	public int signal(int s) {
		return s == FRONT ? signalFront : signalBack;
	}

	public void setSignal(int s, int type) {
		if (s == FRONT) signalFront = type;
		else signalBack = type;
	}

	public boolean hasAnySignal() {
		return signalFront != 0 || signalBack != 0;
	}

	public boolean isSwitch(int s) {
		return side(s).size() > 1;
	}

	/** Direction in which a track attached on side s leaves this node. */
	public V3 leaveDir(int s) {
		return s == FRONT ? dir : dir.neg();
	}

	public int trackCount() {
		return front.size() + back.size();
	}
}
