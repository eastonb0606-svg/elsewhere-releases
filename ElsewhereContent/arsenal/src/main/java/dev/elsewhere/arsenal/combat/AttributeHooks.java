package dev.elsewhere.arsenal.combat;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.mixin.AbstractArrowAccessor;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/** Behaviour of the generic attributes: dodge_chance, kill_heal, projectile_pierce on arrows, and the caster-piece arrow con. */
public final class AttributeHooks {
	private AttributeHooks() {}

	private static final Map<UUID, Long> LAST_HEAL = new HashMap<>();
	/** For tests: forces the next dodge roll. */
	public static Double forceRoll;

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && dodges(p, source)) {
				ServerLevel l = (ServerLevel) p.level();
				l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.9F, 1.4F);
				l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p) killHeal(p, entity);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && !(entity instanceof ThrownTrident) && arrow.tickCount == 0
				&& arrow.getOwner() instanceof Player p) adjustArrow(arrow, p);
		});
	}

	/** Non-boss mob melee only (never players, bosses, projectiles, explosions, magic). Cap 0.25. */
	public static boolean dodges(ServerPlayer p, DamageSource source) {
		double chance = Math.min(ArsenalStats.DODGE_CAP, ArsenalAttributes.value(p, ArsenalAttributes.DODGE_CHANCE));
		if (chance <= 0) return false;
		Entity attacker = source.getEntity();
		if (!(attacker instanceof Mob) || attacker != source.getDirectEntity() || BossCheck.isBoss(attacker)) return false;
		if (!source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return false;
		double roll = forceRoll != null ? forceRoll : p.getRandom().nextDouble();
		return roll < chance;
	}

	public static boolean killHeal(ServerPlayer p, Entity killed) {
		double heal = ArsenalAttributes.value(p, ArsenalAttributes.KILL_HEAL);
		if (heal <= 0 || !(killed instanceof Enemy) || BossCheck.isBoss(killed) || BossCheck.inBossFight(p)) return false;
		long now = p.level().getGameTime();
		Long last = LAST_HEAL.get(p.getUUID());
		if (last != null && now - last < ArsenalStats.KILL_HEAL_INTERVAL_TICKS) return false;
		LAST_HEAL.put(p.getUUID(), now);
		p.heal((float) heal);
		return true;
	}

	/** projectile_pierce adds to Piercing; each worn caster piece cuts arrow damage by 15%. */
	public static void adjustArrow(AbstractArrow arrow, Player p) {
		int extra = (int) ArsenalAttributes.value(p, ArsenalAttributes.PROJECTILE_PIERCE);
		if (extra > 0) ((AbstractArrowAccessor) arrow).elsewhere$setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + extra));
		int casterPieces = dev.elsewhere.arsenal.Overhaul3.CALLINGS ? 0 : Calling.caster(p);
		float marks = Calling.marksmanMult(p);
		if (marks != 1F) arrow.addTag(Calling.MARK_TAG + marks); // applied at hit time, to non-players only
		if (casterPieces > 0) arrow.setBaseDamage(((AbstractArrowAccessor) arrow).elsewhere$getBaseDamage() * Math.pow(1 + ArsenalStats.CASTER_PIECE_MELEE_ARROW, casterPieces));
	}
}
