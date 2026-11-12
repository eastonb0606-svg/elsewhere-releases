package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/**
 * Starsight pair (Glasswaste, Marksman + Caster; GEAR.md A5): Starsight Visor + Prism Mantle. An arrow hit refunds
 * 1 Glimmer x H (once per arrow, so a piercing arrow pays once) and marks the target for 4 s; your spells deal +10% x H to a
 * target you have marked. Marks are kept per (target, archer) and expire on their own (checked when read; no timer, no scan).
 * Nothing on players, pets, minions, villagers or armor stands. Kill switch {@code setbonus_starsight}.
 */
public final class StarsightPair {
	private StarsightPair() {}

	public static final float REFUND = 1.0F, SPELL_BONUS = 0.10F;
	public static final int MARK_TICKS = 80;
	/** An arrow that already refunded Glimmer (piercing arrows refund once). */
	public static final String PAID_TAG = "elsewhere_g3_starsight_paid";
	public static SetBonus PAIR;

	private record Key(UUID target, UUID archer) {}

	private static final Map<Key, Long> MARKS = new HashMap<>();

	static void init() {
		PAIR = SetBonuses.register(SetBonus.of("starsight")
			.piece(EquipmentSlot.HEAD, "glasswaste_starsight_visor")
			.piece(EquipmentSlot.CHEST, "glasswaste_prism_mantle")
			.hybrid(Calling.Path.MARKSMAN, Calling.Path.CASTER)
			.details(2));
		SetBonuses.AFTER_HIT.add(StarsightPair::afterHit);
		Spells.DAMAGE_MULT.add(StarsightPair::spellMult);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> MARKS.clear());
	}

	/** SetBonuses.AFTER_HIT: an arrow from a wearer landed. */
	public static void afterHit(LivingEntity victim, DamageSource src, float taken) {
		if (!(src.getDirectEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident || !(src.getEntity() instanceof ServerPlayer p)) return;
		float pw = SetBonuses.power(p, PAIR);
		if (pw <= 0F || !SetBonusesB.target(p, victim)) return;
		mark(victim, p);
		if (!arrow.entityTags().contains(PAID_TAG)) {
			arrow.addTag(PAID_TAG);
			Glimmer.restore(p, REFUND * pw);
		}
	}

	/** Marks {@code target} for {@code archer}'s spells for 4 s. */
	public static void mark(LivingEntity target, ServerPlayer archer) {
		long now = archer.level().getGameTime();
		if (MARKS.size() > 64) MARKS.values().removeIf(until -> until < now);
		MARKS.put(new Key(target.getUUID(), archer.getUUID()), now + MARK_TICKS);
	}

	public static boolean marked(LivingEntity target, ServerPlayer archer) {
		Key k = new Key(target.getUUID(), archer.getUUID());
		Long until = MARKS.get(k);
		if (until == null) return false;
		if (until < archer.level().getGameTime()) {
			MARKS.remove(k);
			return false;
		}
		return true;
	}

	/** Spells.DAMAGE_MULT: +10% x H on a target this caster marked with an arrow. */
	public static double spellMult(ServerPlayer owner, LivingEntity target) {
		if (target == null || MARKS.isEmpty()) return 1.0;
		float pw = SetBonuses.power(owner, PAIR);
		return pw > 0F && SetBonusesB.target(owner, target) && marked(target, owner) ? 1.0 + SPELL_BONUS * pw : 1.0;
	}
}
