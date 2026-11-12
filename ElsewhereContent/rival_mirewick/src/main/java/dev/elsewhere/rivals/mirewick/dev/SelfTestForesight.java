package dev.elsewhere.rivals.mirewick.dev;

import dev.elsewhere.duelist.foresight.ForesightCheck;
import dev.elsewhere.rivals.mirewick.boss.Magnate;

/**
 * 3.0 B5 (s-foresight): no attack hints without foresight gear. The first Magnate winds up beside two players, one holding an item of
 * {@code #elsewhere:foresight_gear} in the off hand and one with nothing: the wind-up particles and the bar's move label reach
 * only the player with gear. In this module's own run the tag gets {@code minecraft:spyglass} from the dev world pack
 * {@code run/world/datapacks/elsewhere_foresight_test}; in the merged build it holds the real foresight gear.
 */
final class SelfTestForesight {
	private SelfTestForesight() {}

	private static ForesightCheck fc;

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			fc = ForesightCheck.begin(l, Magnate.values()[0].type, 40, -40);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			if (fc != null) fc.probe();
			return 12;
		});
		SelfTest.STEPS.add((srv, l) -> {
			if (fc == null) SelfTest.check("B5 foresight: the probe boss and players were set up", false, "begin failed");
			else for (ForesightCheck.Line x : fc.finish()) SelfTest.check(x.name(), x.ok(), x.detail());
			fc = null;
			return 0;
		});
	}
}
