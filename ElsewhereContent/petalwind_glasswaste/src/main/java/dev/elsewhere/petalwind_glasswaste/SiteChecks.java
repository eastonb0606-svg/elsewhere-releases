package dev.elsewhere.petalwind_glasswaste;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.petalwind.PetalwindDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/** 2.0 alpha.2: every boss site of this module is a real structure that /locate finds, generates, and holds its boss or summon spot. */
final class SiteChecks {
	private SiteChecks() {}

	static void run(MinecraftServer server) {
		for (String kind : new String[] {PGStructures.PAGODA, PGStructures.EYE, PGStructures.ZIGGURAT, PGStructures.SUNPIT}) {
			boolean sky = kind.equals(PGStructures.PAGODA) || kind.equals(PGStructures.EYE);
			ServerLevel level = server.getLevel(sky ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL);
			var holder = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, PGStructures.structureId(kind)));
			if (level == null || holder.isEmpty()) {
				SelfTest.check(false, "site " + kind + ": structure registered and dimension loaded");
				continue;
			}
			BlockPos origin = new BlockPos(5000 + new java.util.Random().nextInt(400000), 64, -5000 - new java.util.Random().nextInt(400000)); // a fresh area (the dev world persists)
			long t0 = System.nanoTime();
			// 3.0 site rules (GENERATION.md G2) turn down bad sites, so these rare sites are ~65-80% as dense as in 2.1 with the same spacing: from a
			// random fresh point the nearest one is past 1,600 blocks about 3% of the time (ziggurat). Search 150 chunks and accept 2,400 (P < 0.1%).
			BlockPos at = level.findNearestMapStructure(HolderSet.direct(holder.get()), origin, 150, false);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			if (at == null) {
				SelfTest.check(false, SelfTest.f("site %s: /locate finds one within 150 chunks of %s (none, %d ms)", kind, origin.toShortString(), ms));
				continue;
			}
			double dist = Math.sqrt(at.distSqr(origin));
			SelfTest.check(dist <= 2400, SelfTest.f("site %s: nearest at %s, %.0f blocks from a fresh area (%d ms)", kind, at.toShortString(), dist, ms));
			var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.FULL, true);
			var start = chunk.getStartForStructure(holder.get().value());
			boolean ok = start != null && start.isValid();
			SelfTest.check(ok, "site " + kind + ": chunk generated with a valid structure start");
			if (!ok) continue;
			PGStructures.Site piece = PGStructures.siteOf(start);
			BlockPos c = piece.center();
			// let the neighbouring chunks of the 3x3 finish, then look for the boss or the summon spot
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, ChunkStatus.FULL, true);
			AABB box = new AABB(c).inflate(30);
			boolean present = switch (kind) {
				case PGStructures.PAGODA -> !level.getEntitiesOfClass(Kazehana.class, box).isEmpty();
				case PGStructures.ZIGGURAT -> !level.getEntitiesOfClass(Heliodor.class, box).isEmpty();
				case PGStructures.SUNPIT -> !level.getEntitiesOfClass(Ankhara.class, box).isEmpty();
				default -> !level.getBlockState(EyeOfTheGale.platforms(c).get(0).below()).isAir();
			};
			if (kind.equals(PGStructures.EYE)) {
				// the survival summon: three wind charges inside the eye wake Tempestra
				for (int i = 0; i < 3; i++) {
					var wc = net.minecraft.world.entity.EntityTypes.WIND_CHARGE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
					wc.snapTo(c.getX() + 0.5, c.getY() + 2 + i, c.getZ() + 0.5);
					level.addFreshEntity(wc);
					PGStructures.eyeTick(level, c, null);
				}
				var ts = level.getEntitiesOfClass(dev.elsewhere.boss.tempestra.Tempestra.class, box);
				SelfTest.check(ts.size() == 1, "site eye_of_the_gale: three wind charges thrown into the eye summon Tempestra (" + ts.size() + ")");
				ts.forEach(t -> t.discard());
				level.getEntitiesOfClass(net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge.class, box).forEach(w -> w.discard());
			}
			SelfTest.check(present, "site " + kind + ": " + (kind.equals(PGStructures.EYE) ? "arena platforms built around the eye" : "its dormant boss is present") + " at " + c.toShortString());
			dev.elsewhere.petalwind_glasswaste.fit3.FitChecks.site(level, kind, start, SelfTest::check); // 3.0 looks + foundations (s-struct-petalwind)
		}
		dev.elsewhere.petalwind_glasswaste.fit3.FitChecks.run(server, SelfTest::check); // breeze temple, twins, plan/NBT/kill-switch checks
		var wings = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, PGStructures.structureId("zephyr_wings")));
		SelfTest.check(wings.isPresent(), "Zephyr Wings has a recipe (elytra + Gale Pinion smithing)");
	}
}
