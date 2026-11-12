package dev.elsewhere.petalwind_glasswaste;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.BossLoot;
import dev.elsewhere.stub.petalwind_glasswaste.BossSummons;
import dev.elsewhere.stub.petalwind_glasswaste.SafeSpot;
import dev.elsewhere.stub.petalwind_glasswaste.TripRules;
import dev.elsewhere.stub.petalwind_glasswaste.TripTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Dev-only self-test (`gradlew runServer`, -Dpetalwind_glasswaste.selftest=true): generates both dimensions, checks safe
 * arrival spots, the boss guards and trip damping by simulation, then spawns each boss in its arena and fights it with a
 * FakePlayer through every phase to its death and loot. Logs PASS/FAIL lines and a total, then stops the server.
 */
public final class SelfTest {
	private SelfTest() {}

	private static int failures, passes;
	private static MinecraftServer server;
	private static final List<String> QUEUE = new ArrayList<>(Bosses.IDS);
	private static BossBase boss;
	private static FakePlayer fake;
	private static int ticks, maxPhase, hits;
	private static boolean done;
	// 0.4.0: leftover summons after a kill, and the discard / orphan / owner-gone cleanup paths
	private static int afterDeath, extraTicks = -1;
	private static BossBase deadBoss;
	private static net.minecraft.world.phys.Vec3 deathPos;
	private static Kazehana extraK;
	private static net.minecraft.world.entity.Entity extraStand, extraOrphan, extraStray;

	static void check(boolean ok, String what) {
		PetalwindGlasswasteContent.LOGGER.info("[selftest] {} {}", ok ? "PASS" : "FAIL", what);
		if (ok) passes++;
		else failures++;
	}

	static String f(String fmt, Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	public static void start(MinecraftServer s) {
		server = s;
		try {
			checkDimensions();
			{ // sfx lane 3.0
				java.util.List<String> bad = Sfx3.selfCheck();
				check(bad.isEmpty(), "sfx3: Ankhara's own roar resolves, subtitled, no ender dragon/wither " + bad);
				Sfx3.setEnabledForTest(false);
				boolean off = Sfx3.pick("ankhara", "roar", net.minecraft.sounds.SoundEvents.RAVAGER_ROAR) == net.minecraft.sounds.SoundEvents.RAVAGER_ROAR;
				Sfx3.setEnabledForTest(true);
				boolean on = Sfx3.pick("ankhara", "roar", net.minecraft.sounds.SoundEvents.RAVAGER_ROAR) != net.minecraft.sounds.SoundEvents.RAVAGER_ROAR;
				Sfx3.setEnabledForTest(null);
				check(off && on, "sfx3: Ankhara roars with her own sound (no shared ravager roar); sfx3_petalwind_glasswaste=false restores it");
			}
			checkGuards();
			dev.elsewhere.petalwind_glasswaste.s3.groups.SpawnGroups3.selfTest(server, (ok, what) -> check(ok, what)); // ARMIES 6 spawn groups (s-spawngroups)
			checkTrips();
			checkBeta2();
			checkStormsage();
			FarChunkWatch.install(); // B7 (s-struct-nether): count far-chunk writes / unsafe reads while sites generate
			SiteChecks.run(server);
			PVChecks.run(server);
		} catch (Throwable t) {
			PetalwindGlasswasteContent.LOGGER.error("[selftest] crashed", t);
			failures++;
		}
		ServerTickEvents.END_SERVER_TICK.register(x -> {
			if (!done) {
				try {
					tickBoss();
				} catch (Throwable t) {
					PetalwindGlasswasteContent.LOGGER.error("[selftest] boss test crashed", t);
					failures++;
					boss = null;
				}
			}
		});
	}

	/** ARSENAL.md beta-2 module tasks: Glimmer items, dodge modifiers by id, trades/drops by id, client settings parsing. */
	private static void checkBeta2() {
		ServerLevel level = server.overworld();
		// dodge_chance: real item modifiers when the attribute exists, clean skip when it doesn't (arsenal not in this dev mod)
		boolean hasDodge = ArsenalHooks.attribute("dodge_chance").isPresent();
		var katana = new ItemStack(dev.elsewhere.petalwind.PetalwindItems.KAZE_KATANA).getOrDefault(
			net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		boolean katanaDodge = katana.modifiers().stream().anyMatch(e -> e.modifier().id().equals(Reg.id("pg_kaze_katana_dodge")));
		if (hasDodge) check(ArsenalHooks.DODGE_APPLIED.size() == 5 && katanaDodge, "dodge_chance present: Galeweave x4 + Kaze Katana carry the modifier");
		else check(ArsenalHooks.dodgeSkipped && ArsenalHooks.DODGE_APPLIED.isEmpty() && !katanaDodge,
			"dodge_chance absent: Galeweave/Kaze Katana dodge skipped cleanly (no modifier, no error)");
		var hood = new ItemStack(dev.elsewhere.petalwind.PetalwindItems.GALEWEAVE_HOOD).getOrDefault(
			net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		var added = ArsenalHooks.withModifier(hood, Attributes.LUCK, Reg.id("pg_selftest_dodge"), GearStats.GALEWEAVE_DODGE_PER_PIECE,
			net.minecraft.world.entity.EquipmentSlotGroup.HEAD);
		check(added.modifiers().size() == hood.modifiers().size() + 1 && added.modifiers().stream().anyMatch(e ->
			e.modifier().id().equals(Reg.id("pg_selftest_dodge")) && Math.abs(e.modifier().amount() - 0.03) < 1e-9
				&& e.slot() == net.minecraft.world.entity.EquipmentSlotGroup.HEAD),
			f("modifier helper keeps the hood's %d armor modifiers and adds +0.03 in the head slot (stand-in attribute)", hood.modifiers().size()));

		// trades by id
		var ww = ModEntities.WINDWRIGHT.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		int wwExpected = 3 + (ArsenalHooks.item("gale_rapier").isPresent() ? 1 : 0) + (ArsenalHooks.item("gale_ribbon").isPresent() ? 1 : 0) + (ArsenalHooks.item("kitestring_staff").isPresent() ? 1 : 0) + (ArsenalHooks.item("bell_of_the_flock").isPresent() ? 1 : 0)
			// gear3 (merged jar) appends the Gale Sling offer in WindwrightTradeMixin (GEAR.md A5)
			+ (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "gale_sling"))
				&& !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.gale_sling_trade")) ? 1 : 0);
		check(ww != null && nonMap(ww.getOffers()) == wwExpected && ww.getOffers().stream().noneMatch(o -> o.getResult().isEmpty()),
			f("Windwright: %d trades (Kitestring Staff %s, Bell of the Flock %s)", ww == null ? -1 : ww.getOffers().size(),
				ArsenalHooks.item("kitestring_staff").isPresent() ? "sold" : "absent, skipped", ArsenalHooks.item("bell_of_the_flock").isPresent() ? "sold" : "absent, skipped"));
		if (ww != null) ww.discard();
		var df = ModEntities.DUNE_FACTOR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		boolean lance = ArsenalHooks.item("sunlance_staff").isPresent() && ArsenalHooks.item("amethyst_wand").isPresent();
		int dfExpected = 4 + (lance ? 1 : 0) + (ArsenalHooks.item("glimmerstone_focus").isPresent() ? 1 : 0);
		check(df != null && nonMap(df.getOffers()) == dfExpected && df.getOffers().stream().noneMatch(o -> o.getResult().isEmpty()),
			f("Dune Factor: %d trades (Sunlance Staff %s, Glimmerstone Focus %s)", df == null ? -1 : df.getOffers().size(), lance ? "sold" : "absent, skipped",
				ArsenalHooks.item("glimmerstone_focus").isPresent() ? "sold" : "absent, skipped"));
		if (df != null) df.discard();
		check(ArsenalHooks.item("stormsage_mantle").isPresent() != ArsenalHooks.stack("stormsage_mantle", 1).isEmpty(),
			"Tempestra's Stormsage Mantle lookup " + (ArsenalHooks.item("stormsage_mantle").isPresent() ? "finds the item" : "is absent and yields no drop"));

		// Glimmer instead of cooldowns
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_glimmer"));
		BlockPos pos = new BlockPos(8, 250, 8);
		level.getChunk(pos);
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		p.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
		ItemStack heart = new ItemStack(dev.elsewhere.petalwind.PetalwindItems.GALE_HEART);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, heart);
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
		dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.denyForTest = true;
		var r1 = heart.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		check(!r1.consumesAction() && level.getBlockState(pos).is(Blocks.STONE) && !p.getCooldowns().isOnCooldown(heart),
			"Gale Heart without enough Glimmer does nothing (no vent, no use delay)");
		dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.denyForTest = false;
		int before = dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.spentForTest;
		var r2 = heart.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		int spent = dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.spentForTest - before;
		check(r2.consumesAction() && level.getBlockState(pos).is(dev.elsewhere.petalwind.PetalwindBlocks.UPDRAFT_VENT) && spent == 10
			&& p.getCooldowns().isOnCooldown(heart), f("Gale Heart spends %d Glimmer (10), places the updraft and starts its 2 s use delay", spent));
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		p.setPos(pos.getX() + 0.5, pos.getY() - 0.0, pos.getZ() + 0.5);
		p.setYRot(0);
		p.setXRot(0);
		var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		ItemStack lens = new ItemStack(dev.elsewhere.glasswaste.GlasswasteItems.HELIOGRAPH_LENS);
		if (zombie != null) {
			zombie.setNoAi(true);
			zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 4.5);
			level.addFreshEntity(zombie);
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, lens);
			dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.denyForTest = true;
			float hp0 = zombie.getHealth();
			lens.use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
			boolean denied = zombie.getHealth() == hp0 && !p.getCooldowns().isOnCooldown(lens);
			dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.denyForTest = false;
			before = dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.spentForTest;
			refillGlimmer(p, 20); // the merged build has the real Glimmer pool, which the Gale Heart check above just drained
			zombie.damageCooldownTime = 0;
			lens.use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
			spent = dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.spentForTest - before;
			check(denied && spent == 6 && zombie.getHealth() < hp0 && p.getCooldowns().isOnCooldown(lens),
				f("Heliograph Lens: denied without Glimmer; spends %d Glimmer (6), hits (%.1f -> %.1f HP), 1 s use delay", spent, hp0, zombie.getHealth()));
			zombie.discard();
		} else check(false, "Heliograph Lens test: could not create a zombie");
		level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		p.discard();

		// client settings parsing (stub of the shared config/elsewhere-client.json)
		var CS = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.class;
		var empty = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.parse("{}");
		var set = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.parse("{\"meters\":{\"heat\":\"Hidden\"},\"screenEffects\":false,\"reduceMotion\":true}");
		var always = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.parse("{\"meters\":{\"heat\":\"always\",\"glimmer\":\"hidden\"}}");
		var hudOff = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.parse("{\"meters\":{\"heat\":\"always\"},\"hudHidden\":true}");
		var junk = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.parse("not json {");
		var M = dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.class;
		check(CS != null && M != null
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.meterFrom(empty, "heat") == dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.AUTO
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.toggleFrom(empty, "screenEffects", true)
			&& !dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.toggleFrom(empty, "reduceMotion", false)
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.meterFrom(set, "heat") == dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.HIDDEN
			&& !dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.toggleFrom(set, "screenEffects", true)
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.toggleFrom(set, "reduceMotion", false)
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.meterFrom(always, "heat") == dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.ALWAYS
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.meterFrom(hudOff, "heat") == dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.HIDDEN
			&& dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.meterFrom(junk, "heat") == dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub.Mode.AUTO,
			"client settings (BALANCE §D4 file): meters.heat auto/always/hidden + hudHidden, screenEffects/reduceMotion toggles and defaults parse");
	}

	private static void checkDimensions() {
		for (var key : List.of(PetalwindDimension.LEVEL, GlasswasteDimension.LEVEL)) {
			ServerLevel level = server.getLevel(key);
			check(level != null, "dimension " + key.identifier() + " exists");
			if (level == null) continue;
			int solid = 0;
			for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
				var chunk = level.getChunk(cx, cz);
				for (int x = 0; x < 16; x += 4) for (int z = 0; z < 16; z += 4) {
					int h = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
					if (h > level.getMinY() + 1) solid++;
				}
			}
			check(solid > 0, f("%s generates terrain (%d of 400 sampled columns solid)", key.identifier().getPath(), solid));
			BlockPos spot = SafeSpot.find(level, BlockPos.ZERO, 640);
			check(spot != null, key.identifier().getPath() + " has a safe arrival spot near 0,0: " + spot);
		}
	}

	private static void checkGuards() {
		check(Math.abs(BossGuards.overpower(40) - 32) < 1e-4 && BossGuards.overpower(20) == 20, "overpower guard: 40 -> 32, 20 -> 20");
	}

	/** Tops up the real Glimmer pool when the merged build has one (arsenal); the standalone stub has none, so this is a no-op there. */
	static void refillGlimmer(net.minecraft.server.level.ServerPlayer p, float v) {
		try {
			Class.forName("dev.elsewhere.arsenal.glimmer.Glimmer").getMethod("setForTest", net.minecraft.server.level.ServerPlayer.class, float.class).invoke(null, p, v);
		} catch (ReflectiveOperationException | LinkageError ignored) {
		}
	}

	/** Simulates the trip rules: living at a trigger spot 100 h gives < 2 trips on average; an explorer gets some. */
	private static void checkTrips() {
		Random r = new Random(7);
		double homeTrips = 0, exploreTrips = 0;
		int runs = 200;
		for (int run = 0; run < runs; run++) {
			TripRules home = new TripRules();
			for (int day = 0; day < 300; day++) { // ~100 h of play, one desert-noon roll per in-game day at home
				home.tick(1200, "overworld@0,0");
				double c = home.chance("glasswaste.desert_noon", TripTrigger.Kind.LOCATION, 0.10, "overworld@0,0", true);
				if (r.nextDouble() < c) {
					home.recordTrip();
					homeTrips++;
				}
			}
			TripRules ex = new TripRules();
			for (int min = 0; min < 15 * 60; min++) {
				String region = "overworld@" + (min / 20) + ",0";
				ex.tick(60, region);
				if (min % 90 == 45 && r.nextDouble() < ex.chance("petalwind.long_fall", TripTrigger.Kind.ACTIVITY, 0.04, region, false)) {
					ex.recordTrip();
					exploreTrips++;
				}
				if (min % 225 == 100 && r.nextDouble() < ex.chance("glasswaste.desert_noon", TripTrigger.Kind.LOCATION, 0.10, region, false)) {
					ex.recordTrip();
					exploreTrips++;
				}
			}
		}
		check(homeTrips / runs < 2.0, f("living 100 h at a desert spot: %.2f trips on average (< 2)", homeTrips / runs));
		check(exploreTrips / runs > 0.1, f("explorer, 15 h, this module's triggers only: %.2f trips on average", exploreTrips / runs));
	}

	private static void tickBoss() {
		if (afterDeath > 0) {
			if (--afterDeath == 0) checkLeftovers();
			return;
		}
		if (boss == null) {
			if (QUEUE.isEmpty()) {
				if (extraTicks < 0) extraTicks = 0;
				if (!cleanupDone) {
					if (summonCleanupTick()) cleanupDone = true;
					return;
				}
				if (ankharaHomeTick()) finish();
				return;
			}
			String id = QUEUE.remove(0);
			boolean sky = id.equals("kazehana") || id.equals("tempestra");
			ServerLevel level = server.getLevel(sky ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL);
			BlockPos at = new BlockPos(6000 + Bosses.IDS.indexOf(id) * 200, sky ? 170 : 0, 6000);
			for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
			if (!sky) at = at.atY(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
			boss = Bosses.summon(level, id, at);
			check(boss != null, id + " spawns with its arena");
			if (boss == null) return;
			float base = (float) boss.getAttributeBaseValue(Attributes.MAX_HEALTH);
			check(true, f("%s base HP %.0f (x1.53 Test Meter = %.0f effective), armor %.0f, toughness %.0f", id, base, base * 1.53,
				boss.getAttributeValue(Attributes.ARMOR), boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
			fake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_" + id));
			fake.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
			fake.setHealth(1024);
			BlockPos c = boss.arenaCenter();
			fake.snapTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 2.5);
			level.addFreshEntity(fake);
			if (boss instanceof Kazehana k) {
				check(k.isDormant(), "kazehana waits dormant (bowing) until challenged");
				k.beginDuel(level, fake);
			}
			ticks = 0;
			maxPhase = 1;
			hits = 0;
			return;
		}
		ServerLevel level = (ServerLevel) boss.level();
		ticks++;
		fake.setHealth(fake.getMaxHealth());
		BlockPos c = boss.arenaCenter();
		if (!boss.insideArena(fake)) fake.teleportTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 2.5);
		maxPhase = Math.max(maxPhase, boss.phase());
		if (ticks == 60 && boss instanceof Heliodor h) {
			BlockPos probe = c.offset(3, -1, 3);
			level.setBlock(probe, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
			h.crack(level, 0);
			check(h.getArmorValue() <= Heliodor.ARMOR - 4, f("heliodor: a mirror hit cracks 4 armor (now %d)", h.getArmorValue()));
			boss.resetFight(level);
			check(level.getBlockState(probe).is(Blocks.MAGMA_BLOCK) == false, "arena reset restores changed blocks");
			check(boss.getHealth() == boss.getMaxHealth() && boss.phase() == 1, "boss reset heals to full and returns to phase 1");
		}
		if (ticks == 20 && boss instanceof Kazehana k) k.testDrawCut(level, fake);
		if (ticks % 10 == 0 && ticks > 60 && boss.isAlive()) {
			if (boss.hurtServer(level, fake.damageSources().playerAttack(fake), 60.0F)) hits++;
			boss.hurtTime = 0;
		}
		if (ticks == 200) {
			float before = fake.getHealth();
			fake.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
			fake.setHealth(20);
			BossDamage.hit(level, boss, fake, 48);
			float lost = 20 - fake.getHealth();
			if (lost == 0) PetalwindGlasswasteContent.LOGGER.info("[selftest] SKIP lethal-hit guard on a FakePlayer (FakePlayers ignore damage)");
			else check(lost <= 12.01F, f("%s: lethal-hit guard caps a 48-raw hit on an unarmored player at %.1f HP (<= 60%%)", boss.bossId(), lost));
			fake.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
			fake.setHealth(before);
		}
		if (!boss.isAlive() || boss.isRemoved()) {
			check(maxPhase == 3, f("%s ran all phases (reached phase %d) in %d ticks, %d hits landed", boss.bossId(), maxPhase, ticks, hits));
			int drops = boss.lastLoot.size();
			check(drops > 0, f("%s dropped loot on the ground (%d stacks, kit drop rules)", boss.bossId(), drops));
			if (boss instanceof Kazehana k) check(k.drawCutsDone > 0, f("kazehana used Draw Cut %d times", k.drawCutsDone));
			if (boss instanceof Ankhara a) check(a.erupts > 0 && a.collapses > 0, f("ankhara erupted %d times, %d ring collapses", a.erupts, a.collapses));
			fake.getInventory().clearContent();
			fake.discard();
			deadBoss = boss;
			deathPos = boss.position();
			afterDeath = 200;
			boss = null;
			return;
		}
		if (ticks > 3600) {
			check(false, f("%s still alive after 3600 ticks (phase %d, %.0f HP)", boss.bossId(), boss.phase(), boss.getHealth()));
			boss.discard();
			boss = null;
		}
	}

	/** 0.4.0 (QA issue 9): Stormsage Mantle 50% on a player's first Tempestra kill, 20% on repeats; first kill is per player. */
	private static void checkStormsage() {
		check(Tempestra.stormsageChance(true) == 0.5F && Tempestra.stormsageChance(false) == 0.2F,
			f("Stormsage Mantle chance: first kill %.2f, repeat %.2f (want 0.50 / 0.20)", Tempestra.stormsageChance(true), Tempestra.stormsageChance(false)));
		ServerLevel level = server.overworld();
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_stormsage"));
		boolean first = BossLoot.markKill(p, "tempestra"), second = BossLoot.markKill(p, "tempestra");
		FakePlayer q = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_stormsage2"));
		boolean other = BossLoot.markKill(q, "tempestra");
		check(first && !second && other, "Tempestra first kill is tracked per player (first, repeat, another player's first)");
		Random r = new Random(40);
		int n = 20000, a = 0, b = 0;
		for (int i = 0; i < n; i++) {
			if (r.nextFloat() < Tempestra.stormsageChance(true)) a++;
			if (r.nextFloat() < Tempestra.stormsageChance(false)) b++;
		}
		check(Math.abs(a / (double) n - 0.5) < 0.02 && Math.abs(b / (double) n - 0.2) < 0.02,
			f("Stormsage Mantle sampled over %d kills: first %.3f, repeat %.3f", n, a / (double) n, b / (double) n));
	}

	/** 10 s after a kill: nothing the boss spawned (tagged summons) is left anywhere near its arena. */
	private static void checkLeftovers() {
		ServerLevel level = (ServerLevel) deadBoss.level();
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(BlockPos.containing(deathPos)).inflate(64);
		int tagged = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> e.entityTags().contains(BossSummons.TAG)).size();
		java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> !(e instanceof net.minecraft.server.level.ServerPlayer)
			&& !(e instanceof net.minecraft.world.entity.item.ItemEntity) && !e.isRemoved())) {
			var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			if (key.getNamespace().equals("elsewhere")) byType.merge(key.getPath(), 1, Integer::sum);
		}
		check(tagged == 0 && BossSummons.alive(deadBoss) == 0, f("%s: %d of %d fight summons left 10 s after death (elsewhere entities within 64 blocks: %s)",
			deadBoss.bossId(), tagged, BossSummons.spawnedBy(deadBoss), byType.isEmpty() ? "none" : byType));
		deadBoss = null;
	}

	/** Discard without a kill, an orphaned summon loaded without its binding, and a summon whose (non-boss) owner vanished. */
	private static boolean summonCleanupTick() {
		ServerLevel level = server.getLevel(PetalwindDimension.LEVEL);
		int t = extraTicks++;
		if (t == 0) {
			BlockPos at = new BlockPos(6000 + 6 * 200, 170, 6000);
			for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
			BossBase b = Bosses.summon(level, "kazehana", at);
			if (!(b instanceof Kazehana k)) {
				check(false, "summon cleanup: could not summon kazehana");
				return true;
			}
			extraK = k;
			fake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_discard"));
			BlockPos c = k.arenaCenter();
			fake.snapTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 2.5);
			level.addFreshEntity(fake);
			k.beginDuel(level, fake);
			var breeze = ModEntities.BLOSSOM_BREEZE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			breeze.snapTo(c.getX() + 3.5, c.getY() + 1, c.getZ() + 0.5);
			BossSummons.spawn(k, level, breeze);
			var m = ModEntities.PETAL_MIRAGE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			m.snapTo(c.getX() - 3.5, c.getY() + 1, c.getZ() + 0.5);
			m.setOwner(k);
			BossSummons.spawn(k, level, m);
			var cloud = new net.minecraft.world.entity.AreaEffectCloud(level, c.getX() + 0.5, c.getY() + 1, c.getZ() - 3.5);
			cloud.setRadius(3.0F);
			cloud.setDuration(600);
			BossSummons.spawn(k, level, cloud);
			// orphan: carries the summon tag but no live binding (as after a restart) -> discarded on the next tick
			var orphan = ModEntities.GALE_KITE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			orphan.snapTo(c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5);
			orphan.addTag(BossSummons.TAG);
			level.addFreshEntity(orphan);
			extraOrphan = orphan;
			// owner-gone path: summon bound to a plain entity that then vanishes (no remove hook) -> tick sweep discards it
			var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, c.getX() + 0.5, c.getY() + 1, c.getZ() + 6.5);
			level.addFreshEntity(stand);
			extraStand = stand;
			var stray = ModEntities.CUMULUS.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			stray.snapTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 7.5);
			BossSummons.spawn(stand, level, stray);
			extraStray = stray;
			return false;
		}
		if (t == 5) {
			check(BossSummons.alive(extraK) == 3 && extraK.isFighting(), f("kazehana holds %d bound summons mid-fight (breeze, mirage, petal cloud)", BossSummons.alive(extraK)));
			check(extraOrphan.isRemoved(), "a tagged summon loaded without its boss binding is discarded on the next tick");
			deathPos = extraK.position();
			extraK.discard();
			extraStand.discard();
			return false;
		}
		if (t == 6) {
			ServerLevel l = level;
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(BlockPos.containing(deathPos)).inflate(64);
			int left = l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
				e -> e.entityTags().contains(BossSummons.TAG) && e.getType() != ModEntities.CUMULUS).size();
			check(left == 0 && BossSummons.alive(extraK) == 0, f("kazehana discarded without a kill: %d of her summons left 1 tick later", left));
			return false;
		}
		if (t == 30) {
			check(extraStray.isRemoved(), "a summon whose owner vanished is swept within 1 s (owner-gone check)");
			fake.discard();
			return true;
		}
		return false;
	}

	// Ankhara home/clamp checks: bare summon, Lure summon, summon at the world floor
	private static boolean cleanupDone;
	private static int ankT = -1;
	private static Ankhara ankBare, ankLure, ankLow;
	private static BlockPos ankBarePos, ankLureFloor;
	private static double ankBareMinY = 1e9, ankLureMinY = 1e9, ankLowMinY = 1e9;
	private static double ankBareMaxD, ankLureMaxD;
	private static FakePlayer ankFake, ankFake2;

	private static void clearForced(ServerLevel level, BlockPos at) {
		for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
	}

	/** A bare summon starts the fight at its summon point and stays there; a Lure summon stays in its Sunpit; nothing goes below min height + 2. */
	private static boolean ankharaHomeTick() {
		ServerLevel level = server.getLevel(GlasswasteDimension.LEVEL);
		int t = ++ankT;
		int minY = level.getMinY();
		if (t == 0) {
			BlockPos at = new BlockPos(7000, 0, 7000);
			clearForced(level, at);
			int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			ankBarePos = new BlockPos(at.getX(), gy + 1, at.getZ());
			ankBare = ModEntities.ANKHARA.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			ankBare.snapTo(ankBarePos.getX() + 0.5, ankBarePos.getY(), ankBarePos.getZ() + 0.5);
			level.addFreshEntity(ankBare);
			ankFake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_ank_bare"));
			ankFake.snapTo(ankBarePos.getX() + 0.5, ankBarePos.getY(), ankBarePos.getZ() + 4.5);
			level.addFreshEntity(ankFake);
			BlockPos lat = new BlockPos(7400, 0, 7000);
			clearForced(level, lat);
			int ly = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lat.getX(), lat.getZ());
			PGStructures.buildAndSpawn(level, PGStructures.SUNPIT, new BlockPos(lat.getX(), ly - 6, lat.getZ()), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED, true);
			ankLureFloor = new BlockPos(lat.getX(), ly - 6, lat.getZ());
			var found = level.getEntitiesOfClass(Ankhara.class, new net.minecraft.world.phys.AABB(ankLureFloor).inflate(30));
			check(found.size() == 1, "Sunpit Lure summon spawns one Ankhara in its own Sunpit (" + found.size() + ")");
			if (!found.isEmpty()) ankLure = found.get(0);
			ankFake2 = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "selftest_ank_lure"));
			ankFake2.snapTo(ankLureFloor.getX() + 0.5, ankLureFloor.getY() + 1, ankLureFloor.getZ() + 4.5);
			level.addFreshEntity(ankFake2);
			BlockPos low = new BlockPos(7800, minY + 1, 7000);
			clearForced(level, low);
			ankLow = ModEntities.ANKHARA.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			ankLow.snapTo(low.getX() + 0.5, minY + 1, low.getZ() + 0.5);
			level.addFreshEntity(ankLow);
			return false;
		}
		if (t > 200) return t > 205;
		if (ankFake != null) ankFake.setHealth(ankFake.getMaxHealth());
		if (ankFake2 != null) ankFake2.setHealth(ankFake2.getMaxHealth());
		if (ankBare != null && ankBare.isAlive()) {
			ankBareMinY = Math.min(ankBareMinY, ankBare.getY());
			ankBareMaxD = Math.max(ankBareMaxD, Math.hypot(ankBare.getX() - (ankBarePos.getX() + 0.5), ankBare.getZ() - (ankBarePos.getZ() + 0.5)));
		}
		if (ankLure != null && ankLure.isAlive()) {
			ankLureMinY = Math.min(ankLureMinY, ankLure.getY());
			ankLureMaxD = Math.max(ankLureMaxD, Math.hypot(ankLure.getX() - (ankLureFloor.getX() + 0.5), ankLure.getZ() - (ankLureFloor.getZ() + 0.5)));
		}
		if (ankLow != null && ankLow.isAlive()) ankLowMinY = Math.min(ankLowMinY, ankLow.getY());
		if (t == 200) {
			check(ankBare != null && ankBare.isAlive() && ankBareMaxD <= Sunpit.ARENA_RADIUS + 2,
				f("bare /summon: Ankhara stays near the summon point after 200 ticks (max %.1f blocks away, home %s)", ankBareMaxD, ankBare == null ? "?" : ankBare.arenaCenter()));
			check(ankBare != null && ankBare.arenaCenter() != null && ankBare.arenaCenter().distSqr(ankBarePos) <= 4, "bare /summon: home is the summon point");
			check(ankBareMinY >= minY + 2 && ankLureMinY >= minY + 2 && ankLowMinY >= minY + 2,
				f("Ankhara y always >= min + 2 (%d): bare %.1f, Lure %.1f, summoned at min+1 %.1f", minY + 2, ankBareMinY, ankLureMinY, ankLowMinY));
			check(ankLure != null && ankLure.isAlive() && ankLureMaxD <= Sunpit.ARENA_RADIUS + 2 && ankLure.arenaCenter() != null && ankLure.arenaCenter().equals(ankLureFloor),
				f("Lure summon: Ankhara stays in its own Sunpit (max %.1f blocks from its centre, home %s)", ankLureMaxD, ankLure == null ? "?" : ankLure.arenaCenter()));
			for (Ankhara a : new Ankhara[] {ankBare, ankLure, ankLow}) if (a != null) a.discard();
			if (ankFake != null) ankFake.discard();
			if (ankFake2 != null) ankFake2.discard();
		}
		return false;
	}

	private static void finish() {
		done = true;
		check(FarChunkWatch.installed() && FarChunkWatch.countFor("elsewhere:sunpit") == 0 && FarChunkWatch.count() == 0, f("B7: 0 far-chunk writes or unsafe terrain reads from elsewhere:sunpit (%d) or any other site (%d %s)", FarChunkWatch.countFor("elsewhere:sunpit"), FarChunkWatch.count(), FarChunkWatch.sources()));
		PetalwindGlasswasteContent.LOGGER.info("[selftest] {} passed, {} failed: {}", passes, failures, failures == 0 ? "ALL PASSED" : "FAILURES");
		if (Boolean.getBoolean("petalwind_glasswaste.selftest.exit")) server.halt(false);
	}
	/** Explorer-map trades depend on a structure being found near the test spot, so they are not counted. */
	private static long nonMap(java.util.List<net.minecraft.world.item.trading.MerchantOffer> offers) {
		return offers.stream().filter(o -> !o.getResult().is(net.minecraft.world.item.Items.FILLED_MAP)).count();
	}
}
