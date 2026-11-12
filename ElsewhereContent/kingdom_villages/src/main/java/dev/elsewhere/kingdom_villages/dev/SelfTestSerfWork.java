package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom.idle.IdlePoses;
import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.VillagesContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * 3.0 polish P9 (s-p9-idle): serf work poses and the synced serf tier. Runs once before the villages self-test's first phase
 * (its checks count in the same TOTAL); registered from DevEntry. No world changes: the serfs are never added to the level.
 */
public final class SelfTestSerfWork {
	private SelfTestSerfWork() {}

	private static boolean done;

	public static void tick(MinecraftServer srv) {
		if (done || SelfTest.tick < 20) return;
		done = true;
		try {
			run(srv.overworld());
		} catch (Throwable t) {
			SelfTest.check("serf work poses ran", false, t);
		}
	}

	static void run(ServerLevel level) {
		SelfTest.check("serf work pose: hauling its load to the spot, working once there, plain walk without a load",
			Serf.workPoseFor(100, true) == IdlePoses.SERF_CARRY && Serf.workPoseFor(100, false) == 0
				&& Serf.workPoseFor(4, true) == IdlePoses.SERF_WORK && Serf.workPoseFor(4, false) == IdlePoses.SERF_WORK, null);
		Serf s = VillagesContent.SERF.create(level, EntitySpawnReason.COMMAND);
		if (s == null) {
			SelfTest.check("serf work poses: test serf created", false, null);
			return;
		}
		s.setWorkPose(IdlePoses.SERF_WORK);
		boolean set = s.workPose() == IdlePoses.SERF_WORK;
		s.getEntityData().packDirty();
		s.setWorkPose(IdlePoses.SERF_WORK);
		boolean quiet = !s.getEntityData().isDirty();
		s.setWorkPose(0);
		SelfTest.check("serf work pose: synced byte set and cleared, nothing re-sent while unchanged", set && quiet && s.workPose() == 0, set + " " + quiet + " " + s.workPose());
		StringBuilder bad = new StringBuilder();
		for (String v : Serf.TIERS) {
			s.variant = v;
			s.syncLook();
			if (!s.syncedVariant().equals(v)) bad.append(v).append("->").append(s.syncedVariant()).append(' ');
		}
		s.variant = "unknown_tier";
		s.syncLook();
		if (!s.syncedVariant().equals("serf")) bad.append("unknown->").append(s.syncedVariant());
		SelfTest.check("serf tier is synced for clients (all four tiers; unknown reads as serf)", bad.length() == 0, bad.length() == 0 ? null : bad);
		s.variant = "thrall";
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		s.saveWithoutId(out);
		CompoundTag ct = out.buildResult();
		Serf copy = VillagesContent.SERF.create(level, EntitySpawnReason.LOAD);
		if (copy != null) copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), ct));
		SelfTest.check("serf tier is synced again after save and load", copy != null && copy.syncedVariant().equals("thrall"), copy == null ? null : copy.syncedVariant());
		s.discard();
		if (copy != null) copy.discard();
	}
}
