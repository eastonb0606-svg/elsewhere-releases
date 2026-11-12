package dev.elsewhere.kingdom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loaded unit types ({@code data/<ns>/kingdom_unit/*.json}, reloaded with {@code /reload}).
 * Built-in ids: {@code elsewhere:man_at_arms, knight, lancer, mounted_swordsman, archer, crossbowman, sergeant}; 0.2.0 adds
 * {@code captain, standard_bearer, herald, surgeon, squire, pikeman}.
 */
public final class UnitTypes {
	private UnitTypes() {}

	public static final Identifier MAN_AT_ARMS = KingdomContent.id("man_at_arms");
	public static final Identifier KNIGHT = KingdomContent.id("knight");
	public static final Identifier LANCER = KingdomContent.id("lancer");
	public static final Identifier MOUNTED_SWORDSMAN = KingdomContent.id("mounted_swordsman");
	public static final Identifier ARCHER = KingdomContent.id("archer");
	public static final Identifier CROSSBOWMAN = KingdomContent.id("crossbowman");
	public static final Identifier SERGEANT = KingdomContent.id("sergeant");
	/** 0.2.0 units. */
	public static final Identifier CAPTAIN = KingdomContent.id("captain");
	public static final Identifier STANDARD_BEARER = KingdomContent.id("standard_bearer");
	public static final Identifier HERALD = KingdomContent.id("herald");
	public static final Identifier SURGEON = KingdomContent.id("surgeon");
	public static final Identifier SQUIRE = KingdomContent.id("squire");
	public static final Identifier PIKEMAN = KingdomContent.id("pikeman");

	private static Map<Identifier, UnitType> units = Map.of();
	private static final Map<Identifier, UnitType> CODE = new LinkedHashMap<>();

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(KingdomContent.id("kingdom_units"),
			new SimpleJsonResourceReloadListener<UnitType>(UnitType.CODEC, FileToIdConverter.json("kingdom_unit")) {
				@Override
				protected void apply(final Map<Identifier, UnitType> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					Map<Identifier, UnitType> m = new LinkedHashMap<>(CODE);
					m.putAll(prepared);
					units = Collections.unmodifiableMap(m);
					Factions.clearUnitCaches();
					KingdomContent.LOG.info("Loaded {} kingdom unit types", units.size());
				}
			});
	}

	/** The unit type, or {@link UnitType#FALLBACK} if unknown. */
	public static UnitType get(Identifier id) {
		UnitType u = units.get(id);
		return u != null ? u : UnitType.FALLBACK;
	}

	public static boolean exists(Identifier id) {
		return units.containsKey(id);
	}

	public static Map<Identifier, UnitType> all() {
		return units;
	}

	/** For code-defined units (e.g. a boss's guard) that need no JSON file. Kept across /reload; a JSON file with the same id wins. */
	public static void putCodeDefined(Identifier id, UnitType type) {
		CODE.put(id, type);
		Map<Identifier, UnitType> m = new LinkedHashMap<>(CODE);
		m.putAll(units);
		if (!units.containsKey(id)) m.put(id, type);
		m.put(id, type);
		units = Collections.unmodifiableMap(m);
		Factions.clearUnitCaches();
	}
}
