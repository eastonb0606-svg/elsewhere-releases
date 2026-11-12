package dev.elsewhere.gear3.setbonus.b;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.gear3.s3.whips.WhipKind;
import dev.elsewhere.gear3.s3.whips.WhipTags;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.summoner.GaleRibbonItem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Self-test of the (B) set bonuses (s-setbonus-b): one block per bonus, run synchronously on a FakePlayer from
 * {@code SelfTest.runChecks}. FakePlayers don't tick and take no damage, so the hooks are called directly with real damage
 * sources (the API's rule), and a real bow release checks the no-spread mixin end to end. Leaves the fake player bare.
 */
public final class SetBonusTestsB {
	private SetBonusTestsB() {}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		FakePlayer p = FakePlayer.get(l);
		Vec3 base = new Vec3(40.5, 100, 56.5);
		p.snapTo(base.x, base.y, base.z, 0, 0);
		Mob husk = husk(l, base.add(3, 0, 0)), husk2 = husk(l, base.add(-3, 0, 0));
		Predicate<ItemStack> ascendant = SetBonuses.ascendant;
		try {
			registry();
			outrider(l, p, husk);
			steadyAim(l, p, husk);
			starsight(l, p, husk, husk2);
			zenith(l, p, husk);
			wolfFury(s, l, p, husk);
			command(l, p, husk);
		} finally {
			SetBonuses.ascendant = ascendant;
			SunscribeZenith.forceSky = null;
			bare(p);
			p.setSprinting(false);
			p.setShiftKeyDown(false);
			p.stopUsingItem();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			UlfhednarWolfFury.clear(p);
			GaleRibbonItem.clear(p.getUUID());
			WhipTags.clear(p.getUUID());
			husk.discard();
			husk2.discard();
			Gear3Scheduler.flush(s);
		}
		info("setbonus-b blocks done");
	}

	// ---------------------------------------------------------------- helpers

	static Mob husk(ServerLevel l, Vec3 at) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static void bare(FakePlayer p) {
		for (EquipmentSlot sl : ARMOR) p.setItemSlot(sl, ItemStack.EMPTY);
		SetBonuses.recompute(p);
	}

	/** Puts on every piece of {@code b} (the first accepted id per slot); false when a piece's module is not in this environment. */
	static boolean wear(FakePlayer p, SetBonus b) {
		bare(p);
		for (int i = 0; i < b.pieceCount(); i++) {
			Identifier id = b.accepted(i).iterator().next();
			if (!BuiltInRegistries.ITEM.containsKey(id)) {
				info(b.id + ": piece " + id + " is not registered in this environment; set checks skipped");
				return false;
			}
			p.setItemSlot(b.slot(i), new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
		}
		SetBonuses.recompute(p);
		return true;
	}

	static Arrow arrow(ServerLevel l, FakePlayer p) {
		return new Arrow(l, p, new ItemStack(Items.ARROW), null);
	}

	static DamageSource melee(ServerLevel l, FakePlayer p) {
		return l.damageSources().playerAttack(p);
	}

	/** Every copy of the lang file on the classpath, merged (first copy wins per key, like the resource stack). */
	static JsonObject lang() {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json"))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return null;
		}
		return out;
	}

	static JsonObject jei() {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/elsewhere_gear3/jei_info.json")) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- registry, lang, JEI, kill switches

	static void registry() {
		String[][] want = {{"command", "4"}, {"outrider", "2"}, {"steady_aim", "4"}, {"starsight", "2"}, {"zenith", "4"}, {"wolf_fury", "4"}};
		JsonObject lang = lang(), jei = jei();
		for (String[] w : want) {
			SetBonus b = SetBonuses.get(w[0]);
			check("setbonus-b " + w[0] + ": registered, " + w[1] + " pieces, kill switch setbonus_" + w[0],
				b != null && b.pieceCount() == Integer.parseInt(w[1]) && b.killKey().equals("setbonus_" + w[0]) && b.enabled());
			if (b == null) continue;
			checkS("setbonus-b " + w[0] + ": its gear3 pieces are registered items", () -> {
				for (int i = 0; i < b.pieceCount(); i++) for (Identifier id : b.accepted(i)) {
					if (BuiltInRegistries.ITEM.containsKey(id)) continue;
					if (id.getPath().startsWith("court_warmarshal") || id.getPath().equals("hrafnholt_ulfhednar_pelt") || id.getPath().equals("hrafnholt_wolfskin_boots")) {
						info(w[0] + ": " + id + " belongs to another module that is not in this environment");
						continue;
					}
					return false;
				}
				return true;
			});
			checkS("setbonus-b " + w[0] + ": lang name and " + b.detailLines() + " detail line(s)", () -> {
				if (lang == null || !lang.has(b.langKey())) return false;
				for (int i = 1; i <= b.detailLines(); i++) if (!lang.has(b.langKey() + ".detail" + i)) return false;
				return !lang.has(b.langKey() + ".detail" + (b.detailLines() + 1));
			});
			checkS("setbonus-b " + w[0] + ": JEI line on each gear3 piece", () -> {
				for (int i = 0; i < b.pieceCount(); i++) for (Identifier id : b.accepted(i)) {
					if (!BuiltInRegistries.ITEM.containsKey(id) || !id.getNamespace().equals("elsewhere")) continue;
					if (dev.elsewhere.gear3.Gear3Items.BY_ID.get(id.getPath()) == null) continue; // another module's piece: its own JEI page
					if (jei == null || !jei.has(id.toString())) return false;
					boolean found = false;
					for (var line : jei.getAsJsonArray(id.toString())) if (line.getAsString().contains(lang.get(b.langKey()).getAsString())) found = true;
					if (!found) return false;
				}
				return true;
			});
		}
	}

	// ---------------------------------------------------------------- Outrider pair

	static void outrider(ServerLevel l, FakePlayer p, Mob husk) {
		SetBonus b = OutriderPair.PAIR;
		check("Outrider: off without both pieces", SetBonuses.power(p, b) == 0F);
		wear(p, b);
		float pw = SetBonuses.power(p, b);
		check("Outrider: pair on, strength = H(vanguard, marksman)", pw > 0F && near(pw, dev.elsewhere.arsenal.spell.Calling.hybrid(p, b.hybridA(), b.hybridB())), pw);
		p.setSprinting(true);
		float hit = OutriderPair.damage(husk, melee(l, p), 10F);
		check("Outrider: sprint melee hit x(1 + 0.10 x H)", near(hit, 10F * (1F + 0.10F * pw)), hit);
		check("Outrider: never against players", near(OutriderPair.damage(p, melee(l, p), 10F), 10F));
		Arrow a = arrow(l, p);
		SetBonusesB.onLoose(p, a);
		float shot = OutriderPair.damage(husk, l.damageSources().arrow(a, p), 10F);
		check("Outrider: an arrow loosed while sprinting is tagged and hits x(1 + 0.10 x H)", near(shot, 10F * (1F + 0.10F * pw)), shot);
		p.setSprinting(false);
		check("Outrider: no bonus for a walking hit", near(OutriderPair.damage(husk, melee(l, p), 10F), 10F));
		Arrow a2 = arrow(l, p);
		SetBonusesB.onLoose(p, a2);
		check("Outrider: an arrow loosed while walking is not tagged", near(OutriderPair.damage(husk, l.damageSources().arrow(a2, p), 10F), 10F));
		p.setSprinting(true);
		SetBonuses.setEnabledForTest(b, false, List.of(p));
		check("Outrider: kill switch setbonus_outrider off -> no bonus", near(OutriderPair.damage(husk, melee(l, p), 10F), 10F));
		SetBonuses.setEnabledForTest(b, true, List.of(p));
		p.setSprinting(false);
		bare(p);
	}

	// ---------------------------------------------------------------- Ridgewatch: Steady Aim

	static void steadyAim(ServerLevel l, FakePlayer p, Mob husk) {
		SetBonus b = RidgewatchSteadyAim.SET;
		Vec3 o = new Vec3(0, 64, 0);
		check("Steady Aim: still 1 s (20 ticks) within 0.1 block counts, 19 ticks or a 0.2 block drift does not",
			RidgewatchSteadyAim.stillSince(0, o, 20, o) && !RidgewatchSteadyAim.stillSince(0, o, 19, o) && !RidgewatchSteadyAim.stillSince(0, o, 40, o.add(0.2, 0, 0)));
		check("Steady Aim: off without the full set", !RidgewatchSteadyAim.steady(p));
		wear(p, b);
		p.setShiftKeyDown(true);
		check("Steady Aim: sneaking makes the shot steady", RidgewatchSteadyAim.steady(p));
		p.setShiftKeyDown(false);
		RidgewatchSteadyAim.startDrawForTest(p, 25);
		check("Steady Aim: a draw held still for 1 s is steady", RidgewatchSteadyAim.steady(p));
		p.snapTo(p.getX() + 0.5, p.getY(), p.getZ(), 0, 0);
		check("Steady Aim: moving during the draw breaks it", !RidgewatchSteadyAim.steady(p));
		p.snapTo(p.getX() - 0.5, p.getY(), p.getZ(), 0, 0);
		RidgewatchSteadyAim.startDrawForTest(p, 5);
		check("Steady Aim: a 0.25 s still draw is not steady", !RidgewatchSteadyAim.steady(p));
		p.setShiftKeyDown(true);
		check("Steady Aim: crossbows never (inaccuracy kept)", RidgewatchSteadyAim.inaccuracy((CrossbowItem) Items.CROSSBOW, p, true, 1F) == 1F);
		check("Steady Aim: only a full draw", RidgewatchSteadyAim.inaccuracy((BowItem) Items.BOW, p, false, 1F) == 1F);
		// end to end: a real full-draw bow release while sneaking (the mixin zeroes the spread, the arrow gets the +30% tag)
		Vec3 look = p.getLookAngle();
		checkS("Steady Aim: a real sneaking full-draw shot flies dead straight (no spread) and hits for +30%", () -> {
			AbstractArrow a = release(l, p);
			if (a == null) return false;
			double dev = a.getDeltaMovement().normalize().distanceTo(look);
			float dmg = RidgewatchSteadyAim.damage(husk, l.damageSources().arrow(a, p), 10F);
			a.discard();
			info("steady shot direction error " + dev + ", damage " + dmg);
			return dev < 1.0E-4 && near(dmg, 13F);
		});
		p.setShiftKeyDown(false);
		checkS("Steady Aim: an unsteady full-draw shot keeps vanilla spread and no bonus", () -> {
			AbstractArrow a = release(l, p);
			if (a == null) return false;
			double dev = a.getDeltaMovement().normalize().distanceTo(look);
			float dmg = RidgewatchSteadyAim.damage(husk, l.damageSources().arrow(a, p), 10F);
			a.discard();
			return dev > 1.0E-4 && near(dmg, 10F);
		});
		check("Steady Aim: never against players", near(RidgewatchSteadyAim.damage(p, l.damageSources().arrow(arrow(l, p), p), 10F), 10F));
		bare(p);
	}

	/** Draws a bow fully and releases it on the fake player; returns the arrow it loosed (null if none). */
	static AbstractArrow release(ServerLevel l, FakePlayer p) {
		ItemStack bow = new ItemStack(Items.BOW);
		p.setItemInHand(InteractionHand.MAIN_HAND, bow);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 4));
		p.startUsingItem(InteractionHand.MAIN_HAND);
		SetBonusesB.lastLoosed = null;
		int before = RidgewatchSteadyAim.steadyShots;
		bow.getItem().releaseUsing(bow, l, p, bow.getUseDuration(p) - 20);
		AbstractArrow a = SetBonusesB.lastLoosed;
		info("bow release: arrow " + (a != null) + ", steady shots +" + (RidgewatchSteadyAim.steadyShots - before));
		p.stopUsingItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		return a;
	}

	// ---------------------------------------------------------------- Starsight pair

	static void starsight(ServerLevel l, FakePlayer p, Mob husk, Mob husk2) {
		SetBonus b = StarsightPair.PAIR;
		wear(p, b);
		float pw = SetBonuses.power(p, b);
		check("Starsight: pair on, strength = H(marksman, caster)", pw > 0F, pw);
		Glimmer.setForTest(p, 0F);
		Arrow a = arrow(l, p);
		DamageSource src = l.damageSources().arrow(a, p);
		StarsightPair.afterHit(husk, src, 5F);
		float g1 = Glimmer.current(p);
		StarsightPair.afterHit(husk2, src, 5F);
		float g2 = Glimmer.current(p);
		check("Starsight: an arrow hit refunds 1 x H Glimmer, once per arrow (a piercing arrow pays once)", near(g1, Math.min(Glimmer.max(p), pw)) && near(g2, g1), g1 + " / " + g2);
		double m = StarsightPair.spellMult(p, husk);
		check("Starsight: spells deal +10% x H to an arrow-marked target", near(m, 1.0 + 0.10 * pw), m);
		double withPair = spell(p, husk);
		LivingEntity stand = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
		check("Starsight: armor stands and players are never marked targets", stand instanceof ArmorStand && !SetBonusesB.target(p, stand) && !SetBonusesB.target(p, p)
			&& SetBonusesB.target(p, husk));
		bare(p);
		check("Starsight: no bonus without the pair", near(StarsightPair.spellMult(p, husk), 1.0));
		double without = spell(p, husk);
		check("Starsight: arsenal's Spells.damage carries the mark bonus", without > 0 && near(withPair / without, m), without + " -> " + withPair);
		wear(p, b);
		StarsightPair.ageMarksForTest(StarsightPair.MARK_TICKS + 1);
		check("Starsight: the mark ends after 4 s", near(StarsightPair.spellMult(p, husk), 1.0) && near(StarsightPair.spellMult(p, husk2), 1.0));
		bare(p);
		Glimmer.setForTest(p, Glimmer.max(p));
	}

	static double spell(FakePlayer p, LivingEntity target) {
		return Spells.damage(p, target, 10F, false, false);
	}

	// ---------------------------------------------------------------- Sunscribe: Zenith

	static void zenith(ServerLevel l, FakePlayer p, Mob husk) {
		SetBonus b = SunscribeZenith.SET;
		check("Zenith: sky rule (bright + open sky = day; dark = night; Nether/End/roofed day = neither)",
			SunscribeZenith.classify(true, true, false) == SunscribeZenith.Sky.DAY && SunscribeZenith.classify(true, false, false) == SunscribeZenith.Sky.NEITHER
				&& SunscribeZenith.classify(false, true, true) == SunscribeZenith.Sky.NIGHT && SunscribeZenith.classify(false, false, false) == SunscribeZenith.Sky.NEITHER);
		checkS("Zenith: the live sky reading runs", () -> SunscribeZenith.read(l, p.blockPosition().above(100)) != null);
		wear(p, b);
		SunscribeZenith.forceSky = SunscribeZenith.Sky.DAY;
		check("Zenith: open daylight sky: cost x0.75, regen x1.25, spell power x1",
			near(SunscribeZenith.costMult(p), 0.75) && near(SunscribeZenith.regenMult(p), 1.25) && near(SunscribeZenith.spellMult(p, husk), 1.0));
		float costDay = Glimmer.cost(p, 20, false), regenDay = Glimmer.regenPerSecond(p);
		double spellDay = spell(p, husk);
		SunscribeZenith.forceSky = SunscribeZenith.Sky.NEITHER;
		check("Zenith: no sky (Nether, End, indoors by day): no change",
			near(SunscribeZenith.costMult(p), 1.0) && near(SunscribeZenith.regenMult(p), 1.0) && near(SunscribeZenith.spellMult(p, husk), 1.0));
		float costNone = Glimmer.cost(p, 20, false), regenNone = Glimmer.regenPerSecond(p);
		double spellNone = spell(p, husk);
		check("Zenith: arsenal's Glimmer cost goes through the hook (20 raw: " + costNone + " -> " + costDay + ")", costDay < costNone && costDay <= costNone * 0.75F + 0.5F);
		check("Zenith: arsenal's Glimmer regen goes through the hook (x1.25)", regenNone > 0F && near(regenDay / regenNone, 1.25), regenNone + " -> " + regenDay);
		SunscribeZenith.forceSky = SunscribeZenith.Sky.NIGHT;
		check("Zenith: night: spell power x0.90, cost and regen unchanged",
			near(SunscribeZenith.spellMult(p, husk), 0.90) && near(SunscribeZenith.costMult(p), 1.0) && near(SunscribeZenith.regenMult(p), 1.0));
		double spellNight = spell(p, husk);
		check("Zenith: Spells.damage at night is x0.90", near(spellNight / spellNone, 0.90) && near(spellDay / spellNone, 1.0), spellNone + " / " + spellNight);
		SetBonuses.ascendant = s -> true;
		SunscribeZenith.forceSky = SunscribeZenith.Sky.DAY;
		check("Zenith: Ascendant set (bonus II): regen x1.375, cost x0.625", near(SunscribeZenith.regenMult(p), 1.375) && near(SunscribeZenith.costMult(p), 0.625));
		SunscribeZenith.forceSky = SunscribeZenith.Sky.NIGHT;
		check("Zenith: Ascendant: the night drawback stays x0.90", near(SunscribeZenith.spellMult(p, husk), 0.90));
		SetBonuses.ascendant = s -> false;
		bare(p);
		check("Zenith: nothing without the full set", near(SunscribeZenith.spellMult(p, husk), 1.0) && near(SunscribeZenith.costMult(p), 1.0));
		SunscribeZenith.forceSky = null;
	}

	// ---------------------------------------------------------------- Ulfhednar: Wolf-Fury

	static void wolfFury(MinecraftServer s, ServerLevel l, FakePlayer p, Mob husk) {
		check("Wolf-Fury: +4% attack speed per stack, max 5 (x1.20)", near(UlfhednarWolfFury.speedMult(1, 1F), 1.04) && near(UlfhednarWolfFury.speedMult(5, 1F), 1.20)
			&& near(UlfhednarWolfFury.speedMult(9, 1F), 1.20));
		check("Wolf-Fury: full-Fury heal 10% of the hit, 5% against bosses", near(UlfhednarWolfFury.healFor(20F, false, 1F), 2F) && near(UlfhednarWolfFury.healFor(20F, true, 1F), 1F));
		Gear3Scheduler.flush(s);
		int pending0 = Gear3Scheduler.pending();
		if (wear(p, UlfhednarWolfFury.SET)) {
			DamageSource src = melee(l, p);
			UlfhednarWolfFury.afterHit(husk, src, 5F);
			check("Wolf-Fury: a melee hit gives 1 stack (+4% attack speed)", UlfhednarWolfFury.stacks(p) == 1 && near(UlfhednarWolfFury.speedModifier(p), 0.04));
			for (int i = 0; i < 5; i++) UlfhednarWolfFury.afterHit(husk, src, 5F);
			check("Wolf-Fury: stacks cap at 5 (+20% attack speed)", UlfhednarWolfFury.stacks(p) == 5 && near(UlfhednarWolfFury.speedModifier(p), 0.20), UlfhednarWolfFury.stacks(p));
			p.setHealth(10F);
			UlfhednarWolfFury.afterHit(husk, src, 20F);
			check("Wolf-Fury: at 5 stacks a hit heals 10% of the damage dealt (20 -> +2 HP)", near(p.getHealth(), 12F), p.getHealth());
			UlfhednarWolfFury.afterHit(husk, l.damageSources().arrow(arrow(l, p), p), 5F);
			UlfhednarWolfFury.afterHit(p, src, 5F);
			check("Wolf-Fury: arrows and hits on players give no stack or heal", UlfhednarWolfFury.stacks(p) == 5 && near(p.getHealth(), 12F));
			check("Wolf-Fury: one expiry timer per stack-giving hit (none re-queued)", Gear3Scheduler.pending() - pending0 == 7, Gear3Scheduler.pending() - pending0);
			Gear3Scheduler.flush(s);
			check("Wolf-Fury: Fury ends 4 s after the last hit (stacks and attack speed gone)", UlfhednarWolfFury.stacks(p) == 0 && near(UlfhednarWolfFury.speedModifier(p), 0.0));
			UlfhednarWolfFury.afterHit(husk, src, 5F);
			bare(p);
			check("Wolf-Fury: taking the set off clears Fury", UlfhednarWolfFury.stacks(p) == 0 && near(UlfhednarWolfFury.speedModifier(p), 0.0));
			Gear3Scheduler.flush(s);
		}
		bare(p);
		UlfhednarWolfFury.afterHit(husk, melee(l, p), 5F);
		check("Wolf-Fury: no stacks without the set", UlfhednarWolfFury.stacks(p) == 0);
		Item horn = dev.elsewhere.gear3.Gear3Items.BY_ID.get("hrafnholt_mead_horn");
		checkS("Mead-Horn: drinking grants 3 Fury stacks (+12% attack speed) for 4 s, with or without the set", () -> {
			if (horn == null) return false;
			ItemStack st = new ItemStack(horn);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(st));
			horn.use(l, p, InteractionHand.MAIN_HAND);
			boolean ok = UlfhednarWolfFury.stacks(p) == 3 && near(UlfhednarWolfFury.speedModifier(p), 0.12);
			Gear3Scheduler.flush(s);
			ok &= UlfhednarWolfFury.stacks(p) == 0 && near(UlfhednarWolfFury.speedModifier(p), 0.0);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.removeAllEffects();
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(st));
			return ok;
		});
		p.setHealth(p.getMaxHealth());
	}

	// ---------------------------------------------------------------- Marshal's Panoply: Command

	static void command(ServerLevel l, FakePlayer p, Mob husk) {
		bare(p);
		check("Command: no bonus without the set (whip +0 ticks, standard +0)", MarshalCommand.whipTagBonusTicks(p) == 0 && MarshalCommand.standardRadiusBonus(p) == 0.0);
		int base = WhipTags.bonusTicks(p); // other lanes may register whip bonuses too: compare with and without the set
		WhipKind kind = WhipKind.values()[0];
		long now = l.getGameTime();
		WhipTags.tag(p, husk, kind);
		WhipTags.Tag plain = WhipTags.liveTag(p);
		WhipTags.clear(p.getUUID());
		int len = WhipTags.tagTicks(kind, WhipTags.wet(husk), base);
		check("Command: a whip tag without the set keeps its own time and 3 s idle grace", plain != null
			&& plain.until() - now == len && plain.idle() == WhipTags.IDLE_TICKS + base, plain == null ? "no tag" : (plain.until() - now) + "/" + plain.idle());
		if (wear(p, MarshalCommand.SET)) {
			check("Command: whip tags +3 s through s-whips' WhipTags bonus (60 ticks)", MarshalCommand.whipTagBonusTicks(p) == 60 && WhipTags.bonusTicks(p) - base == 60);
			WhipTags.tag(p, husk, kind);
			WhipTags.Tag t = WhipTags.liveTag(p);
			WhipTags.clear(p.getUUID());
			check("Command: a real whip tag lasts 3 s longer and idles 3 s longer", t != null && t.until() - now == len + 60 && t.idle() == WhipTags.IDLE_TICKS + base + 60,
				t == null ? "no tag" : (t.until() - now) + "/" + t.idle());
			check("Command: standard radius +8 (Herdcaller's Pennant 12 -> 20)", near(MarshalCommand.standardRadiusBonus(p), 8.0));
			SetBonuses.setEnabledForTest(MarshalCommand.SET, false, List.of(p));
			check("Command: kill switch setbonus_command off -> +0", MarshalCommand.whipTagBonusTicks(p) == 0 && MarshalCommand.standardRadiusBonus(p) == 0.0
				&& WhipTags.bonusTicks(p) == base);
			SetBonuses.setEnabledForTest(MarshalCommand.SET, true, List.of(p));
		}
		bare(p);
	}
}
