package dev.elsewhere.boss.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * P2 shared boss telegraphs (POLISH.md P2). A telegraph is ONE small payload per shape (ring, disc, cone, line, or a
 * travelling wave) sent once when it starts; the client (boss_ui {@code TelegraphClient}) draws it as one translucent
 * quad strip on the ground, with a pulse and a fill-up animated from game time. Nothing is sent per tick.
 * <ul>
 * <li><b>Foresight rule</b> (DECISIONS-2.0 {@code bal-boss-ground-markings}): a telegraph is an attack hint, so it goes
 * only to players who hold or wear {@code #elsewhere:foresight_gear} ({@link ForesightGear#sees}). Only {@link #ATTACK}
 * shapes (the attack itself, e.g. Verdigrim's travelling shockwave) go to everyone in range.</li>
 * <li>Only players within {@link #SEND_RANGE} blocks of the shape receive it.</li>
 * <li>A player whose client can't take the payload (no boss_ui), or every player while the kill switch is off, gets the
 * old dust particles instead, through the caller's {@code dust} callback (still foresight-gated).</li>
 * <li>{@link #pulse} serves the old "call it every few ticks" API: the first call sends the shape; later calls for the same
 * shape re-send it (same id, so the client moves or extends it instead of restarting it) only when it moved or is about to
 * run out, about every 7 ticks.</li>
 * </ul>
 * Kill switch {@code boss_telegraphs}: {@code config/elsewhere-overhaul3.json} {@code "boss_telegraphs": false} or
 * {@code -Delsewhere.overhaul3.boss_telegraphs=false} brings back the dust telegraphs everywhere.
 * Identical copies live in every boss module and the core ({@code modules.json} "shared": the core's copy is kept).
 */
public final class TelegraphNet {
	private TelegraphNet() {}

	public static final byte RING = 0, DISC = 1, CONE = 2, LINE = 3, WAVE = 4, CLEAR = 5;
	/** The server doesn't know when the wind-up ends (repeat-call API): the client shows a charging fill, not a timed one. */
	public static final int OPEN = 1;
	/** The shape is the attack itself, not a hint: every player in range sees it, foresight gear or not. */
	public static final int ATTACK = 2;

	public static final String KEY = "boss_telegraphs";
	public static final double SEND_RANGE = 64.0;
	/** Lifetime of a repeat-call shape after its last refresh, and the refresh margin (ticks). */
	public static final int HOLD = 12, REFRESH = 5;

	/** Kill switch, read once at class load. Tests may flip it. */
	public static boolean enabled = readSwitch();

	/** Counters for the self-tests. */
	public static int sent, refreshed, skipped, dust, clears;

	private static boolean registered;
	private static int nextId = 1;
	/** A repeat-call shape follows its previous call if it is this close (blocks). */
	public static final double MATCH = 3.0;
	private static final Map<ServerLevel, List<Live>> LIVE = new WeakHashMap<>();
	private static final Map<ServerLevel, long[]> SHOWN_UNTIL = new WeakHashMap<>();

	private static final class Live {
		final int id;
		final byte kind;
		final int flags, rgb;
		double x, y, z;
		float a, b, c, d;
		long until, lastCall = Long.MIN_VALUE;

		Live(int id, byte kind, int flags, int rgb) {
			this.id = id;
			this.kind = kind;
			this.flags = flags;
			this.rgb = rgb;
		}

		/** Same size (the parts of the geometry that don't move with the boss). */
		boolean sameSize(float na, float nb, float nc, float nd) {
			return switch (kind) {
				case RING -> Math.abs(a - na) < 0.05F && Math.abs(b - nb) < 0.05F;
				case DISC -> Math.abs(a - na) < 0.05F;
				case CONE -> Math.abs(b - nb) < 0.5F && Math.abs(c - nc) < 0.05F;
				case LINE -> Math.abs(d - nd) < 0.05F;
				default -> a == na && b == nb && c == nc && d == nd;
			};
		}

		/** A cone that turned more than 2 degrees, or a line whose far end moved more than half a block. */
		boolean turned(float na, float nb, float nc) {
			if (kind == CONE) return Math.abs(((na - a) % 360 + 540) % 360 - 180) > 2F;
			if (kind == LINE) return (na - a) * (na - a) + (nb - b) * (nb - b) + (nc - c) * (nc - c) > 0.25F;
			return false;
		}
	}

	/**
	 * One telegraph shape. kind: {@link #RING} a = radius, b = band width; {@link #DISC} a = radius; {@link #CONE} a = yaw
	 * (degrees, Minecraft convention), b = half-angle (degrees), c = length; {@link #LINE} a, b, c = end minus start, d =
	 * width; {@link #WAVE} a = start radius, b = growth per tick, c = max radius, d = band width; {@link #CLEAR} removes
	 * the owner's shapes (owner -1 = all). Encoded size is at most 57 bytes.
	 */
	public record Shape(byte kind, byte flags, int id, int owner, double x, double y, double z, float a, float b, float c, float d, int argb, int ticks) implements CustomPacketPayload {
		public static final Type<Shape> TYPE = new Type<>(Identifier.fromNamespaceAndPath("elsewhere", "telegraph"));
		public static final StreamCodec<FriendlyByteBuf, Shape> CODEC = CustomPacketPayload.codec(Shape::write, Shape::new);

		private Shape(FriendlyByteBuf buf) {
			this(buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readVarInt());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeByte(kind);
			buf.writeByte(flags);
			buf.writeVarInt(id);
			buf.writeVarInt(Math.max(0, owner + 1));
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			buf.writeFloat(a);
			buf.writeFloat(b);
			buf.writeFloat(c);
			buf.writeFloat(d);
			buf.writeInt(argb);
			buf.writeVarInt(Math.max(0, ticks));
		}

		@Override
		public Type<Shape> type() {
			return TYPE;
		}
	}

	/** Registers the payload type (common init, both sides). Safe to call from several modules. */
	public static synchronized void init() {
		if (registered) return;
		registered = true;
		try {
			PayloadTypeRegistry.clientboundPlay().register(Shape.TYPE, Shape.CODEC);
		} catch (IllegalArgumentException e) {
			// another copy of the kit already registered it
		}
	}

	public static boolean registered() {
		return registered;
	}

	/** True if this player's client draws telegraphs (has boss_ui) and the kill switch is on. */
	public static boolean canReceive(ServerPlayer p) {
		if (!enabled || !registered) return false;
		try {
			return ServerPlayNetworking.canSend(p, Shape.TYPE);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** True if this player should see a telegraph at (x, y, z): in range, and wearing foresight gear unless it is an attack. */
	public static boolean wants(ServerPlayer p, double x, double y, double z, boolean attack) {
		if (p.distanceToSqr(x, y, z) > SEND_RANGE * SEND_RANGE) return false;
		return attack || ForesightGear.sees(p);
	}

	/** A shape with a known duration: sent once. Returns how many players got the payload. */
	public static int show(ServerLevel level, @Nullable Entity owner, byte kind, int flags, double x, double y, double z,
			float a, float b, float c, float d, int rgb, int ticks, @Nullable Consumer<ServerPlayer> dustFallback) {
		Shape s = new Shape(kind, (byte) flags, newId(), owner == null ? -1 : owner.getId(), x, y, z, a, b, c, d, 0xFF000000 | rgb, Math.max(1, ticks));
		boolean attack = (flags & ATTACK) != 0;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (!wants(p, x, y, z, attack)) continue;
			if (canReceive(p)) {
				ServerPlayNetworking.send(p, s);
				n++;
			} else if (dustFallback != null) {
				dustFallback.accept(p);
				dust++;
			}
		}
		if (n > 0) {
			sent += n;
			markShown(level, level.getGameTime() + s.ticks);
		}
		return n;
	}

	/**
	 * A shape from the repeat-call API (the boss calls it every few ticks during a wind-up). The first call sends it; the same
	 * geometry within its lifetime only refreshes it when it is about to run out. Players without the client renderer get
	 * the dust on every call, as before.
	 */
	public static void pulse(ServerLevel level, byte kind, int flags, double x, double y, double z, float a, float b, float c, float d,
			int rgb, @Nullable Consumer<ServerPlayer> dustFallback) {
		pulseAt(level, level.getGameTime(), kind, flags, x, y, z, a, b, c, d, rgb, dustFallback);
	}

	/**
	 * {@link #pulse} at a given game time (tests). A call matches the nearest live shape of the same kind, colour and size
	 * within {@link #MATCH} blocks that wasn't already called this tick: a boss-anchored shape that moves (or a cone that
	 * turns, a line whose target walks) keeps its id and is re-sent only when it moved, so the client moves it instead of
	 * leaving a trail. Several marks of one size in one tick stay separate shapes.
	 */
	public static void pulseAt(ServerLevel level, long now, byte kind, int flags, double x, double y, double z, float a, float b, float c, float d,
			int rgb, @Nullable Consumer<ServerPlayer> dustFallback) {
		List<Live> list = LIVE.computeIfAbsent(level, k -> new ArrayList<>());
		list.removeIf(l -> l.until < now);
		Live live = null;
		double best = MATCH * MATCH;
		for (Live l : list) {
			if (l.kind != kind || l.flags != flags || l.rgb != rgb || l.lastCall == now || !l.sameSize(a, b, c, d)) continue;
			double dsq = (l.x - x) * (l.x - x) + (l.y - y) * (l.y - y) + (l.z - z) * (l.z - z);
			if (dsq <= best) {
				best = dsq;
				live = l;
			}
		}
		boolean fresh = live == null;
		boolean send = fresh || best > 0.0625 || live.turned(a, b, c) || live.until - now <= REFRESH;
		if (fresh) {
			live = new Live(newId(), kind, flags, rgb);
			list.add(live);
		}
		live.lastCall = now;
		live.x = x;
		live.y = y;
		live.z = z;
		live.a = a;
		live.b = b;
		live.c = c;
		live.d = d;
		if (send) live.until = now + HOLD;
		Shape s = send ? new Shape(kind, (byte) (flags | OPEN), live.id, -1, x, y, z, a, b, c, d, 0xFF000000 | rgb, HOLD) : null;
		boolean attack = (flags & ATTACK) != 0;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (!wants(p, x, y, z, attack)) continue;
			if (canReceive(p)) {
				if (s != null) {
					ServerPlayNetworking.send(p, s);
					n++;
				} else skipped++;
			} else if (dustFallback != null) {
				dustFallback.accept(p);
				dust++;
			}
		}
		if (n > 0) {
			if (fresh) sent += n;
			else refreshed += n;
			markShown(level, now + HOLD);
		}
	}

	/** Calls {@code dust} for every player who should see a shape at (x, y, z) but can't draw it (per-tick dust fallback). */
	public static void fallback(ServerLevel level, double x, double y, double z, boolean attack, Consumer<ServerPlayer> dustFallback) {
		for (ServerPlayer p : level.players()) {
			if (wants(p, x, y, z, attack) && !canReceive(p)) {
				dustFallback.accept(p);
				dust++;
			}
		}
	}

	/** Removes the owner's shapes (owner null = every shape) on clients in this level; sends nothing if none can be showing. */
	public static void clear(ServerLevel level, @Nullable Entity owner) {
		if (owner == null) LIVE.remove(level);
		long[] until = SHOWN_UNTIL.get(level);
		if (until == null || level.getGameTime() > until[0]) return;
		Shape s = new Shape(CLEAR, (byte) 0, 0, owner == null ? -1 : owner.getId(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
		for (ServerPlayer p : level.players()) {
			if (canReceive(p)) {
				ServerPlayNetworking.send(p, s);
				clears++;
			}
		}
		if (owner == null) SHOWN_UNTIL.remove(level);
	}

	/** Forgets the server-side repeat-call state (client shapes run out by themselves within {@link #HOLD} ticks). */
	public static void reset() {
		LIVE.clear();
		SHOWN_UNTIL.clear();
	}

	/** Minecraft yaw (degrees) of a horizontal direction. */
	public static float yawOf(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Test hook: how many repeat-call shapes this level tracks. */
	public static int liveCount(ServerLevel level) {
		List<Live> l = LIVE.get(level);
		return l == null ? 0 : l.size();
	}

	private static void markShown(ServerLevel level, long until) {
		long[] u = SHOWN_UNTIL.computeIfAbsent(level, k -> new long[1]);
		u[0] = Math.max(u[0], until);
	}

	private static int newId() {
		int id = nextId;
		nextId = nextId >= (1 << 20) ? 1 : nextId + 1;
		return id;
	}

	private static boolean readSwitch() {
		String prop = System.getProperty("elsewhere.overhaul3." + KEY);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (Files.isRegularFile(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has(KEY)) return o.get(KEY).getAsBoolean();
			}
		} catch (Exception e) {
			// unreadable config keeps the default
		}
		return true;
	}
}
