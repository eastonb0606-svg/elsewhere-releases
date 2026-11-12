package dev.elsewhere.minion;

import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.summoner.GearStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server-side bookkeeping: which minions each player has out, oldest first; slot limits; despawn rules. */
public final class MinionManager {
	private MinionManager() {}

	private static final Map<UUID, List<MinionEntity>> BY_OWNER = new HashMap<>();
	private static long order;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ServerPlayerEvents.LEAVE.register(p -> dismissAll(p.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (e instanceof ServerPlayer p) dismissAll(p.getUUID());
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p, from, to) -> dismissAll(p.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> dismissAll(oldP.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> BY_OWNER.clear());
		// minion attacks never damage protected entities (covers anything a subclass does)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (source.getDirectEntity() instanceof MinionEntity m) {
				ServerPlayer owner = m.ownerPlayer();
				return MinionTargeting.mayDamage(owner, victim);
			}
			return true;
		});
		// battle-call rule: trading damage with a hostile mob resets the fervor timer
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			onCombat(victim, source);
			return true;
		});
		// owner sleeps: minions go home
		EntitySleepEvents.START_SLEEPING.register((e, pos) -> {
			if (e instanceof ServerPlayer p) dismissAll(p.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) enforceSlots(p);
		});
	}

	/** A hostile mob for the fervor timer: any Enemy, or a mob currently fighting this owner or one of their minions. */
	static boolean hostile(Entity e, @Nullable ServerPlayer owner) {
		if (e instanceof net.minecraft.world.entity.monster.Enemy) return true;
		if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
			LivingEntity t = mob.getTarget();
			return t == owner || (t instanceof MinionEntity me && owner != null && me.isOwnedBy(owner));
		}
		return false;
	}

	/** Resets fervor: the minion's own when it trades damage, every minion of an owner who trades damage. */
	public static void onCombat(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity by = source.getEntity();
		if (victim instanceof MinionEntity m) {
			if (by != null && hostile(by, m.ownerPlayer())) resetFervor(m);
			return;
		}
		if (direct instanceof MinionEntity m) {
			if (hostile(victim, m.ownerPlayer())) resetFervor(m);
			return;
		}
		if (victim instanceof ServerPlayer p && by != null && hostile(by, p)) refresh(p);
		if (by instanceof ServerPlayer p && hostile(victim, p)) refresh(p);
	}

	public static void refresh(ServerPlayer owner) {
		for (MinionEntity m : minions(owner)) resetFervor(m);
	}

	static void resetFervor(MinionEntity m) {
		ServerPlayer o = m.ownerPlayer();
		m.fervorTicks = o == null ? GearStats.FERVOR_TICKS : MinionTuning.fervorTicks(o, m);
	}

	static void register(ServerPlayer owner, MinionEntity m) {
		m.summonOrder = ++order;
		BY_OWNER.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(m);
	}

	static void unregister(MinionEntity m) {
		UUID u = m.getOwnerUUID();
		if (u == null) return;
		List<MinionEntity> l = BY_OWNER.get(u);
		if (l != null) {
			l.remove(m);
			if (l.isEmpty()) BY_OWNER.remove(u);
		}
	}

	public static List<MinionEntity> minions(ServerPlayer owner) {
		List<MinionEntity> l = BY_OWNER.get(owner.getUUID());
		if (l == null) return List.of();
		l.removeIf(m -> m.isRemoved() || !m.isAlive());
		return List.copyOf(l);
	}

	public static int count(ServerPlayer owner) {
		return minions(owner).size();
	}

	static int indexOf(ServerPlayer owner, MinionEntity m) {
		List<MinionEntity> l = BY_OWNER.get(owner.getUUID());
		return l == null ? 0 : Math.max(0, l.indexOf(m));
	}

	/**
	 * Summons a minion of {@code type} next to the owner: spends the Glimmer cost, then removes the oldest minions
	 * while over the slot limit. Returns the new minion, or null (not enough Glimmer, or 0 slots).
	 */
	public static @Nullable MinionEntity summon(ServerPlayer owner, EntityType<? extends MinionEntity> type, int glimmerCost) {
		ServerLevel level = owner.level();
		if (MinionApi.maxSlots(owner) <= 0) {
			if (dev.elsewhere.summoner.SummonerConfig.maxMinionsPerPlayer() <= 0) owner.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.minions_disabled"));
			return null;
		}
		if (!GlimmerStub.trySpend(owner, glimmerCost)) {
			level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.6F);
			return null;
		}
		MinionEntity m = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (m == null) return null;
		m.setOwner(owner);
		m.fervorTicks = MinionTuning.fervorTicks(owner, m);
		Vec3 look = owner.getLookAngle();
		Vec3 at = owner.position().add(look.x * 1.5, 0, look.z * 1.5);
		m.snapTo(at.x, owner.getY(), at.z, owner.getYRot(), 0);
		m.teleportToOwner(owner);
		level.addFreshEntity(m);
		register(owner, m);
		enforceSlots(owner);
		dev.elsewhere.summoner.SummonerPerks.updateHerdFocus(owner);
		level.playSound(null, m.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		return m;
	}

	/** Removes the oldest minions until the owner is within their slots (no refund). */
	public static void enforceSlots(ServerPlayer owner) {
		List<MinionEntity> l = BY_OWNER.get(owner.getUUID());
		if (l == null) return;
		l.removeIf(m -> m.isRemoved() || !m.isAlive());
		int max = MinionApi.maxSlots(owner);
		while (l.size() > max) {
			MinionEntity oldest = l.removeFirst();
			poof(oldest);
		}
	}

	public static int dismissType(ServerPlayer owner, EntityType<?> type) {
		int n = 0;
		for (MinionEntity m : minions(owner)) {
			if (m.getType() == type) {
				poof(m);
				n++;
			}
		}
		return n;
	}

	/** Removes one minion (expired, idle or unpaid upkeep). */
	public static void dismiss(MinionEntity m) {
		poof(m);
	}

	public static void dismissAll(UUID owner) {
		List<MinionEntity> l = BY_OWNER.remove(owner);
		if (l == null) return;
		for (MinionEntity m : List.copyOf(l)) poof(m);
	}

	/** Calls every minion to the owner's side (Bell of the Flock). */
	public static int recall(ServerPlayer owner) {
		List<MinionEntity> l = minions(owner);
		for (MinionEntity m : l) {
			m.teleportToOwner(owner);
			m.setTarget(null);
		}
		return l.size();
	}

	private static void poof(Entity m) {
		if (m.level() instanceof ServerLevel sl && !m.isRemoved()) {
			sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
		}
		m.discard();
	}

	/** True if any live minion of this owner is within {@code r} blocks of {@code e}. */
	public static boolean anyNear(ServerPlayer owner, LivingEntity e, double r) {
		for (MinionEntity m : minions(owner)) if (m.distanceTo(e) <= r) return true;
		return false;
	}
}
