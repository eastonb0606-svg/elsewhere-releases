package dev.elsewhere.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * A data-driven soldier loadout, loaded from {@code data/<ns>/kingdom_unit/<name>.json} (id {@code <ns>:<name>}).
 * Example (man-at-arms):
 * <pre>{@code
 * {
 *   "role": "melee",
 *   "officer": false,
 *   "skin": "elsewhere:textures/entity/kingdom/soldier_a.png",
 *   "armor": { "head": "minecraft:chainmail_helmet", "chest": "minecraft:chainmail_chestplate",
 *              "legs": "minecraft:chainmail_leggings", "feet": "minecraft:chainmail_boots" },
 *   "armor_dye": 3893806,                      // optional, dyes dyeable (leather) pieces, 0xRRGGBB
 *   "main_hand": "minecraft:iron_sword",
 *   "off_hand": "minecraft:shield",
 *   "mount": "minecraft:horse",                // optional; the soldier spawns riding it
 *   "mount_armor": "minecraft:iron_horse_armor",
 *   "stats":  { "health": 24, "speed": 0.3, "attack_damage": 1.0, "follow_range": 32, "knockback_resistance": 0, "armor": 0, "armor_toughness": 0 },
 *   "combat": { "block_chance": 0.75, "crit_chance": 0.35, "crit_cooldown": 50, "retreat_health": 0.3 }
 * }
 * }</pre>
 * Every field except {@code role} has a default. Items are applied when the soldier spawns (and re-applied if the
 * unit is changed with {@link KingdomSoldier#setUnit}). {@code attack_damage} is the base before the weapon's bonus
 * (players use 1.0); weapon attack speed and damage come from the item exactly like a player's.
 */
public record UnitType(
	Role role,
	boolean officer,
	Identifier skin,
	Armor armor,
	Optional<Integer> armorDye,
	Optional<Identifier> mainHand,
	Optional<Identifier> offHand,
	Optional<Identifier> mount,
	Optional<Identifier> mountArmor,
	Stats stats,
	Combat combat,
	Ability ability,
	Optional<Trim> trim,
	Optional<Float> aggression
) {
	/** The 0.1.0 constructor (no ability, no trim, default aggression). */
	public UnitType(Role role, boolean officer, Identifier skin, Armor armor, Optional<Integer> armorDye, Optional<Identifier> mainHand,
		Optional<Identifier> offHand, Optional<Identifier> mount, Optional<Identifier> mountArmor, Stats stats, Combat combat) {
		this(role, officer, skin, armor, armorDye, mainHand, offHand, mount, mountArmor, stats, combat, Ability.NONE, Optional.empty(), Optional.empty());
	}

	/** Special behaviour of support/officer units (0.2.0). */
	public enum Ability implements StringRepresentable {
		NONE,
		/** Standard-bearer: allies within 12 blocks deal +10% damage and regain morale faster; death costs the squad 25 morale. */
		STANDARD,
		/** Herald: blows a horn once per life when the squad engages; a nearby virtual squad (else a small new squad) reinforces. */
		HERALD,
		/** Surgeon: splashes healing on wounded allies (officers first) every 5 s. */
		SURGEON,
		/** Squire: patches up wounded officers nearby every 15 s. */
		SQUIRE,
		/** Captain: war cry (Strength I 10 s to allies within 16 blocks, 30 s cooldown); commands up to 2 squads. */
		CAPTAIN,
		/** Knight: shield bash (knockback) while blocking, 5 s cooldown. */
		BASH;

		public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Armor trim applied to every armor piece: {@code material} (a trim material id, e.g. {@code minecraft:iron}) and one of
	 * {@code patterns}, picked per soldier (stable per UUID), so a unit has a few looks.
	 */
	public record Trim(Identifier material, java.util.List<Identifier> patterns) {
		public static final Codec<Trim> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("material").forGetter(Trim::material),
			Identifier.CODEC.listOf().fieldOf("patterns").forGetter(Trim::patterns)
		).apply(i, Trim::new));
	}

	/** Aggression added for the killer when a player kills this unit (default: officers 2, others 0). */
	public float aggressionValue() {
		return aggression.orElse(officer ? 2F : 0F);
	}
	/** What AI the soldier runs. */
	public enum Role implements StringRepresentable {
		/** Sword/axe and shield: blocking, crits, strafing, formation front rank. */
		MELEE,
		/** Bow: keeps distance and strafes (vanilla bow goal), formation rear rank. */
		ARCHER,
		/** Crossbow: vanilla crossbow goal, formation rear rank. */
		CROSSBOW,
		/** Mounted spear charge with the vanilla kinetic spear (vanilla spear goal, works on a horse). */
		LANCER,
		/** Mounted sword: charge, slash, ride through, turn, charge again. */
		CAVALRY;

		public static final Codec<Role> CODEC = StringRepresentable.fromEnum(Role::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public boolean ranged() {
			return this == ARCHER || this == CROSSBOW;
		}
	}

	public record Armor(Optional<Identifier> head, Optional<Identifier> chest, Optional<Identifier> legs, Optional<Identifier> feet) {
		public static final Armor NONE = new Armor(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		public static final Codec<Armor> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("head").forGetter(Armor::head),
			Identifier.CODEC.optionalFieldOf("chest").forGetter(Armor::chest),
			Identifier.CODEC.optionalFieldOf("legs").forGetter(Armor::legs),
			Identifier.CODEC.optionalFieldOf("feet").forGetter(Armor::feet)
		).apply(i, Armor::new));
	}

	public record Stats(double health, double speed, double attackDamage, double followRange, double knockbackResistance, double armor, double armorToughness) {
		public static final Stats DEFAULT = new Stats(24, 0.3, 1.0, 32, 0, 0, 0);

		/** Seven-argument form kept for callers written before armor_toughness (2.1 human health ladder). */
		public Stats(double health, double speed, double attackDamage, double followRange, double knockbackResistance, double armor) {
			this(health, speed, attackDamage, followRange, knockbackResistance, armor, 0);
		}
		public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("health", 24.0).forGetter(Stats::health),
			Codec.DOUBLE.optionalFieldOf("speed", 0.3).forGetter(Stats::speed),
			Codec.DOUBLE.optionalFieldOf("attack_damage", 1.0).forGetter(Stats::attackDamage),
			Codec.DOUBLE.optionalFieldOf("follow_range", 32.0).forGetter(Stats::followRange),
			Codec.DOUBLE.optionalFieldOf("knockback_resistance", 0.0).forGetter(Stats::knockbackResistance),
			Codec.DOUBLE.optionalFieldOf("armor", 0.0).forGetter(Stats::armor),
			Codec.DOUBLE.optionalFieldOf("armor_toughness", 0.0).forGetter(Stats::armorToughness)
		).apply(i, Stats::new));
	}

	/**
	 * Combat tuning. {@code block_chance}: chance to raise the shield when a threat starts; {@code crit_chance}: chance to
	 * jump-attack when an attack is ready (rolled once per attack); {@code crit_cooldown}: ticks between jump-crits;
	 * {@code retreat_health}: health fraction below which the soldier may back off (shield up) for a while.
	 * 0.2.0 (all default 1.0 = the original full player-like behaviour, which Lords/the King keep):
	 * {@code sprint_chance}: chance per attack to sprint in for a knockback hit; {@code strafe}: strafing strength 0..1;
	 * {@code retreat_chance}: chance to retreat once health drops below {@code retreat_health}; {@code mounted_bonus}:
	 * damage multiplier against riders and horses (pikemen).
	 */
	public record Combat(float blockChance, float critChance, int critCooldown, float retreatHealth, float sprintChance, float strafe, float retreatChance, float mountedBonus) {
		public static final Combat DEFAULT = new Combat(0.75F, 0.35F, 50, 0.3F);
		public static final Codec<Combat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("block_chance", 0.75F).forGetter(Combat::blockChance),
			Codec.FLOAT.optionalFieldOf("crit_chance", 0.35F).forGetter(Combat::critChance),
			Codec.INT.optionalFieldOf("crit_cooldown", 50).forGetter(Combat::critCooldown),
			Codec.FLOAT.optionalFieldOf("retreat_health", 0.3F).forGetter(Combat::retreatHealth),
			Codec.FLOAT.optionalFieldOf("sprint_chance", 1.0F).forGetter(Combat::sprintChance),
			Codec.FLOAT.optionalFieldOf("strafe", 1.0F).forGetter(Combat::strafe),
			Codec.FLOAT.optionalFieldOf("retreat_chance", 1.0F).forGetter(Combat::retreatChance),
			Codec.FLOAT.optionalFieldOf("mounted_bonus", 1.0F).forGetter(Combat::mountedBonus)
		).apply(i, Combat::new));

		/** The 0.1.0 constructor: full player-like sprinting, strafing and retreating (Lords/King style). */
		public Combat(float blockChance, float critChance, int critCooldown, float retreatHealth) {
			this(blockChance, critChance, critCooldown, retreatHealth, 1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	public static final Identifier DEFAULT_SKIN = KingdomContent.id("textures/entity/kingdom/soldier_a.png");

	public static final Codec<UnitType> CODEC = RecordCodecBuilder.create(i -> i.group(
		Role.CODEC.fieldOf("role").forGetter(UnitType::role),
		Codec.BOOL.optionalFieldOf("officer", false).forGetter(UnitType::officer),
		Identifier.CODEC.optionalFieldOf("skin", DEFAULT_SKIN).forGetter(UnitType::skin),
		Armor.CODEC.optionalFieldOf("armor", Armor.NONE).forGetter(UnitType::armor),
		Codec.INT.optionalFieldOf("armor_dye").forGetter(UnitType::armorDye),
		Identifier.CODEC.optionalFieldOf("main_hand").forGetter(UnitType::mainHand),
		Identifier.CODEC.optionalFieldOf("off_hand").forGetter(UnitType::offHand),
		Identifier.CODEC.optionalFieldOf("mount").forGetter(UnitType::mount),
		Identifier.CODEC.optionalFieldOf("mount_armor").forGetter(UnitType::mountArmor),
		Stats.CODEC.optionalFieldOf("stats", Stats.DEFAULT).forGetter(UnitType::stats),
		Combat.CODEC.optionalFieldOf("combat", Combat.DEFAULT).forGetter(UnitType::combat),
		Ability.CODEC.optionalFieldOf("ability", Ability.NONE).forGetter(UnitType::ability),
		Trim.CODEC.optionalFieldOf("trim").forGetter(UnitType::trim),
		Codec.FLOAT.optionalFieldOf("aggression").forGetter(UnitType::aggression)
	).apply(i, UnitType::new));

	/** Used when a unit id is unknown (data missing): an unarmored swordsman. */
	public static final UnitType FALLBACK = new UnitType(Role.MELEE, false, DEFAULT_SKIN, Armor.NONE, Optional.empty(),
		Optional.of(Identifier.withDefaultNamespace("iron_sword")), Optional.empty(), Optional.empty(), Optional.empty(), Stats.DEFAULT, Combat.DEFAULT);

	public boolean mounted() {
		return mount.isPresent();
	}
}
