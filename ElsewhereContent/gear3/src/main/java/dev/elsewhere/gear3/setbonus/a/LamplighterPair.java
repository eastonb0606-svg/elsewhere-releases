package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lamplighter pair (Mirewick hood + coat, Caster + Summoner), pair bonus (GEAR.md A5, Ritualist): +6% spell power per
 * minion you have out (max +24%), and every spell you cast refreshes your minions' battle timers and heals each 2 HP;
 * all x H(caster, summoner). Uses arsenal's spell hooks: Spells.DAMAGE_MULT (read when a spell hits) and Spells.ON_CAST.
 */
public final class LamplighterPair {
	private LamplighterPair() {}

	public static final float PER_MINION = 0.06F, MAX_BONUS = 0.24F, HEAL = 2F;
	public static SetBonus BONUS;

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("mirewick_lamplighter")
			.piece(EquipmentSlot.HEAD, "mirewick_lamplighter_hood").piece(EquipmentSlot.CHEST, "mirewick_lamplighter_coat")
			.hybrid(Calling.Path.CASTER, Calling.Path.SUMMONER).details(3));
		Spells.DAMAGE_MULT.add(LamplighterPair::spellMult);
		Spells.ON_CAST.add((p, item) -> onCast(p));
	}

	/** Spell damage multiplier: 1 + min(24%, 6% x minions out) x power. */
	public static double spellMult(ServerPlayer owner, LivingEntity target) {
		if (!SetBonuses.anyone()) return 1.0;
		float pw = SetBonuses.power(owner, BONUS);
		if (pw <= 0F) return 1.0;
		int n = MinionManager.count(owner);
		return 1.0 + Math.min(MAX_BONUS, PER_MINION * n) * pw;
	}

	/** After a cast: minions' timers to at least power x full, heal 2 x power each. */
	public static int onCast(ServerPlayer p) {
		if (!SetBonuses.anyone()) return 0;
		float pw = SetBonuses.power(p, BONUS);
		if (pw <= 0F) return 0;
		int n = SetBonuses.refreshMinions(p, pw, HEAL * pw);
		if (n > 0 && p.level() instanceof ServerLevel sl)
			for (var m : MinionManager.minions(p)) sl.sendParticles(ParticleTypes.SMALL_FLAME, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
		return n;
	}
}
