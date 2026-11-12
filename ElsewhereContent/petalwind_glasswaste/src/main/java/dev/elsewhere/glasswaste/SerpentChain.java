package dev.elsewhere.glasswaste;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Segment rig shared by the Sandswimmer (client renderer) and Ankhara (server-side child hitboxes): the head leaves a
 * trail of points and each segment sits at a fixed arc length behind the head along that trail, so the body follows
 * the exact path the head swam.
 */
public final class SerpentChain {
	private final Deque<Vec3> trail = new ArrayDeque<>();
	private final int segments;
	private final double spacing;

	public SerpentChain(int segments, double spacing) {
		this.segments = segments;
		this.spacing = spacing;
	}

	public int segments() {
		return segments;
	}

	public double spacing() {
		return spacing;
	}

	/** Records the head position (call every tick or frame). */
	public void update(Vec3 head, Vec3 initialBackward) {
		if (trail.isEmpty()) {
			Vec3 back = initialBackward.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : initialBackward.normalize();
			for (int i = segments + 1; i >= 1; i--) trail.addLast(head.add(back.scale(spacing * i)));
			trail.addLast(head);
			return;
		}
		Vec3 last = trail.peekLast();
		if (last.distanceToSqr(head) > 0.01) trail.addLast(head);
		else {
			trail.pollLast();
			trail.addLast(head);
		}
		double needed = spacing * (segments + 2);
		double length = 0;
		Iterator<Vec3> it = trail.descendingIterator();
		Vec3 prev = it.next();
		int keep = 1;
		while (it.hasNext()) {
			Vec3 p = it.next();
			length += p.distanceTo(prev);
			prev = p;
			keep++;
			if (length > needed) break;
		}
		while (trail.size() > keep + 1) trail.pollFirst();
	}

	/** Positions of segments 1..n behind the head, spaced along the trail. */
	public List<Vec3> positions() {
		List<Vec3> pts = new ArrayList<>(trail);
		List<Vec3> out = new ArrayList<>(segments);
		if (pts.isEmpty()) return out;
		int idx = pts.size() - 1;
		Vec3 cur = pts.get(idx);
		for (int s = 1; s <= segments; s++) {
			double remain = spacing;
			while (true) {
				if (idx == 0) {
					out.add(cur);
					break;
				}
				Vec3 next = pts.get(idx - 1);
				double d = cur.distanceTo(next);
				if (d >= remain && d > 1.0E-6) {
					cur = cur.add(next.subtract(cur).scale(remain / d));
					out.add(cur);
					break;
				}
				remain -= d;
				cur = next;
				idx--;
			}
		}
		return out;
	}

	public void reset() {
		trail.clear();
	}
}
