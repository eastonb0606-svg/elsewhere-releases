package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * 3.0 polish P3: the synced rank/faction fields behind the soldier nameplates. Appended to SelfTest's step list from a
 * tick listener in DevEntry (SelfTest.add is claimed by another lane), after SelfTest.build() has run.
 */
public final class SelfTestPolish {
	private SelfTestPolish() {}

	private static boolean added;

	public static void tick(MinecraftServer srv) {
		if (added || !SelfTest.started) return;
		added = true;
		STEPS.add((s, l) -> {
			clean(l);
			var a = Factions.ALDERMARK;
			KingdomSoldier pawn = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, a, at(0, 60), null);
			KingdomSoldier sgt = KingdomApi.spawnSoldier(l, UnitTypes.SERGEANT, a, at(2, 60), null);
			KingdomSoldier cap = KingdomApi.spawnSoldier(l, UnitTypes.CAPTAIN, a, at(4, 60), null);
			check("nameplate: man-at-arms is rank 0, faction synced", pawn != null && pawn.rank() == 0 && pawn.syncedFaction().equals(a.toString()), pawn == null ? null : pawn.rank() + " " + pawn.syncedFaction());
			check("nameplate: sergeant is rank 1 (officer)", sgt != null && sgt.rank() == 1, sgt == null ? null : sgt.rank());
			check("nameplate: captain is rank 2", cap != null && cap.rank() == 2, cap == null ? null : cap.rank());
			if (pawn != null) {
				pawn.setUnit(UnitTypes.CAPTAIN);
				check("nameplate: rank follows a unit change", pawn.rank() == 2, pawn.rank());
				pawn.setFaction(Factions.REBELS);
				check("nameplate: faction follows a faction change", pawn.syncedFaction().equals(Factions.REBELS.toString()), pawn.syncedFaction());
				TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, l.registryAccess());
				pawn.saveWithoutId(out);
				CompoundTag ct = out.buildResult();
				KingdomSoldier copy = (KingdomSoldier) pawn.getType().create(l, EntitySpawnReason.LOAD);
				copy.load(TagValueInput.create(ProblemReporter.DISCARDING, l.registryAccess(), ct));
				check("nameplate: rank and faction survive save and load", copy.syncedFaction().equals(Factions.REBELS.toString()), copy.syncedFaction());
				copy.discard();
			}
			clean(l);
			return 2;
		});
	}
}
