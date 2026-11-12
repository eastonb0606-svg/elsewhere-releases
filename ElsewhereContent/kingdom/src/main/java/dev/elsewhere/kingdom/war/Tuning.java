package dev.elsewhere.kingdom.war;

import dev.elsewhere.kingdom.FactionDef;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The tuned brain numbers of one kingdom (WAR-AI.md section 3, the shipped values). Field names in the faction JSON
 * ({@code army.brain.tuning}) are the snake_case names listed in {@link #KEYS}. Built-in defaults exist for
 * {@code aldermark}, {@code hrafnholt}, {@code azmar} and {@code mirewick} (by id path); any other faction starts from the
 * Aldermark baseline. The legacy {@code army.brain} fields (war_frequency, min_attack_odds, intelligence_noise, veterancy_cap,
 * supply_line_only, decision_delay_days, hire_mercenaries, mercenary_daily_cost) override the defaults when they differ from the
 * schema default, and {@code brain.tuning} entries override everything.
 */
public final class Tuning implements Cloneable {
	/** Every key accepted in {@code brain.tuning}. */
	public static final java.util.List<String> KEYS = java.util.List.of("hp", "dmg", "size", "war_freq", "attack_rate", "free_rate", "commit", "min_odds", "retreat",
		"def_retreat", "noise", "skirm_odds", "scout", "vet_gain", "vet_cap", "vet_decay", "supply", "fort_rate", "fort_cap", "fort_border_only", "integ", "unrest",
		"pursuit", "opening", "delay_min", "delay_max", "timing", "timing_p", "broker", "leader_cap", "inc_base", "inc_village", "inc_noise", "head_cost",
		"hire_max", "hire_cost", "hire_pay", "hire_reserve", "hire_retain", "desert", "disorg", "miser", "react", "standing_loss", "standing_gain");

	public double hp = 1, dmg = 1, size = 1;
	public double warFreq = 1.0, attackRate = 0.50, freeRate = 0.20, commit = 0.40;
	public double minOdds = 1.2, retreat = 0.55, defRetreat = 0.0, noise = 0.49, skirmOdds = 0.0;
	public int scout = 2;
	public double vetGain = 0, vetCap = 0, vetDecay = 0;
	public boolean supply = false;
	public double fortRate = 0.002, fortCap = 0.25;
	public boolean fortBorderOnly = false;
	public double integ = 8, unrest = 0.50, pursuit = 0.0;
	public int opening = 0, delayMin = 0, delayMax = 0;
	public boolean timing = false, broker = false;
	public double timingP = 0.0, leaderCap = 1.0;
	public double incBase = 8.0, incVillage = 1.5, incNoise = 0.30, headCost = 6.0;
	public double hireMax = 0, hireCost = 4.0, hirePay = 1.2, hireReserve = 150.0, hireRetain = 3.0, desert = 0.5, disorg = 0.0, miser = 0.0;
	/** Field-army response multiplier (1 normally, lower while a brain seat is empty). */
	public double react = 1.0;
	/** Player Standing multipliers: how fast the player loses / gains Standing with this kingdom (RIVALS-DECISIONS 2.1). */
	public double standingLoss = 1.0, standingGain = 1.0;

	@Override
	public Tuning clone() {
		try {
			return (Tuning) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The built-in tuned values for a faction id path (unknown paths get the Aldermark baseline). */
	public static Tuning builtin(String path) {
		Tuning t = new Tuning();
		switch (path) {
			case "hrafnholt" -> {
				t.hp = 1.15; t.dmg = 1.10; t.size = 1.20;
				t.warFreq = 1.5; t.attackRate = 0.75; t.freeRate = 0.32; t.commit = 0.50;
				t.minOdds = 1.0; t.retreat = 0.0; t.defRetreat = 0.0; t.noise = 0.45; t.skirmOdds = 0; t.scout = 1;
				t.vetGain = 0.02; t.vetCap = 0.10; t.vetDecay = 0.01;
				t.fortRate = 0.0; t.fortCap = 0.10; t.integ = 12; t.unrest = 0.80; t.pursuit = 0.12;
				t.headCost = 7.5;
				t.standingLoss = 1.5; t.standingGain = 0.75;
			}
			case "azmar" -> {
				t.hp = 0.80; t.dmg = 0.90; t.size = 0.75;
				t.warFreq = 1.0; t.attackRate = 0.30; t.freeRate = 0.12; t.commit = 0.75;
				t.minOdds = 1.5; t.retreat = 0.85; t.defRetreat = 0.40; t.noise = 0.07; t.skirmOdds = 1.25; t.scout = 99;
				t.vetGain = 0.20; t.vetCap = 0.30; t.vetDecay = 0.002;
				t.supply = true; t.fortRate = 0.010; t.fortCap = 0.90; t.fortBorderOnly = true;
				t.integ = 4; t.unrest = 0.30; t.opening = 25;
				t.timing = true; t.timingP = 0.365; t.broker = true; t.leaderCap = 0.36;
				t.incVillage = 1.7; t.incNoise = 0.05; t.headCost = 4.8;
			}
			case "mirewick" -> {
				t.hp = 1.0; t.dmg = 1.0; t.size = 0.90;
				t.warFreq = 0.5; t.attackRate = 0.40; t.freeRate = 0.08; t.commit = 0.45;
				t.minOdds = 0.9; t.retreat = 0.40; t.noise = 0.60; t.scout = 1;
				t.fortRate = 0.002; t.fortCap = 0.20; t.integ = 10; t.unrest = 0.60; t.delayMin = 1; t.delayMax = 2;
				t.incBase = 14.0; t.incVillage = 2.2; t.incNoise = 0.35; t.headCost = 6.0;
				t.hireMax = 45; t.hireCost = 4.0; t.hirePay = 1.2; t.hireReserve = 150.0; t.hireRetain = 3.0;
				t.desert = 0.5; t.disorg = 0.35; t.miser = 0.20;
				t.standingLoss = 0.5; t.standingGain = 1.25;
			}
			default -> { }
		}
		return t;
	}

	/** Defaults for the id, then the faction file's legacy brain fields, then {@code brain.tuning}. */
	public static Tuning of(Identifier faction, FactionDef def) {
		Tuning t = builtin(faction.getPath());
		if (def == null) return t;
		FactionDef.Brain b = def.army().brain(), d = FactionDef.Brain.DEFAULT;
		if (b.warFrequency() != d.warFrequency()) t.warFreq = b.warFrequency();
		if (b.minAttackOdds() != d.minAttackOdds()) t.minOdds = b.minAttackOdds();
		if (b.intelligenceNoise() != d.intelligenceNoise()) t.noise = b.intelligenceNoise();
		if (b.veterancyCap() != d.veterancyCap()) t.vetCap = b.veterancyCap();
		if (b.supplyLineOnly() != d.supplyLineOnly()) t.supply = b.supplyLineOnly();
		if (b.decisionDelayDays() != d.decisionDelayDays()) {
			t.delayMin = (int) Math.floor(b.decisionDelayDays());
			t.delayMax = (int) Math.ceil(b.decisionDelayDays());
		}
		if (b.hireMercenaries() && t.hireMax <= 0) t.hireMax = 30;
		if (b.mercenaryDailyCost() > 0) t.hirePay = b.mercenaryDailyCost();
		if (b.retreatInsteadOfDie() && t.retreat < 0.5) t.retreat = 0.5;
		t.hp = def.army().healthMultiplier() != 1.0 ? def.army().healthMultiplier() : t.hp;
		t.dmg = def.army().damageMultiplier() != 1.0 ? def.army().damageMultiplier() : t.dmg;
		t.size = def.army().sizeMultiplier() != 1.0 ? def.army().sizeMultiplier() : t.size;
		for (Map.Entry<String, Double> e : b.tuning().entrySet()) t.set(e.getKey(), e.getValue());
		return t;
	}

	/** Sets one named field (snake_case, see {@link #KEYS}); false for an unknown key. */
	public boolean set(String key, double v) {
		switch (key.toLowerCase(Locale.ROOT)) {
			case "hp" -> hp = v;
			case "dmg" -> dmg = v;
			case "size" -> size = v;
			case "war_freq" -> warFreq = v;
			case "attack_rate" -> attackRate = v;
			case "free_rate" -> freeRate = v;
			case "commit" -> commit = v;
			case "min_odds" -> minOdds = v;
			case "retreat" -> retreat = v;
			case "def_retreat" -> defRetreat = v;
			case "noise" -> noise = v;
			case "skirm_odds" -> skirmOdds = v;
			case "scout" -> scout = (int) v;
			case "vet_gain" -> vetGain = v;
			case "vet_cap" -> vetCap = v;
			case "vet_decay" -> vetDecay = v;
			case "supply" -> supply = v != 0;
			case "fort_rate" -> fortRate = v;
			case "fort_cap" -> fortCap = v;
			case "fort_border_only" -> fortBorderOnly = v != 0;
			case "integ" -> integ = v;
			case "unrest" -> unrest = v;
			case "pursuit" -> pursuit = v;
			case "opening" -> opening = (int) v;
			case "delay_min" -> delayMin = (int) v;
			case "delay_max" -> delayMax = (int) v;
			case "timing" -> timing = v != 0;
			case "timing_p" -> timingP = v;
			case "broker" -> broker = v != 0;
			case "leader_cap" -> leaderCap = v;
			case "inc_base" -> incBase = v;
			case "inc_village" -> incVillage = v;
			case "inc_noise" -> incNoise = v;
			case "head_cost" -> headCost = v;
			case "hire_max" -> hireMax = v;
			case "hire_cost" -> hireCost = v;
			case "hire_pay" -> hirePay = v;
			case "hire_reserve" -> hireReserve = v;
			case "hire_retain" -> hireRetain = v;
			case "desert" -> desert = v;
			case "disorg" -> disorg = v;
			case "miser" -> miser = v;
			case "react" -> react = v;
			case "standing_loss" -> standingLoss = v;
			case "standing_gain" -> standingGain = v;
			default -> {
				return false;
			}
		}
		return true;
	}

	public double get(String key) {
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "hp" -> hp; case "dmg" -> dmg; case "size" -> size; case "war_freq" -> warFreq; case "attack_rate" -> attackRate;
			case "free_rate" -> freeRate; case "commit" -> commit; case "min_odds" -> minOdds; case "retreat" -> retreat;
			case "def_retreat" -> defRetreat; case "noise" -> noise; case "skirm_odds" -> skirmOdds; case "scout" -> scout;
			case "vet_gain" -> vetGain; case "vet_cap" -> vetCap; case "vet_decay" -> vetDecay; case "supply" -> supply ? 1 : 0;
			case "fort_rate" -> fortRate; case "fort_cap" -> fortCap; case "fort_border_only" -> fortBorderOnly ? 1 : 0;
			case "integ" -> integ; case "unrest" -> unrest; case "pursuit" -> pursuit; case "opening" -> opening;
			case "delay_min" -> delayMin; case "delay_max" -> delayMax; case "timing" -> timing ? 1 : 0; case "timing_p" -> timingP;
			case "broker" -> broker ? 1 : 0; case "leader_cap" -> leaderCap; case "inc_base" -> incBase; case "inc_village" -> incVillage;
			case "inc_noise" -> incNoise; case "head_cost" -> headCost; case "hire_max" -> hireMax; case "hire_cost" -> hireCost;
			case "hire_pay" -> hirePay; case "hire_reserve" -> hireReserve; case "hire_retain" -> hireRetain; case "desert" -> desert;
			case "disorg" -> disorg; case "miser" -> miser; case "react" -> react; case "standing_loss" -> standingLoss; case "standing_gain" -> standingGain;
			default -> Double.NaN;
		};
	}
}
