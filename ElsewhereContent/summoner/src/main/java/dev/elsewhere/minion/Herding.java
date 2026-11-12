package dev.elsewhere.minion;

import dev.elsewhere.summoner.GearStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * "Herded" marks from the Herding Crook (ARSENAL.md rev 3 §5b): one target per player for 5 s; that player's minions
 * switch to it and each minion hit on it deals +1 damage. Server-side map; the mark also shows the Herded effect.
 */
public final class Herding {
	private Herding() {}

	private record Mark(UUID target, java.lang.ref.WeakReference<LivingEntity> ref, long until) {}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	public static void mark(ServerPlayer owner, LivingEntity target) {
		long until = owner.level().getGameTime() + GearStats.HERDED_TICKS;
		MARKS.put(owner.getUUID(), new Mark(target.getUUID(), new java.lang.ref.WeakReference<>(target), until));
		if (dev.elsewhere.summoner.SummonerEffects.HERDED != null) {
			target.addEffect(new net.minecraft.world.effect.MobEffectInstance(dev.elsewhere.summoner.SummonerEffects.HERDED, GearStats.HERDED_TICKS, 0, false, true, true), owner);
		}
		MinionManager.refresh(owner);
		if (!MinionManager.minions(owner).isEmpty() && !MinionTargeting.neverTarget(owner, target)) {
			owner.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.minions_attack", target.getDisplayName()));
		}
	}

	/** Sneak-use of the Crook: drops the mark and calls the minions off it. Returns true if there was a mark. */
	public static boolean callOff(ServerPlayer owner) {
		LivingEntity t = target(owner);
		MARKS.remove(owner.getUUID());
		for (MinionEntity m : MinionManager.minions(owner)) {
			if (t != null && m.getTarget() == t) m.setTarget(null);
		}
		if (t != null) t.removeEffect(dev.elsewhere.summoner.SummonerEffects.HERDED);
		owner.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.minions_stand_down"));
		return t != null;
	}

	public static @Nullable LivingEntity target(ServerPlayer owner) {
		Mark m = MARKS.get(owner.getUUID());
		if (m == null) return null;
		if (owner.level().getGameTime() > m.until()) {
			MARKS.remove(owner.getUUID());
			return null;
		}
		LivingEntity held = m.ref().get();
		Entity e = held != null && !held.isRemoved() && held.level() == owner.level() ? held : ((ServerLevel) owner.level()).getEntity(m.target());
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	public static boolean isHerdedBy(ServerPlayer owner, LivingEntity target) {
		LivingEntity t = target(owner);
		return t != null && t == target;
	}

	public static void clear(UUID owner) {
		MARKS.remove(owner);
	}
}
