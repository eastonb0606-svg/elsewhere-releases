package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Glyphmail pair (Azmar hood + hauberk, Vanguard + Caster), pair bonus (GEAR.md A5, Spellblade): your melee hits make
 * 1 Glimmer (at most once per 0.5 s) and spells cast within 3 s of a melee hit cost 30% less; both x H(vanguard, caster).
 * The last-hit time is stored on the hit; the cost hook reads it when a spell is priced. Never from hits on players.
 */
public final class GlyphmailPair {
	private GlyphmailPair() {}

	public static final float GLIMMER_PER_HIT = 1F, COST_CUT = 0.30F;
	public static final int GAIN_COOLDOWN = 10, WINDOW = 60;
	public static SetBonus BONUS;
	/** player -> {last Glimmer gain, last melee hit} (game time) */
	private static final Map<UUID, long[]> STATE = new HashMap<>();

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("azmar_glyphmail")
			.piece(EquipmentSlot.HEAD, "azmar_glyphmail_hood").piece(EquipmentSlot.CHEST, "azmar_glyphmail_hauberk")
			.hybrid(Calling.Path.VANGUARD, Calling.Path.CASTER).details(3)
			.onChange((p, on) -> {
				if (!on) STATE.remove(p.getUUID());
			}));
		SetBonuses.AFTER_HIT.add(GlyphmailPair::hit);
		SetBonuses.SPELL_COST.add(GlyphmailPair::costMult);
	}

	public static void hit(LivingEntity victim, DamageSource src, float taken) {
		ServerPlayer p = SetBonusesA.meleeBy(victim, src);
		if (p == null) return;
		float pw = SetBonuses.power(p, BONUS);
		if (pw <= 0F) return;
		long now = p.level().getGameTime();
		long[] s = STATE.computeIfAbsent(p.getUUID(), u -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
		s[1] = now;
		if (now - s[0] >= GAIN_COOLDOWN || now < s[0]) {
			s[0] = now;
			Glimmer.restore(p, GLIMMER_PER_HIT * pw);
		}
	}

	/** Spell cost multiplier: 1 - 0.30 x power within 3 s of your last melee hit, else 1. */
	public static float costMult(ServerPlayer p) {
		long[] s = STATE.get(p.getUUID());
		if (s == null) return 1F;
		long since = p.level().getGameTime() - s[1];
		if (since < 0 || since > WINDOW) return 1F;
		float pw = SetBonuses.power(p, BONUS);
		return pw > 0F ? Math.max(0F, 1F - COST_CUT * pw) : 1F;
	}

	/** Self-test: forget the hit times. */
	public static void clear() {
		STATE.clear();
	}
}
