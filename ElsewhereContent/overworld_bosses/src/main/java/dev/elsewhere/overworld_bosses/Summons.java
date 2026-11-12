package dev.elsewhere.overworld_bosses;

import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import dev.elsewhere.boss.rimeback.RimebackEntity;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import dev.elsewhere.overworld_bosses.world.BlockSink;
import dev.elsewhere.overworld_bosses.world.Blueprints;
import dev.elsewhere.stub.overworld_bosses.BossArena;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The in-world summon paths and arena geometry for each boss (also used by the dev command and self-test). */
public final class Summons {
	private Summons() {}

	public static BlockSink levelSink(ServerLevel level) {
		return new BlockSink() {
			@Override
			public void set(BlockPos pos, BlockState state) {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}

			@Override
			public BlockState get(BlockPos pos) {
				return level.getBlockState(pos);
			}
		};
	}

	public static BossArena gulpmotherArena(BlockPos o) { return new BossArena(o, 14, 6, 14, o.getY() - 1, 6); }
	public static BossArena strawgrimArena(BlockPos post) { return new BossArena(post, 12, 4, 14, post.getY() - 1, 6); }
	public static BossArena neferkhetArena(BlockPos o) { return new BossArena(o, 15, 5, 18, o.getY() - 1, 4); }
	public static BossArena rimebackArena(BlockPos o) { return new BossArena(o, 16, 4, 20, o.getY() - 1, 6); }
	public static BossArena vorlaghArena(BlockPos o) { return new BossArena(o, 20, 14, 16, o.getY() - 1, 5); }

	private static <T extends ElsewhereBoss> @Nullable T existing(ServerLevel level, Class<T> cls, BossArena arena) {
		List<T> list = level.getEntitiesOfClass(cls, arena.box().inflate(8), ElsewhereBoss::isAlive);
		return list.isEmpty() ? null : list.get(0);
	}

	/** Spawns the boss, snapshots the arena (if the caller did not already), seals it and starts the fight. */
	public static <T extends ElsewhereBoss> @Nullable T spawn(ServerLevel level, EntityType<T> type, Vec3 at, float yaw, BossArena arena) {
		T boss = type.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) return null;
		boss.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		boss.setYHeadRot(yaw);
		boss.setYBodyRot(yaw);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.TRIGGERED, null);
		if (!arena.hasSnapshot()) arena.capture(level);
		level.addFreshEntity(boss);
		boss.beginFight(level, arena);
		return boss;
	}

	public static @Nullable GulpmotherEntity summonGulpmother(ServerLevel level, BlockPos cauldron) {
		BlockPos o = cauldron.subtract(Blueprints.SHRINE_CAULDRON);
		BossArena arena = gulpmotherArena(o);
		GulpmotherEntity ex = existing(level, GulpmotherEntity.class, arena);
		if (ex != null) return ex;
		return spawn(level, OwbEntities.GULPMOTHER, Vec3.atBottomCenterOf(o.below(2)), 180.0F, arena);
	}

	public static @Nullable StrawgrimEntity summonStrawgrim(ServerLevel level, BlockPos post) {
		BossArena arena = strawgrimArena(post);
		StrawgrimEntity ex = existing(level, StrawgrimEntity.class, arena);
		if (ex != null) return ex;
		arena.capture(level);
		for (int i = 0; i < 4; i++) level.setBlock(post.above(i), Blocks.AIR.defaultBlockState(), 3);
		return spawn(level, OwbEntities.STRAWGRIM, Vec3.atBottomCenterOf(post), 180.0F, arena);
	}

	/** @param altar the altar block; the chamber centre is relative to it. */
	public static @Nullable NeferkhetEntity summonNeferkhet(ServerLevel level, BlockPos altar) {
		BlockPos o = altar.subtract(Blueprints.SUN_ALTAR);
		BossArena arena = neferkhetArena(o);
		NeferkhetEntity ex = existing(level, NeferkhetEntity.class, arena);
		if (ex != null) return ex;
		return spawn(level, OwbEntities.NEFERKHET, Vec3.atBottomCenterOf(o), 0.0F, arena);
	}

	public static @Nullable RimebackEntity summonRimeback(ServerLevel level, BlockPos heart) {
		BlockPos o = heart.subtract(Blueprints.FROZEN_HEART);
		BossArena arena = rimebackArena(o);
		RimebackEntity ex = existing(level, RimebackEntity.class, arena);
		if (ex != null) return ex;
		arena.capture(level); // before the heart is removed, so a reset puts it back
		return spawn(level, OwbEntities.RIMEBACK, Vec3.atBottomCenterOf(o.offset(0, 0, -4)), 180.0F, arena);
	}

	public static @Nullable VorlaghEntity summonVorlaghAt(ServerLevel level, BlockPos o) {
		BossArena arena = vorlaghArena(o);
		VorlaghEntity ex = existing(level, VorlaghEntity.class, arena);
		if (ex != null) return ex;
		return spawn(level, OwbEntities.VORLAGH, Vec3.atBottomCenterOf(o.below(5)), 180.0F, arena);
	}

	/** Siren Conch use: needs a Wreck Ring capstan within 24 blocks and a thunderstorm. Null = summoned. */
	public static @Nullable String trySummonVorlagh(ServerLevel level, Player player) {
		BlockPos capstan = null;
		BlockPos p = player.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-24, -6, -24), p.offset(24, 6, 24))) {
			if (level.getBlockState(q).is(OwbBlocks.VORLAGH_CAPSTAN)) {
				capstan = q.immutable();
				break;
			}
		}
		if (capstan == null) return "message.elsewhere.vorlagh.no_wreck";
		if (!level.isThundering()) return "message.elsewhere.vorlagh.no_storm";
		BlockPos o = capstan.subtract(Blueprints.WRECK_CAPSTAN);
		return summonVorlaghAt(level, o) != null ? null : "message.elsewhere.vorlagh.no_wreck";
	}

	public static boolean anyBossNear(ServerLevel level, BlockPos p) {
		return !level.getEntitiesOfClass(ElsewhereBoss.class, new AABB(p).inflate(40), ElsewhereBoss::isAlive).isEmpty();
	}
}
