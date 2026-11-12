package dev.elsewhere.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * A data-driven faction ("kingdom"), loaded from {@code data/<ns>/kingdom_faction/<name>.json} (faction id
 * {@code <ns>:<name>}) and reloaded with {@code /reload}. Aldermark is the first one ({@code elsewhere:aldermark}).
 * <pre>{@code
 * {
 *   "name": "Kingdom of Aldermark",
 *   "name_key": "faction.elsewhere.aldermark",       // optional translation key (wins over "name" when present)
 *   "banner": { "primary": "#2E4C9E", "secondary": "#E8D9A0" },   // heraldry colours (int or "#RRGGBB")
 *   "tabard": { "color": "#2E4C9E", "trim": "#E8D9A0" },           // tabard layer tint + its border
 *   "trim": { "material": "minecraft:gold", "patterns": ["minecraft:host"] },   // optional: replaces every roster unit's armor trim
 *   "hostile_to_players": true,
 *   "allies": [], "peace": [],                         // diplomacy defaults; everything else is at war (config factions.defaultStance)
 *   "roster": {                                        // base unit id -> this faction's version of it (every field optional)
 *     "elsewhere:knight": { "skin": "ns:textures/entity/x.png", "armor": {"chest": "minecraft:iron_chestplate"},
 *                           "armor_dye": 10027008, "main_hand": "minecraft:iron_axe", "off_hand": "minecraft:shield",
 *                           "mount": "minecraft:horse", "mount_armor": "minecraft:iron_horse_armor",
 *                           "trim": {...}, "health_scale": 1.0, "unit": "ns:other_unit" }
 *   },
 *   "rulers": {                                        // 5 Lords and a King, keyed by slot: king, marshal, castellan, master_at_arms, huntsman, chancellor
 *     "king": { "name": "King Osric Vael", "name_key": "entity.elsewhere.court_king", "title": "the Hundredth Crown",
 *               "health": 300, "health_cap": 300,
 *               "drops": [ { "item": "elsewhere:court_crown_of_aldermark" }, { "item": "minecraft:emerald", "min": 6, "max": 12 } ] }
 *   },
 *   "biomes": ["#elsewhere:court_realm_biomes"],      // realm biome preference: biome ids or #tags
 *   "music": "elsewhere:court_music",                 // sound-event prefix: "<prefix>.<ruler slot>" and "<prefix>.realm"
 *   "realm": true,                                    // false: no castle (field armies only)
 *   "society": { "government": "monarchy",            // or "oligarchy" (no crown; a weekly Chair holds the ruler bonus)
 *                "ruler_bonus": {"type": "hp_per_lord", "amount": 12},   // | damage_per_champion (0.15) | hp_per_other_magnate (12) | none
 *                "leaders": ["king","marshal"],       // slots in rank order; empty = every key of "rulers"
 *                "standing": {"start": 0, "cap_until_ruler_killed": true},
 *                "serfs": {"type": "elsewhere:serf", "count_multiplier": 1.0, "health": 20, "levy": {"fraction": 0.33, "weapon": "minecraft:stone_axe"}},
 *                "chair_days": 7, "seat_refill_days": 7 },
 *   "economy": { "currency": "elsewhere:aldermark_crown", "shop": [ {"item": "...", "count": 1, "price": 220, "extra": [{"item": "...", "min": 3}], "weekly_limit": 2} ] },
 *   "army": { "units": [...], "unique_units": [...], "makeup": {"elsewhere:knight": 30}, "health_multiplier": 1.15, "damage_multiplier": 1.1, "speed_multiplier": 0.9,
 *             "size_multiplier": 1.2, "patrol_day": 6, "patrol_night": 7,
 *             "brain": { "war_frequency": 1.5, "min_attack_odds": 1.0, "retreat_instead_of_die": false, "veterancy_cap": 0.0, "intelligence_noise": 0.3, "supply_line_only": false,
 *                        "decision_delay_days": 0, "hire_mercenaries": false, "mercenary_daily_cost": 0,
 *                        "brain_roles": [ {"role": "king", "degrades": ["war_frequency"]} ] } }
 * }
 * }</pre>
 * A rival kingdom needs only this file plus skins; see {@code FACTIONS.md}.
 */
public record FactionDef(
	String name,
	Optional<String> nameKey,
	Banner banner,
	Tabard tabard,
	Optional<UnitType.Trim> trim,
	boolean hostileToPlayers,
	List<Identifier> allies,
	List<Identifier> peace,
	Map<Identifier, RosterEntry> roster,
	Map<String, RulerDef> rulers,
	List<String> biomes,
	Optional<String> music,
	boolean realm,
	Society society,
	Economy economy,
	Army army
) {
	public static final Codec<Integer> COLOR = Codec.withAlternative(Codec.INT, Codec.STRING.comapFlatMap(s -> {
		try {
			String t = s.startsWith("#") ? s.substring(1) : s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
			return DataResult.success((int) Long.parseLong(t, 16));
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "bad colour '" + s + "' (use an int or #RRGGBB)");
		}
	}, i -> String.format("#%06X", i & 0xFFFFFF)));

	/** Heraldry colours. */
	public record Banner(int primary, int secondary) {
		public static final Codec<Banner> CODEC = RecordCodecBuilder.create(i -> i.group(
			COLOR.optionalFieldOf("primary", 0x2E4C9E).forGetter(Banner::primary),
			COLOR.optionalFieldOf("secondary", 0xE8D9A0).forGetter(Banner::secondary)
		).apply(i, Banner::new));
	}

	/** Tabard tint and its trim (border) colour. */
	public record Tabard(int color, int trim) {
		public static final Codec<Tabard> CODEC = RecordCodecBuilder.create(i -> i.group(
			COLOR.optionalFieldOf("color", 0x2E4C9E).forGetter(Tabard::color),
			COLOR.optionalFieldOf("trim", 0xE8D9A0).forGetter(Tabard::trim)
		).apply(i, Tabard::new));
	}

	/** How this faction differs from a base unit type (all fields optional; missing ones keep the base unit's value). */
	public record RosterEntry(
		Optional<Identifier> unit,
		Optional<Identifier> skin,
		Optional<UnitType.Armor> armor,
		Optional<Integer> armorDye,
		Optional<Identifier> mainHand,
		Optional<Identifier> offHand,
		Optional<Identifier> mount,
		Optional<Identifier> mountArmor,
		Optional<UnitType.Trim> trim,
		Optional<Float> healthScale
	) {
		public static final Codec<RosterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("unit").forGetter(RosterEntry::unit),
			Identifier.CODEC.optionalFieldOf("skin").forGetter(RosterEntry::skin),
			UnitType.Armor.CODEC.optionalFieldOf("armor").forGetter(RosterEntry::armor),
			COLOR.optionalFieldOf("armor_dye").forGetter(RosterEntry::armorDye),
			Identifier.CODEC.optionalFieldOf("main_hand").forGetter(RosterEntry::mainHand),
			Identifier.CODEC.optionalFieldOf("off_hand").forGetter(RosterEntry::offHand),
			Identifier.CODEC.optionalFieldOf("mount").forGetter(RosterEntry::mount),
			Identifier.CODEC.optionalFieldOf("mount_armor").forGetter(RosterEntry::mountArmor),
			UnitType.Trim.CODEC.optionalFieldOf("trim").forGetter(RosterEntry::trim),
			Codec.FLOAT.optionalFieldOf("health_scale").forGetter(RosterEntry::healthScale)
		).apply(i, RosterEntry::new));
	}

	/** Form of government. */
	public enum Government implements net.minecraft.util.StringRepresentable {
		/** A ruler with Lords; the highest living Lord is crowned when the ruler dies (Aldermark, Hrafnholt, Azmar). */
		MONARCHY,
		/** A council of equals (Mirewick's Magnates): no crown; a weekly Chair holds the ruler bonus; dead seats are refilled by new families. */
		OLIGARCHY;

		public static final Codec<Government> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Government::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** How the ruler (or the Chair) is strengthened: {@code hp_per_lord} (+amount max HP per living Lord), {@code damage_per_champion} (+amount x damage per living champion, 0.15 = +15%), {@code hp_per_other_magnate}, or {@code none}. */
	public record RulerBonus(String type, double amount) {
		public static final RulerBonus ALDERMARK = new RulerBonus("hp_per_lord", 12.0);
		public static final Codec<RulerBonus> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("type", "hp_per_lord").forGetter(RulerBonus::type),
			Codec.DOUBLE.optionalFieldOf("amount", 12.0).forGetter(RulerBonus::amount)
		).apply(i, RulerBonus::new));
	}

	/** Player Standing: start value (-100..100; default -60 for factions hostile to players, else 0) and Aldermark's "cannot reach Friendly until the ruler has been killed once". */
	public record StandingRule(Optional<Integer> start, boolean capUntilRulerKilled) {
		public static final StandingRule DEFAULT = new StandingRule(Optional.empty(), false);
		public static final Codec<StandingRule> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("start").forGetter(StandingRule::start),
			Codec.BOOL.optionalFieldOf("cap_until_ruler_killed", false).forGetter(StandingRule::capUntilRulerKilled)
		).apply(i, StandingRule::new));
	}

	/** The village-labour tier: type id (a unit or entity id, resolved by the villages module), count per village x multiplier, HP, optional levy (fraction picks up weapons when the village is attacked). */
	public record Serfs(Optional<Identifier> type, float countMultiplier, double health, Optional<Levy> levy) {
		public static final Serfs DEFAULT = new Serfs(Optional.empty(), 1.0F, 20.0, Optional.empty());
		public static final Codec<Serfs> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("type").forGetter(Serfs::type),
			Codec.FLOAT.optionalFieldOf("count_multiplier", 1.0F).forGetter(Serfs::countMultiplier),
			Codec.DOUBLE.optionalFieldOf("health", 20.0).forGetter(Serfs::health),
			Levy.CODEC.optionalFieldOf("levy").forGetter(Serfs::levy)
		).apply(i, Serfs::new));
	}

	public record Levy(float fraction, Identifier weapon) {
		public static final Codec<Levy> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("fraction", 0.33F).forGetter(Levy::fraction),
			Identifier.CODEC.optionalFieldOf("weapon", Identifier.withDefaultNamespace("stone_axe")).forGetter(Levy::weapon)
		).apply(i, Levy::new));
	}

	/** Government, ruler bonus, the leader slots in rank order (empty = every slot of {@code rulers}), Standing rule and serf tier. */
	public record Society(Government government, RulerBonus bonus, List<String> leaders, StandingRule standing, Serfs serfs, int chairDays, int seatRefillDays) {
		public static final Society DEFAULT = new Society(Government.MONARCHY, RulerBonus.ALDERMARK, List.of(), StandingRule.DEFAULT, Serfs.DEFAULT, 7, 7);
		public static final Codec<Society> CODEC = RecordCodecBuilder.create(i -> i.group(
			Government.CODEC.optionalFieldOf("government", Government.MONARCHY).forGetter(Society::government),
			RulerBonus.CODEC.optionalFieldOf("ruler_bonus", RulerBonus.ALDERMARK).forGetter(Society::bonus),
			Codec.STRING.listOf().optionalFieldOf("leaders", List.of()).forGetter(Society::leaders),
			StandingRule.CODEC.optionalFieldOf("standing", StandingRule.DEFAULT).forGetter(Society::standing),
			Serfs.CODEC.optionalFieldOf("serfs", Serfs.DEFAULT).forGetter(Society::serfs),
			Codec.INT.optionalFieldOf("chair_days", 7).forGetter(Society::chairDays),
			Codec.INT.optionalFieldOf("seat_refill_days", 7).forGetter(Society::seatRefillDays)
		).apply(i, Society::new));
	}

	/** One shop line (the faction's Steward equivalent): {@code price} in the faction's currency plus optional extra items, with a per-faction weekly limit (0 = none). */
	public record ShopEntry(Identifier item, int count, int price, List<Drop> extra, int weeklyLimit) {
		public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(ShopEntry::item),
			Codec.INT.optionalFieldOf("count", 1).forGetter(ShopEntry::count),
			Codec.INT.fieldOf("price").forGetter(ShopEntry::price),
			Drop.CODEC.listOf().optionalFieldOf("extra", List.of()).forGetter(ShopEntry::extra),
			Codec.INT.optionalFieldOf("weekly_limit", 0).forGetter(ShopEntry::weeklyLimit)
		).apply(i, ShopEntry::new));
	}

	/** The faction's own currency item and shop table (prices and weekly limits are counted per faction). */
	public record Economy(Optional<Identifier> currency, List<ShopEntry> shop) {
		public static final Economy NONE = new Economy(Optional.empty(), List.of());
		public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("currency").forGetter(Economy::currency),
			ShopEntry.CODEC.listOf().optionalFieldOf("shop", List.of()).forGetter(Economy::shop)
		).apply(i, Economy::new));
	}

	/** A leader role whose death degrades some brain fields until the seat is refilled (e.g. Azmar's Sultana: {@code "min_attack_odds"}, {@code "intelligence_noise"}). */
	public record BrainRole(String role, List<String> degrades) {
		public static final Codec<BrainRole> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("role").forGetter(BrainRole::role),
			Codec.STRING.listOf().optionalFieldOf("degrades", List.of()).forGetter(BrainRole::degrades)
		).apply(i, BrainRole::new));
	}

	/**
	 * The strategic "brain" of the abstract war layer (schema and loading only; the war simulation reads it later, tuned numbers come
	 * from WAR-AI.md): {@code war_frequency} multiplier on starting wars and sieges (Aldermark 1.0), {@code min_attack_odds} (attacks only at
	 * this local odds or better; Aldermark 1.2), {@code retreat_instead_of_die}, {@code veterancy_cap} (extra combat value survivors keep, 0.3 = +30%),
	 * {@code intelligence_noise} (error of its enemy-strength estimates, 0 = perfect), {@code supply_line_only} (conquers only villages connected to its
	 * supply), {@code decision_delay_days}, {@code hire_mercenaries} with {@code mercenary_daily_cost} (in its currency), and {@code brain_roles}: leader
	 * roles whose death degrades the listed fields until the seat is refilled. {@code tuning} is a name to number map for the full WAR-AI table
	 * (see {@code war.Tuning#KEYS}); built-in tuned defaults exist for the four kingdoms, so it is only needed to change one.
	 */
	public record Brain(double warFrequency, double minAttackOdds, boolean retreatInsteadOfDie, double veterancyCap, double intelligenceNoise,
		boolean supplyLineOnly, double decisionDelayDays, boolean hireMercenaries, int mercenaryDailyCost, List<BrainRole> brainRoles, Map<String, Double> tuning) {
		public static final Brain DEFAULT = new Brain(1.0, 1.2, false, 0.0, 0.25, false, 0.0, false, 0, List.of(), Map.of());
		public static final Codec<Brain> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("war_frequency", 1.0).forGetter(Brain::warFrequency),
			Codec.DOUBLE.optionalFieldOf("min_attack_odds", 1.2).forGetter(Brain::minAttackOdds),
			Codec.BOOL.optionalFieldOf("retreat_instead_of_die", false).forGetter(Brain::retreatInsteadOfDie),
			Codec.DOUBLE.optionalFieldOf("veterancy_cap", 0.0).forGetter(Brain::veterancyCap),
			Codec.DOUBLE.optionalFieldOf("intelligence_noise", 0.25).forGetter(Brain::intelligenceNoise),
			Codec.BOOL.optionalFieldOf("supply_line_only", false).forGetter(Brain::supplyLineOnly),
			Codec.DOUBLE.optionalFieldOf("decision_delay_days", 0.0).forGetter(Brain::decisionDelayDays),
			Codec.BOOL.optionalFieldOf("hire_mercenaries", false).forGetter(Brain::hireMercenaries),
			Codec.INT.optionalFieldOf("mercenary_daily_cost", 0).forGetter(Brain::mercenaryDailyCost),
			BrainRole.CODEC.listOf().optionalFieldOf("brain_roles", List.of()).forGetter(Brain::brainRoles),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("tuning", Map.of()).forGetter(Brain::tuning)
		).apply(i, Brain::new));
	}

	/**
	 * Fielded units (base and the faction's two unique soldiers), army makeup (unit id -> percent), faction-wide HP, damage and speed
	 * multipliers (applied to every unit the faction fields; damage scales the unit's base attack-damage attribute), army size multiplier
	 * (garrisons and patrols; read by the villages/war layers), patrol sizes and the war brain.
	 */
	public record Army(List<Identifier> units, List<Identifier> uniqueUnits, Map<Identifier, Float> makeup, double healthMultiplier, double damageMultiplier,
		double speedMultiplier, double sizeMultiplier, int patrolDay, int patrolNight, Brain brain, Optional<String> landing) {
		/** Pre-2.1 ten-argument form (no landing flag). */
		public Army(List<Identifier> units, List<Identifier> uniqueUnits, Map<Identifier, Float> makeup, double healthMultiplier, double damageMultiplier,
			double speedMultiplier, double sizeMultiplier, int patrolDay, int patrolNight, Brain brain) {
			this(units, uniqueUnits, makeup, healthMultiplier, damageMultiplier, speedMultiplier, sizeMultiplier, patrolDay, patrolNight, brain, Optional.empty());
		}

		public static final Army DEFAULT = new Army(List.of(), List.of(), Map.of(), 1.0, 1.0, 1.0, 1.0, 6, 6, Brain.DEFAULT);
		public static final Codec<Army> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.listOf().optionalFieldOf("units", List.of()).forGetter(Army::units),
			Identifier.CODEC.listOf().optionalFieldOf("unique_units", List.of()).forGetter(Army::uniqueUnits),
			Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT).optionalFieldOf("makeup", Map.of()).forGetter(Army::makeup),
			Codec.DOUBLE.optionalFieldOf("health_multiplier", 1.0).forGetter(Army::healthMultiplier),
			Codec.DOUBLE.optionalFieldOf("damage_multiplier", 1.0).forGetter(Army::damageMultiplier),
			Codec.DOUBLE.optionalFieldOf("speed_multiplier", 1.0).forGetter(Army::speedMultiplier),
			Codec.DOUBLE.optionalFieldOf("size_multiplier", 1.0).forGetter(Army::sizeMultiplier),
			Codec.INT.optionalFieldOf("patrol_day", 6).forGetter(Army::patrolDay),
			Codec.INT.optionalFieldOf("patrol_night", 6).forGetter(Army::patrolNight),
			Brain.CODEC.optionalFieldOf("brain", Brain.DEFAULT).forGetter(Army::brain),
			Codec.STRING.optionalFieldOf("landing").forGetter(Army::landing)
		).apply(i, Army::new));
	}

	/** One drop line: {@code count} is rolled min..max uniformly, the line applies with {@code chance}. A missing item is skipped. */
	public record Drop(Identifier item, int min, int max, float chance) {
		public static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(Drop::item),
			Codec.INT.optionalFieldOf("min", 1).forGetter(Drop::min),
			Codec.INT.optionalFieldOf("max", -1).forGetter(Drop::max),
			Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(Drop::chance)
		).apply(i, (item, min, max, chance) -> new Drop(item, min, max < min ? min : max, chance)));
	}

	/** A ruler slot (King or one of the five Lords): who he or she is, health and what drops on the first kill. */
	public record RulerDef(String name, Optional<String> nameKey, String title, double health, Optional<Double> healthCap, List<Drop> drops) {
		public static final Codec<RulerDef> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(RulerDef::name),
			Codec.STRING.optionalFieldOf("name_key").forGetter(RulerDef::nameKey),
			Codec.STRING.optionalFieldOf("title", "").forGetter(RulerDef::title),
			Codec.DOUBLE.fieldOf("health").forGetter(RulerDef::health),
			Codec.DOUBLE.optionalFieldOf("health_cap").forGetter(RulerDef::healthCap),
			Drop.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(RulerDef::drops)
		).apply(i, RulerDef::new));

		/** Highest max health (the King gains health per living Lord, never above this). */
		public double cap() {
			return healthCap.orElse(health);
		}
	}

	public static final Codec<FactionDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(FactionDef::name),
		Codec.STRING.optionalFieldOf("name_key").forGetter(FactionDef::nameKey),
		Banner.CODEC.optionalFieldOf("banner", new Banner(0x2E4C9E, 0xE8D9A0)).forGetter(FactionDef::banner),
		Tabard.CODEC.optionalFieldOf("tabard", new Tabard(0x2E4C9E, 0xE8D9A0)).forGetter(FactionDef::tabard),
		UnitType.Trim.CODEC.optionalFieldOf("trim").forGetter(FactionDef::trim),
		Codec.BOOL.optionalFieldOf("hostile_to_players", true).forGetter(FactionDef::hostileToPlayers),
		Identifier.CODEC.listOf().optionalFieldOf("allies", List.of()).forGetter(FactionDef::allies),
		Identifier.CODEC.listOf().optionalFieldOf("peace", List.of()).forGetter(FactionDef::peace),
		Codec.unboundedMap(Identifier.CODEC, RosterEntry.CODEC).optionalFieldOf("roster", Map.of()).forGetter(FactionDef::roster),
		Codec.unboundedMap(Codec.STRING, RulerDef.CODEC).optionalFieldOf("rulers", Map.of()).forGetter(FactionDef::rulers),
		Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(FactionDef::biomes),
		Codec.STRING.optionalFieldOf("music").forGetter(FactionDef::music),
		Codec.BOOL.optionalFieldOf("realm", true).forGetter(FactionDef::realm),
		Society.CODEC.optionalFieldOf("society", Society.DEFAULT).forGetter(FactionDef::society),
		Economy.CODEC.optionalFieldOf("economy", Economy.NONE).forGetter(FactionDef::economy),
		Army.CODEC.optionalFieldOf("army", Army.DEFAULT).forGetter(FactionDef::army)
	).apply(i, FactionDef::new));

	/** True when the faction changes anything about its units (otherwise the base unit types are used untouched). */
	public boolean reskins() {
		return !roster.isEmpty() || trim.isPresent() || army.healthMultiplier() != 1.0 || army.speedMultiplier() != 1.0 || army.damageMultiplier() != 1.0;
	}

	/** Leader slots in rank order: the explicit {@code society.leaders} list, else the {@code rulers} keys (king, marshal, castellan, master_at_arms, huntsman, chancellor first). */
	public List<String> leaderSlots() {
		if (!society.leaders().isEmpty()) return society.leaders();
		List<String> out = new java.util.ArrayList<>();
		for (String k : new String[] {"king", "marshal", "castellan", "master_at_arms", "huntsman", "chancellor"}) if (rulers.containsKey(k)) out.add(k);
		for (String k : rulers.keySet()) if (!out.contains(k)) out.add(k);
		return out;
	}

	/** The player's starting Standing with this faction. */
	public int startStanding() {
		return Math.clamp(society.standing().start().orElse(hostileToPlayers ? -60 : 0), -100, 100);
	}

	/** A plain def for code-defined factions: tabard colour only. */
	public static FactionDef simple(String name, int color, boolean hostileToPlayers) {
		return new FactionDef(name, Optional.empty(), new Banner(color, color), new Tabard(color, color), Optional.empty(), hostileToPlayers,
			List.of(), List.of(), Map.of(), Map.of(), List.of(), Optional.empty(), false, Society.DEFAULT, Economy.NONE, Army.DEFAULT);
	}
}
