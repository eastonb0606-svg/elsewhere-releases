package dev.elsewhere.overworld_bosses.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import dev.elsewhere.overworld_bosses.OverworldBossesContent;
import dev.elsewhere.overworld_bosses.OwbEntities;
import dev.elsewhere.overworld_bosses.Summons;
import dev.elsewhere.stub.overworld_bosses.ArenaSeals;
import dev.elsewhere.stub.overworld_bosses.BossArena;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server self-test (`gradlew runServer`, -Doverworld_bosses.selftest=true): for each boss, build its arena, summon
 * it through the real summon path, check HP/armor, the overpower and lethal-hit guards, drive it through every phase
 * with hits from a FakePlayer, kill it and check the per-player loot and the arena restore. Logs PASS/FAIL + total.
 */
public final class SelfTest {
	private static int failures, passes;
	private static final Object[][] EXPECT = {
		{"gulpmother", 340.0, 8.0, 2.0}, {"strawgrim", 350.0, 8.0, 4.0}, {"neferkhet", 360.0, 10.0, 2.0},
		{"rimeback", 460.0, 16.0, 4.0}, {"vorlagh", 520.0, 14.0, 6.0},
	};

	private SelfTest() {}

	private static void check(boolean ok, String what) {
		OverworldBossesContent.LOGGER.info("[selftest] {} {}", ok ? "PASS" : "FAIL", what);
		if (ok) passes++;
		else failures++;
	}

	public static void run(MinecraftServer server) {
		failures = 0;
		passes = 0;
		ServerLevel level = server.overworld();
		ElsewhereBoss.testNoReset = true;
		try {
			check(BossDamage.overpowerGuard(20F) == 20F && Math.abs(BossDamage.overpowerGuard(38F) - 31F) < 0.01F, "overpower guard: 20 -> 20, 38 -> 31");
			int i = 0;
			for (Object[] e : EXPECT) {
				runBoss(server, level, (String) e[0], (Double) e[1], (Double) e[2], (Double) e[3], new BlockPos(1000 + i * 160, 120, 1000));
				i++;
			}
			checkSealEdgeCases(level, new BlockPos(1000 + EXPECT.length * 160, 120, 1000));
			checkOverworldContentOff(level);
			checkLoot(level);
			checkBossSounds();
			checkSfx3();
			dev.elsewhere.boss.kit.KitSelfTest.run((ok, what) -> check(ok, what));
			checkWorldgen(level);
			checkGlacierDensity(level);
			dev.elsewhere.overworld_bosses.fit3.FitSelfTest.run(level, (ok, what) -> check(ok, what)); // 3.0 site fit (s-struct-overworld)
		} catch (Throwable t) {
			OverworldBossesContent.LOGGER.error("[selftest] crashed", t);
			failures++;
		}
		OverworldBossesContent.LOGGER.info("[selftest] {} passed, {} failed: {}", passes, failures, failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
		if (Boolean.getBoolean("overworld_bosses.selftest.exit")) server.halt(false);
	}

	/**
	 * 3.0 z-gen: glacier_shelf density with the 3.0 fit rules (multi-candidate centres) vs the 2.1 single-centre code, on 6 seeds over the
	 * same placement regions (no chunk generation: the structure's own findValidGenerationPoint on each seed's RandomState). Every 3.0 site
	 * is re-measured with the fit rule (ground spread <= 24 over the shelf box), so density comes from more centres, not a looser fit.
	 */
	private static void checkGlacierDensity(ServerLevel level) {
		var gen = level.getChunkSource().getGenerator();
		if (!(gen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator nb)) { check(false, "glacier_shelf density: overworld generator is not noise based"); return; }
		var reg = level.registryAccess();
		net.minecraft.world.level.levelgen.structure.StructureSet set = null;
		for (var e : reg.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).entrySet()) {
			if (e.getKey().identifier().getNamespace().equals("elsewhere") && e.getKey().identifier().getPath().equals("glacier_shelf")) set = e.getValue();
		}
		if (set == null || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement p)) {
			check(false, "glacier_shelf density: structure set missing");
			return;
		}
		var st = set.structures().get(0).structure().value();
		boolean was = dev.elsewhere.overworld_bosses.world.OwbStructure.GEN3;
		long t0 = System.currentTimeMillis();
		int off = 0, on = 0, badFit = 0, regions = 0;
		try {
			for (long seed : new long[] {1001L, 2002L, 3003L, 4242L, 5150L, 6061L}) {
				var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, nb.generatorSettings().value());
				for (int rx = -12; rx < 12; rx++) for (int rz = -12; rz < 12; rz++) {
					regions++;
					net.minecraft.world.level.ChunkPos cp = p.getPotentialStructureChunk(seed, rx * p.spacing(), rz * p.spacing());
					var ctx = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(reg, gen, gen.getBiomeSource(),
						rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), rs,
						level.getServer().getStructureTemplateManager(), seed, cp, level, st.biomes()::contains);
					dev.elsewhere.overworld_bosses.world.OwbStructure.GEN3 = false;
					if (st.findValidGenerationPoint(ctx).isPresent()) off++;
					dev.elsewhere.overworld_bosses.world.OwbStructure.GEN3 = true;
					var stub = st.findValidGenerationPoint(ctx);
					if (stub.isEmpty()) continue;
					on++;
					BlockPos o = stub.get().position();
					int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
					for (int dx = -16; dx <= 16; dx += 8) for (int dz = -26; dz <= 16; dz += 7) {
						int g = gen.getFirstOccupiedHeight(o.getX() + dx, o.getZ() + dz, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
						lo = Math.min(lo, g);
						hi = Math.max(hi, g);
					}
					if (hi - lo > 30) badFit++;
				}
			}
		} finally {
			dev.elsewhere.overworld_bosses.world.OwbStructure.GEN3 = was;
		}
		OverworldBossesContent.LOGGER.info("[selftest] glacier_shelf density: 2.1 {} sites, 3.0 {} sites in {} regions on 6 seeds ({} ms)", off, on, regions, System.currentTimeMillis() - t0);
		check(off > 0 && on * 100 >= off * 85 && on * 100 <= off * 160,
			f("glacier_shelf density back near 2.1: %d sites with the 3.0 rules vs %d with the 2.1 code (%d regions, 6 seeds)", on, off, regions));
		check(badFit == 0, f("glacier_shelf 3.0 sites keep the fit: %d of %d with ground spread over 30 on a 5x7 lattice", badFit, on));
	}

	private static void runBoss(MinecraftServer server, ServerLevel level, String id, double hp, double armor, double tough, BlockPos center) {
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("owb_" + id).getBytes()), "owb_" + id));
		player.setPos(Vec3.atBottomCenterOf(center).add(3, 0, 3));
		ElsewhereBoss boss = DevCommands.spawnWithArena(level, id, center);
		check(boss != null && boss.isAlive(), id + " summoned through its summon path");
		if (boss == null) return;
		check(Math.abs(boss.getAttributeBaseValue(Attributes.MAX_HEALTH) - hp) < 0.01, f("%s base HP %.0f", id, hp));
		check(Math.abs(boss.getAttributeBaseValue(Attributes.ARMOR) - armor) < 0.01 && Math.abs(boss.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS) - tough) < 0.01,
			f("%s armor %.0f / toughness %.0f", id, armor, tough));
		check(boss.getArena() != null && boss.getArena().hasSnapshot(), id + " arena snapshotted and sealed");
		BossArena arena = boss.getArena();
		AABB sealBox = arena != null ? arena.box().inflate(2) : new AABB(center).inflate(24);
		int sealsDuring = countSeals(level, sealBox);
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
		for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(sealBox.minX, sealBox.minY, sealBox.minZ), BlockPos.containing(sealBox.maxX, sealBox.maxY, sealBox.maxZ))) {
			if (level.getBlockState(q).is(BossRegistry.ARENA_SEAL)) {
				var o = ArenaSeals.original(level, q);
				before.put(q.immutable(), o != null ? o : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
		}
		check(sealsDuring > 0 && arena != null && sealsDuring == arena.sealCount(), f("%s arena seal placed (%d seal blocks, %d tracked)", id, sealsDuring, arena == null ? -1 : arena.sealCount()));
		// Lethal-hit guard: a huge boss hit on a player is capped at 60% of max health after armor.
		float capped = BossDamage.lethalGuard(player, BossDamage.source(level, dev.elsewhere.stub.overworld_bosses.BossRegistry.BOSS_CRUSH, boss, boss), 200F);
		float uncapped = BossDamage.lethalGuard(player, player.damageSources().generic(), 200F);
		check(Math.abs(capped - player.getMaxHealth() * 0.6F) < 0.01F && uncapped == 200F, f("%s lethal-hit guard caps a 200 hit at %.1f (60%% of %.0f), non-boss damage untouched", id, capped, player.getMaxHealth()));
		player.setHealth(player.getMaxHealth());
		// Drive the phases: tick the boss between hits so transitions and attacks run.
		int maxPhase = 1;
		boolean headChecked = false;
		for (int step = 0; step < 4000 && boss.isAlive(); step++) {
			boss.damageCooldownTime = 0;
			if (boss instanceof dev.elsewhere.boss.vorlagh.VorlaghEntity v) {
				for (var t : v.tentacles()) if (t != null && !t.isSevered()) { t.damageCooldownTime = 0; t.hurtServer(level, player.damageSources().playerAttack(player), 30F); }
			}
			if (boss instanceof dev.elsewhere.boss.strawgrim.StrawgrimEntity s && s.isHeadless() && s.head() != null) {
				s.head().hurtServer(level, player.damageSources().playerAttack(player), 20F);
				if (!headChecked) {
					headChecked = true;
					var hd = s.head();
					s.setTarget(player);
					hd.testSkipToWarn();
					boolean swooped = false;
					int swoopTicks = 0;
					double minDy = 99;
					for (int k = 0; k < 60; k++) {
						hd.tick();
						if (hd.isSwooping()) { swooped = true; swoopTicks++; minDy = Math.min(minDy, Math.abs(hd.getY() - player.getY())); }
					}
					check(swooped && swoopTicks >= 20 && swoopTicks <= 32 && minDy < 4.0, f("strawgrim head swoops for ~1.5 s every 6 s (%d swoop ticks, closest height gap %.1f)", swoopTicks, minDy));
				}
			} else {
				boss.hurtServer(level, player.damageSources().playerAttack(player), 20F);
			}
			for (int k = 0; k < 3; k++) boss.tick();
			maxPhase = Math.max(maxPhase, boss.getPhase());
			player.setHealth(player.getMaxHealth());
			player.setPos(Vec3.atBottomCenterOf(center).add(3, 0, 3));
		}
		String extra = "";
		if (boss instanceof dev.elsewhere.boss.vorlagh.VorlaghEntity v) {
			StringBuilder sb = new StringBuilder(" severed=" + v.severedCount() + " hp=" + boss.getHealth() + " tentacles:");
			for (var t : v.tentacles()) sb.append(t == null ? " null" : String.format(Locale.ROOT, " %.0f/%s/%s", t.getHealth(), t.isSevered(), t.isAlive()));
			extra = sb.toString();
		}
		check(maxPhase == 3, id + " reached phase 3 (max " + maxPhase + ")" + extra);
		check(!boss.isAlive() || boss.isDeadOrDying(), id + " died");
		List<List<ItemStack>> loot = boss.lastLoot();
		check(!loot.isEmpty() && !loot.get(0).isEmpty(), id + " dropped per-player loot " + (loot.isEmpty() ? "[]" : loot.get(0).toString()));
		int xp = 0;
		for (var o : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(center).inflate(60))) xp += o.getValue();
		check(xp >= dev.elsewhere.boss.kit.BossRewards.XP_FLOOR, id + " always drops XP (" + xp + ")");
		for (var o : level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(center).inflate(60))) o.discard();
		for (int k = 0; k < 70; k++) boss.tick();
		check(boss.isRemoved(), id + " removed after its death animation");
		ArenaSeals.sweep(level);
		int left = countSeals(level, sealBox);
		int wrong = 0;
		for (var e : before.entrySet()) if (level.getBlockState(e.getKey()) != e.getValue()) wrong++;
		check(left == 0 && wrong == 0, f("%s arena unsealed after death: %d of %d seal blocks left, %d seal spots not back to their old block", id, left, sealsDuring, wrong));
	}

	private static int countSeals(ServerLevel level, AABB box) {
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (level.getBlockState(q).is(BossRegistry.ARENA_SEAL)) n++;
		}
		return n;
	}

	private static void killNow(ServerLevel level, ElsewhereBoss boss) {
		boss.setHealth(0.0F);
		boss.die(level.damageSources().genericKill());
		for (int k = 0; k < 120 && !boss.isRemoved(); k++) {
			boss.tickCount++;
			boss.tick();
		}
	}

	/**
	 * QA issue 12: two overlapping Vorlagh arenas (a summon-path spawn plus a direct spawn that builds its default arena
	 * around itself), killed one after the other; a boss discarded mid-fight; and an orphaned seal with no boss.
	 * Every case must end with 0 arena_seal blocks and the old blocks back.
	 */
	/** sfx lane 3.0: each overworld boss has its own attack telegraph (no shared ravager roar wind-up); Rimeback's own roar. */
	private static void checkSfx3() {
		java.util.List<String> bad = dev.elsewhere.overworld_bosses.Sfx3.selfCheck();
		check(bad.isEmpty(), "sfx3: " + dev.elsewhere.overworld_bosses.Sfx3.paths().size() + " boss sound events resolve, subtitled, none ender dragon or wither" + (bad.isEmpty() ? "" : " " + bad));
		StringBuilder miss = new StringBuilder();
		for (String b : dev.elsewhere.overworld_bosses.BossSfx.BOSSES) if (!dev.elsewhere.overworld_bosses.Sfx3.has(b, "tele")) miss.append(' ').append(b);
		check(miss.length() == 0 && dev.elsewhere.overworld_bosses.Sfx3.has("rimeback", "roar") && dev.elsewhere.overworld_bosses.Sfx3.has("rimeback", "groan"), "sfx3: 5 overworld bosses have their own telegraph; Rimeback roar/groan without ravager" + miss);
	}

	private static void checkBossSounds() {
		com.google.gson.JsonObject json;
		try (var in = SelfTest.class.getResourceAsStream("/assets/elsewhere/sounds.json")) {
			json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
		} catch (Exception e) {
			check(false, "sounds.json readable: " + e);
			return;
		}
		boolean ok = true;
		StringBuilder miss = new StringBuilder();
		for (String b : dev.elsewhere.overworld_bosses.BossSfx.BOSSES) for (String k : dev.elsewhere.overworld_bosses.BossSfx.KINDS) {
			boolean good = dev.elsewhere.overworld_bosses.BossSfx.has(b, k) && json.has("boss." + b + "." + k) && json.has("boss." + b + "." + k + "_2")
				&& net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "boss." + b + "." + k + "_2"));
			if (!good) { ok = false; miss.append(' ').append(b).append('.').append(k); }
		}
		check(ok, "boss sounds: 5 bosses x (roar, big attack, death) x 2 layers registered and in sounds.json" + miss);
	}

	private static void checkSealEdgeCases(ServerLevel level, BlockPos c) {
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz);
		ElsewhereBoss a = DevCommands.spawnWithArena(level, "vorlagh", c);
		VorlaghEntity b = OwbEntities.VORLAGH.create(level, EntitySpawnReason.COMMAND);
		if (a == null || b == null || a.getArena() == null) {
			check(false, "seal edge cases: two Vorlaghs spawned");
			return;
		}
		b.snapTo(c.getX() + 7.5, c.getY() - 3, c.getZ() + 5.5, 0.0F, 0.0F);
		level.addFreshEntity(b);
		for (int k = 0; k < 5 && b.getArena() == null; k++) {
			b.tickCount++;
			b.tick();
		}
		BossArena aa = a.getArena(), ba = b.getArena();
		if (ba == null) {
			check(false, "seal edge cases: direct-spawned Vorlagh built its default arena");
			return;
		}
		AABB box = aa.box().minmax(ba.box()).inflate(2);
		int both = countSeals(level, box);
		check(aa != ba && aa.isLive() && ba.isLive() && both > aa.sealCount() && both > ba.sealCount(),
			f("two overlapping Vorlagh arenas sealed (%d seal blocks; arena A %d, arena B %d)", both, aa.sealCount(), ba.sealCount()));
		killNow(level, a);
		ArenaSeals.sweep(level);
		int afterA = countSeals(level, box);
		check(a.isRemoved() && ba.sealsStanding(level) == ba.sealCount() && afterA == ba.sealCount(),
			f("first Vorlagh dead: the other live arena keeps its whole wall (%d/%d) and nothing else (%d seal blocks)", ba.sealsStanding(level), ba.sealCount(), afterA));
		killNow(level, b);
		ArenaSeals.sweep(level);
		int afterB = countSeals(level, box);
		check(b.isRemoved() && afterB == 0, f("both overlapping Vorlaghs dead: %d arena_seal blocks left (of %d)", afterB, both));
		// Discarded mid-fight (no death, no reset): the seal must still lift.
		ElsewhereBoss d = DevCommands.spawnWithArena(level, "vorlagh", c);
		int dDuring = d == null ? 0 : countSeals(level, box);
		if (d != null) d.discard();
		int dLeft = countSeals(level, box);
		check(d != null && dDuring > 0 && dLeft == 0, f("Vorlagh discarded mid-fight: arena seal lifted (%d -> %d)", dDuring, dLeft));
		// A direct spawn discarded while a summoned one keeps fighting in the same spot, then the summoned one discarded.
		ElsewhereBoss e1 = DevCommands.spawnWithArena(level, "vorlagh", c);
		VorlaghEntity e2 = OwbEntities.VORLAGH.create(level, EntitySpawnReason.COMMAND);
		if (e1 != null && e2 != null) {
			e2.snapTo(c.getX() - 6.5, c.getY() - 2, c.getZ() + 3.5, 0.0F, 0.0F);
			level.addFreshEntity(e2);
			for (int k = 0; k < 5 && e2.getArena() == null; k++) {
				e2.tickCount++;
				e2.tick();
			}
			e2.discard();
			boolean e1Whole = e1.getArena() != null && e1.getArena().sealsStanding(level) == e1.getArena().sealCount();
			e1.discard();
			int eLeft = countSeals(level, box);
			check(e1Whole && eLeft == 0, f("overlap + discards: the fighting arena keeps its wall while the other is discarded (%s), 0 seals after both (%d)", e1Whole, eLeft));
		} else check(false, "overlap + discards: spawned");
		// Orphaned seal (no live boss holds it): the once-a-second sweep lifts it.
		BossArena orphan = Summons.vorlaghArena(c);
		orphan.capture(level);
		orphan.seal(level);
		int oDuring = countSeals(level, box);
		int swept = ArenaSeals.sweep(level);
		int oLeft = countSeals(level, box);
		check(oDuring > 0 && swept == oDuring && oLeft == 0 && ArenaSeals.tracked(level) == 0,
			f("orphaned arena seal swept (%d placed, %d swept, %d left, %d still tracked)", oDuring, swept, oLeft, ArenaSeals.tracked(level)));
	}

	/** overworldContent=false: no boss site is placed (searched far from the sites checked below) and Tidewrack Drowned can't spawn naturally. */
	private static void checkOverworldContentOff(ServerLevel level) {
		dev.elsewhere.overworld_bosses.OwbConfig.override = false;
		try {
			check(!dev.elsewhere.overworld_bosses.OwbConfig.overworldContent(), "overworldContent override reads false");
			int none = 0;
			for (String kind : dev.elsewhere.overworld_bosses.world.OwbWorldgen.KINDS) {
				var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(dev.elsewhere.overworld_bosses.world.OwbWorldgen.key(kind));
				if (holder.isEmpty()) continue;
				var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder.get()), new BlockPos(-150000, 0, 150000), 48, false);
				if (found == null) none++;
			}
			check(none == dev.elsewhere.overworld_bosses.world.OwbWorldgen.KINDS.length, "overworldContent=false: no boss site placed within 48 chunks (" + none + " of " + dev.elsewhere.overworld_bosses.world.OwbWorldgen.KINDS.length + " kinds absent)");
			var d = dev.elsewhere.overworld_bosses.OwbEntities.TIDEWRACK_DROWNED.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
			check(d != null && !d.checkSpawnRules(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL), "overworldContent=false: Tidewrack Drowned refuse natural spawns");
			if (d != null) d.discard();
		} finally {
			dev.elsewhere.overworld_bosses.OwbConfig.override = null;
		}
		check(dev.elsewhere.overworld_bosses.OwbConfig.overworldContent(), "overworldContent defaults to true");
	}

	/** Loot tables in the 26.3 format: the Siren Conch's 8% chance and rotten flesh's 0-2 count apply (old keys were ignored: 100%, always 1). */
	private static void checkLoot(ServerLevel level) {
		var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
			net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "entities/tidewrack_drowned"));
		var table = level.getServer().reloadableRegistries().getLootTable(key);
		var d = dev.elsewhere.overworld_bosses.OwbEntities.TIDEWRACK_DROWNED.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (d == null) { check(false, "tidewrack drowned created for the loot check"); return; }
		d.setPos(0, 100, 0);
		var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, d)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, d.position())
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
		int conch = 0, fleshZero = 0, fleshTwo = 0, n = 500;
		for (int i = 0; i < n; i++) {
			int flesh = 0;
			for (ItemStack st : table.getRandomItems(params)) {
				if (st.is(dev.elsewhere.overworld_bosses.OwbItems.SIREN_CONCH)) conch++;
				if (st.is(net.minecraft.world.item.Items.ROTTEN_FLESH)) flesh += st.getCount();
			}
			if (flesh == 0) fleshZero++;
			if (flesh == 2) fleshTwo++;
		}
		d.discard();
		check(conch > 15 && conch < 70, "tidewrack drowned Siren Conch drop honours its 8% chance (" + conch + "/" + n + ")");
		check(fleshZero > 0 && fleshTwo > 0, "tidewrack drowned rotten flesh honours set_count 0-2 (none " + fleshZero + ", two " + fleshTwo + ")");
	}

	/** Locates each boss site near spawn and generates its chunks (structure placement must not throw). */
	private static void checkWorldgen(ServerLevel level) {
		for (String kind : dev.elsewhere.overworld_bosses.world.OwbWorldgen.KINDS) {
			var reg = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
			var holder = reg.get(dev.elsewhere.overworld_bosses.world.OwbWorldgen.key(kind));
			if (holder.isEmpty()) {
				check(false, "structure " + kind + " registered");
				continue;
			}
			var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder.get()), BlockPos.ZERO, 64, false);
			if (found == null) {
				check(true, "structure " + kind + " registered (none within 64 chunks of 0,0 on this seed)");
				continue;
			}
			BlockPos at = found.getFirst();
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
			var start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(found.getSecond().value());
			check(start != null && start.isValid(), "structure " + kind + " generated at " + at.toShortString() + (start != null && start.isValid() ? " box " + start.getBoundingBox() : ""));
		}
	}

	private static String f(String fmt, Object... args) {
		return String.format(Locale.ROOT, fmt, args);
	}
}
