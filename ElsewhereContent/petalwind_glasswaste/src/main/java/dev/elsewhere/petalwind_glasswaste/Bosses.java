package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.heliodor.SunwrightZiggurat;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalPagoda;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import org.jspecify.annotations.Nullable;

/** Boss wiring: arena listeners and "build the arena + spawn the boss" used by worldgen and /elsewhere_dev boss. */
public final class Bosses {
	private Bosses() {}

	public static final List<String> IDS = List.of("kazehana", "tempestra", "heliodor", "ankhara");

	public static void init() {
		Kazehana.registerBellListener();
		dev.elsewhere.stub.petalwind_glasswaste.BossSummons.init();
	}

	/** Builds the boss's arena around {@code at} (ground level) and spawns the boss. */
	public static @Nullable BossBase summon(ServerLevel level, String id, BlockPos at) {
		// an arena is never built at the world floor (an unloaded column reads as the minimum height): keep room for the pit and the burrow
		at = at.atY(Math.max(at.getY(), level.getMinY() + 24));
		return switch (id) {
			case "kazehana" -> {
				BlockPos center = PetalPagoda.build(level, at, true);
				Kazehana k = spawnAt(level, ModEntities.KAZEHANA.create(level, EntitySpawnReason.COMMAND), center, PetalPagoda.ARENA_RADIUS, 4, 12);
				yield k;
			}
			case "tempestra" -> {
				BlockPos center = EyeOfTheGale.build(level, at);
				Tempestra t = spawnAt(level, ModEntities.TEMPESTRA.create(level, EntitySpawnReason.COMMAND), center, EyeOfTheGale.ARENA_RADIUS, 18, 20);
				yield t;
			}
			case "heliodor" -> {
				BlockPos center = SunwrightZiggurat.build(level, at);
				yield spawnAt(level, ModEntities.HELIODOR.create(level, EntitySpawnReason.COMMAND), center, SunwrightZiggurat.ARENA_RADIUS, 4, 12);
			}
			case "ankhara" -> {
				BlockPos center = Sunpit.build(level, at);
				Ankhara a = ModEntities.ANKHARA.create(level, EntitySpawnReason.COMMAND);
				if (a == null) yield null;
				a.setArena(center, Sunpit.ARENA_RADIUS, 6, 12);
				a.snapTo(center.getX() + 0.5, center.getY() - 4.5, center.getZ() + 0.5);
				level.addFreshEntity(a);
				yield a;
			}
			default -> null;
		};
	}

	private static <T extends BossBase> @Nullable T spawnAt(WorldGenLevel level, @Nullable T boss, BlockPos center, int radius, int down, int up) {
		if (boss == null) return null;
		boss.setArena(center, radius, down, up);
		boss.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		level.addFreshEntity(boss);
		return boss;
	}
}
