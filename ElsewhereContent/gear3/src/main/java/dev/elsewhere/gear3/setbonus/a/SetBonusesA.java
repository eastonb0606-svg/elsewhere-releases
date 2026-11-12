package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.gear3.setbonus.SetBonuses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * s-setbonus-a: the first seven gear3 set and pair bonuses (GEAR.md A5, alphabetical by set): Elderbark (Greater Bond),
 * Falconer pair, Galeplate (Single Combat), Glyphmail pair, Hearthguard (Hold the Line), Hird pair, Lamplighter pair.
 * One init call from Gear3Content.init(). Kill switches: {@code setbonus_<id>} and {@code set_bonuses}.
 */
public final class SetBonusesA {
	private SetBonusesA() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		SetBonuses.init();
		GreaterBond.init();
		FalconerPair.init();
		SingleCombat.init();
		GlyphmailPair.init();
		HoldTheLine.init();
		HirdPair.init();
		LamplighterPair.init();
	}

	/** A player's own melee swing landing on {@code victim} (not a player: set bonuses stay out of PvP). */
	static ServerPlayer meleeBy(LivingEntity victim, DamageSource src) {
		if (victim instanceof Player) return null;
		Entity by = src.getEntity();
		if (by instanceof ServerPlayer p && src.getDirectEntity() == p && src.is(DamageTypes.PLAYER_ATTACK) && victim != p) return p;
		return null;
	}
}
