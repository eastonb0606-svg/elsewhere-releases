package dev.elsewhere.core.boss;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Applies the lethal-hit guard to every hit a boss lands on a player, including vanilla melee and projectiles the boss
 * owns, by cancelling the hit and re-applying the capped amount.
 */
public final class BossEvents {
	private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);

	private BossEvents() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRY.get() || !(entity instanceof Player) || !(source.getEntity() instanceof ElsewhereBoss)) {
				return true;
			}
			float capped = BossGuards.lethalCap(entity, amount, source);
			if (capped >= amount - 1.0E-3F || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			REENTRY.set(true);
			try {
				entity.hurtServer(level, source, capped);
			} finally {
				REENTRY.set(false);
			}
			return false;
		});
	}
}
