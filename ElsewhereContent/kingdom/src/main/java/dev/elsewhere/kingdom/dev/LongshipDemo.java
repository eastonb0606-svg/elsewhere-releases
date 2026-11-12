package dev.elsewhere.kingdom.dev;

import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.war.HrafnholtLongship;
import dev.elsewhere.kingdom.war.Longship;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Dev only (client screenshot runs call it by reflection): a real voyage of the test sea faction to a shore, crewed by eight kingdom soldiers. */
public final class LongshipDemo {
	private LongshipDemo() {}

	static Longship.Voyage last;

	public static String start(MinecraftServer srv, int tx, int ty, int tz) {
		ServerLevel lv = srv.overworld();
		if (!Factions.exists(SelfTestLongship.SEA)) Factions.register(SelfTestLongship.SEA, 0x2E5A2C, false);
		FactionDefs.putCodeDefined(SelfTestLongship.SEA, SelfTestLongship.seaDef());
		BlockPos target = new BlockPos(tx, ty, tz);
		Longship.Shore sh = Longship.findShore(lv, target);
		if (sh == null) return "no shore";
		last = Longship.sail(lv, SelfTestLongship.SEA, target, sh, 0, -1, -7, new Longship.Crew() {
			@Override
			public List<KingdomSoldier> spawn(BlockPos at) {
				return KingdomApi.spawnSquad(lv, at, Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
					List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.PIKEMAN, UnitTypes.CROSSBOWMAN, UnitTypes.SQUIRE, UnitTypes.MAN_AT_ARMS));
			}
		});
		return last == null ? "no route" : "ok " + sh;
	}

	/** "PHASE x y z passengers" of the demo ship. */
	public static String status(MinecraftServer srv) {
		if (last == null) return "NONE";
		ServerLevel lv = srv.overworld();
		HrafnholtLongship s = last.ship == null ? null : lv.getEntity(last.ship) instanceof HrafnholtLongship h ? h : null;
		if (s == null) return last.phase.name();
		return String.format(Locale.ROOT, "%s %.1f %.1f %.1f %d", last.phase.name(), s.getX(), s.getY(), s.getZ(), s.getPassengers().size());
	}

	public static int ships(MinecraftServer srv) {
		return srv.overworld().getEntities(KingdomEntities.LONGSHIP, e -> true).size();
	}
}
