package dev.elsewhere.gear2.dev;

import dev.elsewhere.gear2.Gear2Hooks;
import dev.elsewhere.gear2.Gear2Items;
import dev.elsewhere.gear2.Gear2Stats;
import dev.elsewhere.gear2.Gear2Tooltips;
import dev.elsewhere.gear2.Termites;
import dev.elsewhere.gear2.item.MirrorboltArbalest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 3.0 polish P5 checks (s-p5-tooltips): the gear2 tooltip lines show the items' real numbers. Static checks compare every
 * argument with Gear2Stats and the stack's own components; behaviour checks run the real hooks (flail chain, rapier
 * lunge/still, arbalest charge time, termite face) and compare with what the tooltip says. Hooked from
 * {@link SelfTest#start} after the synchronous body.
 */
final class SelfTestTooltips {
	private SelfTestTooltips() {}

	static final String[] LORE = {"galvanic_coilmail", "ironclad_flail", "gale_rapier", "mirrorbolt_arbalest", "steam_auger", "termite_hive_lantern",
		"seers_hourglass", "prospectors_pendulum", "void_tether_spool", "amberstill_plate"};

	static void run(ServerLevel l, FakePlayer p) {
		Item[] stat = {Gear2Items.IRONCLAD_FLAIL, Gear2Items.GALE_RAPIER, Gear2Items.MIRRORBOLT_ARBALEST, Gear2Items.STEAM_AUGER, Gear2Items.TERMITE_HIVE_LANTERN,
			Gear2Items.SEERS_HOURGLASS, Gear2Items.PROSPECTORS_PENDULUM, Gear2Items.VOID_TETHER_SPOOL, Gear2Items.COILMAIL_HELMET, Gear2Items.COILMAIL_CHESTPLATE,
			Gear2Items.COILMAIL_LEGGINGS, Gear2Items.COILMAIL_BOOTS, Gear2Items.AMBERSTILL_HELMET, Gear2Items.AMBERSTILL_CHESTPLATE, Gear2Items.AMBERSTILL_LEGGINGS,
			Gear2Items.AMBERSTILL_BOOTS};
		SelfTest.checkS("P5 tooltip: 16 gear2 weapons, tools, trinkets and armor pieces have lines; parts and templates none", () -> {
			for (Item i : stat) if (Gear2Tooltips.of(new ItemStack(i)).summary().isEmpty()) throw new IllegalStateException(id(i));
			for (Item i : List.of(Gear2Items.IRONCLAD_CHAIN, Gear2Items.HELIODOR_MIRROR_CORE, Gear2Items.STEAM_AUGER_BIT, Gear2Items.TERMITE_QUEEN,
				Gear2Items.GALVANIC_TEMPLATE, Gear2Items.AMBERSTILL_TEMPLATE)) if (!Gear2Tooltips.of(new ItemStack(i)).isEmpty()) throw new IllegalStateException(id(i));
			return true;
		});
		SelfTest.checkS("P5 tooltip: no Shift = summary + hint line, Shift = summary + detail (Ironclad Flail)", () -> {
			ItemStack s = new ItemStack(Gear2Items.IRONCLAD_FLAIL);
			Gear2Tooltips.Lines ln = Gear2Tooltips.of(s);
			List<Component> lo = Gear2Tooltips.lines(s, false), hi = Gear2Tooltips.lines(s, true);
			return ln.summary().size() == 1 && ln.detail().size() == 2 && lo.size() == 2 && Gear2Tooltips.HOLD.equals(key(lo.get(1)))
				&& hi.size() == 3 && hi.stream().noneMatch(c -> Gear2Tooltips.HOLD.equals(key(c)));
		});
		List<Component> all = new ArrayList<>();
		for (Item i : Gear2Items.ALL) {
			all.addAll(Gear2Tooltips.of(new ItemStack(i)).summary());
			all.addAll(Gear2Tooltips.of(new ItemStack(i)).detail());
		}
		SelfTest.checkS("P5 tooltip: every gear2 P5 line is translated, its %s count matches its arguments; P5 lines and gear2 lore carry no typed number", () -> {
			java.util.Map<String, String> lang = shippedLang("elsewhere.p5.flail");
			Set<String> keys = new LinkedHashSet<>(List.of(Gear2Tooltips.HOLD, "elsewhere.p5.tether.none", "elsewhere.p5.tether.anchor"));
			for (Component c : all) {
				TranslatableContents t = (TranslatableContents) c.getContents();
				keys.add(t.getKey());
				int slots = lang.getOrDefault(t.getKey(), "").replace("%%", "").split("%s", -1).length - 1;
				if (slots != t.getArgs().length) throw new IllegalStateException(t.getKey() + " has " + slots + " slots for " + t.getArgs().length + " args");
			}
			for (String set : LORE) for (int n = 1; n <= 3; n++) keys.add("item.elsewhere." + set + ".lore" + n);
			for (String k : keys) {
				if (!lang.containsKey(k)) throw new IllegalStateException("untranslated " + k);
				if (lang.get(k).matches(".*\\d.*")) throw new IllegalStateException("digit in " + k + ": " + lang.get(k));
			}
			return keys.size() > 50;
		});

		// ---- static: arguments = stats
		double flailHit = Gear2Tooltips.attackDamage(Gear2Items.IRONCLAD_FLAIL), rapierHit = Gear2Tooltips.attackDamage(Gear2Items.GALE_RAPIER);
		double peak = Gear2Stats.FLAIL_CHAIN_STEP * Gear2Stats.FLAIL_CHAIN_MAX;
		SelfTest.check("P5 tooltip: flail and rapier numbers = Gear2Stats and the stack's attack damage",
			SelfTest.near(flailHit, Gear2Stats.FLAIL_DAMAGE) && SelfTest.near(rapierHit, Gear2Stats.RAPIER_DAMAGE)
				&& args(summary(Gear2Items.IRONCLAD_FLAIL), Gear2Stats.FLAIL_CHAIN_STEP * 100, Gear2Stats.FLAIL_CHAIN_WINDOW / 20.0, peak * 100)
				&& args(detail(Gear2Items.IRONCLAD_FLAIL, "elsewhere.p5.flail.peak"), flailHit * (1 + peak), flailHit)
				&& args(detail(Gear2Items.IRONCLAD_FLAIL, "elsewhere.p5.flail.backward"), -Gear2Stats.FLAIL_BACKWARD_SLOW * 100)
				&& args(summary(Gear2Items.GALE_RAPIER), Gear2Stats.RAPIER_LUNGE, rapierHit + Gear2Stats.RAPIER_LUNGE)
				&& args(detail(Gear2Items.GALE_RAPIER, "elsewhere.p5.rapier.still"), Gear2Stats.RAPIER_STILL_MULT * 100, rapierHit * Gear2Stats.RAPIER_STILL_MULT),
			String.join("/", str(summary(Gear2Items.IRONCLAD_FLAIL))) + " | " + String.join("/", str(summary(Gear2Items.GALE_RAPIER))));
		SelfTest.check("P5 tooltip: arbalest, auger, termite, hourglass, pendulum and spool numbers = Gear2Stats",
			args(summary(Gear2Items.MIRRORBOLT_ARBALEST), Gear2Stats.ARBALEST_DAMAGE, Gear2Stats.ARBALEST_RELOAD_SECONDS, Gear2Stats.ARBALEST_PIERCE)
				&& args(detail(Gear2Items.MIRRORBOLT_ARBALEST, "elsewhere.p5.range"), Gear2Stats.ARBALEST_RANGE)
				&& args(summary(Gear2Items.STEAM_AUGER), Gear2Stats.AUGER_SPEED, Gear2Stats.AUGER_SPEED / Gear2Stats.NETHERITE_PICK_SPEED)
				&& args(detail(Gear2Items.STEAM_AUGER, "elsewhere.p5.auger.reach"), p.getAttributes().getBaseValue(Attributes.BLOCK_INTERACTION_RANGE) + Gear2Stats.AUGER_REACH_DELTA)
				&& args(summary(Gear2Items.TERMITE_HIVE_LANTERN), 3, 3, Gear2Stats.TERMITE_SPREAD_TICKS / 20.0)
				&& args(detail(Gear2Items.TERMITE_HIVE_LANTERN, "elsewhere.p5.termite.speed"), Gear2Stats.TERMITE_SPEED, Gear2Stats.TERMITE_SPEED / Gear2Stats.NETHERITE_PICK_SPEED * 100)
				&& args(summary(Gear2Items.SEERS_HOURGLASS), Gear2Stats.HOURGLASS_LEAD_TICKS / 20.0)
				&& args(summary(Gear2Items.PROSPECTORS_PENDULUM), Gear2Stats.PENDULUM_RANGE, Gear2Stats.PENDULUM_TYPES)
				&& args(summary(Gear2Items.VOID_TETHER_SPOOL), Gear2Stats.TETHER_RANGE, Gear2Stats.TETHER_HUNGER, Gear2Stats.TETHER_COOLDOWN / 1200.0),
			String.join("/", str(summary(Gear2Items.MIRRORBOLT_ARBALEST))) + " | " + String.join("/", str(summary(Gear2Items.TERMITE_HIVE_LANTERN))));
		SelfTest.check("P5 tooltip: Coilmail Capacitor and Amberstill Brace numbers = Gear2Stats and the worn toughness",
			args(summary(Gear2Items.COILMAIL_CHESTPLATE), Gear2Stats.CAPACITOR_MAX, Gear2Stats.CAPACITOR_FADE_TICKS / 20.0)
				&& args(detail(Gear2Items.COILMAIL_BOOTS, "elsewhere.p5.capacitor.discharge"), Gear2Stats.CAPACITOR_DAMAGE_PER_CHARGE, Gear2Stats.CAPACITOR_TARGETS, Gear2Stats.CAPACITOR_RADIUS)
				&& args(detail(Gear2Items.COILMAIL_HELMET, "elsewhere.p5.coilmail.lightning"), Gear2Stats.COILMAIL_LIGHTNING_PER_PIECE * 100)
				&& args(summary(Gear2Items.AMBERSTILL_LEGGINGS), Gear2Stats.STILLNESS_TOUGHNESS, Gear2Stats.STILLNESS_TICKS / 20.0,
					4 * Gear2Stats.AMBERSTILL_TOUGHNESS + Gear2Stats.STILLNESS_TOUGHNESS)
				&& args(detail(Gear2Items.AMBERSTILL_HELMET, "elsewhere.p5.brace.sprint"), 4 * Gear2Stats.AMBERSTILL_TOUGHNESS),
			String.join("/", str(summary(Gear2Items.AMBERSTILL_LEGGINGS))));

		// ---- behaviour: the real hooks give the numbers the tooltip shows
		Mob dummy = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND); // only its UUID is used by the chain
		float mult = 0;
		for (int i = 0; i <= Gear2Stats.FLAIL_CHAIN_MAX + 1; i++) mult = Gear2Hooks.flailHit(p, dummy);
		Object[] pk = argsOf(detail(Gear2Items.IRONCLAD_FLAIL, "elsewhere.p5.flail.peak"));
		SelfTest.check("P5 tooltip: flail full-chain damage shown = base x what Gear2Hooks.flailHit really returns at the cap",
			Gear2Tooltips.fmt(flailHit * mult).equals(pk[0]), Gear2Tooltips.fmt(flailHit * mult) + " vs shown " + pk[0]);
		Gear2Hooks.flailHit(p, EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND)); // a new target ends this chain
		boolean sprint0 = p.isSprinting();
		Vec3 v0 = p.getDeltaMovement();
		p.setSprinting(true);
		float lunge = Gear2Hooks.rapierHit(p, dummy, (float) rapierHit);
		p.setSprinting(false);
		p.setDeltaMovement(Vec3.ZERO);
		float still = Gear2Hooks.standingStill(p) ? Gear2Hooks.rapierHit(p, dummy, (float) rapierHit) : Float.NaN;
		p.setSprinting(sprint0);
		p.setDeltaMovement(v0);
		Object[] rs = argsOf(summary(Gear2Items.GALE_RAPIER)), rd = argsOf(detail(Gear2Items.GALE_RAPIER, "elsewhere.p5.rapier.still"));
		SelfTest.check("P5 tooltip: rapier lunge and standing-still damage shown = what Gear2Hooks.rapierHit really returns",
			Gear2Tooltips.fmt(lunge).equals(rs[1]) && Gear2Tooltips.fmt(still).equals(rd[1]), lunge + " / " + still + " vs shown " + rs[1] + " / " + rd[1]);
		int charge = MirrorboltArbalest.chargeTicks(new ItemStack(Gear2Items.MIRRORBOLT_ARBALEST), p);
		Object[] ar = argsOf(summary(Gear2Items.MIRRORBOLT_ARBALEST));
		SelfTest.check("P5 tooltip: arbalest reload shown = the charge time the crossbow really uses",
			Gear2Tooltips.fmt(charge / 20.0).equals(ar[1]), charge + " ticks vs shown " + ar[1]);
		int face = Termites.face(new BlockPos(5, 70, 5), Direction.Axis.Z).size() + 1;
		Object[] tm = argsOf(summary(Gear2Items.TERMITE_HIVE_LANTERN));
		SelfTest.check("P5 tooltip: termite face shown = the blocks Termites really queue (plus the mined one)",
			Gear2Tooltips.fmt(Math.sqrt(face)).equals(tm[0]) && tm[0].equals(tm[1]), face + " blocks vs shown " + tm[0] + "x" + tm[1]);
		ItemStack auger = new ItemStack(Gear2Items.STEAM_AUGER);
		auger.set(Gear2Items.AUGER_FUEL, 37);
		ItemStack spool = new ItemStack(Gear2Items.VOID_TETHER_SPOOL);
		Object[] fuel = argsOf(detail(auger, "elsewhere.p5.auger.fuel"));
		boolean none = hasDetail(spool, "elsewhere.p5.tether.none");
		spool.set(Gear2Items.TETHER_ANCHOR, GlobalPos.of(l.dimension(), new BlockPos(12, 64, -7)));
		Object[] anc = argsOf(detail(spool, "elsewhere.p5.tether.anchor"));
		SelfTest.check("P5 tooltip: per-stack lines read the stack (Auger fuel left, Spool anchor or none)",
			fuel.length == 2 && "37".equals(fuel[1]) && String.valueOf(Gear2Stats.AUGER_BLOCKS_PER_COAL).equals(fuel[0]) && none
				&& anc.length == 4 && "12".equals(anc[0]) && "64".equals(anc[1]) && "-7".equals(anc[2]) && "minecraft:overworld".equals(anc[3]),
			String.join("/", str(fuel)) + " | " + String.join("/", str(anc)));
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
		List<Component> s = Gear2Tooltips.of(new ItemStack(i)).summary();
		return s.isEmpty() ? Component.empty() : s.get(0);
	}

	static Component detail(Item i, String key) {
		return detail(new ItemStack(i), key);
	}

	static Component detail(ItemStack st, String key) {
		for (Component c : Gear2Tooltips.of(st).detail()) if (key.equals(key(c))) return c;
		return Component.empty();
	}

	static boolean hasDetail(ItemStack st, String key) {
		for (Component c : Gear2Tooltips.of(st).detail()) if (key.equals(key(c))) return true;
		return false;
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

	static boolean args(Component c, double... want) {
		Object[] got = argsOf(c);
		if (got.length != want.length) return false;
		for (int i = 0; i < want.length; i++) if (!Gear2Tooltips.fmt(want[i]).equals(got[i])) return false;
		return true;
	}
}
