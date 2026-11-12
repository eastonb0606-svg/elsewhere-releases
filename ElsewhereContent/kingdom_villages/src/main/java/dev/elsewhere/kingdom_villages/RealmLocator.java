package dev.elsewhere.kingdom_villages;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where is the Aldermark realm (castle)? The court lane (K-B) owns the realm. It can either put its castle structure in the
 * structure tag {@code #elsewhere:aldermark_realm} (this module ships it empty) or call {@link #register}. With neither, no
 * village is ever occupied or besieged (everything stays FREE), which is the safe default.
 */
public final class RealmLocator {
	private RealmLocator() {}

	public interface Locator {
		@Nullable BlockPos nearest(ServerLevel level, BlockPos from);
	}

	public static final TagKey<Structure> REALM_TAG = TagKey.create(Registries.STRUCTURE, VillagesContent.id("aldermark_realm"));
	public static final int MAX_DIST = 2600;
	private static final List<Locator> LOCATORS = new ArrayList<>();
	private static final Map<Long, Optional<BlockPos>> CACHE = new HashMap<>();
	/** Dev/self-test override (Aldermark's castle). */
	public static @Nullable BlockPos testRealm;
	/** Dev/self-test overrides for the other realms (faction id to castle). */
	public static final Map<Identifier, BlockPos> testRealms = new HashMap<>();
	private static final Map<Identifier, List<Locator>> FACTION_LOCATORS = new HashMap<>();

	/** A realm: the faction and its castle. */
	public record Realm(Identifier faction, BlockPos castle) {}

	/** Registers another way to find {@code faction}'s castle (the court lane may call this; it is also found through CourtApi). */
	public static void register(Identifier faction, Locator l) {
		FACTION_LOCATORS.computeIfAbsent(faction, k -> new ArrayList<>()).add(l);
		CACHE.clear();
	}

	/** Castle of one faction near {@code from}, or null (fallen kingdoms have no occupying realm). */
	public static @Nullable BlockPos findFor(ServerLevel level, BlockPos from, Identifier faction) {
		if (faction.equals(Factions.ALDERMARK)) return find(level, from);
		BlockPos t = testRealms.get(faction);
		if (t != null) return t;
		for (Locator l : FACTION_LOCATORS.getOrDefault(faction, List.of())) {
			BlockPos p = l.nearest(level, from);
			if (p != null) return p;
		}
		return courtRealm(level.getServer(), faction);
	}

	private static boolean courtMissing;
	private static java.lang.reflect.Method courtRealm;

	/** {@code CourtApi.realmCenter(server, faction)} by reflection; null when the court module is absent or has no realm. */
	static @Nullable BlockPos courtRealm(net.minecraft.server.MinecraftServer server, Identifier faction) {
		if (courtMissing) return null;
		try {
			if (courtRealm == null) courtRealm = Class.forName("dev.elsewhere.kingdom_court.CourtApi").getMethod("realmCenter", net.minecraft.server.MinecraftServer.class, Identifier.class);
			return (BlockPos) courtRealm.invoke(null, server, faction);
		} catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
			courtMissing = true;
			return null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * The nearest realm (any faction, fallen ones skipped) within {@link #MAX_DIST} of {@code from}. With only Aldermark's castle
	 * known this is exactly the 2.0 behaviour.
	 */
	public static @Nullable Realm findRealm(ServerLevel level, BlockPos from) {
		Realm best = null;
		double bd = Double.MAX_VALUE;
		List<Identifier> factions = new ArrayList<>(FactionDefs.realmFactions());
		if (!factions.contains(Factions.ALDERMARK)) factions.add(0, Factions.ALDERMARK);
		for (Identifier f : factions) {
			if (!f.equals(Factions.ALDERMARK) && KingdomApi.isFactionFallen(level.getServer(), f)) continue;
			BlockPos c = findFor(level, from, f);
			if (c == null) continue;
			double d = horizontal(c, from);
			if (d <= MAX_DIST && d < bd) {
				bd = d;
				best = new Realm(f, c);
			}
		}
		return best;
	}

	/** Every known castle (for border tests): faction to position. */
	public static Map<Identifier, BlockPos> castles(ServerLevel level) {
		Map<Identifier, BlockPos> out = new HashMap<>();
		List<Identifier> factions = new ArrayList<>(FactionDefs.realmFactions());
		if (!factions.contains(Factions.ALDERMARK)) factions.add(0, Factions.ALDERMARK);
		for (Identifier f : factions) {
			BlockPos c = f.equals(Factions.ALDERMARK) ? testRealm : testRealms.get(f);
			if (c == null) c = courtRealm(level.getServer(), f);
			if (c == null && !f.equals(Factions.ALDERMARK)) c = findFor(level, BlockPos.ZERO, f);
			if (c != null) out.put(f, c);
		}
		return out;
	}

	public static void register(Locator l) {
		LOCATORS.add(l);
		CACHE.clear();
	}

	public static void reset() {
		CACHE.clear();
		testRealms.clear();
	}

	public static @Nullable BlockPos find(ServerLevel level, BlockPos from) {
		if (testRealm != null) return testRealm;
		for (Locator l : LOCATORS) {
			BlockPos p = l.nearest(level, from);
			if (p != null && horizontal(p, from) <= MAX_DIST) return p;
		}
		long key = BlockPos.asLong(from.getX() >> 8, 0, from.getZ() >> 8);
		Optional<BlockPos> cached = CACHE.get(key);
		if (cached != null) return cached.orElse(null);
		BlockPos found = null;
		Optional<HolderSet.Named<Structure>> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(REALM_TAG);
		if (set.isPresent() && set.get().size() > 0) {
			Pair<BlockPos, Holder<Structure>> p = level.getChunkSource().getGenerator().findNearestMapStructure(level, set.get(), from, 12, false);
			if (p != null && horizontal(p.getFirst(), from) <= MAX_DIST) found = p.getFirst();
		}
		CACHE.put(key, Optional.ofNullable(found));
		return found;
	}

	public static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
