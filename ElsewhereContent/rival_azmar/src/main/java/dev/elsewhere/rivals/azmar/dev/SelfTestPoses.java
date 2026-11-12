package dev.elsewhere.rivals.azmar.dev;

import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.MovePose;
import dev.elsewhere.duelist.boss.PoseProbe;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * 3.0 P4/B4 (s-p4-rivalanims): every Azmar boss winds up and strikes each of its moves with its own pose key (the synced byte
 * read back at the wind-up and right after the move fires, on a boss outside the level), and moves in the Azmar style.
 * Moves scripted outside SignatureMove: Amir's Final Charge, the Sultana's Scarab Shell, the Wazir's Seal, the Falconer's Storm.
 */
final class SelfTestPoses {
	private SelfTestPoses() {}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			Map<EntityType<? extends HumanBoss>, List<String>> bosses = Map.of(
				AzmarBosses.SULTANA, List.of("swing", "scarab_shell"),
				AzmarBosses.AMIR, List.of("swing", "final_charge"),
				AzmarBosses.WAZIR, List.of("swing", "seal_of_ibis"),
				AzmarBosses.FALCONER, List.of("swing", "falcon_storm"));
			boolean style = true;
			for (var e : bosses.entrySet()) {
				String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getKey()).getPath();
				List<PoseProbe.Result> r = PoseProbe.probeType(l, e.getKey(), e.getValue());
				boolean ok = !r.isEmpty();
				StringBuilder sb = new StringBuilder();
				for (PoseProbe.Result x : r) {
					ok &= x.ok();
					sb.append(x.move()).append('=').append(MovePose.name(x.key())).append(x.ok() ? "" : "!").append(' ');
				}
				SelfTest.check("B4 poses: " + path + " winds up and strikes every move with its own pose key (" + r.size() + " moves)", ok, sb.toString().trim());
				style &= PoseTable.kindOf(path) == PoseTable.AZMAR;
			}
			SelfTest.check("B4 poses: every Azmar boss moves in the Azmar style (quick snap, low guard, whipping torso)", style, null);
			return 0;
		});
	}
}
