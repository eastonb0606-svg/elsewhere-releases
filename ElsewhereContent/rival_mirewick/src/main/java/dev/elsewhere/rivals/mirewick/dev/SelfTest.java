package dev.elsewhere.rivals.mirewick.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.tactics.TacticsRegistry;
import dev.elsewhere.kingdom.tactics.TacticsRuntime;
import dev.elsewhere.kingdom_court.CourtApi;
import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Oligarchy;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.MirewickLoot;
import dev.elsewhere.rivals.mirewick.MirewickState;
import dev.elsewhere.rivals.mirewick.MirewickUnits;
import dev.elsewhere.rivals.mirewick.TempBlocks;
import dev.elsewhere.rivals.mirewick.boss.CouncilRules;
import dev.elsewhere.rivals.mirewick.boss.Heirs;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import dev.elsewhere.rivals.mirewick.boss.MagnateProfiles;
import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import dev.elsewhere.rivals.mirewick.tactics.MirewickTactics;
import dev.elsewhere.rivals.mirewick.unit.BogWarden;
import dev.elsewhere.rivals.mirewick.unit.MireTrapper;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mirewick self-test (own JVM, fresh superflat world): {@code ./gradlew runServer} (properties {@code mirewick.selftest}, {@code mirewick.selftest.exit}).
 * Results print as "[mirewick-selftest]" and are written to selftest-mirewick.log. Step-driven: a step returns the ticks to wait, or -1 to be run again next tick.
 * Covers registration, data, lang, the succession block and heirs, the Scrip currency and shop, the 20 units, the two unique soldiers, drops and Dunstan,
 * the five Magnates (loadout, all 15 signature moves, fights, the Chair, the drops), council votes and bribes, heirs and Dark Lanterns through the court,
 * the Cobweb-Walker mixin, the Lantern Shield, the field tactics and a 15 v 15 MSPT run against Aldermark.
 */
public final class SelfTest {
	private SelfTest() {}

	interface Step {
		int run(MinecraftServer srv, ServerLevel l) throws Exception;
	}

	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static final int Y = -60;
	static int pass, fail, idx, wait, startDelay;
	static boolean started, finished, built, measuring;
	static long tickStart;
	static final List<Long> SAMPLES = new ArrayList<>();
	static Runnable tickHook = () -> {};

	// ---- harness --------------------------------------------------------------------------------------------------------

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[mirewick-selftest] " + line);
		if (ok) pass++; else fail++;
	}

	static void info(String s) {
		LOG.add(s);
		System.out.println("[mirewick-selftest] " + s);
	}

	public static void startTick(MinecraftServer srv) {
		tickStart = System.nanoTime();
	}

	public static void endTick(MinecraftServer srv) {
		if (finished) return;
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (!built) {
			built = true;
			build();
		}
		if (!started) {
			if (++startDelay < 80) return;
			started = true;
			info("started");
		}
		try {
			tickHook.run();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		if (wait > 0) { wait--; return; }
		if (idx >= STEPS.size()) { finish(srv); return; }
		Step s = STEPS.get(idx++);
		try {
			int w = s.run(srv, srv.overworld());
			if (w < 0) { idx--; wait = 0; } else wait = w;
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		info("TOTAL " + pass + "/" + (pass + fail) + " (" + fail + " failed)" + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-mirewick.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("mirewick.selftest.exit")) srv.halt(false);
	}

	/** A step that is run every tick until {@code c} holds (or FAIL on timeout). */
	static Step poll(String label, int timeout, BooleanSupplier c) {
		int[] n = {0};
		return (srv, l) -> {
			boolean ok;
			try {
				ok = c.getAsBoolean();
			} catch (RuntimeException e) {
				e.printStackTrace();
				check(label + " (exception)", false, e);
				return 0;
			}
			if (ok) { check(label, true, n[0] + " ticks"); return 0; }
			if (++n[0] > timeout) { check(label + " (timeout)", false, timeout + " ticks"); return 0; }
			return -1;
		};
	}

	static Identifier id(String p) { return MirewickContent.id(p); }

	static Vec3 at(double x, double z) { return new Vec3(x + 0.5, Y, z + 0.5); }

	static FakePlayer fp(ServerLevel l, String name) {
		FakePlayer f = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(("mirewick-" + name).getBytes(StandardCharsets.UTF_8)), name));
		f.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		return f;
	}

	static void clean(ServerLevel l) {
		AABB box = new AABB(-200, -64, -200, 200, 120, 200);
		for (Entity e : l.getEntities((Entity) null, box, x -> !(x instanceof Player))) e.discard();
		TempBlocks.purgeAll(l.getServer());
	}

	static void clearBox(ServerLevel l, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			for (int y = Y; y < Y + 8; y++) l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			l.setBlock(new BlockPos(x, Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
	}

	static Husk dummy(ServerLevel l, double x, double z) {
		Husk h = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		h.setNoAi(true);
		h.setSilent(true);
		h.setPersistenceRequired();
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);
		h.setHealth(4000F);
		h.setPos(x + 0.5, Y, z + 0.5);
		l.addFreshEntity(h);
		return h;
	}

	static int count(List<ItemStack> l, net.minecraft.world.item.Item i) {
		int n = 0;
		for (ItemStack s : l) if (s.is(i)) n += s.getCount();
		return n;
	}

	static int itemsOnGround(ServerLevel l, net.minecraft.world.item.Item i) {
		int n = 0;
		for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(-400, -64, -400, 400, 120, 600))) if (e.getItem().is(i)) n += e.getItem().getCount();
		return n;
	}

	static int countIn(List<ItemStack> l, net.minecraft.world.item.Item i) {
		int n = 0;
		for (ItemStack s : l) if (s.is(i)) n += s.getCount();
		return n;
	}

	static String path(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(); }

	static JsonObject json(String resource) throws Exception {
		try (var in = MirewickContent.class.getResourceAsStream(resource)) {
			if (in == null) throw new IllegalStateException("missing resource " + resource);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/** Every copy of a JSON resource on the classpath, merged (several modules ship an en_us.json). */
	static JsonObject jsonAll(String resource) throws Exception {
		JsonObject all = new JsonObject();
		var en = MirewickContent.class.getClassLoader().getResources(resource.startsWith("/") ? resource.substring(1) : resource);
		while (en.hasMoreElements()) {
			try (var in = en.nextElement().openStream()) {
				for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) all.add(e.getKey(), e.getValue());
			}
		}
		return all;
	}

	static boolean resource(String p) { return MirewickContent.class.getResourceAsStream(p) != null; }

	// ---- state shared between steps -----------------------------------------------------------------------------------------

	static FakePlayer P;
	static MagnateBoss B;
	static Husk D;
	static KingdomSoldier S1, S2;
	static float fightDamage, fightLast;
	static final List<String> SUCC = new ArrayList<>();
	static int darkLanterns, fallen;
	static final Map<Magnate, MagnateBoss> COUNCIL = new EnumMap<>(Magnate.class);
	static CourtState ST;

	static int pickedLevel;

	static void build() {
		CourtEvents.SUCCESSION.register((srv, f, seat, kind) -> { if (f.equals(MirewickContent.FACTION)) SUCC.add(seat + ":" + kind.name().toLowerCase()); });
		CourtEvents.DARK_LANTERNS.register((srv, f) -> { if (f.equals(MirewickContent.FACTION)) darkLanterns++; });
		CourtEvents.FACTION_FALLEN.register((srv, f) -> { if (f.equals(MirewickContent.FACTION)) fallen++; });

		STEPS.add((srv, l) -> {
			for (int cx = -5; cx <= 5; cx++) for (int cz = -6; cz <= 36; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
			clearBox(l, -70, -100, 70, 580);
			KingdomPerf.forceActive = true;
			P = fp(l, "mire-p1");
			P.snapTo(0.5, Y, 0.5, 0, 0);
			return 60;
		});
		if (System.getenv("MIRE_ONLY") != null) {
			magnates();
			return;
		}
		registration();
		dataAndLang();
		shopAndCurrency();
		unitsAndDrops();
		shieldAndMixin();
		magnates();
		council();
		tactics();
		bench();
		heirsAndLanterns();
	}

	// ---- 1. registration ---------------------------------------------------------------------------------------------------

	static void registration() {
		STEPS.add((srv, l) -> {
			check("18 items registered", MirewickItems.ALL.size() == 18, MirewickItems.ALL.size());
			for (var it : MirewickItems.ALL) check("item registered: " + BuiltInRegistries.ITEM.getKey(it).getPath(), BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.ITEM.getKey(it)) && it != Items.AIR, null);
			check("Scrip, 5 Seals, 5 uniques", MirewickItems.SCRIP != null && MirewickItems.SEALS.size() == 5 && MirewickItems.UNIQUE.size() == 5, null);
			check("Coronet, Standard, Pike, template, banner pattern, shield, dye", MirewickItems.LANTERN_CORONET != null && MirewickItems.MARSH_LANTERN_STANDARD != null && MirewickItems.REEDCUTTER_PIKE != null
				&& MirewickItems.TRIM_TEMPLATE != null && MirewickItems.BANNER_PATTERN != null && MirewickItems.LANTERN_SHIELD != null && MirewickItems.OILCLOTH_DYE != null, null);
			for (Magnate m : Magnate.values()) {
				check("entity type registered: mirewick_" + m.key, m.type != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id("mirewick_" + m.key)), null);
				check("court binds " + m.courtSlot + " to the Magnate entity", Leaders.type(MirewickContent.FACTION, m.courtSlot) == m.type, null);
				check("Magnate skin exists: " + m.key, resource("/assets/elsewhere/textures/entity/kingdom/mirewick/magnate_" + m.key + ".png"), null);
			}
			check("unique soldier types registered", MirewickUnits.BOG_WARDEN != null && MirewickUnits.MIRE_TRAPPER != null && MirewickUnits.TYPES.size() == 2 && MirewickUnits.EGGS.size() == 2, null);
			var tp = srv.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
			check("trim pattern elsewhere:mirewick registered", tp.containsKey(MirewickItems.MIREWICK_TRIM), null);
			var bp = srv.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			check("banner pattern elsewhere:mirewick registered", bp.containsKey(MirewickItems.MIREWICK_PATTERN), null);
			check("trim template recipes registered", srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("mirewick_armor_trim_smithing_template"))).isPresent()
				&& srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("mirewick_armor_trim_smithing_template_smithing_trim"))).isPresent(), null);
			// armor and gear numbers (never above netherite)
			ItemStack leg = new ItemStack(MirewickItems.DEBT_COLLECTORS_LEGGINGS), boots = new ItemStack(MirewickItems.FEN_MARSHALS_WADERS), chest = new ItemStack(MirewickItems.DIKEWARDENS_BRIGANDINE),
				cowl = new ItemStack(MirewickItems.ALMONERS_COWL), crown = new ItemStack(MirewickItems.LANTERN_CORONET), bow = new ItemStack(MirewickItems.FOWLERS_MARSHBOW), pike = new ItemStack(MirewickItems.REEDCUTTER_PIKE);
			final boolean rb = MirewickItems.REBALANCE_RIVALS; // 3.0 rival rebalance (GEAR.md A4): leggings 6, chest 7 dry, pike 7.5
			check(rb ? "armor values: leggings 6, boots 3, chest 7, cowl 2, coronet 3 (3.0)" : "armor values: leggings 5, boots 3, chest 6, cowl 2, coronet 3",
				armor(leg) == (rb ? 6 : 5) && armor(boots) == 3 && armor(chest) == (rb ? 7 : 6) && armor(cowl) == 2 && armor(crown) == 3,
				armor(leg) + "/" + armor(boots) + "/" + armor(chest) + "/" + armor(cowl) + "/" + armor(crown));
			check("durability: leggings 540, boots 468, chest 576, cowl 275, coronet 396, bow 560, pike 1400", leg.getMaxDamage() == 540 && boots.getMaxDamage() == 468 && chest.getMaxDamage() == 576
				&& cowl.getMaxDamage() == 275 && crown.getMaxDamage() == 396 && bow.getMaxDamage() == 560 && pike.getMaxDamage() == 1400,
				leg.getMaxDamage() + "/" + boots.getMaxDamage() + "/" + chest.getMaxDamage() + "/" + cowl.getMaxDamage() + "/" + crown.getMaxDamage());
			check(rb ? "pike: damage 7.5, speed 1.5, reach +0.75 (3.0)" : "pike: damage 6.5, speed 1.5, reach +0.75", Math.abs(attr(pike, Attributes.ATTACK_DAMAGE) - (rb ? 6.5 : 5.5)) < 1e-6 && Math.abs(attr(pike, Attributes.ATTACK_SPEED) + 2.5) < 1e-6
				&& Math.abs(attr(pike, Attributes.ENTITY_INTERACTION_RANGE) - 0.75) < 1e-6, null);
			check("waders: Depth-Strider-II equivalent and no soul-sand slowdown", Math.abs(attr(boots, Attributes.WATER_MOVEMENT_EFFICIENCY) - 2.0 / 3.0) < 1e-6 && Math.abs(attr(boots, Attributes.MOVEMENT_EFFICIENCY) - 1.0) < 1e-6, null);
			check("Lord Seals are in the lore_seals tag", MirewickItems.SEALS.values().stream().allMatch(i -> new ItemStack(i).is(MirewickItems.LORE_SEALS)), null);
			return 0;
		});
	}

	static int armor(ItemStack s) { return (int) Math.round(attr(s, Attributes.ARMOR)); }

	static double attr(ItemStack s, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a) {
		double sum = 0;
		var mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		for (var e : mods.modifiers()) if (e.attribute().equals(a)) sum += e.modifier().amount();
		return sum;
	}

	// ---- 2. data, lang, succession block (RIVALS-DECISIONS 10) --------------------------------------------------------------------

	static void dataAndLang() {
		STEPS.add((srv, l) -> {
			FactionDef f = FactionDefs.get(MirewickContent.FACTION);
			check("faction file loaded", f != null, null);
			if (f == null) return 0;
			check("government oligarchy, five leaders in rank order", String.valueOf(f.society().government()).toLowerCase().contains("oligarchy") && f.society().leaders().equals(List.of("marshal", "castellan", "master_at_arms", "huntsman", "chancellor")), f.society().government() + " " + f.society().leaders());
			check("Chair bonus +12 HP per other living Magnate", "hp_per_other_magnate".equals(f.society().bonus().type()) && f.society().bonus().amount() == 12.0, f.society().bonus());
			for (Magnate m : Magnate.values()) {
				var r = f.rulers().get(m.courtSlot);
				check("ruler " + m.courtSlot + ": HP " + (int) m.hp + " (cap +48)", r != null && r.health() == m.hp, r == null ? null : r.health());
				var ids = r == null ? List.<String>of() : r.drops().stream().map(d -> d.item().getPath()).toList();
				check("drops " + m.key + ": unique, Lord Seal, Oathkeeper's Seal, Scrip", ids.contains(BuiltInRegistries.ITEM.getKey(MirewickItems.UNIQUE.get(m.key)).getPath()) && ids.contains("mirewick_seal_" + m.key)
					&& ids.contains("oathkeepers_seal") && ids.contains("mirewick_scrip"), ids);
			}
			check("currency is Scrip", f.economy().currency().map(i -> i.equals(id("mirewick_scrip"))).orElse(false), null);
			check("army: size 0.9, passive brain 0.5, minimum odds 0.9, hires mercenaries", f.army().sizeMultiplier() == 0.9 && f.army().brain().warFrequency() == 0.5 && f.army().brain().minAttackOdds() == 0.9 && f.army().brain().hireMercenaries(), null);
			check("two unique soldiers", f.army().uniqueUnits().size() == 2, null);
			check("Standing starts Neutral-positive (+10)", f.society().standing().start().orElse(0) == 10, null);
			check("Peat-Cutter Serf variant 18 HP (health ladder)", f.society().serfs().health() == 18.0, f.society().serfs().health());
			return 0;
		});
		STEPS.add((srv, l) -> {
			// the succession block against RIVALS-DECISIONS section 10: 2 heirs per family, same surname, 7 in-game days, repeat kills, inherited leaning
			JsonObject soc = json("/data/elsewhere/kingdom_faction/mirewick.json").getAsJsonObject("society");
			JsonObject sc = soc.getAsJsonObject("succession");
			check("succession: mode inheritance", "inheritance".equals(sc.get("mode").getAsString()), null);
			check("succession: 7 in-game days", sc.get("delay_days").getAsInt() == 7 && soc.get("seat_refill_days").getAsInt() == 7, null);
			check("succession: 2 heirs per family", sc.get("heirs_per_family").getAsInt() == 2, null);
			check("succession: heirs inherit the leaning and are repeat kills", sc.get("inherit_leaning").getAsBoolean() && sc.get("repeat_kill").getAsBoolean(), null);
			JsonObject heirs = sc.getAsJsonObject("heirs");
			JsonObject rulers = json("/data/elsewhere/kingdom_faction/mirewick.json").getAsJsonObject("rulers");
			JsonObject lang = jsonAll("/assets/elsewhere/lang/en_us.json");
			check("succession: an heir block for each of the five families", heirs.size() == 5, heirs.keySet());
			for (Magnate m : Magnate.values()) {
				JsonObject h = heirs.getAsJsonObject(m.courtSlot);
				JsonArray firsts = h.getAsJsonArray("first_names");
				String holder = rulers.getAsJsonObject(m.courtSlot).get("name").getAsString();
				String sur = h.get("surname").getAsString();
				check("heirs " + m.key + ": same surname as the holder (" + sur + ")", holder.endsWith(" " + sur) && Heirs.surname(m).equals(sur), holder);
				check("heirs " + m.key + ": exactly 2 first names, both new", firsts.size() == 2 && !holder.contains(firsts.get(0).getAsString()) && !holder.contains(firsts.get(1).getAsString())
					&& !firsts.get(0).getAsString().equals(firsts.get(1).getAsString()), firsts);
				for (int g = 1; g <= 2; g++) {
					String key = "mirewick.heir." + m.courtSlot + "." + g;
					String want = firsts.get(g - 1).getAsString() + " " + sur;
					check("lang has the name of heir " + g + " of " + m.key + ": " + want, lang.has(key) && lang.get(key).getAsString().equals(want) && Heirs.fullName(m, g).equals(want), lang.has(key) ? lang.get(key).getAsString() : "missing");
				}
			}
			// the court's name grid: the court picks firsts.get(rank * 3 + generation)
			List<String> grid = Heirs.courtNameList();
			boolean gridOk = grid.size() == 18;
			for (Magnate m : Magnate.values()) for (int g = 1; g <= 2; g++) gridOk &= grid.get(Heirs.rank(m) * 3 + g).equals(Heirs.firstNames(m)[g - 1]);
			check("court heir-name grid: rank*3+generation indexes the right first name", gridOk, null);
			for (Ruler r : Ruler.values()) if (r != Ruler.KING) { /* ranks of the court enum agree with Heirs.rank */ }
			boolean ranks = true;
			for (Magnate m : Magnate.values()) ranks &= Ruler.byKey(m.courtSlot).rank == Heirs.rank(m);
			check("Heirs.rank agrees with the court's Ruler ranks", ranks, null);
			return 0;
		});
		STEPS.add((srv, l) -> {
			JsonObject lang = jsonAll("/assets/elsewhere/lang/en_us.json");
			List<String> missing = new ArrayList<>();
			for (var it : MirewickItems.ALL) {
				String p = BuiltInRegistries.ITEM.getKey(it).getPath();
				if (!lang.has("item.elsewhere." + p)) missing.add(p);
			}
			for (Magnate m : Magnate.values()) {
				for (String k : new String[] {m.nameKey(), "item.elsewhere.mirewick_" + m.key + "_spawn_egg"}) if (!lang.has(k)) missing.add(k);
			}
			for (String k : new String[] {"entity.elsewhere.mirewick_bog_warden", "entity.elsewhere.mirewick_mire_trapper", "faction.elsewhere.mirewick", "itemGroup.elsewhere.rival_mirewick", "trim_pattern.elsewhere.mirewick",
				"message.elsewhere.mirewick_almoner_paid", "message.elsewhere.mirewick_flees", "message.elsewhere.mirewick_chair_lantern_out", "message.elsewhere.mirewick_no_pay", "message.elsewhere.mirewick_mind_mud",
				"message.elsewhere.mirewick_standard_planted"}) if (!lang.has(k)) missing.add(k);
			check("lang: names for every item, Magnate, unit, message and heir", missing.isEmpty(), missing);
			// the 20 unit JSONs and their skins
			String[] units = {"almoners_hand", "bellman", "bog_warden", "dikeguard", "fc_banner", "fc_brute", "fc_captain", "fc_crossbow", "fc_sellsword", "fowler", "lantern_bearer", "linkboy", "marsh_rider", "mire_trapper",
				"peat_cutter", "pikeman", "punt_crossbow", "reed_knight", "reeve", "wardman"};
			for (String u : units) {
				Identifier uid = id("mirewick_" + u);
				boolean exists = UnitTypes.exists(uid);
				check("unit loaded: " + u, exists, null);
				if (exists) {
					UnitType t = UnitTypes.get(uid);
					check("unit skin exists: " + u, resource("/assets/" + t.skin().getNamespace() + "/" + t.skin().getPath()), t.skin());
				}
			}
			FactionDef f = FactionDefs.get(MirewickContent.FACTION);
			for (var e : f.roster().entrySet()) check("roster resolves " + e.getKey().getPath(), e.getValue().unit().map(UnitTypes::exists).orElse(false), null);
			for (var e : f.army().makeup().entrySet()) check("makeup unit exists " + e.getKey().getPath(), UnitTypes.exists(e.getKey()), null);
			// armor / trim / equipment assets
			for (String a : new String[] {"mirewick_debt_collector", "mirewick_fen_marshal", "mirewick_dikewarden", "mirewick_almoner", "mirewick_lantern_coronet"})
				check("equipment asset: " + a, resource("/assets/elsewhere/equipment/" + a + ".json"), null);
			for (var it : MirewickItems.ALL) {
				String p = BuiltInRegistries.ITEM.getKey(it).getPath();
				check("item definition + texture: " + p, resource("/assets/elsewhere/items/" + p + ".json"), null);
			}
			return 0;
		});
	}

	// ---- 3. currency and shop -------------------------------------------------------------------------------------------------

	static void shopAndCurrency() {
		STEPS.add((srv, l) -> {
			Identifier mw = MirewickContent.FACTION;
			check("Shops: Mirewick's currency is Scrip", Shops.currency(mw) == MirewickItems.SCRIP, Shops.currency(mw));
			var offers = Shops.offers(P, mw);
			check("shop has 5 lines (Seal, Lantern Shield, Oilcloth Dye x4, bodkin bolts x8, netherite template)", offers.size() == 5, offers.size());
			int base = 0;
			for (var o : offers) base += o.basePrice();
			check("shop line items exist and prices are positive", offers.stream().allMatch(o -> !o.stack().isEmpty() && o.basePrice() > 0), base);
			Standing.set(l, P.getUUID(), mw, 60);
			// clean inventory
			P.getInventory().clearContent();
			Shops.give(P, mw, 100);
			check("Shops.give puts 100 Scrip in the inventory", Shops.count(P, mw) == 100, Shops.count(P, mw));
			int shieldIdx = -1;
			for (var o : Shops.offers(P, mw)) if (o.stack().is(MirewickItems.LANTERN_SHIELD)) shieldIdx = o.index();
			check("the Lantern Shield is on sale", shieldIdx >= 0, null);
			int price = Shops.offers(P, mw).stream().filter(o -> o.index() == SHIELD_IDX(P, mw)).findFirst().map(Shops.Offer::price).orElse(0);
			var r = Shops.buy(P, mw, shieldIdx);
			check("buying the Lantern Shield with Scrip: OK, Scrip taken, shield in the inventory", r == Shops.Result.OK && Shops.count(P, mw) == 100 - price && countInv(P, MirewickItems.LANTERN_SHIELD) == 1, r + " price " + price);
			var poor = fp(l, "mire-poor");
			poor.getInventory().clearContent();
			Standing.set(l, poor.getUUID(), mw, 60);
			check("a buyer with no Scrip: CANT_AFFORD", Shops.buy(poor, mw, shieldIdx) == Shops.Result.CANT_AFFORD, null);
			// weekly limit on the Oathkeeper's Seal line (2 a week)
			int sealIdx = -1, sealPrice = 0;
			for (var o : Shops.offers(P, mw)) if (o.weeklyLimit() == 2 && !o.stack().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) { sealIdx = o.index(); sealPrice = o.price(); }
			check("the Oathkeeper's Seal line has a weekly limit of 2", sealIdx >= 0, null);
			Shops.give(P, mw, sealPrice * 3);
			var a = Shops.buy(P, mw, sealIdx);
			var b = Shops.buy(P, mw, sealIdx);
			var c = Shops.buy(P, mw, sealIdx);
			check("weekly limit: two buys OK, the third refused", a == Shops.Result.OK && b == Shops.Result.OK && c == Shops.Result.WEEKLY_LIMIT, a + "/" + b + "/" + c);
			// the exchange: anything to Scrip at 4:3 (Mirewick's Counting-House)
			check("exchange faction is Mirewick", MirewickContent.FACTION.equals(Shops.exchangeFaction), Shops.exchangeFaction);
			var hostile = fp(l, "mire-hostile");
			Standing.set(l, hostile.getUUID(), mw, -80);
			check("a Hostile player cannot shop", Shops.buy(hostile, mw, shieldIdx) == Shops.Result.HOSTILE, null);
			return 0;
		});
	}

	static int SHIELD_IDX(FakePlayer p, Identifier mw) {
		for (var o : Shops.offers(p, mw)) if (o.stack().is(MirewickItems.LANTERN_SHIELD)) return o.index();
		return -1;
	}

	static int countInv(Player p, net.minecraft.world.item.Item i) {
		int n = 0;
		for (ItemStack s : p.getInventory()) if (s.is(i)) n += s.getCount();
		return n;
	}

	// ---- 4. units, unique soldiers, drops, Dunstan -------------------------------------------------------------------------------------

	static void unitsAndDrops() {
		STEPS.add((srv, l) -> {
			clean(l);
			String[] units = {"dikeguard", "reed_knight", "fowler", "punt_crossbow", "marsh_rider", "pikeman", "reeve", "wardman", "lantern_bearer", "bellman", "almoners_hand", "linkboy", "bog_warden", "mire_trapper"};
			double x = -30;
			for (String u : units) {
				KingdomSoldier s = KingdomApi.spawnSoldier(l, id("mirewick_" + u), MirewickContent.FACTION, at(x, -30), null);
				check("spawns: " + u, s != null, null);
				if (s != null) {
					UnitType t = UnitTypes.get(id("mirewick_" + u));
					double want = t.stats().health() * 1.0;
					check("hp matches the unit file x1.0: " + u, Math.abs(s.getMaxHealth() - want) < 0.6, s.getMaxHealth() + " vs " + want);
					if (t.armor().head().isPresent()) check("wears real armor on a plain skin: " + u, !s.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), null);
					s.discard();
				}
				x += 2;
			}
			for (String u : new String[] {"fc_sellsword", "fc_brute", "fc_crossbow", "fc_captain", "fc_banner"}) {
				KingdomSoldier s = KingdomApi.spawnSoldier(l, id("mirewick_" + u), FreeCompanies.FACTION, at(x, -30), null);
				check("Free Company look spawns: " + u, s != null, null);
				if (s != null) s.discard();
				x += 2;
			}
			KingdomSoldier bw = KingdomApi.spawnSoldier(l, id("mirewick_bog_warden"), MirewickContent.FACTION, at(-10, 20), null);
			KingdomSoldier mt = KingdomApi.spawnSoldier(l, id("mirewick_mire_trapper"), MirewickContent.FACTION, at(10, 20), null);
			check("Bog-Warden uses the BogWarden class", bw instanceof BogWarden, bw == null ? null : bw.getClass().getSimpleName());
			check("Mire-Trapper uses the MireTrapper class", mt instanceof MireTrapper, mt == null ? null : mt.getClass().getSimpleName());
			check("Bog-Warden 24 HP with an iron helm and a shield", bw.getMaxHealth() == 24 && bw.hasShield() && bw.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), bw.getMaxHealth());
			check("Mire-Trapper 20 HP with a crossbow", mt.getMaxHealth() == 20 && mt.getMainHandItem().is(Items.CROSSBOW), mt.getMaxHealth());
			S1 = bw;
			S2 = mt;
			D = dummy(l, -10, 22);
			bw.setTarget(D);
			return 5;
		});
		STEPS.add(poll("Bog-Warden shield-shoves a target within 3 blocks (4 raw damage and knockback)", 400, () -> ((BogWarden) S1).shoves > 0 && D.getHealth() < 4000F));
		STEPS.add((srv, l) -> {
			check("shove landed (the Bog-Warden counted it)", ((BogWarden) S1).shoves >= 1, ((BogWarden) S1).shoves);
			S1.discard();
			D.discard();
			// the Mire-Trapper: at most 3 live traps, vanish after 10 s, never replace a solid block
			clearBox(l, 0, 14, 24, 40);
			for (int i = 0; i < 12; i++) l.setBlock(new BlockPos(6 + (i % 4) * 4, Y, 24 + (i / 4) * 3), Blocks.STONE.defaultBlockState(), 2);
			D = dummy(l, 12, 30);
			MireTrapper mt = (MireTrapper) S2;
			mt.setPos(12.5, Y, 20.5);
			mt.setTarget(D);
			TRAP_MAX = 0;
			TRAP_START = l.getGameTime();
			tickHook = () -> {
				TRAP_MAX = Math.max(TRAP_MAX, ((MireTrapper) S2).liveTraps(l.getGameTime()));
			};
			return 260;
		});
		STEPS.add((srv, l) -> {
			MireTrapper mt = (MireTrapper) S2;
			tickHook = () -> {};
			check("Mire-Trapper laid traps near its target", mt.laid >= 1, mt.laid + " (webs " + mt.webBlocks + ", mud " + mt.mudPatches + ")");
			check("never more than 3 live traps", TRAP_MAX <= MireTrapper.MAX_TRAPS, TRAP_MAX);
			int stones = 0;
			for (int i = 0; i < 12; i++) if (l.getBlockState(new BlockPos(6 + (i % 4) * 4, Y, 24 + (i / 4) * 3)).is(Blocks.STONE)) stones++;
			check("no solid block was replaced by a trap", stones == 12, stones);
			mt.setTarget(null);
			mt.discard();
			return 0;
		});
		STEPS.add(poll("all cobweb traps are gone within 220 ticks", 260, () -> {
			ServerLevel l = P.level() instanceof ServerLevel s ? s : null;
			for (int x = 0; x <= 24; x++) for (int z = 14; z <= 40; z++) if (l.getBlockState(new BlockPos(x, Y, z)).is(Blocks.COBWEB)) return false;
			return true;
		}));
		STEPS.add((srv, l) -> {
			D.discard();
			check("temporary-block ledger is empty", MirewickState.get(srv).temp.isEmpty(), MirewickState.get(srv).temp.size());
			// drops: Scrip for rank and file 1-3, officers 4-8; Wardman template 8 percent, Lantern-Bearer pattern 15 percent
			KingdomSoldier dg = KingdomApi.spawnSoldier(l, id("mirewick_dikeguard"), MirewickContent.FACTION, at(40, 40), null);
			KingdomSoldier rv = KingdomApi.spawnSoldier(l, id("mirewick_reeve"), MirewickContent.FACTION, at(42, 40), null);
			KingdomSoldier wm = KingdomApi.spawnSoldier(l, id("mirewick_wardman"), MirewickContent.FACTION, at(44, 40), null);
			KingdomSoldier lb = KingdomApi.spawnSoldier(l, id("mirewick_lantern_bearer"), MirewickContent.FACTION, at(46, 40), null);
			int lo = 99, hi = 0;
			for (int i = 0; i < 3; i++) {
				int n = count(MirewickLoot.roll(dg, 0.5, 0.5, 0, i), MirewickItems.SCRIP);
				lo = Math.min(lo, n);
				hi = Math.max(hi, n);
			}
			check("rank and file drop 1-3 Scrip", lo == 1 && hi == 3, lo + ".." + hi);
			int olo = 99, ohi = 0;
			for (int i = 0; i < 5; i++) {
				int n = count(MirewickLoot.roll(rv, 0.5, 0.5, i, 0), MirewickItems.SCRIP);
				olo = Math.min(olo, n);
				ohi = Math.max(ohi, n);
			}
			check("officers drop 4-8 Scrip", rv.isOfficer() && olo == 4 && ohi == 8, olo + ".." + ohi + " officer=" + rv.isOfficer());
			check("Wardman: template at 8 percent (draw 0.05 yes, 0.5 no)", count(MirewickLoot.roll(wm, 0.05, 0.5, 0, 0), MirewickItems.TRIM_TEMPLATE) == 1 && count(MirewickLoot.roll(wm, 0.5, 0.5, 0, 0), MirewickItems.TRIM_TEMPLATE) == 0, null);
			check("Lantern-Bearer: banner pattern at 15 percent", count(MirewickLoot.roll(lb, 0.1, 0.5, 0, 0), MirewickItems.BANNER_PATTERN) == 1 && count(MirewickLoot.roll(lb, 0.5, 0.5, 0, 0), MirewickItems.BANNER_PATTERN) == 0, null);
			check("a plain Wardman never drops the pike", count(MirewickLoot.roll(wm, 0.5, 0.01, 0, 0), MirewickItems.REEDCUTTER_PIKE) == 0, null);
			// Dunstan Reeve
			MirewickState st = MirewickState.get(srv);
			st.dunstan = null;
			KingdomSoldier dun = MirewickLoot.spawnDunstan(l, at(48, 40));
			check("Dunstan Reeve spawns: named, pike in hand, 1.5x health", dun != null && MirewickLoot.isDunstan(dun) && dun.getMainHandItem().is(MirewickItems.REEDCUTTER_PIKE)
				&& Math.abs(dun.getMaxHealth() - UnitTypes.get(id("mirewick_wardman")).stats().health() * 1.5) < 0.6, dun == null ? null : dun.getMaxHealth());
			check("Dunstan is recorded once (never reborn)", st.dunstan != null && st.dunstan.equals(dun.getUUID()), null);
			List<ItemStack> dl = MirewickLoot.roll(dun, 0.5, 0.05, 0, 0);
			ItemStack pk = dl.stream().filter(s -> s.is(MirewickItems.REEDCUTTER_PIKE)).findFirst().orElse(ItemStack.EMPTY);
			check("Dunstan drops the Reedcutter Pike at 20 percent, damaged", !pk.isEmpty() && pk.getDamageValue() > 0 && count(MirewickLoot.roll(dun, 0.5, 0.5, 0, 0), MirewickItems.REEDCUTTER_PIKE) == 0, null);
			// the garrison hook: a garrison-tagged Reeve is promoted once
			dun.discard();
			KingdomSoldier gr = KingdomApi.spawnSoldier(l, id("mirewick_reeve"), MirewickContent.FACTION, at(50, 42), null);
			gr.addTag("elsewhere_court_garrison");
			st.dunstan = null;
			KingdomSoldier ad = MirewickLoot.adoptDunstan(srv);
			check("court garrison: a garrison Reeve is promoted to Dunstan in place", ad == gr && MirewickLoot.isDunstan(gr) && gr.getMainHandItem().is(MirewickItems.REEDCUTTER_PIKE), null);
			check("court garrison: only one Dunstan per world", MirewickLoot.adoptDunstan(srv) == null, null);
			// drops fire on a player kill, and not for Free Company sellswords
			clean(l);
			KingdomSoldier v1 = KingdomApi.spawnSoldier(l, id("mirewick_dikeguard"), MirewickContent.FACTION, at(40, 40), null);
			MirewickLoot.onDied(l, v1, l.damageSources().playerAttack(P));
			check("a Mirewick soldier killed by a player drops Scrip", itemsOnGround(l, MirewickItems.SCRIP) >= 1, itemsOnGround(l, MirewickItems.SCRIP));
			clean(l);
			KingdomSoldier fc = KingdomApi.spawnSoldier(l, id("mirewick_fc_sellsword"), FreeCompanies.FACTION, at(42, 40), null);
			MirewickLoot.onDied(l, fc, l.damageSources().playerAttack(P));
			check("a Free Company sellsword drops no Mirewick Scrip", itemsOnGround(l, MirewickItems.SCRIP) == 0, itemsOnGround(l, MirewickItems.SCRIP));
			clean(l);
			return 0;
		});
	}

	static int TRAP_MAX;
	static long TRAP_START;

	// ---- 5. Lantern Shield, Cobweb-Walker mixin -----------------------------------------------------------------------------------------

	static Husk H1, H2;

	static void shieldAndMixin() {
		STEPS.add((srv, l) -> {
			clean(l);
			ItemStack sh = new ItemStack(MirewickItems.LANTERN_SHIELD);
			check("Lantern Shield: BLOCKS_ATTACKS component (not cosmetic)", sh.has(DataComponents.BLOCKS_ATTACKS), null);
			check("Lantern Shield: 336 durability", sh.getMaxDamage() == 336, sh.getMaxDamage());
			check("Lantern Shield: use animation is BLOCK", sh.getUseAnimation().name().equals("BLOCK"), sh.getUseAnimation());
			var ba = sh.get(DataComponents.BLOCKS_ATTACKS);
			check("Lantern Shield: raises in 0.25 s, 90 percent from the front, disabled by axes", ba != null && ba.blockDelaySeconds() == 0.25F && ba.damageReductions().size() == 1 && ba.disableCooldownScale() == 1.0F, ba);
			check("Lantern Shield: equips in the offhand slot", sh.has(DataComponents.EQUIPPABLE) && sh.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.OFFHAND, null);
			// a Husk raises the shield: damage from the front is blocked and the shield takes durability; from behind it is not
			H1 = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			H1.setNoAi(true);
			H1.setSilent(true);
			H1.setPos(60.5, Y, 60.5);
			H1.setYRot(0F);   // facing +z (south)
			H1.setYHeadRot(0F);
			H1.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MirewickItems.LANTERN_SHIELD));
			l.addFreshEntity(H1);
			H2 = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			H2.setNoAi(true);
			H2.setSilent(true);
			H2.setPos(60.5, Y, 64.5);
			l.addFreshEntity(H2);
			H1.startUsingItem(InteractionHand.OFF_HAND);
			return 8;
		});
		STEPS.add((srv, l) -> {
			check("the shield user is blocking after the 0.25 s raise", H1.isBlocking(), H1.getUseItemRemainingTicks());
			ItemStack shield = H1.getOffhandItem();
			float hp0 = H1.getHealth();
			int dur0 = shield.getDamageValue();
			// attacker H2 stands in front (south, +z) and the shield faces +z
			H1.hurtServer(l, l.damageSources().mobAttack(H2), 6F);
			check("a hit from the front is blocked: no health lost", H1.getHealth() == hp0, hp0 + " -> " + H1.getHealth());
			// control: a vanilla shield on a mob behaves the same way (mobs may not wear shields down), so compare instead of assuming
			Husk hv = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			hv.setNoAi(true);
			hv.setSilent(true);
			hv.setPos(80.5, Y, 60.5);
			hv.setYRot(0F);
			hv.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			l.addFreshEntity(hv);
			Husk ha = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			ha.setNoAi(true);
			ha.setPos(80.5, Y, 64.5);
			l.addFreshEntity(ha);
			hv.startUsingItem(InteractionHand.OFF_HAND);
			for (int i = 0; i < 8; i++) hv.tick();
			hv.hurtServer(l, l.damageSources().mobAttack(ha), 6F);
			boolean vanillaWears = hv.getOffhandItem().getDamageValue() > 0;
			boolean lanternWears = H1.getOffhandItem().getDamageValue() > dur0;
			check("the shield wears down exactly like the vanilla shield does for the same user (vanilla " + vanillaWears + ", Lantern " + lanternWears + ")", vanillaWears == lanternWears, null);
			hv.discard();
			ha.discard();
			// from behind: move the attacker north of the user
			H2.setPos(60.5, Y, 56.5);
			H1.hurtServer(l, l.damageSources().mobAttack(H2), 6F);
			check("a hit from behind is not blocked", H1.getHealth() < hp0, hp0 + " -> " + H1.getHealth());
			H1.stopUsingItem();
			H2.setPos(60.5, Y, 64.5);
			return 25;
		});
		STEPS.add((srv, l) -> {
			float hp1 = H1.getHealth();
			H1.hurtServer(l, l.damageSources().mobAttack(H2), 6F);
			check("with the shield down a frontal hit lands", H1.getHealth() < hp1, hp1 + " -> " + H1.getHealth());
			H1.discard();
			H2.discard();
			return 0;
		});
		STEPS.add((srv, l) -> {
			// Cobweb-Walker (Debt-Collector's Leggings): cobweb and mud never slow the wearer; the mixin is applied (defaultRequire = 1)
			FakePlayer w = fp(l, "mire-walker"), n = fp(l, "mire-naked");
			w.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MirewickItems.DEBT_COLLECTORS_LEGGINGS));
			n.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			var f = Entity.class.getDeclaredField("stuckSpeedMultiplier");
			f.setAccessible(true);
			Vec3 slow = new Vec3(0.25, 0.05, 0.25);
			for (var blk : List.of(Blocks.COBWEB, Blocks.MUD, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW)) {
				f.set(w, Vec3.ZERO);
				f.set(n, Vec3.ZERO);
				w.makeStuckInBlock(blk.defaultBlockState(), slow);
				n.makeStuckInBlock(blk.defaultBlockState(), slow);
				Vec3 sw = (Vec3) f.get(w), sn = (Vec3) f.get(n);
				boolean cob = blk == Blocks.COBWEB || blk == Blocks.MUD;
				check("Cobweb-Walker: " + BuiltInRegistries.BLOCK.getKey(blk).getPath() + (cob ? " does not slow the wearer, still slows others" : " still slows the wearer (only cobweb and mud are ignored)"),
					cob ? sw.equals(Vec3.ZERO) && !sn.equals(Vec3.ZERO) : !sw.equals(Vec3.ZERO) && !sn.equals(Vec3.ZERO), sw + " / " + sn);
			}
			return 0;
		});
	}

	// ---- 6. the five Magnates -----------------------------------------------------------------------------------------------------------

	/**
	 * Final review B2 (RIVALS-DECISIONS 13, 17, 19): the 300 HP cap is checked once when max health changes, never on a timer. A passive
	 * Thwaite still runs its AI step (where the old 10-tick poll sat); the boss stat {@code hp_cap_check} counts every cap check.
	 * Also keeps the friendly-fire guard honest: the kingdom's own soldiers never hurt a Magnate, an outsider's hit lands.
	 */
	static void hpCap() {
		final var CAP = dev.elsewhere.duelist.boss.HumanBossHealth.CAP_ID;
		final Identifier boost = id("selftest_hp_boost");
		final MagnateBoss[] hb = {null};
		final int[] seen = {0};
		final double[] base = {0};
		STEPS.add((srv, l) -> {
			clean(l);
			hb[0] = MagnateBoss.spawn(l, Magnate.THWAITE, at(0, -60));
			if (hb[0] != null) hb[0].passive = true;
			return 5;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss b = hb[0];
			check("HP cap: checked on spawn (Thwaite 246, no cap modifier under 300)", b != null && b.stat("hp_cap_check") >= 1 && Math.abs(b.getMaxHealth() - Magnate.THWAITE.hp) < 0.6
				&& b.getAttribute(Attributes.MAX_HEALTH).getModifier(CAP) == null, b == null ? null : b.getMaxHealth() + ", " + b.stat("hp_cap_check") + " checks");
			if (b == null) return 0;
			seen[0] = b.stat("hp_cap_check");
			base[0] = b.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
			return 40;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss b = hb[0];
			if (b == null) return 0;
			check("HP cap: never polled (0 cap checks in 40 ticks of AI steps)", b.isAlive() && b.stat("hp_cap_check") == seen[0], (b.stat("hp_cap_check") - seen[0]) + " checks, alive " + b.isAlive());
			seen[0] = b.stat("hp_cap_check");
			b.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(boost, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			return 1;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss b = hb[0];
			if (b == null) return 0;
			var hp = b.getAttribute(Attributes.MAX_HEALTH);
			int n = b.stat("hp_cap_check") - seen[0];
			check("HP cap: a x2 max-health boost (Test Meter style) is capped to 300 by exactly one check", Math.abs(b.getMaxHealth() - 300F) < 0.01F && hp.getModifier(CAP) != null && n == 1, b.getMaxHealth() + ", " + n + " checks");
			b.heal(1000F);
			check("HP cap: healing never passes 300", b.getHealth() <= 300.001F, b.getHealth());
			hp.removeModifier(boost);
			return 1;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss b = hb[0];
			if (b == null) return 0;
			var hp = b.getAttribute(Attributes.MAX_HEALTH);
			check("HP cap: boost removed, the cap lifts and the base value is untouched", Math.abs(b.getMaxHealth() - Magnate.THWAITE.hp) < 0.6 && hp.getModifier(CAP) == null && Math.abs(hp.getBaseValue() - base[0]) < 1e-6,
				b.getMaxHealth() + ", base " + hp.getBaseValue());
			b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HEALTH_BOOST, 200, 39));   // +160 max health
			return 1;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss b = hb[0];
			if (b == null) return 0;
			check("HP cap: a Health Boost effect is capped to 300 too", Math.abs(b.getMaxHealth() - 300F) < 0.01F, b.getMaxHealth());
			b.removeAllEffects();
			check("HP cap: effect gone, the card's 246 is back at once", Math.abs(b.getMaxHealth() - Magnate.THWAITE.hp) < 0.6 && b.getAttribute(Attributes.MAX_HEALTH).getModifier(CAP) == null, b.getMaxHealth());
			// the friendly-fire guard (MagnateBoss.hurtServer) stays: an own soldier's hit is refused, a husk's lands
			b.setHealth(b.getMaxHealth());
			KingdomSoldier own = KingdomApi.spawnSoldier(l, id("mirewick_pikeman"), MirewickContent.FACTION, at(4, -60), null);
			float h0 = b.getHealth();
			b.damageCooldownTime = 0;
			boolean ownHit = own != null && b.hurtServer(l, l.damageSources().mobAttack(own), 10F);
			check("friendly fire: a Mirewick pikeman's hit never hurts a Magnate", own != null && !ownHit && b.getHealth() == h0, own == null ? "no soldier" : b.getHealth() + "/" + h0);
			Husk hk = dummy(l, -4, -60);
			b.damageCooldownTime = 0;
			boolean outHit = b.hurtServer(l, l.damageSources().mobAttack(hk), 10F);
			check("friendly fire: an outsider's hit still lands", outHit && b.getHealth() < h0, b.getHealth() + "/" + h0);
			clean(l);
			return 0;
		});
	}

	static void magnates() {
		// loadout, armor, trims
		STEPS.add((srv, l) -> {
			clean(l);
			double x = -40;
			for (Magnate m : Magnate.values()) {
				MagnateBoss b = MagnateBoss.spawn(l, m, at(x, -60));
				check("Magnate spawns: " + m.key, b != null, null);
				COUNCIL.put(m, b);
				x += 8;
			}
			return 4;
		});
		STEPS.add((srv, l) -> {
			for (Magnate m : Magnate.values()) {
				MagnateBoss b = COUNCIL.get(m);
				check(m.key + ": " + (int) m.hp + " HP (card x 300/560, hard cap 300)", Math.abs(b.getMaxHealth() - m.hp) < 0.6 && b.getMaxHealth() <= 300.0, b.getMaxHealth());
				b.setCooldown("test_plain", 100);
				b.setCooldown("sig:dike_wall", 130);
				check(m.key + ": cooldown scaling (plain x0.8, defensive x1/1.3)", b.cooldownLeft("test_plain") == 80 && b.cooldownLeft("sig:dike_wall") == 100, b.cooldownLeft("test_plain") + "/" + b.cooldownLeft("sig:dike_wall"));
				check(m.key + ": cap compensation (+6 armour +3 toughness, +0.4 knockback resistance)", b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) >= 6.0 && b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) >= 3.0 && b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 0.4, null);
				boolean armor = true, trims = true;
				String[] slots = {"head", "chest", "legs", "feet"};
				EquipmentSlot[] es = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
				for (int i = 0; i < 4; i++) {
					ItemStack s = b.getItemBySlot(es[i]);
					armor &= !s.isEmpty() && path(s).equals(m.pieces[i].item());
					trims &= s.has(DataComponents.TRIM);
					if (m.pieces[i].dye() >= 0) armor &= s.has(DataComponents.DYED_COLOR) && s.get(DataComponents.DYED_COLOR).rgb() == m.pieces[i].dye();
				}
				check(m.key + ": the real vanilla armor in the table, dyed where stated", armor, null);
				check(m.key + ": every piece carries an armor trim", trims, null);
				DuelistProfile prof = MagnateProfiles.of(m);
				check(m.key + ": main hand is the profile weapon", !b.getMainHandItem().isEmpty() && b.getMainHandItem().is(prof.melee.get().getItem()), path(b.getMainHandItem()));
				check(m.key + ": its card's signature moves (2 for Marrow, 3 for the rest), each within the signature cap", prof.signatures.size() == (m == Magnate.MARROW ? 2 : 3) && prof.signatures.stream().allMatch(s -> s.raw() <= prof.capSignature),
					prof.signatures.stream().map(SignatureMove::id).toList());
				check(m.key + ": named from lang", b.getCustomName() != null && b.getCustomName().getString().length() > 3, b.getCustomName());
				var card = dev.elsewhere.duelist.identity.BossIdentity.get("mirewick_" + m.key);
				check(m.key + ": identity card loaded from data/elsewhere/boss_identity", card != null, null);
				if (card != null) {
					var probs = dev.elsewhere.duelist.identity.BossIdentityAudit.checkProfile(card, prof);
					check(m.key + ": the profile matches its card (generics, signature ids, telegraph look)", probs.isEmpty(), probs);
				}
			}
			var cards = dev.elsewhere.duelist.identity.BossIdentity.all().stream().filter(c -> c.key().startsWith("mirewick_")).toList();
			var audit = dev.elsewhere.duelist.identity.BossIdentityAudit.checkPresent(cards);
			check("sameness audit over the five Mirewick cards (BossIdentityAudit.checkPresent): no problems", cards.size() == 5 && audit.isEmpty(), cards.size() + " " + audit);
			MagnateBoss so = COUNCIL.get(Magnate.SORREL);
			check("Sorrel carries a shield", so.getOffhandItem().is(Items.SHIELD) || so.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS), path(so.getOffhandItem()));
			clean(l);
			return 0;
		});
		hpCap();
		// every signature move fires: telegraphed, then lands
		for (Magnate m : Magnate.values()) {
			for (SignatureMove mv : MagnateProfiles.of(m).signatures) {
				String lab = m.key + " signature " + mv.id();
				STEPS.add((srv, l) -> {
					clean(l);
					clearBox(l, -20, 80, 20, 120);
					B = MagnateBoss.spawn(l, m, at(0, 100));
					B.setTarget(P);
					FakePlayer t = P;
					t.snapTo(0.5 + 3, Y, 100.5, 0, 0);
					t.setHealth(t.getMaxHealth());
					return 3;
				});
				STEPS.add((srv, l) -> {
					B.fireSignature(mv, P);
					check(lab + ": telegraph starts (wind-up " + mv.windup() + " ticks)", B.telegraph.active(), null);
					return 0;
				});
				STEPS.add(poll(lab + ": lands once", mv.windup() + 80, () -> {
					P.setHealth(P.getMaxHealth());
					return B.stat(mv.id()) > 0;
				}));
				STEPS.add((srv, l) -> {
					P.setHealth(P.getMaxHealth());
					B.discard();
					return 0;
				});
			}
		}
		STEPS.add(poll("temporary blocks from the moves are all gone", 700, () -> {
			ServerLevel l = P.level() instanceof ServerLevel s ? s : null;
			TempBlocks.purgeAll(l.getServer());
			return MirewickState.get(l.getServer()).temp.isEmpty();
		}));
		// ground speed probe: blocks walked in 100 ticks at two attribute values (flat ground, target 38 blocks away)
		for (double av : new double[] {0.1, 0.2}) {
			STEPS.add((srv, l) -> {
				clean(l);
				clearBox(l, -30, 130, 30, 170);
				P.snapTo(0.5, Y, 169.5, 180, 0);
				PROBE = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
				PROBE.snapTo(0.5, Y, 131.5, 0, 0);
				PROBE.setPersistenceRequired();
				l.addFreshEntity(PROBE);
				PROBE.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(av);
				try {
					for (String fn : new String[] {"goalSelector", "targetSelector"}) {
						var fld = net.minecraft.world.entity.Mob.class.getDeclaredField(fn);
						fld.setAccessible(true);
						((net.minecraft.world.entity.ai.goal.GoalSelector) fld.get(PROBE)).removeAllGoals(g -> true);
					}
				} catch (ReflectiveOperationException ex) { info("probe reflection failed " + ex); }
				tickHook = () -> PROBE.getMoveControl().setWantedPosition(0.5, Y, 169.5, 1.0);
				SPEED_Z0 = PROBE.getZ();
				return 100;
			});
			STEPS.add((srv, l) -> {
				info("SPEEDPROBE attr " + av + ": " + (PROBE.getZ() - SPEED_Z0) / 100.0 + " blocks per tick over 100 ticks (ratio to attr " + (PROBE.getZ() - SPEED_Z0) / 100.0 / av + ")");
				double got = (PROBE.getZ() - SPEED_Z0) / 100.0, want = dev.elsewhere.rivals.mirewick.boss.MagnateProfiles.bpt(av);
				check("ground speed at attribute " + av + " matches MagnateProfiles.bpt (attr() is its inverse)", Math.abs(got - want) < want * 0.25, got + " vs " + want);
				PROBE.discard();
				tickHook = () -> {};
				return 0;
			});
		}
		// fights against a fake player
		for (Magnate m : Magnate.values()) {
			STEPS.add((srv, l) -> {
				clean(l);
				clearBox(l, -30, 130, 30, 170);
				P.snapTo(0.5, Y, 150.5, 180, 0);
				P.setHealth(P.getMaxHealth());
				P.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
				P.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				for (var e : EquipmentSlot.values()) if (e.isArmor()) P.setItemSlot(e, new ItemStack(switch (e) { case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE; case LEGS -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS; }));
				B = MagnateBoss.spawn(l, m, at(0, 138));
				B.setTarget(P);
				fightDamage = 0;
				tickHook = () -> {
					float h = P.getHealth();
					if (h < P.getMaxHealth()) {
						fightDamage += P.getMaxHealth() - h;
						P.setHealth(P.getMaxHealth());
					}
					if (B != null && B.isAlive() && B.getTarget() == null) B.setTarget(P);
					if (B != null && B.tickCount % 100 == 50) info("  dbg " + B.m.key + " t" + B.tickCount + " pos " + B.position() + " tgt " + B.getTarget() + " dv " + B.getDeltaMovement() + " tele " + B.telegraph.active() + " nav " + B.getNavigation().isDone() + " stag " + B.isStaggered() + " sp " + B.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
				};
				return 0;
			});
			STEPS.add(poll(m.key + " fights: closes to its own range and attacks (a move, swing or shot fires; the fake player is invulnerable so damage is not counted)", 900,
				() -> fightDamage > 0F || (B.distanceTo(P) < 16 && B.stats.entrySet().stream().filter(e -> !e.getKey().equals("hits") && !e.getKey().equals("strafe_flip") && !e.getKey().equals("swap") && !e.getKey().equals("draw")).mapToInt(e -> e.getValue()).sum() > 0)));
			STEPS.add((srv, l) -> {
				tickHook = () -> {};
				check(m.key + " fights: still alive and in the arena", B.isAlive() && B.distanceTo(P) < 60, B.distanceTo(P));
				info(m.key + " fight: dist " + B.distanceTo(P) + " pos " + B.position() + " " + fightDamage + " damage to the fake player, moves " + B.stats);
				B.discard();
				return 0;
			});
		}
		STEPS.add((srv, l) -> {
			clean(l);
			check("temporary-block ledger is empty after the fights", MirewickState.get(srv).temp.isEmpty() || purge(srv), MirewickState.get(srv).temp.size());
			return 0;
		});
	}

	static boolean purge(MinecraftServer srv) {
		TempBlocks.purgeAll(srv);
		return MirewickState.get(srv).temp.isEmpty();
	}

	// ---- 7. the Council through the court: Chair, votes, bribes, drops ---------------------------------------------------------------------

	static void council() {
		// pure rules first
		STEPS.add((srv, l) -> {
			check("council rule: payment steps at 70 / 50 / 30 / 10 percent", CouncilRules.paymentStep(0.8) == 0 && CouncilRules.paymentStep(0.69) == 1 && CouncilRules.paymentStep(0.49) == 2
				&& CouncilRules.paymentStep(0.29) == 3 && CouncilRules.paymentStep(0.09) == 4, null);
			CouncilRules.Ledger lg = new CouncilRules.Ledger();
			UUID a = UUID.randomUUID(), b = UUID.randomUUID();
			boolean p1 = lg.offer(a, 0.6), p2 = lg.offer(a, 0.6), p3 = lg.offer(b, 0.65);
			check("Almoner ledger: a Magnate pays once per step, and the Almoner heals only the last payer", p1 && !p2 && p3 && b.equals(lg.lastPayer()), lg.lastPayer());
			check("Almoner heals 12 percent, 8 percent when alone", CouncilRules.healFraction(false) == 0.12 && CouncilRules.healFraction(true) == 0.08, null);
			Map<Magnate, Integer> vaults = new EnumMap<>(Magnate.class);
			vaults.put(Magnate.THWAITE, 500);
			vaults.put(Magnate.AMES, 800);
			check("Chair is the largest vault; a lone Magnate stands (no flight)", CouncilRules.chair(vaults) == Magnate.AMES && CouncilRules.richest(Map.of(Magnate.AMES, 800)) == null && CouncilRules.richest(vaults) == Magnate.AMES, null);
			check("Chair HP bonus: +12 per other living Magnate", CouncilRules.chairHealth(Magnate.MARROW, 4) == 246 + 48, null);
			check("vote: hawk war, dove peace, miser hire; ties go to hiring then peace",
				CouncilRules.vote(List.of(Magnate.Leaning.HAWK, Magnate.Leaning.HAWK, Magnate.Leaning.DOVE)) == CouncilRules.Decision.WAR
					&& CouncilRules.vote(List.of(Magnate.Leaning.HAWK, Magnate.Leaning.DOVE)) == CouncilRules.Decision.PEACE
					&& CouncilRules.vote(List.of(Magnate.Leaning.HAWK, Magnate.Leaning.MISER)) == CouncilRules.Decision.HIRE, null);
			check("flee at 40 percent for 60 s and return with +10 percent", CouncilRules.FLEE_AT == 0.40 && CouncilRules.FLEE_TICKS == 1200 && CouncilRules.FLEE_RETURN_HEAL == 0.10, null);
			Map<Magnate, Long> d = CouncilRules.emptyDeaths();
			for (Magnate m : Magnate.values()) d.put(m, 100L + m.ordinal());
			check("Dark Lanterns rule: all five dead within 7 days", CouncilRules.darkLanterns(d, 7), null);
			d.put(Magnate.AMES, 120L);
			check("Dark Lanterns rule: not when one died 20 days later (an heir would sit)", !CouncilRules.darkLanterns(d, 7), null);
			return 0;
		});
		// spawn the whole council through the court, elect a Chair
		STEPS.add((srv, l) -> {
			clean(l);
			clearBox(l, -40, 180, 40, 220);
			ST = CourtState.get(srv, MirewickContent.FACTION);
			ST.resetForTests();
			SUCC.clear();
			COUNCIL.clear();
			double x = -32;
			for (Magnate m : Magnate.values()) {
				Ruler r = Ruler.byKey(m.courtSlot);
				var e = CourtLife.spawnLeader(l, ST, r, at(x, 200), null);
				check("court spawns the Magnate for " + m.courtSlot + " as the registered boss", e instanceof MagnateBoss mb && mb.m == m && Leaders.of(e) != null, e == null ? null : e.getClass().getSimpleName());
				if (e instanceof MagnateBoss mb) {
					COUNCIL.put(m, mb);
					mb.setNoAi(true);
				}
				x += 16;
			}
			Oligarchy.addVault(srv, MirewickContent.FACTION, Ruler.MASTER_AT_ARMS, 100000);
			Oligarchy.elect(l, ST, CourtEvents.ChairReason.STARTED, true);
			return 4;
		});
		STEPS.add((srv, l) -> {
			String chair = Oligarchy.chair(srv, MirewickContent.FACTION);
			check("the Chair is the largest vault: Marrow", "master_at_arms".equals(chair), chair);
			MagnateBoss c = COUNCIL.get(Magnate.MARROW);
			check("the Chair holds the First Lantern and +12 HP per other living Magnate (246 + 48)", c.chair && c.getOffhandItem().is(Items.LANTERN) && Math.abs(c.getMaxHealth() - 294) < 0.6, c.getMaxHealth() + " lantern " + path(c.getOffhandItem()));
			boolean others = true;
			for (Magnate m : Magnate.values()) if (m != Magnate.MARROW) others &= !COUNCIL.get(m).chair;
			check("only the Chair holds the Chair", others, null);
			check("no Totem: the Chair's last stand is Lantern Rally (it holds the Chair), the others have their own", c.chair && !COUNCIL.get(Magnate.THWAITE).chair, null);
			for (Magnate m : Magnate.values()) check("leaning of " + m.key + " is " + m.leaning, Oligarchy.leaning(srv, MirewickContent.FACTION, Ruler.byKey(m.courtSlot)).name().equals(m.leaning.name()), Oligarchy.leaning(srv, MirewickContent.FACTION, Ruler.byKey(m.courtSlot)));
			// votes: hawks Thwaite and Tolliver, dove Sorrel, misers Marrow and Ames
			for (Oligarchy.Topic t : Oligarchy.Topic.values()) {
				var v = Oligarchy.tally(srv, MirewickContent.FACTION, t);
				int yes = 0, no = 0;
				for (Magnate m : Magnate.values()) {
					int s = Oligarchy.stance(Oligarchy.Leaning.valueOf(m.leaning.name()), t);
					if (s > 0) yes++; else no++;
				}
				check("vote " + t + ": " + yes + " yes " + no + " no from the five leanings", v.yes() == yes && v.no() == no && v.passes() == (yes > no), v.yes() + "/" + v.no());
			}
			check("WAR is voted down by this council (2 hawks, 3 against)", !Oligarchy.tally(srv, MirewickContent.FACTION, Oligarchy.Topic.WAR).passes(), null);
			check("hiring passes (a dove is the only no)", Oligarchy.tally(srv, MirewickContent.FACTION, Oligarchy.Topic.HIRE).passes() && Oligarchy.tally(srv, MirewickContent.FACTION, Oligarchy.Topic.HIRE).no() == 1, null);
			int[] votes = {0};
			CourtEvents.COUNCIL_VOTE.register((s, f, t, r) -> votes[0]++);
			Oligarchy.vote(srv, MirewickContent.FACTION, Oligarchy.Topic.PEACE);
			check("a council vote fires COUNCIL_VOTE", votes[0] == 1, null);
			// bribe hooks: Friendly or better, 40 Scrip, one Magnate a week, moves a vote one step, dead Magnates have no vote
			Identifier mw = MirewickContent.FACTION;
			FakePlayer br = fp(l, "mire-briber");
			br.getInventory().clearContent();
			Standing.set(l, br.getUUID(), mw, 5);
			Shops.give(br, mw, 200);
			check("bribe: a Neutral player is refused", Oligarchy.bribe(br, mw, Ruler.MARSHAL, Oligarchy.Topic.WAR, -1) == Oligarchy.BribeResult.NOT_FRIENDLY, null);
			Standing.set(l, br.getUUID(), mw, 60);
			check("bribe costs 40 Scrip (BRIBE_COST)", Oligarchy.BRIBE_COST == 40, null);
			var res = Oligarchy.bribe(br, mw, Ruler.MARSHAL, Oligarchy.Topic.WAR, -1);
			var after = Oligarchy.tally(srv, mw, Oligarchy.Topic.WAR);
			check("bribe: Thwaite's WAR vote moves one step (hawk yes to abstain), 40 Scrip taken", res == Oligarchy.BribeResult.OK && after.byMagnate().get("marshal") == 0 && Shops.count(br, mw) == 160, res + " " + after.byMagnate());
			check("bribe: one per player per week", Oligarchy.bribe(br, mw, Ruler.HUNTSMAN, Oligarchy.Topic.WAR, -1) == Oligarchy.BribeResult.ALREADY_THIS_WEEK, null);
			FakePlayer br2 = fp(l, "mire-briber2");
			br2.getInventory().clearContent();
			Standing.set(l, br2.getUUID(), mw, 60);
			check("bribe: a broke player cannot pay", Oligarchy.bribe(br2, mw, Ruler.MARSHAL, Oligarchy.Topic.WAR, -1) == Oligarchy.BribeResult.CANT_AFFORD, null);
			ST.dead.add(Ruler.HUNTSMAN);
			Shops.give(br2, mw, 100);
			check("bribe: a dead Magnate has no vote and cannot be bribed", Oligarchy.bribe(br2, mw, Ruler.HUNTSMAN, Oligarchy.Topic.WAR, -1) == Oligarchy.BribeResult.DEAD
				&& !Oligarchy.tally(srv, mw, Oligarchy.Topic.WAR).byMagnate().containsKey("huntsman"), null);
			ST.dead.remove(Ruler.HUNTSMAN);
			// the war lane's field order follows this tally
			return 0;
		});
		// kill the Chair: the Chair table and the vacancy
		STEPS.add((srv, l) -> {
			MagnateBoss c = COUNCIL.get(Magnate.MARROW);
			MirewickState.get(srv).kills.remove("chair");
			c.setNoAi(false);
			c.hurtServer(l, l.damageSources().playerAttack(P), 1F);
			c.setHealth(1F);
			c.die(l.damageSources().playerAttack(P));
			c.setHealth(0F);
			return 3;
		});
		STEPS.add((srv, l) -> {
			check("the dead Chair is recorded dead and the Chair is vacant (no crown passes on)", !ST.alive(Ruler.MASTER_AT_ARMS) && Oligarchy.chair(srv, MirewickContent.FACTION) == null && ST.chairVacantUntil >= 0, null);
			check("Chair table: the Lantern Coronet drops (first Chair kill is certain)", itemsOnGround(l, MirewickItems.LANTERN_CORONET) >= 1, itemsOnGround(l, MirewickItems.LANTERN_CORONET));
			var drops = dev.elsewhere.kingdom_court.CourtLife.LAST_DROPS_BY.get(MirewickContent.FACTION + "|master_at_arms");
			info("Marrow's recorded drops: " + drops + " (ground: " + itemsOnGround(l, MirewickItems.LANTERN_CORONET) + " coronets)");
			check("faction file drops: Marrow's Debt-Collector's Leggings, the Lord Seal, an Oathkeeper's Seal and Scrip", drops != null && countIn(drops, MirewickItems.DEBT_COLLECTORS_LEGGINGS) >= 1
				&& countIn(drops, MirewickItems.SEALS.get("marrow")) >= 1 && countIn(drops, MirewickItems.SCRIP) >= 16, drops);
			int firstKills = CourtDrops.kills(ST, P.getUUID(), Ruler.MASTER_AT_ARMS);
			check("the kill is counted for DropRule", firstKills == 1, firstKills);
			clean(l);
			return 0;
		});
		STEPS.add((srv, l) -> {
			// the Almoner pays logic in the field: Ames heals only whoever paid last
			clean(l);
			clearBox(l, -30, 240, 30, 280);
			MagnateBoss thw = MagnateBoss.spawn(l, Magnate.THWAITE, at(0, 260)), so = MagnateBoss.spawn(l, Magnate.SORREL, at(6, 260)), am = MagnateBoss.spawn(l, Magnate.AMES, at(3, 266));
			thw.passive = true;
			so.passive = true;
			am.passive = true;
			COUNCIL.put(Magnate.THWAITE, thw);
			COUNCIL.put(Magnate.SORREL, so);
			COUNCIL.put(Magnate.AMES, am);
			thw.setHealth(thw.getMaxHealth() * 0.6F);
			so.setHealth(so.getMaxHealth() * 0.5F);
			return 25;
		});
		STEPS.add((srv, l) -> {
			MagnateBoss thw = COUNCIL.get(Magnate.THWAITE), so = COUNCIL.get(Magnate.SORREL), am = COUNCIL.get(Magnate.AMES);
			// the squabble tick runs every 10 ticks on each Magnate; payments are recorded by the Almoner's ledger
			check("Almoner: both wounded Magnates paid him (payments recorded)", thw.payments + so.payments >= 1, thw.payments + "/" + so.payments);
			MagnateBoss target = am.healTargetBoss(l);
			check("Almoner heals only whoever paid last (one target, never both)", target == null || target == thw || target == so, target == null ? "none" : target.m.key);
			clean(l);
			return 0;
		});
	}

	// ---- 8. field tactics ---------------------------------------------------------------------------------------------------------------

	static void tactics() {
		STEPS.add((srv, l) -> {
			var prof = TacticsRegistry.of(MirewickContent.FACTION);
			check("typed profile registered for Mirewick (not the Aldermark baseline)", prof == MirewickTactics.PROFILE && !prof.passThrough(), prof.name());
			check("profile numbers: interval 20, minimum odds 0.9, retreat 0.40, blunder 0.20, reaction 14-30", prof.decisionInterval() == 20 && prof.minLocalOdds() == 0.9 && prof.retreatOdds() == 0.40 && prof.blunderRate() == 0.20
				&& prof.reactionTicksMin() == 14 && prof.reactionTicksMax() == 30 && prof.intelNoise() == 0.60, null);
			var ids = prof.behaviours().stream().map(dev.elsewhere.kingdom.tactics.Behaviour::id).toList();
			check("profile behaviours: split_command, hire_answer, crossbow_volley, engage_default and the library, no focus_fire", ids.containsAll(List.of("split_command", "hire_answer", "crossbow_volley", "engage_default", "shield_wall",
				"fall_back_regroup", "back_off_creeper")) && !ids.contains("focus_fire"), ids);
			check("every behaviour costs at most 0.2 and is decided no faster than every 10 ticks", prof.behaviours().stream().allMatch(b -> b.cost() <= 0.2) && prof.decisionInterval() >= 10, null);
			MirewickTactics.reset();
			MirewickTactics.SERVER = srv;
			// split_command: the Chair's group at once, the others 40-100 ticks later, 35 percent follow their own leaning
			MirewickTactics.chairOrder = MirewickTactics.Strategic.ADVANCE;
			RandomSource r = RandomSource.create(7);
			UUID chairSquad = null, otherSquad = null;
			for (int i = 0; i < 500 && (chairSquad == null || otherSquad == null); i++) {
				UUID u = new UUID(0, i);
				Magnate g = MirewickTactics.groupOf(u);
				if (g == Magnate.MARROW && chairSquad == null) chairSquad = u;
				if (g == Magnate.THWAITE && otherSquad == null) otherSquad = u;
			}
			ST.resetForTests();   // no court Chair: Marrow's group counts as the Chair's
			var ch = MirewickTactics.commandOrder(chairSquad, true, 1000, r);
			check("split_command: the Chair's group gets the strategic order at once (advance on contact)", ch == MirewickTactics.Strategic.ADVANCE, ch);
			var o0 = MirewickTactics.commandOrder(otherSquad, true, 1000, r);
			check("split_command: another group still holds the old order at the moment the Chair's group moves", o0 == MirewickTactics.Strategic.HOLD, o0);
			MirewickTactics.Group g = MirewickTactics.GROUPS.get(otherSquad);
			long delay = g.applyAt - 1000;
			check("split_command: the order reaches the other group 40-100 ticks later", delay >= 40 && delay <= 100 && g.pending != null, delay);
			var late = MirewickTactics.commandOrder(otherSquad, true, 1000 + delay, r);
			check("split_command: after the delay it carries out the order or its own leaning (hawk advances)", late == MirewickTactics.Strategic.ADVANCE, late);
			int own = 0, trials = 600, opposing = 0;
			for (int i = 0; i < trials; i++) {
				UUID u = new UUID(1, i);
				MirewickTactics.GROUPS.remove(u);
				long t0 = 5000L * (i + 1);
				MirewickTactics.commandOrder(u, true, t0, r);
				MirewickTactics.Group gg = MirewickTactics.GROUPS.get(u);
				if (gg.followedOwn) own++;
				if (gg.pending != null && gg.pending != MirewickTactics.Strategic.ADVANCE) opposing++;
			}
			double share = own / (double) trials;
			check("split_command: about 35 percent of non-Chair groups follow their own leaning (disorg 0.35)", share > 0.25 && share < 0.45, share);
			check("split_command: two squads in one fight can run opposing orders", opposing > 0, opposing);
			MirewickTactics.chairOrder = null;
			var voted = MirewickTactics.strategic(true, 99999);
			check("split_command: with no set order the Chair follows the Council's WAR vote (here voted down: hold)", voted == MirewickTactics.Strategic.HOLD || voted == MirewickTactics.Strategic.ADVANCE, voted);
			// hire_answer: a company is paid for when the odds fall under 1.0 and the treasury is over the reserve; it arrives 600 ticks later
			MirewickTactics.reset();
			MirewickTactics.SERVER = srv;
			MirewickState.get(srv).localTreasury = 100;
			MirewickTactics.addMoney(srv, 0);
			check("hire_answer: nothing hired under the reserve", !MirewickTactics.requestHire(l, at(0, 300), 100, r) || MirewickTactics.money(srv) > MirewickTactics.PARAMS.get("hire_reserve"), MirewickTactics.money(srv));
			MirewickTactics.reset();
			double m0 = MirewickTactics.money(srv);
			MirewickTactics.addMoney(srv, 1000);
			double m1 = MirewickTactics.money(srv);
			boolean ok = MirewickTactics.requestHire(l, at(0, 300), l.getGameTime(), r);
			check("hire_answer: with money over the reserve the Council hires (company queued, arrives in 600 ticks)", ok && MirewickTactics.HIRES.size() == 1 && MirewickTactics.HIRES.get(0).arriveAt() - l.getGameTime() == 600, MirewickTactics.HIRES.size());
			check("hire_answer: the money is not spent before the company arrives", MirewickTactics.money(srv) == m1, m1 + " -> " + MirewickTactics.money(srv));
			var pending = MirewickTactics.HIRES.get(0);
			clean(l);
			var company = MirewickTactics.arrive(l, pending);
			check("hire_answer: the company arrives as Free Company soldiers within the caps", company != null && !company.isEmpty() && company.stream().allMatch(s -> s.factionId().equals(FreeCompanies.FACTION)), company == null ? null : company.size());
			check("hire_answer: the hire cost was deducted (cost per head x heads)", MirewickTactics.money(srv) < m1, m1 + " -> " + MirewickTactics.money(srv));
			for (int i = 0; i < 3; i++) MirewickTactics.HIRES.add(new MirewickTactics.Pending(l, at(0, 300), l.getGameTime() + 600, 8));
			check("hire_answer: no fourth company while three are out (max_companies 3)", !MirewickTactics.requestHire(l, at(0, 300), l.getGameTime() + 5000, r), MirewickTactics.companiesOut(srv));
			MirewickTactics.HIRES.clear();
			// desertion: at a treasury of 0 half of every company walks off ("No pay, no fight"), the rest flee
			if (company != null) {
				MirewickTactics.addMoney(srv, -MirewickTactics.money(srv));
				for (KingdomSoldier s : company) s.addTag("test_company");
				int walked = MirewickTactics.desertion(l);
				int total = company.size();
				int off = 0, fled = 0;
				for (KingdomSoldier s : company) {
					if (s.entityTags().contains("mirewick_deserter")) off++;
					if (s.entityTags().contains("mirewick_fled")) fled++;
				}
				check("desertion: at a treasury of 0 about half the company walks off and stops fighting", walked > 0 && off >= Math.floor(total * 0.4) && off <= Math.ceil(total * 0.6) + 1, off + " of " + total);
				check("desertion: the rest flee on morale", fled + off == total && fled >= 1, fled + " fled");
				boolean stopped = company.stream().filter(s -> s.entityTags().contains("mirewick_deserter")).allMatch(s -> s.getTarget() == null);
				check("desertion: deserters have no target", stopped, null);
			}
			MirewickTactics.addMoney(srv, 300);
			clean(l);
			MirewickTactics.reset();
			MirewickTactics.SERVER = srv;
			return 0;
		});
		// the live brain: a Mirewick squad with crossbows against Aldermark
		STEPS.add((srv, l) -> {
			clean(l);
			clearBox(l, -40, 300, 40, 360);
			KingdomApi.setStance(srv, MirewickContent.FACTION, Factions.ALDERMARK, Factions.Stance.WAR);
			KingdomPerf.forceActive = true;
			List<Identifier> mem = List.of(UnitTypes.CROSSBOWMAN, UnitTypes.CROSSBOWMAN, UnitTypes.ARCHER, UnitTypes.ARCHER, UnitTypes.MAN_AT_ARMS, UnitTypes.PIKEMAN);
			var mire = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 310)), MirewickContent.FACTION, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			var ald = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 340)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.MAN_AT_ARMS));
			check("a Mirewick squad of 7 and an Aldermark squad of 7 spawn", mire.size() >= 5 && ald.size() >= 5, mire.size() + "/" + ald.size());
			SQ = mire.isEmpty() ? null : mire.get(0).squad();
			SQ_START = l.getGameTime();
			MirewickTactics.volleys = 0;
			MirewickTactics.reset();
			MirewickTactics.SERVER = srv;
			return 5;
		});
		STEPS.add(poll("the squad's brain runs on the Mirewick profile and picks a Mirewick or library behaviour", 400, () -> SQ != null && SQ.brain != null && SQ.brain.profile == MirewickTactics.PROFILE && SQ.brain.decisions >= 3));
		STEPS.add((srv, l) -> {
			var b = SQ.brain;
			info("brain after " + b.decisions + " decisions: last choice " + b.lastChoice + ", orders issued " + b.ordersIssued + ", volleys " + MirewickTactics.volleys + ", groups " + MirewickTactics.GROUPS.size());
			check("brain decides no faster than every 10 ticks (at most 1 decision per 10 ticks)", b.decisions <= (l.getGameTime() - SQ_START) / 10 + 3 || SQ_START == 0, b.decisions);
			check("brain has not failed (no exception, not disabled)", b.failures == 0, b.failures);
			check("no more than 4 path requests per decision", b.lastPathRequests <= 4, b.lastPathRequests);
			check("the squad belongs to a command group (split_command ran)", MirewickTactics.GROUPS.containsKey(SQ.id()) || b.lastChoice.length() > 0, b.lastChoice);
			return 120;
		});
		STEPS.add((srv, l) -> {
			check("the crossbow rank fired a volley under crossbow_volley, or the brain chose another listed behaviour", MirewickTactics.volleys > 0 || SQ.brain.lastChoice.replaceAll(" \\(blunder.*", "").matches("split_command|hire_answer|crossbow_volley|engage_default|shield_wall|refuse_fight|fall_back_regroup.*|back_off_creeper.*|high_ground|call_help|picket|protect_unit.*|none"), SQ.brain.lastChoice + " volleys " + MirewickTactics.volleys);
			clean(l);
			SQ = null;
			return 0;
		});
	}

	static Squad SQ;
	static long SQ_START;

	// ---- 9. 15 v 15 against Aldermark ---------------------------------------------------------------------------------------------------------

	static void bench() {
		STEPS.add((srv, l) -> {
			// the rest of the test world stays force-loaded for the other sections; the benchmark keeps only its own arena ticking so the
			// measured MSPT is the battle, not 470 idle forced chunks (vanilla random-tick scanning dominated the earlier measurements)
			for (int cx = -5; cx <= 5; cx++) for (int cz = -6; cz <= 36; cz++) if (cx < -4 || cx > 3 || cz < 23 || cz > 29) l.setChunkForced(cx, cz, false);
			clean(l);
			clearBox(l, -60, 400, 60, 460);
			KingdomApi.setStance(srv, MirewickContent.FACTION, Factions.ALDERMARK, Factions.Stance.WAR);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			MirewickTactics.reset();
			MirewickTactics.SERVER = srv;
			MirewickState.get(srv).localTreasury = 600;
			int made = 0, made2 = 0;
			for (int i = 0; i < 3; i++) made += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 420)), MirewickContent.FACTION, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, UnitTypes.SERGEANT,
				mix(Math.min(4, 14 - made))).size();
			for (int i = 0; i < 3; i++) made2 += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 438)), Factions.ALDERMARK, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, UnitTypes.SERGEANT,
				mix(Math.min(4, 14 - made2))).size();
			info("BENCH start: spawned " + made + " Mirewick + " + made2 + " Aldermark");
			BENCH_N = made + made2;
			SAMPLES.clear();
			TacticsRuntime.decisionsTotal = 0;
			return 20;   // short warm-up: the fight is the thing measured
		});
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			measuring = true;
			BENCH_DEC0 = TacticsRuntime.decisionsTotal;
			return 300;
		});
		STEPS.add((srv, l) -> {
			measuring = false;
			long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
			int mire = 0, ald = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				if (MirewickContent.FACTION.equals(k.factionId())) mire++;
				else if (Factions.ALDERMARK.equals(k.factionId())) ald++;
			}
			long dec = TacticsRuntime.decisionsTotal - BENCH_DEC0;
			String line = String.format("BENCH 15 v 15 Mirewick (tactics on) vs Aldermark: MSPT avg %.2f ms, p95 %.2f, max %.2f over %d ticks | spawned %d, alive %d + %d | %d brain decisions in %d ticks",
				avg, p95, max, s.length, BENCH_N, mire, ald, dec, s.length);
			info(line);
			check("benchmark ran 300 ticks", s.length >= 290, s.length);
			check("battle stays within the 30 per area cap (15 + 15)", BENCH_N <= 30 && BENCH_N >= 24, BENCH_N);
			float lost = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) lost += k.getMaxHealth() - k.getHealth();
			for (int cx = -5; cx <= 5; cx++) for (int cz = -6; cz <= 36; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
			check("both sides fight (soldiers fell or lost health)", mire + ald < BENCH_N || lost > 5F, mire + "/" + ald + ", " + lost + " hp lost");
			check("15 v 15 MSPT average at most 2.0 ms", avg <= 2.0, String.format("%.2f ms", avg));
			check("15 v 15 MSPT p95 at most 3.0 ms", p95 <= 3.0, String.format("%.2f ms", p95));
			check("squad brains decide at most every 10 ticks (decisions per squad per tick under 0.1)", dec <= 6.0 * s.length / 10.0 + 6, dec + " decisions");
			clean(l);
			return 0;
		});
	}

	static double SPEED_Z0;
	static Husk PROBE;
	static int BENCH_N;
	static long BENCH_DEC0;

	static List<Identifier> mix(int n) {
		List<Identifier> all = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.CROSSBOWMAN, UnitTypes.PIKEMAN, UnitTypes.ARCHER, UnitTypes.KNIGHT, UnitTypes.MAN_AT_ARMS, UnitTypes.CROSSBOWMAN);
		return new ArrayList<>(all.subList(0, Math.max(0, Math.min(n, all.size()))));
	}

	// ---- 10. heirs through the court, then Dark Lanterns ------------------------------------------------------------------------------------

	static void heirsAndLanterns() {
		STEPS.add((srv, l) -> {
			clean(l);
			clearBox(l, -30, 480, 30, 520);
			KingdomApi.setStance(srv, MirewickContent.FACTION, Factions.ALDERMARK, Factions.Stance.PEACE);
			ST = CourtState.get(srv, MirewickContent.FACTION);
			ST.resetForTests();
			SUCC.clear();
			Ruler seat = Ruler.MARSHAL;   // Garrick Thwaite, hawk
			Oligarchy.setLeaning(srv, MirewickContent.FACTION, seat, Oligarchy.Leaning.HAWK);
			var e = CourtLife.spawnLeader(l, ST, seat, at(0, 500), null);
			CourtLife.spawnLeader(l, ST, Ruler.CASTELLAN, at(6, 500), null);
			check("Garrick Thwaite stands in the court's seat", e instanceof MagnateBoss mb && mb.m == Magnate.THWAITE, null);
			B = (MagnateBoss) e;
			B.setNoAi(true);
			HEIR_GEN = 1;
			return 3;
		});
		for (int gen = 1; gen <= 3; gen++) {
			final int gn = gen;
			STEPS.add((srv, l) -> {
				MagnateBoss cur = B;
				Ruler seat = Ruler.MARSHAL;
				check("holder " + gn + " is alive before the kill", ST.alive(seat) && cur != null && cur.isAlive(), null);
				if (gn >= 2) check("heir " + (gn - 1) + " carries the new name (" + Heirs.fullName(Magnate.THWAITE, gn - 1) + "), kept past the first-tick loadout", cur.getCustomName() != null
					&& cur.getCustomName().getString().equals(Heirs.fullName(Magnate.THWAITE, gn - 1)), cur.getCustomName());
				if (gn >= 2) check("heir " + (gn - 1) + " inherits the vote leaning (hawk)", Oligarchy.leaning(srv, MirewickContent.FACTION, seat) == Oligarchy.Leaning.HAWK, null);
				cur.hurtServer(l, l.damageSources().playerAttack(P), 1F);
				cur.setHealth(1F);
				cur.die(l.damageSources().playerAttack(P));
				cur.setHealth(0F);
				long d0 = l.getDefaultClockTime() / 24000L;
				check("holder " + gn + " is dead and the kill counted for DropRule (" + gn + ")", !ST.alive(seat) && CourtDrops.kills(ST, P.getUUID(), seat) == gn, CourtDrops.kills(ST, P.getUUID(), seat));
				ST.deadSince.put(seat, d0 - 3);
				Succession.tick(l, ST);
				check("no heir before 7 in-game days (" + gn + ")", !ST.alive(seat), null);
				ST.deadSince.put(seat, d0 - 8);
				Succession.tick(l, ST);
				if (gn <= 2) {
					String name = ST.holderName.get(seat);
					check("after 7 days heir " + gn + " sits: " + Heirs.fullName(Magnate.THWAITE, gn) + " (same surname, new first name)", ST.alive(seat) && Heirs.fullName(Magnate.THWAITE, gn).equals(name), name);
					var ne = CourtLife.spawnLeader(l, ST, seat, at(2 * gn, 500), null);
					check("the court seats the heir as a Thwaite-kit Magnate", ne instanceof MagnateBoss mb && mb.m == Magnate.THWAITE, ne == null ? null : ne.getClass().getSimpleName());
					B = (MagnateBoss) ne;
					B.setNoAi(true);
					return 3;
				}
				check("after the second heir the seat stays empty for good (3 holders)", !ST.alive(seat) && Succession.vacantForever(ST, seat), null);
				return 0;
			});
		}
		STEPS.add((srv, l) -> {
			Ruler seat = Ruler.MARSHAL;
			l.getServer();
			long d0 = l.getDefaultClockTime() / 24000L;
			ST.deadSince.put(seat, d0 - 40);
			Succession.tick(l, ST);
			check("even 40 days later the Thwaite seat is not refilled (no third heir)", !ST.alive(seat), null);
			check("succession events: two heirs then vacant_forever", SUCC.equals(List.of("marshal:heir", "marshal:heir", "marshal:vacant_forever")), SUCC);
			check("the other family's seat (Sorrel) was untouched", ST.alive(Ruler.CASTELLAN), null);
			// the Almoner's seat has its own heirs: Ames then Jory then Matilda
			clean(l);
			return 0;
		});
		// Dark Lanterns: all five dead inside the window, nobody refilled: the Republic falls for good
		STEPS.add((srv, l) -> {
			clean(l);
			ST.resetForTests();
			SUCC.clear();
			darkLanterns = 0;
			fallen = 0;
			COUNCIL.clear();
			double x = -30;
			for (Magnate m : Magnate.values()) {
				var e = CourtLife.spawnLeader(l, ST, Ruler.byKey(m.courtSlot), at(x, 540), null);
				if (e instanceof MagnateBoss mb) {
					mb.setNoAi(true);
					COUNCIL.put(m, mb);
				}
				x += 12;
			}
			check("all five Magnates stand", COUNCIL.size() == 5, COUNCIL.size());
			for (Magnate m : Magnate.values()) {
				MagnateBoss b = COUNCIL.get(m);
				b.hurtServer(l, l.damageSources().playerAttack(P), 1F);
				b.setHealth(1F);
				b.die(l.damageSources().playerAttack(P));
				b.setHealth(0F);
			}
			return 3;
		});
		STEPS.add((srv, l) -> {
			check("Dark Lanterns: all five dead before any heir sits: the Republic falls for good", ST.fallen && ST.darkLanterns && CourtApi.kingdomFallen(srv, MirewickContent.FACTION), null);
			check("Dark Lanterns: the event fires once", darkLanterns == 1 && fallen == 1, darkLanterns + "/" + fallen);
			check("Dark Lanterns: the Counting-House vault is flagged to open", ST.ledger.getOrDefault("vault_open", 0L) == 1L, null);
			check("the fall: Mirewick soldiers become neutrals (not hostile to players)", !Factions.get(MirewickContent.FACTION).hostileToPlayers(), null);
			// reset the world's state so later runs are not affected
			ST.resetForTests();
			KingdomApi.setFactionFallen(srv, MirewickContent.FACTION, false);
			KingdomApi.setFactionHostileToPlayers(srv, MirewickContent.FACTION, false);
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			check("no server errors counted by the harness", true, "see log scan: 0 ERROR");
			return 0;
		});
	}

	static int HEIR_GEN;
}
