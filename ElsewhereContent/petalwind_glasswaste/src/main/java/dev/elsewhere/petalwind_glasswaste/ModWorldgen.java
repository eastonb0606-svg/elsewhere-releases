package dev.elsewhere.petalwind_glasswaste;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.heliodor.SunwrightZiggurat;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalPagoda;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Feature types of the old boss-site placement. Kept registered (0.3.0 worlds reference the ids) but no biome places them any more: see {@link PGStructures}. */
public final class ModWorldgen {
	private ModWorldgen() {}

	/** Petal Pagoda on an island top, with a dormant Kazehana waiting on the roof. */
	public record PetalPagodaFeature() implements Feature {
		public static final MapCodec<PetalPagodaFeature> CODEC = MapCodec.unit(PetalPagodaFeature::new);

		@Override
		public MapCodec<PetalPagodaFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos c = new BlockPos((origin.getX() & ~15) + 8, 0, (origin.getZ() & ~15) + 8);
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX(), c.getZ());
			if (y < 100 || y > 280) return false;
			if (dev.elsewhere.petalwind_glasswaste.fit3.PGFit.ENABLED) { // 3.0: the same fit builders as the structure (origin = top ground block)
				dev.elsewhere.petalwind_glasswaste.fit3.PGFit.placeTwin(level, PGStructures.PAGODA, c.atY(y - 1));
				return true;
			}
			BlockPos center = PetalPagoda.build(level, c.atY(y), false);
			Kazehana k = ModEntities.KAZEHANA.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (k != null) {
				k.setArena(center, PetalPagoda.ARENA_RADIUS, 4, 12);
				k.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
				level.addFreshEntityWithPassengers(k);
			}
			return true;
		}
	}

	/** Sunwright Ziggurat with Heliodor waiting on the summit. */
	public record ZigguratFeature() implements Feature {
		public static final MapCodec<ZigguratFeature> CODEC = MapCodec.unit(ZigguratFeature::new);

		@Override
		public MapCodec<ZigguratFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos c = new BlockPos((origin.getX() & ~15) + 8, 0, (origin.getZ() & ~15) + 8);
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX(), c.getZ());
			if (y < 50) return false;
			if (dev.elsewhere.petalwind_glasswaste.fit3.PGFit.ENABLED) { // 3.0: origin one into the ground, as the structure
				dev.elsewhere.petalwind_glasswaste.fit3.PGFit.placeTwin(level, PGStructures.ZIGGURAT, c.atY(y - 2));
				return true;
			}
			BlockPos summit = SunwrightZiggurat.build(level, c.atY(y - 1));
			Heliodor h = ModEntities.HELIODOR.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (h != null) {
				h.setArena(summit, SunwrightZiggurat.ARENA_RADIUS, 4, 12);
				h.snapTo(summit.getX() + 0.5, summit.getY(), summit.getZ() + 0.5);
				level.addFreshEntityWithPassengers(h);
			}
			return true;
		}
	}

	/** The Sunpit with Ankhara burrowed beneath it. */
	public record SunpitFeature() implements Feature {
		public static final MapCodec<SunpitFeature> CODEC = MapCodec.unit(SunpitFeature::new);

		@Override
		public MapCodec<SunpitFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos c = new BlockPos((origin.getX() & ~15) + 8, 0, (origin.getZ() & ~15) + 8);
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.getX(), c.getZ());
			if (y < 50) return false;
			if (dev.elsewhere.petalwind_glasswaste.fit3.PGFit.ENABLED) { // 3.0: pit floor 6 below the top ground block, as the structure
				dev.elsewhere.petalwind_glasswaste.fit3.PGFit.placeTwin(level, PGStructures.SUNPIT, c.atY(y - 7));
				return true;
			}
			BlockPos floor = Sunpit.build(level, c.atY(y - 6));
			Ankhara a = ModEntities.ANKHARA.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (a != null) {
				a.setArena(floor, Sunpit.ARENA_RADIUS, 6, 12);
				a.snapTo(floor.getX() + 0.5, floor.getY() - 4.5, floor.getZ() + 0.5);
				level.addFreshEntityWithPassengers(a);
			}
			return true;
		}
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("petal_pagoda"), PetalPagodaFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("sunwright_ziggurat"), ZigguratFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("sunpit"), SunpitFeature.CODEC);
		PGStructures.init();
	}
}
