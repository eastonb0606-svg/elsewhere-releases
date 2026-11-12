package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.SummonerPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hird pair (Hrafnholt Hird leggings + boots, Vanguard + Summoner), pair bonus (GEAR.md A5, Warband): Herd focus (your own
 * -10% damage per minion beyond the first) is halved, and your melee hits refresh your minions' battle timers; both
 * x H(vanguard, summoner) (at H below 1 the timers refresh to H of full). Herd focus is scaled where the summoner module
 * computes it (SetBonusMixins.Herd), so its own modifier refresh picks it up; the timer refresh runs on a landed melee hit.
 */
public final class HirdPair {
	private HirdPair() {}

	public static final double FOCUS_CUT = 0.5;
	public static SetBonus BONUS;

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("hrafnholt_hird")
			.piece(EquipmentSlot.LEGS, "hrafnholt_hird_leggings").piece(EquipmentSlot.FEET, "hrafnholt_hird_boots")
			.hybrid(Calling.Path.VANGUARD, Calling.Path.SUMMONER).details(3)
			.onChange((p, on) -> SummonerPerks.updateHerdFocus(p)));
		SetBonuses.AFTER_HIT.add(HirdPair::hit);
	}

	/** SummonerPerks.herdFocus return value (a negative fraction): halved x power while the pair is on. */
	public static double herdFocus(Player p, double original) {
		if (original == 0.0 || BONUS == null || !(p instanceof ServerPlayer)) return original;
		float pw = SetBonuses.power(p, BONUS);
		return pw > 0F ? original * Math.max(0.0, 1.0 - FOCUS_CUT * pw) : original;
	}

	static void hit(LivingEntity victim, DamageSource src, float taken) {
		ServerPlayer p = SetBonusesA.meleeBy(victim, src);
		if (p == null || victim instanceof MinionEntity) return;
		float pw = SetBonuses.power(p, BONUS);
		if (pw > 0F) SetBonuses.refreshMinions(p, pw, 0F);
	}
}
