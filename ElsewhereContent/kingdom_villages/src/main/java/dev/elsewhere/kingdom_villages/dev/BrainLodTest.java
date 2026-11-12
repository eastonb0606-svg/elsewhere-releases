package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.s3.brainlod.BrainLod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/** Self-test checks for the villager brain LOD/stagger ({@link BrainLod}, s-siegeperf). Dev only, dropped at merge. */
final class BrainLodTest {
	private BrainLodTest() {}

	/** The tier rules (pure). */
	static void rules() {
		SelfTest.check("villager brain LOD: free villagers near a player run the start pass every tick (vanilla)",
			BrainLod.interval(5 * 5, false) == 1 && BrainLod.interval(15 * 15, false) == 1, null);
		SelfTest.check("villager brain LOD: captured villagers every 2nd tick within 16 blocks, every 4th beyond or with no player",
			BrainLod.interval(15 * 15, true) == 2 && BrainLod.interval(30 * 30, true) == 4 && BrainLod.interval(Double.MAX_VALUE, true) == 4, null);
		SelfTest.check("villager brain LOD: free villagers vanilla within 48 blocks, every 2nd tick to 96, every 4th beyond",
			BrainLod.interval(40 * 40, false) == 1 && BrainLod.interval(80 * 80, false) == 2 && BrainLod.interval(200 * 200, false) == 4
				&& BrainLod.interval(Double.MAX_VALUE, false) == 4, null);
		SelfTest.check("villager brain LOD kill switch is on by default (elsewhere.overhaul3.villager_brain_lod)", BrainLod.ON && BrainLod.enabled, null);
	}

	/**
	 * Live, on a real villager at {@code far} (more than 96 blocks from every observer): the start pass runs on 1 tick in
	 * 4, memories still expire every tick, a hurt villager reacts on every tick, and the kill switch restores vanilla.
	 */
	static void live(ServerLevel level, BlockPos far) {
		Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (v == null) {
			SelfTest.check("villager brain LOD: test villager spawned", false, null);
			return;
		}
		v.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0F, 0F);
		level.addFreshEntity(v);
		Brain<Villager> b = v.getBrain();
		int every = BrainLod.computeInterval(level, v);
		b.setMemoryWithExpiry(MemoryModuleType.LAST_WOKEN, level.getGameTime(), 30L);
		long run0 = BrainLod.startsRun, skip0 = BrainLod.startsSkipped;
		for (int i = 0; i < 40; i++) {
			v.tickCount++;
			b.tick(level, v);
		}
		long run = BrainLod.startsRun - run0, skip = BrainLod.startsSkipped - skip0;
		SelfTest.check("villager brain LOD: a villager far from every player runs its start pass on 1 brain tick in 4",
			every == 4 && run == 10 && skip == 30, "interval " + every + ", ran " + run + ", skipped " + skip);
		SelfTest.check("villager brain LOD: memories still expire every tick (a 30-tick memory is gone after 40 brain ticks)",
			!b.hasMemoryValue(MemoryModuleType.LAST_WOKEN), b.getMemory(MemoryModuleType.LAST_WOKEN));
		v.hurtTime = 10;
		run0 = BrainLod.startsRun;
		skip0 = BrainLod.startsSkipped;
		for (int i = 0; i < 8; i++) {
			v.tickCount++;
			b.tick(level, v);
		}
		SelfTest.check("villager brain LOD: a hurt villager runs every start pass (no delayed reaction)",
			BrainLod.startsRun - run0 == 8 && BrainLod.startsSkipped == skip0, "ran " + (BrainLod.startsRun - run0) + ", skipped " + (BrainLod.startsSkipped - skip0));
		v.hurtTime = 0;
		BrainLod.enabled = false;
		run0 = BrainLod.startsRun;
		skip0 = BrainLod.startsSkipped;
		for (int i = 0; i < 8; i++) {
			v.tickCount++;
			b.tick(level, v);
		}
		BrainLod.enabled = BrainLod.ON;
		SelfTest.check("villager brain LOD: switched off, the brain is vanilla (nothing skipped or counted)",
			BrainLod.startsRun == run0 && BrainLod.startsSkipped == skip0, null);

		// futile walk search: a target 60 blocks straight up is unreachable; the first search ends where the villager
		// stands, the repeats from the same block to the same target are skipped (the walk target is kept)
		net.minecraft.world.entity.ai.behavior.MoveToTargetSink sink = new net.minecraft.world.entity.ai.behavior.MoveToTargetSink();
		BlockPos up = v.blockPosition().above(60);
		long now = level.getGameTime(), s0 = BrainLod.walkSearches, sv0 = BrainLod.walkSearchesSaved;
		boolean keptTarget = true;
		for (int i = 0; i < 6; i++) {
			BrainLod.resetWalkBudget();
			if (sink.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING) sink.doStop(level, v, now);
			b.eraseMemory(MemoryModuleType.PATH);
			b.setMemory(MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(up, 0.5F, 0));
			v.setOnGround(true);
			sink.tryStart(level, v, now);
			if (i > 0 && !b.hasMemoryValue(MemoryModuleType.WALK_TARGET)) keptTarget = false;
		}
		if (sink.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING) sink.doStop(level, v, now);
		long searched = BrainLod.walkSearches - s0, saved = BrainLod.walkSearchesSaved - sv0;
		SelfTest.check("villager walk searches: an unreachable walk target is searched once from a spot, the 5 repeats are skipped and the target kept",
			searched == 1 && saved == 5 && keptTarget, "searched " + searched + ", skipped " + saved + ", target kept " + keptTarget);
		v.discard();
	}
}
