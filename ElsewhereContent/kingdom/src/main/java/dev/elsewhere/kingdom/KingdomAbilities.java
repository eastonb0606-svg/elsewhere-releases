package dev.elsewhere.kingdom;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;

/**
 * Support and officer abilities (unit {@code ability}), run from the soldier's server tick:
 * <ul>
 * <li>{@code standard}: every second, same-faction soldiers within 12 blocks get +10% melee damage for 1.5 s.</li>
 * <li>{@code captain}: war cry on engaging (Strength I 10 s to same-faction soldiers within 16 blocks), 30 s cooldown.</li>
 * <li>{@code herald}: once per life when its squad engages, blows the horn and calls reinforcements
 * ({@link KingdomLod#callReinforcements}).</li>
 * <li>{@code surgeon}: every 5 s, heals the most wounded ally within 10 blocks below 60% (officers first) by 4.</li>
 * <li>{@code squire}: every 15 s, patches up an officer within 8 blocks below 50% by 3.</li>
 * </ul>
 */
public final class KingdomAbilities {
	private KingdomAbilities() {}

	public static final double STANDARD_RADIUS = 12, WAR_CRY_RADIUS = 16, SURGEON_RADIUS = 10, SQUIRE_RADIUS = 8;
	public static final int WAR_CRY_TICKS = 200, WAR_CRY_COOLDOWN = 600, SURGEON_COOLDOWN = 100, SQUIRE_COOLDOWN = 300;
	public static final float SURGEON_HEAL = 4F, SQUIRE_HEAL = 3F;

	static List<KingdomSoldier> allies(ServerLevel level, KingdomSoldier s, double r) {
		return level.getEntitiesOfClass(KingdomSoldier.class, new AABB(s.blockPosition()).inflate(r),
			o -> o.isAlive() && !Factions.hostile(s.factionId(), o.factionId()) && o.distanceToSqr(s) <= r * r);
	}

	static void tick(ServerLevel level, KingdomSoldier s) {
		switch (s.unit().ability()) {
			case STANDARD -> {
				if ((s.tickCount + s.phase) % 20 != 0) return;
				long until = level.getGameTime() + 30;
				for (KingdomSoldier o : allies(level, s, STANDARD_RADIUS)) o.standardBuffUntil = until;
			}
			case CAPTAIN -> {
				if (s.abilityCooldown > 0 || s.getTarget() == null || !s.getTarget().isAlive() || s.distanceToSqr(s.getTarget()) > 20 * 20) return;
				warCry(level, s);
			}
			case HERALD -> {
				if (s.hornUsed || s.getTarget() == null || (s.tickCount + s.phase) % 10 != 0) return;
				Squad sq = s.squad();
				if (sq != null && sq.enemies().isEmpty()) return;
				blowHorn(level, s);
			}
			case SURGEON -> {
				if (s.abilityCooldown > 0 || (s.tickCount + s.phase) % 10 != 0) return;
				if (heal(level, s, SURGEON_RADIUS, 0.6F, SURGEON_HEAL, false)) s.abilityCooldown = SURGEON_COOLDOWN;
			}
			case SQUIRE -> {
				if (s.abilityCooldown > 0 || (s.tickCount + s.phase) % 10 != 0) return;
				if (heal(level, s, SQUIRE_RADIUS, 0.5F, SQUIRE_HEAL, true)) s.abilityCooldown = SQUIRE_COOLDOWN;
			}
			default -> {
			}
		}
	}

	/** Captain's war cry: Strength I for 10 s to same-faction soldiers within 16 blocks (self included). */
	public static int warCry(ServerLevel level, KingdomSoldier s) {
		s.abilityCooldown = WAR_CRY_COOLDOWN;
		List<KingdomSoldier> near = allies(level, s, WAR_CRY_RADIUS);
		for (KingdomSoldier o : near) o.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_CRY_TICKS, 0), s);
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.VINDICATOR_CELEBRATE, s.getSoundSource(), 2.0F, 0.8F);
		s.swingForAttack(InteractionHand.MAIN_HAND);
		KingdomSoldier.totalWarCries++;
		return near.size();
	}

	/** Herald's horn: once per life; calls reinforcements and fires {@link KingdomEvents#HORN_BLOWN}. */
	public static void blowHorn(ServerLevel level, KingdomSoldier s) {
		s.hornUsed = true;
		KingdomSoldier.totalHorns++;
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.RAID_HORN.value(), s.getSoundSource(), 24.0F, 1.0F);
		s.swingForAttack(InteractionHand.OFF_HAND);
		int called = KingdomLod.callReinforcements(level, s);
		KingdomEvents.HORN_BLOWN.invoker().onHorn(level, s, called);
	}

	private static boolean heal(ServerLevel level, KingdomSoldier s, double r, float below, float amount, boolean officersOnly) {
		KingdomSoldier best = null;
		float bestScore = Float.MAX_VALUE;
		for (KingdomSoldier o : allies(level, s, r)) {
			if (officersOnly && !o.isOfficer()) continue;
			float ratio = o.getHealth() / o.getMaxHealth();
			if (ratio >= below) continue;
			float score = ratio - (o.isOfficer() ? 0.5F : 0F);
			if (score < bestScore) {
				bestScore = score;
				best = o;
			}
		}
		if (best == null) return false;
		best.heal(amount);
		KingdomSoldier.totalHeals++;
		s.getLookControl().setLookAt(best, 30F, 30F);
		s.swingForAttack(InteractionHand.OFF_HAND);
		if (officersOnly) level.sendParticles(ParticleTypes.HEART, best.getX(), best.getY(1.0), best.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		else {
			level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.SPLASH_POTION_THROW, s.getSoundSource(), 0.6F, 1.0F);
			level.levelEvent(LevelEvent.PARTICLES_INSTANT_POTION_SPLASH, best.blockPosition(), 0xF82423);
		}
		return true;
	}
}
