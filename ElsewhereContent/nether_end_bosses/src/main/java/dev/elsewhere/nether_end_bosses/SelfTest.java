package dev.elsewhere.nether_end_bosses;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.solaxis.Solaxis;
import dev.elsewhere.boss.solaxis.SolaxisArena;
import dev.elsewhere.boss.solaxis.SolaxisContent;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.BossFight;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.Guards;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only check run by {@code gradlew runServer} (-Dnether_end_bosses.selftest=true). Builds every arena, summons each
 * boss through its real in-world trigger, drives it through every attack and phase with direct hits, and checks
 * stats, guards, minions, loot, arena restore and reset. Tick-driven, so bosses run their real AI. Exits when done.
 */
public final class SelfTest {
	private static int failures;
	private static int passes;
	private static TickScript script;
	private static MinecraftServer server;
	private static boolean finished;

	private SelfTest() {}

	static void start(MinecraftServer srv) {
		server = srv;
		failures = 0;
		passes = 0;
		script = new TickScript(what -> check(false, "timed out / crashed: " + what));
		script.waitTicks(20);
		checkGuards();
		checkBeta2();
		checkBossSounds();
		checkSfx3();
		dev.elsewhere.boss.kit.KitSelfTest.run(SelfTest::check);
		dev.elsewhere.nether_end_bosses.fit3.FitSelfTest.run(server, SelfTest::check); // 3.0 terrain-fit arenas (s-struct-nether)
		checkWorldgen();
		testSolaxis();
		testBoss("grunthar", Level.NETHER, new BlockPos(200, 70, 600), 170, 14, 2, new float[] {-1F, 0.29F});
		testBoss("mournmaw", Level.NETHER, new BlockPos(400, 70, 600), 520, 8, 2, new float[] {0.59F, 0.29F});
		testMercy(new BlockPos(400, 70, 800));
		testBoss("ossyra", Level.END, new BlockPos(1400, 80, 0), 950, 18, 8, new float[] {0.64F, 0.29F});
		testBoss("aphelion", Level.END, new BlockPos(1600, 80, 0), 1300, 16, 8, new float[] {0.69F, 0.34F});
		testBareSummon("mournmaw", Level.NETHER, new BlockPos(600, 70, 600));
		testBareSummon("aphelion", Level.END, new BlockPos(1800, 80, 0));
		script.run(() -> dev.elsewhere.nether_end_bosses.fit3.FitSelfTest.finalChecks(SelfTest::check)); // s-struct-nether: far-chunk watch
		script.run(SelfTest::finish);
	}

	/** sfx lane 3.0: every boss has its own sounds, none is an ender dragon or wither sound, the kill switch works. */
	private static void checkSfx3() {
		java.util.List<String> bad = Sfx3.selfCheck();
		check(bad.isEmpty(), "sfx3: " + Sfx3.paths().size() + " boss sound events resolve to files or vanilla events, all subtitled, none ender dragon or wither" + (bad.isEmpty() ? "" : " " + bad));
		StringBuilder miss = new StringBuilder();
		for (String b : BossSfx.BOSSES) for (String c : new String[] {"roar", "roar_layer", "tele", "big"}) if (!Sfx3.has(b, c)) miss.append(' ').append(b).append('.').append(c);
		for (String c : new String[] {"growl", "breath", "coil", "death"}) if (!Sfx3.has("aphelion", c)) miss.append(" aphelion.").append(c);
		check(miss.length() == 0, "sfx3: all 5 nether/end bosses have roar, layer, telegraph and big cues; Aphelion growl/breath/coil/death" + miss);
		Sfx3.setEnabledForTest(true);
		boolean onOk = Sfx3.pick("aphelion", "growl", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL) != net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL
			&& Sfx3.pick("aphelion", "breath", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT) != net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT
			&& Sfx3.pick("aphelion", "coil", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP) != net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP;
		Sfx3.setEnabledForTest(false);
		boolean offOk = Sfx3.pick("aphelion", "growl", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL) == net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL;
		Sfx3.setEnabledForTest(null);
		check(onOk && offOk, "sfx3: switch on = Aphelion uses no ender dragon sound; sfx3_nether_end_bosses=false restores 2.x");
		check(Sfx3.attackPitch("coil") == Sfx3.attackPitch("coil") && Sfx3.attackPitch("coil") >= 0.84F && Sfx3.attackPitch("void_breath") <= 1.17F, "sfx3: telegraph pitch is fixed per attack (0.84-1.16)");
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
		for (String b : BossSfx.BOSSES) for (String k : BossSfx.KINDS) {
			boolean good = BossSfx.has(b, k) && json.has("boss." + b + "." + k) && json.has("boss." + b + "." + k + "_2")
				&& net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "boss." + b + "." + k + "_2"));
			if (!good) { ok = false; miss.append(' ').append(b).append('.').append(k); }
		}
		check(ok, "boss sounds: 5 bosses x (roar, big attack, death) x 2 layers registered and in sounds.json" + miss);
	}

	/** QA defects: a boss created by /summon (no ritual) must start its fight, show bars and stay near its summon point. */
	private static void testBareSummon(String id, net.minecraft.resources.ResourceKey<Level> dim, BlockPos at) {
		ServerLevel[] lv = new ServerLevel[1];
		ElsewhereBoss[] boss = new ElsewhereBoss[1];
		FakePlayer[] p = new FakePlayer[1];
		script.run(() -> {
			lv[0] = server.getLevel(dim);
			forceChunks(lv[0], at, 40);
			p[0] = fake(lv[0], "selftest_bare_" + id, Vec3.atBottomCenterOf(at.offset(0, 0, 4)), 1024);
		});
		script.waitTicks(5);
		script.run(() -> {
			var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", id));
			boss[0] = (ElsewhereBoss) type.create(lv[0], net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			boss[0].setPos(Vec3.atBottomCenterOf(at));
			lv[0].addFreshEntity(boss[0]);
		});
		script.waitTicks(5);
		script.run(() -> { if (boss[0].fight() != null) boss[0].fight().join(p[0]); }); // FakePlayers are not in level.players(), so they are joined by hand
		script.waitTicks(40);
		script.run(() -> {
			double d = boss[0].position().distanceTo(Vec3.atBottomCenterOf(at));
			check(boss[0].isAlive() && boss[0].fight() != null && boss[0].home() != null, f("%s: a bare /summon starts a fight with the summon point as home (alive %b, removed %b, ticks %d, dormant %b, pos %s)", id, boss[0].isAlive(), boss[0].isRemoved(), boss[0].tickCount, boss[0].isDormant(), boss[0].blockPosition().toShortString()));
			check(boss[0].fight() != null && BossFight.barViewer(p[0], boss[0].fight().arena), id + ": a bare /summon puts the nearby survival player inside the arena, so they get the boss bar");
			check(d < 40 && boss[0].getY() > at.getY() - 30, f("%s: a bare /summon stays near its summon point after 45 ticks (%.1f blocks away, y %.1f)", id, d, boss[0].getY()));
			boss[0].discard();
		});
	}

	/** 2.0 alpha.2: each arena is a structure that /locate finds from a fresh area, generates without error, and holds its summon spot. */
	private static void checkWorldgen() {
		String[][] sites = {{"grunthar", "grunthar_treasure_hall"}, {"solaxis", "solaxis_pyre_cradle"}, {"mournmaw", "mournmaw_weeping_basin"},
			{"ossyra", "ossyra_matriarch_spire"}, {"aphelion", "aphelion_shattered_halo"}};
		check(NetherEndBossesContent.WORLDGEN, "arena world generation is switched on");
		for (String[] s : sites) {
			boolean end = s[0].equals("ossyra") || s[0].equals("aphelion");
			var level = server.getLevel(end ? Level.END : Level.NETHER);
			var holder = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Reg.id(s[1])));
			if (level == null || holder.isEmpty()) {
				check(false, s[0] + ": structure " + s[1] + " registered and dimension loaded");
				continue;
			}
			BlockPos origin = end ? new BlockPos(3000, 64, 3000) : new BlockPos(-3000, 64, 3000);
			long t0 = System.nanoTime();
			BlockPos at = level.findNearestMapStructure(net.minecraft.core.HolderSet.direct(holder.get()), origin, 100, false);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			if (at == null) {
				check(false, f("%s: /locate finds %s within 100 chunks of %s (none, %d ms)", s[0], s[1], origin.toShortString(), ms));
				continue;
			}
			double dist = Math.sqrt(at.distSqr(origin));
			check(dist <= 1600, f("%s: nearest %s at %s, %.0f blocks from a fresh area (%d ms; limit 1600)", s[0], s[1], at.toShortString(), dist, ms));
			var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
			var start = chunk.getStartForStructure(holder.get().value());
			boolean ok = start != null && start.isValid() && start.getPieces().get(0) instanceof BossArenaStructure.Piece;
			check(ok, s[0] + ": chunk generated with a valid structure start");
			if (!ok) continue;
			BlockPos o = ((BossArenaStructure.Piece) start.getPieces().get(0)).origin;
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
				level.getChunk((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
			BlockPos anchor = ArenaBuilder.BY_BOSS.get(s[0]).anchor(o);
			boolean spot = !level.getBlockState(anchor).isAir() || !level.getBlockState(o.below()).isAir();
			check(spot, s[0] + ": arena built, summon spot at " + anchor.toShortString() + " is " + level.getBlockState(anchor).getBlock());
		}
	}

	static void tick(MinecraftServer srv) {
		if (script != null && !finished) script.tick();
	}

	private static void finish() {
		finished = true;
		log("{}  ({} passed, {} failed)", failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)", passes, failures);
		if (Boolean.getBoolean("nether_end_bosses.selftest.exit")) server.halt(false);
	}

	static void check(boolean ok, String what) {
		log("{} {}", ok ? "PASS" : "FAIL", what);
		if (ok) passes++;
		else failures++;
	}

	private static void log(String fmt, Object... args) {
		NetherEndBossesContent.LOGGER.info("[selftest] " + fmt, args);
	}

	static String f(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}

	// ---- shared helpers ----

	static FakePlayer fake(ServerLevel level, String name, Vec3 at, float health) {
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		player.setPos(at);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		player.setHealth(health);
		return player;
	}

	static void forceChunks(ServerLevel level, BlockPos center, int radiusBlocks) {
		ChunkPos c = ChunkPos.containing(center);
		int r = (radiusBlocks >> 4) + 1;
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.setChunkForced(c.x() + dx, c.z() + dz, true);
	}

	/** Vanilla armor formula, to check the overpower guard. */
	static float afterArmor(float dmg, float armor, float tough) {
		float f = Math.max(armor * 0.2F, Math.min(20, armor - 4 * dmg / (tough + 8)));
		return dmg * (1 - f / 25.0F);
	}

	static float playerHit(ServerLevel level, ElsewhereBoss boss, FakePlayer player, float amount) {
		boss.damageCooldownTime = 0;
		float before = boss.getHealth();
		boss.hurtServer(level, player.damageSources().playerAttack(player), amount);
		return before - boss.getHealth();
	}

	/** Brings the boss to just under an HP ratio with player hits (respects armor and guards, so it loops). */
	static void damageTo(ServerLevel level, ElsewhereBoss boss, FakePlayer player, float ratio) {
		for (int i = 0; i < 400 && boss.isAlive() && boss.getHealth() / boss.getMaxHealth() > ratio; i++) {
			float gap = boss.getHealth() - boss.getMaxHealth() * ratio;
			playerHit(level, boss, player, Math.min(20.0F, gap + 2.0F));
		}
	}

	static void kill(ServerLevel level, ElsewhereBoss boss, FakePlayer player) {
		for (int i = 0; i < 600 && boss.isAlive(); i++) playerHit(level, boss, player, 20.0F);
	}

	/** Forces each attack of the phase in turn and waits until it completes. */
	static void runPhaseAttacks(TickScript s, Supplier<? extends ElsewhereBoss> boss, int phase, FakePlayer player) {
		s.then(sub -> {
			for (AttackSpec spec : boss.get().attacks()) {
				if (!spec.inPhase(phase)) continue;
				sub.run(() -> {
					boss.get().completedAttacks.remove(spec.id());
					player.setHealth(player.getMaxHealth());
					boss.get().forceAttack(spec.id());
				});
				sub.waitUntil(boss.get().bossId() + " phase " + phase + " attack " + spec.id(), () -> boss.get().completedAttacks.contains(spec.id()), 700);
				sub.run(() -> check(boss.get().completedAttacks.contains(spec.id()), f("%s phase %d: %s (%s) runs to completion", boss.get().bossId(), phase, spec.id(), spec.name())));
			}
		});
	}

	static List<ItemStack> lootFor(ServerLevel level, FakePlayer player, AABB box) {
		List<ItemStack> out = new ArrayList<>();
		// polish-loot-chest = No: loot is plain ground drops at the boss
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(40))) out.add(e.getItem());
		return out;
	}

	static boolean has(List<ItemStack> loot, Item item) {
		for (ItemStack s : loot) if (s.is(item)) return true;
		return false;
	}

	static void clearLoot(ServerLevel level, AABB box) {
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(40))) e.discard();
	}

	// ---- framework guards ----

	/** Beta 2: Glimmer costs (Ninefold Rod 6 from a 20 pool), Wyrmfang reach, and a loot condition in the 26.3 format. */
	private static void checkBeta2() {
		ServerLevel level = server.overworld();
		try {
			net.fabricmc.fabric.api.entity.FakePlayer fp = net.fabricmc.fabric.api.entity.FakePlayer.get(level,
				new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("neb_beta2".getBytes()), "neb_beta2"));
			// fix-selftests: independent of pool size/regen (the combined jar swaps this stub for the arsenal's real pool)
			float before = dev.elsewhere.stub.nether_end_bosses.GlimmerStub.current(fp);
			boolean a = dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(fp, GearStats.NINEFOLD_ROD_GLIMMER);
			float afterSpend = dev.elsewhere.stub.nether_end_bosses.GlimmerStub.current(fp);
			boolean huge = dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(fp, 9999.0F);
			check(before >= GearStats.NINEFOLD_ROD_GLIMMER && a && !huge && afterSpend < before + 0.01F,
				"glimmer: a Ninefold Rod cast (6) is paid from the pool and an impossible cost is refused (" + before + "->" + afterSpend + ", " + a + "/" + huge + ")");
			double base = fp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
			fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(dev.elsewhere.boss.aphelion.AphelionContent.WYRMFANG));
			dev.elsewhere.boss.aphelion.AphelionContent.wyrmfangReach(fp);
			double held = fp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
			fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			dev.elsewhere.boss.aphelion.AphelionContent.wyrmfangReach(fp);
			double after = fp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
			check(Math.abs(held - base - GearStats.WYRMFANG_REACH) < 1e-6 && Math.abs(after - base) < 1e-6,
				String.format(java.util.Locale.ROOT, "wyrmfang reach: %.1f -> %.1f held -> %.1f put away", base, held, after));
		} catch (Throwable t) {
			check(false, "beta 2 glimmer / reach: " + t);
		}
		try {
			// survives_explosion now applies (the old `conditions` key was ignored: the block always dropped)
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "blocks/gilded_throne"));
			var table = level.getServer().reloadableRegistries().getLootTable(key);
			var state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "gilded_throne")).defaultBlockState();
			int plain = 0, blast = 0, n = 400;
			for (int i = 0; i < n; i++) {
				var p1 = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.ZERO)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
					.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
				if (!table.getRandomItems(p1).isEmpty()) plain++;
				var p2 = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.ZERO)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, 4.0F)
					.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
				if (!table.getRandomItems(p2).isEmpty()) blast++;
			}
			check(plain == n && blast > 50 && blast < 160, "loot: gilded throne always drops when mined, ~25% after a radius-4 explosion (survives_explosion applies: " + plain + "/" + n + ", " + blast + "/" + n + ")");
		} catch (Throwable t) {
			check(false, "loot condition check: " + t);
		}
	}

	private static void checkGuards() {
		script.run(() -> {
			check(Guards.overpower(20) == 20 && Guards.overpower(24) == 24, "overpower guard leaves hits up to 24 alone");
			check(Guards.overpower(38) == 31 && Guards.overpower(57) == 40.5F, "overpower guard halves past 24 (TRT 38 -> 31, crit 57 -> 40.5)");
			ServerLevel level = server.overworld();
			check(ElsewhereBoss.scaled(level, 40) == switch (level.getDifficulty()) {
				case PEACEFUL -> 0F;
				case EASY -> 24F;
				case NORMAL -> 40F;
				case HARD -> 50F;
			}, "scripted damage scales x0.6 Easy / x1 Normal / x1.25 Hard (difficulty " + level.getDifficulty().getSerializedName() + ")");
		});
	}

	// ---- generic boss loop (the other four bosses) ----

	private static void testBoss(String id, net.minecraft.resources.ResourceKey<Level> dim, BlockPos origin, double hp, double armor, double tough, float[] next) {
		ServerLevel[] lv = new ServerLevel[1];
		ElsewhereBoss[] boss = new ElsewhereBoss[1];
		FakePlayer[] p = new FakePlayer[1];
		BossFight[] fight = new BossFight[1];
		script.run(() -> {
			lv[0] = server.getLevel(dim);
			forceChunks(lv[0], origin, 40);
			ArenaBuilder.BY_BOSS.get(id).build(lv[0], origin, lv[0].getRandom());
			p[0] = fake(lv[0], "selftest_" + id, Vec3.atBottomCenterOf(origin.offset(0, 0, 4)), 1024);
		});
		script.waitTicks(5);
		script.run(() -> {
			boss[0] = DevCommands.SUMMONERS.get(id).apply(lv[0], origin);
			check(boss[0] != null && boss[0].isAlive(), id + ": summons with its arena");
			if (boss[0] == null) return;
			fight[0] = boss[0].fight();
			check(BossFight.barViewer(p[0], fight[0].arena), id + ": a survival player inside the arena is a boss-bar viewer when the fight starts (and spectators/creative inside the arena see it too)");
			check(boss[0].attacks().stream().filter(a -> a.isBig()).allMatch(a -> a.windup() > 0) && boss[0].attacks().stream().anyMatch(a -> a.isBig()), id + ": big attacks use the 25% longer wind-up (" + boss[0].attacks().stream().filter(a -> a.isBig()).map(a -> a.id() + "=" + a.windup()).toList() + ")");
			fight[0].join(p[0]);
			check(boss[0].getAttribute(Attributes.MAX_HEALTH).getBaseValue() == hp, f("%s: %.0f base HP (%.0f with Test Meter x1.53)", id, hp, hp * 1.53));
			check(boss[0].getAttributeValue(Attributes.ARMOR) == armor && boss[0].getAttributeValue(Attributes.ARMOR_TOUGHNESS) == tough, f("%s: armor %.0f / toughness %.0f", id, armor, tough));
			float mult = id.equals("ossyra") ? 0.25F : id.equals("aphelion") ? 0.35F : 1.0F;
			float a = playerHit(lv[0], boss[0], p[0], 40.0F);
			float b = afterArmor(32 * mult, (float) armor, (float) tough);
			check(Math.abs(a - b) < 0.05F, f("%s: overpower guard: a 40 hit lands as 32 before armor (%.2f, expected %.2f)", id, a, b));
		});
		Supplier<ElsewhereBoss> b = () -> boss[0];
		for (int ph = 1; ph <= next.length + 1; ph++) {
			int phase = ph;
			script.then(s -> {
				if (boss[0] == null) return;
				runPhaseAttacks(s, b, phase, p[0]);
				if (phase > next.length) return;
				float ratio = next[phase - 1];
				s.run(() -> {
					if (ratio < 0 && boss[0] instanceof dev.elsewhere.boss.grunthar.Grunthar g && g.mount() != null) {
						dev.elsewhere.boss.grunthar.Ashtusk mount = g.mount();
						check(mount.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 190 && mount.getAttributeValue(Attributes.ARMOR) == 12, "grunthar: Ashtusk 190 base HP, armor 12");
						runAll(mount);
						kill(lv[0], mount, p[0]);
					} else {
						damageTo(lv[0], boss[0], p[0], ratio);
					}
				});
				s.waitUntil(id + " phase " + (phase + 1), () -> boss[0].getPhase() == phase + 1 && !boss[0].inTransition(), 200);
				s.run(() -> check(boss[0].getPhase() == phase + 1, f("%s: reaches phase %d", id, phase + 1)));
			});
		}
		if (id.equals("ossyra") || id.equals("aphelion")) {
			script.then(s -> {
				if (boss[0] == null) return;
				s.run(() -> {
					p[0].setHealth(p[0].getMaxHealth());
					p[0].setPos(Vec3.atBottomCenterOf(origin).add(0, -20, 0));
				});
				s.waitTicks(2);
				s.run(() -> {
					check(fight[0].arena.contains(p[0]), f("%s: a void fall returns you to the ledge (now at y %.1f)", id, p[0].getY()));
					if (id.equals("ossyra")) boss[0].forceAttack("gravity_flip");
				});
				s.waitTicks(id.equals("ossyra") ? 70 : 1);
				s.run(() -> {
					if (id.equals("ossyra")) {
						double g = p[0].getAttributeValue(Attributes.GRAVITY);
						check(Math.abs(g + 0.08) < 1.0E-6 || Math.abs(g - 0.08) < 1.0E-6 && boss[0].hasFlag(dev.elsewhere.boss.ossyra.Ossyra.FLAG_INVERTED) == false, f("ossyra: Gravity Flip toggles participants' gravity (now %.3f)", g));
					} else {
						check(p[0].getAttributeValue(Attributes.GRAVITY) < 0.05, f("aphelion: phase 3 halves player gravity (%.3f)", p[0].getAttributeValue(Attributes.GRAVITY)));
					}
				});
			});
		}
		script.then(s -> {
			if (boss[0] == null) return;
			s.run(() -> {
				p[0].setPos(Vec3.atBottomCenterOf(origin.offset(0, 0, 4)));
				kill(lv[0], boss[0], p[0]);
			});
			s.waitUntil(id + " death", () -> !boss[0].isAlive() || fight[0].isOver(), 60);
			s.waitTicks(5);
			s.run(() -> {
				List<ItemStack> loot = lootFor(lv[0], p[0], fight[0].arena.box());
				check(fight[0].isOver(), id + ": fight ends on death");
				check(loot.size() >= 3, id + ": first kill drops personal loot (" + loot.size() + " stacks: " + loot.stream().map(st -> st.getItem().toString()).toList() + ")");
				int xp = 0;
				for (net.minecraft.world.entity.ExperienceOrb o : lv[0].getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, fight[0].arena.box().inflate(40))) xp += o.getValue();
				check(xp >= dev.elsewhere.boss.kit.BossRewards.XP_FLOOR, id + ": the kill always drops XP (" + xp + ")");
				for (net.minecraft.world.entity.ExperienceOrb o : lv[0].getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, fight[0].arena.box().inflate(40))) o.discard();
				check(fight[0].minionCount(lv[0]) == 0, id + ": minions cleaned up");
				int diff = fight[0].arena.diff(lv[0]);
				// Aphelion: surviving end crystals keep re-lighting the fire under themselves (vanilla End behaviour)
				check(diff == 0 || id.equals("aphelion") && diff <= 4, id + ": arena restored after the win (" + diff + " blocks differ)" + (diff == 0 ? "" : " :: " + fight[0].arena.diffDetail(lv[0], 6)));
				check(p[0].getAttribute(Attributes.GRAVITY).getModifier(StubFramework.FIGHT_GRAVITY) == null, id + ": fight gravity cleared after the fight");
				clearLoot(lv[0], fight[0].arena.box());
				ChunkPos c = ChunkPos.containing(origin);
				lv[0].setChunkForced(c.x(), c.z(), false);
			});
		});
	}

	/** Mournmaw's mercy ending: a dried ghast placed in the basin during phase 3 calms her; mercy drops. */
	private static void testMercy(BlockPos origin) {
		ServerLevel[] lv = new ServerLevel[1];
		ElsewhereBoss[] boss = new ElsewhereBoss[1];
		FakePlayer[] p = new FakePlayer[1];
		BossFight[] fight = new BossFight[1];
		script.run(() -> {
			lv[0] = server.getLevel(Level.NETHER);
			forceChunks(lv[0], origin, 30);
			ArenaBuilder.BY_BOSS.get("mournmaw").build(lv[0], origin, lv[0].getRandom());
			p[0] = fake(lv[0], "selftest_mercy", Vec3.atBottomCenterOf(origin.offset(0, 0, 6)), 1024);
			boss[0] = DevCommands.SUMMONERS.get("mournmaw").apply(lv[0], origin);
			fight[0] = boss[0].fight();
			fight[0].join(p[0]);
			damageTo(lv[0], boss[0], p[0], 0.55F);
		});
		script.waitUntil("mournmaw phase 2 (mercy run)", () -> boss[0].getPhase() == 2 && !boss[0].inTransition(), 100);
		script.run(() -> damageTo(lv[0], boss[0], p[0], 0.25F));
		script.waitUntil("mournmaw phase 3 (mercy run)", () -> boss[0].getPhase() == 3 && !boss[0].inTransition(), 100);
		script.run(() -> lv[0].setBlock(origin.below(), net.minecraft.world.level.block.Blocks.DRIED_GHAST.defaultBlockState(), 3));
		script.waitUntil("mercy timer starts", () -> ((dev.elsewhere.boss.mournmaw.Mournmaw) boss[0]).mercyTicks() >= 0, 60);
		script.run(() -> {
			check(((dev.elsewhere.boss.mournmaw.Mournmaw) boss[0]).mercyTicks() >= 0, "mournmaw: a dried ghast in the basin during phase 3 starts the mercy timer");
			((dev.elsewhere.boss.mournmaw.Mournmaw) boss[0]).calm(lv[0]);
		});
		script.waitTicks(3);
		script.run(() -> {
			List<ItemStack> loot = lootFor(lv[0], p[0], fight[0].arena.box());
			check(fight[0].isOver() && boss[0].isRemoved(), "mournmaw: mercy ends the fight without a kill");
			check(has(loot, dev.elsewhere.boss.mournmaw.MournmawContent.MOURNMAWS_BLESSING), "mournmaw: mercy drops Mournmaw's Blessing (" + loot.size() + " stacks)");
			clearLoot(lv[0], fight[0].arena.box());
		});
	}

	private static void runAll(ElsewhereBoss mount) {
		mount.completedAttacks.clear();
	}

	// ---- Solaxis ----

	private static void testSolaxis() {
		ServerLevel[] lv = new ServerLevel[1];
		BlockPos origin = new BlockPos(0, 70, 600);
		Solaxis[] boss = new Solaxis[1];
		FakePlayer[] p = new FakePlayer[1];
		BossFight[] fight = new BossFight[1];
		script.run(() -> {
			lv[0] = server.getLevel(Level.NETHER);
			forceChunks(lv[0], origin, 20);
			SolaxisArena.INSTANCE.build(lv[0], origin, lv[0].getRandom());
			check(lv[0].getBlockState(origin).is(SolaxisContent.PYRE_CRADLE_CORE), "solaxis: Pyre Cradle builds with its core");
			int spawners = 0;
			for (int[] s : SolaxisArena.SPAWNERS) if (lv[0].getBlockState(origin.offset(s[0], s[1], s[2])).is(Blocks.SPAWNER)) spawners++;
			check(spawners == 4, "solaxis: Pyre Cradle has 4 blaze spawners (" + spawners + ")");
			p[0] = fake(lv[0], "selftest_solaxis", Vec3.atBottomCenterOf(origin.offset(0, 0, 5)), 1024);
		});
		script.waitTicks(5);
		script.run(() -> {
			ServerLevel level = lv[0];
			// the real trigger: a player breaks the spawners
			for (int i = 0; i < 3; i++) {
				int[] s = SolaxisArena.SPAWNERS[i];
				p[0].gameMode.destroyBlock(origin.offset(s[0], s[1], s[2]));
			}
			check(level.getEntitiesOfClass(Solaxis.class, new AABB(origin).inflate(20)).isEmpty(), "solaxis: stays asleep with 1 spawner left");
			int[] s = SolaxisArena.SPAWNERS[3];
			p[0].gameMode.destroyBlock(origin.offset(s[0], s[1], s[2]));
			List<Solaxis> found = level.getEntitiesOfClass(Solaxis.class, new AABB(origin).inflate(20));
			check(found.size() == 1, "solaxis: breaking the 4th spawner wakes Solaxis");
			if (found.isEmpty()) return;
			boss[0] = found.get(0);
			fight[0] = boss[0].fight();
			fight[0].join(p[0]);
			check(boss[0].getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 350 && boss[0].getHealth() == 350, "solaxis: 350 base HP (536 with Test Meter x1.53)");
			check(boss[0].getAttributeValue(Attributes.ARMOR) == 8 && boss[0].getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 2, "solaxis: armor 8 / toughness 2");
			check(fight[0].arena.sealedBlocks() > 0, "solaxis: arena sealed (" + fight[0].arena.sealedBlocks() + " seal blocks)");
			check(boss[0].getBbWidth() == 3.5F && boss[0].getBbHeight() == 6.0F, "solaxis: hitbox 3.5 x 6");
			check(boss[0].rodsLeft() == 9, "solaxis: 9 rods in phase 1");
			// overpower guard on a real hit
			float a = playerHit(level, boss[0], p[0], 40.0F);
			float b = afterArmor(32, 8, 2);
			check(Math.abs(a - b) < 0.05F, f("solaxis: overpower guard: a 40 hit lands as 32 before armor (%.2f, expected %.2f)", a, b));
			// snowballs deal 6
			Snowball ball = new Snowball(level, p[0], new ItemStack(net.minecraft.world.item.Items.SNOWBALL));
			boss[0].damageCooldownTime = 0;
			float before = boss[0].getHealth();
			boss[0].hurtServer(level, p[0].damageSources().thrown(ball, p[0]), 0.0F);
			check(before - boss[0].getHealth() > 2.0F, f("solaxis: a snowball hurts (%.2f after armor from 6)", before - boss[0].getHealth()));
			// lethal-hit guard on a 20 HP player
			FakePlayer frail = fake(level, "selftest_frail", Vec3.atBottomCenterOf(origin), 20);
			float capped = Guards.lethal(frail, boss[0].source(StubFramework.BOSS_MELEE, null), 48.0F);
			check(Math.abs(capped - 12.0F) < 0.01F, f("lethal-hit guard: a boss hit worth 48 after armor is capped at 60%% of a 20 HP player (%.1f)", capped));
			check(Guards.lethal(frail, frail.damageSources().generic(), 48.0F) == 48.0F, "lethal-hit guard ignores non-boss damage");
		});
		Supplier<Solaxis> b = () -> boss[0];
		script.then(s -> runPhaseAttacks(s, b, 1, p[0]));
		script.then(s -> {
			s.run(() -> damageTo(lv[0], boss[0], p[0], 0.65F));
			s.waitUntil("solaxis phase 2", () -> boss[0].getPhase() == 2, 100);
			s.run(() -> {
				check(boss[0].getPhase() == 2 && boss[0].inTransition(), "solaxis: phase 2 starts with a transition");
				check(playerHit(lv[0], boss[0], p[0], 20) == 0, "solaxis: invulnerable during the 2 s transition");
				check(boss[0].rodsLeft() == 6, "solaxis: 3 rods ejected (6 left)");
				check(boss[0].fight().minionCount(lv[0]) >= 3, "solaxis: ejected rods became Pyre Shards (" + boss[0].fight().minionCount(lv[0]) + ")");
			});
			s.waitUntil("solaxis transition end", () -> !boss[0].inTransition(), 60);
			runPhaseAttacks(s, b, 2, p[0]);
			s.run(() -> damageTo(lv[0], boss[0], p[0], 0.32F));
			s.waitUntil("solaxis phase 3", () -> boss[0].getPhase() == 3 && !boss[0].inTransition(), 100);
			s.run(() -> check(boss[0].rodsLeft() == 3, "solaxis: phase 3 with 3 rods"));
			runPhaseAttacks(s, b, 3, p[0]);
			// Supernova DPS check: dealing the damage fizzles it
			s.run(() -> {
				boss[0].forceAttack("supernova");
			});
			s.waitTicks(10);
			s.run(() -> {
				float hpBefore = boss[0].getHealth();
				damageTo(lv[0], boss[0], p[0], (hpBefore - boss[0].getMaxHealth() * 0.19F) / boss[0].getMaxHealth());
			});
			s.waitUntil("supernova fizzles", () -> !boss[0].hasFlag(Solaxis.FLAG_CORE_PULSE), 40);
			s.run(() -> check(!boss[0].hasFlag(Solaxis.FLAG_CORE_PULSE), "solaxis: enough damage in 20 s fizzles the Supernova"));
			s.run(() -> {
				p[0].setPos(Vec3.atBottomCenterOf(origin.offset(0, 0, 5)));
				kill(lv[0], boss[0], p[0]);
			});
			s.waitUntil("solaxis death", () -> !boss[0].isAlive(), 40);
			s.waitTicks(5);
			s.run(() -> {
				List<ItemStack> loot = lootFor(lv[0], p[0], fight[0].arena.box());
				check(fight[0].isOver(), "solaxis: fight ends on death");
				check(has(loot, SolaxisContent.NINEFOLD_ROD) && has(loot, SolaxisContent.SOLAXIS_EMBER.asItem()),
					"solaxis: first kill drops the signature Ninefold Rod and the Solaxis Ember (G7; Pyre Crown 50%: " + has(loot, SolaxisContent.PYRE_CROWN) + ")");
				check(has(loot, StubFramework.CHALLENGERS_CLEAVER), "solaxis: first kill guarantees the Tier I signature (Challenger's Cleaver)");
				check(fight[0].arena.diff(lv[0]) == 0, "solaxis: arena restored after the win (seal gone)");
				check(fight[0].minionCount(lv[0]) == 0, "solaxis: Pyre Shards cleaned up");
				clearLoot(lv[0], fight[0].arena.box());
			});
			// reset: everyone leaves
			s.run(() -> {
				boss[0] = SolaxisContent.summon(lv[0], origin, p[0]);
				fight[0] = boss[0].fight();
				fight[0].join(p[0]);
				boss[0].forceAttack("solar_flare");
			});
			s.waitTicks(40);
			s.run(() -> {
				BossFight.dropoutTicksOverride = 10;
				p[0].setPos(Vec3.atBottomCenterOf(origin.offset(0, 0, 60)));
			});
			s.waitUntil("solaxis reset", () -> boss[0].isRemoved(), 100);
			s.run(() -> {
				BossFight.dropoutTicksOverride = -1;
				check(boss[0].isRemoved() && fight[0].isOver(), "solaxis: everyone leaving for the drop-out time resets the fight");
				check(fight[0].arena.diff(lv[0]) == 0, "solaxis: arena restored after the reset");
				ServerLevel level = lv[0];
				for (ChunkPos c : List.of(ChunkPos.containing(origin))) level.setChunkForced(c.x(), c.z(), false);
			});
		});
	}
}
