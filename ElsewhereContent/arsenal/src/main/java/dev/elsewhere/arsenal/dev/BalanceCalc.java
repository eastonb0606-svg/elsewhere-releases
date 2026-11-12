package dev.elsewhere.arsenal.dev;

import dev.elsewhere.arsenal.spell.Calling;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Balance calculator (BUILD-3.0 full-scale test 7, GEAR.md B): every gear tier's median EHP (vs a 40-damage hit, Protection IV) and
 * median melee DPS (Sharpness V, Vanguard Primary on calling weapons) lands in its band relative to netherite. Same math and rows as
 * Ideas/overhaul/tools/balance_model.py. Where a weapon is registered in this jar, its real attack damage and speed replace the row.
 */
public final class BalanceCalc {
	private BalanceCalc() {}

	static final double SHARP5 = 3.0, PROT4 = 1 - 16 / 25.0, HIT = 40.0;

	/** tier -> {ehpLo, ehpHi, dpsLo, dpsHi} relative to netherite. */
	static final Map<String, double[]> BANDS = new LinkedHashMap<>();
	static {
		BANDS.put("G2", new double[] {0.84, 0.90, 0.88, 1.00});
		BANDS.put("G3", new double[] {0.97, 1.03, 0.97, 1.10});
		BANDS.put("G4", new double[] {1.00, 1.12, 1.08, 1.22});
		BANDS.put("G5", new double[] {1.08, 1.20, 1.15, 1.30});
	}

	record Armor(String name, String tier, double armor, double tough, double reduce) {}

	record Weapon(String name, String tier, String id, double dmg, double speed, double mult, double ignore) {}

	static final List<Armor> ARMOR = List.of(
		new Armor("Any G2 set + Netherite Lining", "G3", 20, 12, 0),
		new Armor("Sunwright (Forged)", "G4", 20, 12, 0),
		new Armor("Tidebrass (Forged)", "G4", 20, 13, 0),
		new Armor("Climber (Forged)", "G4", 20, 12, 0),
		new Armor("Hushweave (Forged)", "G4", 20, 11, 0),
		new Armor("Galvanic Coilmail (Forged)", "G4", 20, 12, 0),
		new Armor("Amberstill Plate (Forged, braced)", "G4", 20, 16, 0),
		new Armor("Galeplate (duelist)", "G4", 20, 10, 0),
		new Armor("Hearthguard (tank, holding)", "G4", 24, 14, 0.10),
		new Armor("Ulfhednar (berserker)", "G4", 20, 12, 0),
		new Armor("Ridgewatch (sniper)", "G4", 19, 10, 0),
		new Armor("Sunscribe Vestments (caster)", "G4", 20, 10, 0),
		new Armor("Elderbark Regalia (summoner)", "G4", 20, 12, 0),
		new Armor("Marshal's Panoply (commander)", "G4", 20, 12, 0),
		new Armor("Any Forged set + Ascension", "G5", 20, 16, 0));

	static final List<Weapon> WEAPONS = List.of(
		new Weapon("Lanternfang Estoc (riposte)", "G4", "mirewick_lanternfang_estoc", 6, 1.8, 1.11, 0),
		new Weapon("Glassfang", "G4", "glassfang", 9, 1.5, 1, 4),
		new Weapon("Kingsplitter", "G4", "kingsplitter", 12, 0.9, 1, 0),
		new Weapon("Ironclad Flail (ramp)", "G4", "ironclad_flail", 7, 1.25, 1.5, 0),
		new Weapon("Skald's Rune-Axe (3rd hit)", "G3", "hrafnholt_skalds_rune_axe", 10, 1.0, 1.167, 0),
		new Weapon("Gale Rapier (half the hits standing, -15%)", "G3", "gale_rapier", 5, 2.4, 0.925, 0),
		new Weapon("Echo-Forged Blade", "G4", null, 9, 1.4, 1, 0),
		new Weapon("Runeseax (hybrid)", "G4", "hrafnholt_runeseax", 7, 1.7, 1, 0),
		new Weapon("Sunstone sword", "G4", "sunstone_sword", 9, 1.6, 1, 0),
		new Weapon("Challenger's Cleaver", "G2", "challengers_cleaver", 7, 1.6, 1, 0),
		new Weapon("Ascended Glassfang (+5%)", "G5", null, 9, 1.5, 1.05, 4));

	static double afterArmor(double d, double armor, double tough) {
		double f = Math.min(20.0, Math.max(armor * 0.2, armor - d / (2.0 + tough / 4.0)));
		return d * (1.0 - f / 25.0);
	}

	static double ehp(double armor, double tough, double reduce) {
		return 20.0 / (afterArmor(HIT, armor, tough) / HIT * PROT4 * (1.0 - reduce));
	}

	/** DPS against an unarmored mob (armor ignore only matters vs armor; kept for the boss column of the model). */
	static double dps(double dmg, double speed, double mult) {
		return afterArmor((dmg + SHARP5) * mult, 0, 0) * speed;
	}

	static double median(List<Double> v) {
		double[] a = v.stream().mapToDouble(Double::doubleValue).toArray();
		Arrays.sort(a);
		return a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2;
	}

	/** {attack damage, attack speed} of a registered weapon from its default attribute modifiers, or null. */
	static double[] registered(String id) {
		if (id == null) return null;
		var key = Identifier.fromNamespaceAndPath("elsewhere", id);
		if (!BuiltInRegistries.ITEM.containsKey(key)) return null;
		ItemAttributeModifiers mods = new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double dmg = 1, spd = 4;
		boolean any = false;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
			if (e.attribute().is(Attributes.ATTACK_DAMAGE)) {
				dmg += e.modifier().amount();
				any = true;
			} else if (e.attribute().is(Attributes.ATTACK_SPEED)) spd += e.modifier().amount();
		}
		return any ? new double[] {dmg, spd} : null;
	}

	/** Runs the calculator; returns report lines, and each tier/kind check goes through {@code check}. */
	public static List<String> run(java.util.function.BiConsumer<String, Boolean> check) {
		List<String> out = new ArrayList<>();
		double nEhp = ehp(20, 12, 0), nDps = dps(8, 1.6, 1);
		check.accept("balance: netherite reference EHP 106.8 / DPS 17.6", Math.abs(nEhp - 106.8) < 0.15 && Math.abs(nDps - 17.6) < 0.05);
		double vPrimary = Calling.damageOf(Calling.masteryOf(Calling.Rank.PRIMARY, 0));
		check.accept("balance: Vanguard Primary D = 1.15, Unattuned D = 1.00", Math.abs(vPrimary - 1.15) < 1e-4
			&& Math.abs(Calling.damageOf(Calling.masteryOf(Calling.Rank.UNATTUNED, 0)) - 1.0) < 1e-4);
		Map<String, List<Double>> ea = new LinkedHashMap<>(), da = new LinkedHashMap<>();
		for (Armor a : ARMOR) ea.computeIfAbsent(a.tier, k -> new ArrayList<>()).add(ehp(a.armor, a.tough, a.reduce) / nEhp);
		int real = 0;
		for (Weapon w : WEAPONS) {
			double dmg = w.dmg, spd = w.speed;
			double[] r = registered(w.id);
			if (r != null) {
				dmg = r[0];
				spd = r[1];
				real++;
				out.add(String.format("balance: %s registered %.2f / %.2f (row %.2f / %.2f)", w.name, dmg, spd, w.dmg, w.speed));
			}
			double cm = "G2".equals(w.tier) ? 1.0 : vPrimary;
			da.computeIfAbsent(w.tier, k -> new ArrayList<>()).add(dps(dmg, spd, w.mult * cm) / nDps);
		}
		for (var e : BANDS.entrySet()) {
			String t = e.getKey();
			double[] b = e.getValue();
			if (ea.containsKey(t)) {
				double m = median(ea.get(t));
				out.add(String.format("balance: %s EHP median %.3f of %d in [%.2f, %.2f]", t, m, ea.get(t).size(), b[0], b[1]));
				check.accept(String.format("balance: %s EHP median %.3f in band", t, m), m >= b[0] - 1e-9 && m <= b[1] + 1e-9);
			}
			if (da.containsKey(t)) {
				double m = median(da.get(t));
				out.add(String.format("balance: %s DPS median %.3f of %d in [%.2f, %.2f]", t, m, da.get(t).size(), b[2], b[3]));
				check.accept(String.format("balance: %s DPS median %.3f in band", t, m), m >= b[2] - 1e-9 && m <= b[3] + 1e-9);
			}
		}
		out.add("balance: " + real + " weapon rows read from registered items");
		return out;
	}
}
