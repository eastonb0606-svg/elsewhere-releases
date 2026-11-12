package dev.elsewhere.stub.petalwind_glasswaste;

import dev.elsewhere.boss.kit.TelegraphNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ground-decal telegraphs (BOSSES.md: yellow = dodge, red = big hit, cyan = interactable/weak point).
 * 3.0 (POLISH.md P2): each call sends ONE {@link TelegraphNet} shape (owned by the boss, so {@link #clear} removes it on
 * the client too) that the client draws and animates for its duration; hints only for players with foresight gear.
 * The old dust decal (re-drawn every 3 ticks) is kept only for players whose client lacks the renderer, or for everyone
 * with the {@code boss_telegraphs} kill switch off.
 */
public final class Telegraph {
	private Telegraph() {}

	public static final int YELLOW = 0xFFE23A, RED = 0xFF2A2A, CYAN = 0x3AF0FF, WHITE = 0xFFFFFF, SCORCH = 0x3A2410;

	private enum Shape { CIRCLE, RING, LINE }

	/** {@code to}: players who get the dust version (empty when every viewer draws the shape client-side). */
	private record Decal(Entity owner, ServerLevel level, Shape shape, Vec3 a, Vec3 b, double radius, double width, int color, long end, List<ServerPlayer> to) {}

	private static final List<Decal> ACTIVE = new ArrayList<>();
	private static long now;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			now = server.getTickCount();
			if (ACTIVE.isEmpty()) return;
			if (now % 3 != 0) {
				ACTIVE.removeIf(d -> d.end <= now || d.owner.isRemoved());
				return;
			}
			for (Iterator<Decal> it = ACTIVE.iterator(); it.hasNext(); ) {
				Decal d = it.next();
				if (d.end <= now || d.owner.isRemoved()) {
					it.remove();
					continue;
				}
				if (!d.to.isEmpty()) draw(d);
			}
		});
	}

	public static void circle(Entity owner, ServerLevel level, Vec3 center, double radius, int color, int ticks) {
		add(owner, level, Shape.CIRCLE, TelegraphNet.DISC, center, center, radius, 0, color, ticks, (float) radius, 0, 0, 0);
	}

	public static void ring(Entity owner, ServerLevel level, Vec3 center, double radius, int color, int ticks) {
		add(owner, level, Shape.RING, TelegraphNet.RING, center, center, radius, 0, color, ticks, (float) radius, 0.45F, 0, 0);
	}

	public static void line(Entity owner, ServerLevel level, Vec3 from, Vec3 to, double width, int color, int ticks) {
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() < 1.0E-6) return;
		add(owner, level, Shape.LINE, TelegraphNet.LINE, from, to, 0, width, color, ticks, (float) d.x, (float) d.y, (float) d.z, (float) width);
	}

	private static void add(Entity owner, ServerLevel level, Shape shape, byte kind, Vec3 a, Vec3 b, double radius, double width, int color, int ticks,
			float pa, float pb, float pc, float pd) {
		List<ServerPlayer> dust = new ArrayList<>(0);
		TelegraphNet.show(level, owner, kind, 0, a.x, a.y + 0.06, a.z, pa, pb, pc, pd, color, ticks, dust::add);
		ACTIVE.add(new Decal(owner, level, shape, a, b, radius, width, color, now + ticks, dust));
	}

	public static void clear(Entity owner) {
		boolean had = ACTIVE.removeIf(d -> d.owner == owner);
		if (had && owner.level() instanceof ServerLevel sl) TelegraphNet.clear(sl, owner);
	}

	public static int count(Entity owner) {
		return (int) ACTIVE.stream().filter(d -> d.owner == owner).count();
	}

	private static void draw(Decal d) {
		DustParticleOptions dust = new DustParticleOptions(d.color, 1.6F);
		switch (d.shape) {
			case CIRCLE -> {
				for (double r = 0.5; r <= d.radius; r += 1.0) ringAt(d, dust, r, (int) Math.max(8, r * 6));
			}
			case RING -> ringAt(d, dust, d.radius, (int) Math.max(12, d.radius * 7));
			case LINE -> {
				Vec3 dir = d.b.subtract(d.a);
				double len = dir.length();
				Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
				for (double t = 0; t <= len; t += 0.7) {
					Vec3 p = d.a.add(dir.scale(t / len));
					for (double w = -d.width / 2; w <= d.width / 2 + 1e-3; w += Math.max(0.6, d.width / 3)) {
						Vec3 q = p.add(side.scale(w));
						for (ServerPlayer to : d.to) d.level.sendParticles(to, dust, true, true, q.x, q.y + 0.1, q.z, 1, 0.05, 0, 0.05, 0);
					}
				}
			}
		}
	}

	private static void ringAt(Decal d, DustParticleOptions dust, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			for (ServerPlayer to : d.to) d.level.sendParticles(to, dust, true, true, d.a.x + Math.cos(a) * r, d.a.y + 0.1, d.a.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}
}
