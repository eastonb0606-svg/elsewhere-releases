package dev.elsewhere.boss.verdigrim;

import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Ringing all four cathedral bells (within a minute) starts Verdigrim's hymn; during the fight bells drain the flood. */
public final class VerdigrimSummon {
	private static final Map<Long, Set<Long>> RUNG = new HashMap<>();
	private static final Map<Long, Long> RUNG_AT = new HashMap<>();

	private VerdigrimSummon() {}

	public static @Nullable VerdigrimBoss activeNear(ServerLevel level, BlockPos pos) {
		List<VerdigrimBoss> list = level.getEntitiesOfClass(VerdigrimBoss.class, new AABB(pos).inflate(32), b -> b.isAlive());
		return list.isEmpty() ? null : list.get(0);
	}

	/** Bell hook (UseBlockCallback). Returns PASS so the bell still rings. */
	public static InteractionResult onBell(ServerLevel level, BlockPos bell, ServerPlayer player) {
		VerdigrimBoss boss = activeNear(level, bell);
		if (boss != null) {
			boss.ringBell(level, bell, player);
			return InteractionResult.PASS;
		}
		BlockPos organ = findOrgan(level, bell);
		if (organ == null) return InteractionResult.PASS;
		long key = organ.asLong();
		long now = level.getGameTime();
		if (now - RUNG_AT.getOrDefault(key, 0L) > 1200) RUNG.remove(key);
		RUNG_AT.put(key, now);
		Set<Long> rung = RUNG.computeIfAbsent(key, k -> new HashSet<>());
		rung.add(bell.asLong());
		player.sendOverlayMessage(Component.translatable("message.elsewhere.verdigrim.bells", rung.size()));
		if (rung.size() >= 4) {
			RUNG.remove(key);
			summon(level, VerdigrimArena.centerFromOrgan(organ));
		}
		return InteractionResult.PASS;
	}

	/** Note-block hook: during the fight the organ's note blocks are the replay keyboard. */
	public static InteractionResult onNoteBlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
		VerdigrimBoss boss = activeNear(level, pos);
		if (boss == null) return InteractionResult.PASS;
		int pipe = boss.pipeForNoteBlock(pos);
		if (pipe < 0) return InteractionResult.PASS;
		boss.notePlayed(level, pipe, player);
		return InteractionResult.SUCCESS;
	}

	static @Nullable BlockPos findOrgan(ServerLevel level, BlockPos near) {
		for (BlockPos p : BlockPos.betweenClosed(near.offset(-16, -4, -24), near.offset(16, 4, 24))) {
			if (level.getBlockState(p).is(VerdigrisBlocks.DROWNED_ORGAN)) return p.immutable();
		}
		return null;
	}

	/** Spawns Verdigrim in the arena whose nave centre is {@code center}, and opens the fight. */
	public static VerdigrimBoss summon(ServerLevel level, BlockPos center) {
		VerdigrimBoss boss = VerdigrimEntities.VERDIGRIM.create(level, EntitySpawnReason.EVENT);
		int[] s = VerdigrimArena.BOSS_SPAWN;
		boss.snapTo(center.getX() + s[0] + 0.5, center.getY() + s[1], center.getZ() + s[2] + 0.5, 180, 0);
		boss.setArena(center, VerdigrimArena.RADIUS, VerdigrimArena.HEIGHT);
		level.addFreshEntity(boss);
		boss.beginFight(level);
		level.playSound(null, center, dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds.VERDIGRIM_ROAR.value(), net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.7F);
		return boss;
	}

	public static void init() {
		dev.elsewhere.verdigris_ironpeak.SummonFocusBlock.HANDLERS.put("drowned_organ", (level, pos, player) -> {
			player.sendOverlayMessage(Component.translatable("message.elsewhere.verdigrim.organ"));
			return InteractionResult.SUCCESS;
		});
	}

	public static boolean isBell(net.minecraft.world.level.block.state.BlockState s) {
		return s.is(Blocks.BELL);
	}
}
