package dev.elsewhere.lore;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/** Public hooks for the other kingdom lanes (K-B court, K-C villages). Everything is server-side. */
public final class LoreApi {
	private LoreApi() {}

	/**
	 * Extra ways to find the castle for the Survey Map, tried before the structure tag
	 * {@code #elsewhere:on_royal_survey_maps}. A locator returns the castle position nearest the given position, or null.
	 */
	public static final List<BiFunction<ServerLevel, BlockPos, @Nullable BlockPos>> CASTLE_LOCATORS = new CopyOnWriteArrayList<>();

	/** Extra "this village counts as liberated" tests (K-C: pass a village state check). Combined with {@link #markLiberated}. */
	public static final List<BiPredicate<ServerLevel, BlockPos>> LIBERATED_PROBES = new CopyOnWriteArrayList<>();

	/** Faction-aware castle locators (rivals): the castle of {@code faction} nearest {@code from}, or null. */
	@FunctionalInterface
	public interface FactionLocator {
		@Nullable BlockPos find(ServerLevel level, BlockPos from, Identifier faction);
	}

	public static final List<FactionLocator> FACTION_LOCATORS = new CopyOnWriteArrayList<>();

	public static void addFactionLocator(FactionLocator l) {
		FACTION_LOCATORS.add(l);
	}

	/** Same as {@link #markLiberated(ServerLevel, BlockPos)} but records which kingdom held the village (its cartographer sells that map). */
	public static void markLiberated(ServerLevel level, BlockPos villageCentre, Identifier faction) {
		LoreState.get(level).markLiberated(villageCentre, faction.toString());
	}

	/**
	 * Called by the villages module (by reflection) when a player comes near a border marker: awards the shared "Marked Ground"
	 * step and the faction's own border step. {@code kind}: tower_flag, raven_post, obelisk, lantern_post, other.
	 */
	public static void hintSeen(ServerPlayer player, Identifier faction, String kind) {
		LoreAdvancements.hintSeen(player, faction, kind);
	}

	/** Kingdom that held the liberated village at {@code pos}: recorded faction, else the villages ledger (owner / prevOwner), else Aldermark. */
	public static Identifier liberatedFaction(ServerLevel level, BlockPos pos) {
		String rec = LoreState.get(level).factionNear(pos, 96);
		if (!rec.isEmpty()) return LoreFactions.fromString(rec).id;
		if (!ledgerMissing) {
			try {
				Class<?> c = Class.forName("dev.elsewhere.kingdom_villages.VillageLedger");
				Object ledger = c.getMethod("get", ServerLevel.class).invoke(null, level);
				Object map = c.getField("villages").get(ledger);
				double best = 96.0 * 96.0;
				String found = "";
				for (Object rec2 : ((java.util.Map<?, ?>) map).values()) {
					BlockPos ctr = (BlockPos) rec2.getClass().getField("center").get(rec2);
					double dx = ctr.getX() - pos.getX(), dz = ctr.getZ() - pos.getZ(), d = dx * dx + dz * dz;
					if (d > best) continue;
					String o = field(rec2, "owner");
					if (o.isEmpty()) o = field(rec2, "prevOwner");
					best = d;
					found = o;
				}
				if (!found.isEmpty()) return LoreFactions.fromString(found).id;
			} catch (ClassNotFoundException e) {
				// not present
			} catch (ReflectiveOperationException | RuntimeException e) {
				// older ledger without the field: Aldermark
			}
		}
		return LoreFactions.ALDERMARK;
	}

	private static String field(Object o, String name) {
		try {
			Object v = o.getClass().getField(name).get(o);
			return v == null ? "" : v.toString();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "";
		}
	}

	public static void addCastleLocator(BiFunction<ServerLevel, BlockPos, @Nullable BlockPos> locator) {
		CASTLE_LOCATORS.add(locator);
	}

	public static void addLiberatedProbe(BiPredicate<ServerLevel, BlockPos> probe) {
		LIBERATED_PROBES.add(probe);
	}

	/** K-C: call when a village changes from occupied to free (a player broke the siege). Its cartographer then sells the Survey Map. */
	public static void markLiberated(ServerLevel level, BlockPos villageCentre) {
		LoreState.get(level).markLiberated(villageCentre);
	}

	public static boolean isLiberated(ServerLevel level, BlockPos pos) {
		if (LoreState.get(level).isLiberated(pos, 96)) return true;
		for (BiPredicate<ServerLevel, BlockPos> p : LIBERATED_PROBES) if (p.test(level, pos)) return true;
		return villagesLedgerLiberated(level, pos);
	}

	private static boolean ledgerMissing;

	/** Reads K-C's ledger by reflection (module may be absent): a VILLAGE record in state LIBERATED or RESTORED within 96 blocks. */
	private static boolean villagesLedgerLiberated(ServerLevel level, BlockPos pos) {
		if (ledgerMissing) return false;
		try {
			Class<?> c = Class.forName("dev.elsewhere.kingdom_villages.VillageLedger");
			Object ledger = c.getMethod("get", ServerLevel.class).invoke(null, level);
			Object map = c.getField("villages").get(ledger);
			for (Object rec : ((java.util.Map<?, ?>) map).values()) {
				String st = String.valueOf(rec.getClass().getField("state").get(rec));
				if (!st.equals("LIBERATED") && !st.equals("RESTORED")) continue;
				BlockPos ctr = (BlockPos) rec.getClass().getField("center").get(rec);
				long dx = ctr.getX() - pos.getX(), dz = ctr.getZ() - pos.getZ();
				if (dx * dx + dz * dz <= 96L * 96L) return true;
			}
		} catch (ClassNotFoundException e) {
			ledgerMissing = true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			ledgerMissing = true;
			LoreContent.LOG.warn("K-C village ledger probe disabled: {}", e.toString());
		}
		return false;
	}

	/** K-B: places the Aldermark Reeve (clan=false) or the Clan Quartermaster (clan=true), a fixed-trade villager. Null if it cannot spawn. */
	public static @Nullable Villager spawnQuartermaster(ServerLevel level, BlockPos pos, boolean clan) {
		return Quartermasters.spawn(level, pos, clan);
	}

	/** The nearest castle position to {@code from} via the locators and the structure tag, or null. */
	public static @Nullable BlockPos findCastle(ServerLevel level, BlockPos from) {
		return LoreMaps.findCastle(level, from);
	}
}
