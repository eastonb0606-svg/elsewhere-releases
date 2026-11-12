package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Outrider pair (Aldermark, Vanguard + Marksman; GEAR.md A5): Outrider Chaps + Outrider Boots. Melee hits made while
 * sprinting, and arrows loosed while sprinting, deal +10% x H (the pair's hybrid strength). The arrow's bonus is fixed when
 * it is loosed (an arrow tag) and applied when it hits. Never against players. Kill switch {@code setbonus_outrider}.
 */
public final class OutriderPair {
	private OutriderPair() {}

	public static final float BONUS = 0.10F;
	/** Arrow tag: the multiplier fixed when a sprinting wearer loosed it. */
	public static final String TAG = "elsewhere_g3_outrider:";
	public static SetBonus PAIR;

	static void init() {
		PAIR = SetBonuses.register(SetBonus.of("outrider")
			.piece(EquipmentSlot.LEGS, "aldermark_outrider_chaps")
			.piece(EquipmentSlot.FEET, "aldermark_outrider_boots")
			.hybrid(Calling.Path.VANGUARD, Calling.Path.MARKSMAN)
			.details(2));
		SetBonuses.DAMAGE.add(OutriderPair::damage);
	}

	/** Damage multiplier at bonus strength {@code power} (H for the pair, x1.5 when Ascendant): 1 + 0.10 x power. */
	public static float mult(float power) {
		return 1F + BONUS * Math.max(0F, power);
	}

	/** SetBonuses.DAMAGE: a sprint melee hit by a wearer, or an arrow a wearer loosed while sprinting. */
	public static float damage(LivingEntity victim, DamageSource src, float amount) {
		if (victim instanceof Player) return amount;
		Entity direct = src.getDirectEntity();
		if (direct instanceof ServerPlayer p && src.getEntity() == p && src.is(DamageTypes.PLAYER_ATTACK)) {
			// Player.attack reads the sprint flag before the hit and clears it only after the knockback, so it is still set here
			if (!p.isSprinting()) return amount;
			float pw = SetBonuses.power(p, PAIR);
			return pw > 0F ? amount * mult(pw) : amount;
		}
		if (direct instanceof AbstractArrow arrow) {
			float m = SetBonusesB.tagValue(arrow, TAG);
			if (m > 0F) return amount * m;
		}
		return amount;
	}

	/** An arrow loosed by {@code p}: tagged with the multiplier when {@code p} is sprinting and wears the pair. */
	static void onLoose(ServerPlayer p, AbstractArrow arrow) {
		if (!p.isSprinting() || SetBonusesB.tagValue(arrow, TAG) > 0F) return;
		float pw = SetBonuses.power(p, PAIR);
		if (pw > 0F) arrow.addTag(TAG + mult(pw));
	}
}
