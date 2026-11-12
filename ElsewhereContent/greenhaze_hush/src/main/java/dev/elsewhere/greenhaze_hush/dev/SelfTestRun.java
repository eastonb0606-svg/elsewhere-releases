package dev.elsewhere.greenhaze_hush.dev;

import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.grumblegut.Grumblegut;
import dev.elsewhere.boss.lithara.Lithara;
import dev.elsewhere.boss.percolax.Percolax;
import dev.elsewhere.greenhaze.Greenhaze;
import dev.elsewhere.greenhaze.GreenhazeTrips;
import dev.elsewhere.greenhaze_hush.GreenhazeHushContent;
import dev.elsewhere.hush.Hush;
import dev.elsewhere.stub.greenhaze_hush.BossDamage;
import dev.elsewhere.stub.greenhaze_hush.BossEntity;
import dev.elsewhere.stub.greenhaze_hush.TripSystem;
import dev.elsewhere.stub.greenhaze_hush.TripTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server self-test (greenhaze_hush.selftest=true): both dimensions load and generate, each boss spawns with its arena,
 * is driven through every phase by a FakePlayer, checks HP/armor, the overpower and lethal-hit guards, dies and drops loot;
 * an arena reset restores blocks; trip simulations meet the user's targets. Logs PASS/FAIL lines and a total.
 */
public final class SelfTestRun {
	private SelfTestRun() {}

	private static int pass, fail, tick;
	private static final List<Runnable> STEPS = new ArrayList<>();
	private static final List<Integer> AT = new ArrayList<>();

	static void check(String name, boolean ok, String detail) {
		if (ok) pass++; else fail++;
		GreenhazeHushContent.LOGGER.info("[selftest] {} {} {}", ok ? "PASS" : "FAIL", name, detail);
	}

	static int now() { return tick; }

	static void at(int t, Runnable r) {
		AT.add(t);
		STEPS.add(r);
	}

	record Fight(String id, BossEntity[] boss, FakePlayer[] player) {}

	public static void run(MinecraftServer server) {
		BossEntity.selfTestNoReset = true;
		ServerLevel gh = server.getLevel(Greenhaze.LEVEL), hu = server.getLevel(Hush.LEVEL);
		check("greenhaze dimension loaded", gh != null, "");
		check("the_hush dimension loaded", hu != null, "");
		{ // sfx lane 3.0
			java.util.List<String> bad = dev.elsewhere.greenhaze_hush.Sfx3.selfCheck();
			check("sfx3: boss sounds resolve, subtitled, no ender dragon or wither", bad.isEmpty(), dev.elsewhere.greenhaze_hush.Sfx3.paths().size() + " events " + bad);
			StringBuilder miss = new StringBuilder();
			for (String b : new String[] {"auris", "grumblegut", "lithara", "percolax"}) for (String c : new String[] {"roar", "roar_layer", "tele", "big"}) if (!dev.elsewhere.greenhaze_hush.Sfx3.has(b, c)) miss.append(' ').append(b).append('.').append(c);
			check("sfx3: all 4 greenhaze/hush bosses have their own phase roar and telegraph", miss.length() == 0, miss.toString());
			dev.elsewhere.greenhaze_hush.Sfx3.setEnabledForTest(false);
			boolean off = !dev.elsewhere.greenhaze_hush.Sfx3.roar(null, "auris") && dev.elsewhere.greenhaze_hush.Sfx3.pick("auris", "roar", null) == null;
			dev.elsewhere.greenhaze_hush.Sfx3.setEnabledForTest(null);
			check("sfx3: wind-ups only on real attacks (Sonic Boom yes; Deafened/Vent opened/Chewing no)", dev.elsewhere.greenhaze_hush.Sfx3.isAttack("Sonic Boom") && dev.elsewhere.greenhaze_hush.Sfx3.isAttack("Couch Slam: jump!") && !dev.elsewhere.greenhaze_hush.Sfx3.isAttack("Deafened! Strike now") && !dev.elsewhere.greenhaze_hush.Sfx3.isAttack("Vent opened!") && !dev.elsewhere.greenhaze_hush.Sfx3.isAttack("Chewing: belly exposed!"), "");
			check("sfx3: kill switch sfx3_greenhaze_hush=false falls back to 2.x", off, "");
		}
		dev.elsewhere.greenhaze_hush.s3.groups.SpawnGroups3.selfTest(server, (ok, what) -> check(what, ok, "")); // ARMIES 6 spawn groups (s-spawngroups)
		if (gh == null || hu == null) { finish(server); return; }
		for (ServerLevel l : new ServerLevel[]{gh, hu}) {
			try {
				int n = 0;
				for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) { l.getChunk(cx, cz); n++; }
				check("generates chunks in " + l.dimension().identifier(), true, n + " chunks");
			} catch (Throwable t) {
				check("generates chunks in " + l.dimension().identifier(), false, t.toString());
			}
		}
		int ghY = gh.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		check("greenhaze surface height sane", ghY > 40 && ghY < 250, "y=" + ghY);
		// trips
		double resident = TripSystem.simulate(TripTrigger.Kind.ACTIVITY, GreenhazeTrips.GREEN_LEAF_USE.id(), GreenhazeTrips.BASE_CHANCE, 15, 100, false, 400, 1);
		double home = TripSystem.simulate(TripTrigger.Kind.ACTIVITY, GreenhazeTrips.GREEN_LEAF_USE.id(), GreenhazeTrips.BASE_CHANCE, 15, 100, true, 400, 2);
		double explorer15 = TripSystem.simulate(TripTrigger.Kind.ACTIVITY, GreenhazeTrips.GREEN_LEAF_USE.id(), GreenhazeTrips.BASE_CHANCE, 15, 15, false, 400, 3);
		double shriekHome = TripSystem.simulate(TripTrigger.Kind.LOCATION, "the_hush.shriek", 0.02, 3, 100, true, 400, 4);
		check("green leaf user living in one region 100 h < 2 trips", resident < 2, String.format("%.2f", resident));
		check("green leaf user at home 100 h < 2 trips", home < 2, String.format("%.2f", home));
		check("green leaf user 15 h ~ rare", explorer15 < 1.5, String.format("%.2f", explorer15));
		check("shrieker resident at home 100 h < 2 trips", shriekHome < 2, String.format("%.2f", shriekHome));

		// worldgen: every code-built structure is found and its chunks generate without errors
		String[][] structs = {{"greenhaze", "great_hotbox"}, {"greenhaze", "bonfire_terrace"}, {"greenhaze", "sesh_hut"}, {"greenhaze", "lucid_spring"},
			{"greenhaze", "munchie_market"}, {"greenhaze", "fern_terrace_farms"}, {"the_hush", "listening_engine"}, {"the_hush", "choir_geode"}, {"the_hush", "silent_monastery"}, {"the_hush", "hanging_undercity"}};
		for (String[] st : structs) {
			ServerLevel l = st[0].equals("greenhaze") ? gh : hu;
			try {
				var reg = l.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
				var holder = reg.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
					dev.elsewhere.greenhaze_hush.Reg.id(st[1])));
				var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, net.minecraft.core.HolderSet.direct(holder), BlockPos.ZERO, 60, false);
				if (found == null) { check("structure " + st[1] + " found", false, "none within 60 chunks"); continue; }
				BlockPos at = found.getFirst();
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
				check("structure " + st[1] + " generates", true, at.toShortString());
			} catch (Throwable t) {
				check("structure " + st[1] + " generates", false, t.toString());
			}
		}
		// beta 2: Glimmer stub (Cloud Jar costs 8: 20 -> 12 -> 4 -> refused)
		FakePlayer caster = FakePlayer.get(gh, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "caster"));
		boolean a = dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(caster, 8), b = dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(caster, 8),
			c = dev.elsewhere.stub.greenhaze_hush.GlimmerStub.trySpend(caster, 8);
		check("glimmer stub spends and refuses", a && b && !c, a + "/" + b + "/" + c);
		// beta 2: loot tables in the 26.3 format (old `conditions`/`functions` keys were silently ignored)
		try {
			int[] echo = lootRolls(gh, caster, "entities/echo_stalker", "elsewhere:resonant_shard", 400);
			check("echo stalker resonant shard drop honours its 30% chance", echo[0] > 70 && echo[0] < 170, echo[0] + "/400 rolls");
			int[] toad = lootRolls(gh, caster, "entities/toke_toad", "elsewhere:toad_slime", 60);
			check("toke toad slime drop honours set_count 1-2", toad[0] == 60 && toad[1] == 2, "rolls with drop " + toad[0] + ", max count " + toad[1]);
		} catch (Throwable t) {
			check("loot tables roll", false, t.toString());
		}
		// beta 2: screen-effect preference (Nausea from the Haze is skipped when effects are off / reduce motion is on)
		try {
			dev.elsewhere.greenhaze.GreenLeafBridge.forceLoaded(false);
			dev.elsewhere.greenhaze.Haze.set(caster, 0F);
			dev.elsewhere.greenhaze.Haze.add(caster, 70, true);
			check("haze meter works without Green Leaf", Math.abs(dev.elsewhere.greenhaze.Haze.get(caster) - 70F) < 0.01, "" + dev.elsewhere.greenhaze.Haze.get(caster));
			for (int i = 0; i < 30; i++) dev.elsewhere.greenhaze.Haze.applyEffects(caster, 70F);
			boolean nauseaOn = caster.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
			caster.removeAllEffects();
			dev.elsewhere.greenhaze_hush.FxPrefs.set(caster.getUUID(), true, true);
			for (int i = 0; i < 30; i++) dev.elsewhere.greenhaze.Haze.applyEffects(caster, 70F);
			boolean nauseaReduced = caster.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
			boolean slowKept = caster.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			check("haze nausea only with screen effects on (reduce motion skips it, slowness stays)", nauseaOn && !nauseaReduced && slowKept,
				"on=" + nauseaOn + " reduced=" + nauseaReduced + " slowness=" + slowKept);
			dev.elsewhere.greenhaze.GreenLeafBridge.forceLoaded(null);
			caster.removeAllEffects();
		} catch (Throwable t) {
			check("haze / screen-effect prefs", false, t.toString());
		}
		phase1c(gh, hu);
		SelfTest2.run(server, gh, hu);
		structuresAndLoot(server, gh, hu);
		liveBuilds(gh);
		SelfTestFit.add(gh, hu);
		gh.getChunk(4, 0);
		gh.getChunk(-4, 0);
		List<Fight> fights = new ArrayList<>();
		fights.add(fight("percolax", gh, new BlockPos(64, gh.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 64, 0), 0), Percolax::buildAndSummon,
			Percolax.BASE_HP, Percolax.ARMOR));
		fights.add(fight("grumblegut", gh, new BlockPos(-64, gh.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -64, 0), 0), Grumblegut::buildAndSummon,
			Grumblegut.BASE_HP, Grumblegut.ARMOR));
		fights.add(fight("auris", hu, new BlockPos(64, 0, 0), Auris::buildAndSummon, Auris.BASE_HP, Auris.ARMOR));
		fights.add(fight("lithara", hu, new BlockPos(-80, -40, 0), Lithara::buildAndSummon, Lithara.BASE_HP, Lithara.ARMOR));

		ServerTickEvents.END_SERVER_TICK.register(s -> {
			tick++;
			for (int i = 0; i < STEPS.size(); i++) if (AT.get(i) == tick) {
				try { STEPS.get(i).run(); } catch (Throwable t) { check("step " + i, false, t.toString()); GreenhazeHushContent.LOGGER.error("selftest step", t); }
			}
			if (tick == 1320) finish(s);
		});
	}

	interface Summoner { BossEntity summon(ServerLevel level, BlockPos pos); }

	static Fight fight(String id, ServerLevel level, BlockPos pos, Summoner summoner, double baseHp, double armor) {
		BossEntity[] boss = new BossEntity[1];
		FakePlayer[] fp = new FakePlayer[1];
		int base = 20 + 300 * (id.equals("percolax") ? 0 : id.equals("grumblegut") ? 1 : id.equals("auris") ? 2 : 3);
		at(base - 10, () -> {
			for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) level.setChunkForced((pos.getX() >> 4) + cx, (pos.getZ() >> 4) + cz, true);
		});
		at(base, () -> {
			BlockPos p0 = pos;
			level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
			if (level.dimension().equals(Greenhaze.LEVEL)) p0 = new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
			boss[0] = summoner.summon(level, p0);
			fp[0] = FakePlayer.get(level);
			fp[0].snapTo(boss[0].getX() + 3, boss[0].getY(), boss[0].getZ() + 3, 0F, 0F);
			check(id + " spawned", boss[0].isAlive(), boss[0].blockPosition().toShortString());
			check(id + " base HP", Math.abs(boss[0].getAttribute(Attributes.MAX_HEALTH).getBaseValue() - baseHp) < 0.01, "" + boss[0].getMaxHealth());
			check(id + " armor", Math.abs(boss[0].getAttributeValue(Attributes.ARMOR) - armor) < 0.01, "" + boss[0].getAttributeValue(Attributes.ARMOR));
			check(id + " arena sealed", boss[0].arena() != null && boss[0].arena().isSealed() && boss[0].arena().sealBlockCount() > 0,
				boss[0].arena() == null ? "no arena" : boss[0].arena().sealBlockCount() + " seal blocks");
		});
		// bal-boss-punish-window: attackDone opens this boss's own window; the multiplier is its table bonus
		at(base + 6, () -> {
			String atk = switch (id) { case "percolax" -> "big_rip"; case "grumblegut" -> "charge"; case "auris" -> "sonic"; default -> "psalm"; };
			boss[0].attackDone(atk, 1);
		});
		at(base + 9, () -> {
			var op = dev.elsewhere.boss.kit.Openings.get(id, switch (id) { case "percolax" -> "big_rip"; case "grumblegut" -> "charge"; case "auris" -> "sonic"; default -> "psalm"; });
			check(id + " opening opens after its attack: damage x" + (1 + op.bonus()), Math.abs(boss[0].openingMultiplier() - (1 + op.bonus())) < 0.001, "" + boss[0].openingMultiplier());
		});
		// overpower guard: a 100-damage hit becomes 24 + 38 = 62 before multipliers
		at(base + 5, () -> {
			check(id + " overpower guard", Math.abs(BossDamage.overpowerGuard(100F) - 62F) < 0.01, "" + BossDamage.overpowerGuard(100F));
			float capped = BossDamage.lethalGuard(fp[0], BossDamage.source(level, boss[0]), 1000F);
			check(id + " lethal-hit guard", capped <= fp[0].getMaxHealth() * 0.6F + 0.01F, "1000 after armor -> " + capped);
		});
		// drive phases: every 2 ticks, hit it toward the next threshold (bosses gate damage, so keep trying)
		int[] maxPhase = {0};
		for (int t = base + 10; t < base + 250; t += 2) {
			at(t, () -> {
				BossEntity b = boss[0];
				if (b == null || !b.isAlive()) return;
				maxPhase[0] = Math.max(maxPhase[0], b.phase());
				if (b.inTransition()) return;
				beat(level, b, fp[0], Math.max(0F, b.nextThreshold() - 0.03F));
			});
		}
		at(base + 255, () -> {
			check(id + " reached phase 2", maxPhase[0] >= 1, "max phase index " + maxPhase[0]);
			check(id + " reached phase 3", maxPhase[0] >= 2, "max phase index " + maxPhase[0]);
		});
		at(base + 262, () -> {
			check(id + " died", !boss[0].isAlive(), "health=" + boss[0].getHealth());
			var box = new net.minecraft.world.phys.AABB(boss[0].arenaCenter(), boss[0].arenaCenter()).inflate(24);
			var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
			var orbs = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box);
			int xp = 0;
			for (var o : orbs) xp += o.getValue();
			String sig = switch (id) { case "percolax" -> "phasewalker_greaves"; case "grumblegut" -> "challengers_cleaver"; case "auris" -> "hushstep_greaves"; default -> "choir_vestment"; };
			boolean hasSig = drops.stream().anyMatch(d -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(d.getItem().getItem()).getPath().equals(sig));
			check(id + " dropped loot on the ground, incl. its guaranteed first-kill signature item (" + sig + ")", hasSig && boss[0].lastLootRecipients >= 1, drops.size() + " item entities, recipients=" + boss[0].lastLootRecipients);
			check(id + " always drops XP (at least 400)", xp >= 400, "xp " + xp);
			check(id + " fight record is cleared on a win (no refund)", !dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightActive(boss[0].getUUID()), "");
			// drop rates through the real rollLoot (first kill: signature always; repeat: signature about 67%)
			int sigFirst = 0, sigRepeat = 0;
			for (int i = 0; i < 150; i++) {
				if (boss[0].rollLoot(fp[0], true).stream().anyMatch(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(sig))) sigFirst++;
				if (boss[0].rollLoot(fp[0], false).stream().anyMatch(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(sig))) sigRepeat++;
			}
			check(id + " drop rule: signature 100% on a first kill, about 67% afterwards", sigFirst == 150 && sigRepeat > 150 * 0.55 && sigRepeat < 150 * 0.79, sigFirst + "/150 first, " + sigRepeat + "/150 repeat");
			check(id + " arena released", boss[0].arena() == null || !boss[0].arena().isSealed(), "");
		});
		if (id.equals("grumblegut")) {
			at(base + 270, () -> {
				BossEntity b = summoner.summon(level, pos);
				BlockPos mark = pos.above(3);
				level.setBlock(mark, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
				b.resetFight(level);
				check("arena reset restores blocks and removes the boss", !level.getBlockState(mark).is(Blocks.GOLD_BLOCK) && b.isRemoved(), "");
				// bal-boss-retry: a wipe queues the summon item back to the summoner 30 s later
				var kit = dev.elsewhere.boss.kit.KitStore.of(level.getServer());
				int refundsBefore = kit.data().refunds.size();
				BossEntity b2 = summoner.summon(level, pos);
				b2.setSummonItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 64));
				b2.damageCooldownTime = 0;
				b2.hurtServer(level, level.damageSources().playerAttack(fp[0]), 1F);
				boolean recorded = kit.fightActive(b2.getUUID());
				b2.resetFight(level);
				var refund = kit.data().refunds.size() > refundsBefore ? kit.data().refunds.get(kit.data().refunds.size() - 1) : null;
				check("bal-boss-retry: a wipe queues the 64 bread back to the summoner after " + dev.elsewhere.boss.kit.BossKit.RETRY_DELAY_TICKS + " ticks",
					recorded && refund != null && refund.item.equals("minecraft:bread") && refund.count == 64 && refund.player.equals(fp[0].getUUID().toString())
					&& refund.due - kit.now() == dev.elsewhere.boss.kit.BossKit.RETRY_DELAY_TICKS && !kit.fightActive(b2.getUUID()), refund == null ? "no refund" : refund.item + " x" + refund.count);
				kit.data().refunds.clear();
			});
		}
		return new Fight(id, boss, fp);
	}

	/** Deal player damage until the boss is at or below the target fraction (respects its gating by retrying). */
	static void beat(ServerLevel level, BossEntity boss, FakePlayer p, float targetFrac) {
		if (boss == null || !boss.isAlive()) return;
		for (int i = 0; i < 60 && boss.isAlive() && boss.healthFraction() > targetFrac; i++) {
			boss.damageCooldownTime = 0;
			if (!boss.inTransition()) boss.hurtServer(level, level.damageSources().playerAttack(p), 20F);
			else break;
		}
	}

	/** Rolls an elsewhere loot table n times; returns {rolls containing itemId, largest stack of itemId}. */
	static int[] lootRolls(ServerLevel level, net.minecraft.world.entity.Entity e, String table, String itemId, int n) {
		var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, dev.elsewhere.greenhaze_hush.Reg.id(table));
		var lt = level.getServer().reloadableRegistries().getLootTable(key);
		var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, e)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, e.position())
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
		int hits = 0, max = 0;
		for (int i = 0; i < n; i++) {
			int got = 0;
			for (var st : lt.getRandomItems(params)) if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)) got += st.getCount();
			if (got > 0) hits++;
			max = Math.max(max, got);
		}
		return new int[]{hits, max};
	}

	/** 2.0 phase 1c: map trades, Swift Sneak book, new survival sources generate in fresh chunks. */
	static void phase1c(ServerLevel gh, ServerLevel hu) {
		try {
			var bud = dev.elsewhere.greenhaze.GreenhazeEntities.BUDTENDER.create(gh, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			bud.snapTo(0.5, 100, 0.5);
			var bo = bud.getOffers();
			long nonMap = bo.stream().filter(o -> !o.getResult().is(net.minecraft.world.item.Items.FILLED_MAP)).count();
			long maps = bo.size() - nonMap;
			check("Budtender trades: " + nonMap + " goods + " + maps + " explorer map(s)", nonMap >= 12 && bo.stream().noneMatch(o -> o.getResult().isEmpty()), "");
			check("Budtender sells Giant Fern and Lucid Lily for Haze Resin", bo.stream().anyMatch(o -> o.getResult().is(dev.elsewhere.greenhaze.GreenhazeBlocks.GIANT_FERN.asItem()))
				&& bo.stream().anyMatch(o -> o.getResult().is(dev.elsewhere.greenhaze.GreenhazeBlocks.LUCID_LILY.asItem())), "");
			var arc = dev.elsewhere.hush.HushEntities.MUTE_ARCHIVIST.create(hu, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			arc.snapTo(0.5, 0, 0.5);
			var ao = arc.getOffers();
			var sneak = ao.stream().filter(o -> o.getResult().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)).findFirst();
			check("Mute Archivist: Swift Sneak book costs 20 Resonant Shards, once per restock", sneak.isPresent() && sneak.get().getBaseCostA().count() == 20 && sneak.get().getMaxUses() == 1, "");
			bud.discard();
			arc.discard();
		} catch (Throwable t) {
			check("phase 1c trades", false, t.toString());
		}
		try {
			final int B = 2000 + new java.util.Random().nextInt(20000); // fresh chunks every run (the dev world persists)
			java.util.Set<String> biomes = new java.util.TreeSet<>();
			int ore = 0, fern = 0, tree = 0, bush = 0, vent = 0, lily = 0;
			for (int[] o : new int[][] {{B, B}, {-B - 40, B}, {B, -B - 40}, {-B - 40, -B - 40}, {0, B + 400}, {B + 400, 0}}) for (int cx = o[0]; cx < o[0] + 8; cx++) for (int cz = o[1]; cz < o[1] + 8; cz++) {
				var c = gh.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
				biomes.add(describe(gh, c, cx, cz));
				for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
					int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
					for (int y = Math.max(-60, top - 8); y <= top + 12; y++) {
						var b = c.getBlockState(new BlockPos(x, y, z)).getBlock();
						if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.GIANT_FERN) fern++;
						else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.KUSHWOOD_LOG) tree++;
						else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.MUNCHFRUIT_BUSH) bush++;
						else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.HAZE_VENT) vent++;
						else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.LUCID_LILY) lily++;
					}
					for (int y = -60; y < Math.min(top, 100); y += 1) if (c.getBlockState(new BlockPos(x, y, z)).getBlock() == dev.elsewhere.greenhaze.GreenhazeBlocks.TRICHOME_QUARTZ_ORE) ore++;
				}
			}
			for (String bn : new String[] {"munchie_groves", "hazebottom_swamp"}) {
				if (bn.equals("munchie_groves") ? bush != 0 : vent != 0) continue;
				// rare biomes (Munchie Groves for bushes, Hazebottom Swamp for vents): go and find one like /locate biome and count there
				final String biomeName = bn;
				var mg = gh.findClosestBiome3d(h -> h.is(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, dev.elsewhere.greenhaze_hush.Reg.id(biomeName))),
					new BlockPos(B, 70, B), 6400, 32, 64);
				if (mg != null) {
					biomes.add(bn + "@" + mg.getFirst().toShortString());
					for (int cx = (mg.getFirst().getX() >> 4) - 4; cx <= (mg.getFirst().getX() >> 4) + 4; cx++) for (int cz = (mg.getFirst().getZ() >> 4) - 4; cz <= (mg.getFirst().getZ() >> 4) + 4; cz++) {
						var c = gh.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
						for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
							int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
							for (int y = Math.max(-60, top - 8); y <= top + 12; y++) {
								var b = c.getBlockState(new BlockPos(x, y, z)).getBlock();
								if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.MUNCHFRUIT_BUSH) bush++;
								else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.HAZE_VENT) vent++;
								else if (b == dev.elsewhere.greenhaze.GreenhazeBlocks.LUCID_LILY) lily++;
							}
						}
					}
				}
			}
			String d = "ore " + ore + ", fern " + fern + ", kushwood log " + tree + ", munchfruit " + bush + ", vent " + vent + ", lily " + lily + " in 384 fresh Greenhaze chunks " + biomes;
			check("Greenhaze generates Trichome Quartz Ore", ore > 0, d);
			check("Greenhaze generates Giant Ferns and Kushwood trees", fern > 0 && tree > 0, d);
			check("Greenhaze generates Munchfruit bushes, Haze Vents and Lucid Lilies", bush > 0 && vent > 0 && lily > 0, d);
			int echo = 0;
			for (int cx = B; cx < B + 8; cx++) for (int cz = B; cz < B + 8; cz++) {
				var c = hu.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
				for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = hu.getMinY(); y < hu.getMaxY(); y += 1)
					if (c.getBlockState(new BlockPos(x, y, z)).getBlock() == dev.elsewhere.hush.HushBlocks.ECHO_CRYSTAL_ORE) echo++;
			}
			check("The Hush generates Echo Crystal Ore", echo > 0, echo + " in 64 fresh chunks");
		} catch (Throwable t) {
			check("phase 1c worldgen", false, t.toString());
		}
	}

	static String describe(ServerLevel gh, net.minecraft.world.level.chunk.ChunkAccess c, int cx, int cz) {
		int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
		String biome = gh.getBiome(new BlockPos(cx * 16 + 8, top, cz * 16 + 8)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
		return biome;
	}

	static void finish(MinecraftServer server) {
		GreenhazeHushContent.LOGGER.info("[selftest] TOTAL {} passed, {} failed", pass, fail);
		if (Boolean.getBoolean("greenhaze_hush.selftest.exit")) server.halt(false);
	}

	/** The two new Greenhaze structures built live on forced chunks: the market has stalls, chests, a campfire and a Budtender; the farm has crops, water, ferns and its chest. */
	static void liveBuilds(ServerLevel gh) {
		BlockPos o = new BlockPos(-320, 100, 320);
		at(8, () -> { for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) gh.setChunkForced((o.getX() >> 4) + cx, (o.getZ() >> 4) + cz, true); });
		at(12, () -> {
			gh.getChunk(o.getX() >> 4, o.getZ() >> 4);
			dev.elsewhere.greenhaze.GreenhazeMarket.MUNCHIE_MARKET.build(dev.elsewhere.greenhaze_hush.world.Sink.live(gh), o, net.minecraft.util.RandomSource.create(1));
		});
		at(16, () -> {
			int chest = 0, lootChest = 0, campfire = 0, wool = 0, barrel = 0;
			for (BlockPos q : BlockPos.betweenClosed(o.offset(-14, -2, -14), o.offset(14, 6, 14))) {
				var st = gh.getBlockState(q);
				if (st.is(Blocks.CHEST)) { chest++; if (gh.getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c && c.getLootTable() != null) lootChest++; }
				else if (st.is(Blocks.CAMPFIRE)) campfire++;
				else if (st.is(net.minecraft.tags.BlockTags.WOOL)) wool++;
				else if (st.is(Blocks.BARREL)) barrel++;
			}
			int bud = gh.getEntitiesOfClass(dev.elsewhere.greenhaze.GreenhazeMobs.Budtender.class, new net.minecraft.world.phys.AABB(o).inflate(16)).size();
			check("Munchie Market builds: 2 loot chests, a campfire, 4 striped stalls, a Budtender", chest == 2 && lootChest == 2 && campfire == 1 && wool >= 60 && bud >= 1,
				chest + " chests (" + lootChest + " with loot), " + campfire + " campfire, " + wool + " wool, " + barrel + " barrels, " + bud + " Budtender");
			gh.getEntitiesOfClass(dev.elsewhere.greenhaze.GreenhazeMobs.Budtender.class, new net.minecraft.world.phys.AABB(o).inflate(16)).forEach(net.minecraft.world.entity.Entity::discard);
			dev.elsewhere.greenhaze.GreenhazeMarket.FERN_TERRACE_FARMS.build(dev.elsewhere.greenhaze_hush.world.Sink.live(gh), o, net.minecraft.util.RandomSource.create(2));
		});
		at(20, () -> {
			int farm = 0, crops = 0, water = 0, ferns = 0, bushes = 0, lootChest = 0;
			for (BlockPos q : BlockPos.betweenClosed(o.offset(-15, -2, -15), o.offset(15, 8, 15))) {
				var st = gh.getBlockState(q);
				if (st.is(Blocks.FARMLAND)) farm++;
				else if (st.is(Blocks.WHEAT) || st.is(Blocks.CARROTS) || st.is(Blocks.POTATOES)) crops++;
				else if (st.is(Blocks.WATER)) water++;
				else if (st.is(Blocks.LARGE_FERN)) ferns++;
				else if (st.is(dev.elsewhere.greenhaze.GreenhazeBlocks.MUNCHFRUIT_BUSH)) bushes++;
				else if (st.is(Blocks.CHEST) && gh.getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c && c.getLootTable() != null) lootChest++;
			}
			check("Fern Terrace Farms builds: farmland with ripe crops, water channels, ferns, munchfruit and a loot chest", farm >= 60 && crops >= 60 && water >= 6 && ferns >= 2 && bushes >= 1 && lootChest == 1,
				farm + " farmland, " + crops + " crops, " + water + " water, " + ferns + " ferns, " + bushes + " bushes, " + lootChest + " chest");
			for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) gh.setChunkForced((o.getX() >> 4) + cx, (o.getZ() >> 4) + cz, false);
		});
	}

	/** polish-structures: every structure's loot chests carry loot tables that roll something; the new structures have chests and a Budtender. */
	static void structuresAndLoot(MinecraftServer server, ServerLevel gh, ServerLevel hu) {
		try {
			final int fresh = 40000 + new java.util.Random().nextInt(40000);
			String[][] all = {{"greenhaze", "great_hotbox"}, {"greenhaze", "bonfire_terrace"}, {"greenhaze", "lucid_spring"}, {"greenhaze", "sesh_hut"}, {"greenhaze", "munchie_market"},
				{"greenhaze", "fern_terrace_farms"}, {"the_hush", "listening_engine"}, {"the_hush", "choir_geode"}, {"the_hush", "silent_monastery"}, {"the_hush", "hanging_undercity"}};
			for (String[] st : all) {
				ServerLevel l = st[0].equals("greenhaze") ? gh : hu;
				var reg = l.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
				var holder = reg.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, dev.elsewhere.greenhaze_hush.Reg.id(st[1])));
				// a fresh area every run (the dev world persists, so near the origin the structures were built by older code)
				var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, net.minecraft.core.HolderSet.direct(holder), new BlockPos(fresh, 0, fresh), 100, false);
				if (found == null) { check("loot chests of " + st[1], false, "structure not found"); continue; }
				BlockPos at = found.getFirst();
				int chests = 0, looted = 0;
				for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
					var chunk = l.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
					for (var be : chunk.getBlockEntities().values()) {
						if (!(be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c)) continue;
						chests++;
						if (c.getLootTable() != null && c.getLootTable().identifier().getPath().startsWith("chests/")) looted++;
					}
				}
				check("structure " + st[1] + " generates with loot chests that carry loot tables", looted >= 1, looted + " looted of " + chests + " containers near " + at.toShortString());
			}
			// every chest loot table of this module rolls something valid; the Hush ones carry the Hushweave template about half the time
			var caster = FakePlayer.get(gh, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "looter"));
			for (String t : new String[] {"great_hotbox", "bonfire_terrace", "lucid_spring", "sesh_hut", "munchie_market", "fern_terrace_farms", "listening_engine", "choir_geode", "silent_monastery", "hanging_undercity"}) {
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, dev.elsewhere.greenhaze_hush.Reg.id("chests/" + t));
				var lt = server.reloadableRegistries().getLootTable(key);
				var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(gh)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, caster.position())
					.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
				int nonEmpty = 0, tmpl = 0;
				for (int i = 0; i < 200; i++) {
					var items = lt.getRandomItems(params);
					if (!items.isEmpty()) nonEmpty++;
					if (items.stream().anyMatch(s -> s.is(dev.elsewhere.hush.HushItems.HUSHWEAVE_TEMPLATE))) tmpl++;
				}
				boolean templateTable = t.equals("listening_engine") || t.equals("choir_geode") || t.equals("silent_monastery") || t.equals("hanging_undercity");
				check("loot table chests/" + t + " rolls loot" + (templateTable ? " and the Hushweave template about half the time" : ""), nonEmpty == 200 && (!templateTable || (tmpl > 70 && tmpl < 130)),
					nonEmpty + "/200 non-empty" + (templateTable ? ", template " + tmpl + "/200" : ""));
			}
		} catch (Throwable t) {
			check("structures and loot", false, t.toString());
			GreenhazeHushContent.LOGGER.error("structures and loot", t);
		}
	}
}
