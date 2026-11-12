package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.info;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.armies.ArmyScale;
import dev.elsewhere.rivals.azmar.AzmarContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.Identifier;

/**
 * Azmar is the smallest kingdom in every army size row (RIVALS-DECISIONS 19, standing user ruling; 3.0.0 final review B3). Every
 * {@code army.scale} size row is strictly below every other loaded kingdom's and at most 75% of Aldermark's; the legacy patrol sizes and
 * the army-size multiplier are the smallest too. Azmar keeps its lore unit (the rank block: a 2x2 block and its jemadar) and wins by
 * brains, so its brain edge is pinned as well. Compares against every realm kingdom loaded: Aldermark in the lane run, all four in the
 * merged jar.
 */
final class SelfTestSmallest {
	private SelfTestSmallest() {}

	record Row(String name, ToDoubleFunction<ArmyScale> value) {}

	/** Every size row of {@code army.scale} (ARMIES.md 2.2 / 9.3). */
	static final List<Row> ROWS = List.of(
		new Row("patrol by day", ArmyScale::patrolDay),
		new Row("patrol by night", ArmyScale::patrolNight),
		new Row("squad (rank block)", ArmyScale::squadSize),
		new Row("company (regiment)", ArmyScale::companySize),
		new Row("village garrison min", s -> s.village()[0]),
		new Row("village garrison max", s -> s.village()[1]),
		new Row("town garrison min", s -> s.town()[0]),
		new Row("town garrison max", s -> s.town()[1]),
		new Row("capital garrison min", s -> s.capital()[0]),
		new Row("capital garrison max", s -> s.capital()[1]),
		new Row("field army min", s -> s.fieldArmy()[0]),
		new Row("field army max", s -> s.fieldArmy()[1]),
		new Row("battle side max", ArmyScale::battleSideMax),
		new Row("live front", ArmyScale::liveFront),
		new Row("wave size", s -> s.wave().size()),
		new Row("capital residents", ArmyScale::residentsCapital),
		new Row("town residents", ArmyScale::residentsTown));

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			Identifier az = AzmarContent.FACTION;
			ArmyScale a = ArmyScale.of(az);
			ArmyScale ald = ArmyScale.of(Factions.ALDERMARK);
			// every other kingdom with army data (data-defined factions only), and each realm kingdom that is loaded must have it
			List<ArmyScale> others = new ArrayList<>();
			for (ArmyScale s : ArmyScale.all().values()) if (!s.faction().equals(az)) others.add(s);
			List<String> missing = new ArrayList<>();
			for (String k : List.of("aldermark", "hrafnholt", "mirewick")) {
				Identifier id = Identifier.fromNamespaceAndPath(az.getNamespace(), k);
				if ((k.equals("aldermark") || FactionDefs.get(id) != null) && ArmyScale.of(id) == null) missing.add(k);
			}
			info("smallest-kingdom check compares Azmar with " + others.stream().map(s -> s.faction().getPath()).toList());
			check("Azmar army.scale loaded, and every other realm kingdom loaded has army.scale to compare (Aldermark at least)",
				a != null && ald != null && missing.isEmpty() && !others.isEmpty(), "missing " + missing);
			if (a == null || others.isEmpty()) return 0;
			check("Azmar keeps its lore unit: rank_block of " + a.squadSize() + " = " + a.formation().width() + "x" + a.formation().depth()
				+ " block + jemadar; regiment of 2 blocks = " + a.companySize() + "; a relief wave is one block (" + a.wave().size() + ")",
				"rank_block".equals(a.unitName()) && a.formation().width() * a.formation().depth() + 1 == a.squadSize()
					&& a.companySize() == 2 * a.squadSize() && a.wave().size() == a.squadSize() && "relief".equals(a.wave().style()), a);
			for (Row r : ROWS) {
				double v = r.value.applyAsDouble(a);
				double least = Double.MAX_VALUE;
				String by = "";
				for (ArmyScale o : others) {
					double ov = r.value.applyAsDouble(o);
					if (ov < least) {
						least = ov;
						by = o.faction().getPath();
					}
				}
				double cap = ald == null ? Double.MAX_VALUE : 0.75 * r.value.applyAsDouble(ald);
				check("Azmar smallest in every row: " + r.name + " " + fmt(v) + " < every other kingdom (next " + by + " " + fmt(least) + ")"
					+ (ald == null ? "" : " and <= 75% of Aldermark (" + fmt(cap) + ")"), v < least && v <= cap + 1e-9, null);
			}
			// the legacy patrol keys still drive live patrols (Multipliers.patrolSize); the army-size multiplier scales garrisons and patrols
			FactionDef d = FactionDefs.get(az);
			if (d != null) {
				boolean ok = true;
				StringBuilder s = new StringBuilder();
				for (ArmyScale o : others) {
					FactionDef od = FactionDefs.get(o.faction());
					if (od == null) continue;
					ok &= d.army().patrolDay() < od.army().patrolDay() && d.army().patrolNight() < od.army().patrolNight()
						&& d.army().sizeMultiplier() < od.army().sizeMultiplier();
					s.append(o.faction().getPath()).append(' ').append(od.army().patrolDay()).append('/').append(od.army().patrolNight())
						.append(" x").append(od.army().sizeMultiplier()).append("; ");
				}
				check("Azmar smallest in every row: live patrols " + d.army().patrolDay() + "/" + d.army().patrolNight() + " (caravan) and army size x"
					+ d.army().sizeMultiplier() + " below every other kingdom", ok, s);
				// smallest by numbers, best by brains: the sharpest intel, the most veterancy, the only kingdom that waits for 1.5:1 and retreats
				FactionDef.Brain b = d.army().brain();
				boolean brains = b.minAttackOdds() >= 1.5 && b.retreatInsteadOfDie() && b.supplyLineOnly();
				for (ArmyScale o : others) {
					FactionDef od = FactionDefs.get(o.faction());
					if (od == null) continue;
					FactionDef.Brain ob = od.army().brain();
					brains &= b.intelligenceNoise() < ob.intelligenceNoise() && b.veterancyCap() > ob.veterancyCap() && b.minAttackOdds() > ob.minAttackOdds();
				}
				check("Azmar still wins by brains: lowest intel noise " + b.intelligenceNoise() + ", highest veterancy cap " + b.veterancyCap()
					+ ", attacks only at " + b.minAttackOdds() + ":1, retreats instead of dying, supply lines; line relief every " + a.wave().intervalSeconds()
					+ " s at " + Math.round(a.wave().triggerFront() * 100) + "% of its front",
					brains && "relief".equals(a.wave().style()) && a.wave().intervalSeconds() <= 30 && a.wave().triggerFront() >= 0.85, null);
			}
			return 0;
		});
	}

	static String fmt(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v);
	}
}
