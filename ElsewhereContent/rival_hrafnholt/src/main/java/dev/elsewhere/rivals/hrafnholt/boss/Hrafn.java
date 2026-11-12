package dev.elsewhere.rivals.hrafnholt.boss;

import java.util.Set;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The six Hrafnholt bosses (RIVAL-BOSSES H1-H6): registry path, court slot, HP, armour (real vanilla pieces and trims on a plain skin)
 * and the two signature moves a Risen champion keeps (faction file {@code succession.risen.moves}).
 */
public enum Hrafn {
	JARL("jarl", "king", 300, 4, "halvard", Set.of(),
		p("iron_helmet", "ward", "gold"), p("diamond_chestplate", "rib", "quartz"), p("iron_leggings", "rib", "quartz"), p("diamond_boots", "ward", "iron")),
	HERSIR("hersir", "marshal", 236, 3, "sigrun", Set.of("raid_charge", "horn_of_three_calls"),
		p("iron_helmet", "ward", "quartz"), p("diamond_chestplate", "rib", "iron"), p("iron_leggings", "rib", "iron"), p("iron_boots", "ward", "iron")),
	HALLWARDEN("hallwarden", "castellan", 257, 3, "torvald", Set.of("hearth_ward", "door_breaker"),
		p("iron_helmet", "ward", "iron"), p("diamond_chestplate", "rib", "gold"), p("iron_leggings", "ward", "iron"), p("iron_boots", "rib", "quartz")),
	SKALD("skald", "master_at_arms", 236, 3, "ulfar", Set.of("rune_throw", "three_beat_verse"),
		p("iron_helmet", "rib", "gold"), p("chainmail_chestplate", "ward", "gold"), p("chainmail_leggings", "rib", "iron"), p("chainmail_boots", "ward", "iron")),
	CHIEF("chief", "huntsman", 246, 3, "gudrun", Set.of("furrow_run", "pelt_leap"),
		new Piece("leather_helmet", "ward", "quartz", 0x6E6A66), null, p("iron_leggings", "rib", "iron"), null),
	HUNTMASTER("huntmaster", "chancellor", 214, 2, "eydis", Set.of("wolf_pack", "pinning_arrow"),
		p("chainmail_helmet", "rib", "iron"), new Piece("leather_chestplate", "ward", "copper", 0x6B5D4F), p("chainmail_leggings", "rib", "iron"), new Piece("leather_boots", "ward", "iron", 0x6B5D4F));

	/** A Hird-Captain's stats (kingdom_unit hrafnholt_hird_captain): 30 HP; Risen = plus 15 percent. */
	public static final double CAPTAIN_HP = 30.0;
	public static final double RISEN_BONUS = 0.15;
	/** RIVALS-DECISIONS 13: human bosses never have more than 300 max health. */
	public static final double HP_CAP = 300.0;

	public record Piece(String item, String pattern, String material, int dye) {}

	static Piece p(String item, String pattern, String material) {
		return new Piece(item, pattern, material, -1);
	}

	public final String key, courtSlot, skin;
	public final double hp;
	public final int tier;
	public final Set<String> risenMoves;
	public final @Nullable Piece[] pieces;
	/** Registry-filled: the seat boss type and the Risen type. */
	public net.minecraft.world.entity.EntityType<HrafnholtBoss> type, risenType;

	Hrafn(String key, String courtSlot, double hp, int tier, String skin, Set<String> risenMoves, @Nullable Piece... pieces) {
		this.key = key;
		this.courtSlot = courtSlot;
		this.hp = hp;
		this.tier = tier;
		this.skin = skin;
		this.risenMoves = risenMoves;
		this.pieces = pieces;
	}

	public boolean champion() {
		return this != JARL;
	}

	public Identifier id() {
		return HrafnholtContent.id("hrafnholt_" + key);
	}

	public Identifier risenId() {
		return HrafnholtContent.id("hrafnholt_risen_" + key);
	}

	public String nameKey() {
		return "entity.elsewhere.hrafnholt_" + key;
	}

	public String risenNameKey() {
		return "entity.elsewhere.hrafnholt_risen_" + key;
	}

	public String skinPath() {
		return "hrafnholt/boss/" + skin;
	}

	public String risenSkinPath() {
		return "hrafnholt/boss/risen_" + key;
	}

	public static @Nullable Hrafn bySlot(String slot) {
		for (Hrafn h : values()) if (h.courtSlot.equals(slot)) return h;
		return null;
	}

	public static @Nullable Hrafn byKey(String k) {
		for (Hrafn h : values()) if (h.key.equals(k)) return h;
		return null;
	}
}
