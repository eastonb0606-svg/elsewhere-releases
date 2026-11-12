package dev.elsewhere.kingdom_villages;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Border hints: a player who comes within 24 blocks of a kingdom's marker (Aldermark's tower-flag, Hrafnholt's raven post,
 * Azmar's obelisk, Mirewick's lantern post) at an outpost or a claimed village is "seeing the border": the lore module
 * ({@code LoreApi.hintSeen}) awards the matching Whispers advancement. By reflection (the lore module may be absent).
 */
public final class Hints {
	private Hints() {}

	public static final int RANGE = 24;
	/** Self-test sink: called instead of (and before) the lore module. */
	public interface Sink {
		void seen(ServerPlayer player, Identifier faction, String kind);
	}

	public static @Nullable Sink testSink;
	private static boolean missing;
	private static Method hintSeen;

	public static String kindOf(VillageRecord r) {
		return switch (FactionVillages.of(r.owner).shape()) {
			case HRAFNHOLT -> "raven_post";
			case AZMAR -> "obelisk";
			case MIREWICK -> "lantern_post";
			default -> "tower_flag";
		};
	}

	/** Once per second-ish: players near a hint of an occupied village or an outpost. */
	static void tick(ServerLevel level, VillageLedger ledger) {
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) continue;
			BlockPos pp = p.blockPosition();
			for (VillageRecord r : ledger.villages.values()) near(p, pp, r);
			for (VillageRecord r : ledger.sites.values()) if (r.kind == VillageRecord.Kind.OUTPOST) near(p, pp, r);
		}
	}

	private static void near(ServerPlayer p, BlockPos pp, VillageRecord r) {
		BlockPos h = r.hint;
		if (h == null || !(r.isOccupiedLike() || r.kind == VillageRecord.Kind.OUTPOST && r.state == VillageRecord.State.OCCUPIED)) return;
		if (h.distSqr(pp) > (double) RANGE * RANGE) return;
		seen(p, VillageManager.factionOf(r), kindOf(r));
	}

	private static final java.util.Set<String> SEEN = new java.util.HashSet<>();

	public static void seen(ServerPlayer p, Identifier faction, String kind) {
		if (testSink != null) testSink.seen(p, faction, kind);
		if (missing || !SEEN.add(p.getUUID() + "/" + faction + "/" + kind)) return; // once per session: the advancement is awarded once anyway
		try {
			if (hintSeen == null) hintSeen = Class.forName("dev.elsewhere.lore.LoreApi").getMethod("hintSeen", ServerPlayer.class, Identifier.class, String.class);
			hintSeen.invoke(null, p, faction, kind);
		} catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
			missing = true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			VillagesContent.LOG.warn("LoreApi.hintSeen failed: {}", e.toString());
			missing = true;
		}
	}
}
