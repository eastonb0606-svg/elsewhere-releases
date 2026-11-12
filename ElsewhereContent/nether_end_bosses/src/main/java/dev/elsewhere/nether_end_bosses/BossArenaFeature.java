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
		ArenaBuilder builder = ArenaBuilder.BY_BOSS.get(boss);
		if (builder == null) return false;
		BlockPos center = new BlockPos((origin.getX() & ~15) + 8, origin.getY(), (origin.getZ() & ~15) + 8);
		BlockPos at = builder.findOrigin(level, center, random);
		if (at == null) return false;
		builder.build(level, at, random);
		NetherEndBossesContent.LOGGER.debug("Generated {} arena at {}", boss, at);
		return true;
	}
}
