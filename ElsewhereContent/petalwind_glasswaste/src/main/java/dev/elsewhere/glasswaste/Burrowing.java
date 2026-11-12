package dev.elsewhere.glasswaste;

import net.minecraft.world.entity.LivingEntity;

/** Marker for mobs that burrow (Glass Scarab, Sandswimmer, Ankhara); weapons with burrow bonuses check it. */
public interface Burrowing {
	boolean isBurrowed();

	static boolean isBurrowing(LivingEntity entity) {
		return entity instanceof Burrowing;
	}
}
