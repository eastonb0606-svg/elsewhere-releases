package dev.elsewhere.kingdom_court.ruler;

import dev.elsewhere.kingdom_court.CourtContent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The six unique rulers of Aldermark. {@link #rank} is the succession order (0 = the King, then 1 = highest Lord):
 * when the crowned ruler dies, the living Lord with the lowest rank takes the throne. Health is at most half of
 * KINGDOM.md's numbers (the Chancellor at most a quarter), per KINGDOM-DECISIONS.
 */
public enum Ruler {
	KING("king", 0, 300.0, 2.0, 20, 8),            // KINGDOM.md 720 -> 300 + 12 per living Lord (max 360 = half)
	MARSHAL("marshal", 1, 190.0, 1.5, 12, 3),      // 400 -> 190
	CASTELLAN("castellan", 2, 210.0, 1.0, 16, 4),  // 440 -> 210
	MASTER_AT_ARMS("master_at_arms", 3, 165.0, 1.0, 10, 2), // 340 -> 165
	HUNTSMAN("huntsman", 4, 170.0, 1.0, 8, 1),     // 360 -> 170
	CHANCELLOR("chancellor", 5, 90.0, 1.0, 8, 0);  // 380 -> 90 (quarter = 95)

	public final String key;
	public final int rank;
	public final double health;
	/** Base attack damage (weapon damage is added on top, as for a player). */
	public final double attack;
	/** Total armor points worn (from items + attribute). */
	public final int armor;
	public final int toughness;
	public @Nullable EntityType<? extends CourtRuler> type;

	Ruler(String key, int rank, double health, double attack, int armor, int toughness) {
		this.key = key;
		this.rank = rank;
		this.health = health;
		this.attack = attack;
		this.armor = armor;
		this.toughness = toughness;
	}

	public Identifier unitId() {
		return CourtContent.id("court_" + key);
	}

	public Identifier entityId() {
		return CourtContent.id("court_" + key);
	}

	public boolean isLord() {
		return this != KING;
	}

	public String nameKey() {
		return "entity.elsewhere.court_" + key;
	}

	public static @Nullable Ruler byKey(String k) {
		for (Ruler r : values()) if (r.key.equals(k)) return r;
		return null;
	}

	/** Lords in succession order (highest first). */
	public static Ruler[] lordsByRank() {
		return new Ruler[] {MARSHAL, CASTELLAN, MASTER_AT_ARMS, HUNTSMAN, CHANCELLOR};
	}
}
