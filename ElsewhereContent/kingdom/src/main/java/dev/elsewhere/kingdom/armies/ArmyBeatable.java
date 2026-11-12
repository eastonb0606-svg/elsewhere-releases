package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "A full group must be killable by a skilled player with weak diamond enchants" (RIVALS-DECISIONS 19, item 12). A duel calculator
 * fed with the real attributes of spawned soldiers (health, armor, toughness, melee damage including the weapon and active effects such
 * as a Berserker's Winter Rage) and, where the armies self-test measured it, the damage each unit type really deals per second to the
 * same weak-diamond kit ({@link ArmiesBeatableRig}: fire pots, bolts, shield shoves and crits included). Run by the armies self-test
 * for full basic squads of every kingdom with attack tokens on.
 * <p>The skilled player: full diamond armor (20 armor, 8 toughness) with Protection I on every piece (EPF 4), a diamond sword with
 * Sharpness I (8 damage), 1.6 swings a second, half of them critical, 70% of the time on target; a shield that takes half of the melee
 * damage; knockback and footwork keep each token holder swinging 60% of the time; 20 health, three golden apples (4 absorption + 4
 * regeneration each) and 0.5 health a second of natural regeneration. Melee soldiers swing once a second (or deal what was measured,
 * whichever is more); shooters hit for 4 every 1.5 s (or what was measured, whichever is more), 70% of shots landing. Only
 * {@code attack_tokens} melee and ranged soldiers fight at once; <b>companions</b> (a Wolf-Handler's hounds: not soldiers, so not held by
 * tokens) always fight. The player kills the companions first, then the weakest melee soldier, then the shooters. A soldier with a
 * shield blocks half of its {@code block_chance} of the player's hits. A weapon that disables shields (axes: {@code
 * disable_blocking_for_seconds}) takes the player's shield down after each blocked hit, so the shield is up only
 * {@code 1 / (1 + sum(seconds x hits per second))} of the time against them.
 */
public final class ArmyBeatable {
	private ArmyBeatable() {}

	/**
	 * One fighter. {@code disableSecs}: how long a blocked hit of its weapon disables the player's shield (0 for swords and spears).
	 * {@code measuredDps}: damage per second it dealt to the weak-diamond dummy at full uptime (after armor and Protection), or -1.
	 */
	public record Unit(Identifier id, double health, double armor, double toughness, double melee, boolean ranged, double block, double disableSecs, double measuredDps) {
		static Unit of(KingdomSoldier s) {
			boolean shield = s.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD);
			return new Unit(s.unitId(), s.getMaxHealth(), s.getAttributeValue(Attributes.ARMOR), s.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
				s.getAttributeValue(Attributes.ATTACK_DAMAGE), s.unit().role().ranged(), shield ? s.unit().combat().blockChance() : 0, s.getSecondsToDisableBlocking(), -1);
		}

		/** A companion (not a soldier): its own health, armor and bite; it never blocks. */
		static Unit companion(LivingEntity e) {
			double melee = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
			return new Unit(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), e.getMaxHealth(), e.getAttributeValue(Attributes.ARMOR),
				e.getAttributeValue(Attributes.ARMOR_TOUGHNESS), melee, false, 0, e.getSecondsToDisableBlocking(), -1);
		}

		Unit withDps(double dps) {
			return new Unit(id, health, armor, toughness, melee, ranged, block, disableSecs, dps);
		}
	}

	/** {@code killed} counts soldiers and companions; {@code squad} is the soldiers, {@code companions} the companions. */
	public record Result(boolean playerWins, double playerHealthLeft, double seconds, int killed, int squad, int companions) {}

	static final double PLAYER_ARMOR = 20, PLAYER_TOUGH = 8, PLAYER_EPF = 4, SWORD = 8, SWINGS = 1.6, CRIT_SHARE = 0.5, UPTIME = 0.7, SHIELD = 0.5, MELEE_UPTIME = 0.6,
		HEALTH = 20 + 3 * 8, REGEN = 0.5, ARROW = 4, ARROW_EVERY = 1.5, ARROW_HIT = 0.7;

	/** Vanilla armor then Protection. */
	static double afterArmor(double dmg, double armor, double tough, double epf) {
		double f = Math.clamp(armor - dmg / (2 + tough / 4), armor / 5, 20);
		return dmg * (1 - f / 25) * (1 - Math.min(20, epf) / 25);
	}

	/** Damage per second a melee fighter deals to the player at full uptime: one swing a second, or what it was measured to deal. */
	static double meleeDps(Unit u) {
		return Math.max(afterArmor(u.melee, PLAYER_ARMOR, PLAYER_TOUGH, PLAYER_EPF), u.measuredDps);
	}

	/** Damage per second a shooter deals to the player when every shot lands: a 4-damage arrow every 1.5 s, or what it was measured to deal. */
	static double rangedDps(Unit u) {
		return Math.max(afterArmor(ARROW, PLAYER_ARMOR, PLAYER_TOUGH, PLAYER_EPF) / ARROW_EVERY, u.measuredDps);
	}

	public static Result duel(List<Unit> squad, int meleeTokens, int rangedTokens) {
		return duel(squad, List.of(), meleeTokens, rangedTokens);
	}

	public static Result duel(List<Unit> squad, List<Unit> companions, int meleeTokens, int rangedTokens) {
		List<Unit> comp = new ArrayList<>(companions), melee = new ArrayList<>(), ranged = new ArrayList<>();
		for (Unit u : squad) (u.ranged ? ranged : melee).add(u);
		comp.sort(Comparator.comparingDouble(Unit::health));
		melee.sort(Comparator.comparingDouble(Unit::health));
		double hp = HEALTH, t = 0, dt = 0.05, cur = -1;
		Unit target = null;
		int killed = 0;
		double perHit = SWORD * (1 + 0.5 * CRIT_SHARE), rate = SWINGS * UPTIME;
		while (hp > 0 && (!comp.isEmpty() || !melee.isEmpty() || !ranged.isEmpty()) && t < 600) {
			if (target == null) {
				target = !comp.isEmpty() ? comp.get(0) : !melee.isEmpty() ? melee.get(0) : ranged.get(0);
				cur = target.health;
			}
			cur -= afterArmor(perHit, target.armor, target.toughness, 0) * rate * (1 - 0.5 * target.block) * dt; // shield-bearers block half their block chance (a skilled player swings around the rest)
			if (cur <= 0) {
				if (!comp.remove(target) && !melee.remove(target)) ranged.remove(target);
				target = null;
				killed++;
			}
			int nm = Math.min(meleeTokens, melee.size()), nr = Math.min(rangedTokens, ranged.size());
			// axes: each blocked hit takes the player's shield down for disableSecs; hits arrive at MELEE_UPTIME per second per attacker
			double down = 0;
			for (int i = 0; i < nm; i++) down += melee.get(i).disableSecs * MELEE_UPTIME;
			for (Unit c : comp) down += c.disableSecs * MELEE_UPTIME;
			double shield = SHIELD / (1 + down);
			double in = 0;
			for (int i = 0; i < nm; i++) in += meleeDps(melee.get(i)) * (1 - shield) * MELEE_UPTIME;
			for (Unit c : comp) in += meleeDps(c) * (1 - shield) * MELEE_UPTIME;
			for (int i = 0; i < nr; i++) in += rangedDps(ranged.get(i)) * ARROW_HIT;
			hp += (REGEN - in) * dt;
			hp = Math.min(hp, HEALTH);
			t += dt;
		}
		return new Result(hp > 0 && comp.isEmpty() && melee.isEmpty() && ranged.isEmpty(), Math.max(0, hp), t, killed, squad.size(), companions.size());
	}
}
