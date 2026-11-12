package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 polish P3 (s-p3-nameplates): court rulers carry the synced nameplate rank (4 sovereign, 3 lord) that the kingdom
 * client draws as a crown or coronet plate with their own name. Rulers are created like CourtLife.spawnRuler does, but
 * never added to the world or the court state, so no other step sees them. Needs the kingdom 3.0 jar with
 * {@code KingdomSoldier.nameplateRank()} (an older devlib jar gives rank 1 and these checks fail).
 * Appended after SelfTest.build() by a SERVER_STARTED listener in DevEntry.
 */
public final class SelfTestNameplates {
	private SelfTestNameplates() {}

	public static void add() {
		STEPS.add((srv, l) -> {
			CourtRuler king = ruler(l, Ruler.KING);
			CourtRuler marshal = ruler(l, Ruler.MARSHAL);
			check("nameplate P3: the King's plate rank is sovereign (4)", king != null && king.rank() == 4, king == null ? null : king.rank());
			check("nameplate P3: a Lord's plate rank is lord (3)", marshal != null && marshal.rank() == 3, marshal == null ? null : marshal.rank());
			check("nameplate P3: rulers have a synced name for their plate", king != null && king.hasCustomName() && marshal != null && marshal.hasCustomName(), null);
			if (marshal != null) {
				marshal.setCrowned(true);
				check("nameplate P3: a crowned Lord's plate becomes sovereign (succession resync)", marshal.rank() == 4, marshal.rank());
				TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, l.registryAccess());
				marshal.saveWithoutId(out);
				CompoundTag tag = out.buildResult();
				CourtRuler copy = (CourtRuler) marshal.getType().create(l, EntitySpawnReason.LOAD);
				if (copy != null) copy.load(TagValueInput.create(ProblemReporter.DISCARDING, l.registryAccess(), tag));
				check("nameplate P3: a crowned Lord loads back as sovereign (crown read before the resync)", copy != null && copy.crowned && copy.rank() == 4, copy == null ? null : copy.rank());
				if (copy != null) copy.discard();
				marshal.setCrowned(false);
				check("nameplate P3: an uncrowned Lord is a lord again", marshal.rank() == 3, marshal.rank());
			}
			check("nameplate P3: a ruler's faction is synced for the crest and tint", king != null && king.syncedFaction().equals(Factions.ALDERMARK.toString()), king == null ? null : king.syncedFaction());
			if (king != null) king.discard();
			if (marshal != null) marshal.discard();
			return 1;
		});
	}

	/** Set up like CourtLife.spawnRuler (faction, unit, loadout and name), not added to the level. */
	private static @Nullable CourtRuler ruler(ServerLevel l, Ruler r) {
		if (r.type == null) return null;
		CourtRuler e = (CourtRuler) r.type.create(l, EntitySpawnReason.COMMAND);
		if (e == null) return null;
		e.snapTo(0.5, 100, 0.5, 0F, 0F);
		e.setFaction(Factions.ALDERMARK);
		e.setUnit(r.unitId());
		return e;
	}
}
