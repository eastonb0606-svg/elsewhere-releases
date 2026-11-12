package dev.elsewhere.arsenal.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.ArsenalTooltips;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.arsenal.spell.GearV2;
import dev.elsewhere.arsenal.spell.RepeaterItem;
import dev.elsewhere.arsenal.spell.Spells;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 3.0 polish P5 checks (s-p5-tooltips): the arsenal tooltip lines show the items' real numbers. Static checks compare
 * every line argument with the stat the item runs on; behaviour checks cast the real spell on a test player and compare
 * what happened (cooldown length, Glimmer spent, damage dealt, ward size) with what the tooltip says. Hooked from
 * {@link SelfTest#body} inside the arena platform (needs entity-ticking chunks for the Sunlance target).
 */
final class SelfTestTooltips {
	private SelfTestTooltips() {}

	/** Every arsenal key P5 shows or gives numbers to (the 2.1 caster_piece line is the Callings-off fallback, not listed). */
	static final String[] CLIENT_KEYS = {"elsewhere.tooltip.amethyst_wand", "elsewhere.tooltip.sunlance_staff", "elsewhere.tooltip.stormcoil_staff",
		"elsewhere.tooltip.sculk_resonator", "elsewhere.tooltip.mirrorglass_prism", "elsewhere.tooltip.hushward_bell", "elsewhere.tooltip.tonic",
		"elsewhere.tooltip.moonlit", "elsewhere.tooltip.stormsage_mantle", ArsenalTooltips.HOLD};

	static void run(ServerLevel l, BlockPos base) {
		Item[] stat = {ArsenalItems.AMETHYST_WAND, ArsenalItems.SUNLANCE_STAFF, ArsenalItems.STORMCOIL_STAFF, ArsenalItems.SCULK_RESONATOR,
			ArsenalItems.MIRRORGLASS_PRISM, ArsenalItems.HUSHWARD_BELL, ArsenalItems.CLOCKWORK_REPEATER, ArsenalItems.GLIMMER_TONIC,
			ArsenalItems.STRONG_GLIMMER_TONIC, ArsenalItems.GLIMMERSTONE_FOCUS, ArsenalItems.MOONSILK_ROBE, ArsenalItems.LINED_MOONSILK_BOOTS};
		SelfTest.checkS("P5 tooltip: 12 arsenal stat items have lines; thread, mote and the Diadem none", () -> {
			for (Item i : stat) if (ArsenalTooltips.of(new ItemStack(i)).isEmpty()) throw new IllegalStateException(id(i));
			return ArsenalTooltips.of(new ItemStack(ArsenalItems.MOONSILK_THREAD)).isEmpty() && ArsenalTooltips.of(new ItemStack(ArsenalItems.GLIMMER_MOTE)).isEmpty()
				&& ArsenalTooltips.of(new ItemStack(ArsenalItems.CANTORS_DIADEM)).isEmpty() && ArsenalTooltips.of(ItemStack.EMPTY).isEmpty();
		});
		SelfTest.checkS("P5 tooltip: no Shift = summary + hint line, Shift = summary + detail (Sunlance)", () -> {
			ItemStack s = new ItemStack(ArsenalItems.SUNLANCE_STAFF);
			ArsenalTooltips.Lines ln = ArsenalTooltips.of(s);
			List<Component> lo = ArsenalTooltips.lines(s, false), hi = ArsenalTooltips.lines(s, true);
			return ln.summary().size() == 1 && ln.detail().size() >= 3 && lo.size() == 2 && ArsenalTooltips.HOLD.equals(key(lo.get(1)))
				&& hi.size() == 1 + ln.detail().size() && hi.stream().noneMatch(c -> ArsenalTooltips.HOLD.equals(key(c)));
		});

		// ---- every line: translated, placeholder count = argument count, and no digit typed into the lang text
		Set<String> keys = new LinkedHashSet<>(List.of(CLIENT_KEYS));
		List<Component> all = new ArrayList<>();
		for (Item i : ArsenalItems.ALL) {
			all.addAll(ArsenalTooltips.of(new ItemStack(i)).summary());
			all.addAll(ArsenalTooltips.of(new ItemStack(i)).detail());
		}
		SelfTest.checkS("P5 tooltip: every arsenal P5 line is translated, its %s count matches its arguments, no number is typed into the lang text", () -> {
			java.util.Map<String, String> lang = shippedLang("elsewhere.p5.spell");
			for (Component c : all) {
				TranslatableContents t = (TranslatableContents) c.getContents();
				keys.add(t.getKey());
				int slots = lang.getOrDefault(t.getKey(), "").replace("%%", "").split("%s", -1).length - 1;
				if (slots != t.getArgs().length) throw new IllegalStateException(t.getKey() + " has " + slots + " slots for " + t.getArgs().length + " args");
			}
			String[][] client = {{"elsewhere.tooltip.tonic", "3"}, {"elsewhere.tooltip.moonlit", "1"}, {"elsewhere.tooltip.stormsage_mantle", "1"}};
			for (String[] kc : client) {
				int slots = lang.getOrDefault(kc[0], "").replace("%%", "").split("%s", -1).length - 1;
				if (slots != Integer.parseInt(kc[1])) throw new IllegalStateException(kc[0] + " has " + slots + " slots");
			}
			for (String k : keys) {
				if (!lang.containsKey(k)) throw new IllegalStateException("untranslated " + k);
				if (lang.get(k).matches(".*\\d.*")) throw new IllegalStateException("digit in " + k + ": " + lang.get(k));
			}
			return keys.size() > 30;
		});
		SelfTest.check("P5 tooltip: the existing tonic, Moonlit and Mantle lines get their numbers from the stats",
			args(ArsenalTooltips.tonicArgs(ArsenalItems.GLIMMER_TONIC), ArsenalStats.TONIC_RESTORE, ArsenalStats.TONIC_SURGE_MULT, ArsenalStats.TONIC_SURGE_TICKS / 20.0)
				&& args(ArsenalTooltips.tonicArgs(ArsenalItems.STRONG_GLIMMER_TONIC), ArsenalStats.STRONG_TONIC_RESTORE, ArsenalStats.TONIC_SURGE_MULT, ArsenalStats.TONIC_SURGE_TICKS / 20.0)
				&& args(ArsenalTooltips.moonlitArgs(), (ArsenalStats.MOONLIT_BONUS - 1) * 100) && args(ArsenalTooltips.mantleArgs(), ArsenalStats.MANTLE_AIR_REGEN_MULT),
			String.join("/", str(ArsenalTooltips.tonicArgs(ArsenalItems.GLIMMER_TONIC))));

		// ---- static: the summary/detail arguments are the item stats
		CasterItem wand = (CasterItem) ArsenalItems.AMETHYST_WAND, sun = (CasterItem) ArsenalItems.SUNLANCE_STAFF, storm = (CasterItem) ArsenalItems.STORMCOIL_STAFF;
		SelfTest.check("P5 tooltip: caster damage and cooldown = ArsenalStats and the caster's own use delay (wand, Sunlance, Stormcoil)",
			ArsenalTooltips.delayTicks(wand) == ArsenalStats.WAND_DELAY_TICKS && ArsenalTooltips.delayTicks(sun) == ArsenalStats.SUNLANCE_DELAY_TICKS
				&& ArsenalTooltips.delayTicks(storm) == ArsenalStats.STORMCOIL_DELAY_TICKS
				&& args(summary(wand), ArsenalStats.WAND_DAMAGE, ArsenalStats.WAND_DELAY_TICKS / 20.0)
				&& args(summary(sun), ArsenalStats.SUNLANCE_DAMAGE, ArsenalStats.SUNLANCE_DELAY_TICKS / 20.0)
				&& args(summary(storm), ArsenalStats.STORMCOIL_DAMAGE, ArsenalStats.STORMCOIL_DELAY_TICKS / 20.0),
			String.join("/", str(summary(sun))));
		SelfTest.check("P5 tooltip: Resonator, Prism, Bell and Repeater summaries = their stats",
			args(summary(ArsenalItems.SCULK_RESONATOR), ArsenalStats.RESONATOR_DAMAGE, ArsenalStats.RESONATOR_CHARGE_TICKS / 20.0)
				&& args(summary(ArsenalItems.MIRRORGLASS_PRISM), GearV2.PRISM_DPS_LOW, GearV2.PRISM_DPS_HIGH)
				&& args(summary(ArsenalItems.HUSHWARD_BELL), 4.0 * (GearV2.BELL_ABSORB_AMPLIFIER + 1), GearV2.BELL_WARD_TICKS / 20.0)
				&& args(summary(ArsenalItems.CLOCKWORK_REPEATER), RepeaterItem.BURST, RepeaterItem.RELOAD_TICKS / 20.0),
			String.join("/", str(summary(ArsenalItems.MIRRORGLASS_PRISM))));
		SelfTest.check("P5 tooltip: detail lines = their stats (Sunlance targets/range/fire/costs, Stormcoil arcs, Resonator armor, Prism ramp, Bell radius)",
			args(detail(sun, "elsewhere.p5.sunlance.line"), ArsenalStats.SUNLANCE_TARGETS, ArsenalStats.SUNLANCE_RANGE)
				&& args(detail(sun, "elsewhere.p5.sunlance.fire"), ArsenalStats.SUNLANCE_FIRE_SECONDS)
				&& args(detail(sun, "elsewhere.p5.base_cost"), ArsenalStats.SUNLANCE_COST)
				&& args(detail(sun, "elsewhere.p5.sunlance.cost"), ArsenalStats.SUNLANCE_COST_DAY, ArsenalStats.SUNLANCE_COST_DARK)
				&& args(detail(storm, "elsewhere.p5.stormcoil.arcs"), ArsenalStats.STORMCOIL_ARCS, ArsenalStats.STORMCOIL_ARC_RADIUS, ArsenalStats.STORMCOIL_ARC_DAMAGE, ArsenalStats.STORMCOIL_ARCS_WET)
				&& args(detail(ArsenalItems.SCULK_RESONATOR, "elsewhere.p5.resonator.armor"), ArsenalStats.RESONATOR_ARMOR_IGNORED * 100, ArsenalStats.RESONATOR_KNOCKBACK)
				&& args(detail(ArsenalItems.MIRRORGLASS_PRISM, "elsewhere.p5.prism.ramp"), GearV2.PRISM_DPS_HIGH, GearV2.PRISM_RAMP_TICKS / 20.0)
				&& args(detail(ArsenalItems.MIRRORGLASS_PRISM, "elsewhere.p5.prism.cost"), GearV2.PRISM_COST)
				&& args(detail(ArsenalItems.HUSHWARD_BELL, "elsewhere.p5.bell.silence"), GearV2.BELL_RADIUS)
				&& args(detail(ArsenalItems.CLOCKWORK_REPEATER, "elsewhere.p5.repeater.cooldown"), (RepeaterItem.COOLDOWN + RepeaterItem.BURST * RepeaterItem.GAP) / 20.0)
				&& args(detail(ArsenalItems.GLIMMER_TONIC, "elsewhere.p5.tonic.dazed"), ArsenalStats.DAZED_SPELL_DAMAGE * 100, ArsenalStats.DAZED_TICKS / 20.0, ArsenalStats.DAZED_MAX_TICKS / 20.0)
				&& args(summary(ArsenalItems.GLIMMERSTONE_FOCUS), ArsenalStats.FOCUS_MAX, ArsenalStats.FOCUS_REGEN * 100), "");
		int[] others = {0, 0};
		SelfTest.checkS("P5 tooltip: another module's caster (gear3) shows its real use delay", () -> {
			for (Item i : BuiltInRegistries.ITEM) {
				if (!(i instanceof CasterItem c) || ArsenalItems.ALL.contains(i) || ArsenalTooltips.delayTicks(c) <= 0) continue;
				others[0]++;
				if (args(summary(i), ArsenalTooltips.delayTicks(c) / 20.0)) others[1]++;
			}
			return others[0] == others[1];
		});
		SelfTest.LOG.add("INFO P5 tooltip: " + others[0] + " other-module casters checked");

		// ---- behaviour: cast the real items on a test player and compare with the tooltip
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("arsenal-p5".getBytes()), "P5Tester"));
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0); // facing +z (south)
		p.getFoodData().setFoodLevel(20);
		Calling.recompute(p, true);
		for (CasterItem c : List.of(wand, sun, storm)) {
			ItemStack st = new ItemStack((Item) c);
			Glimmer.setForTest(p, Glimmer.max(p));
			boolean cast = c.castNow(l, p, st, InteractionHand.MAIN_HAND);
			int shown = (int) Math.round(Double.parseDouble((String) argsOf(summary(c))[1]) * 20);
			int ticks = 0;
			while (p.getCooldowns().isOnCooldown(st) && ticks < 400) {
				p.getCooldowns().tick();
				ticks++;
			}
			SelfTest.check("P5 tooltip: " + id((Item) c) + " cooldown shown = the cooldown a real cast applies", cast && ticks == shown, ticks + " ticks vs shown " + shown);
		}
		Glimmer.setForTest(p, Glimmer.max(p));
		float before = Glimmer.current(p);
		int base0 = Integer.parseInt((String) argsOf(detail(storm, "elsewhere.p5.base_cost"))[0]);
		boolean cast = storm.castNow(l, p, new ItemStack(ArsenalItems.STORMCOIL_STAFF), InteractionHand.MAIN_HAND);
		float spent = before - Glimmer.current(p);
		SelfTest.check("P5 tooltip: Stormcoil base cost shown, scaled by Glimmer.cost, = the Glimmer a real cast spends", cast && SelfTest.near(spent, Glimmer.cost(p, base0, false)),
			spent + " spent, base " + base0);

		// Sunlance on a husk in line: the damage dealt starts from the shown number
		Mob husk = SelfTest.mob(l, base.getX() + 0.5, base.getY(), base.getZ() + 3.5);
		l.getEntities(p, husk.getBoundingBox().inflate(1)); // warm-up query (GOTCHAS: the first query after addFreshEntity can miss)
		Glimmer.setForTest(p, Glimmer.max(p));
		Spells.lastDamage = -1;
		ItemStack lance = new ItemStack(ArsenalItems.SUNLANCE_STAFF);
		boolean hit = sun.castNow(l, p, lance, InteractionHand.MAIN_HAND);
		float dealt = Spells.lastDamage;
		float shownDmg = Float.parseFloat((String) argsOf(summary(sun))[0]);
		float expect = Spells.damage(p, husk, shownDmg, false, false);
		SelfTest.check("P5 tooltip: Sunlance damage shown is the base a real cast deals (same multipliers)", hit && dealt > 0 && SelfTest.near(dealt, expect),
			dealt + " dealt vs " + expect + " from shown " + shownDmg);
		husk.discard();

		// Hushward Bell: the ward shown = the absorption the Bell really grants
		p.removeAllEffects();
		p.setAbsorptionAmount(0);
		GearV2.clearWard(p);
		Glimmer.setForTest(p, Glimmer.max(p));
		boolean rang = ((CasterItem) ArsenalItems.HUSHWARD_BELL).castNow(l, p, new ItemStack(ArsenalItems.HUSHWARD_BELL), InteractionHand.MAIN_HAND);
		float ward = p.getAbsorptionAmount();
		SelfTest.check("P5 tooltip: Hushward ward shown = the absorption a real ring grants", rang && SelfTest.near(ward, Float.parseFloat((String) argsOf(summary(ArsenalItems.HUSHWARD_BELL))[0])),
			ward + " vs shown " + argsOf(summary(ArsenalItems.HUSHWARD_BELL))[0]);
		GearV2.clearWard(p);
		p.removeAllEffects();
		p.setAbsorptionAmount(0);

		// Clockwork Repeater: the burst cooldown shown = what fireBurst applies
		ItemStack rep = new ItemStack(ArsenalItems.CLOCKWORK_REPEATER);
		RepeaterItem.setLoaded(rep, RepeaterItem.BURST);
		RepeaterItem.fireBurst(p, rep); // the burst itself needs a listed player; a FakePlayer only gets the cooldown
		int rt = 0;
		while (p.getCooldowns().isOnCooldown(rep) && rt < 400) {
			p.getCooldowns().tick();
			rt++;
		}
		int shownRep = (int) Math.round(Double.parseDouble((String) argsOf(detail(ArsenalItems.CLOCKWORK_REPEATER, "elsewhere.p5.repeater.cooldown"))[0]) * 20);
		SelfTest.check("P5 tooltip: Repeater burst cooldown shown = the cooldown a real burst applies", rt == shownRep && RepeaterItem.loaded(rep) == 0, rt + " vs " + shownRep);
		Glimmer.setForTest(p, 0);
	}

	// ------------------------------------------------------------------------------------------------ helpers

	/**
	 * This module's lang as shipped: of every assets/elsewhere/lang/en_us.json on the classpath (dev runs also carry older
	 * devlib copies), the first that has {@code probe}, so the check reads the strings this build really ships.
	 */
	static java.util.Map<String, String> shippedLang(String probe) {
		try {
			var urls = SelfTestTooltips.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
			while (urls.hasMoreElements()) {
				try (var in = new java.io.InputStreamReader(urls.nextElement().openStream(), java.nio.charset.StandardCharsets.UTF_8)) {
					com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
					if (!o.has(probe)) continue;
					java.util.Map<String, String> m = new java.util.HashMap<>();
					for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
					return m;
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("lang read failed: " + e);
		}
		throw new IllegalStateException("no en_us.json with " + probe);
	}

	static String id(Item i) {
		return BuiltInRegistries.ITEM.getKey(i).getPath();
	}

	static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
	}

	static Component summary(Item i) {
		List<Component> s = ArsenalTooltips.of(new ItemStack(i)).summary();
		return s.isEmpty() ? Component.empty() : s.get(0);
	}

	static Component summary(CasterItem c) {
		return summary((Item) c);
	}

	static Component detail(Item i, String key) {
		for (Component c : ArsenalTooltips.of(new ItemStack(i)).detail()) if (key.equals(key(c))) return c;
		return Component.empty();
	}

	static Component detail(CasterItem c, String key) {
		return detail((Item) c, key);
	}

	static Object[] argsOf(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
	}

	static String[] str(Object[] a) {
		String[] s = new String[a.length];
		for (int i = 0; i < a.length; i++) s[i] = String.valueOf(a[i]);
		return s;
	}

	static String[] str(Component c) {
		return str(argsOf(c));
	}

	/** The line's arguments are exactly these numbers, formatted the way tooltips format them. */
	static boolean args(Component c, double... want) {
		return args(argsOf(c), want);
	}

	static boolean args(Object[] got, double... want) {
		if (got.length != want.length) return false;
		for (int i = 0; i < want.length; i++) if (!ArsenalTooltips.fmt(want[i]).equals(got[i])) return false;
		return true;
	}
}
