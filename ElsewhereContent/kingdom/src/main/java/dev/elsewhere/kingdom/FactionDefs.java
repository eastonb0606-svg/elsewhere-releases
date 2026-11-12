package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/**
 * Loaded faction definitions ({@code data/<ns>/kingdom_faction/*.json}, reloaded with {@code /reload}) plus factions
 * defined in code ({@link #putCodeDefined}, kept across reloads; a JSON file with the same id wins). Every change is
 * pushed into {@link Factions}, which owns the live state (stances, player hostility, unit resolution).
 */
public final class FactionDefs {
	private FactionDefs() {}

	private static Map<Identifier, FactionDef> loaded = Map.of();
	private static final Map<Identifier, FactionDef> CODE = new LinkedHashMap<>();
	private static Map<Identifier, FactionDef> merged = Map.of();

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(KingdomContent.id("kingdom_factions"),
			new SimpleJsonResourceReloadListener<FactionDef>(FactionDef.CODEC, FileToIdConverter.json("kingdom_faction")) {
				@Override
				protected void apply(final Map<Identifier, FactionDef> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					loaded = Map.copyOf(prepared);
					rebuild();
					KingdomContent.LOG.info("Loaded {} kingdom factions: {}", merged.size(), merged.keySet());
				}
			});
	}

	private static void rebuild() {
		Map<Identifier, FactionDef> m = new LinkedHashMap<>(CODE);
		m.putAll(loaded);
		merged = Collections.unmodifiableMap(m);
		Factions.syncDefs(merged);
	}

	/** The definition, or null (the faction exists only as a code registration such as {@code elsewhere:rebels}). */
	public static @Nullable FactionDef get(Identifier id) {
		return merged.get(id);
	}

	public static Map<Identifier, FactionDef> all() {
		return merged;
	}

	/** Factions with a castle, Aldermark first, the rest by id (the court places one realm per faction in this order). */
	public static List<Identifier> realmFactions() {
		List<Identifier> out = new ArrayList<>();
		if (merged.get(Factions.ALDERMARK) == null || merged.get(Factions.ALDERMARK).realm()) out.add(Factions.ALDERMARK);
		merged.entrySet().stream().filter(e -> !e.getKey().equals(Factions.ALDERMARK) && e.getValue().realm())
			.map(Map.Entry::getKey).sorted().forEach(out::add);
		return out;
	}

	/** Registers a faction from code (a dev/test faction, or a mod that does not ship JSON). Applies at once; survives /reload. */
	public static void putCodeDefined(Identifier id, FactionDef def) {
		CODE.put(id, def);
		rebuild();
	}

	public static void removeCodeDefined(Identifier id) {
		if (CODE.remove(id) != null) {
			rebuild();
			Factions.unregister(id);
		}
	}

	public static Optional<FactionDef.RulerDef> ruler(Identifier faction, String slot) {
		FactionDef d = merged.get(faction);
		return d == null ? Optional.empty() : Optional.ofNullable(d.rulers().get(slot));
	}
}
