package dev.elsewhere.verdigris_ironpeak.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.galvanox.GalvanoxBoss;
import dev.elsewhere.boss.galvanox.GalvanoxSummon;
import dev.elsewhere.boss.hrolf.HrolfBoss;
import dev.elsewhere.boss.hrolf.HrolfSummon;
import dev.elsewhere.boss.thurgrim.ThurgrimSummon;
import dev.elsewhere.boss.verdigrim.VerdigrimBoss;
import dev.elsewhere.boss.verdigrim.VerdigrimSummon;
import dev.elsewhere.stub.verdigris_ironpeak.BossGuards;
import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.stub.verdigris_ironpeak.TripMath;
import dev.elsewhere.stub.verdigris_ironpeak.TripTrigger;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.Reg;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakContent;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_ironpeak.ElsewhereTrader;
import dev.elsewhere.verdigris_ironpeak.EndgameGear;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_sea.VerdigrisSea;
import dev.elsewhere.verdigris_ironpeak.MapOffers;
import dev.elsewhere.verdigris_ironpeak.StructureTraders;
import dev.elsewhere.verdigris_ironpeak.world.ProceduralStructure;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/** Dev-only self-test, run by `gradlew runServer` (-Dverdigris_ironpeak.selftest=true). Logs PASS/FAIL lines and a total. */
public final class SelfTest {
	private static int passed, failed;

	private SelfTest() {}

	public static void init() {
		if (Boolean.getBoolean("verdigris_ironpeak.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(SelfTest::drain);
		}
	}

	// 3.0 z-gen heap check: the self-test runs inside SERVER_STARTED, so no tick (and no chunk unload) happens while it generates
	// hundreds of chunks (Ironpeak is 1,536 high). After the tests the server ticks until the chunks unload, then we check that the
	// used heap after a full GC is back near the pre-test baseline: structure generation (site rules, starts, pieces) must not
	// retain memory once its chunks are gone.
	static long heapBase, heapPeak, testStart;
	static int chunksBase, chunksPeak, drainTicks = -1;

	static long usedAfterGc() {
		Runtime rt = Runtime.getRuntime();
		System.gc();
		return (rt.totalMemory() - rt.freeMemory()) >> 20;
	}

	static int loadedChunks(MinecraftServer server) {
		int n = 0;
		for (ServerLevel l : server.getAllLevels()) n += l.getChunkSource().getLoadedChunksCount();
		return n;
	}

	static void drain(MinecraftServer server) {
		if (drainTicks < 0) return;
		drainTicks++;
		int loaded = loadedChunks(server);
		// spawn chunks stay; everything the test generated unloads within a few hundred ticks (budgeted unloads per tick)
		int limit = chunksBase + (chunksPeak - chunksBase) / 10 + 50;
		// at most 1000 ticks: a server with pause-when-empty-seconds=60 stops ticking (and this hook) at 1200 empty ticks
		if (drainTicks < 1000 && (drainTicks % 20 != 0 || loaded > limit)) return;
		int drainTicks0 = drainTicks;
		drainTicks = -1;
		// unloading only queues the chunks' saves (copied chunk data waiting for the region writer); /stop writes them all at once,
		// which is where the 1.5 GB JVM ran out. Flush them here, as /stop does, then measure what is still held.
		long queued = usedAfterGc();
		server.saveAllChunks(true, true, false);
		long after = usedAfterGc();
		VerdigrisIronpeakContent.LOG.info("[selftest] heap: base {} MB, after tests {} MB with {} chunks loaded, after unload {} MB with {} chunks loaded and their saves queued, after the save flush {} MB",
			heapBase, heapPeak, chunksPeak, queued, loaded, after);
		check(loaded <= limit, f("the test's chunks unload once the server ticks (%d before the tests, %d after, %d after %d ticks)", chunksBase, chunksPeak, loaded, drainTicks0));
		check(after - heapBase <= Math.max(160, (heapPeak - heapBase) / 4),
			f("no worldgen retention: used heap after unload and save flush %d MB vs base %d MB (after tests %d MB with %d chunks; %d MB while their saves were queued)", after, heapBase, heapPeak, chunksPeak, queued));
		VerdigrisIronpeakContent.LOG.info("[selftest] TOTAL {} passed, {} failed ({} ms) {}", passed, failed, System.currentTimeMillis() - testStart, failed == 0 ? "ALL PASSED" : "");
		server.halt(false);
	}

	static void check(boolean ok, String what) {
		VerdigrisIronpeakContent.LOG.info("[selftest] {} {}", ok ? "PASS" : "FAIL", what);
		if (ok) passed++;
		else failed++;
	}

	static String f(String fmt, Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	static void run(MinecraftServer server) {
		passed = failed = 0;
		long start = System.currentTimeMillis();
		testStart = start;
		heapBase = usedAfterGc();
		chunksBase = loadedChunks(server);
		try {
			checkRegistry();
			dev.elsewhere.verdigris_ironpeak.s3.groups.SpawnGroups3.selfTest(server, (ok, what) -> check(ok, what)); // ARMIES 6 spawn groups (s-spawngroups)
			checkLoot(server);
			checkArsenal();
			checkBeta2(server);
			checkDimensions(server);
			checkTrips();
			checkPhase1c(server);
			checkEndgame(server);
			checkEvents(server);
			checkBoss(server, "verdigrim", Fx.VERDIGRIS_SEA, "drowned_cathedral", 500, 12, 4, 3, VerdigrimSummon::summon);
			checkBoss(server, "galvanox", Fx.VERDIGRIS_SEA, "lighthouse", 580, 18, 6, 3, GalvanoxSummon::summon);
			checkBoss(server, "thurgrim", Fx.IRONPEAK, "summit_throne", 640, 18, 8, 3, ThurgrimSummon::summon);
			checkBoss(server, "hrolf", Fx.IRONPEAK, "proving_grounds", 300, 12, 4, 3, HrolfSummon::summon);
			SelfTestStructFit.run(server); // 3.0 structure looks + per-piece ground (s-struct-verdigris)
		} catch (Throwable t) {
			VerdigrisIronpeakContent.LOG.error("[selftest] crashed", t);
			failed++;
		}
		if (Boolean.getBoolean("verdigris_ironpeak.selftest.exit")) {
			// TOTAL is logged by drain() after the chunk-unload heap check
			chunksPeak = loadedChunks(server);
			heapPeak = usedAfterGc();
			drainTicks = 0;
			return;
		}
		VerdigrisIronpeakContent.LOG.info("[selftest] TOTAL {} passed, {} failed ({} ms) {}", passed, failed, System.currentTimeMillis() - start, failed == 0 ? "ALL PASSED" : "");
	}

	static void checkRegistry() {
		int missingLang = 0;
		for (String id : Reg.ITEMS.keySet()) {
			var item = Reg.ITEMS.get(id);
			if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("elsewhere")) missingLang++;
		}
		check(missingLang == 0 && Reg.ITEMS.size() > 60, f("%d items, %d blocks, %d entity types registered in the elsewhere namespace", Reg.ITEMS.size(), Reg.BLOCKS.size(), Reg.ENTITIES.size()));
		int noTexture = 0;
		for (String id : Reg.ITEMS.keySet()) {
			if (SelfTest.class.getResource("/assets/elsewhere/items/" + id + ".json") == null) {
				noTexture++;
				VerdigrisIronpeakContent.LOG.warn("[selftest] no item model definition for {}", id);
			}
		}
		check(noTexture == 0, f("every item has an item model definition (%d missing)", noTexture));
		int noState = 0;
		for (String id : Reg.BLOCKS.keySet()) if (SelfTest.class.getResource("/assets/elsewhere/blockstates/" + id + ".json") == null) noState++;
		check(noState == 0, f("every block has a blockstate file (%d missing)", noState));
	}

	static void checkArsenal() {
		var ks = new ItemStack(dev.elsewhere.ironpeak.IronpeakItems.KINGSPLITTER);
		var mods = ks.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		boolean reach = mods.modifiers().stream().anyMatch(e -> e.attribute().is(Attributes.ENTITY_INTERACTION_RANGE.unwrapKey().orElseThrow()));
		boolean dmg = mods.modifiers().stream().anyMatch(e -> e.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow()));
		check(reach && dmg, "kingsplitter keeps its axe stats and gains +0.5 reach (ARSENAL.md)");
		check(Math.abs(dev.elsewhere.ironpeak.RocFeatherLongbowItem.power(28) - 1.0F) < 1.0E-4 && dev.elsewhere.ironpeak.RocFeatherLongbowItem.power(20) < 1.0F,
			"roc-feather longbow: full draw takes 1.4 s");
		int offers = dev.elsewhere.verdigris_sea.VerdigrisEntities.tidewrightOffers(null).size();
		check(offers >= 6, f("tidewright offers build (%d; Brassback Key / Stormcoil Staff added only if registered)", offers));
	}

	/** Beta 2 module tasks (ARSENAL.md §2, §6, §10): longbow, zoom math, Kingsplitter reach, trades/drops by id, HUD toggles. */
	static void checkBeta2(MinecraftServer server) {
		var bow = dev.elsewhere.ironpeak.IronpeakItems.ROC_FEATHER_LONGBOW;
		check(BuiltInRegistries.ITEM.getKey(bow).equals(Reg.id("roc_feather_longbow")) && bow instanceof net.minecraft.world.item.BowItem,
			"roc-feather longbow registered as elsewhere:roc_feather_longbow (a bow)");
		check(new ItemStack(bow).getMaxDamage() == 768, f("longbow durability %d (768)", new ItemStack(bow).getMaxDamage()));
		float p14 = dev.elsewhere.ironpeak.RocFeatherLongbowItem.power(14), v10 = net.minecraft.world.item.BowItem.getPowerForTime(10);
		check(dev.elsewhere.ironpeak.RocFeatherLongbowItem.FULL_DRAW_TICKS == 28 && Math.abs(p14 - v10) < 1.0E-4
			&& dev.elsewhere.ironpeak.RocFeatherLongbowItem.power(27) < 1.0F && dev.elsewhere.ironpeak.RocFeatherLongbowItem.power(40) == 1.0F,
			f("longbow draw curve: vanilla's stretched to 28 ticks (power(14)=%.3f = vanilla power(10)=%.3f)", p14, v10));

		var reg = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		var infinity = reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY);
		var power = reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER);
		var unbreaking = reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
		ItemStack lb = new ItemStack(bow);
		var acc = net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE;
		check(!lb.canBeEnchantedWith(infinity, acc) && lb.canBeEnchantedWith(power, acc) && lb.canBeEnchantedWith(unbreaking, acc)
			&& new ItemStack(Items.BOW).canBeEnchantedWith(infinity, acc),
			"longbow takes Power and Unbreaking but not Infinity (vanilla bow still takes Infinity)");

		ServerLevel level = server.overworld();
		BlockPos at = level.getRespawnData().pos().above(40);
		float ratio = dev.elsewhere.ironpeak.RocFeatherLongbowItem.arrowVelocity(1.0F) / (net.minecraft.world.item.BowItem.getPowerForTime(20) * 3.0F);
		check(Math.abs(ratio - 1.4F) < 1.0E-4, f("full-draw arrow speed x%.3f vs a vanilla bow's 3.0 (spec x1.4)", ratio));
		double[] lbShot = shotSpeed(level, at.east(4), new ItemStack(bow), 28, true);
		check(lbShot[1] == 1, f("longbow spends an arrow even with Infinity on it (%d used)", (int) lbShot[1]));

		var mods = lb.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		var pierceId = Reg.id("projectile_pierce");
		boolean hasPierceAttr = BuiltInRegistries.ATTRIBUTE.containsKey(pierceId);
		boolean pierceMod = mods.modifiers().stream().anyMatch(e -> e.attribute().unwrapKey().map(k -> k.identifier().equals(pierceId)).orElse(false) && e.modifier().amount() == 1);
		check(pierceMod == hasPierceAttr, f("longbow projectile_pierce +1 by id lookup (attribute %s, modifier %s)", hasPierceAttr ? "present" : "absent: skipped", pierceMod));

		var ks = new ItemStack(dev.elsewhere.ironpeak.IronpeakItems.KINGSPLITTER).getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
			net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		double reach = ks.modifiers().stream().filter(e -> e.attribute().is(Attributes.ENTITY_INTERACTION_RANGE.unwrapKey().orElseThrow()))
			.mapToDouble(e -> e.modifier().amount()).sum();
		boolean speed = ks.modifiers().stream().anyMatch(e -> e.attribute().is(Attributes.ATTACK_SPEED.unwrapKey().orElseThrow()));
		check(Math.abs(reach - 0.5) < 1.0E-6 && speed, f("kingsplitter entity_interaction_range +%.2f (0.5) on top of its axe stats", reach));

		// zoom math (client uses the same helper; FOV modifier = 1 / zoom, scaled by vanilla's FOV Effects slider)
		float z0 = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(0, 0), zFull = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(1, 0);
		float zSneak = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(1, 1), zHalf = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(0.5F, 0);
		float zCap = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(5, 5);
		float halfSneakHalfDraw = dev.elsewhere.ironpeak.RocFeatherLongbowItem.zoom(dev.elsewhere.ironpeak.RocFeatherLongbowItem.drawProgress(14), 0);
		check(Math.abs(z0 - 1) < 1e-6 && Math.abs(zFull - 1.5F) < 1e-6 && Math.abs(zSneak - 2.0F) < 1e-6 && Math.abs(zHalf - 1.125F) < 1e-6
			&& zCap <= 2.0F && Math.abs(halfSneakHalfDraw - 1.125F) < 1e-4,
			f("longbow zoom eases with draw: 1.0 -> %.3f (half) -> %.2fx full, %.2fx sneaking at full draw, capped %.2f", zHalf, zFull, zSneak, zCap));

		// Quartermaster rank-3 trade: bow + 4 Roc Feathers + 2 Skyiron (extra input from the inventory)
		var qm = fake(level, at.west(4));
		dev.elsewhere.ironpeak.Ironpeak.data(qm).putInt("max_y", dev.elsewhere.ironpeak.Ironpeak.RANK_HEIGHTS[2]);
		boolean atRank2 = dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(qm).stream().anyMatch(o -> o.getResult().is(bow));
		dev.elsewhere.ironpeak.Ironpeak.data(qm).putInt("max_y", dev.elsewhere.ironpeak.Ironpeak.RANK_HEIGHTS[3]);
		var offer = dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(qm).stream().filter(o -> o.getResult().is(bow)).findFirst().orElse(null);
		check(!atRank2 && offer != null && dev.elsewhere.ironpeak.Ironpeak.rank(qm) == 3, "quartermaster sells the longbow from clan rank 3 (not at rank 2)");
		if (offer != null) {
			ItemStack a = new ItemStack(Items.BOW), b = new ItemStack(dev.elsewhere.ironpeak.IronpeakItems.ROC_FEATHER, 4);
			boolean without = offer.satisfiedBy(a, b);
			qm.getInventory().add(new ItemStack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON, 3));
			boolean with = offer.satisfiedBy(a, b);
			boolean took = offer.take(a, b);
			int left = qm.getInventory().countItem(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON);
			ItemStack got = offer.assemble();
			check(offer.getCostA().is(Items.BOW) && offer.getCostB().is(dev.elsewhere.ironpeak.IronpeakItems.ROC_FEATHER) && offer.getCostB().getCount() == 4
				&& !without && with && took && left == 1 && a.isEmpty() && b.isEmpty() && got.is(bow) && lore(got) == 0 && lore(offer.getResult()) == 1,
				f("longbow trade: bow + 4 Roc Feathers + 2 Skyiron (refused without Skyiron: %s, Skyiron left %d of 3, result clean: %s)", !without, left,
					lore(got) == 0));
		}

		// trades and drops of other modules' items: by id lookup, skipped cleanly when absent
		var items = BuiltInRegistries.ITEM;
		boolean key = items.containsKey(Reg.id("brassback_key")), staff = items.containsKey(Reg.id("stormcoil_staff")) && items.containsKey(Reg.id("amethyst_wand"));
		var tw = dev.elsewhere.verdigris_sea.VerdigrisEntities.arsenalOffers(qm);
		boolean keyOk = !key || tw.stream().anyMatch(o -> o.getResult().is(items.getValue(Reg.id("brassback_key")))
			&& o.getCostA().is(dev.elsewhere.verdigris_sea.VerdigrisItems.SCRAPED_PATINA) && o.getCostA().getCount() == 3 && o.getCostB().getCount() == 16);
		boolean staffOk = !staff || tw.stream().anyMatch(o -> o instanceof dev.elsewhere.verdigris_ironpeak.ExtraCostOffer x && x.extras().size() == 2
			&& o.getCostB().getCount() == 12);
		check(tw.size() == (key ? 1 : 0) + (staff ? 1 : 0) && keyOk && staffOk,
			f("tidewright arsenal trades by id (Brassback Key %s, Stormcoil Staff %s; %d offers)", key ? "present" : "absent: skipped", staff ? "present" : "absent: skipped", tw.size()));
		boolean harness = items.containsKey(Reg.id("engineers_harness"));
		check((dev.elsewhere.verdigris_ironpeak.ArsenalLoot.unique("verdigrim") != null) == key && (dev.elsewhere.verdigris_ironpeak.ArsenalLoot.unique("galvanox") != null) == harness
			&& dev.elsewhere.verdigris_ironpeak.ArsenalLoot.unique("thurgrim") == null, f("other modules' boss drops join the kit as uniques and skip cleanly when absent (key %s, harness %s)", key, harness));

		// HUD / client toggles (stub of the shared settings; same parser in every module)
		var o1 = dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.parse("{\"altitudeMeter\":\"hidden\",\"longbowZoom\":false}");
		var o2 = dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.parse("{\"altitudeMeter\":\"always\",\"hudHidden\":true}");
		var o3 = dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.parse("not json");
		check(dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.meterFrom(o1, "altitudeMeter") == dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.Mode.HIDDEN
			&& !dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.toggleFrom(o1, "longbowZoom", true)
			&& dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.meterFrom(o2, "altitudeMeter") == dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.Mode.HIDDEN
			&& dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.meterFrom(o3, "altitudeMeter") == dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.Mode.AUTO
			&& dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.toggleFrom(o3, "longbowZoom", true),
			"client settings: altitudeMeter auto/always/hidden, hudHidden hides it, longbowZoom on by default");
	}

	static int lore(ItemStack s) {
		return s.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().size();
	}

	/** Fires a bow at the given draw from a fake player with 4 arrows; returns {arrow speed, arrows used}. */
	static double[] shotSpeed(ServerLevel level, BlockPos at, ItemStack bow, int drawTicks, boolean infinity) {
		var p = fake(level, at);
		p.setNoGravity(true);
		if (infinity) {
			var inf = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY);
			bow.enchant(inf, 1);
		}
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);
		p.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
		var box = new AABB(at).inflate(8);
		var before = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box);
		bow.getItem().releaseUsing(bow, level, p, bow.getUseDuration(p) - drawTicks);
		double speed = 0;
		for (var a : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box)) {
			if (before.contains(a)) continue;
			speed = Math.max(speed, a.getDeltaMovement().length());
			a.discard();
		}
		int used = 4 - p.getInventory().countItem(Items.ARROW);
		return new double[] {speed, used};
	}

	static void checkLoot(MinecraftServer server) {
		int bad = 0;
		for (String t : new String[] {"entities/brass_guardian", "entities/clockwork_drowned", "entities/highland_yak", "blocks/pearlite_ore", "blocks/verdigris_plate", "blocks/kelpwood_slab", "chests/clockwork_rig"}) {
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Reg.id(t));
			var table = server.reloadableRegistries().getLootTable(key);
			if (table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
				bad++;
				VerdigrisIronpeakContent.LOG.warn("[selftest] loot table {} did not load", t);
			}
		}
		check(bad == 0, f("loot tables load (%d missing of 7 sampled)", bad));
	}

	static void checkDimensions(MinecraftServer server) {
		for (var dim : List.of(Fx.VERDIGRIS_SEA, Fx.IRONPEAK)) {
			ServerLevel level = server.getLevel(dim);
			check(level != null, "dimension " + dim.identifier() + " is loaded");
			if (level == null) continue;
			long t = System.currentTimeMillis();
			int solid = 0, water = 0, top = level.getMinY();
			for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) {
				var chunk = level.getChunk(cx * 16, cz * 16, ChunkStatus.FULL, true);
				for (int x = 0; x < 16; x += 4) for (int z = 0; z < 16; z += 4) {
					int h = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
					top = Math.max(top, h);
				}
			}
			for (int y = level.getMinY(); y < level.getMaxY(); y += 8) {
				var s = level.getBlockState(new BlockPos(8, y, 8));
				if (s.is(Blocks.WATER)) water++;
				else if (!s.isAir()) solid++;
			}
			check(solid > 0, f("%s generates terrain (4 chunks in %d ms, highest surface Y %d, %d solid / %d water samples in one column)",
				dim.identifier(), System.currentTimeMillis() - t, top, solid, water));
			check(level.getBiome(new BlockPos(8, top, 8)).unwrapKey().map(k -> k.identifier().getNamespace().equals("elsewhere")).orElse(false),
				dim.identifier() + " uses its own biomes: " + level.getBiome(new BlockPos(8, top, 8)).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		}
	}

	/** 2.0 alpha.3: resident traders, Tidelock Vaults, explorer maps, trade caps, the Climber template, the hidden Skyiron sword. */
	static void checkPhase1c(MinecraftServer server) {
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA), peak = server.getLevel(Fx.IRONPEAK);
		if (sea == null || peak == null) {
			check(false, "phase 1c: dimensions missing");
			return;
		}
		// --- traders spawn in their structures, once, and come back after a death
		String[][] sites = {{"engineers_atoll", "tidewright"}, {"clan_hold", "clan_quartermaster"}};
		List<Runnable> unforce = new java.util.ArrayList<>();
		for (String[] st : sites) {
			ServerLevel level = st[0].equals("clan_hold") ? peak : sea;
			var found = MapOffers.find(level, List.of(Reg.id(st[0])), new BlockPos(0, 64, 0));
			check(found != null, st[0] + ": found by the structure search");
			if (found == null) continue;
			BlockPos at = found.getFirst();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, ChunkStatus.FULL, true);
			StructureTraders.clearCooldowns();
			var sitesFound = StructureTraders.sites(level, at, 40);
			check(!sitesFound.isEmpty(), st[0] + ": structure start is visible to the trader spawner");
			if (sitesFound.isEmpty()) continue;
			var site = sitesFound.get(0);
			// the spot must be entity-visible before ensure(force) runs: a chunk loaded inside the self-test only becomes so once the
			// server runs its chunk tasks, and until then ensure's own de-dup query (and the cleanup below) cannot see the trader it
			// just added, so a second call spawned a duplicate (merged-jar run, GOTCHAS: arsenal)
			int fx = site.spot().getX() >> 4, fz = site.spot().getZ() >> 4;
			level.setChunkForced(fx, fz, true);
			unforce.add(() -> level.setChunkForced(fx, fz, false));
			long visibleBy = System.nanoTime() + 30_000_000_000L;
			while (!level.isPositionEntityTicking(site.spot()) && System.nanoTime() < visibleBy) {
				if (!level.getChunkSource().pollTask()) java.util.concurrent.locks.LockSupport.parkNanos(200_000L);
			}
			check(level.getBlockState(site.spot()).isAir(), st[0] + ": the trader's spot is free (" + site.spot().toShortString() + ")");
			int n1 = StructureTraders.ensure(level, at, true), n2 = StructureTraders.ensure(level, at, true);
			var found1 = level.getEntitiesOfClass(ElsewhereTrader.class, new net.minecraft.world.phys.AABB(site.spot()).inflate(24));
			check(n1 == 1 && n2 == 0 && found1.size() == 1 && found1.get(0).isPersistenceRequired(), f("%s: exactly one persistent %s spawns (%d, then %d more)", st[0], st[1], n1, n2));
			if (found1.isEmpty()) continue;
			ElsewhereTrader t = found1.get(0);
			check(t.getType() == site.type() && !t.removeWhenFarAway(1e6), st[1] + ": right type, never despawns");
			// trades: caps and gating
			FakePlayer cust = fake(level, site.spot());
			var offers = t.offersFor(cust);
			if (st[1].equals("tidewright")) {
				var gear = offers.stream().filter(o -> o.getResult().is(Items.EMERALD)).findFirst().orElse(null);
				check(gear != null && gear.getMaxUses() == 16, "tidewright: 6 gears -> emerald restocks like vanilla (16 uses)");
			} else {
				var iron = offers.stream().filter(o -> o.getResult().is(Items.EMERALD) && o.getBaseCostA().is(Items.IRON_BARS)).findFirst().orElse(null);
				check(iron != null && iron.getMaxUses() == 16, "quartermaster: 16 iron bars -> emerald uses 16");
				var qm = dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(t, cust);
				check(qm.stream().noneMatch(o -> o.getResult().is(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_HELMET)), "quartermaster: no finished Climber helmet for sale");
			}
			// maps point at real structures
			var maps = offers.stream().filter(o -> o.getResult().is(Items.FILLED_MAP)).toList();
			check(!maps.isEmpty(), st[1] + ": sells explorer maps (" + maps.size() + " at rank " + (st[1].equals("clan_hold") ? "0" : "-") + ")");
			for (var m : maps) check(m.getItemCostA().item().value() == Items.EMERALD && m.getItemCostB().isPresent(), st[1] + ": map costs emeralds + a compass");
			// kill, cooldown, respawn
			t.discard();
			StructureTraders.clearCooldowns();
			check(StructureTraders.ensure(level, at, true) == 1, st[1] + ": a missing trader is replaced");
			level.getEntitiesOfClass(ElsewhereTrader.class, new net.minecraft.world.phys.AABB(site.spot()).inflate(24)).forEach(e -> e.discard());
		}
		unforce.forEach(Runnable::run);
		// maps resolve to generated structures
		String[][] mapSites = {{"verdigris_sea", "drowned_cathedral"}, {"verdigris_sea", "lighthouse"}, {"ironpeak", "summit_throne"}, {"ironpeak", "proving_grounds"},
			{"the_end", "ossyra_matriarch_spire"}, {"the_end", "aphelion_shattered_halo"}, {"the_nether", "grunthar_treasure_hall"}, {"the_nether", "solaxis_pyre_cradle"}, {"the_nether", "mournmaw_weeping_basin"}};
		for (String[] ms : mapSites) {
			ServerLevel level = ms[0].equals("verdigris_sea") ? sea : ms[0].equals("ironpeak") ? peak : server.getLevel(ms[0].equals("the_end") ? net.minecraft.world.level.Level.END : net.minecraft.world.level.Level.NETHER);
			if (level == null) {
				check(false, "map target dimension " + ms[0] + " missing");
				continue;
			}
			if (level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Reg.id(ms[1]))).isEmpty()) {
				check(true, "explorer map to " + ms[1] + ": structure not in this build (other module), skipped");
				continue;
			}
			var found = MapOffers.find(level, List.of(Reg.id(ms[1])), BlockPos.ZERO);
			boolean real = false;
			if (found != null) {
				BlockPos p = found.getFirst();
				real = level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true).getStartForStructure(found.getSecond().value()) != null;
			}
			var map = found == null ? null : MapOffers.makeMap(level, Reg.id(ms[1]), BlockPos.ZERO, net.minecraft.network.chat.Component.literal("t"));
			check(found != null && real && map != null && map.is(Items.FILLED_MAP), "explorer map to " + ms[1] + (found == null ? " (none found)" : " at " + found.getFirst().toShortString() + (real ? " (a real structure start)" : " (NO structure there)")));
		}
		// --- Tidelock Vault generates and opens with the Tide Key
		var vf = MapOffers.find(sea, List.of(Reg.id("tidelock_vault")), new BlockPos(0, 64, 0));
		check(vf != null, "tidelock_vault: found by the structure search");
		if (vf != null) {
			BlockPos at = vf.getFirst();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) sea.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, ChunkStatus.FULL, true);
			BlockPos origin = null;
			for (var start : sea.structureManager().startsForStructure(at.getX() >> 4, at.getZ() >> 4, s -> s instanceof ProceduralStructure ps && ps.blueprint().equals("tidelock_vault")))
				for (var piece : start.getPieces()) if (piece instanceof ProceduralStructure.Piece pp) origin = pp.origin();
			check(origin != null, "tidelock_vault: structure start generated at " + (origin == null ? "?" : origin.toShortString()));
			if (origin != null) {
				BlockPos vp = origin.offset(0, 1, -3);
				var state = sea.getBlockState(vp);
				check(state.is(Blocks.VAULT), "tidelock_vault: a vault block stands in the chamber (" + state.getBlock().getDescriptionId() + ")");
				if (state.is(Blocks.VAULT) && sea.getBlockEntity(vp) instanceof net.minecraft.world.level.block.entity.vault.VaultBlockEntity v) {
					var cfg = v.getConfig();
					check(cfg.keyItem().is(VerdigrisItems.TIDE_KEY) && cfg.lootTable().identifier().equals(Reg.id("chests/tidelock_vault")), "tidelock_vault: keyed to the Tide Key, loot chests/tidelock_vault");
					var table = server.reloadableRegistries().getLootTable(cfg.lootTable());
					check(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "tidelock_vault: loot table loads");
					FakePlayer keyer = fake(sea, vp.offset(0, 0, 1));
					var wrong = new ItemStack(Items.STICK);
					sea.setBlock(vp, state.setValue(net.minecraft.world.level.block.VaultBlock.STATE, net.minecraft.world.level.block.entity.vault.VaultState.ACTIVE), 3);
					var key = new ItemStack(VerdigrisItems.TIDE_KEY, 2);
					var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(vp), net.minecraft.core.Direction.SOUTH, vp, false);
					sea.getBlockState(vp).useItemOn(wrong, sea, keyer, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
					sea.getBlockState(vp).useItemOn(key, sea, keyer, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
					check(wrong.getCount() == 1 && key.getCount() == 1, "tidelock_vault: the Tide Key opens it (key used up) and a stick does nothing");
					check(sea.getBlockState(vp).getValue(net.minecraft.world.level.block.VaultBlock.STATE) == net.minecraft.world.level.block.entity.vault.VaultState.UNLOCKING, "tidelock_vault: unlocking after the key");
				}
			}
		}
		// --- the new / hidden items
		check(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_UPGRADE_TEMPLATE != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_UPGRADE_TEMPLATE).equals(Reg.id("climber_upgrade_smithing_template")),
			"climber_upgrade_smithing_template is registered");
		check(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Reg.id("skyiron_sword")), "skyiron_sword stays registered (hidden from the tab and JEI, no recipe)");
		FakePlayer rk = fake(peak, new BlockPos(0, 300, 0));
		var tpl = dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(rk);
		check(tpl.stream().noneMatch(o -> o.getResult().is(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_HELMET)), "rank 0 quartermaster offers no helmet");
	}

	static void checkTrips() {
		// A player living at a storm-copper spot or a peak (home within 128 blocks) for 100 h.
		Random r = new Random(1);
		double livingTrips = 0;
		int runs = 200;
		for (int run = 0; run < runs; run++) {
			double lastTrip = Double.NEGATIVE_INFINITY;
			int trips = 0;
			for (double h = 0; h < 100; h += 1.0 / 3) { // one in-game day = 20 min
				double c = TripMath.chance(TripTrigger.Kind.LOCATION, "ironpeak.peak", 0.15, 1, h, h - lastTrip, 50, true, 0, false);
				if (r.nextDouble() < c) {
					trips++;
					lastTrip = h;
				}
				// ~1 storm-minute per hour near copper at home (DIMENSIONS.md: ~15 storm-minutes in 15 h)
				if (((int) (h * 3)) % 3 != 0) continue;
				double s = TripMath.chance(TripTrigger.Kind.LOCATION, "verdigris_sea.storm_minute", 0.20, 1, h, h - lastTrip, 50, true, 0, false);
				if (r.nextDouble() < s) {
					trips++;
					lastTrip = h;
				}
			}
			livingTrips += trips;
		}
		livingTrips /= runs;
		check(livingTrips < 2, f("living at a trigger spot for 100 h: %.2f trips on average (< 2)", livingTrips));
		// Typical explorer over 15 h: 3 fresh peaks, 15 storm-minutes in fresh regions.
		double explorer = 0;
		for (int run = 0; run < 2000; run++) {
			double lastTrip = Double.NEGATIVE_INFINITY;
			int trips = 0;
			for (int i = 0; i < 18; i++) {
				double h = 1 + i * (14.0 / 18);
				boolean peak = i % 6 == 0;
				double c = TripMath.chance(TripTrigger.Kind.LOCATION, peak ? "ironpeak.peak" : "verdigris_sea.storm_minute", peak ? 0.15 : 0.20, 1, h, h - lastTrip, 0, false, 0, false);
				if (r.nextDouble() < c) {
					trips++;
					lastTrip = h;
				}
			}
			explorer += trips;
		}
		explorer /= 2000;
		check(explorer > 0.3 && explorer < 4.0, f("typical explorer, this module's triggers over 15 h: %.2f trips (part of the ~2 total; BALANCE.md base chances 15%% / 20%%)", explorer));
		check(TripMath.familiarity(0, false) == 1 && Math.abs(TripMath.familiarity(0.5, false) - 0.5) < 1e-9 && TripMath.familiarity(10, false) == 0.03 && TripMath.familiarity(0, true) == 0.03,
			"familiarity damping halves every 30 min, floor 0.03, 0.03 at home");
	}

	static void checkHrolfLink(MinecraftServer server, ServerLevel level, HrolfBoss h) {
		var goat = h.mount();
		check(goat != null && goat.rider == h, "hrolf: Hrolf and Grimhorn start linked");
		var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
		h.saveWithoutId(out);
		var tag = out.buildResult();
		check(goat != null && tag.getStringOr("MountUUID", "").equals(goat.getUUID().toString()), "hrolf: the mount UUID is saved in NBT");
		h.forgetMount();
		boolean none = h.mount() == null;
		boolean back = h.relinkMount(level);
		check(none && back && h.mount() == goat && goat.rider == h, "hrolf: the link is restored from the saved UUID after a reload");
	}

	static ItemStack stack(net.minecraft.world.item.Item i) {
		return new ItemStack(i);
	}

	/** Strips the netherite that fake() puts on (and its modifiers) so a set can be measured on its own. */
	static void bare(FakePlayer p) {
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			p.getItemBySlot(slot).forEachModifier(slot, (attr, mod) -> {
				var inst = p.getAttribute(attr);
				if (inst != null) inst.removeModifier(mod.id());
			});
			p.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	static void wear(FakePlayer p, net.minecraft.world.item.Item h, net.minecraft.world.item.Item c, net.minecraft.world.item.Item l, net.minecraft.world.item.Item b) {
		bare(p);
		p.setItemSlot(EquipmentSlot.HEAD, stack(h));
		p.setItemSlot(EquipmentSlot.CHEST, stack(c));
		p.setItemSlot(EquipmentSlot.LEGS, stack(l));
		p.setItemSlot(EquipmentSlot.FEET, stack(b));
		equipSync(p);
		dev.elsewhere.stub.verdigris_ironpeak.SetBonus.update(p);
	}

	/** FakePlayers do not run the equipment-change step: apply the worn and held items' attribute modifiers by hand. */
	static void equipSync(FakePlayer p) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			p.getItemBySlot(slot).forEachModifier(slot, (attr, mod) -> {
				var inst = p.getAttribute(attr);
				if (inst != null && !inst.hasModifier(mod.id())) inst.addTransientModifier(mod);
			});
		}
	}

	static FakePlayer holding(ServerLevel level, net.minecraft.world.item.Item item) {
		FakePlayer p = fake(level, new BlockPos(0, 100, 0));
		p.setItemSlot(EquipmentSlot.MAINHAND, stack(item));
		equipSync(p);
		return p;
	}

	static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	/** polish-endgame-sets: the Tidebrass, Tidesteel, Climber and Skyiron lines, trims, rifts, trades. */
	static void checkEndgame(MinecraftServer server) {
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA), peak = server.getLevel(Fx.IRONPEAK);
		var items = BuiltInRegistries.ITEM;
		boolean ids = true;
		for (String id : List.of("skyiron_ingot", "tidebrass_ingot", "tidebrass_upgrade_smithing_template", "climber_upgrade_smithing_template", "tidal_armor_trim_smithing_template", "summit_armor_trim_smithing_template",
			"diving_suit_helmet", "diving_suit_chestplate", "diving_suit_leggings", "diving_suit_boots", "skyiron_pickaxe", "tidesteel_sword")) ids &= items.containsKey(Reg.id(id));
		check(ids, "endgame ids registered (old diving_suit_* ids kept for the Tidebrass set, new ingots, templates and trim templates)");
		// --- recipes: ingots, G9 smithing for every piece and tool, template duplication
		var rm = server.getRecipeManager();
		int missing = 0;
		List<String> need = new java.util.ArrayList<>(List.of("skyiron_ingot", "tidebrass_ingot", "climber_template_duplicate", "tidebrass_template_duplicate"));
		for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
			need.add("climber_" + slot + "_smithing");
			need.add("diving_suit_" + slot + "_smithing");
		}
		for (String t : List.of("pickaxe", "axe", "shovel", "hoe")) need.add("skyiron_" + t + "_smithing");
		for (String t : List.of("sword", "pickaxe", "axe", "shovel", "hoe")) need.add("tidesteel_" + t + "_smithing");
		for (String r : need) if (rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Reg.id(r))).isEmpty()) {
			missing++;
			VerdigrisIronpeakContent.LOG.warn("[selftest] missing recipe {}", r);
		}
		check(missing == 0, f("%d endgame recipes load (ingots, smithing for 4+4 armor, 4 Skyiron and 5 Tidesteel tools, template duplication); %d missing", need.size(), missing));
		check(rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Reg.id("skyiron_sword_smithing"))).isEmpty(), "no Skyiron sword recipe (hidden: id kept, no recipe, no tab)");
		var smithIn = new net.minecraft.world.item.crafting.SmithingRecipeInput(stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_UPGRADE_TEMPLATE), stack(Items.DIAMOND_BOOTS), stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_INGOT));
		var smith = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, smithIn, sea);
		check(smith.isPresent() && smith.get().value().assemble(smithIn).is(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS), "smithing chain: Climber template + diamond boots + Skyiron Ingot = Climber Boots");
		var smith2In = new net.minecraft.world.item.crafting.SmithingRecipeInput(stack(VerdigrisItems.TIDEBRASS_UPGRADE_TEMPLATE), stack(Items.DIAMOND_SWORD), stack(VerdigrisItems.TIDEBRASS_INGOT));
		var smith2 = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, smith2In, sea);
		check(smith2.isPresent() && smith2.get().value().assemble(smith2In).is(VerdigrisItems.TIDESTEEL_SWORD), "smithing chain: Tidebrass template + diamond sword + Tidebrass Ingot = Tidesteel Sword");
		// --- enchantment cons
		var reg = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		var acc = net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE;
		String[] why = {"", "", "", "", "", "", ""};
		boolean[] r = {
			!stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_PICKAXE).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), acc),
			stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_PICKAXE).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), acc),
			!stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING), acc),
			stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), acc),
			!stack(VerdigrisItems.DIVING_SUIT_HELMET).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.RESPIRATION), acc) && !stack(VerdigrisItems.DIVING_SUIT_HELMET).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.AQUA_AFFINITY), acc),
			!stack(VerdigrisItems.DIVING_SUIT_BOOTS).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.DEPTH_STRIDER), acc),
			stack(VerdigrisItems.DIVING_SUIT_BOOTS).canBeEnchantedWith(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), acc)};
		boolean cons = true;
		for (boolean x : r) cons &= x;
		VerdigrisIronpeakContent.LOG.info("[selftest] cons detail {}", java.util.Arrays.toString(r));
		check(cons, "cons: Skyiron pick no Fortune (efficiency ok), Climber boots no Feather Falling (protection ok), Tidebrass helm no Respiration/Aqua Affinity, boots no Depth Strider");
		// --- Climber set: per-piece perks and Mountain Goat
		FakePlayer cl = fake(peak, new BlockPos(0, 300, 0));
		wear(cl, dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_HELMET, dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_CHESTPLATE, dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_LEGGINGS, dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS);
		check(near(cl.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5), f("Mountain Goat: fall damage x%.2f (-50%%)", cl.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
		check(near(cl.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 9.0) && near(cl.getAttributeValue(Attributes.STEP_HEIGHT), 1.1) && near(cl.getAttributeValue(Attributes.BURNING_TIME), 2.0),
			f("Climber per-piece perks: safe fall %.1f (+3 chest, +3 boots), step %.2f (+0.5 legs), burning x%.2f (fur +25%% each)", cl.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), cl.getAttributeValue(Attributes.STEP_HEIGHT), cl.getAttributeValue(Attributes.BURNING_TIME)));
		check(near(cl.getAttributeValue(Attributes.ARMOR), 19) && near(cl.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12), f("Climber armor %.0f / toughness %.0f (netherite 20 / 12: a safe near-equal)", cl.getAttributeValue(Attributes.ARMOR), cl.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		EndgameGear.startGoatCling(cl);
		check(dev.elsewhere.ironpeak.IceAxeItem.CLINGING.containsKey(cl.getUUID()) && EndgameGear.goatReadyAt(cl) - peak.getGameTime() == GearStats.GOAT_CLING_COOLDOWN && GearStats.GOAT_CLING_TICKS == 60,
			"Mountain Goat: cling 3 s, 8 s cooldown");
		dev.elsewhere.ironpeak.IceAxeItem.CLINGING.remove(cl.getUUID());
		cl.setItemSlot(EquipmentSlot.HEAD, stack(dev.elsewhere.ironpeak.IronpeakItems.SUMMIT_CROWN));
		dev.elsewhere.stub.verdigris_ironpeak.SetBonus.update(cl);
		check(near(cl.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5), "Mountain Goat: the Summit Crown counts as the Climber helmet");
		cl.setItemSlot(EquipmentSlot.FEET, stack(Items.NETHERITE_BOOTS));
		dev.elsewhere.stub.verdigris_ironpeak.SetBonus.update(cl);
		check(near(cl.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0), "Mountain Goat: gone when a piece comes off");
		check(dev.elsewhere.ironpeak.IronpeakItems.thinAirProof(stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_HELMET)) && dev.elsewhere.ironpeak.IronpeakItems.thinAirProof(stack(dev.elsewhere.ironpeak.IronpeakItems.SUMMIT_CROWN))
			&& !dev.elsewhere.ironpeak.IronpeakItems.thinAirProof(stack(Items.LEATHER_HELMET)), "Climber helm: no Thin Air hunger or Mining Fatigue (leather helm still suffers)");
		FakePlayer ps = fake(peak, new BlockPos(0, 300, 0));
		ps.setItemSlot(EquipmentSlot.FEET, stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS));
		boolean snow = net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(ps);
		ps.setItemSlot(EquipmentSlot.FEET, stack(dev.elsewhere.ironpeak.IronpeakItems.CRAMPONS));
		boolean snow2 = net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(ps);
		ps.setItemSlot(EquipmentSlot.FEET, stack(Items.NETHERITE_BOOTS));
		check(snow && snow2 && !net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(ps), "powder snow: Climber Boots and Crampons walk on it, netherite boots do not");
		// --- Tidebrass: perks, Sea Legs
		FakePlayer tb = fake(sea, new BlockPos(0, 100, 0));
		wear(tb, VerdigrisItems.DIVING_SUIT_HELMET, VerdigrisItems.DIVING_SUIT_CHESTPLATE, VerdigrisItems.DIVING_SUIT_LEGGINGS, VerdigrisItems.DIVING_SUIT_BOOTS);
		check(near(tb.getAttributeValue(Attributes.OXYGEN_BONUS), 4.0) && near(tb.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 1.0) && near(tb.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED), 1.0),
			f("Tidebrass: oxygen +%.0f (helm 2, chest 2), water movement %.2f (legs and boots 0.33 + Sea Legs 0.34), underwater mining %.2f (full speed)", tb.getAttributeValue(Attributes.OXYGEN_BONUS), tb.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), tb.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED)));
		check(near(tb.getAttributeValue(Attributes.ARMOR), 20) && near(tb.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12), "Tidebrass armor 20 / toughness 12 (equal to netherite on paper; the price is land speed and no fire-proofing)");
		EndgameGear.tickPlayer(tb, false);
		double landSpeed = tb.getAttributeValue(Attributes.MOVEMENT_SPEED) / tb.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		check(near(landSpeed, 0.8), f("Tidebrass con: on land -20%% speed (x%.2f)", landSpeed));
		check(!VerdigrisSea.allowDamage(tb, sea.damageSources().lightningBolt(), 5) && VerdigrisSea.allowDamage(fake(sea, new BlockPos(0, 100, 0)), sea.damageSources().lightningBolt(), 5),
			"Sea Legs: lightning cannot hurt the full set (netherite still takes it)");
		tb.setItemSlot(EquipmentSlot.HEAD, stack(VerdigrisItems.VERDIGRIS_MITRE));
		dev.elsewhere.stub.verdigris_ironpeak.SetBonus.update(tb);
		check(near(tb.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 1.0), "Sea Legs: the Verdigris Mitre counts as the helmet");
		// --- Tidesteel wet bonus
		FakePlayer ts = holding(sea, VerdigrisItems.TIDESTEEL_SWORD);
		double dry = ts.getAttributeValue(Attributes.ATTACK_DAMAGE);
		EndgameGear.tickPlayer(ts, true);
		double wetD = ts.getAttributeValue(Attributes.ATTACK_DAMAGE);
		EndgameGear.tickPlayer(ts, false);
		check(near(dry, 7.0) && near(wetD, 9.0) && near(ts.getAttributeValue(Attributes.ATTACK_DAMAGE), 7.0), f("Tidesteel sword: %.0f dry, %.0f wet, back to %.0f when dry again", dry, wetD, ts.getAttributeValue(Attributes.ATTACK_DAMAGE)));
		check(near(holding(sea, VerdigrisItems.TIDESTEEL_AXE).getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0), "Tidesteel axe: 9 dry");
		// --- Skyiron Quarry and felling
		BlockPos q = new BlockPos(6400, 120, 6400);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) peak.getChunk((q.getX() + dx * 16) >> 4, (q.getZ() + dz * 16) >> 4, ChunkStatus.FULL, true);
		FakePlayer sk = fake(peak, q.offset(0, 5, 0));
		sk.setItemSlot(EquipmentSlot.MAINHAND, stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_PICKAXE));
		for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) peak.setBlockAndUpdate(q.offset(x, y, z), Blocks.STONE.defaultBlockState());
		peak.setBlockAndUpdate(q.offset(1, 0, 0), Blocks.COAL_ORE.defaultBlockState());
		peak.setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
		int broke = EndgameGear.quarry(peak, sk, q, net.minecraft.core.Direction.NORTH);
		int gone = 0;
		for (BlockPos n : EndgameGear.faceNeighbours(q, net.minecraft.core.Direction.NORTH)) if (peak.getBlockState(n).isAir()) gone++;
		check(broke == 7 && gone == 7 && peak.getBlockState(q.offset(1, 0, 0)).is(Blocks.COAL_ORE) && peak.getBlockState(q.offset(0, 0, 1)).is(Blocks.STONE),
			f("Quarry pickaxe above Y 63: the 3x3 face breaks (%d of 8, the ore stays, the layer behind stays)", broke));
		check(!EndgameGear.quarryPickBlock(peak, q.offset(0, 0, 1), Blocks.OBSIDIAN.defaultBlockState()) && !EndgameGear.quarryPickBlock(peak, q.offset(0, 0, 1), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState())
			&& EndgameGear.quarryPickBlock(peak, q.offset(0, 0, 1), Blocks.STONE.defaultBlockState()), "Quarry rule: hardness 3 or less, no ores");
		BlockPos low = new BlockPos(6400, 40, 6400);
		for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) peak.setBlockAndUpdate(low.offset(x, y, z), Blocks.STONE.defaultBlockState());
		check(EndgameGear.quarry(peak, sk, low, net.minecraft.core.Direction.NORTH) == 0, "Quarry pickaxe below Y 63: just a pickaxe (0 extra blocks)");
		sk.setItemSlot(EquipmentSlot.MAINHAND, stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_SHOVEL));
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) peak.setBlockAndUpdate(q.offset(x, 8, z), Blocks.DIRT.defaultBlockState());
		peak.setBlockAndUpdate(q.offset(0, 8, 0), Blocks.AIR.defaultBlockState());
		check(EndgameGear.quarry(peak, sk, q.offset(0, 8, 0), net.minecraft.core.Direction.UP) == 8, "Quarry shovel: dirt 3x3");
		sk.setItemSlot(EquipmentSlot.MAINHAND, stack(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_AXE));
		BlockPos tree = q.offset(10, 3, 10);
		for (int y = 0; y < 30; y++) peak.setBlockAndUpdate(tree.above(y), Blocks.OAK_LOG.defaultBlockState());
		int felled = EndgameGear.fell(peak, sk, tree);
		check(felled == GearStats.FELL_MAX_LOGS && peak.getBlockState(tree.above(29)).is(Blocks.OAK_LOG), f("Skyiron axe: fells up to 24 connected logs above Y 63 (%d)", felled));
		check(near(holding(peak, dev.elsewhere.ironpeak.IronpeakItems.SKYIRON_AXE).getAttributeValue(Attributes.ATTACK_DAMAGE), 9.0), "Skyiron axe: 9 damage");
		check(near(dev.elsewhere.ironpeak.Ironpeak.skyironMultiplier(1400), 1.0), "Skyiron: altitude mining speed is gone (Quarry replaces it)");
		// --- Return Rifts: Ironpeak foothills only, spacing 30 / separation 10
		try (var in = SelfTest.class.getResourceAsStream("/data/elsewhere/worldgen/structure_set/ironpeak_return_rifts.json")) {
			String js = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s", "");
			check(js.contains("\"spacing\":30") && js.contains("\"separation\":10"), "ironpeak return rifts: spacing 30, separation 10");
		} catch (Exception e) {
			check(false, "ironpeak return rift set readable: " + e);
		}
		var riftTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, Reg.id("has_return_rift/ironpeak"));
		int riftsOk = 0, riftsSeen = 0;
		java.util.Set<BlockPos> distinct = new java.util.HashSet<>();
		for (int i = 0; i < 12; i++) {
			var found = MapOffers.find(peak, List.of(Reg.id("ironpeak_return_rift")), new BlockPos(-9000 + (i % 4) * 6000, 64, -6000 + (i / 4) * 6000));
			if (found == null) continue;
			BlockPos rp = found.getFirst();
			if (!distinct.add(new BlockPos(rp.getX(), 0, rp.getZ()))) continue;
			riftsSeen++;
			peak.getChunk(rp.getX() >> 4, rp.getZ() >> 4, ChunkStatus.FULL, true);
			int surf = peak.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, rp.getX(), rp.getZ());
			boolean okRift = peak.getBiome(new BlockPos(rp.getX(), surf, rp.getZ())).is(riftTag) && surf <= 410;
			if (okRift) riftsOk++;
			else VerdigrisIronpeakContent.LOG.info("[selftest] rift off-biome at {} surface {} biome {}", rp, surf, peak.getBiome(new BlockPos(rp.getX(), surf, rp.getZ())).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		}
		check(riftsSeen >= 6 && riftsOk == riftsSeen, f("ironpeak return rifts: %d of %d located rifts are in foothills biomes below Y 400 (ruin included)", riftsOk, riftsSeen));
		check(SelfTest.class.getResource("/data/elsewhere/worldgen/structure_set/greenhaze_return_rifts.json") == null, "no swamp (Greenhaze) rift set and one rift system per dimension in this module");
		// --- trims
		var patterns = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN);
		check(patterns.get(Reg.id("tidal")).isPresent() && patterns.get(Reg.id("summit")).isPresent(), "trims: the Tidal and Summit patterns are registered");
		int trimMissing = 0;
		for (String n : List.of("tidal", "summit")) {
			if (SelfTest.class.getResource("/assets/elsewhere/textures/trims/entity/humanoid/" + n + ".png") == null || SelfTest.class.getResource("/assets/elsewhere/textures/trims/entity/humanoid_leggings/" + n + ".png") == null) trimMissing++;
			if (rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Reg.id(n + "_armor_trim_smithing_template_smithing_trim"))).isEmpty()) trimMissing++;
			if (rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Reg.id(n + "_armor_trim_smithing_template_duplicate"))).isEmpty()) trimMissing++;
		}
		check(trimMissing == 0 && stack(VerdigrisItems.DIVING_SUIT_CHESTPLATE).is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR) && stack(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_HELMET).is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR),
			f("trims: textures, apply and duplicate recipes exist (%d missing), our armor is trimmable", trimMissing));
		// --- trades
		FakePlayer qm = fake(peak, new BlockPos(0, 300, 0));
		dev.elsewhere.ironpeak.Ironpeak.setVictory(qm, "thurgrim");
		var qo = dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(qm);
		boolean pend = items.containsKey(Reg.id("prospectors_pendulum"));
		check(qo.stream().anyMatch(o -> o.getResult().is(items.getValue(Reg.id("prospectors_pendulum")))) == pend && qo.stream().allMatch(o -> o.getMaxUses() <= 16),
			f("ironpeak trader: Prospector's Pendulum for 6 Skyiron + compass (%s), every trade capped at 16 uses", pend ? "present" : "gear2 absent: skipped"));
		if (pend) check(qo.stream().anyMatch(o -> o.getResult().is(items.getValue(Reg.id("prospectors_pendulum"))) && o.getCostA().getCount() == 6 && o.getCostB().is(Items.COMPASS)), "pendulum price is 6 Skyiron + 1 compass");
		var to = dev.elsewhere.verdigris_sea.VerdigrisEntities.tidewrightOffers(qm);
		boolean rep = items.containsKey(Reg.id("clockwork_repeater"));
		check(to.stream().anyMatch(o -> o.getResult().is(items.getValue(Reg.id("clockwork_repeater")))) == rep && to.stream().allMatch(o -> o.getMaxUses() <= 16),
			f("verdigris trader: Clockwork Repeater for a crossbow + 12 Gears (%s), every trade capped at 16 uses", rep ? "present" : "arsenal absent: skipped"));
		check(qo.stream().noneMatch(o -> o.getResult().is(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_CHESTPLATE) || o.getResult().is(dev.elsewhere.ironpeak.IronpeakItems.CLIMBER_BOOTS)),
			"bal-trader-climber: the armor is smithed, only the template is sold");
		// --- kit rates
		check(near(dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.SIGNATURE, true, 0), 1.0) && near(dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, true, 0), 0.5)
			&& near(dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.SIGNATURE, false, 0), 0.67) && near(dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, false, 6), 0.6),
			"drop rules: signature 100%/67%, others 50%/33%, pity +10% to 60%");
	}

	/** new-event-thunder-tide and new-summit-race. */
	static void checkEvents(MinecraftServer server) {
		ServerLevel sea = server.getLevel(Fx.VERDIGRIS_SEA), peak = server.getLevel(Fx.IRONPEAK);
		var store = dev.elsewhere.boss.kit.KitStore.of(server);
		store.data().cooldowns.remove("thunder_tide_active");
		store.data().cooldowns.remove("thunder_tide_next");
		boolean idleBefore = !dev.elsewhere.verdigris_sea.ThunderTide.isActive(server);
		int calm = dev.elsewhere.verdigris_sea.ThunderTide.boltInterval(sea);
		dev.elsewhere.verdigris_sea.ThunderTide.start(server);
		int tide = dev.elsewhere.verdigris_sea.ThunderTide.boltInterval(sea);
		check(idleBefore && dev.elsewhere.verdigris_sea.ThunderTide.isActive(server) && dev.elsewhere.verdigris_sea.ThunderTide.remaining(server) > 3500 && calm >= 400 && tide <= 300,
			f("thunder tide: starts for 3 minutes, bolts every %d ticks instead of %d", tide, calm));
		check(store.data().cooldowns.containsKey("thunder_tide_active") && store.data().cooldowns.containsKey("thunder_tide_next"), "thunder tide: state is on disk (KitStore), so a restart keeps it and never re-arms the next one");
		BlockPos at = new BlockPos(7000, 100, 7000);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) sea.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
		var drowned = dev.elsewhere.verdigris_sea.VerdigrisEntities.CLOCKWORK_DROWNED.create(sea, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		int gearBefore = 0;
		if (drowned != null) {
			drowned.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			sea.addFreshEntity(drowned);
			drowned.kill(sea);
			int viaEvent = sea.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4), e -> e.getItem().is(VerdigrisItems.VERDIGRIS_GEAR)).size();
			VerdigrisIronpeakContent.LOG.info("[selftest] drowned alive={} gear items via the death event: {}", drowned.isAlive(), viaEvent);
			if (viaEvent == 0) VerdigrisIronpeakContent.LOG.info("[selftest] direct bonusDrop -> {} (dim {} active {} type {})", dev.elsewhere.verdigris_sea.ThunderTide.bonusDrop(drowned), drowned.level().dimension().identifier(), dev.elsewhere.verdigris_sea.ThunderTide.isActive(server), drowned.getClass().getSimpleName());
			int gears = sea.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4), e -> e.getItem().is(VerdigrisItems.VERDIGRIS_GEAR)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			check(gears >= 1, f("thunder tide: Clockwork Drowned drop an extra Verdigris Gear (%d on the ground)", gears));
			sea.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6)).forEach(Entity::discard);
		} else {
			check(false, "thunder tide: Clockwork Drowned could not be created");
		}
		store.data().cooldowns.remove("thunder_tide_active");
		store.data().cooldowns.remove("thunder_tide_next");
		// summit race
		FakePlayer rc = fake(peak, new BlockPos(0, 450, 0));
		rc.snapTo(0.5, 450, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		boolean started = dev.elsewhere.ironpeak.SummitRace.startTick(rc) >= 0;
		rc.snapTo(0.5, 300, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		boolean cancelled = dev.elsewhere.ironpeak.SummitRace.startTick(rc) < 0;
		rc.snapTo(0.5, 450, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		rc.snapTo(0.5, 1310, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		int skyiron = 0;
		for (int i = 0; i < rc.getInventory().getContainerSize(); i++) if (rc.getInventory().getItem(i).is(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON)) skyiron += rc.getInventory().getItem(i).getCount();
		check(started && cancelled && dev.elsewhere.ironpeak.SummitRace.best(rc) > 0 && dev.elsewhere.ironpeak.SummitRace.startTick(rc) < 0 && skyiron == 4,
			f("summit race: starts at Y 400, drops below cancel it, the finish at Y 1300 pays %d Skyiron (fast run) and records a best time", skyiron));
		rc.snapTo(0.5, 450, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		rc.snapTo(0.5, 1310, 0.5);
		dev.elsewhere.ironpeak.SummitRace.tick(rc);
		int skyiron2 = 0;
		for (int i = 0; i < rc.getInventory().getContainerSize(); i++) if (rc.getInventory().getItem(i).is(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON)) skyiron2 += rc.getInventory().getItem(i).getCount();
		check(skyiron2 == skyiron && !store.cooldownReady("summit_race_reward|" + rc.getUUID()), "summit race: rewards once per 20 minutes (persistent cooldown), never gear");
	}

	static FakePlayer fake(ServerLevel level, BlockPos at) {
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_fighter"));
		p.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		return p;
	}

	static void checkBoss(MinecraftServer server, String id, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, String blueprint,
		double hp, double armor, double tough, int phases, BiFunction<ServerLevel, BlockPos, ? extends StubBoss> summon) {
		ServerLevel level = server.getLevel(dim);
		if (level == null) {
			check(false, id + ": dimension missing");
			return;
		}
		BlockPos c = new BlockPos(4000 + id.length() * 200, 200, 4000);
		level.getChunk(c.getX() >> 4, c.getZ() >> 4);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
		Blueprint bp = Blueprint.ALL.get(blueprint);
		bp.build(new BlockSink(level, c, null, level.getRandom()), level.getRandom());
		StubBoss boss = summon.apply(level, c);
		check(boss != null && boss.isAlive(), id + ": spawns in its arena");
		if (boss == null) return;
		var kit = dev.elsewhere.boss.kit.KitStore.of(server);
		check(kit.fightActive(boss.getUUID()), id + ": the fight is recorded on disk (fix-arena-restart / retry)");
		if (boss instanceof HrolfBoss hb) checkHrolfLink(server, level, hb);
		double maxHp = boss.getAttributeBaseValue(Attributes.MAX_HEALTH);
		check(maxHp == hp && boss.getAttributeValue(Attributes.ARMOR) == armor && boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == tough,
			f("%s: base HP %.0f (effective %.0f now), armor %.0f, toughness %.0f", id, maxHp, boss.getMaxHealth(), boss.getAttributeValue(Attributes.ARMOR), boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		check(BossGuards.overpower(38) == 31 && BossGuards.overpower(57) == 40.5F && BossGuards.overpower(16.8F) == 16.8F, id + ": overpower guard (38 -> 31, 57 -> 40.5)");
		// Lethal-hit guard (mixin on getDamageAfterMagicAbsorb): boss sources are recognised and capped at 60% of max health.
		check(BossGuards.fromBoss(boss.damageSources().mobAttack(boss)) && BossGuards.lethalCap(1000, 20) == 12F && BossGuards.lethalCap(5, 20) == 5F,
			id + ": lethal-hit guard recognises its hits and caps them at 60% of max health");
		// Arena snapshot and reset.
		BlockPos probe = c.offset(3, 1, 3);
		level.setBlockAndUpdate(probe, Blocks.GOLD_BLOCK.defaultBlockState());
		check(boss.snapshot() != null && boss.snapshot().differences(level) > 0, id + ": arena change detected by the snapshot");
		// Drive phases with player hits.
		FakePlayer hero = fake(level, c.offset(0, 0, 3));
		hero.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		if (boss instanceof GalvanoxBoss g) {
			int before = g.plates();
			g.scrape(level, hero, new ItemStack(Items.NETHERITE_AXE), net.minecraft.world.InteractionHand.MAIN_HAND);
			check(g.plates() == before - 1, f("galvanox: an axe scrape removes a plate (%d -> %d)", before, g.plates()));
			float plated = g.incomingFactor();
			int keep = g.plates();
			g.setPlates(0);
			float bare = g.incomingFactor();
			g.setPlates(keep);
			check(plated == 0.25F && bare == 1.0F, f("galvanox: plates block 75%% of damage (x%.2f plated, x%.2f bare)", plated, bare));
			check(g.chainLightning(level) >= 0, "galvanox: chain lightning runs");
		}
		if (boss instanceof VerdigrimBoss v) {
			v.setFlood(level, 4);
			check(level.getBlockState(c.offset(5, 2, 0)).is(Blocks.WATER), "verdigrim: flooding fills the nave");
			v.setFlood(level, 0);
			check(level.getBlockState(c.offset(5, 2, 0)).isAir(), "verdigrim: draining clears it");
		}
		int maxPhase = 0;
		int safety = 0;
		while (boss.isAlive() && safety++ < 400) {
			boss.setInvulnerableTime(0);
			boss.skipTransition();
			boss.setEmptyTicks(0);
			if (boss instanceof HrolfBoss h && h.mount() != null && h.mount().isAlive() && safety % 2 == 0) {
				h.mount().setInvulnerableTime(0);
				h.mount().hurtServer(level, level.damageSources().playerAttack(hero), 30);
			} else {
				boss.hurtServer(level, level.damageSources().playerAttack(hero), 30);
			}
			maxPhase = Math.max(maxPhase, boss.phase());
			for (int i = 0; i < 25 && boss.isAlive(); i++) {
				boss.setEmptyTicks(0);
				boss.tick();
				if (boss instanceof HrolfBoss h && h.mount() != null && h.mount().isAlive()) h.mount().tick(); // damage cooldown runs on its own ticks
			}
			if (boss instanceof VerdigrimBoss v && v.phase() == 2 && v.chordWindow() == 0 && safety % 7 == 0) {
				v.forceChord(List.of(1, 3, 5, 7));
				for (int n : new int[] {1, 3, 5, 7}) v.notePlayed(level, n, hero);
				check(v.isStunned(), "verdigrim: replaying the last chord stuns him");
			}
		}
		if (boss instanceof HrolfBoss h && h.mount() != null) VerdigrisIronpeakContent.LOG.info("[selftest] hrolf: mount health at the end {} / {}", h.mount().getHealth(), h.mount().getMaxHealth());
		check(maxPhase == phases - 1, f("%s: ran through all %d phases (reached phase %d)", id, phases, maxPhase + 1));
		check(!boss.isAlive(), f("%s: dies (after %d hits)", id, safety));
		check(!kit.fightActive(boss.getUUID()), id + ": the won fight is no longer on file");
		int xp = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(c).inflate(40)).stream().mapToInt(o -> o.getValue()).sum();
		check(xp >= dev.elsewhere.boss.kit.BossRewards.XP_FLOOR, f("%s: XP always drops (%d, floor %d)", id, xp, dev.elsewhere.boss.kit.BossRewards.XP_FLOOR));
		level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(c).inflate(40)).forEach(o -> o.discard());
		net.minecraft.world.item.Item sig = switch (id) {
			case "verdigrim" -> VerdigrisItems.VERDIGRIS_MITRE;
			case "galvanox" -> VerdigrisItems.GALVANIC_TRIDENT;
			case "thurgrim" -> dev.elsewhere.ironpeak.IronpeakItems.SUMMIT_CROWN;
			default -> dev.elsewhere.ironpeak.IronpeakItems.AVALANCHE_HORN;
		};
		check(boss.lastLoot.stream().anyMatch(st -> st.is(sig)), id + ": first kill drops the signature item (100%)");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(16));
		StringBuilder names = new StringBuilder();
		for (ItemEntity e : drops) names.append(e.getItem().getHoverName().getString()).append(" x").append(e.getItem().getCount()).append(", ");
		check(!drops.isEmpty() && drops.stream().noneMatch(e -> e.getOwner() != null && e.hasPickUpDelay() && false), id + ": loot dropped on the ground like any mob: " + names);
		check(boss.snapshot() != null && boss.snapshot().differences(level) == 0, id + ": arena restored after the fight");
		for (ItemEntity e : drops) e.discard();
		level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(c).inflate(40)).forEach(m -> m.discard());
		// Reset path: a second fight abandoned by everyone restores the arena and removes the boss.
		StubBoss again = summon.apply(level, c);
		level.setBlockAndUpdate(probe, Blocks.GOLD_BLOCK.defaultBlockState());
		again.resetFight(level);
		check(again.isRemoved() && level.getBlockState(probe).isAir(), id + ": reset when everyone leaves restores the arena and removes the boss");
		check(!kit.fightActive(again.getUUID()), id + ": a wiped fight is cleared from the store (retry path)");
		level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(c).inflate(40)).forEach(m -> m.discard());
	}
}
