package dev.elsewhere.boss.hrolf;

import dev.elsewhere.verdigris_ironpeak.SummonFocusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** Sound the Challenge Horn at the Proving Grounds gate: Hrolf rides in on Grimhorn. */
public final class HrolfSummon {
	private HrolfSummon() {}

	public static void init() {
		SummonFocusBlock.HANDLERS.put("proving_challenge_horn", (level, pos, player) -> {
			if (!level.getEntitiesOfClass(HrolfBoss.class, new AABB(pos).inflate(40)).isEmpty()) return InteractionResult.PASS;
			summon(level, ProvingGroundsArena.centerFromHorn(pos));
			return InteractionResult.SUCCESS;
		});
	}

	public static HrolfBoss summon(ServerLevel level, BlockPos center) {
		GrimhornEntity goat = HrolfEntities.GRIMHORN.create(level, EntitySpawnReason.EVENT);
		goat.snapTo(center.getX() + 0.5, center.getY() + 1, center.getZ() - 5.5, 0, 0);
		level.addFreshEntity(goat);
		HrolfBoss boss = HrolfEntities.HROLF.create(level, EntitySpawnReason.EVENT);
		boss.snapTo(goat.getX(), goat.getY() + 2, goat.getZ(), 0, 0);
		boss.setArena(center, ProvingGroundsArena.RADIUS, ProvingGroundsArena.HEIGHT);
		boss.setMount(goat);
		level.addFreshEntity(boss);
		boss.startRiding(goat, true, true);
		boss.beginFight(level);
		level.playSound(null, center, dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds.HROLF_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.9F);
		return boss;
	}
}
