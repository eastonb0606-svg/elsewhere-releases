package dev.elsewhereqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Release test suite (-Delsewhereqa.suite=release). Everything that touches a module that may not be in the jar is reflective and
 * reports SKIP (never a silent pass) when the class is absent. Statuses: PASS / FAIL / WARN / SKIP.
 * Sections (-Delsewhereqa.release=a,b,c; default all): registry, drops, transmute, traders, locate, retry, greenleaf, kingdom, world, bosses.
 */
final class ReleaseSuite {
	static final List<String> KINGDOM_BOSS_PREFIXES = List.of("marshal", "castellan", "master_at_arms", "huntsman", "chancellor", "king", "osric", "ysolde",
		"bertrand", "corvin", "lisbet", "marek");
	static final String KP = "dev.elsewhere.kingdom.";

	private ReleaseSuite() {
	}

	static boolean isKingdomBoss(final String path) {
		return KINGDOM_BOSS_PREFIXES.stream().anyMatch(b -> path.equals(b) || path.startsWith(b + "_") && !path.contains("minion") && !path.contains("spawn"));
	}

	static boolean on(final String section) {
		String s = System.getProperty("elsewhereqa.release", "");
		return s.isBlank() || Set.of(s.split(",")).contains(section);
	}

	static void skip(final String area, final String why) {
		QAServer.record("SKIP", area, why);
	}

	// ------------------------------------------------------------------ plan
	static void plan(final MinecraftServer server, final String suite) {
		switch (suite) {
			case "release" -> {
				QAServer.STEPS.add(new QAServer.Inventory());
				if (on("drops")) {
					QAServer.STEPS.add(new DropTables());
				}
				if (on("transmute")) {
					QAServer.STEPS.add(new Transmute());
					QAServer.STEPS.add(new TransmutePlan()); // s-compat-transmute: planned Transmutation Table entries
				}
				if (on("traders")) {
					QAServer.STEPS.add(new Traders());
				}
				if (on("locate")) {
					QAServer.STEPS.add(new Locate("locate", true));
				}
				if (on("kingdom")) {
					QAServer.STEPS.add(new KingdomCaps());
					QAServer.STEPS.add(new KingdomBattle());
					QAServer.STEPS.add(new KingdomSiege());
				}
				if (on("retry")) {
					QAServer.STEPS.add(new Retry());
				}
				if (on("greenleaf")) {
					GreenLeafCompat.plan(server); // s-compat-greenhaze: Green Leaf x 3.0 compatibility (SKIP when Green Leaf is absent)
				}
				if (on("world")) {
					QAServer.STEPS.add(new QAServer.Blocks_());
					QAServer.STEPS.add(new QAServer.Items_());
					int i = 0;
					for (var key : QAServer.dims(server)) {
						QAServer.STEPS.add(new QAServer.DimVisit(key, i++));
					}
				}
				if (on("bosses")) {
					QAServer.STEPS.add(new QAServer.BossSetup());
				}
			}
			case "greenleaf" -> GreenLeafCompat.plan(server); // s-compat-greenhaze: the Green Leaf compat suite on its own
			case "clientload" -> ClientLoad.plan(server); // s-clientload: client-relevant load per 3.0 scene
			case "used-seed" -> QAServer.STEPS.add(new UsedSeed());
			case "used-check" -> QAServer.STEPS.add(new UsedCheck());
			case "upgrade-seed" -> QAServer.STEPS.add(new UpgradeSeed());
			case "upgrade-check" -> {
				QAServer.STEPS.add(new UpgradeCheck());
				QAServer.STEPS.add(new Locate("upgrade locate", false));
			}
			default -> QAServer.record("FAIL", "harness", "unknown suite " + suite);
		}
	}

	// ------------------------------------------------------------------ reflection helpers
	static Class<?> cls(final String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException | LinkageError e) {
			return null;
		}
	}

	static Object call(final Object target, final Class<?> c, final String name, final Object... args) throws Exception {
		for (Method m : c.getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == args.length) {
				boolean ok = true;
				Class<?>[] pt = m.getParameterTypes();
				for (int i = 0; i < pt.length; i++) {
					if (args[i] != null && !wrap(pt[i]).isInstance(args[i])) {
						ok = false;
						break;
					}
				}
				if (ok) {
					try {
						return m.invoke(target, args);
					} catch (java.lang.reflect.InvocationTargetException e) {
						throw e.getCause() instanceof Exception ex ? ex : new RuntimeException(e.getCause());
					}
				}
			}
		}
		throw new NoSuchMethodException(c.getName() + "." + name + "/" + args.length);
	}

	static Class<?> wrap(final Class<?> c) {
		if (!c.isPrimitive()) {
			return c;
		}
		return switch (c.getName()) {
			case "int" -> Integer.class;
			case "long" -> Long.class;
			case "float" -> Float.class;
			case "double" -> Double.class;
			case "boolean" -> Boolean.class;
			default -> c;
		};
	}

	static Object field(final Class<?> c, final String name) throws Exception {
		Field f = c.getField(name);
		return f.get(null);
	}

	static Identifier id(final String ns, final String path) {
		return Identifier.fromNamespaceAndPath(ns, path);
	}

	static String key(final Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	// ------------------------------------------------------------------ boss hooks (called by QAServer.BossFight)
	/** XP + drop-rule checks, 4 s after the boss died. */
	static void afterBossDeath(final ServerLevel level, final ServerPlayer player, final EntityType<?> type, final String bossId, final Vec3 deathPos,
		final List<String> drops, final int xpStart) {
		String area = "boss " + bossId;
		// XP: orbs still on the ground plus what the hunter already absorbed
		int orbs = 0;
		for (ExperienceOrb o : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(BlockPos.containing(deathPos)).inflate(64))) {
			orbs += o.getValue();
		}
		int absorbed = Math.max(0, player.totalExperience - xpStart);
		QAServer.check(orbs + absorbed > 0, area, QAServer.f("always drops XP: %d in orbs + %d already absorbed by the killer", orbs, absorbed));
		// Drop rule table (first kill: signature 100% + others 50%; repeat: 67% / 33%)
		Class<?> bd = cls("dev.elsewhere.core.boss.BossDrops");
		if (bd == null) {
			skip(area, "BossDrops class not in this jar: drop-rule table not checked");
			return;
		}
		try {
			List<?> uniques = (List<?>)call(null, bd, "uniques", type);
			if (uniques.isEmpty()) {
				QAServer.record("WARN", area, "no registered uniques (BossDrops.uniques is empty): boss may use only a loot table");
				return;
			}
			List<String> rows = new ArrayList<>();
			int sig = 0;
			boolean ok = true;
			String signatureItem = null;
			for (Object u : uniques) {
				float first = (float)call(u, u.getClass(), "firstKillChance");
				float repeat = (float)call(u, u.getClass(), "repeatChance");
				String name = (String)call(u, u.getClass(), "name");
				Item item = (Item)((java.util.function.Supplier<?>)call(u, u.getClass(), "item")).get();
				rows.add(QAServer.f("%s %.2f/%.2f", name, first, repeat));
				if (first >= 0.999F) {
					sig++;
					signatureItem = key(item);
					ok &= repeat > 0.60F && repeat < 0.74F;
				} else {
					ok &= first >= 0.4F && first <= 0.51F && repeat >= 0.25F && repeat <= 0.36F;
				}
			}
			QAServer.record(sig == 1 && ok ? "PASS" : sig >= 1 ? "WARN" : "FAIL", area,
				"drop-rule table (first/repeat): " + rows + (sig == 1 ? "" : " - expected exactly one signature at 100% on the first kill"));
			if (signatureItem != null) {
				final String sigItem = signatureItem;
				boolean got = drops.stream().anyMatch(d -> d.contains(sigItem));
				QAServer.check(got, area, "first kill dropped its signature item " + sigItem + " (100% rule); drops " + drops);
			}
		} catch (Exception e) {
			QAServer.record("WARN", area, "drop-rule check could not run: " + e);
		}
	}

	// ------------------------------------------------------------------ drop constants + table
	static final class DropTables implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			Class<?> dr = cls("dev.elsewhere.boss.kit.DropRule");
			if (dr == null) {
				skip("drop rules", "dev.elsewhere.boss.kit.DropRule not in this jar");
			} else {
				float fs = (float)field(dr, "FIRST_SIGNATURE"), fo = (float)field(dr, "FIRST_OTHER"), rs = (float)field(dr, "REPEAT_SIGNATURE"),
					ro = (float)field(dr, "REPEAT_OTHER"), ps = (float)field(dr, "PITY_STEP"), pc = (float)field(dr, "PITY_CAP");
				QAServer.check(fs == 1.0F && Math.abs(fo - 0.5F) < 1e-3 && Math.abs(rs - 0.67F) < 0.011 && Math.abs(ro - 0.33F) < 0.011,
					"drop rules", QAServer.f("DECISIONS constants: first %.2f/%.2f, repeat %.2f/%.2f (want 1.00/0.50 and 0.67/0.33)", fs, fo, rs, ro));
				QAServer.check(Math.abs(ps - 0.10F) < 1e-3 && Math.abs(pc - 0.60F) < 1e-3, "drop rules",
					QAServer.f("bad-luck protection +%.0f%% per miss up to %.0f%% (want +10%% / 60%%)", ps * 100, pc * 100));
				// pity maths
				Class<?> kind = cls("dev.elsewhere.boss.kit.DropRule$Kind");
				Object other = kind.getEnumConstants()[1];
				float c0 = (float)call(null, dr, "chance", other, true, 0), c3 = (float)call(null, dr, "chance", other, true, 3), c9 = (float)call(null, dr, "chance", other, true, 9);
				QAServer.check(c3 > c0 && c9 <= pc + 0.001F, "drop rules", QAServer.f("pity: 0 misses %.2f, 3 misses %.2f, 9 misses %.2f (capped at %.2f)", c0, c3, c9, pc));
			}
			Class<?> kit = cls("dev.elsewhere.boss.kit.BossKit");
			if (kit == null) {
				skip("boss retry", "BossKit not in this jar");
			} else {
				int delay = (int)field(kit, "RETRY_DELAY_TICKS");
				QAServer.check(delay == 600, "boss retry", "retry delay is " + delay + " ticks (want 600 = 30 s)");
			}
			Class<?> br = cls("dev.elsewhere.boss.kit.BossRewards");
			if (br != null) {
				QAServer.check((int)field(br, "XP_FLOOR") > 0, "boss xp", "BossRewards.XP_FLOOR = " + field(br, "XP_FLOOR"));
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ Test Meter transmutation values
	static final class Transmute implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			Class<?> tv = cls("dev.testmeter.TransmuteValues");
			if (tv == null) {
				skip("transmute", "Test Meter is not loaded in this run");
				return true;
			}
			Class<?> value = cls("dev.testmeter.TransmuteValues$Value");
			double net = val(tv, value, new ItemStack(Items.NETHERITE_INGOT));
			double dia = val(tv, value, new ItemStack(Items.DIAMOND));
			QAServer.check(net > 0 && dia > 0 && net > dia, "transmute", QAServer.f("vanilla spot values: netherite ingot %.0f, diamond %.0f Quanta", net, dia));
			StringBuilder tsv = new StringBuilder("item\tvalue\tsource\tx_netherite\n");
			List<String> unvalued = new ArrayList<>(), cheapGear = new ArrayList<>(), leaked = new ArrayList<>();
			int excluded = 0, valued = 0;
			Set<String> uniqueItems = new TreeSet<>();
			Class<?> bd = cls("dev.elsewhere.core.boss.BossDrops");
			if (bd != null) {
				for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
					if (!BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals("elsewhere")) {
						continue;
					}
					for (Object u : (List<?>)call(null, bd, "uniques", t)) {
						uniqueItems.add(key((Item)((java.util.function.Supplier<?>)call(u, u.getClass(), "item")).get()));
					}
				}
			}
			for (Identifier iid : QAServer.ids(BuiltInRegistries.ITEM)) {
				Item item = BuiltInRegistries.ITEM.getValue(iid);
				Object v = call(null, tv, "lookup", new ItemStack(item));
				double q = 0;
				String src = "none";
				if (v != null) {
					q = (double)call(v, value, "value");
					src = String.valueOf(call(v, value, "source"));
				}
				tsv.append(iid).append('\t').append(q).append('\t').append(src).append('\t').append(net > 0 ? QAServer.f("%.2f", q / net) : "").append('\n');
				boolean isExcluded = v == null || src.equals("EXCLUDED") || q <= 0;
				if (isExcluded) {
					excluded++;
				} else {
					valued++;
					if (uniqueItems.contains(iid.toString())) {
						leaked.add(iid.getPath());
					}
					if (isGear(item) && q < 5 * net) {
						cheapGear.add(iid.getPath() + "=" + Math.round(q / net * 100) / 100.0 + "x");
					}
				}
				if (v == null && !uniqueItems.contains(iid.toString())) {
					unvalued.add(iid.getPath());
				}
			}
			Files.writeString(Path.of("selftest-qa-transmute.tsv"), tsv.toString());
			QAServer.record("PASS", "transmute", valued + " Elsewhere items have a Quanta value, " + excluded + " excluded (table in run/selftest-qa-transmute.tsv)");
			QAServer.check(leaked.isEmpty(), "transmute", "boss uniques are excluded from transmutation; priced anyway: " + leaked);
			QAServer.check(cheapGear.isEmpty(), "transmute", "craftable Elsewhere gear with a value is priced at least 5x a netherite ingot; cheaper: " + cheapGear);
			QAServer.record("PASS", "transmute", unvalued.size() + " non-unique items have no transmutation value (excluded by tag or by Test Meter's own rules): "
				+ unvalued.stream().limit(25).toList());
			return true;
		}

		static double val(final Class<?> tv, final Class<?> value, final ItemStack st) throws Exception {
			Object v = call(null, tv, "lookup", st);
			return v == null ? 0 : (double)call(v, value, "value");
		}

		/** weapons, tools and armour: anything with a tool, weapon or equippable component. */
		static boolean isGear(final Item item) {
			var c = item.components();
			return c.has(net.minecraft.core.component.DataComponents.TOOL) || c.has(net.minecraft.core.component.DataComponents.WEAPON)
				|| c.has(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		}
	}

	// ------------------------------------------------------------------ traders
	static final class Traders implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			ServerLevel lvl = server.overworld();
			FakePlayer p = QAServer.fake(lvl, "QATrader");
			int merchants = 0;
			List<String> names = new ArrayList<>();
			Class<?> tv = cls("dev.testmeter.TransmuteValues");
			double net = 0;
			if (tv != null) {
				net = Transmute.val(tv, cls("dev.testmeter.TransmuteValues$Value"), new ItemStack(Items.NETHERITE_INGOT));
			}
			for (Identifier id : QAServer.ids(BuiltInRegistries.ENTITY_TYPE)) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				Entity e;
				try {
					e = type.create(lvl, EntitySpawnReason.COMMAND);
				} catch (Throwable t) {
					continue;
				}
				if (!(e instanceof Merchant m)) {
					if (e != null) {
						e.discard();
					}
					continue;
				}
				merchants++;
				names.add(id.getPath());
				String area = "trader " + id.getPath();
				try {
					e.snapTo(lvl.getRespawnData().pos().getX() + 0.5, 100, lvl.getRespawnData().pos().getZ() + 0.5, 0, 0);
					if (e instanceof Mob mob) {
						mob.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(e.blockPosition()), EntitySpawnReason.COMMAND, null);
					}
					m.setTradingPlayer(p);
					List<MerchantOffer> offers = null;
					try {
						Object o = e.getClass().getMethod("offersFor", ServerPlayer.class).invoke(e, p);
						offers = new ArrayList<>();
						for (Object x : (Iterable<?>)o) {
							offers.add((MerchantOffer)x);
						}
					} catch (NoSuchMethodException nsm) {
						try {
							offers = new ArrayList<>();
							for (Object x : (Iterable<?>)e.getClass().getMethod("offers").invoke(null)) {
								offers.add((MerchantOffer)x);
							}
						} catch (NoSuchMethodException nsm2) {
							offers = new ArrayList<>(m.getOffers());
						}
					}
					int n = offers.size();
					int bad = 0, over = 0, arb = 0, hardArb = 0;
					List<String> notes = new ArrayList<>();
					for (MerchantOffer o : offers) {
						ItemStack res = o.getResult();
						ItemStack a = o.getCostA();
						ItemStack b = o.getCostB();
						if (res.isEmpty() || a.isEmpty() && b.isEmpty() || BuiltInRegistries.ITEM.getKey(res.getItem()).getPath().equals("air")) {
							bad++;
							notes.add("empty offer");
						}
						if (o.getMaxUses() > 16 && !a.is(Items.EMERALD)) {
							over++;
						}
						if (o.getMaxUses() <= 0) {
							bad++;
							notes.add("maxUses " + o.getMaxUses() + " for " + key(res.getItem()));
						}
						if (tv != null) {
							Class<?> vc = cls("dev.testmeter.TransmuteValues$Value");
							double rv = Transmute.val(tv, vc, res) * res.getCount();
							double cv = Transmute.val(tv, vc, a) * a.getCount() + (b.isEmpty() ? 0 : Transmute.val(tv, vc, b) * b.getCount());
							if (res.getMaxStackSize() == 1) {
								rv = Math.min(rv, cv); // Test Meter marks non-stackable purchases with what was paid and burns them for at most that
							}
							if (rv > 0 && cv > 0 && rv > 2 * cv) {
								arb++;
								if (rv > 20 * cv && rv >= net * 0.9) {
									hardArb++;
								}
								notes.add(QAServer.f("%s costs %.0f Quanta of inputs, burns for %.0f", key(res.getItem()), cv, rv));
							}
						}
					}
					QAServer.check(n > 0 && bad == 0, area, QAServer.f("%d offers, %d broken %s", n, bad, notes.stream().limit(4).toList()));
					QAServer.record(over == 0 ? "PASS" : "WARN", area, over + " offers allow more than 16 uses (vanilla-like caps wanted)");
					if (tv != null) {
						QAServer.record(arb == 0 ? "PASS" : hardArb > 0 ? "FAIL" : "WARN", area, arb + " offers are Test Meter arbitrage (output burns for over 2x the inputs; "
							+ hardArb + " over 20x and worth a netherite ingot or more = exploit): " + notes.stream().limit(3).toList());
					}
					m.setTradingPlayer(null);
				} catch (Throwable t) {
					QAServer.record("FAIL", area, "offers could not be read: " + t);
				}
				e.discard();
			}
			QAServer.record(merchants > 0 ? "PASS" : "FAIL", "traders", merchants + " Elsewhere merchant entity types: " + names);
			return true;
		}
	}

	// ------------------------------------------------------------------ /locate every structure
	static final class Locate implements QAServer.Step {
		final String area;
		final boolean strict;
		Set<String> only;
		List<Holder.Reference<Structure>> todo;
		int next;
		int found, missed;
		final Map<String, String> where = new TreeMap<>();

		Locate(final String area, final boolean strict) {
			this.area = area;
			this.strict = strict;
		}

		@Override
		public boolean tick(final MinecraftServer server) {
			if (todo == null) {
				todo = new ArrayList<>();
				server.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
					if (h.key().identifier().getNamespace().equals("elsewhere") && (only == null || only.contains(h.key().identifier().toString()))) {
						todo.add(h);
					}
				});
				todo.sort((a, b) -> a.key().identifier().toString().compareTo(b.key().identifier().toString()));
				QAServer.LOG.info("[qa] locating {} elsewhere structures", todo.size());
			}
			if (next >= todo.size()) {
				QAServer.record(missed == 0 && found > 0 ? "PASS" : "FAIL", area, found + " of " + todo.size() + " Elsewhere structures found with /locate-style search");
				try {
					Files.writeString(Path.of("selftest-qa-locate.tsv"), where.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(java.util.stream.Collectors.joining("\n")));
				} catch (Exception ignored) {
					// informational only
				}
				return true;
			}
			Holder.Reference<Structure> h = todo.get(next++);
			String name = h.key().identifier().toString();
			BlockPos[] origins = {BlockPos.ZERO, new BlockPos(3000, 64, 3000), new BlockPos(-3000, 64, 3000), new BlockPos(3000, 64, -3000)};
			long t0 = System.currentTimeMillis();
			String hit = null;
			// dimensions whose biome source can contain a biome this structure allows
			List<ServerLevel> levels = new ArrayList<>();
			for (ServerLevel l : server.getAllLevels()) {
				try {
					Set<Holder<Biome>> possible = l.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
					HolderSet<Biome> allowed = h.value().biomes();
					boolean any = false;
					for (Holder<Biome> b : possible) {
						if (allowed.contains(b)) {
							any = true;
							break;
						}
					}
					if (any) {
						levels.add(l);
					}
				} catch (Throwable t) {
					levels.add(l);
				}
			}
			outer:
			for (BlockPos o : origins) {
				for (ServerLevel l : levels) {
					try {
						var pair = l.getChunkSource().getGenerator().findNearestMapStructure(l, HolderSet.direct(h), o, 100, false);
						if (pair != null) {
							BlockPos bp = pair.getFirst();
							hit = l.dimension().identifier() + " " + bp.toShortString() + " (" + (int)Math.sqrt(bp.distSqr(o)) + " blocks from " + o.getX() + "," + o.getZ() + ")";
							break outer;
						}
					} catch (Throwable t) {
						QAServer.LOG.warn("[qa] locate {} in {} threw {}", name, l.dimension().identifier(), t.toString());
					}
				}
				if (!strict) {
					break;
				}
			}
			long ms = System.currentTimeMillis() - t0;
			if (hit != null) {
				found++;
				where.put(name, hit);
				QAServer.record("PASS", area, name + " found in " + hit + " (" + ms + " ms)");
			} else if (realmAbsent(server, name) != null) {
				// s-locate: a realm structure exists at most once per world (Hrafnholt's waterfront only when its realm is by the water); the court says this world has none
				where.put(name, "ABSENT BY DESIGN: " + realmAbsent(server, name));
				skip(area, name + " is not in this world by design: " + realmAbsent(server, name));
			} else {
				missed++;
				where.put(name, "NOT FOUND");
				QAServer.record("FAIL", area, name + " not found within 100 chunks of 4 origins in " + levels.stream().map(l -> l.dimension().identifier().toString()).toList());
			}
			return false;
		}

		/** The court's reason this world has none of realm structure {@code id} ({@code RealmLocate.absentReason}, by reflection), or null (not a realm structure, present, or no court module). */
		static String realmAbsent(final MinecraftServer server, final String id) {
			try {
				return (String) Class.forName("dev.elsewhere.kingdom_court.world.RealmLocate").getMethod("absentReason", MinecraftServer.class, Identifier.class).invoke(null, server, Identifier.parse(id));
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				return null;
			}
		}
	}

	// ------------------------------------------------------------------ retry (a wipe returns the summon item after 30 s)
	static final class Retry implements QAServer.Step {
		FakePlayer p;
		int t;
		Object store;
		Class<?> storeC;
		ItemStack stack;
		UUID bossUuid;

		boolean hasGold() {
			for (ItemStack s : p.getInventory()) {
				if (s.is(Items.GOLD_BLOCK)) {
					return true;
				}
			}
			for (var ie : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8))) {
				if (ie.getItem().is(Items.GOLD_BLOCK)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			Class<?> kit = cls("dev.elsewhere.boss.kit.KitStore");
			if (kit == null) {
				skip("boss retry", "KitStore not in this jar");
				return true;
			}
			t++;
			if (t == 1) {
				ServerLevel lvl = server.overworld();
				p = QAServer.fake(lvl, "QARetry");
				lvl.getChunk(0, 0);
				p.snapTo(0.5, 100, 0.5, 0, 0);
				if (!lvl.players().contains(p)) {
					lvl.addNewPlayer(p);
				}
				QAServer.listPlayer(server, p, true);
				p.getInventory().clearContent();
				storeC = kit;
				store = call(null, kit, "of", server);
				stack = new ItemStack(Items.GOLD_BLOCK);
				bossUuid = UUID.randomUUID();
				call(store, kit, "fightStart", bossUuid, "grunthar", p.getUUID(), stack, "minecraft:the_nether", 1, 2, 3);
				boolean active = (boolean)call(store, kit, "fightActive", bossUuid);
				QAServer.check(active, "boss retry", "fight is recorded as active after the summon item is spent");
				boolean wiped = (boolean)call(store, kit, "fightWiped", bossUuid, "wipe");
				QAServer.check(wiped, "boss retry", "a wipe queues a refund of the summon item");
				int early = ((List<?>)call(store, kit, "takeDueRefunds", (java.util.function.Predicate<UUID>)u -> true)).size();
				QAServer.check(early == 0, "boss retry", "the refund is not due immediately (" + early + " due at once)");
				return false;
			}
			if (t == 20 * 15) {
				QAServer.check(!hasGold(), "boss retry", "15 s after the wipe the summon item has NOT come back yet (30 s delay)");
				return false;
			}
			if (t == 20 * 32) {
				Class<?> bk = cls("dev.elsewhere.boss.kit.BossKit");
				int delivered = (int)call(null, bk, "deliverRefunds", server);
				QAServer.check(hasGold(), "boss retry", "32 s after the wipe the summon item is back with the summoner (manual delivery call returned " + delivered
					+ "; the periodic delivery may have run first)");
				p.level().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
				QAServer.listPlayer(server, p, false);
				return true;
			}
			return false;
		}
	}

	// ------------------------------------------------------------------ kingdom
	static boolean kingdomPresent() {
		return cls(KP + "KingdomApi") != null;
	}

	static Object formation(final String name) throws Exception {
		Class<?> f = cls(KP + "Squad$Formation");
		for (Object o : f.getEnumConstants()) {
			if (o.toString().equals(name)) {
				return o;
			}
		}
		return f.getEnumConstants()[0];
	}

	static final class KingdomCaps implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			if (!kingdomPresent()) {
				skip("kingdom", "dev.elsewhere.kingdom is not in this jar (kingdom caps, battle, siege all skipped)");
				return true;
			}
			Path cfg = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-kingdom.json");
			if (!Files.exists(cfg)) {
				QAServer.record("WARN", "kingdom caps", "config/elsewhere-kingdom.json was not written at first start");
				return true;
			}
			JsonObject root = JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();
			JsonObject perf = root.has("perf") ? root.getAsJsonObject("perf") : root;
			int perPlayer = intOf(perf, "maxRealSoldiersPerPlayer", intOf(perf, "perf.maxRealSoldiersPerPlayer", -1)),
				perArea = intOf(perf, "maxRealSoldiersPerArea", intOf(perf, "perf.maxRealSoldiersPerArea", -1));
			QAServer.check(perPlayer > 0 && perPlayer <= 15, "kingdom caps", "max real soldiers per player is " + perPlayer + " (KINGDOM-DECISIONS: at most 15)");
			QAServer.check(perArea > 0 && perArea <= 30, "kingdom caps", "max real soldiers per 48-block area is " + perArea + " (KINGDOM-DECISIONS: at most 30)");
			QAServer.record("PASS", "kingdom caps", "config: " + perf);
			return true;
		}

		static int intOf(final JsonObject o, final String k, final int d) {
			JsonElement e = o.get(k);
			return e == null ? d : e.getAsInt();
		}
	}

	/** 15 Aldermark vs 15 Rebels (30 real soldiers) with a survival player 20 blocks away; MSPT over 600 ticks. */
	static final class KingdomBattle implements QAServer.Step {
		int t;
		ServerLevel lvl;
		FakePlayer p;
		BlockPos origin;
		final List<Double> mspt = new ArrayList<>();
		final List<Double> base = new ArrayList<>();
		final List<Entity> spawned = new ArrayList<>();

		@Override
		public String name() {
			return "kingdom battle";
		}

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			if (!kingdomPresent()) {
				return true;
			}
			t++;
			if (t == 1) {
				lvl = server.overworld();
				origin = new BlockPos(7000, 0, 7000);
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -4; dz <= 4; dz++) {
						lvl.setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
					}
				}
				lvl.getChunk(origin);
				int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) + 1;
				origin = new BlockPos(origin.getX(), Math.max(y, lvl.getSeaLevel() + 1), origin.getZ());
				for (int dx = -30; dx <= 30; dx++) {
					for (int dz = -30; dz <= 30; dz++) {
						lvl.setBlock(origin.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
						for (int dy = 0; dy < 6; dy++) {
							lvl.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				p = QAServer.fake(lvl, "QAKingdomWatcher");
				p.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 20.5, 0, 0);
				if (!lvl.players().contains(p)) {
					lvl.addNewPlayer(p);
				}
				QAServer.listPlayer(server, p, true);
				return false;
			}
			if (t <= 100) {
				return false; // settle after clearing
			}
			if (t <= 300) {
				base.add(QAServer.lastTickMs);
				return false;
			}
			if (t == 301) {
				Class<?> api = cls(KP + "KingdomApi"), ut = cls(KP + "UnitTypes"), fa = cls(KP + "Factions");
				Identifier ald = (Identifier)field(fa, "ALDERMARK"), reb = (Identifier)field(fa, "REBELS");
				Identifier sgt = (Identifier)field(ut, "SERGEANT"), maa = (Identifier)field(ut, "MAN_AT_ARMS"), kn = (Identifier)field(ut, "KNIGHT"),
					ar = (Identifier)field(ut, "ARCHER"), cb = (Identifier)field(ut, "CROSSBOWMAN");
				// make Aldermark hostile to players is irrelevant here: the factions fight each other
				int made = 0;
				for (int side = 0; side < 2; side++) {
					Identifier fac = side == 0 ? ald : reb;
					for (int sq = 0; sq < 3; sq++) {
						BlockPos at = origin.offset(side == 0 ? -12 : 12, 0, sq * 4 - 4);
						List<Identifier> members = side == 0 ? List.of(maa, maa, kn, kn, ar) : List.of(maa, kn, kn, cb, ar);
						Object formation = formation(sq == 0 ? "SHIELD_WALL" : "LINE");
						List<?> got = (List<?>)call(null, api, "spawnSquad", lvl, at, fac, formation, sgt, members);
						made += got.size();
						for (Object s : got) {
							spawned.add((Entity)s);
						}
						if (!got.isEmpty()) {
							Object squad = call(got.get(0), got.get(0).getClass(), "squad");
							call(squad, squad.getClass(), "setObjective", origin.offset(side == 0 ? 12 : -12, 0, 0));
						}
					}
				}
				QAServer.record(made >= 28 ? "PASS" : "WARN", "kingdom battle", made + " of 36 requested soldiers spawned (15 per side + 3 sergeants; the area cap trims the rest)");
				return false;
			}
			if (t <= 901) {
				mspt.add(QAServer.lastTickMs);
				return false;
			}
			long alive = spawned.stream().filter(Entity::isAlive).count();
			double avg = mspt.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			double b = base.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			List<Double> sorted = new ArrayList<>(mspt);
			sorted.sort(Double::compare);
			double p95 = sorted.isEmpty() ? 0 : sorted.get((int)(sorted.size() * 0.95));
			double max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
			QAServer.check(avg < 20 && p95 < 40, "kingdom battle", QAServer.f("30-soldier battle MSPT: avg %.1f ms (idle baseline %.1f), p95 %.1f, max %.1f over %d ticks; %d soldiers still alive "
				+ "(budget: avg < 20, p95 < 40)", avg, b, p95, max, mspt.size(), alive));
			for (Entity e : spawned) {
				e.discard();
			}
			p.level().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
			QAServer.listPlayer(server, p, false);
			return true;
		}
	}

	/**
	 * Siege safety on a test village with a player-placed block. Tries the conquest module's entry points; when none exists yet it runs the
	 * occupation proxy (an Aldermark squad sent to the bell for 60 s). The pass condition is the same either way: player-placed blocks,
	 * their chest contents and the villagers survive.
	 */
	static final class KingdomSiege implements QAServer.Step {
		int t;
		ServerLevel lvl;
		FakePlayer p;
		BlockPos c;
		BlockPos goldPos, chestPos, bedPos;
		final List<Entity> villagers = new ArrayList<>();
		final List<Entity> soldiers = new ArrayList<>();
		String how = "occupation proxy (no siege entry point found in this jar)";
		Map<BlockPos, BlockState> before = new LinkedHashMap<>();

		@Override
		public String name() {
			return "kingdom siege";
		}

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			if (!kingdomPresent()) {
				return true;
			}
			t++;
			if (t == 1) {
				lvl = server.overworld();
				c = new BlockPos(8000, 0, 8000);
				for (int dx = -5; dx <= 5; dx++) {
					for (int dz = -5; dz <= 5; dz++) {
						lvl.setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, true);
					}
				}
				lvl.getChunk(c);
				int y = Math.max(lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()), lvl.getSeaLevel()) + 1;
				c = new BlockPos(c.getX(), y, c.getZ());
				for (int dx = -40; dx <= 40; dx++) {
					for (int dz = -40; dz <= 40; dz++) {
						for (int dy = 0; dy < 10; dy++) {
							lvl.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
						}
						lvl.setBlock(c.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
					}
				}
				// village: well + bell, three houses (planks, door gap, bed inside), a farm plot
				lvl.setBlock(c, Blocks.BELL.defaultBlockState(), 2);
				for (int h = 0; h < 3; h++) {
					BlockPos o = c.offset(-12 + h * 12, 0, 8);
					for (int dx = 0; dx < 6; dx++) {
						for (int dz = 0; dz < 5; dz++) {
							for (int dy = 0; dy < 4; dy++) {
								boolean wall = dx == 0 || dx == 5 || dz == 0 || dz == 4 || dy == 3;
								lvl.setBlock(o.offset(dx, dy, dz), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
							}
						}
					}
					lvl.setBlock(o.offset(2, 0, 0), Blocks.AIR.defaultBlockState(), 2);
					lvl.setBlock(o.offset(2, 1, 0), Blocks.AIR.defaultBlockState(), 2);
					lvl.setBlock(o.offset(1, 0, 2), BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("red_bed")).defaultBlockState(), 2);
					Entity v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(lvl, EntitySpawnReason.COMMAND);
					v.snapTo(o.getX() + 3.5, o.getY(), o.getZ() + 2.5, 0, 0);
					if (v instanceof Mob m) {
						m.setPersistenceRequired();
					}
					lvl.addFreshEntity(v);
					villagers.add(v);
				}
				// the player's build: placed through a fake player so the placement hooks fire
				p = QAServer.fake(lvl, "QAVillager");
				p.snapTo(c.getX() + 0.5, c.getY(), c.getZ() - 6.5, 0, 0);
				if (!lvl.players().contains(p)) {
					lvl.addNewPlayer(p);
				}
				QAServer.listPlayer(server, p, true);
				p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
				goldPos = c.offset(-6, 0, 3);
				chestPos = c.offset(-4, 0, 3);
				bedPos = c.offset(-2, 0, 3);
				place(Items.GOLD_BLOCK, goldPos);
				place(Items.CHEST, chestPos);
				place(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("blue_bed")), bedPos);
				place(Items.OAK_PLANKS, goldPos.above());
				if (lvl.getBlockEntity(chestPos) instanceof net.minecraft.world.Container ch) {
					ch.setItem(0, new ItemStack(Items.DIAMOND, 7));
					ch.setItem(1, new ItemStack(Items.NETHERITE_INGOT, 2));
				}
				for (BlockPos q : BlockPos.betweenClosed(goldPos.offset(-1, 0, -1), bedPos.offset(2, 1, 1))) {
					before.put(q.immutable(), lvl.getBlockState(q));
				}
				QAServer.record(lvl.getBlockState(goldPos).is(Blocks.GOLD_BLOCK) && lvl.getBlockState(chestPos).is(Blocks.CHEST) ? "PASS" : "FAIL", "kingdom siege",
					"fixture built: village with bell, 3 houses, 3 villagers and a player-placed gold block, chest (7 diamonds, 2 netherite) and bed");
				// try a real siege entry point
				how = startSiege(server);
				if (how == null) {
					Class<?> api = cls(KP + "KingdomApi"), ut = cls(KP + "UnitTypes"), fa = cls(KP + "Factions");
					Identifier ald = (Identifier)field(fa, "ALDERMARK");
					call(null, api, "setFactionHostileToPlayers", server, ald, true);
					List<?> got = (List<?>)call(null, api, "spawnSquad", lvl, c.offset(0, 0, -25), ald, formation("COLUMN"), field(ut, "SERGEANT"),
						List.of(field(ut, "MAN_AT_ARMS"), field(ut, "MAN_AT_ARMS"), field(ut, "KNIGHT"), field(ut, "ARCHER"), field(ut, "CROSSBOWMAN")));
					for (Object s : got) {
						soldiers.add((Entity)s);
					}
					if (!got.isEmpty()) {
						Object squad = call(got.get(0), got.get(0).getClass(), "squad");
						call(squad, squad.getClass(), "setObjective", c);
					}
					how = "occupation proxy: " + got.size() + " Aldermark soldiers marched on the bell (no siege entry point found in this jar)";
				}
				QAServer.LOG.info("[qa] siege started: {}", how);
				return false;
			}
			if (t == 20 * 75) {
				boolean gold = lvl.getBlockState(goldPos).is(Blocks.GOLD_BLOCK) && lvl.getBlockState(goldPos.above()).is(Blocks.OAK_PLANKS);
				int diff = 0;
				for (var e : before.entrySet()) {
					if (!lvl.getBlockState(e.getKey()).equals(e.getValue())) {
						diff++;
					}
				}
				int dia = 0, nether = 0;
				if (lvl.getBlockEntity(chestPos) instanceof net.minecraft.world.Container ch) {
					dia = ch.countItem(Items.DIAMOND);
					nether = ch.countItem(Items.NETHERITE_INGOT);
				}
				long vAlive = villagers.stream().filter(Entity::isAlive).count();
				QAServer.check(gold && diff == 0, "kingdom siege", "player-placed blocks untouched after the siege (" + diff + " of the player's blocks changed); " + how);
				QAServer.check(dia == 7 && nether == 2, "kingdom siege", "the player's chest keeps its contents (" + dia + " diamonds, " + nether + " netherite)");
				QAServer.check(vAlive == villagers.size(), "kingdom siege", "no villager killed (" + vAlive + " of " + villagers.size() + " alive)");
				long sAlive = soldiers.stream().filter(Entity::isAlive).count();
				QAServer.record("PASS", "kingdom siege", "end state: " + sAlive + " attackers still around the bell; " + how);
				for (Entity e : soldiers) {
					e.discard();
				}
				for (Entity e : villagers) {
					e.discard();
				}
				p.level().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
				QAServer.listPlayer(server, p, false);
				return true;
			}
			return false;
		}

		void place(final Item item, final BlockPos at) {
			p.snapTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 0, 90);
			ItemStack st = new ItemStack(item);
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
			var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at.below(), false);
			p.gameMode.useItemOn(p, lvl, st, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			p.snapTo(c.getX() + 0.5, c.getY(), c.getZ() - 6.5, 0, 0);
		}

		/** Looks for the conquest module's siege entry points; returns a description or null when none exists. */
		String startSiege(final MinecraftServer server) {
			String[] classes = {"conquest.Sieges", "conquest.SiegeManager", "conquest.Conquest", "conquest.ConquestApi", "siege.SiegeManager", "KingdomConquest", "Sieges"};
			String[] methods = {"startSiege", "beginSiege", "forceSiege", "siege"};
			for (String cn : classes) {
				Class<?> k = cls(KP + cn);
				if (k == null) {
					continue;
				}
				for (String mn : methods) {
					try {
						call(null, k, mn, lvl, c);
						return KP + cn + "." + mn + "(level, bell)";
					} catch (Exception ignored) {
						// try the next shape
					}
				}
			}
			var src = server.createCommandSourceStack().withLevel(lvl).withPosition(Vec3.atCenterOf(c)).withSuppressedOutput().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
			for (String cmd : new String[] {"kingdom siege " + c.getX() + " " + c.getY() + " " + c.getZ(), "kingdom siege start " + c.getX() + " " + c.getY() + " " + c.getZ(),
				"kingdom conquest siege " + c.getX() + " " + c.getY() + " " + c.getZ()}) {
				if (QAServer.runCommand(server, src, cmd) > 0) {
					return "/" + cmd;
				}
			}
			return null;
		}
	}

	// ------------------------------------------------------------------ 0.3.0 -> new jar upgrade
	static final Path SEED = Path.of("upgrade-seed.json");

	static final class UpgradeSeed implements QAServer.Step {
		int t, dimIndex;
		final JsonObject seed = new JsonObject();
		final JsonArray chests = new JsonArray();
		List<net.minecraft.resources.ResourceKey<Level>> dims;
		FakePlayer p;

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			if (t++ == 0) {
				JsonObject reg = new JsonObject();
				for (var e : Map.of("items", BuiltInRegistries.ITEM, "blocks", BuiltInRegistries.BLOCK, "entities", BuiltInRegistries.ENTITY_TYPE).entrySet()) {
					JsonArray a = new JsonArray();
					for (Identifier id : QAServer.ids(e.getValue())) {
						a.add(id.toString());
					}
					reg.add(e.getKey(), a);
				}
				JsonArray structs = new JsonArray();
				server.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
					if (h.key().identifier().getNamespace().equals("elsewhere")) {
						structs.add(h.key().identifier().toString());
					}
				});
				reg.add("structures", structs);
				JsonArray dimsJ = new JsonArray();
				dims = QAServer.dims(server);
				for (var k : dims) {
					dimsJ.add(k.identifier().toString());
				}
				reg.add("dimensions", dimsJ);
				seed.add("registry", reg);
				seed.addProperty("modVersion", FabricLoader.getInstance().getModContainer("elsewhere").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
				p = QAServer.fake(server.overworld(), "QASeeder");
				QAServer.record("PASS", "upgrade seed", "old jar " + seed.get("modVersion").getAsString() + " registry recorded: " + QAServer.f("%d items, %d blocks, %d entity types, %d structures, %d dimensions",
					reg.getAsJsonArray("items").size(), reg.getAsJsonArray("blocks").size(), reg.getAsJsonArray("entities").size(), structs.size(), dimsJ.size()));
				return false;
			}
			if (dimIndex <= dims.size()) {
				// dimIndex 0 = overworld, then each Elsewhere dimension: generate a patch of chunks, put a chest of Elsewhere items on the ground
				ServerLevel lvl = dimIndex == 0 ? server.overworld() : server.getLevel(dims.get(dimIndex - 1));
				int cx = 100 + dimIndex * 16;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						lvl.getChunk(cx / 16 + dx, dx + dz + 20 + dimIndex);
					}
				}
				int x = cx, z = (20 + dimIndex) * 16 + 4;
				lvl.getChunk(x >> 4, z >> 4);
				int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, Math.max(y, lvl.getMinY() + 2), z);
				lvl.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
				lvl.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
				JsonObject ch = new JsonObject();
				ch.addProperty("dim", lvl.dimension().identifier().toString());
				ch.addProperty("x", pos.getX());
				ch.addProperty("y", pos.getY());
				ch.addProperty("z", pos.getZ());
				JsonArray items = new JsonArray();
				int slot = 0;
				if (lvl.getBlockEntity(pos) instanceof net.minecraft.world.Container cont) {
					int k = 0;
					for (Identifier id : QAServer.ids(BuiltInRegistries.ITEM)) {
						if (k++ % 17 == dimIndex % 17 && slot < 27) {
							Item it = BuiltInRegistries.ITEM.getValue(id);
							int n = Math.min(3, Math.max(1, it.getDefaultMaxStackSize()));
							cont.setItem(slot++, new ItemStack(it, n));
							JsonObject o = new JsonObject();
							o.addProperty("id", id.toString());
							o.addProperty("count", n);
							items.add(o);
						}
					}
					cont.setChanged();
				}
				ch.add("items", items);
				if (slot > 0) {
					chests.add(ch);
				} else {
					QAServer.record("WARN", "upgrade seed", lvl.dimension().identifier() + ": no chest could be placed at " + pos.toShortString() + " (build limit?); not part of the read-back");
				}
				QAServer.record("PASS", "upgrade seed", QAServer.f("%s: 25 chunks generated, chest at %s with %d Elsewhere item stacks", lvl.dimension().identifier(), pos.toShortString(), slot));
				dimIndex++;
				return false;
			}
			seed.add("chests", chests);
			Files.writeString(SEED, seed.toString());
			server.saveEverything(true, true, true);
			QAServer.record("PASS", "upgrade seed", "world saved; seed written to run/" + SEED);
			return true;
		}
	}

	static final class UpgradeCheck implements QAServer.Step {
		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			if (!Files.exists(SEED)) {
				QAServer.record("FAIL", "upgrade", "run/" + SEED + " missing: the 0.3.0 seed run did not complete");
				return true;
			}
			JsonObject seed = JsonParser.parseString(Files.readString(SEED)).getAsJsonObject();
			QAServer.record("PASS", "upgrade", "world created with " + seed.get("modVersion").getAsString() + " opened by " + FabricLoader.getInstance().getModContainer("elsewhere")
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?") + " without a crash");
			JsonObject reg = seed.getAsJsonObject("registry");
			check("items", reg, BuiltInRegistries.ITEM);
			check("blocks", reg, BuiltInRegistries.BLOCK);
			check("entities", reg, BuiltInRegistries.ENTITY_TYPE);
			List<String> dimsNow = QAServer.dims(server).stream().map(k -> k.identifier().toString()).toList();
			List<String> lostDims = new ArrayList<>();
			reg.getAsJsonArray("dimensions").forEach(d -> {
				if (!dimsNow.contains(d.getAsString())) {
					lostDims.add(d.getAsString());
				}
			});
			QAServer.check(lostDims.isEmpty(), "upgrade", "all 0.3.0 dimensions still exist; missing " + lostDims);
			List<String> structNow = new ArrayList<>();
			server.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> structNow.add(h.key().identifier().toString()));
			List<String> lostS = new ArrayList<>();
			reg.getAsJsonArray("structures").forEach(s -> {
				if (!structNow.contains(s.getAsString())) {
					lostS.add(s.getAsString());
				}
			});
			QAServer.check(lostS.isEmpty(), "upgrade", "all 0.3.0 structure ids still exist; missing " + lostS);
			int okChests = 0, bad = 0;
			List<String> notes = new ArrayList<>();
			for (JsonElement ce : seed.getAsJsonArray("chests")) {
				JsonObject ch = ce.getAsJsonObject();
				ServerLevel lvl = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, Identifier.parse(ch.get("dim").getAsString())));
				if (lvl == null) {
					bad++;
					notes.add(ch.get("dim").getAsString() + " level missing");
					continue;
				}
				BlockPos pos = new BlockPos(ch.get("x").getAsInt(), ch.get("y").getAsInt(), ch.get("z").getAsInt());
				lvl.getChunk(pos);
				boolean good = lvl.getBlockEntity(pos) instanceof net.minecraft.world.Container;
				if (good) {
					var cont = (net.minecraft.world.Container)lvl.getBlockEntity(pos);
					for (JsonElement ie : ch.getAsJsonArray("items")) {
						JsonObject io = ie.getAsJsonObject();
						Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(io.get("id").getAsString()));
						if (cont.countItem(it) < io.get("count").getAsInt()) {
							good = false;
							notes.add(lvl.dimension().identifier() + ": lost " + io.get("id").getAsString());
						}
					}
				} else {
					notes.add(lvl.dimension().identifier() + ": chest gone at " + pos.toShortString());
				}
				if (good) {
					okChests++;
				} else {
					bad++;
				}
			}
			QAServer.check(bad == 0 && okChests > 0, "upgrade", okChests + " seeded chests (Elsewhere items from the 0.3.0 registry) read back intact in the upgraded world; problems " + notes);
			return true;
		}

		static void check(final String what, final JsonObject reg, final net.minecraft.core.Registry<?> r) {
			List<String> gone = new ArrayList<>();
			reg.getAsJsonArray(what).forEach(e -> {
				if (!r.containsKey(Identifier.parse(e.getAsString()))) {
					gone.add(e.getAsString());
				}
			});
			QAServer.check(gone.isEmpty(), "upgrade", "every 0.3.0 " + what + " id is still registered (registry ids are never removed or renamed); missing " + gone);
		}
	}

	// ------------------------------------------------------------------ used-server upgrade (0.3.0 server that ran for days, then the release jar)
	static final Path USED = Path.of("used-seed.json");
	static final int PREGEN = Integer.getInteger("elsewhereqa.pregen", 500);

	/** blocks that change on their own (crops, leaves, grass turning to dirt); fire is never volatile: it is the siege sign. */
	static boolean volatileBlock(final BlockState st) {
		return !st.is(Blocks.FIRE) && (st.isRandomlyTicking() || st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.COARSE_DIRT));
	}

	/** Doors, trapdoors and fence gates are opened and closed by villagers (vanilla), so only their kind and facing count, not open/powered. */
	static int stateKey(final BlockState st) {
		if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
			|| st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
			BlockState n = st;
			if (n.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
				n = n.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, false);
			}
			if (n.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
				n = n.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false);
			}
			return n.toString().hashCode();
		}
		return st.toString().hashCode();
	}

	static long areaHash(final ServerLevel l, final BlockPos c) {
		long h = 1125899906842597L;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-24, -6, -24), c.offset(24, 10, 24))) {
			BlockState st = l.getBlockState(q);
			if (volatileBlock(st)) {
				continue; // crops, leaves, grass and the like change on their own; fire does not and is counted
			}
			h = 31 * h + stateKey(st);
		}
		return h;
	}

	static JsonArray areaCells(final ServerLevel l, final BlockPos c) {
		JsonArray a = new JsonArray();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-24, -6, -24), c.offset(24, 10, 24))) {
			BlockState st = l.getBlockState(q);
			a.add(volatileBlock(st) ? 0 : stateKey(st));
		}
		return a;
	}

	static final class UsedSeed implements QAServer.Step {
		int t, cx, cz, dimIdx, vt;
		BlockPos vpos;
		int total;

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			ServerLevel ow = server.overworld();
			int rc = PREGEN / 16 + 1;
			if (t++ == 0) {
				cx = -rc;
				cz = -rc;
				QAServer.LOG.info("[qa] pre-generating {} blocks radius ({}x{} chunks) in the Overworld", PREGEN, rc * 2 + 1, rc * 2 + 1);
			}
			if (cx <= rc) {
				for (int i = 0; i < 40 && cx <= rc; i++) {
					ow.getChunk(cx, cz);
					total++;
					if (++cz > rc) {
						cz = -rc;
						cx++;
					}
				}
				if (total % 1000 < 40) {
					QAServer.LOG.info("[qa] pregen {} chunks", total);
				}
				return false;
			}
			if (dimIdx == 0) {
				QAServer.record("PASS", "used seed", total + " Overworld chunks generated within " + PREGEN + " blocks of spawn");
				List<net.minecraft.resources.ResourceKey<Level>> dims = QAServer.dims(server);
				int n = 0;
				for (var k : dims) {
					String p = k.identifier().getPath();
					if (p.equals("petalwind") || p.equals("ironpeak")) {
						ServerLevel l = server.getLevel(k);
						for (int x = -3; x <= 3; x++) {
							for (int z = -3; z <= 3; z++) {
								l.getChunk(x, z);
							}
						}
						n++;
					}
				}
				QAServer.record(n == 2 ? "PASS" : "WARN", "used seed", n + " Elsewhere dimensions got 49 chunks each (Petalwind, Ironpeak)");
				dimIdx = 1;
				return false;
			}
			// village near spawn + one trade
			if (vpos == null) {
				List<Holder.Reference<Structure>> vs0 = new ArrayList<>();
				ow.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
					if (h.key().identifier().getPath().startsWith("village_") && h.key().identifier().getNamespace().equals("minecraft")) {
						vs0.add(h);
					}
				});
				var pr = ow.getChunkSource().getGenerator().findNearestMapStructure(ow, HolderSet.direct(vs0), BlockPos.ZERO, 40, false);
				vpos = pr == null ? BlockPos.ZERO : pr.getFirst();
				for (int dx = -5; dx <= 5; dx++) {
					for (int dz = -5; dz <= 5; dz++) {
						ow.setChunkForced((vpos.getX() >> 4) + dx, (vpos.getZ() >> 4) + dz, true);
					}
				}
				QAServer.LOG.info("[qa] village search from spawn found {}", pr == null ? "nothing" : vpos.toShortString());
			}
			if (vt++ < 60) {
				return false; // let the village chunks and their entities load
			}
			JsonObject seed = new JsonObject();
			List<Holder.Reference<Structure>> vs = new ArrayList<>();
			ow.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
				if (h.key().identifier().getPath().startsWith("village_") && h.key().identifier().getNamespace().equals("minecraft")) {
					vs.add(h);
				}
			});
			var pair = ow.getChunkSource().getGenerator().findNearestMapStructure(ow, HolderSet.direct(vs), BlockPos.ZERO, 40, false);
			if (pair == null) {
				QAServer.record("FAIL", "used seed", "no village within 640 blocks of spawn in this seed: cannot trade; change level-seed");
			} else {
				BlockPos vp = pair.getFirst();
				ow.getChunk(vp);
				Entity best = null;
				for (Entity e : ow.getEntitiesOfClass(Entity.class, new AABB(vp).inflate(96), e -> e instanceof Merchant && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("villager")
					&& !((Merchant)e).getOffers().isEmpty())) {
					if (best == null || e.distanceToSqr(vp.getX(), vp.getY(), vp.getZ()) < best.distanceToSqr(vp.getX(), vp.getY(), vp.getZ())) {
						best = e;
					}
				}
				if (best == null) {
					best = net.minecraft.world.entity.EntityTypes.VILLAGER.create(ow, EntitySpawnReason.COMMAND);
					int gy = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vp.getX(), vp.getZ());
					best.snapTo(vp.getX() + 0.5, gy, vp.getZ() + 0.5, 0, 0);
					((Mob)best).finalizeSpawn(ow, ow.getCurrentDifficultyAt(vp), EntitySpawnReason.COMMAND, null);
					ow.addFreshEntity(best);
					QAServer.record("WARN", "used seed", "village at " + vp.toShortString() + " had no loaded villager; spawned one there for the trade");
				}
				{
					Merchant m = (Merchant)best;
					FakePlayer p = QAServer.fake(ow, "QATrader0");
					m.setTradingPlayer(p);
					var offers = m.getOffers();
					int uses = -1;
					if (!offers.isEmpty()) {
						MerchantOffer o = offers.get(0);
						m.notifyTrade(o);
						uses = o.getUses();
					}
					m.setTradingPlayer(null);
					BlockPos at = best.blockPosition();
					seed.addProperty("villageX", at.getX());
					seed.addProperty("villageY", at.getY());
					seed.addProperty("villageZ", at.getZ());
					seed.addProperty("villager", best.getUUID().toString());
					seed.addProperty("hash", Long.toString(areaHash(ow, at)));
					seed.add("cells", areaCells(ow, at));
					QAServer.record(uses > 0 ? "PASS" : "WARN", "used seed", "one trade made with the villager at " + at.toShortString() + " (village structure at " + vp.toShortString() + ", offer uses now " + uses + ")");
				}
			}
			seed.addProperty("pregen", PREGEN);
			JsonArray structs = new JsonArray();
			ow.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
				if (h.key().identifier().getNamespace().equals("elsewhere")) {
					structs.add(h.key().identifier().toString());
				}
			});
			seed.add("structures", structs);
			seed.addProperty("modVersion", FabricLoader.getInstance().getModContainer("elsewhere").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
			Files.writeString(USED, seed.toString());
			server.saveEverything(true, true, true);
			QAServer.record("PASS", "used seed", "world saved with " + seed.get("modVersion").getAsString());
			return true;
		}
	}

	static final class UsedCheck implements QAServer.Step {
		int t;
		FakePlayer p;
		JsonObject seed;
		BlockPos at;

		@Override
		public boolean tick(final MinecraftServer server) throws Exception {
			ServerLevel ow = server.overworld();
			if (t++ == 0) {
				if (!Files.exists(USED)) {
					QAServer.record("FAIL", "used server", "run/used-seed.json missing: the 0.3.0 seed run did not finish");
					return true;
				}
				seed = JsonParser.parseString(Files.readString(USED)).getAsJsonObject();
				QAServer.record("PASS", "used server", "0.3.0-era world (" + seed.get("pregen").getAsInt() + "-block radius pre-generated, one village trade) opened by "
					+ FabricLoader.getInstance().getModContainer("elsewhere").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?") + " without a crash");
				if (seed.has("villageX")) {
					at = new BlockPos(seed.get("villageX").getAsInt(), seed.get("villageY").getAsInt(), seed.get("villageZ").getAsInt());
					for (int dx = -4; dx <= 4; dx++) {
						for (int dz = -4; dz <= 4; dz++) {
							ow.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, true);
						}
					}
					ow.getChunk(at);
					p = QAServer.fake(ow, "QAUsedWatcher");
					p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
					if (!ow.players().contains(p)) {
						ow.addNewPlayer(p);
					}
					QAServer.listPlayer(server, p, true);
				}
				return false;
			}
			if (t < 20 * 90) {
				return false; // 90 s with a player standing in the village: a conquest module would act now if it ever would
			}
			if (t == 20 * 90) {
				if (at != null) {
					boolean same = Long.toString(areaHash(ow, at)).equals(seed.get("hash").getAsString());
					String diffs = "";
					if (!same && seed.has("cells")) {
						JsonArray cells = seed.getAsJsonArray("cells");
						int i = 0, n = 0;
						for (BlockPos q : BlockPos.betweenClosed(at.offset(-24, -6, -24), at.offset(24, 10, 24))) {
							BlockState st = ow.getBlockState(q);
							int now = volatileBlock(st) ? 0 : stateKey(st);
							if (now != cells.get(i++).getAsInt()) {
								if (n++ < 6) {
									diffs += q.toShortString() + "=" + BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath() + " ";
								}
							}
						}
						diffs = " (" + n + " blocks differ, first: " + diffs.trim() + ")";
					}
					QAServer.check(same, "used server", "the village near spawn is unchanged after 90 s with a player present (no burning, breaking or rebuilding within 24 blocks)" + diffs);
					int soldiers = 0;
					for (Entity e : ow.getEntitiesOfClass(Entity.class, new AABB(at).inflate(150), e -> {
						String pth = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
						return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("elsewhere") && (kingdomPresent() && cls(KP + "KingdomSoldier").isInstance(e) || pth.contains("soldier") || pth.contains("serf"));
					})) {
						soldiers++;
					}
					QAServer.check(soldiers == 0, "used server", "no kingdom soldiers within 150 blocks of the traded village (it is never besieged or occupied): " + soldiers);
					p.level().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
					QAServer.listPlayer(server, p, false);
				}
				// kingdom castle resolves outside the generated area
				var reg = ow.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				List<Holder.Reference<Structure>> castles = new ArrayList<>();
				reg.listElements().forEach(h -> {
					String pth = h.key().identifier().getPath();
					if (h.key().identifier().getNamespace().equals("elsewhere") && (pth.contains("castle") || pth.contains("aldermark") || pth.contains("realm"))) {
						castles.add(h);
					}
				});
				if (castles.isEmpty()) {
					skip("used server", "no castle/realm structure (castle_aldermark) in this jar: kingdom realm resolution not checked");
				} else {
					int outside = seed.get("pregen").getAsInt();
					boolean found = false, allOutside = true;
					String info = "";
					for (Holder.Reference<Structure> h : castles) {
						var pr = ow.getChunkSource().getGenerator().findNearestMapStructure(ow, HolderSet.direct(h), BlockPos.ZERO, 100, false);
						if (pr != null) {
							found = true;
							BlockPos c = pr.getFirst();
							boolean out = Math.max(Math.abs(c.getX()), Math.abs(c.getZ())) > outside;
							allOutside &= out;
							info += h.key().identifier() + " at " + c.toShortString() + (out ? " (outside) " : " (INSIDE the old area) ");
						}
					}
					QAServer.check(found && allOutside, "used server", "kingdom castle/realm resolves outside the " + outside + "-block generated area: " + (info.isEmpty() ? "not found" : info));
				}
				// new 2.0 structures are found in chunks nobody generated
				Set<String> old = new TreeSet<>();
				seed.getAsJsonArray("structures").forEach(e -> old.add(e.getAsString()));
				Set<String> fresh = new TreeSet<>();
				reg.listElements().forEach(h -> {
					String n = h.key().identifier().toString();
					if (n.startsWith("elsewhere:") && !old.contains(n)) {
						fresh.add(n);
					}
				});
				if (fresh.isEmpty()) {
					skip("used locate", "no structure id is new compared with 0.3.0");
					return true;
				}
				QAServer.record("PASS", "used locate", fresh.size() + " structures are new since 0.3.0: " + fresh);
				Locate l = new Locate("used locate", true);
				l.only = fresh;
				QAServer.STEPS.addFirst(l);
				return true;
			}
			return true;
		}
	}
}
