package dev.elsewhere.boss.selenne;

import dev.elsewhere.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Picking the Moon Bloom at a Moonring's centre wakes Selenne above it. */
public final class SelenneSummon {
	private SelenneSummon() {
	}

	public static @Nullable Selenne summon(final ServerLevel level, final BlockPos bloom, final @Nullable Player player) {
		if (!level.getEntitiesOfClass(Selenne.class, new net.minecraft.world.phys.AABB(bloom).inflate(32.0)).isEmpty()) {
			return null;
		}
		Selenne boss = ModEntities.SELENNE.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return null;
		}
		boss.snapTo(bloom.getX() + 0.5, bloom.getY() + 6.0, bloom.getZ() + 0.5, 0.0F, 0.0F);
		boss.setArena(bloom, MoonringArena.RADIUS, 2, 24);
		level.addFreshEntity(boss);
		boss.wake(level);
		return boss;
	}
}
