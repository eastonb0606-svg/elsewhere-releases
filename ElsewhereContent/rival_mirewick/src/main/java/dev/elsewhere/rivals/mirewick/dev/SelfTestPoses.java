package dev.elsewhere.rivals.mirewick.dev;

import dev.elsewhere.duelist.boss.MovePose;
import dev.elsewhere.duelist.boss.PoseProbe;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 3.0 P4/B4 (s-p4-rivalanims): every Magnate winds up and strikes each of its moves with its own pose key (the synced byte read
 * back at the wind-up and right after the move fires, on a boss outside the level), and moves in the Mirewick style. Moves
 * scripted outside SignatureMove: the lantern and debt moves of MagnateBoss.
 */
final class SelfTestPoses {
	private SelfTestPoses() {}

	static final List<String> COMMON = List.of("swing", "paid_passage", "lamp_shove", "oil_flask", "lantern_rally");

	static List<String> scripted(Magnate m) {
		List<String> out = new ArrayList<>(COMMON);
		switch (m) {
			case MARROW -> out.add("double_debt");
			case THWAITE -> out.add("mire_flood");
			case SORREL -> out.add("sluice_open");
			case TOLLIVER -> out.add("duck_blind");
			case AMES -> out.add("scrip_scatter");
		}
		return out;
	}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			boolean style = true;
			for (Magnate m : Magnate.values()) {
				String path = BuiltInRegistries.ENTITY_TYPE.getKey(m.type).getPath();
				List<PoseProbe.Result> r = PoseProbe.probeType(l, m.type, scripted(m));
				boolean ok = !r.isEmpty();
				StringBuilder sb = new StringBuilder();
				for (PoseProbe.Result x : r) {
					ok &= x.ok();
					sb.append(x.move()).append('=').append(MovePose.name(x.key())).append(x.ok() ? "" : "!").append(' ');
				}
				SelfTest.check("B4 poses: " + path + " winds up and strikes every move with its own pose key (" + r.size() + " moves)", ok, sb.toString().trim());
				style &= PoseTable.kindOf(path) == PoseTable.MIREWICK;
			}
			SelfTest.check("B4 poses: every Magnate moves in the Mirewick style (measured, upright, chin up)", style, null);
			return 0;
		});
	}
}
