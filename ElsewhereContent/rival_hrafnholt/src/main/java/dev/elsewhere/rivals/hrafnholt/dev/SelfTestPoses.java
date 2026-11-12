package dev.elsewhere.rivals.hrafnholt.dev;

import dev.elsewhere.duelist.boss.PoseProbe;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.rivals.hrafnholt.boss.Hrafn;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * 3.0 P4/B4 (s-p4-rivalanims): every Hrafnholt boss, seat and Risen, winds up and strikes each of its moves with its own pose key
 * (the synced byte read back at the wind-up and right after the move fires, on a boss outside the level), and moves in the
 * Hrafnholt style. Moves scripted outside SignatureMove: the last stand and the boss's own swing.
 */
final class SelfTestPoses {
	private SelfTestPoses() {}

	static final List<String> SCRIPTED = List.of("swing", "last_stand");

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			boolean style = true;
			for (Hrafn h : Hrafn.values()) {
				List<EntityType<HrafnholtBoss>> types = new ArrayList<>();
				types.add(h.type);
				if (h.risenType != null) types.add(h.risenType);
				for (EntityType<HrafnholtBoss> t : types) {
					String path = BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath();
					List<PoseProbe.Result> r = PoseProbe.probeType(l, t, SCRIPTED);
					boolean ok = !r.isEmpty();
					StringBuilder sb = new StringBuilder();
					for (PoseProbe.Result x : r) {
						ok &= x.ok();
						sb.append(x.move()).append('=').append(dev.elsewhere.duelist.boss.MovePose.name(x.key())).append(x.ok() ? "" : "!").append(' ');
					}
					SelfTest.check("B4 poses: " + path + " winds up and strikes every move with its own pose key (" + r.size() + " moves)", ok, sb.toString().trim());
					style &= PoseTable.kindOf(path) == PoseTable.HRAFNHOLT;
				}
			}
			SelfTest.check("B4 poses: every Hrafnholt boss moves in the Hrafnholt style (heavy blows, wide stance, long recovery)", style, null);
			return 0;
		});
	}
}
