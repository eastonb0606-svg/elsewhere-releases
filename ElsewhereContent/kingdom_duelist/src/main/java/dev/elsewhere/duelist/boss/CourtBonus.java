package dev.elsewhere.duelist.boss;

import java.util.function.IntSupplier;

/**
 * The ruler bonus (X9): damage and HP that depend on how many of the court are alive. The faction lane supplies the
 * count (it reads its own kingdom data file). Examples: {@code perLiving(champions, 0.10, 0.50, 0)} is the Hird-oath;
 * {@code perLiving(courtBosses, 0, 0, 12)} is Azmar's +12 HP per living court boss; the Chair adds {@code flatDamage}.
 */
public final class CourtBonus {
	public static final CourtBonus NONE = new CourtBonus(() -> 0, 0, 0, 0, 0);

	private final IntSupplier living;
	private final double dmgPer, dmgMax, hpPer, flatDamage;

	private CourtBonus(IntSupplier living, double dmgPer, double dmgMax, double hpPer, double flatDamage) {
		this.living = living;
		this.dmgPer = dmgPer;
		this.dmgMax = dmgMax;
		this.hpPer = hpPer;
		this.flatDamage = flatDamage;
	}

	/** @param dmgPer damage bonus per living ally (0.10 = +10 percent); @param dmgMax cap on that bonus; @param hpPer extra max HP per living ally. */
	public static CourtBonus perLiving(IntSupplier living, double dmgPer, double dmgMax, double hpPer) {
		return new CourtBonus(living, dmgPer, dmgMax, hpPer, 0);
	}

	public CourtBonus withFlatDamage(double f) {
		return new CourtBonus(living, dmgPer, dmgMax, hpPer, f);
	}

	/** Damage multiplier, 1.0 for none. */
	public double damageMultiplier() {
		return 1.0 + Math.min(dmgMax, dmgPer * living.getAsInt()) + flatDamage;
	}

	/** Extra max HP right now. */
	public double bonusHp() {
		return hpPer * living.getAsInt();
	}
}
