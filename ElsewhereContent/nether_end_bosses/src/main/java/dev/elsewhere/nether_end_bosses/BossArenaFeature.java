package dev.elsewhere.nether_end_bosses;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Feature type {@code elsewhere:nether_end_boss_arena}: generates one boss arena ({"boss": "solaxis"}) centred on the
 * chunk middle so the build stays inside the 3x3 chunks a feature may write to (arena radius at most 24).
 */
public record BossArenaFeature(String boss) implements Feature {
	public static final MapCodec<BossArenaFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.fieldOf("boss").forGetter(BossArenaFeature::boss)).apply(i, BossArenaFeature::new));

	@Override
	public MapCodec<BossArenaFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos center = new BlockPos((origin.getX() & ~15) + 8, origin.getY(), (origin.getZ() & ~15) + 8);
		var fit = dev.elsewhere.nether_end_bosses.fit3.NetherFit.ARENAS.get(boss);
		if (dev.elsewhere.nether_end_bosses.fit3.NetherFit.ENABLED && fit != null) {
			// 3.0: the twin goes through the same site rules and v2 builder as the structure, clipped to the 3x3 chunks of the centre
			var rs = level.getLevel().getChunkSource().randomState();
			int y = dev.elsewhere.nether_end_bosses.fit3.FitSite.pick(boss, center.getX(), center.getZ(),
				(cx, cz) -> generator.getBaseColumn(cx, cz, level, rs), level.getMinY(), level.getMaxY(), random);
			if (y == dev.elsewhere.nether_end_bosses.fit3.FitSite.NONE) return false;
			BlockPos at = new BlockPos(center.getX(), y, center.getZ());
			int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
			var clip = new net.minecraft.world.level.levelgen.structure.BoundingBox((ccx - 1) << 4, level.getMinY(), (ccz - 1) << 4, ((ccx + 1) << 4) + 15, level.getMaxY(), ((ccz + 1) << 4) + 15);
			dev.elsewhere.nether_end_bosses.fit3.NetherFit.buildWhole(level, fit, at, clip);
			NetherEndBossesContent.LOGGER.debug("Generated {} arena (fit3 twin) at {}", boss, at);
			return true;
		}
		ArenaBuilder builder = dev.elsewhere.nether_end_bosses.fit3.NetherFit.LEGACY.getOrDefault(boss, ArenaBuilder.BY_BOSS.get(boss));
		if (builder == null) return false;
		BlockPos at = builder.findOrigin(level, center, random);
		if (at == null) return false;
		builder.build(level, at, random);
		NetherEndBossesContent.LOGGER.debug("Generated {} arena at {}", boss, at);
		return true;
	}
}
