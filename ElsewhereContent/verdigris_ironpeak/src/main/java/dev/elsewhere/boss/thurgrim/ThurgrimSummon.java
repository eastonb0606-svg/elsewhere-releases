package dev.elsewhere.boss.thurgrim;

import dev.elsewhere.ironpeak.Ironpeak;
import dev.elsewhere.verdigris_ironpeak.SummonFocusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** Beat the war drum after defeating Hrolf Grimhorn (clan rank 5+) to call Thurgrim to his throne. */
public final class ThurgrimSummon {
	private ThurgrimSummon() {}

	public static void init() {
		SummonFocusBlock.HANDLERS.put("summit_war_drum", (level, pos, player) -> {
			level.playSound(null, pos, dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds.WAR_DRUM.value(), net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 0.8F);
			if (!level.getEntitiesOfClass(ThurgrimBoss.class, new AABB(pos).inflate(40)).isEmpty()) return InteractionResult.SUCCESS;
			if (Ironpeak.rank(player) < 5 && !player.isCreative()) {
				player.sendOverlayMessage(Component.translatable("message.elsewhere.thurgrim.unworthy"));
				return InteractionResult.SUCCESS;
			}
			summon(level, ThurgrimArena.centerFromDrum(pos));
			return InteractionResult.SUCCESS;
		});
	}

	public static ThurgrimBoss summon(ServerLevel level, BlockPos center) {
		ThurgrimBoss boss = ThurgrimEntities.THURGRIM.create(level, EntitySpawnReason.EVENT);
		boss.snapTo(center.getX() + 0.5, center.getY() + 1, center.getZ() - 6.5, 0, 0);
		boss.setArena(center, ThurgrimArena.RADIUS, ThurgrimArena.HEIGHT);
		level.addFreshEntity(boss);
		boss.beginFight(level);
		return boss;
	}
}
