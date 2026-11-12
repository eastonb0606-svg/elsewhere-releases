package dev.elsewhere.rivals.mirewick.boss;

import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * The five seats of the Lantern Council (RIVAL-BOSSES.md M1-M5). Numbers are the document's: HP, tier, leaning, armor pieces with
 * their dye, trim pattern and trim material (real vanilla armor and trims on a plain-clothing skin), weapons and the councils role.
 */
public enum Magnate {
	MARROW("marrow", "master_at_arms", 246, 3, Leaning.MISER, Role.RANGED, UnitType.Role.CROSSBOW, "crossbow", "iron_sword", null, 0.30, 8.0,
		new Piece[] {new Piece("chainmail_helmet", -1, "tide", "gold"), new Piece("leather_chestplate", 0x5B2A86, "wild", "gold"),
			new Piece("leather_leggings", 0x9E1B32, "tide", "gold"), new Piece("leather_boots", 0x9E1B32, "wild", "amethyst")}),
	THWAITE("thwaite", "marshal", 246, 3, Leaning.HAWK, Role.FRONT, UnitType.Role.MELEE, "diamond_spear", "iron_axe", null, 0.29, 7.0,
		new Piece[] {new Piece("iron_helmet", -1, "tide", "amethyst"), new Piece("diamond_chestplate", -1, "wild", "amethyst"),
			new Piece("chainmail_leggings", -1, "tide", "amethyst"), new Piece("iron_boots", -1, "wild", "amethyst")}),
	SORREL("sorrel", "castellan", 257, 3, Leaning.DOVE, Role.FRONT, UnitType.Role.MELEE, "mace", "shield", null, 0.25, 8.0,
		new Piece[] {new Piece("iron_helmet", -1, "tide", "amethyst"), new Piece("diamond_chestplate", -1, "wild", "amethyst"),
			new Piece("diamond_leggings", -1, "tide", "amethyst"), new Piece("iron_boots", -1, "wild", "amethyst")}),
	TOLLIVER("tolliver", "huntsman", 236, 2, Leaning.HAWK, Role.RANGED, UnitType.Role.ARCHER, "bow", "iron_axe", null, 0.32, 6.0,
		new Piece[] {new Piece("leather_helmet", 0x5B2A86, "wild", "gold"), new Piece("chainmail_chestplate", -1, "tide", "amethyst"),
			new Piece("leather_leggings", 0x9E1B32, "wild", "gold"), new Piece("leather_boots", 0x9E1B32, "tide", "amethyst")}),
	AMES("ames", "chancellor", 204, 2, Leaning.MISER, Role.SUPPORT, UnitType.Role.MELEE, "iron_shovel", null, null, 0.29, 6.0,
		new Piece[] {new Piece("leather_helmet", 0x5B2A86, "tide", "gold"), new Piece("leather_chestplate", 0x5B2A86, "wild", "gold"),
			new Piece("leather_leggings", 0x5B2A86, "tide", "gold"), new Piece("leather_boots", 0x5B2A86, "wild", "gold")});

	/** How a Magnate votes: a hawk votes war, a dove peace, a miser hires sellswords. */
	public enum Leaning { HAWK, MISER, DOVE }

	/** Role on arrival in a group fight (RIVAL-BOSSES Council rule 1). */
	public enum Role { FRONT, RANGED, SUPPORT }

	/** One armor piece: item path, dye (-1 = undyed), trim pattern path and trim material path (vanilla). */
	public record Piece(String item, int dye, String pattern, String material) {}

	public static final double CHAIR_HP_PER_OTHER = 12.0;
	public static final double CHAIR_DAMAGE = 0.15;
	public static final String[] KEYS = {"marrow", "thwaite", "sorrel", "tolliver", "ames"};

	public final String key, courtSlot;
	public final double hp;
	public final int tier;
	public final Leaning leaning;
	public final Role role;
	public final UnitType.Role unitRole;
	public final String mainHand, altHand, offHand;
	public final double speed, attack;
	public final Piece[] pieces;
	/** Registered by {@link MagnateBoss#init()}. */
	public EntityType<MagnateBoss> type;

	Magnate(String key, String courtSlot, double hp, int tier, Leaning leaning, Role role, UnitType.Role unitRole, String main, String alt, String off, double speed, double attack, Piece[] pieces) {
		this.key = key;
		this.courtSlot = courtSlot;
		this.hp = hp;
		this.tier = tier;
		this.leaning = leaning;
		this.role = role;
		this.unitRole = unitRole;
		this.mainHand = main;
		this.altHand = alt;
		this.offHand = off;
		this.speed = speed;
		this.attack = attack;
		this.pieces = pieces;
	}

	public Identifier unitId() {
		return MirewickContent.id("mirewick_" + key);
	}

	public Identifier skin() {
		return MirewickContent.id("textures/entity/kingdom/mirewick/magnate_" + key + ".png");
	}

	public String nameKey() {
		return "entity.elsewhere.mirewick_" + key;
	}

	public static Magnate byKey(String k) {
		for (Magnate m : values()) if (m.key.equals(k)) return m;
		return null;
	}
}
