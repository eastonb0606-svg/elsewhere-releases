package dev.elsewhere.kingdom_court.world;

import com.mojang.datafixers.util.Pair;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.world.CourtWorld.RealmStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 (s-locate): {@code /locate}, explorer maps and every other structure search ({@code ChunkGenerator.findNearestMapStructure}, hooked by
 * {@code mixin/LocateCastleMixin}) for the realm structures: every structure of type {@link RealmStructure} (castle_aldermark, castle_azmar,
 * castle_hrafnholt, castle_mirewick, waterfront_hrafnholt). Vanilla only scans random-spread and concentric-ring placements, so these were never found,
 * and the rival realms stand up to 12,000 blocks out anyway. Each has exactly one site per world, saved in {@link CourtState}, so the answer is read from
 * there: exact, instant (a few map lookups, no chunk scan) and at any distance, whatever the search radius. A search set without a realm structure is
 * left to vanilla untouched; a mixed set (a tag holding realms and other structures) answers the nearer of the realm site and vanilla's own result for
 * the rest.
 * <p>
 * The hook answers only where the realm really generates, as vanilla does: the overworld (CourtState is overworld data), a world with structures on,
 * and a generator that places the structure with its court placement (so a superflat world, or a data pack that removes the structure set, finds
 * nothing). A castle is found at its saved site (the courtyard, y = the castle floor). Hrafnholt's waterfront is found at the jetty where it meets the
 * shore (y = sea level); a world whose Hrafnholt is not by the water has no waterfront, and the search then says so instead of pointing at nothing.
 * <p>
 * Kill switch {@code realm_locate} ({@code -Delsewhere.overhaul3.realm_locate=false}, the court's Overhaul3 convention): back to the 2.x answer,
 * Aldermark's castle only.
 */
public final class RealmLocate {
	private RealmLocate() {}

	/** Kill switch {@code realm_locate}, default on. Not final only so the self-test can check the off path. */
	public static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.realm_locate", "true"));
	/**
	 * Self-test only, never set in play: the court self-test world is flat with structures off, where no realm generates, so with this on the hook
	 * reads the court placement from the structure-set registry instead of this world's generator and ignores the structures setting.
	 */
	public static boolean testIgnoreGenerator;
	/** Searches this hook has answered (self-test probe: a search without a realm structure must leave it unchanged). */
	public static int answered;

	/** True when the hook answers a search for {@code wanted}: the set holds a realm structure (kill switch off: only the 2.x Aldermark-only case). */
	public static boolean handles(ServerLevel level, HolderSet<Structure> wanted) {
		if (!enabled) return level.dimension() == Level.OVERWORLD && wanted.size() == 1 && wanted.get(0).is(CourtWorld.STRUCTURE_KEY);
		for (Holder<Structure> h : wanted) if (realm(h) != null) return true;
		return false;
	}

	/**
	 * The search result for {@code wanted} (only called when {@link #handles} is true): the nearest realm site among its realm structures, or vanilla's own
	 * result for its other members when that is nearer; null when nothing is found. Distances are horizontal.
	 */
	public static @Nullable Pair<BlockPos, Holder<Structure>> find(ChunkGenerator gen, ServerLevel level, HolderSet<Structure> wanted, BlockPos from, int radius, boolean skipKnown) {
		answered++;
		if (!enabled) {
			BlockPos s = CourtWorld.site(level.getSeed());
			return s == null ? null : Pair.of(s, wanted.get(0));
		}
		List<Holder<Structure>> others = new ArrayList<>();
		Pair<BlockPos, Holder<Structure>> best = null;
		double bestD = Double.MAX_VALUE;
		for (Holder<Structure> h : wanted) {
			RealmStructure rs = realm(h);
			if (rs == null) {
				others.add(h);
				continue;
			}
			BlockPos p = located(level, h, rs);
			if (p != null && dist2(from, p) < bestD) {
				bestD = dist2(from, p);
				best = Pair.of(p, h);
			}
		}
		if (!others.isEmpty()) {
			// the rest of the set keeps the vanilla search (this call re-enters the hook with no realm member, so it is not intercepted)
			Pair<BlockPos, Holder<Structure>> v = gen.findNearestMapStructure(level, HolderSet.direct(others), from, radius, skipKnown);
			if (v != null && dist2(from, v.getFirst()) < bestD) best = v;
		}
		return best;
	}

	/** Where a search in {@code level} finds realm structure {@code h}, or null when it does not generate there or this world has none (not a realm structure: null). */
	public static @Nullable BlockPos located(ServerLevel level, Holder<Structure> h) {
		RealmStructure rs = realm(h);
		return rs == null ? null : located(level, h, rs);
	}

	static @Nullable BlockPos located(ServerLevel level, Holder<Structure> h, RealmStructure rs) {
		if (level.dimension() != Level.OVERWORLD || !placedHere(level, h, rs)) return null;
		return site(level.getServer(), rs.faction, rs.waterfront, level.getChunkSource().getGenerator().getSeaLevel());
	}

	/**
	 * The located position of {@code faction}'s castle ({@code waterfront} false: its saved site) or waterfront (the jetty at the shoreline, at {@code seaLevel}),
	 * read from the faction's saved court; null when this world has no realm site for it, or no waterfront.
	 */
	public static @Nullable BlockPos site(MinecraftServer server, Identifier faction, boolean waterfront, int seaLevel) {
		CourtState st = CourtState.peek(server, faction);
		if (st == null || st.site == null) return null;
		if (!waterfront) return st.site;
		int[] w = water(st);
		return w == null ? null : shore(st.site, w[0], w[1], seaLevel);
	}

	/**
	 * {dir, shore} of a waterfront realm from its saved court (dir 1 south, 3 north; shore = blocks from the castle centre), exactly what
	 * {@code CourtLife.syncRealms} hands to {@link CourtWorld#setWater} for generation; null when the realm is not by the water.
	 */
	public static int @Nullable [] water(CourtState st) {
		return st.site != null && st.waterDist > 0 && !Double.isNaN(st.waterBearing) ? new int[] {st.waterBearing > 0 ? 1 : 3, st.waterDist} : null;
	}

	/** The jetty deck where the waterfront meets the shore ({@link Waterfront#build}: deck along v = 0 at y = sea level, shore at u = {@code shore}). */
	public static BlockPos shore(BlockPos site, int dir, int shore, int seaLevel) {
		int[] xz = Waterfront.world(site, dir, shore, 0);
		return new BlockPos(xz[0], seaLevel, xz[1]);
	}

	/** True when {@code level} generates {@code h} with its court placement (see the class comment); the self-test switch reads the registry instead. */
	static boolean placedHere(ServerLevel level, Holder<Structure> h, RealmStructure rs) {
		if (testIgnoreGenerator) return rs.faction.equals(registryFaction(level, h));
		if (!level.getServer().getWorldGenSettings().options().generateStructures()) return false;
		for (StructurePlacement p : level.getChunkSource().getGeneratorState().getPlacementsForStructure(h))
			if (p instanceof CourtWorld.Placement cp && cp.faction().equals(rs.faction)) return true;
		return false;
	}

	/** The faction of the court placement that places {@code h} in the structure-set registry (whatever the generator), or null. */
	public static @Nullable Identifier registryFaction(ServerLevel level, Holder<Structure> h) {
		for (StructureSet set : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().map(Holder::value).toList()) {
			if (!(set.placement() instanceof CourtWorld.Placement cp)) continue;
			for (StructureSet.StructureSelectionEntry e : set.structures()) if (e.structure().value() == h.value()) return cp.faction();
		}
		return null;
	}

	/** The faction a realm structure belongs to, or null when {@code h} is not a realm structure. */
	public static @Nullable Identifier factionOf(Holder<Structure> h) {
		RealmStructure rs = realm(h);
		return rs == null ? null : rs.faction;
	}

	/** True for a realm's waterfront structure (waterfront_hrafnholt). */
	public static boolean isWaterfront(Holder<Structure> h) {
		RealmStructure rs = realm(h);
		return rs != null && rs.waterfront;
	}

	/**
	 * For suites that locate every structure (ElsewhereQA's release locate step, by reflection: {@code absentReason(MinecraftServer, Identifier)}): null when
	 * {@code structure} is not a realm structure or this world has it; otherwise why this world has none. A realm structure exists at most once per world,
	 * and Hrafnholt's waterfront only when its castle stands by the water, so "not found" is then the correct answer, not a failure.
	 */
	public static @Nullable String absentReason(MinecraftServer server, Identifier structure) {
		ServerLevel ow = server.overworld();
		Holder<Structure> h = ow.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure).orElse(null);
		RealmStructure rs = h == null ? null : realm(h);
		if (rs == null) return null;
		CourtState st = CourtState.peek(server, rs.faction);
		if (st == null || st.site == null) return rs.faction + " has no realm site in this world";
		if (rs.waterfront && water(st) == null) return rs.faction + "'s realm is not by the water in this world, so it has no waterfront";
		if (!placedHere(ow, h, rs)) return "this world does not generate it (structures off, or its structure set is removed)";
		return null;
	}

	static @Nullable RealmStructure realm(Holder<Structure> h) {
		return h.isBound() && h.value() instanceof RealmStructure rs ? rs : null;
	}

	static double dist2(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
