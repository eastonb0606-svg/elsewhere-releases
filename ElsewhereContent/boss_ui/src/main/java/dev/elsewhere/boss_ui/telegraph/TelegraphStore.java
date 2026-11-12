package dev.elsewhere.boss_ui.telegraph;

import dev.elsewhere.boss.kit.TelegraphNet;

/**
 * The client's active telegraph shapes: a fixed pool of {@link #CAP} slots (POLISH.md budget rule 6: at most 8 world-space
 * telegraphs per client), allocated once. A payload with a known id refreshes its slot (end moves, start stays, so the
 * fill-up doesn't restart); a new id takes a free slot or the one that ends soonest. Pure logic, no client classes, so the
 * dedicated-server self-test can check it.
 */
public final class TelegraphStore {
	public static final int CAP = 8;

	public static final class Slot {
		public boolean used;
		public byte kind;
		public int flags, id, owner, argb;
		public double x, y, z;
		public float a, b, c, d;
		public long start, end;
		/** World-space bounds, set on arrival, for distance and frustum culling. */
		public double minX, minY, minZ, maxX, maxY, maxZ;

		void set(TelegraphNet.Shape s, long now) {
			used = true;
			kind = s.kind();
			flags = s.flags();
			id = s.id();
			owner = s.owner();
			argb = s.argb();
			x = s.x();
			y = s.y();
			z = s.z();
			a = s.a();
			b = s.b();
			c = s.c();
			d = s.d();
			start = now;
			end = now + Math.max(1, s.ticks());
			bounds();
		}

		/** Squared distance from a point to this shape's bounds (0 inside). */
		public double distSqr(double px, double py, double pz) {
			double dx = Math.max(0, Math.max(minX - px, px - maxX));
			double dy = Math.max(0, Math.max(minY - py, py - maxY));
			double dz = Math.max(0, Math.max(minZ - pz, pz - maxZ));
			return dx * dx + dy * dy + dz * dz;
		}

		private void bounds() {
			double r;
			switch (kind) {
				case TelegraphNet.LINE -> {
					double w = Math.max(0.5, d);
					minX = Math.min(x, x + a) - w;
					maxX = Math.max(x, x + a) + w;
					minY = Math.min(y, y + b) - 0.5;
					maxY = Math.max(y, y + b) + 0.5;
					minZ = Math.min(z, z + c) - w;
					maxZ = Math.max(z, z + c) + w;
					return;
				}
				case TelegraphNet.CONE -> r = c;
				case TelegraphNet.WAVE -> r = c + d;
				case TelegraphNet.RING -> r = a + b;
				default -> r = a;
			}
			minX = x - r;
			maxX = x + r;
			minY = y - 0.5;
			maxY = y + 0.5;
			minZ = z - r;
			maxZ = z + r;
		}
	}

	public final Slot[] slots = new Slot[CAP];
	/** Shapes pushed out because all slots were busy (test counter). */
	public int evicted;

	public TelegraphStore() {
		for (int i = 0; i < CAP; i++) slots[i] = new Slot();
	}

	/** Applies one payload at client game time {@code now}. */
	public void accept(TelegraphNet.Shape s, long now) {
		if (s.kind() == TelegraphNet.CLEAR) {
			for (Slot sl : slots) if (sl.used && (s.owner() < 0 || sl.owner == s.owner())) sl.used = false;
			return;
		}
		if (s.kind() < 0 || s.kind() > TelegraphNet.WAVE) return;
		Slot target = null;
		for (Slot sl : slots) {
			if (sl.used && sl.id == s.id()) {
				// refresh: keep the start so the fill-up carries on
				long start = sl.start;
				sl.set(s, now);
				sl.start = start;
				return;
			}
		}
		expire(now);
		for (Slot sl : slots) {
			if (!sl.used) {
				target = sl;
				break;
			}
		}
		if (target == null) {
			target = slots[0];
			for (Slot sl : slots) if (sl.end < target.end) target = sl;
			evicted++;
		}
		target.set(s, now);
	}

	public void expire(long now) {
		for (Slot sl : slots) if (sl.used && now >= sl.end) sl.used = false;
	}

	public void clear() {
		for (Slot sl : slots) sl.used = false;
	}

	public int count() {
		int n = 0;
		for (Slot sl : slots) if (sl.used) n++;
		return n;
	}
}
