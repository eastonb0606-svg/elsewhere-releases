package dev.elsewhereqa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Green Leaf ("the Greenhaze mod") x Elsewhere 3.0 compatibility suite (s-compat-greenhaze): -Delsewhereqa.suite=greenleaf, and the
 * "greenleaf" section of the release suite. Needs the combined Elsewhere jar and a Green Leaf jar in mods-under-test (Test Meter for
 * the transmutation checks). Green Leaf and Elsewhere are only reached through registries and reflection, so this runs against any
 * Green Leaf version; anything that cannot be reached is a FAIL (a silent fallback would hide a broken tie-in), absent modules SKIP.
 * Area names start with "greenleaf". Report: ElsewhereQA/reports/greenhaze-compat-3.0.md.
 */
final class GreenLeafCompat {
	private GreenLeafCompat() {}

	static final String GL = "dev.greenleaf.";
	static final String BRIDGE = "dev.elsewhere.greenhaze.GreenLeafBridge";
	static final BlockPos ARENA = new BlockPos(-4600, 200, -4600);

	static void plan(final MinecraftServer server) {
		if (!FabricLoader.getInstance().isModLoaded("greenleaf")) {
			ReleaseSuite.skip("greenleaf", "Green Leaf is not loaded in this run (put its jar in mods-under-test / GL_JAR): compat suite not run");
			return;
		}
		QAServer.STEPS.add(new Boot());
		QAServer.STEPS.add(new Bridge());
		QAServer.STEPS.add(new Effects());
		QAServer.STEPS.add(new Npcs());
		QAServer.STEPS.add(new Food());
		QAServer.STEPS.add(new Values());
		QAServer.STEPS.add(new Hud());
	}

	// ------------------------------------------------------------------ helpers
	static Class<?> cls(final String n) {
		return ReleaseSuite.cls(n);
	}

	static Object call(final Object target, final Class<?> c, final String name, final Object... args) throws Exception {
		return ReleaseSuite.call(target, c, name, args);
	}

	static Object data(final ServerPlayer p) throws Exception {
		return call(null, cls(GL + "GreenLeaf"), "data", p);
	}

	static boolean has(final Object o, final String f) {
		try {
			o.getClass().getField(f);
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}

	static double num(final Object o, final String f) throws Exception {
		return ((Number)o.getClass().getField(f).get(o)).doubleValue();
	}

	static void set(final Object o, final String f, final Object v) throws Exception {
		Field fl = o.getClass().getField(f);
		if (fl.getType() == int.class) {
			fl.setInt(o, ((Number)v).intValue());
		} else if (fl.getType() == double.class) {
			fl.setDouble(o, ((Number)v).doubleValue());
		} else {
			fl.set(o, v);
		}
	}

	/** Sober, no tolerance, not coming down. */
	static Object fresh(final ServerPlayer p) throws Exception {
		Object d = data(p);
		call(d, d.getClass(), "sober");
		set(d, "tolerance", 0.0);
		if (has(d, "comedown")) {
			set(d, "comedown", false);
		}
		if (has(d, "sessionSeconds")) {
			set(d, "sessionSeconds", 0);
		}
		return d;
	}

	static Item item(final String id) {
		Identifier i = Identifier.parse(id);
		return BuiltInRegistries.ITEM.containsKey(i) ? BuiltInRegistries.ITEM.getValue(i) : null;
	}

	/** A stone floor with headroom in the overworld; each step gets its own fake player (a fake that tripped away stays away). */
	static FakePlayer arena(final MinecraftServer server, final String name) {
		ServerLevel level = server.overworld();
		level.getChunk(ARENA.getX() >> 4, ARENA.getZ() >> 4);
		for (BlockPos q : BlockPos.betweenClosed(ARENA.offset(-6, -1, -6), ARENA.offset(6, 3, 6))) {
			level.setBlock(q, q.getY() == ARENA.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
		}
		FakePlayer p = QAServer.fake(level, name);
		p.getInventory().clearContent();
		p.removeAllEffects();
		p.snapTo(ARENA.getX() + 0.5, ARENA.getY(), ARENA.getZ() + 0.5, 0, 0);
		return p;
	}

	static ItemStack joint() {
		Item j = item("greenleaf:joint");
		return j == null ? ItemStack.EMPTY : new ItemStack(j);
	}

	static Map<Holder<MobEffect>, MobEffectInstance> effects(final LivingEntity e) {
		Map<Holder<MobEffect>, MobEffectInstance> m = new HashMap<>();
		for (MobEffectInstance i : e.getActiveEffects()) {
			m.put(i.getEffect(), new MobEffectInstance(i));
		}
		return m;
	}

	/** A human kingdom boss (court rulers and officers): at most 300 HP. */
	static boolean humanBoss(final String path) {
		return ReleaseSuite.isKingdomBoss(path) || path.startsWith("court_") && !path.contains("minion") && !path.contains("spawn");
	}

	/** Green Leaf's once-a-second step for one player (applies its effects). */
	static void glTick(final ServerPlayer p, final Object d) throws Exception {
		Method m = cls(GL + "High").getMethod("tick", ServerPlayer.class, d.getClass());
		m.invoke(null, p, d);
	}

	// ------------------------------------------------------------------ 1. both mods load together; nobody squats in the other's namespace
	static final class Boot implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf boot";
			List<String> mods = new ArrayList<>();
			for (String id : List.of("elsewhere", "greenleaf", "testmeter", "jei")) {
				mods.add(id + "@" + FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("absent"));
			}
			QAServer.record("PASS", a, "server started with " + mods + " (no crash, no registry or mixin conflict at boot)");
			// Green Leaf's items are exactly the greenleaf: namespace; Elsewhere registers nothing there and vice versa
			Set<Identifier> own = new TreeSet<>();
			for (Object o : (List<?>)ReleaseSuite.field(cls(GL + "GreenItems"), "ALL")) {
				own.add(BuiltInRegistries.ITEM.getKey((Item)o));
			}
			Set<Identifier> ns = new TreeSet<>();
			for (Identifier k : BuiltInRegistries.ITEM.keySet()) {
				if (k.getNamespace().equals("greenleaf")) {
					ns.add(k);
				}
			}
			QAServer.check(!own.isEmpty() && own.equals(ns), a, QAServer.f("greenleaf: holds exactly Green Leaf's %d items (%d registered there)", own.size(), ns.size()));
			List<String> squat = new ArrayList<>();
			for (Identifier k : BuiltInRegistries.ITEM.keySet()) {
				String c = BuiltInRegistries.ITEM.getValue(k).getClass().getName();
				if (k.getNamespace().equals("greenleaf") && c.startsWith("dev.elsewhere") || k.getNamespace().equals("elsewhere") && c.startsWith("dev.greenleaf")) {
					squat.add(k.toString());
				}
			}
			for (Identifier k : BuiltInRegistries.BLOCK.keySet()) {
				String c = BuiltInRegistries.BLOCK.getValue(k).getClass().getName();
				if (k.getNamespace().equals("greenleaf") && c.startsWith("dev.elsewhere") || k.getNamespace().equals("elsewhere") && c.startsWith("dev.greenleaf")) {
					squat.add(k.toString());
				}
			}
			QAServer.check(squat.isEmpty(), a, "no item or block registered in the other mod's namespace: " + squat);
			// Green Leaf's ServerPlayer mixins applied next to Elsewhere's (calling, glimmer, haze attachments)
			Class<?> holder = cls(GL + "HighHolder");
			QAServer.check(holder != null && holder.isAssignableFrom(ServerPlayer.class), a, "Green Leaf's player mixin (HighHolder) applied alongside Elsewhere's player mixins");
			// JEI: both plugins declared, distinct classes (definitions only: plugins load on the client)
			List<String> plugins = new ArrayList<>();
			for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("jei_mod_plugin", Object.class)) {
				plugins.add(c.getProvider().getMetadata().getId() + "=" + c.getDefinition());
			}
			boolean glPlugin = plugins.stream().anyMatch(s -> s.startsWith("greenleaf="));
			boolean esPlugin = plugins.stream().anyMatch(s -> s.startsWith("elsewhere="));
			QAServer.check(glPlugin && esPlugin && new HashSet<>(plugins).size() == plugins.size(), a,
				"JEI: Green Leaf's and Elsewhere's plugins are both declared, separate classes: " + plugins);
			return true;
		}
	}

	// ------------------------------------------------------------------ 2. Elsewhere's bridge reaches the (reworked) high meter and obeys its rules
	static final class Bridge implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf bridge";
			Class<?> br = cls(BRIDGE), haze = cls("dev.elsewhere.greenhaze.Haze");
			if (br == null || haze == null) {
				QAServer.record("FAIL", a, "GreenLeafBridge / Haze not in this jar: the Greenhaze tie-in is missing");
				return true;
			}
			boolean api = cls(GL + "api.GreenLeafApi") != null;
			QAServer.check((boolean)call(null, br, "usable"), a, "Elsewhere reaches Green Leaf's high meter (no fallback to the Haze-only meter)");
			if (api) {
				QAServer.check((boolean)call(null, br, "hasApi"), a, "Elsewhere uses Green Leaf's stable API (dev.greenleaf.api.GreenLeafApi)");
			} else {
				QAServer.record("WARN", a, "this Green Leaf jar has no dev.greenleaf.api.GreenLeafApi (pre-2.1): Elsewhere falls back to the 2.x field path");
			}
			FakePlayer p = arena(server, "GLBridge");
			Object d = fresh(p);
			call(null, br, "addHigh", p, 20.0);
			double pend = num(d, "pending");
			QAServer.check(pend > 19.9 && pend < 20.1, a, QAServer.f("haze/smog/vent hit of 20 lands as Green Leaf smoke kicking in: pending %.2f", pend));
			if (api) {
				QAServer.check(num(d, "tolerance") > 0, a, QAServer.f("it goes through Green Leaf's own dose (tolerance now %.4f), not around it", num(d, "tolerance")));
			}
			set(d, "high", 42.0);
			double seen = ((Number)call(null, haze, "get", p)).doubleValue();
			QAServer.check(Math.abs(seen - 42) < 0.01, a, QAServer.f("the Greenhaze Haze bar shows Green Leaf's high: %.2f for 42", seen));
			call(null, haze, "add", p, -10.0, true);
			QAServer.check(Math.abs(num(d, "high") - 32) < 0.01, a, QAServer.f("Lucid Water / Lucid Lilies lower the real high: 42 -> %.2f", num(d, "high")));
			// session cap: while coming down, nothing from Elsewhere may top the high back up
			Object cfg = ReleaseSuite.field(cls(GL + "GreenLeaf"), "config");
			if (has(d, "comedown") && has(cfg, "sessionCap") && (boolean)cfg.getClass().getField("sessionCap").get(cfg)) {
				d = fresh(p);
				set(d, "high", 50.0);
				set(d, "comedown", true);
				call(null, br, "addHigh", p, 15.0);
				call(null, haze, "add", p, 5.0, false);
				QAServer.check(num(d, "pending") == 0, a, QAServer.f("coming down (session cap): Greenhaze haze and smoke add nothing (pending %.2f)", num(d, "pending")));
				QAServer.check((boolean)call(null, br, "refusesHits", p), a, "coming down: Elsewhere sees that Green Leaf refuses hits (no trip roll or Hush cough for a refused puff)");
				Item everflowCd = item("elsewhere:everflow_bong"), sativaCd = item("greenleaf:sativa_bud");
				if (everflowCd != null && sativaCd != null) {
					p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(everflowCd));
					p.getInventory().setItem(5, new ItemStack(sativaCd));
					everflowCd.use(p.level(), p, InteractionHand.MAIN_HAND);
					QAServer.check(p.getInventory().getItem(5).getCount() == 1 && num(d, "pending") == 0 && num(d, "sativa") == 0, a,
						QAServer.f("coming down: the Everflow Bong refuses like Green Leaf's own bong (bud kept %d, pending %.2f)", p.getInventory().getItem(5).getCount(), num(d, "pending")));
					p.getInventory().setItem(5, ItemStack.EMPTY);
					p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				}
				d = fresh(p);
				QAServer.check(!(boolean)call(null, br, "refusesHits", p), a, "sober again: hits are accepted (refusesHits false)");
			} else {
				QAServer.record("SKIP", a, "this Green Leaf has no session cap (comedown/sessionCap): cap check not applicable");
			}
			// recognition of every smokable and bud (Percolax's altar, Everflow Bong, trip rolls)
			Class<?> smokable = cls(GL + "SmokableItem");
			List<String> wrong = new ArrayList<>();
			for (Object o : (List<?>)ReleaseSuite.field(cls(GL + "GreenItems"), "ALL")) {
				Item it = (Item)o;
				ItemStack st = new ItemStack(it);
				String path = BuiltInRegistries.ITEM.getKey(it).getPath();
				boolean isSmokable = (boolean)call(null, br, "isSmokable", st), isBud = (boolean)call(null, br, "isBud", st);
				if (smokable != null && smokable.isInstance(it) && !isSmokable) {
					wrong.add(path + " not seen as smokable");
				}
				if (path.endsWith("_bud") != isBud) {
					wrong.add(path + " bud=" + isBud);
				}
				if (isBud && !Set.of("sativa", "indica", "hybrid").contains(String.valueOf(call(null, br, "budStrain", st)))) {
					wrong.add(path + " strain " + call(null, br, "budStrain", st));
				}
				if (!path.contains("bong") && !(smokable != null && smokable.isInstance(it)) && isSmokable) {
					wrong.add(path + " wrongly smokable");
				}
			}
			QAServer.check(wrong.isEmpty(), a, "every Green Leaf joint/blunt/vape is passable at Percolax's altar, every bud packs the Everflow Bong in its strain: " + wrong);
			// Everflow Bong: a rip in the packed bud's strain, through Green Leaf's dose
			Item everflow = item("elsewhere:everflow_bong"), sativa = item("greenleaf:sativa_bud");
			if (everflow != null && sativa != null) {
				FakePlayer q = arena(server, "GLEverflow");
				Object e = fresh(q);
				q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(everflow));
				q.getInventory().setItem(5, new ItemStack(sativa));
				everflow.use(q.level(), q, InteractionHand.MAIN_HAND);
				QAServer.check(q.getInventory().getItem(5).isEmpty() && num(e, "pending") > 0 && num(e, "sativa") > 0 && num(e, "indica") == 0, a,
					QAServer.f("Everflow Bong rip with a Sativa bud: bud burned, smoke %.1f kicking in, Sativa %.1f / Indica %.1f", num(e, "pending"), num(e, "sativa"), num(e, "indica")));
			} else {
				QAServer.record("FAIL", a, "elsewhere:everflow_bong or greenleaf:sativa_bud missing");
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ 3. Green Leaf's effects next to callings, spells and set bonuses
	static final class Effects implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf effects";
			FakePlayer p = arena(server, "GLEffects");
			Class<?> calling = cls("dev.elsewhere.arsenal.spell.Calling"), path = cls("dev.elsewhere.arsenal.spell.Calling$Path");
			List<String> ranksBefore = new ArrayList<>();
			if (calling != null && path != null) {
				for (Object pa : path.getEnumConstants()) {
					ranksBefore.add(String.valueOf(call(null, calling, "rank", p, pa)));
				}
			}
			// couch-locked Indica (the strain with Resistance) and Sativa (Speed/Haste): every effect Green Leaf hands out
			Set<String> seen = new TreeSet<>();
			List<String> bad = new ArrayList<>();
			for (String strain : List.of("indica", "sativa", "hybrid")) {
				p.removeAllEffects();
				Object d = fresh(p);
				set(d, "high", 95.0);
				set(d, strain.equals("hybrid") ? "sativa" : strain, strain.equals("hybrid") ? 47.5 : 95.0);
				if (strain.equals("hybrid")) {
					set(d, "indica", 47.5);
				}
				for (int i = 0; i < 20; i++) {
					set(d, "high", 95.0);
					glTick(p, d);
				}
				for (MobEffectInstance e : p.getActiveEffects()) {
					String n = e.getEffect().getRegisteredName() + " " + (e.getAmplifier() + 1);
					seen.add(n);
					if (!e.isAmbient() || e.getDuration() > 8 * 60 * 20 || e.isInfiniteDuration()) {
						bad.add(n + (e.isAmbient() ? "" : " not ambient") + " " + e.getDuration() + "t");
					}
				}
			}
			QAServer.check(bad.isEmpty(), a, "Green Leaf's effects are ambient and short-lived (Mirewick's Almoner's Cowl and beacon rules skip ambient, so no 3.0 hook can stretch them): "
				+ seen + (bad.isEmpty() ? "" : " BAD " + bad));
			// Mirewick Almoner's Cowl ("+10% to a new beneficial effect") must not lengthen Green Leaf's effects, and still works on a real potion
			Class<?> mp = cls("dev.elsewhere.rivals.mirewick.item.MirewickPassives"), mi = cls("dev.elsewhere.rivals.mirewick.item.MirewickItems");
			if (mp != null && mi != null) {
				Method cowl = mp.getDeclaredMethod("cowl", ServerPlayer.class);
				cowl.setAccessible(true);
				p.removeAllEffects();
				Object d = fresh(p);
				set(d, "high", 95.0);
				set(d, "indica", 95.0);
				p.setItemSlot(EquipmentSlot.HEAD, new ItemStack((Item)ReleaseSuite.field(mi, "ALMONERS_COWL")));
				cowl.invoke(null, p);
				glTick(p, d);
				Map<Holder<MobEffect>, MobEffectInstance> before = effects(p);
				cowl.invoke(null, p);
				List<String> stretched = new ArrayList<>();
				for (MobEffectInstance e : p.getActiveEffects()) {
					MobEffectInstance b = before.get(e.getEffect());
					if (b != null && e.getDuration() > b.getDuration()) {
						stretched.add(e.getEffect().getRegisteredName() + " " + b.getDuration() + "->" + e.getDuration());
					}
				}
				p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
				cowl.invoke(null, p);
				MobEffectInstance fr = p.getEffect(MobEffects.FIRE_RESISTANCE);
				QAServer.check(stretched.isEmpty() && fr != null && fr.getDuration() > 200, a, QAServer.f(
					"Almoner's Cowl leaves Green Leaf's effects alone %s while a real potion still gets its +10%% (200 -> %d)", stretched, fr == null ? 0 : fr.getDuration()));
				p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			} else {
				QAServer.record("SKIP", a, "rival_mirewick not in this jar: Almoner's Cowl check not run");
			}
			// Resistance never stacks past the strongest single source (Kingdom seal = Resistance II), and damage still lands
			p.removeAllEffects();
			Object d = fresh(p);
			set(d, "high", 95.0);
			set(d, "indica", 95.0);
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 1));
			glTick(p, d);
			MobEffectInstance res = p.getEffect(MobEffects.RESISTANCE);
			Method absorb = LivingEntity.class.getDeclaredMethod("getDamageAfterMagicAbsorb", net.minecraft.world.damagesource.DamageSource.class, float.class);
			absorb.setAccessible(true);
			float taken = (float)absorb.invoke(p, p.damageSources().generic(), 20F);
			QAServer.check(res != null && res.getAmplifier() == 1 && taken > 11.9F && taken < 12.1F, a, QAServer.f(
				"Green Leaf's Resistance does not stack on a Kingdom seal's Resistance II: level %d, a 20 hit still deals %.1f", res == null ? 0 : res.getAmplifier() + 1, taken));
			// Callings read gear, never effects: ranks are the same high or sober
			if (calling != null && path != null) {
				List<String> ranksAfter = new ArrayList<>();
				for (Object pa : path.getEnumConstants()) {
					ranksAfter.add(String.valueOf(call(null, calling, "rank", p, pa)));
				}
				QAServer.check(ranksBefore.equals(ranksAfter), a, "Calling ranks unchanged by a Green Leaf high: " + ranksAfter);
			} else {
				QAServer.record("SKIP", a, "arsenal Calling not in this jar");
			}
			p.removeAllEffects();
			fresh(p);
			return true;
		}
	}

	// ------------------------------------------------------------------ 4. soldiers, villagers, townsfolk and a kingdom boss next to a smoker
	static final class Npcs implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf npcs";
			FakePlayer p = arena(server, "GLNpcs");
			ServerLevel level = p.level();
			fresh(p);
			List<EntityType<?>> types = new ArrayList<>();
			types.add(net.minecraft.world.entity.EntityTypes.VILLAGER);
			int soldiers = 0, folk = 0;
			EntityType<?> boss = null;
			for (Identifier id : QAServer.ids(BuiltInRegistries.ENTITY_TYPE)) {
				String path = id.getPath();
				EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				if ((path.contains("soldier") || path.contains("man_at_arms") || path.contains("archer")) && !path.contains("spawn") && soldiers < 3) {
					types.add(t);
					soldiers++;
				} else if ((path.contains("townsfolk") || path.contains("serf") || path.contains("steward") || path.contains("herald") || path.contains("clerk")) && folk < 3) {
					types.add(t);
					folk++;
				} else if (boss == null && humanBoss(path)) {
					boss = t;
				}
			}
			if (boss != null) {
				types.add(boss);
			}
			List<LivingEntity> npcs = new ArrayList<>();
			List<String> names = new ArrayList<>();
			int i = 0;
			for (EntityType<?> t : types) {
				Entity e;
				try {
					e = t.create(level, EntitySpawnReason.COMMAND);
				} catch (Throwable ex) {
					QAServer.record("WARN", a, BuiltInRegistries.ENTITY_TYPE.getKey(t) + " could not be created outside its spawner: " + ex);
					continue;
				}
				if (!(e instanceof LivingEntity le)) {
					continue;
				}
				double ang = i++ * Math.PI * 2 / types.size();
				le.snapTo(ARENA.getX() + 0.5 + Math.cos(ang) * 2.5, ARENA.getY(), ARENA.getZ() + 0.5 + Math.sin(ang) * 2.5, 0, 0);
				level.addFreshEntity(le);
				npcs.add(le);
				names.add(BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath());
			}
			QAServer.check(npcs.size() >= 2, a, "spawned next to the smoker: " + names);
			Map<LivingEntity, Map<Holder<MobEffect>, MobEffectInstance>> fx = new HashMap<>();
			Map<LivingEntity, Float> hp = new HashMap<>();
			for (LivingEntity e : npcs) {
				fx.put(e, effects(e));
				hp.put(e, e.getHealth());
			}
			// puff-puff-pass is for players: sneak-using a joint on an NPC never hands it over or loses it
			List<String> lost = new ArrayList<>(), threw = new ArrayList<>();
			p.setShiftKeyDown(true);
			for (LivingEntity e : npcs) {
				ItemStack j = joint();
				p.setItemInHand(InteractionHand.MAIN_HAND, j);
				try {
					p.interactOn(e, InteractionHand.MAIN_HAND, e.position());
				} catch (Throwable ex) {
					threw.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + ": " + ex);
				}
				if (!p.getMainHandItem().is(j.getItem())) {
					lost.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
				}
			}
			p.setShiftKeyDown(false);
			QAServer.check(threw.isEmpty() && lost.isEmpty(), a, "sneak-use with a joint on soldiers, villagers, townsfolk and a boss: no error, joint kept; threw " + threw + ", lost " + lost);
			// smoke right next to them: Green Leaf only doses players, so no NPC gets an effect or loses health
			ItemStack j = joint();
			p.setItemInHand(InteractionHand.MAIN_HAND, j);
			String puff = "ok";
			try {
				for (int k = 0; k < 3; k++) {
					j.getItem().finishUsingItem(j, level, p);
				}
			} catch (Throwable ex) {
				puff = String.valueOf(ex);
			}
			List<String> touched = new ArrayList<>();
			for (LivingEntity e : npcs) {
				if (!effects(e).keySet().equals(fx.get(e).keySet()) || e.getHealth() < hp.get(e)) {
					touched.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
				}
			}
			QAServer.check("ok".equals(puff) && touched.isEmpty(), a, "3 puffs among them: " + puff + "; NPCs given an effect or hurt: " + touched);
			for (LivingEntity e : npcs) {
				if (humanBoss(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) {
					QAServer.check(e.getMaxHealth() <= 300, a, QAServer.f("human boss %s stays at or under 300 HP next to a smoker: %.0f",
						BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), e.getMaxHealth()));
				}
				e.discard();
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ 5. munchies on every Elsewhere food
	static final class Food implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf food";
			FakePlayer p = arena(server, "GLFood");
			ServerLevel home = p.level();
			int eaten = 0;
			List<String> bad = new ArrayList<>();
			for (Identifier id : QAServer.ids(BuiltInRegistries.ITEM)) {
				Item it = BuiltInRegistries.ITEM.getValue(id);
				ItemStack st = new ItemStack(it);
				if (!st.has(DataComponents.FOOD)) {
					continue;
				}
				if (p.level() != home || p.isRemoved()) {
					p = arena(server, "GLFood" + eaten);
				}
				Object d = fresh(p);
				set(d, "high", 50.0);
				set(d, "sativa", 50.0);
				p.getFoodData().setFoodLevel(6);
				p.getFoodData().setSaturation(0);
				try {
					it.finishUsingItem(st, p.level(), p);
					eaten++;
				} catch (Throwable ex) {
					bad.add(id.getPath() + " threw " + ex);
					continue;
				}
				if (p.getFoodData().getSaturationLevel() > p.getFoodData().getFoodLevel() + 0.001F) {
					bad.add(QAServer.f("%s saturation %.1f > food %d", id.getPath(), p.getFoodData().getSaturationLevel(), p.getFoodData().getFoodLevel()));
				}
			}
			QAServer.check(eaten > 0 && bad.isEmpty(), a, eaten + " Elsewhere foods eaten while high (Green Leaf munchies + Elsewhere food effects): " + (bad.isEmpty() ? "no errors, saturation never above food" : bad));
			return true;
		}
	}

	// ------------------------------------------------------------------ 6. Transmutation Table: no cheap Elsewhere back door into Green Leaf
	static final class Values implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf transmute";
			Class<?> tv = cls("dev.testmeter.TransmuteValues"), vc = cls("dev.testmeter.TransmuteValues$Value");
			if (tv == null || vc == null) {
				ReleaseSuite.skip(a, "Test Meter is not loaded in this run: Transmutation Table checks not run");
				return true;
			}
			StringBuilder tsv = new StringBuilder("item\tquanta\n");
			double minBud = Double.MAX_VALUE, bong = 0;
			int valued = 0, total = 0;
			for (Object o : (List<?>)ReleaseSuite.field(cls(GL + "GreenItems"), "ALL")) {
				Item it = (Item)o;
				double q = ReleaseSuite.Transmute.val(tv, vc, new ItemStack(it));
				String path = BuiltInRegistries.ITEM.getKey(it).getPath();
				tsv.append("greenleaf:").append(path).append('\t').append(q).append('\n');
				total++;
				if (q > 0) {
					valued++;
				}
				if (path.endsWith("_bud") && q > 0) {
					minBud = Math.min(minBud, q);
				}
				if (path.equals("bong")) {
					bong = q;
				}
			}
			double hazeborn = val(tv, vc, "elsewhere:hazeborn_bud"), everflow = val(tv, vc, "elsewhere:everflow_bong");
			tsv.append("elsewhere:hazeborn_bud\t").append(hazeborn).append('\n').append("elsewhere:everflow_bong\t").append(everflow).append('\n');
			Files.writeString(Path.of("selftest-qa-greenleaf-transmute.tsv"), tsv.toString());
			QAServer.record("PASS", a, QAServer.f("%d of %d Green Leaf items have a Quanta value (table in run/selftest-qa-greenleaf-transmute.tsv)", valued, total));
			// Hazeborn is the Greenhaze's own bud: the Budtender sells it for 4 Haze Resin, so its Quanta value follows that price.
			// Pricing it like Green Leaf's 512-Quanta bud would make the Budtender a Quanta faucet (4 resin in, a bud's worth out).
			double resin = val(tv, vc, "elsewhere:haze_resin");
			QAServer.check(hazeborn > 0 && (resin <= 0 || hazeborn <= 2 * 4 * resin), a, QAServer.f(
				"Hazeborn Bud is transmutable at its Budtender price (no Quanta faucet): %.1f Quanta vs 4 Haze Resin = %.1f (Green Leaf's cheapest bud %.1f)",
				hazeborn, 4 * resin, minBud < Double.MAX_VALUE ? minBud : 0));
			QAServer.check(everflow <= 0 || everflow >= bong, a, QAServer.f("Everflow Bong (no water, never breaks) costs at least a Green Leaf bong or is excluded: %.1f vs %.1f", everflow, bong));
			return true;
		}

		static double val(final Class<?> tv, final Class<?> vc, final String id) throws Exception {
			Item it = item(id);
			return it == null ? 0 : ReleaseSuite.Transmute.val(tv, vc, new ItemStack(it));
		}
	}

	// ------------------------------------------------------------------ 7. HUD: the Calling panel and Green Leaf's high meter share the top-left corner
	static final class Hud implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			String a = "greenleaf hud";
			Class<?> l = cls("dev.elsewhere.arsenal.CallingPanelLayout");
			if (l == null) {
				QAServer.record("FAIL", a, "arsenal CallingPanelLayout missing: the Calling panel would cover Green Leaf's high meter");
				return true;
			}
			int with = (int)call(null, l, "topFor", true, true), without = (int)call(null, l, "topFor", false, true), now = (int)call(null, l, "top");
			boolean overlap = (boolean)call(null, l, "overlapsGreenLeafMeter", with, 4, 12);
			QAServer.check(!overlap && now == with && without == 6, a, QAServer.f(
				"Calling panel starts at y %d with Green Leaf (its meter ends at y 26), y %d without; this run uses %d", with, without, now));
			return true;
		}
	}
}
