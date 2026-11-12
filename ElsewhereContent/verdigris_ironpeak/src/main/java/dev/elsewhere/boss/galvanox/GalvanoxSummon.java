package dev.elsewhere.boss.galvanox;

import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.SummonFocusBlock;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Galvanox wakes when lightning strikes the lighthouse lantern (with a player within 32 blocks). Using the lantern
 * calls the storm onto it: a bolt lands on the lantern 3 s later.
 */
public final class GalvanoxSummon {
	private static final Map<BlockPos, Integer> CALLED = new HashMap<>();
	private static ServerLevel calledLevel;

	private GalvanoxSummon() {}

	public static void init() {
		SummonFocusBlock.HANDLERS.put("lighthouse_lantern", (level, pos, player) -> {
			if (!level.getEntitiesOfClass(GalvanoxBoss.class, new AABB(pos).inflate(40)).isEmpty()) return InteractionResult.PASS;
			CALLED.put(pos.immutable(), 60);
			calledLevel = level;
			player.sendOverlayMessage(Component.translatable("message.elsewhere.galvanox.call"));
			return InteractionResult.SUCCESS;
		});
	}

	public static void serverTick(MinecraftServer server) {
		if (CALLED.isEmpty() || calledLevel == null) return;
		CALLED.replaceAll((p, t) -> t - 1);
		CALLED.entrySet().removeIf(e -> {
			if (e.getValue() > 0) return false;
			Fx.lightning(calledLevel, Vec3.atBottomCenterOf(e.getKey().above(2)), true, null);
			wake(calledLevel, e.getKey());
			return true;
		});
	}

	/** Lightning hook: a bolt within 6 blocks of a lantern wakes Galvanox if a player is near. */
	public static void onLightning(ServerLevel level, LightningBolt bolt) {
		BlockPos c = bolt.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-6, -6, -6), c.offset(6, 6, 6))) {
			if (level.getBlockState(p).is(VerdigrisBlocks.LIGHTHOUSE_LANTERN)) {
				if (level.getNearestPlayer(p.getX(), p.getY(), p.getZ(), 32, false) != null) wake(level, p.immutable());
				return;
			}
		}
	}

	public static GalvanoxBoss wake(ServerLevel level, BlockPos lantern) {
		if (!level.getEntitiesOfClass(GalvanoxBoss.class, new AABB(lantern).inflate(40)).isEmpty()) return null;
		return summon(level, GalvanoxArena.centerFromLantern(lantern));
	}

	public static GalvanoxBoss summon(ServerLevel level, BlockPos center) {
		GalvanoxBoss boss = GalvanoxEntities.GALVANOX.create(level, EntitySpawnReason.EVENT);
		boss.snapTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 0, 0);
		boss.setArena(center, GalvanoxArena.RADIUS, GalvanoxArena.HEIGHT);
		level.addFreshEntity(boss);
		boss.beginFight(level);
		level.playSound(null, center, dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds.GALVANOX_ROAR.value(), net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.6F);
		return boss;
	}
}
