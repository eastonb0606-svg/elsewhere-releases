package dev.elsewhere.minion;

import dev.elsewhere.summoner.GearStats;
import dev.elsewhere.summoner.SummonerPerks;
import net.minecraft.world.entity.player.Player;

/**
 * Class identity (ARSENAL.md rev 3 §3b, Summoner Calling): every minion number that may depend on the owner's "attunement" (the count of worn
 * summoner-tagged armor pieces, 0..4) goes through here. The current design (ARSENAL.md) makes them all flat;
 * change the tables in {@link GearStats} to make off-class minions weaker, shorter-lived or costlier.
 */
public final class MinionTuning {
	private MinionTuning() {}

	/** Summoner Calling of the owner (0..4). */
	public static int attunement(Player owner) {
		return dev.elsewhere.stub.summoner.CallingStub.summoner(owner);
	}

	/** Calling for one minion: starter-staff minions (Paper Kite) count as at least Calling 2. */
	public static int calling(Player owner, MinionEntity m) {
		int c = attunement(owner);
		return m != null && m.isStarterMinion() ? Math.max(GearStats.STARTER_MIN_CALLING, c) : c;
	}

	/** 3.0 Callings (GEAR.md A1): Summoner mastery M, starter minions at least 0.60; -1 = the 2.1 tables. */
	public static float mastery(Player owner, MinionEntity m) {
		float v = dev.elsewhere.stub.summoner.CallingStub.mastery(owner);
		if (v < 0) return v;
		return m != null && m.isStarterMinion() ? Math.max(0.60F, v) : v;
	}

	public static double damageMultiplier(Player owner, MinionEntity m) {
		float v = mastery(owner, m);
		return v >= 0 ? 0.90 + 0.25 * v : at(GearStats.ATTUNE_DAMAGE, calling(owner, m));
	}

	public static int fervorTicks(Player owner, MinionEntity m) {
		float v = mastery(owner, m);
		return v >= 0 ? Math.round(20F * (30F + 30F * v)) : at(GearStats.ATTUNE_FERVOR_TICKS, calling(owner, m));
	}

	public static int reserve(Player owner, MinionEntity m) {
		if (m instanceof dev.elsewhere.summoner.Sporeling) return GearStats.SPORELING_RESERVE;
		float v = mastery(owner, m);
		return v >= 0 ? Math.round(6F - 2F * v) : at(GearStats.ATTUNE_RESERVE, calling(owner, m));
	}

	private static <T> T at(T[] table, int n) {
		return table[Math.max(0, Math.min(table.length - 1, n))];
	}

	public static double damageMultiplier(Player owner) {
		return damageMultiplier(owner, null);
	}

	public static int fervorTicks(Player owner) {
		return fervorTicks(owner, null);
	}

	public static float glimmerDrainPerSecond(Player owner) {
		return at(GearStats.ATTUNE_GLIMMER_DRAIN, attunement(owner));
	}

	/** Extra cap on slots by attunement (on top of the attribute and the hard cap). */
	public static int slotCap(Player owner) {
		float v = mastery(owner, null);
		if (v < 0) return at(GearStats.ATTUNE_SLOT_CAP, attunement(owner));
		int cap = 1 + (int) Math.floor(3F * (v - 0.25F) / 0.75F + 1e-4F); // 3.0: Primary 4, Secondary 2-3, Unattuned/Open 1
		var h = dev.elsewhere.stub.summoner.SummonerAttributes.slots();
		var inst = h == null ? null : owner.getAttribute(h);
		if (inst != null && inst.getValue() > inst.getBaseValue()) cap++; // any slot gear: +1 in total
		return Math.max(1, Math.min(GearStats.HARD_SLOT_CAP, cap));
	}
}
