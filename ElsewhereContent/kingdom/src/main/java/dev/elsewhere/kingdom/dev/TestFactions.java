package dev.elsewhere.kingdom.dev;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * The self-test faction {@code elsewhere:test_marches}: Aldermark recoloured (green skins, green tabard), a few changed
 * loadouts, its own five Lords and King with vanilla drops. It lives in the dev package and is registered only by the
 * self-tests, never in production; it proves that a rival kingdom is just data plus skins.
 */
public final class TestFactions {
	private TestFactions() {}

	public static final Identifier MARCHES = KingdomContent.id("test_marches");
	public static final int TABARD = 0x2E9E4C;

	static Identifier skin(String name) {
		return KingdomContent.id("textures/entity/kingdom/testfaction/" + name + ".png");
	}

	static FactionDef.RosterEntry entry(String skin, String main, String off, Integer dye, Float health) {
		return new FactionDef.RosterEntry(Optional.empty(), Optional.of(skin(skin)), Optional.empty(), Optional.ofNullable(dye),
			Optional.ofNullable(main).map(Identifier::withDefaultNamespace), Optional.ofNullable(off).map(Identifier::withDefaultNamespace), Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(health));
	}

	static FactionDef.RulerDef ruler(String name, String title, double health, double cap, String drop) {
		return new FactionDef.RulerDef(name, Optional.empty(), title, health, cap > 0 ? Optional.of(cap) : Optional.empty(),
			List.of(new FactionDef.Drop(Identifier.withDefaultNamespace(drop), 1, 1, 1.0F), new FactionDef.Drop(Identifier.withDefaultNamespace("gold_ingot"), 4, 8, 1.0F)));
	}

	public static FactionDef def() {
		Map<Identifier, FactionDef.RosterEntry> roster = new LinkedHashMap<>();
		roster.put(UnitTypes.MAN_AT_ARMS, entry("soldier_a", "stone_sword", null, null, null));
		roster.put(UnitTypes.KNIGHT, entry("soldier_b", "iron_axe", "shield", null, 1.25F));
		roster.put(UnitTypes.ARCHER, entry("archer", null, null, 0x2E9E4C, null));
		roster.put(UnitTypes.SERGEANT, entry("officer", null, null, null, null));
		roster.put(UnitTypes.CAPTAIN, entry("captain", null, null, null, null));
		Map<String, FactionDef.RulerDef> rulers = new LinkedHashMap<>();
		rulers.put("king", ruler("Warden-King Test", "the Green Crown", 280, 340, "diamond"));
		rulers.put("marshal", ruler("Marshal Test", "Lord Marshal", 150, 0, "emerald"));
		rulers.put("castellan", ruler("Castellan Test", "Lord Castellan", 170, 0, "iron_ingot"));
		rulers.put("master_at_arms", ruler("Master Test", "Master-at-Arms", 140, 0, "gold_nugget"));
		rulers.put("huntsman", ruler("Huntsman Test", "Lord Huntsman", 140, 0, "amethyst_shard"));
		rulers.put("chancellor", ruler("Chancellor Test", "Lord Chancellor", 80, 0, "lapis_lazuli"));
		return new FactionDef("Test Marches", Optional.empty(), new FactionDef.Banner(TABARD, 0xD9E8A0), new FactionDef.Tabard(TABARD, 0xD9E8A0),
			Optional.of(new UnitType.Trim(Identifier.withDefaultNamespace("emerald"), List.of(Identifier.withDefaultNamespace("ward")))),
			false, List.of(), List.of(), roster, rulers, List.of("#elsewhere:court_realm_biomes"), Optional.of("elsewhere:court_music"), true,
			new FactionDef.Society(FactionDef.Government.MONARCHY, new FactionDef.RulerBonus("hp_per_lord", 12.0), List.of(), new FactionDef.StandingRule(Optional.of(-20), false), FactionDef.Serfs.DEFAULT, 7, 7),
			new FactionDef.Economy(Optional.of(Identifier.withDefaultNamespace("gold_nugget")), List.of()), FactionDef.Army.DEFAULT);
	}

	/** Registers the test faction (idempotent). */
	public static void register() {
		FactionDefs.putCodeDefined(MARCHES, def());
	}
}
