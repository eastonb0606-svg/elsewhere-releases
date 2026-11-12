package dev.elsewhere.kingdom_court.ruler;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_court.CourtContent;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Code-defined unit types and entity types {@code elsewhere:court_<ruler>} (spawn eggs land in the kingdom tab). */
public final class CourtRulers {
	private CourtRulers() {}

	public static final Identifier CAPTAIN_SKIN = CourtContent.id("textures/entity/kingdom/captain.png");
	public static final Identifier OFFICER_SKIN = CourtContent.id("textures/entity/kingdom/officer.png");
	public static final Identifier MARSHAL_FOOT = CourtContent.id("court_marshal_foot");

	static Optional<Identifier> mc(String path) {
		return path == null ? Optional.empty() : Optional.of(Identifier.withDefaultNamespace(path));
	}

	static UnitType.Armor armor(String h, String c, String l, String f) {
		return new UnitType.Armor(mc(h), mc(c), mc(l), mc(f));
	}

	static UnitType unit(Ruler r, UnitType.Role role, UnitType.Armor armor, Integer dye, String main, String off, String mount, String mountArmor, double speed, float block, float crit) {
		return new UnitType(role, true, r == Ruler.KING || r == Ruler.CHANCELLOR ? OFFICER_SKIN : CAPTAIN_SKIN, armor, Optional.ofNullable(dye), mc(main), mc(off), mc(mount), mc(mountArmor),
			new UnitType.Stats(r.health, speed, r.attack, 40.0, r == Ruler.KING ? 0.5 : 0.3, 0.0),
			new UnitType.Combat(Math.min(0.95F, block * 1.3F), crit, 30, 0.0F));   // RIVALS-DECISIONS 13: defensive behaviour (blocking) 30 percent more often, at most 0.95
	}

	/** The faction's definition of this ruler slot, or null when the faction has none (code defaults apply). */
	public static FactionDef.RulerDef def(Identifier faction, Ruler r) {
		FactionDef d = Factions.def(faction);
		return d == null ? null : d.rulers().get(r.key);
	}

	/** Display name: the faction's own name (translation key when given), else the built-in Aldermark name. */
	public static Component displayName(Identifier faction, Ruler r) {
		FactionDef.RulerDef d = def(faction, r);
		if (d == null) return Component.translatable(r.nameKey());
		return d.nameKey().<Component>map(Component::translatable).orElseGet(() -> Component.literal(d.name()));
	}

	/** Base max health of the slot for this faction (the file's number; the enum's when there is none). */
	public static double baseHealth(Identifier faction, Ruler r) {
		FactionDef.RulerDef d = def(faction, r);
		return (d == null ? r.health : d.health()) * hpScale(faction);
	}

	/** Aldermark's King and Lords are scaled by one factor that brings a full-court King (300 + 5 x 12 = 360) to the 300 cap (RIVALS-DECISIONS 13); the rivals' numbers are their own. */
	public static final double ALDERMARK_SCALE = 300.0 / 360.0;

	public static double hpScale(Identifier faction) {
		return dev.elsewhere.kingdom.Factions.ALDERMARK.equals(faction) ? ALDERMARK_SCALE : 1.0;
	}


	/**
	 * Highest max health the slot may reach (the King's gating). An explicit {@code health_cap} in the faction file is an absolute
	 * max health (kingdom 0.4.5 aldermark.json: 300, the human boss cap) and is NOT scaled again by {@link #hpScale}: scaling it gave
	 * Aldermark's full-court King 250 instead of 300. Without one, the cap is the slot's own (scaled) health, as before.
	 */
	public static double healthCap(Identifier faction, Ruler r) {
		FactionDef.RulerDef d = def(faction, r);
		double scaled = (d == null ? r.health : d.health()) * hpScale(faction);
		double cap = d == null ? (r.health + 60.0) * hpScale(faction) : d.healthCap().map(c -> Math.max(c, scaled)).orElse(scaled);
		return Math.min(dev.elsewhere.kingdom_court.api.HpCap.CAP, cap);
	}

	public static void init() {
		UnitTypes.putCodeDefined(Ruler.KING.unitId(), unit(Ruler.KING, UnitType.Role.MELEE,
			armor("golden_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"), null, "netherite_sword", "shield", null, null, 0.3, 0.9F, 0.55F));
		UnitTypes.putCodeDefined(Ruler.MARSHAL.unitId(), unit(Ruler.MARSHAL, UnitType.Role.LANCER,
			armor("iron_helmet", "diamond_chestplate", "iron_leggings", "diamond_boots"), null, "diamond_spear", null, "horse", "diamond_horse_armor", 0.3, 0.8F, 0.5F));
		UnitTypes.putCodeDefined(MARSHAL_FOOT, unit(Ruler.MARSHAL, UnitType.Role.MELEE,
			armor("iron_helmet", "diamond_chestplate", "iron_leggings", "diamond_boots"), null, "diamond_axe", null, null, null, 0.3, 0.6F, 0.55F));
		UnitTypes.putCodeDefined(Ruler.CASTELLAN.unitId(), unit(Ruler.CASTELLAN, UnitType.Role.MELEE,
			armor("iron_helmet", "netherite_chestplate", "netherite_leggings", "iron_boots"), null, "diamond_sword", "shield", null, null, 0.27, 0.95F, 0.45F));
		UnitTypes.putCodeDefined(Ruler.MASTER_AT_ARMS.unitId(), unit(Ruler.MASTER_AT_ARMS, UnitType.Role.MELEE,
			armor("chainmail_helmet", "diamond_chestplate", "chainmail_leggings", "diamond_boots"), null, "diamond_sword", "iron_sword", null, null, 0.33, 0.0F, 0.7F));
		UnitTypes.putCodeDefined(Ruler.HUNTSMAN.unitId(), unit(Ruler.HUNTSMAN, UnitType.Role.ARCHER,
			armor("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"), 0x2F5D2A, "bow", null, null, null, 0.32, 0.0F, 0.5F));
		UnitTypes.putCodeDefined(Ruler.CHANCELLOR.unitId(), unit(Ruler.CHANCELLOR, UnitType.Role.MELEE,
			armor("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"), 0x4B2A6B, "writable_book", null, null, null, 0.3, 0.0F, 0.1F));
		for (Ruler r : Ruler.values()) {
			final Ruler rr = r;
			r.type = KingdomEntities.registerSoldierType("court_" + r.key, r.unitId(), (t, l) -> new CourtRuler(t, l, rr));
		}
	}
}
