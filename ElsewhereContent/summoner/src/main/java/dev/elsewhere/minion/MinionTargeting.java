package dev.elsewhere.minion;

import dev.elsewhere.summoner.GearStats;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Target rules of ARSENAL.md §5a plus the user's 20:35 / 20:40 decisions:
 * <ul>
 * <li><b>Automatic</b> targeting is for hostile mobs only ({@link #isHostile}): what the owner hit, what hurt the owner,
 * hostiles targeting the owner or a minion.</li>
 * <li>Passive and neutral mobs are never picked automatically (not even when the owner hits them), except
 * <b>self-defence</b>: a mob that is attacking the owner or one of the owner's minions right now.</li>
 * <li><b>Order:</b> a target the owner marked Herded with the Herding Crook may be attacked whatever it is (players,
 * other people's pets, villagers, golems, passive and neutral mobs) until the mark ends or is called off, except
 * {@link #neverTarget}: the owner, the owner's own pets, any minion, armor stands, and players the owner can't harm
 * (server {@code pvp} off, or a teammate with friendly fire off).</li>
 * </ul>
 */
public final class MinionTargeting {
	private MinionTargeting() {}

	public enum Reason { OWNER_HIT, HURT_OWNER, AGGRO, KEEP }

	/** Hostile = an {@code Enemy} that isn't a neutral mob (endermen, zombified piglins, piglins count as neutral). */
	public static boolean isHostile(Entity e) {
		return e instanceof Enemy && !(e instanceof NeutralMob) && !(e instanceof AbstractPiglin);
	}

	/** Never, not even by a Crook order. */
	public static boolean neverTarget(@Nullable ServerPlayer owner, Entity e) {
		if (e instanceof MinionEntity) return true;
		if (e instanceof ArmorStand) return true;
		if (owner == null) return true;
		if (e == owner) return true;
		if (e instanceof OwnableEntity o) {
			EntityReference<LivingEntity> ref = o.getOwnerReference();
			if (ref != null && owner.getUUID().equals(ref.getUUID())) return true; // the owner's own pets
		}
		if (e instanceof Player p) return p.isSpectator() || p.isCreative() || !owner.canHarmPlayer(p);
		return false;
	}

	/** Entities minions never pick or damage on their own (only by a Crook order). */
	public static boolean isProtected(@Nullable ServerPlayer owner, Entity e) {
		if (e instanceof Player) return true;
		if (e instanceof MinionEntity) return true;
		if (owner != null && e == owner) return true;
		if (e instanceof TamableAnimal t && t.isTame()) return true;
		if (e instanceof OwnableEntity o && !(e instanceof Enemy) && o.getOwnerReference() != null) return true;
		if (e instanceof AbstractVillager) return true;
		if (e instanceof AbstractGolem) return true;
		if (e instanceof ArmorStand) return true;
		return false;
	}

	/** The owner's Crook order is on this entity and it may be ordered. */
	public static boolean ordered(@Nullable ServerPlayer owner, Entity e) {
		return owner != null && e instanceof LivingEntity le && Herding.isHerdedBy(owner, le) && !neverTarget(owner, e);
	}

	/** Damage guard for every minion hit. */
	public static boolean mayDamage(@Nullable ServerPlayer owner, Entity victim) {
		if (ordered(owner, victim)) return true;
		return !isProtected(owner, victim);
	}

	/** Self-defence: the mob is attacking the owner or one of the owner's minions right now. */
	public static boolean attackingUs(ServerPlayer owner, LivingEntity t) {
		if (!(t instanceof Mob m)) return false;
		LivingEntity tt = m.getTarget();
		return tt == owner || (tt instanceof MinionEntity me && me.isOwnedBy(owner));
	}

	public static boolean isValidTarget(ServerPlayer owner, MinionEntity minion, @Nullable LivingEntity t, Reason why) {
		if (t == null || !t.isAlive() || t.isRemoved() || t.level() != minion.level()) return false;
		if (t.isInvulnerable()) return false;
		if (ordered(owner, t)) return true;
		if (isProtected(owner, t)) return false;
		if (isHostile(t)) return true;
		return attackingUs(owner, t); // passive / neutral: self-defence only
	}

	/** Picks a target: Herded order, what the owner hit (5 s), what hurt the owner (5 s), nearest mob attacking the owner or a minion (10 blocks). */
	public static @Nullable LivingEntity pick(ServerPlayer owner, MinionEntity minion) {
		LivingEntity herded = Herding.target(owner);
		if (herded != null && herded.distanceTo(owner) <= GearStats.TARGET_RANGE && isValidTarget(owner, minion, herded, Reason.OWNER_HIT)) return herded;
		LivingEntity ribboned = dev.elsewhere.summoner.GaleRibbonItem.target(owner);
		if (ribboned != null && ribboned.distanceTo(owner) <= GearStats.TARGET_RANGE && isValidTarget(owner, minion, ribboned, Reason.OWNER_HIT)) return ribboned;
		int now = owner.tickCount;
		LivingEntity hit = owner.getLastHurtMob();
		if (hit != null && now - owner.getLastHurtMobTimestamp() <= GearStats.TARGET_MEMORY_TICKS
			&& hit.distanceTo(owner) <= GearStats.TARGET_RANGE && isValidTarget(owner, minion, hit, Reason.OWNER_HIT)) return hit;
		LivingEntity by = owner.getLastHurtByMob();
		if (by != null && now - owner.getLastHurtByMobTimestamp() <= GearStats.TARGET_MEMORY_TICKS
			&& by.distanceTo(owner) <= GearStats.TARGET_RANGE && isValidTarget(owner, minion, by, Reason.HURT_OWNER)) return by;
		double r = GearStats.AGGRO_SCAN_RANGE;
		List<Mob> near = owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(r),
			m -> attackingUs(owner, m) && m.distanceTo(owner) <= r && isValidTarget(owner, minion, m, Reason.AGGRO));
		Mob best = null;
		double bd = Double.MAX_VALUE;
		for (Mob m : near) {
			double d = m.distanceToSqr(owner);
			if (d < bd) {
				bd = d;
				best = m;
			}
		}
		return best;
	}
}
