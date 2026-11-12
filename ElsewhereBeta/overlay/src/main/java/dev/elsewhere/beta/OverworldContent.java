package dev.elsewhere.beta;

import dev.elsewhere.ElsewhereConfig;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Beta 2 vanilla-friendly switch {@code overworldContent} in {@code config/elsewhere.json} (default true). False: the
 * Overworld boss sites (Croaking Shrine, Sun Chamber, Glacier Shelf, Wreck Ring, and so their site-only mobs such as the
 * Tidewrack Drowned) are not placed, and no accidental trip happens ({@link GlobalTrips#allowed}). Wayfinders, summon
 * items and everything inside the Elsewhere dimensions still work.
 */
public final class OverworldContent {
	private OverworldContent() {}

	public static final Set<String> SITE_SETS = Set.of("elsewhere:croaking_shrine", "elsewhere:sun_chamber", "elsewhere:glacier_shelf", "elsewhere:wreck_ring");

	public static boolean enabled() {
		return ElsewhereConfig.get().overworldContent;
	}

	public static void init() {
		if (!enabled()) BetaSmokeTest.LOG.info("[elsewhere] overworldContent=false: Overworld boss sites and accidental trips are off");
	}

	public static boolean isSiteSet(Holder<StructureSet> h) {
		return h.unwrapKey().map(k -> SITE_SETS.contains(k.identifier().toString())).orElse(false);
	}

	public static List<Holder<StructureSet>> filter(List<Holder<StructureSet>> sets) {
		if (enabled()) return sets;
		return sets.stream().filter(h -> !isSiteSet(h)).toList();
	}
}
