package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose;
import dev.elsewhere.kingdom.idle.IdlePoses;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * 3.0 polish P9 (s-p9-idle): the routine pose sink and the synced idle byte on soldiers. Appended to SelfTest's step list
 * from a tick listener in DevEntry (the SelfTestPolish pattern), after SelfTest.build() has run.
 */
public final class SelfTestIdle {
	private SelfTestIdle() {}

	private static boolean added;
	private static KingdomSoldier still;
	private static long syncedBefore, changesBefore;

	public static void tick(MinecraftServer srv) {
		if (added || !SelfTest.started) return;
		added = true;
		STEPS.add((s, l) -> {
			clean(l);
			boolean was = IdlePoses.enabled;
			IdlePoses.enabled = true;
			var a = Factions.ALDERMARK;
			KingdomSoldier pawn = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, a, at(0, 64), null);
			KingdomSoldier other = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, a, at(3, 64), null);
			if (pawn == null || other == null) {
				check("idle: test soldiers spawned", false, pawn + " " + other);
				IdlePoses.enabled = was;
				return 2;
			}
			check("idle: a new soldier has no idle pose", pawn.idlePose() == 0, pawn.idlePose());
			RoutinePoses.set(pawn, Pose.SIT);
			check("idle: the routine sink puts SIT on the synced byte", pawn.idlePose() == Pose.SIT.id && RoutinePoses.get(pawn) == Pose.SIT, pawn.idlePose());
			check("idle: the sink handled it (no vanilla crouch fallback)", pawn.getPose() != net.minecraft.world.entity.Pose.CROUCHING, pawn.getPose());
			pawn.getEntityData().packDirty();
			IdlePoses.sink(pawn, Pose.SIT);
			check("idle: an unchanged pose sends nothing (data not dirty)", !pawn.getEntityData().isDirty(), null);
			RoutinePoses.set(pawn, Pose.SALUTE);
			check("idle: a changed pose is marked for one send", pawn.getEntityData().isDirty() && pawn.idlePose() == Pose.SALUTE.id, pawn.idlePose());
			boolean all = true;
			StringBuilder bad = new StringBuilder();
			for (Pose p : Pose.values()) {
				IdlePoses.sink(pawn, p);
				if (pawn.idlePose() != p.id) {
					all = false;
					bad.append(p).append('=').append(pawn.idlePose()).append(' ');
				}
			}
			check("idle: every routine pose id round-trips through the byte", all, bad.length() == 0 ? null : bad);
			RoutinePoses.set(pawn, Pose.NONE);
			check("idle: NONE clears the pose", pawn.idlePose() == 0 && RoutinePoses.get(pawn) == Pose.NONE, pawn.idlePose());

			// a soldier saved while sitting at the fire sits again when it loads (the routine tag survives; ENTITY_LOAD restores the byte)
			RoutinePoses.set(pawn, Pose.SIT);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, l.registryAccess());
			pawn.saveWithoutId(out);
			CompoundTag ct = out.buildResult();
			pawn.discard();
			KingdomSoldier copy = (KingdomSoldier) pawn.getType().create(l, EntitySpawnReason.LOAD);
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, l.registryAccess(), ct));
			int before = copy.idlePose();
			boolean ok = l.addFreshEntity(copy);
			check("idle: pose restored from the saved routine tag on load", ok && copy.idlePose() == Pose.SIT.id, before + " -> " + copy.idlePose() + (ok ? "" : " (not added)"));

			// kill switch: the sink declines, the routine's vanilla fallback applies (crouch for the low poses)
			IdlePoses.enabled = false;
			RoutinePoses.set(other, Pose.KNEEL);
			check("idle: kill switch off leaves the byte alone and the fallback crouches", other.idlePose() == 0 && other.getPose() == net.minecraft.world.entity.Pose.CROUCHING,
				other.idlePose() + " " + other.getPose());
			RoutinePoses.set(other, Pose.NONE);
			IdlePoses.enabled = true;
			check("idle: the fallback stands the soldier up again", other.getPose() == net.minecraft.world.entity.Pose.STANDING, other.getPose());
			IdlePoses.enabled = was;

			// no per-tick work: a posed soldier changes nothing while it sits (checked in the next step, 40 ticks later)
			RoutinePoses.set(other, Pose.DRINK);
			still = other;
			syncedBefore = IdlePoses.synced;
			changesBefore = RoutinePoses.changes;
			return 40;
		});
		STEPS.add((s, l) -> {
			KingdomSoldier o = still;
			check("idle: no pose traffic while a soldier keeps its pose (40 ticks)", o != null && o.isAlive() && o.idlePose() == Pose.DRINK.id
				&& IdlePoses.synced == syncedBefore && RoutinePoses.changes == changesBefore,
				o == null ? null : o.idlePose() + " synced +" + (IdlePoses.synced - syncedBefore) + " changes +" + (RoutinePoses.changes - changesBefore));
			still = null;
			clean(l);
			return 2;
		});
	}
}
