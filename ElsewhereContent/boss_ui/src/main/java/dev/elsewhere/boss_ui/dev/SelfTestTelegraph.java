package dev.elsewhere.boss_ui.dev;

import dev.elsewhere.boss.kit.TelegraphNet;
import dev.elsewhere.boss_ui.telegraph.TelegraphMesh;
import dev.elsewhere.boss_ui.telegraph.TelegraphStore;
import io.netty.buffer.Unpooled;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * P2 telegraph checks (POLISH.md P2): payload codec and size, the 8-slot client store, the quad mesh and its LOD and
 * budget, the foresight gate, the repeat-call dedup and the kill switch. Pure logic plus one FakePlayer; no client needed.
 */
public final class SelfTestTelegraph {
	private SelfTestTelegraph() {}

	private static final float[] BOUNDS = new float[6];
	private static int verts;

	public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
		check.accept(TelegraphNet.registered(), "telegraph: payload type registered at common init (BossUiContent)");
		check.accept(TelegraphNet.enabled, "telegraph: kill switch boss_telegraphs defaults on");

		// codec round trip and size budget (POLISH.md rule 9: below 64 bytes)
		TelegraphNet.Shape s = new TelegraphNet.Shape(TelegraphNet.CONE, (byte) TelegraphNet.OPEN, 1 << 20, Integer.MAX_VALUE - 1, -29999984.5, 319.0, 29999984.5,
			-135F, 45F, 12F, 0.5F, 0xFFFF2A1A, 100000);
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		TelegraphNet.Shape.CODEC.encode(buf, s);
		int size = buf.readableBytes();
		TelegraphNet.Shape back = TelegraphNet.Shape.CODEC.decode(buf);
		check.accept(back.equals(s), "telegraph: payload survives encode/decode (" + back + ")");
		check.accept(size < 64, "telegraph: worst-case payload is " + size + " bytes (< 64)");
		TelegraphNet.Shape none = new TelegraphNet.Shape(TelegraphNet.CLEAR, (byte) 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		FriendlyByteBuf b2 = new FriendlyByteBuf(Unpooled.buffer());
		TelegraphNet.Shape.CODEC.encode(b2, none);
		check.accept(TelegraphNet.Shape.CODEC.decode(b2).owner() == -1, "telegraph: 'no owner' (-1) survives the wire");

		// client store: 8 slots, refresh keeps the fill-up start, evicts the one ending soonest, CLEAR by owner
		TelegraphStore st = new TelegraphStore();
		for (int i = 0; i < 10; i++) st.accept(shape(TelegraphNet.DISC, i + 1, i < 5 ? 7 : 9, 0, 3, 20 + i), 100);
		check.accept(st.count() == TelegraphStore.CAP && st.evicted == 2, "telegraph: client keeps at most 8 shapes (" + st.count() + ", evicted " + st.evicted + ")");
		boolean oldestGone = true;
		for (TelegraphStore.Slot sl : st.slots) oldestGone &= sl.id != 1 && sl.id != 2;
		check.accept(oldestGone, "telegraph: a full store drops the shapes that end soonest");
		st.accept(shape(TelegraphNet.DISC, 5, 7, 0, 3, 30), 110);
		TelegraphStore.Slot five = find(st, 5);
		check.accept(five != null && five.start == 100 && five.end == 140, "telegraph: a refresh (same id) extends the shape and keeps its start");
		st.accept(shape(TelegraphNet.CLEAR, 0, 7, 0, 0, 0), 111);
		boolean only9 = true;
		for (TelegraphStore.Slot sl : st.slots) if (sl.used) only9 &= sl.owner == 9;
		check.accept(only9 && st.count() == 5, "telegraph: CLEAR removes only that boss's shapes (" + st.count() + " left)");
		st.accept(shape(TelegraphNet.CLEAR, 0, -1, 0, 0, 0), 112);
		check.accept(st.count() == 0, "telegraph: CLEAR for everyone empties the store");
		st.accept(shape(TelegraphNet.RING, 50, -1, 0, 4, 10), 200);
		st.expire(210);
		check.accept(st.count() == 0, "telegraph: shapes expire at their end tick with no packet");

		// mesh: every kind draws flat quads inside its bounds; LOD cuts detail; nothing past 64 blocks
		byte[] kinds = {TelegraphNet.RING, TelegraphNet.DISC, TelegraphNet.CONE, TelegraphNet.LINE, TelegraphNet.WAVE};
		boolean allDraw = true, inBounds = true, lodLess = true, quadsOk = true;
		int worst = 0;
		StringBuilder why = new StringBuilder();
		for (byte k : kinds) {
			TelegraphStore one = new TelegraphStore();
			one.accept(k == TelegraphNet.LINE ? new TelegraphNet.Shape(k, (byte) 0, 1, -1, 10, 64, 10, 12, 0, 9, 1.5F, 0xFFFFD21F, 40)
				: k == TelegraphNet.CONE ? new TelegraphNet.Shape(k, (byte) 0, 1, -1, 10, 64, 10, -60, 30, 12, 0, 0xFFFFD21F, 40)
				: k == TelegraphNet.WAVE ? new TelegraphNet.Shape(k, (byte) TelegraphNet.ATTACK, 1, -1, 10, 64, 10, 1, 0.6F, 20, 0.8F, 0xFFFFD21F, 40)
				: new TelegraphNet.Shape(k, (byte) 0, 1, -1, 10, 64, 10, 9, 0.4F, 0, 0, 0xFFFFD21F, 40), 0);
			TelegraphStore.Slot sl = one.slots[0];
			int q0 = mesh(sl, 20, 0), q2 = mesh(sl, 20, 2), q3 = TelegraphMesh.emit(sl, 0, 0, 0, 20, -1, true, (x, y, z, c) -> {});
			boolean b = BOUNDS[0] >= sl.minX - 0.6 && BOUNDS[3] <= sl.maxX + 0.6 && BOUNDS[2] >= sl.minZ - 0.6 && BOUNDS[5] <= sl.maxZ + 0.6;
			allDraw &= q0 > 0 && q2 > 0 && q3 == 0;
			inBounds &= b;
			lodLess &= q2 < q0;
			quadsOk &= verts == 0 || verts % 4 == 0;
			worst = Math.max(worst, q0);
			why.append(' ').append(k).append(':').append(q0).append('/').append(q2);
		}
		check.accept(allDraw, "telegraph: ring, disc, cone, line and wave all draw, and nothing draws past 64 blocks (quads full/min:" + why + ")");
		check.accept(inBounds, "telegraph: every vertex lies inside the shape's culling bounds");
		check.accept(quadsOk, "telegraph: geometry is whole quads (4 vertices each)");
		check.accept(lodLess, "telegraph: 48-64 block LOD draws fewer quads than close up");
		check.accept(TelegraphStore.CAP * worst <= 4000, "telegraph: worst frame (8 shapes, full detail) stays within 4000 quads (" + TelegraphStore.CAP * worst + ")");
		check.accept(TelegraphMesh.lod(23 * 23) == 0 && TelegraphMesh.lod(40 * 40) == 1 && TelegraphMesh.lod(60 * 60) == 2 && TelegraphMesh.lod(65 * 65) == -1,
			"telegraph: LOD bands are 24 / 48 / 64 blocks");

		// fill-up from game time; open (repeat-call) shapes charge without a known end; the wave ends at its max radius
		TelegraphStore timed = new TelegraphStore();
		timed.accept(shape(TelegraphNet.DISC, 1, -1, 0, 4, 40), 0);
		float half = TelegraphMesh.progress(timed.slots[0], 20), full = TelegraphMesh.progress(timed.slots[0], 40);
		check.accept(Math.abs(half - 0.5F) < 0.01F && full == 1F, "telegraph: fill-up is half full halfway through the wind-up (" + half + ")");
		TelegraphStore open = new TelegraphStore();
		open.accept(new TelegraphNet.Shape(TelegraphNet.RING, (byte) TelegraphNet.OPEN, 1, -1, 0, 64, 0, 4, 0.4F, 0, 0, 0xFFFFFFFF, 12), 0);
		float c1 = TelegraphMesh.progress(open.slots[0], 5), c2 = TelegraphMesh.progress(open.slots[0], 30);
		check.accept(c1 > 0F && c2 > c1 && c2 < 1F, "telegraph: repeat-call shapes charge towards full (" + c1 + " -> " + c2 + ")");
		TelegraphStore wave = new TelegraphStore();
		wave.accept(new TelegraphNet.Shape(TelegraphNet.WAVE, (byte) TelegraphNet.ATTACK, 1, -1, 0, 64, 0, 1, 0.6F, 10, 0.8F, 0xFFFFFFFF, 40), 0);
		check.accept(mesh(wave.slots[0], 10, 0) > 0 && mesh(wave.slots[0], 16, 0) == 0, "telegraph: the shockwave grows 0.6/tick and is gone past its max radius");

		// foresight gate (DECISIONS-2.0): hints only with foresight gear; the attack itself (wave) for everyone in range
		ServerLevel level = server.overworld();
		FakePlayer fp = FakePlayer.get(level);
		fp.setPos(0, 100, 0);
		check.accept(!TelegraphNet.wants(fp, 3, 100, 3, false), "telegraph: no foresight gear, no hint");
		check.accept(TelegraphNet.wants(fp, 3, 100, 3, true), "telegraph: an attack shape (shockwave) shows without foresight gear");
		check.accept(!TelegraphNet.wants(fp, 70, 100, 0, true), "telegraph: nothing is sent past 64 blocks");
		check.accept(!TelegraphNet.canReceive(fp), "telegraph: a client without the renderer gets the dust fallback, not the payload");

		// repeat-call tracking: a wind-up calling every 3 ticks is one shape; a moving boss's ring follows it (same shape);
		// a second ring of that size far away and a ring of another size are separate; old shapes run out on their own
		TelegraphNet.clear(level, null);
		int sent0 = TelegraphNet.sent;
		for (int t = 0; t <= 15; t += 3) TelegraphNet.pulseAt(level, 1000 + t, TelegraphNet.RING, 0, 5, 100, 5, 4, 0.4F, 0, 0, 0xFF2A1A, null);
		check.accept(TelegraphNet.liveCount(level) == 1, "telegraph: six repeat calls of one ring track one shape (" + TelegraphNet.liveCount(level) + ")");
		TelegraphNet.pulseAt(level, 1018, TelegraphNet.RING, 0, 6.2, 100, 5, 4, 0.4F, 0, 0, 0xFF2A1A, null);
		check.accept(TelegraphNet.liveCount(level) == 1, "telegraph: a ring that moves with its boss stays one shape (no trail)");
		TelegraphNet.pulseAt(level, 1018, TelegraphNet.RING, 0, 20, 100, 20, 4, 0.4F, 0, 0, 0xFF2A1A, null);
		TelegraphNet.pulseAt(level, 1018, TelegraphNet.RING, 0, 6.2, 100, 5, 2, 0.4F, 0, 0, 0xFF2A1A, null);
		check.accept(TelegraphNet.liveCount(level) == 3, "telegraph: a far ring and a smaller ring are their own shapes (" + TelegraphNet.liveCount(level) + ")");
		TelegraphNet.pulseAt(level, 1060, TelegraphNet.CONE, 0, 5, 100, 5, 10, 22, 12, 0, 0xFFD21F, null);
		check.accept(TelegraphNet.liveCount(level) == 1, "telegraph: shapes no longer called run out (" + TelegraphNet.liveCount(level) + " left)");
		check.accept(TelegraphNet.sent == sent0, "telegraph: nobody in range, no packets");
		TelegraphNet.clear(level, null);
		check.accept(TelegraphNet.liveCount(level) == 0, "telegraph: clear forgets the level's shapes");

		// kill switch: off means every viewer gets the old dust, never the payload
		boolean was = TelegraphNet.enabled;
		TelegraphNet.enabled = false;
		check.accept(!TelegraphNet.canReceive(fp), "telegraph: kill switch off sends no payloads (dust only)");
		TelegraphNet.enabled = was;
		check.accept(TelegraphNet.yawOf(0, 1) == 0F && Math.abs(TelegraphNet.yawOf(-1, 0) - 90F) < 1e-3, "telegraph: direction to Minecraft yaw (south 0, west 90)");
	}

	private static TelegraphNet.Shape shape(byte kind, int id, int owner, double x, float a, int ticks) {
		return new TelegraphNet.Shape(kind, (byte) 0, id, owner, x, 64, 0, a, 0.4F, 0, 0, 0xFFFFD21F, ticks);
	}

	private static TelegraphStore.Slot find(TelegraphStore st, int id) {
		for (TelegraphStore.Slot sl : st.slots) if (sl.used && sl.id == id) return sl;
		return null;
	}

	/** Emits with the camera at the origin, recording the vertex bounds (world space) and count. */
	private static int mesh(TelegraphStore.Slot sl, double now, int lod) {
		BOUNDS[0] = BOUNDS[1] = BOUNDS[2] = Float.MAX_VALUE;
		BOUNDS[3] = BOUNDS[4] = BOUNDS[5] = -Float.MAX_VALUE;
		verts = 0;
		return TelegraphMesh.emit(sl, 0, 0, 0, now, lod, true, (x, y, z, c) -> {
			verts++;
			BOUNDS[0] = Math.min(BOUNDS[0], x);
			BOUNDS[1] = Math.min(BOUNDS[1], y);
			BOUNDS[2] = Math.min(BOUNDS[2], z);
			BOUNDS[3] = Math.max(BOUNDS[3], x);
			BOUNDS[4] = Math.max(BOUNDS[4], y);
			BOUNDS[5] = Math.max(BOUNDS[5], z);
		});
	}
}
