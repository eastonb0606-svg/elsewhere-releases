package dev.elsewhere.test;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.boss.BossDrops;
import dev.elsewhere.core.boss.ElsewhereBoss;
import dev.elsewhere.palehollow.ArsenalLinks;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.boss.mother_creak.ElderTrunkArena;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.boss.selenne.MoonringArena;
import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.boss.selenne.SelenneSummon;
import dev.elsewhere.core.boss.BossGuards;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.core.travel.ReturnPoint;
import dev.elsewhere.core.travel.RiftEntity;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.travel.SafeLanding;
import dev.elsewhere.core.trip.AccidentalTrips;
import dev.elsewhere.core.trip.TripRules;
import dev.elsewhere.core.trip.TripState;
import dev.elsewhere.core.trip.TripTrigger;
import dev.elsewhere.core.wayfinder.AttunementRecipe;
import dev.elsewhere.core.wayfinder.Attunements;
import dev.elsewhere.palehollow.GearStats;
import dev.elsewhere.palehollow.PaleHollowDimension;
import dev.elsewhere.palehollow.PaleHollowTrips;
import dev.elsewhere.registry.ModAttachments;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-server self-test ({@code ./gradlew runServer}, port 25611): attunement recipe, rift round trip with followers,
 * accidental-trip rules and simulations, Pale Hollow worldgen, both bosses (stats vs ladder, guards, phases, arena
 * reset, drops). Logs PASS/FAIL lines and a total, then stops the server.
 */
public final class SelfTest {
	private static int passed;
	private static int failed;
	private static final List<String> FAILURES = new ArrayList<>();

	private SelfTest() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				run(server);
			} catch (Throwable t) {
				Elsewhere.LOG.error("[selftest] crashed", t);
				check(false, "self-test crashed: " + t);
			}
			Elsewhere.LOG.info("[selftest] {} passed, {} failed{}", passed, failed, failed == 0 ? " - ALL PASSED" : " - FAILURES: " + FAILURES);
			if (Boolean.getBoolean("elsewhere.selftest.exit")) {
				server.halt(false);
			}
		});
	}

	static void check(final boolean ok, final String what) {
		if (ok) {
			passed++;
		} else {
			failed++;
			FAILURES.add(what);
		}
		Elsewhere.LOG.info("[selftest] {} {}", ok ? "PASS" : "FAIL", what);
	}

	static String f(final String fmt, final Object... args) {
		return String.format(java.util.Locale.ROOT, fmt, args);
	}

	private static FakePlayer fake(final ServerLevel level, final String name) {
		return FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
	}

	private static void run(final MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		ServerLevel hollow = server.getLevel(PaleHollowDimension.LEVEL);
		check(hollow != null, "Pale Hollow dimension is loaded");
		if (hollow == null) {
			return;
		}
		AccidentalTrips.playTicksOverride = p -> 100L * 72000L;
		dev.elsewhere.core.s3.groups.SpawnGroups3.selfTest(server, (ok, what) -> check(ok, what)); // ARMIES 6 spawn groups (s-spawngroups)
		recipes(server, overworld);
		worldgen(hollow);
		travel(server, overworld, hollow);
		tripRules();
		tripSimulation();
		tripLive(overworld);
		motherCreak(hollow);
		selenne(hollow);
		update040(server, overworld, hollow);
		phase1c(hollow);
		V2SelfTest.run(server, overworld, hollow);
		AccidentalTrips.playTicksOverride = null;
	}

	// ------------------------------------------------------------------ recipes
	private static CraftingInput grid(final Item... items) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Item i : items) {
			stacks.add(i == null ? ItemStack.EMPTY : new ItemStack(i));
		}
		return CraftingInput.of(3, 3, stacks);
	}

	private static void recipes(final MinecraftServer server, final ServerLevel level) {
		check(Attunements.all().size() >= 1 && Attunements.all().values().stream().anyMatch(d -> d.dimension().equals(PaleHollowDimension.LEVEL)),
			"attunement data loaded from data/elsewhere/attunement (" + Attunements.all().size() + " file(s))");
		Item w = ModItems.WAYFINDER;
		Item r = Items.RESIN_CLUMP, e = Items.OPEN_EYEBLOSSOM, l = Items.PALE_OAK_LOG, m = Items.PALE_MOSS_BLOCK, b = Items.BONE_BLOCK;
		CraftingInput doc = grid(r, e, r, l, w, l, m, b, m);
		AttunementRecipe recipe = AttunementRecipe.INSTANCE;
		check(recipe.matches(doc, level) && recipe.assemble(doc).is(ModItems.WAYFINDER_PALE_HOLLOW),
			"Wayfinder + resin, eyeblossom, pale oak log, pale moss, bone block (doc layout) -> Wayfinder: The Pale Hollow");
		CraftingInput shuffled = grid(b, w, m, l, e, r, null, null, null);
		check(recipe.matches(shuffled, level), "attunement accepts any layout with one of each of the 5 items");
		CraftingInput missing = grid(r, e, r, l, w, l, m, null, m);
		check(!recipe.matches(missing, level), "only 4 of the 5 ingredients -> no attunement");
		CraftingInput extra = grid(r, e, r, l, w, l, m, b, Items.DIRT);
		check(!recipe.matches(extra, level), "a foreign item in the grid -> no attunement");
		CraftingInput two = grid(r, e, r, l, w, l, m, b, w);
		check(!recipe.matches(two, level), "two Wayfinders in the grid -> no attunement");
		var shaped = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Elsewhere.id("wayfinder")));
		check(shaped.isPresent(), "base Wayfinder recipe (APA / ACA / .P.) is registered");
		var shapedPh = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Elsewhere.id("wayfinder_pale_hollow")));
		check(shapedPh.isPresent(), "recipe-book version of the Pale Hollow attunement is registered");
	}

	// ------------------------------------------------------------------ worldgen
	private static void worldgen(final ServerLevel hollow) {
		Set<String> biomes = new HashSet<>();
		int trunks = 0;
		int dry = 0;
		int samples = 0;
		for (int cx = -6; cx <= 6; cx += 2) {
			for (int cz = -6; cz <= 6; cz += 2) {
				ChunkAccess chunk = hollow.getChunk(cx * 4, cz * 4);
				int x = (cx * 4 << 4) + 8;
				int z = (cz * 4 << 4) + 8;
				int y = hollow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				hollow.getBiome(new BlockPos(x, 64, z)).unwrapKey().ifPresent(k -> biomes.add(k.identifier().getPath()));
				samples++;
				if (SafeLanding.column(hollow, x, z, 400) != null) {
					dry++;
				}
				for (int by = 64; by < 240; by += 6) {
					if (chunk.getBlockState(new BlockPos(x, by, z)).is(ModBlocks.HEARTWOOD_LOG)) {
						trunks++;
						break;
					}
				}
			}
		}
		check(biomes.size() >= 3, "Pale Hollow generates several of its biomes in a 50x50-chunk sample: " + biomes);
		check(dry > samples / 4, f("terrain: %d of %d sample columns are dry, landable ground", dry, samples));
		// world-trunks: scan a wider area for the tall heartwood columns
		int found = 0;
		for (int cx = -8; cx <= 8 && found == 0; cx++) {
			for (int cz = -8; cz <= 8 && found == 0; cz++) {
				ChunkAccess chunk = hollow.getChunk(cx, cz);
				for (int x = 0; x < 16 && found == 0; x += 2) {
					for (int z = 0; z < 16 && found == 0; z += 2) {
						for (int by = 100; by < 200; by += 10) {
							BlockPos p = new BlockPos((cx << 4) + x, by, (cz << 4) + z);
							if (chunk.getBlockState(p).is(Blocks.PALE_OAK_WOOD) || chunk.getBlockState(p).is(ModBlocks.HEARTWOOD_LOG)) {
								found++;
								break;
							}
						}
					}
				}
			}
		}
		check(found > 0, "a world-trunk (pale oak column above Y 100) generates within 8 chunks of the origin");
		BlockPos rift = ReturnRifts.nearestRift(hollow, 0, 0);
		hollow.getChunk(rift.getX() >> 4, rift.getZ() >> 4);
		boolean riftBlock = false;
		for (int y = hollow.getMinY(); y < hollow.getMaxY() && !riftBlock; y++) {
			riftBlock = hollow.getBlockState(new BlockPos(rift.getX(), y, rift.getZ())).is(ModBlocks.RETURN_RIFT);
		}
		check(riftBlock, f("a Return Rift ruin generated at its cell anchor (%d, %d)", rift.getX(), rift.getZ()));
		BlockPos a = ReturnRifts.anchor(hollow.getSeed(), hollow.dimension(), 0, 0);
		BlockPos b = ReturnRifts.anchor(hollow.getSeed(), hollow.dimension(), 1, 0);
		check(a.getX() >= 96 && a.getX() < 504 && b.getX() >= 696 && b.getX() < 1104,
			"Return Rift anchors: one per 600x600 cell, kept 96 blocks from cell edges");
	}

	// ------------------------------------------------------------------ travel
	private static void travel(final MinecraftServer server, final ServerLevel overworld, final ServerLevel hollow) {
		FakePlayer p = fake(overworld, "Traveller");
		overworld.getChunk(2000 >> 4, -1200 >> 4);
		BlockPos start = new BlockPos(2000, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 2000, -1200), -1200);
		p.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
		check(RiftTravel.travelNow(p, PaleHollowDimension.LEVEL), "travel into the Pale Hollow");
		check(p.level() == hollow, "player is now in the Pale Hollow");
		BlockPos feet = p.blockPosition();
		check(SafeLanding.isSafe(hollow, feet), f("landing spot is safe: solid dry ground, 2 air blocks (%s)", feet.toShortString()));
		check(feet.getY() <= PaleHollowDimension.MAX_LANDING_Y, "landing is on the forest floor, not on a world-trunk");
		BlockPos rift = ReturnRifts.nearestRift(hollow, feet.getX(), feet.getZ());
		double toRift = Math.sqrt(Math.pow(rift.getX() - feet.getX(), 2) + Math.pow(rift.getZ() - feet.getZ(), 2));
		check(toRift <= ReturnRifts.LANDING_RADIUS + 70, f("landing is %.0f blocks from the nearest Return Rift (limit ~300)", toRift));
		ReturnPoint rp = p.getAttached(ModAttachments.RETURN_POINT);
		check(rp != null && rp.dimension().equals(Level.OVERWORLD) && rp.pos().distanceTo(Vec3.atBottomCenterOf(start)) < 1.0,
			"return point saved where the player left the Overworld");
		check(RiftTravel.travelNow(p, null), "travel home");
		check(p.level() == overworld && Math.max(Math.abs(p.getX() - (start.getX() + 0.5)), Math.abs(p.getZ() - (start.getZ() + 0.5))) <= 25.0 && SafeLanding.isSafe(overworld, p.blockPosition()),
			f("back at the return point, or the nearest safe ground within SafeLanding.near radius 24 (square search, so up to ~34 blocks diagonal) (%.1f blocks away; left from %s, now at %s)", p.position().distanceTo(Vec3.atBottomCenterOf(start)),
				start.toShortString(), p.blockPosition().toShortString()));

		// a real rift: owner goes through at 3 s, a friend stepping in within 10 s follows
		FakePlayer friend = fake(overworld, "Friend");
		friend.snapTo(p.getX() + 1, p.getY(), p.getZ(), 0.0F, 0.0F);
		check(RiftTravel.openWayfinderRift(p, PaleHollowDimension.LEVEL), "attuned Wayfinder opens a rift");
		RiftEntity rift1 = RiftTravel.lastRift;
		check(rift1 != null && rift1.color() == PaleHollowDimension.COLOR, "rift carries the Pale Hollow colour");
		int open = (int)Math.round(ElsewhereConfig.get().wayfinders.riftOpenSeconds * 20);
		for (int i = 0; i < open - 1; i++) {
			rift1.tick();
		}
		check(p.level() == overworld, "owner is still in the Overworld before the rift finishes opening");
		rift1.tick();
		check(p.level() == hollow, f("owner pulled through after %d ticks (%.1f s)", open, open / 20.0));
		for (int i = 0; i < 100; i++) {
			rift1.tick();
		}
		check(rift1.stepIn(friend) && friend.level() == hollow && friend.distanceTo(p) < 8.0,
			f("a friend stepping in 5 s later follows and lands %.1f blocks away", friend.distanceTo(p)));
		for (int i = 0; i < 200 && !rift1.isRemoved(); i++) {
			rift1.tick();
		}
		check(rift1.isRemoved(), "rift closes after its 10 s linger");

		// Wayfinder inside the Pale Hollow opens a rift home
		check(RiftTravel.openWayfinderRift(p, PaleHollowDimension.LEVEL), "Wayfinder used inside the Pale Hollow opens a rift home");
		RiftEntity home = RiftTravel.lastRift;
		for (int i = 0; i <= open; i++) {
			home.tick();
		}
		check(p.level() == overworld, "home rift returns to the Overworld");

		// Frayed Wayfinder: forced rift home from the Pale Hollow
		RiftTravel.travelNow(friend, PaleHollowDimension.LEVEL);
		check(RiftTravel.openForcedRift(friend, null), "Frayed Wayfinder rift opens on the player");
		RiftEntity frayed = RiftTravel.lastRift;
		friend.snapTo(friend.getX() + 30, friend.getY(), friend.getZ(), 0.0F, 0.0F);
		for (int i = 0; i <= open; i++) {
			frayed.tick();
		}
		check(friend.level() == overworld, "forced rift takes its owner even after they walked away");
		check(ElsewhereDimensions.isElsewhere(PaleHollowDimension.LEVEL) && !ElsewhereDimensions.isElsewhere(Level.OVERWORLD),
			"dimension registry knows which levels are Elsewhere");
	}

	// ------------------------------------------------------------------ trip rules
	private static TripRules.Situation sit(final double hoursPlayed, final double sinceTripH, final double regionH, final boolean home, final boolean budget) {
		long play = (long)(hoursPlayed * 72000);
		long last = sinceTripH < 0 ? -1L : play - (long)(sinceTripH * 72000);
		return new TripRules.Situation(play, last, regionH, home, budget, false);
	}

	private static void tripRules() {
		ElsewhereConfig.AccidentalTrips cfg = ElsewhereConfig.get().accidentalTrips;
		TripTrigger.Kind loc = TripTrigger.Kind.LOCATION;
		TripTrigger.Kind act = TripTrigger.Kind.ACTIVITY;
		check(Math.abs(TripRules.effectiveChance(0.02, loc, sit(3, 3, 0, false, true), cfg) - 0.02) < 1e-9,
			"any-sleep in a new region, 3 h after the last trip: full 2%");
		check(Math.abs(TripRules.familiarity(0.5, false, cfg) - 0.5) < 1e-9 && Math.abs(TripRules.familiarity(1.0, false, cfg) - 0.25) < 1e-9,
			"familiarity halves every 30 minutes spent in a region");
		check(Math.abs(TripRules.familiarity(10.0, false, cfg) - 0.03) < 1e-9, "familiarity floor is 0.03");
		check(Math.abs(TripRules.familiarity(0.0, true, cfg) - 0.03) < 1e-9, "within 128 blocks of the respawn point: always 0.03");
		check(TripRules.effectiveChance(0.15, loc, sit(0.9, -1, 0, false, true), cfg) == 0.0, "no trips in the first 60 minutes of play");
		check(TripRules.effectiveChance(0.15, loc, sit(10, 1.9, 0, false, true), cfg) == 0.0, "2 h cooldown after a trip");
		check(TripRules.effectiveChance(0.15, loc, sit(10, 2.1, 0, false, true), cfg) > 0.0, "chance returns after the cooldown");
		check(Math.abs(TripRules.effectiveChance(0.02, loc, sit(20, 9, 0, false, true), cfg) - 0.04) < 1e-9, "pity: x2 after 8 h without a trip");
		check(Math.abs(TripRules.effectiveChance(0.02, loc, sit(20, 9, 0, true, true), cfg) - 0.02 * 0.03) < 1e-9, "pity never applies at home");
		check(TripRules.effectiveChance(0.04, act, sit(5, 3, 0, false, false), cfg) == 0.0, "activity trigger over its hourly budget does not roll");
		TripState st = new TripState();
		long t0 = 5 * 72000L;
		int allowed = 0;
		for (int i = 0; i < 10; i++) {
			if (st.spendBudget("petalwind.long_fall", t0 + i * 100, 3)) {
				allowed++;
			}
		}
		boolean nextHour = st.spendBudget("petalwind.long_fall", t0 + 72000L, 3);
		check(allowed == 3 && nextHour, "long-fall budget: 3 rolls per hour of playtime, refilled the next hour");
		ElsewhereConfig.AccidentalTrips off = new ElsewhereConfig.AccidentalTrips();
		off.enabled = false;
		check(TripRules.effectiveChance(0.15, loc, sit(10, -1, 0, false, true), off) == 0.0, "config enabled=false turns trips off");
		ElsewhereConfig.AccidentalTrips half = new ElsewhereConfig.AccidentalTrips();
		half.scale = 0.5;
		check(Math.abs(TripRules.effectiveChance(0.02, loc, sit(3, 3, 0, false, true), half) - 0.01) < 1e-9, "config scale multiplies every chance");
		// Monte Carlo: the roll matches the stated probability
		Random rnd = new Random(42);
		int hits = 0;
		int n = 200_000;
		double pr = TripRules.effectiveChance(0.15, loc, sit(3, 3, 0.25, false, true), cfg);
		for (int i = 0; i < n; i++) {
			if (rnd.nextDouble() < pr) {
				hits++;
			}
		}
		double sigma = Math.sqrt(n * pr * (1 - pr));
		check(Math.abs(hits - n * pr) < 4 * sigma, f("near-pale-oak sleep after 15 min in the region: %.4f expected, %.4f rolled over %d tries",
			pr, hits / (double)n, n));
	}

	// ------------------------------------------------------------------ simulations (CONTRACT.md targets)
	private record SimTrigger(String id, TripTrigger.Kind kind, double base) {
	}

	/** A resident (BALANCE.md T3): everything happens at home (m = 0.03, no pity) at a fixed rate per hour for 100 h. */
	private static double simulateResident(final Random rnd, final int runs, final TripTrigger.Kind kind, final double base, final int perHour,
		final String id) {
		ElsewhereConfig.AccidentalTrips cfg = ElsewhereConfig.get().accidentalTrips;
		double total = 0;
		long step = 72000L / perHour;
		for (int r = 0; r < runs; r++) {
			long last = -1;
			int trips = 0;
			TripState st = new TripState();
			for (long t = 0; t < 100L * 72000L; t += step) {
				boolean budget = kind != TripTrigger.Kind.ACTIVITY || st.spendBudget(id, t, cfg.hourlyBudgets.getOrDefault(id, 3));
				TripRules.Situation s = new TripRules.Situation(t, last, 50.0, true, budget, false);
				if (rnd.nextDouble() < TripRules.effectiveChance(base, kind, s, cfg)) {
					trips++;
					last = t;
				}
			}
			total += trips;
		}
		return total / runs;
	}

	/** The typical explorer from BALANCE.md T3 (pre-dragon, no Green Leaf) over 15 h. */
	private static double simulateExplorer(final Random rnd, final int runs) {
		ElsewhereConfig.AccidentalTrips cfg = ElsewhereConfig.get().accidentalTrips;
		TripTrigger.Kind L = TripTrigger.Kind.LOCATION;
		TripTrigger.Kind A = TripTrigger.Kind.ACTIVITY;
		// {trigger, count, home?}
		Object[][] plan = {
			{new SimTrigger("pale_hollow.sleep", L, 0.02), 28, true}, {new SimTrigger("pale_hollow.sleep", L, 0.02), 11, false},
			{new SimTrigger("pale_hollow.sleep_near_pale_oak", L, 0.12), 1, false},
			{new SimTrigger("petalwind.long_fall", A, 0.15), 8, false}, {new SimTrigger("petalwind.long_fall", A, 0.15), 2, true},
			{new SimTrigger("petalwind.wind_launch", A, 0.08), 7, false}, {new SimTrigger("petalwind.wind_launch", A, 0.08), 5, true},
			{new SimTrigger("glasswaste.desert_noon", L, 0.15), 4, false},
			{new SimTrigger("verdigris_sea.storm_minute", L, 0.20), 3, false}, {new SimTrigger("verdigris_sea.storm_minute", L, 0.20), 4, true},
			{new SimTrigger("ironpeak.peak", L, 0.15), 3, false},
		};
		double total = 0;
		long span = 15L * 72000L;
		for (int r = 0; r < runs; r++) {
			List<Object[]> events = new ArrayList<>();
			for (Object[] row : plan) {
				for (int i = 0; i < (Integer)row[1]; i++) {
					events.add(new Object[] {(long)(rnd.nextDouble() * span), row[0], row[2]});
				}
			}
			events.sort((x, y) -> Long.compare((Long)x[0], (Long)y[0]));
			long last = -1;
			int trips = 0;
			TripState st = new TripState();
			for (Object[] ev : events) {
				long t = (Long)ev[0];
				SimTrigger tr = (SimTrigger)ev[1];
				boolean home = (Boolean)ev[2];
				// away events happen 10-40 minutes after arriving in a region (m about 0.65)
				double regionH = home ? 40.0 : 0.17 + rnd.nextDouble() * 0.5;
				boolean budget = tr.kind() != TripTrigger.Kind.ACTIVITY || st.spendBudget(tr.id(), t, cfg.hourlyBudgets.getOrDefault(tr.id(), 3));
				TripRules.Situation s = new TripRules.Situation(t, last, regionH, home, budget, false);
				if (rnd.nextDouble() < TripRules.effectiveChance(tr.base(), tr.kind(), s, cfg)) {
					trips++;
					last = t;
				}
			}
			total += trips;
		}
		return total / runs;
	}

	private static void tripSimulation() {
		Random rnd = new Random(7);
		double explorer = simulateExplorer(rnd, 20000);
		check(explorer > 1.4 && explorer < 2.4, f("typical exploring player (BALANCE.md T3 profile): %.2f trips per 15 h (target about 2)", explorer));
		TripTrigger.Kind L = TripTrigger.Kind.LOCATION;
		TripTrigger.Kind A = TripTrigger.Kind.ACTIVITY;
		Object[][] residents = {
			{"sleeping by a pale garden 3x/h", L, 0.12, 3, "pale_hollow.sleep_near_pale_oak"},
			{"drop shaft, 3 long falls/h", A, 0.15, 3, "petalwind.long_fall"},
			{"mace user, 4 wind launches/h", A, 0.08, 4, "petalwind.wind_launch"},
			{"desert house, 3 noons/h", L, 0.15, 3, "glasswaste.desert_noon"},
			{"house on a peak, 3/h", L, 0.15, 3, "ironpeak.peak"},
			{"Green Leaf at home, 12 uses/h", A, 0.015, 12, "greenhaze.green_leaf_use"},
			{"lives by an ancient city, 2 Wardens/h", A, 0.15, 2, "the_hush.warden"},
		};
		for (Object[] row : residents) {
			double m = simulateResident(rnd, 1000, (TripTrigger.Kind)row[1], (Double)row[2], (Integer)row[3], (String)row[4]);
			check(m < 2.0, f("resident for 100 h, worst case (%s): %.2f trips (target < 2)", row[0], m));
		}
	}

	// ------------------------------------------------------------------ live trip path
	private static void tripLive(final ServerLevel overworld) {
		FakePlayer p = fake(overworld, "Sleeper");
		overworld.getChunk(-3000 >> 4, 2500 >> 4);
		BlockPos away = new BlockPos(-3000, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, -3000, 2500), 2500);
		p.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0F, 0.0F);
		check(!AccidentalTrips.inHome(p), "a spot 4000 blocks from spawn is outside the home radius");
		double c = AccidentalTrips.currentChance(p, PaleHollowTrips.SLEEP, true);
		check(Math.abs(c - 0.04) < 1e-6 || Math.abs(c - 0.02) < 1e-6, f("live any-sleep chance away from home: %.3f%%", c * 100));
		AccidentalTrips.rollOverride = () -> 0.0;
		boolean fired = AccidentalTrips.fire(p, PaleHollowTrips.SLEEP);
		check(fired && AccidentalTrips.isPulling(p), "a successful roll starts the 3 s pull instead of teleporting at once");
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, p.damageSources().generic(), 1.0F, 1.0F, false);
		check(!AccidentalTrips.isPulling(p) && p.level() == overworld, "taking damage during the pull cancels the trip");
		AccidentalTrips.rollOverride = null;
		AccidentalTrips.startPull(p, PaleHollowTrips.SLEEP);
		MinecraftServer server = overworld.getServer();
		for (int i = 0; i < 62; i++) {
			invokeTripTick(server);
		}
		check(p.level().dimension().equals(PaleHollowDimension.LEVEL), "after the pull the player arrives in the Pale Hollow");
		boolean hasFrayed = false;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			hasFrayed |= p.getInventory().getItem(i).is(ModItems.FRAYED_WAYFINDER);
		}
		check(hasFrayed, "accidental arrival hands out a Frayed Wayfinder");
		TripState st = p.getAttachedOrCreate(ModAttachments.TRIP_STATE);
		check(st.lastTripPlayTicks >= 0, "trip time recorded for the 2 h cooldown");
		check(AccidentalTrips.currentChance(p, PaleHollowTrips.SLEEP, true) == 0.0, "no further trips from inside an Elsewhere dimension");
	}

	private static void invokeTripTick(final MinecraftServer server) {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
	}

	// ------------------------------------------------------------------ bosses
	private static float hitOnNetheriteProt4(final float raw) {
		// armor 20, toughness 12, Protection IV x4 = 16 EPF = 64% reduction
		// CombatRules.getDamageAfterAbsorb without a weapon: armor 20, toughness 12
		float toughness = 2.0F + 12.0F / 4.0F;
		float realArmor = Math.max(20.0F * 0.2F, Math.min(20.0F, 20.0F - raw / toughness));
		float afterArmor = raw * (1.0F - realArmor / 25.0F);
		return afterArmor * (1.0F - 0.64F);
	}

	private static void tickBoss(final Entity e, final int ticks) {
		for (int i = 0; i < ticks && !e.isRemoved(); i++) {
			e.tick();
		}
	}

	private static List<Item> dropsNear(final ServerLevel level, final Entity e) {
		List<Item> out = new ArrayList<>();
		for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(6.0))) {
			for (int i = 0; i < it.getItem().getCount(); i++) {
				out.add(it.getItem().getItem());
			}
		}
		return out;
	}

	private static void motherCreak(final ServerLevel hollow) {
		BlockPos top = new BlockPos(600, 90, 600);
		hollow.getChunk(top);
		MotherCreak boss = ElderTrunkArena.buildAndSpawn(hollow, top, null);
		check(boss != null, "Elder Trunk arena builds and Mother Creak spawns");
		if (boss == null) {
			return;
		}
		check(boss instanceof Enemy, "Mother Creak implements Enemy (Test Meter's x1.53 applies)");
		check(boss.getMaxHealth() == 360.0F && boss.getAttributeValue(Attributes.ARMOR) == 8.0 && boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 2.0,
			f("Mother Creak stats: HP %.0f (ladder 360 base / 551 effective), armor %.0f/8, toughness %.0f/2",
				boss.getMaxHealth(), boss.getAttributeValue(Attributes.ARMOR), boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		float top38 = hitOnNetheriteProt4(GearStats.MOTHER_CREAK_ROOT_BURST);
		check(Math.abs(top38 - 6.9F) < 0.35F, f("Root Burst 38 raw vs netherite Prot IV = %.1f HP (ladder 6.9)", top38));
		float claw = hitOnNetheriteProt4(GearStats.MOTHER_CREAK_CLAW);
		check(Math.abs(claw - 4.8F) < 0.35F, f("branch claw 30 raw vs netherite Prot IV = %.1f HP (ladder 4.8)", claw));
		check(boss.heartPositions().size() == 4, "4 resin hearts set in the trunk wall");
		check(!boss.isAwake() && !boss.hurtServer(hollow, hollow.damageSources().generic(), 10.0F), "dormant: she sleeps and ignores damage");

		boss.wake(hollow, 2);
		check(boss.isAwake() && Math.abs(boss.getMaxHealth() - 540.0F) < 0.01F, f("party scaling: 2 players -> %.0f HP (360 x 1.5)", boss.getMaxHealth()));
		check(boss.arena() != null && boss.arena().isRaised(), "arena seal raised on wake");
		tickBoss(boss, 45);
		check(boss.phase() == MotherCreak.PHASE_STILLNESS, "phase 1 Stillness with all 4 hearts");
		float hp = boss.getHealth();
		boss.hurtServer(hollow, hollow.damageSources().generic(), 20.0F);
		check(boss.getHealth() == hp, "invulnerable while hearts stand and she is not stunned");

		int[] expectPhase = {MotherCreak.PHASE_ROOT_BURST, MotherCreak.PHASE_LEAF_LITTER, MotherCreak.PHASE_LEAF_LITTER, MotherCreak.PHASE_FURY};
		String[] names = {"Root Burst", "Leaf Litter", "Leaf Litter", "Heartwood Fury"};
		FakePlayer heartMiner = null;
		for (int i = 0; i < 4; i++) {
			if (i == 0) {
				// the real mechanic: a survival player breaks a wall heart through the normal block-break path
				FakePlayer miner = fake(hollow, "HeartMiner");
				miner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				miner.snapTo(boss.getX(), boss.getY(), boss.getZ(), 0.0F, 0.0F);
				boolean broke = miner.gameMode.destroyBlock(boss.heartPositions().get(0));
				check(broke && hollow.getBlockState(boss.heartPositions().get(0)).isAir(), "Mother Creak: a survival player can mine a wall heart");
				heartMiner = miner;
			} else {
				hollow.setBlock(boss.heartPositions().get(i), Blocks.AIR.defaultBlockState(), 3);
			}
			tickBoss(boss, 1);
			check(boss.heartsLeft() == 3 - i && boss.phase() == expectPhase[i] && boss.stunTicks() > 0,
				f("heart %d broken -> phase %d (%s), stunned %.1f s", i + 1, boss.phase(), names[i], boss.stunTicks() / 20.0));
			tickBoss(boss, 41);
			if (i == 0) {
				float before = boss.getHealth();
				boss.hurtServer(hollow, hollow.damageSources().playerAttack(heartMiner), 20.0F);
				check(boss.getHealth() < before, f("Mother Creak: after a player mines a heart, a player attack damages her (hittable during the stun window) (took %.1f)", before - boss.getHealth()));
				tickBoss(boss, GearStats.MOTHER_CREAK_STUN_TICKS + 5);
				before = boss.getHealth();
				boss.hurtServer(hollow, hollow.damageSources().generic(), 20.0F);
				check(boss.getHealth() == before, "invulnerable again once the stun ends");
			}
		}
		check(boss.getAttributeValue(Attributes.ARMOR) == 0.0, "Heartwood Fury: bark plates fall (armor 8 -> 0)");
		tickBoss(boss, 30);
		float before = boss.getHealth();
		boss.hurtServer(hollow, hollow.damageSources().generic(), 40.0F);
		float taken = before - boss.getHealth();
		check(Math.abs(taken - BossGuards.overpower(40.0F)) < 0.6F, f("overpower guard: a 40 hit deals %.1f (24 + 16/2 = 32)", taken));

		FakePlayer victim = fake(hollow, "Victim");
		victim.snapTo(boss.getX() + 2, boss.getY(), boss.getZ(), 0.0F, 0.0F);
		float capped = BossGuards.lethalCap(victim, 38.0F, hollow.damageSources().mobAttack(boss));
		float after = BossGuards.afterArmor(victim, capped, hollow.damageSources().mobAttack(boss));
		check(after <= victim.getMaxHealth() * 0.6F + 0.01F, f("lethal-hit guard: an unarmored player takes %.1f of 20 HP from a 38 raw hit", after));

		// root burst spawns spikes along the telegraphed line (once the stun is over)
		tickBoss(boss, boss.stunTicks() + 2);
		boss.startRootBurst(hollow, victim);
		tickBoss(boss, MotherCreak.BURST_WINDUP + 6);
		int spikes = hollow.getEntitiesOfClass(dev.elsewhere.boss.mother_creak.RootSpike.class, boss.getBoundingBox().inflate(30)).size();
		check(spikes > 0, f("Root Burst: 1 s dust line, then %d root spikes erupt", spikes));

		// arena reset restores the snapshot
		BlockPos wallProbe = boss.arena().center.offset(ElderTrunkArena.RADIUS, 6, 0);
		// the floor decoration is random (1 in 10 floor cells carry a pale moss carpet), so compare with the state before the damage, not with air
		net.minecraft.world.level.block.state.BlockState probeBefore = hollow.getBlockState(boss.arena().center.offset(3, 0, 3));
		hollow.setBlock(boss.arena().center.offset(3, 0, 3), Blocks.STONE.defaultBlockState(), 3);
		boss.resetFight(hollow);
		check(!boss.isAwake() && boss.getHealth() == boss.getMaxHealth() && boss.getMaxHealth() == 360.0F,
			"reset when everyone leaves: asleep, full health, solo HP");
		check(hollow.getBlockState(boss.arena().center.offset(3, 0, 3)) == probeBefore && !boss.arena().isRaised(),
			"reset restores arena blocks and lowers the seal (probe now " + hollow.getBlockState(boss.arena().center.offset(3, 0, 3)) + ", raised " + boss.arena().isRaised()
				+ ", centre " + boss.arena().center.toShortString() + ", snapshot " + boss.arena().snapshotSize() + ")");
		check(!hollow.getBlockState(boss.heartPositions().get(0)).isAir(), "reset puts the broken hearts back");
		check(hollow.getBlockState(wallProbe) != null, "arena walls intact");

		// drops
		boss.wake(hollow, 1);
		tickBoss(boss, 45);
		boss.hurtServer(hollow, new net.minecraft.world.damagesource.DamageSource(hollow.damageSources().genericKill().typeHolder(), fake(hollow, "BossKiller")), Float.MAX_VALUE);
		tickBoss(boss, 2);
		List<Item> drops = dropsNear(hollow, boss);
		long bark = drops.stream().filter(i -> i == ModItems.PALE_BARK).count();
		Elsewhere.LOG.info("[selftest] Mother Creak drop list: {}", drops);
		check(boss.isDeadOrDying() && drops.contains(ModItems.RESIN_HEART_LOCKET) && drops.contains(ModItems.HEARTWOOD_GREATBOW) && bark >= 10,
			f("Mother Creak drops: Resin Heart Locket, Heartwood Greatbow, %d Pale Bark", bark));
	}

	private static void selenne(final ServerLevel hollow) {
		BlockPos center = new BlockPos(-600, 80, 900);
		hollow.getChunk(center);
		MoonringArena.build(hollow, center);
		check(hollow.getBlockState(center).is(ModBlocks.MOON_BLOOM), "Moonring builds with the Moon Bloom at its centre");
		int cauldrons = 0;
		for (BlockPos c : MoonringArena.cauldrons(center)) {
			if (hollow.getBlockState(c).is(Blocks.WATER_CAULDRON)) {
				cauldrons++;
			}
		}
		check(cauldrons == 12, "12 tuff monoliths each with a water cauldron (phase 3 reflections)");
		Selenne boss = SelenneSummon.summon(hollow, center, null);
		check(boss != null && boss.isAwake(), "picking the Moon Bloom wakes Selenne");
		if (boss == null) {
			return;
		}
		check(boss.getMaxHealth() == 460.0F && boss.getAttributeValue(Attributes.ARMOR) == 10.0 && boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 4.0,
			f("Selenne stats: HP %.0f (ladder 460 / 704), armor %.0f/10, toughness %.0f/4", boss.getMaxHealth(),
				boss.getAttributeValue(Attributes.ARMOR), boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		float beam = hitOnNetheriteProt4(GearStats.SELENNE_MOONBEAM);
		check(Math.abs(beam - 8.7F) < 0.4F, f("Moonbeam 44 raw vs netherite Prot IV = %.1f HP (ladder 8.7)", beam));
		tickBoss(boss, 45);
		check(boss.isOpen(), "phase 1 starts with the eye open");
		float hp = boss.getHealth();
		boss.hurtServer(hollow, hollow.damageSources().generic(), 20.0F);
		check(boss.getHealth() == hp, "open eye: hits bounce off");
		int guard = 0;
		while (boss.isOpen() && guard++ < 400) {
			tickBoss(boss, 1);
		}
		check(!boss.isOpen(), f("eye closes after %.1f s", guard / 20.0));
		boss.hurtServer(hollow, hollow.damageSources().generic(), 20.0F);
		check(boss.getHealth() < hp, f("closed eye: hittable (took %.1f)", hp - boss.getHealth()));

		boss.setHealth(boss.getMaxHealth() * 0.55F);
		tickBoss(boss, 2);
		check(boss.phase() == Selenne.PHASE_PETALS, "below 60%: phase 2 Petals");
		tickBoss(boss, 45);
		check(boss.crackMonolith(hollow) && boss.crackedCount() >= 1, "phase 2 cracks monoliths (less cover)");
		tickBoss(boss, 260);
		int wisps = hollow.getEntitiesOfClass(dev.elsewhere.palehollow.entity.EyeblossomWisp.class, boss.getBoundingBox().inflate(30)).size();
		check(wisps > 0, f("phase 2 summons Eyeblossom Wisps (%d)", wisps));

		boss.setHealth(boss.getMaxHealth() * 0.25F);
		tickBoss(boss, 2);
		check(boss.phase() == Selenne.PHASE_UNBLINKING && boss.lidsTorn(), "below 30%: phase 3 Unblinking, lids torn");
		tickBoss(boss, 45);
		check(boss.isOpen(), "phase 3: the eye never closes");
		float h3 = boss.getHealth();
		boss.hurtServer(hollow, hollow.damageSources().generic(), 20.0F);
		check(boss.getHealth() == h3, "phase 3: hits that do not come from a reflection are ignored");
		FakePlayer archer = fake(hollow, "Archer");
		BlockPos cauldron = MoonringArena.cauldronPos(center, 0);
		archer.snapTo(cauldron.getX() + 0.5, cauldron.getY(), cauldron.getZ() + 1.5, 0.0F, 0.0F);
		check(boss.atReflection(hollow, archer), "standing at a monolith's water cauldron counts as its reflection");
		boss.setHealth(boss.getMaxHealth() * 0.08F);
		tickBoss(boss, 3);
		check(boss.fullMoon() > 0, "under 10%: the Full Moon begins its 10 s charge");

		// reset restores cracked monoliths
		boss.resetFight(hollow);
		int intact = 0;
		for (int i = 0; i < MoonringArena.MONOLITHS; i++) {
			if (!hollow.getBlockState(MoonringArena.monolithBase(center, i).above(5)).isAir()) {
				intact++;
			}
		}
		check(intact == 12 && !boss.lidsTorn(), "reset restores every monolith and her lids");

		boss.wake(hollow, 1);
		tickBoss(boss, 45);
		boss.hurtServer(hollow, new net.minecraft.world.damagesource.DamageSource(hollow.damageSources().genericKill().typeHolder(), fake(hollow, "BossKiller")), Float.MAX_VALUE);
		tickBoss(boss, 2);
		List<Item> drops = dropsNear(hollow, boss);
		check(drops.contains(ModItems.EYEBLOSSOM_CIRCLET), "Selenne first kill drops the Eyeblossom Circlet (kit: signature 100%)");
		check(hollow.getEntitiesOfClass(Selenne.class, new AABB(center).inflate(40)).stream().noneMatch(Entity::isAlive), "Selenne is dead");
	}

	// ------------------------------------------------------------------ 0.4.0
	private static void update040(final MinecraftServer server, final ServerLevel overworld, final ServerLevel hollow) {
		// --- /elsewhere trips on|off (any player) and overworldContent
		check(ElsewhereConfig.get().overworldContent, "0.4.0: config overworldContent is a public field, default true");
		TripTrigger sleep = AccidentalTrips.triggers().get("pale_hollow.sleep");
		FakePlayer t = fake(overworld, "Toggler");
		t.snapTo(5000.5, 100, 5000.5, 0.0F, 0.0F);
		AccidentalTrips.rollOverride = () -> 0.0;
		try {
			check(sleep != null && AccidentalTrips.playerEnabled(t) && AccidentalTrips.allowed(t), "0.4.0: trips are on by default for a player");
			int r = server.getCommands().getDispatcher().execute("elsewhere trips off", t.createCommandSourceStack());
			check(r == 1 && !AccidentalTrips.playerEnabled(t), "0.4.0: /elsewhere trips off works for a non-op player");
			check(!AccidentalTrips.fire(t, sleep) && !AccidentalTrips.isPulling(t), "0.4.0: trips off -> a forced roll does not fire");
			AccidentalTrips.startPull(t, sleep);
			check(!AccidentalTrips.isPulling(t), "0.4.0: trips off -> no pull starts either");
			r = server.getCommands().getDispatcher().execute("elsewhere trips on", t.createCommandSourceStack());
			check(r == 1 && AccidentalTrips.playerEnabled(t), "0.4.0: /elsewhere trips on turns them back on");
			check(AccidentalTrips.fire(t, sleep) && AccidentalTrips.isPulling(t), "0.4.0: trips on -> the forced roll fires again");
			AccidentalTrips.setPlayerEnabled(t, false);
			check(!AccidentalTrips.isPulling(t), "0.4.0: switching trips off cancels a running pull");
			AccidentalTrips.setPlayerEnabled(t, true);
			r = server.getCommands().getDispatcher().execute("elsewhere trips", t.createCommandSourceStack());
			check(r == 1, "0.4.0: /elsewhere trips (status) works for a non-op player");
			boolean denied;
			try {
				server.getCommands().getDispatcher().execute("elsewhere home", t.createCommandSourceStack());
				denied = false;
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				denied = true;
			}
			check(denied, "0.4.0: other /elsewhere subcommands stay op-only");
			ElsewhereConfig.get().overworldContent = false;
			check(!AccidentalTrips.allowed(t) && !AccidentalTrips.fire(t, sleep), "0.4.0: overworldContent=false -> no accidental trip, even a forced roll");
		} catch (Exception e) {
			check(false, "0.4.0 trips commands threw " + e);
		} finally {
			ElsewhereConfig.get().overworldContent = true;
			AccidentalTrips.rollOverride = null;
			AccidentalTrips.setPlayerEnabled(t, false);
			AccidentalTrips.setPlayerEnabled(t, true);
		}

		// --- Wayfinder used inside its dimension (real use path with the shared cooldown)
		FakePlayer w = fake(overworld, "Wayfarer");
		w.snapTo(40.5, overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40), 40.5, 0.0F, 0.0F);
		net.minecraft.world.item.ItemStack wf = new net.minecraft.world.item.ItemStack(ModItems.WAYFINDER_PALE_HOLLOW);
		w.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wf);
		RiftTravel.lastRift = null;
		net.minecraft.world.InteractionResult r1 = w.gameMode.useItem(w, overworld, wf, net.minecraft.world.InteractionHand.MAIN_HAND);
		check(r1.consumesAction() && RiftTravel.lastRift != null && w.getCooldowns().isOnCooldown(wf),
			"0.4.0 Wayfinder: use in the Overworld opens a rift and starts the shared 60 s cooldown");
		RiftTravel.lastCooldownResync = -1;
		RiftTravel.travelNow(w, PaleHollowDimension.LEVEL);
		check(w.level() == hollow, "0.4.0 Wayfinder: arrived in the Pale Hollow");
		int resent = RiftTravel.lastCooldownResync;
		check(resent > 1000, f("0.4.0 Wayfinder bug reproduced + fixed: the server still has %d ticks of cooldown after the trip; it is now re-sent to the client on arrival (the client forgets cooldowns on a dimension change, so it showed none and the use did nothing)", resent));
		RiftTravel.lastRift = null;
		w.gameMode.useItem(w, hollow, wf, net.minecraft.world.InteractionHand.MAIN_HAND);
		check(RiftTravel.lastRift == null, "0.4.0 Wayfinder: during the remaining cooldown a use opens nothing (now visible as a cooldown)");
		w.getCooldowns().removeCooldown(w.getCooldowns().getCooldownGroup(wf));
		net.minecraft.world.InteractionResult r2 = w.gameMode.useItem(w, hollow, wf, net.minecraft.world.InteractionHand.MAIN_HAND);
		RiftEntity home = RiftTravel.lastRift;
		check(r2.consumesAction() && home != null && home.level() == hollow && home.color() == 0xF2EBDD,
			"0.4.0 Wayfinder: off cooldown, the attuned Wayfinder used inside its dimension opens a rift home (real use path)");
		if (home != null) {
			int open = (int)Math.round(ElsewhereConfig.get().wayfinders.riftOpenSeconds * 20);
			for (int i = 0; i <= open; i++) {
				home.tick();
			}
		}
		check(w.level() == overworld, "0.4.0 Wayfinder: the home rift takes the player back to the Overworld");
		check(RiftTravel.resyncWayfinderCooldown(w) > 0 && w.getCooldowns().isOnCooldown(wf), "0.4.0 Wayfinder: the home use started the cooldown again");

		// --- boss uniques: 50% first kill, 20% after (per player, saved)
		FakePlayer k = fake(hollow, "Slayer");
		BossDrops.rollOverride = () -> 0.3;
		try {
			boolean staff = ArsenalLinks.item(ArsenalLinks.ROOTCALL_STAFF).isPresent();
			boolean crown = ArsenalLinks.item(ArsenalLinks.ELDER_BARK_CROWN).isPresent();
			check(BossDrops.uniques(ModEntities.SELENNE).isEmpty() && BossDrops.uniques(ModEntities.MOTHER_CREAK).isEmpty(),
				"2.0: Selenne and Mother Creak uniques moved from BossDrops to the boss kit");
			check(BossDrops.kills(k, ModEntities.SELENNE) == 0, "0.4.0: a new player has no Selenne kills");
			int first = BossDrops.roll(ModEntities.SELENNE, k, k.getRandom()).size();
			BossDrops.recordKill(k, ModEntities.SELENNE);
			int repeat = BossDrops.roll(ModEntities.SELENNE, k, k.getRandom()).size();
			check(BossDrops.kills(k, ModEntities.SELENNE) == 1, "0.4.0: kills are counted per player and boss");
			check(first == 0 && repeat == 0, staff ? "2.0: BossDrops no longer rolls the Rootcall Staff (Selenne's kit drop rule does)"
				: "2.0: BossDrops rolls nothing for Selenne (rootcall_staff absent in this build)");
			int cf = BossDrops.roll(ModEntities.MOTHER_CREAK, k, k.getRandom()).size();
			BossDrops.recordKill(k, ModEntities.MOTHER_CREAK);
			int cr = BossDrops.roll(ModEntities.MOTHER_CREAK, k, k.getRandom()).size();
			check(cf == 0 && cr == 0, crown ? "2.0: BossDrops no longer rolls the Elder Bark Crown (Mother Creak's kit drop rule does)"
				: "2.0: BossDrops rolls nothing for Mother Creak (elder_bark_crown absent in this build)");
			BossDrops.rollOverride = () -> 0.1;
			{
				var other = dev.elsewhere.boss.kit.DropRule.Kind.OTHER;
				java.util.Map<String, Integer> miss = new java.util.HashMap<>();
				var u = java.util.List.of(dev.elsewhere.boss.kit.DropRule.other("rootcall_staff", () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
				int got = dev.elsewhere.boss.kit.DropRule.roll(false, miss, u, net.minecraft.util.RandomSource.create(1)).size();
				check(dev.elsewhere.boss.kit.DropRule.chance(other, true, 0) == 0.5F && dev.elsewhere.boss.kit.DropRule.chance(other, false, 0) == 0.33F
					&& dev.elsewhere.boss.kit.DropRule.chance(other, false, 9) == dev.elsewhere.boss.kit.DropRule.PITY_CAP
					&& (got == 1 ? miss.get("rootcall_staff") == 0 : miss.get("rootcall_staff") == 1),
					"2.0: Rootcall Staff / Elder Bark Crown follow the kit rule (50% first kill, 33% repeat, +10% pity per miss to 60%)");
			}
			check(ArsenalLinks.motherCreakExtras(k.getRandom()).isEmpty(), "0.4.0: Mother Creak no longer drops the crown on every kill");
			boolean thread = ArsenalLinks.item(ArsenalLinks.MOONSILK_THREAD).isPresent();
			int n = ArsenalLinks.selenneExtras(k.getRandom()).stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
			check(thread ? n >= 4 && n <= 8 : n == 0, thread ? f("0.4.0: Selenne drops %d Moonsilk Thread (4-8)", n)
				: "0.4.0: moonsilk_thread not registered in this build (arsenal absent): Selenne/Wisp thread skipped cleanly");
		} finally {
			BossDrops.rollOverride = null;
		}

		// --- attributes by id, kill heal off in boss arenas, Moon-Eye mark
		boolean pierce = BuiltInRegistries.ATTRIBUTE.containsKey(ArsenalLinks.PROJECTILE_PIERCE);
		net.minecraft.world.item.component.ItemAttributeModifiers bow = new net.minecraft.world.item.ItemStack(ModItems.HEARTWOOD_GREATBOW)
			.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		boolean bowHas = bow.modifiers().stream().anyMatch(e -> e.modifier().id().equals(Elsewhere.id("greatbow_pierce")) && e.modifier().amount() == 1.0);
		check(pierce ? bowHas : !bowHas, pierce ? "0.4.0: Heartwood Greatbow has projectile_pierce +1" : "0.4.0: projectile_pierce not registered (arsenal absent): Greatbow pierce skipped cleanly");
		boolean blade = ArsenalLinks.item(ArsenalLinks.ECHO_FORGED_BLADE).isPresent() && BuiltInRegistries.ATTRIBUTE.containsKey(ArsenalLinks.KILL_HEAL);
		if (blade) {
			net.minecraft.world.item.component.ItemAttributeModifiers b = new net.minecraft.world.item.ItemStack(ArsenalLinks.item(ArsenalLinks.ECHO_FORGED_BLADE).get())
				.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
			boolean heal = b.modifiers().stream().anyMatch(e -> e.modifier().id().equals(Elsewhere.id("echo_forged_kill_heal")) && e.modifier().amount() == 2.0);
			boolean dmg = b.modifiers().stream().anyMatch(e -> e.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
			check(heal && dmg, "0.4.0: Echo-Forged Blade has kill_heal +2 and keeps its attack damage");
		} else {
			check(!ArsenalLinks.APPLIED.contains("echo_forged_blade:kill_heal"), "0.4.0: echo_forged_blade/kill_heal absent in this build: skipped cleanly");
		}
		net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> bosses = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Elsewhere.id("bosses"));
		check(ModEntities.MOTHER_CREAK.builtInRegistryHolder().is(bosses) && ModEntities.SELENNE.builtInRegistryHolder().is(bosses),
			"0.4.0: Mother Creak and Selenne are #elsewhere:bosses (the arsenal's kill heal and dodge skip bosses and their fights)");
		check(!ElsewhereBoss.inActiveArena(k), "0.4.0: no awake boss nearby -> not in a boss arena (kill heal allowed)");
		dev.elsewhere.boss.selenne.Selenne sel = ModEntities.SELENNE.create(hollow, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (sel != null) {
			k.snapTo(9000.5, 120, 9000.5, 0.0F, 0.0F);
			sel.snapTo(9002.5, 120, 9000.5, 0.0F, 0.0F);
			sel.setArena(net.minecraft.core.BlockPos.containing(9000, 120, 9000), 20, 4, 20);
			hollow.addFreshEntity(sel);
			hollow.getEntitiesOfClass(ElsewhereBoss.class, k.getBoundingBox().inflate(96.0)); // warm-up query (GOTCHAS: arsenal)
			boolean before = ElsewhereBoss.inActiveArena(k);
			sel.wake(hollow);
			boolean during = ElsewhereBoss.inActiveArena(k);
			check(!before && during, f("0.4.0: inside an awake boss's arena -> ElsewhereBoss.inActiveArena (before wake %s, awake %s)", before, during));
			sel.discard();
		}
		net.minecraft.world.entity.animal.cow.Cow cow = net.minecraft.world.entity.EntityTypes.COW.create(hollow, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		boolean arsenal = BuiltInRegistries.ITEM.containsKey(ArsenalLinks.MOONSILK_THREAD);
		boolean marked = cow != null && ArsenalLinks.markForSpells(cow, k);
		check(arsenal ? marked : !marked, arsenal ? "0.4.0: Moon-Eye marks reach the arsenal's spell bonus" : "0.4.0: arsenal absent: Moon-Eye mark for spells skipped cleanly");
	}

	// ------------------------------------------------------------------ 2.0 phase 1c: Sap Tapper and Resin Kiln
	private static void phase1c(final ServerLevel hollow) {
		var offers = dev.elsewhere.palehollow.entity.SapTapper.offers();
		boolean staff = dev.elsewhere.palehollow.ArsenalLinks.item(dev.elsewhere.palehollow.entity.SapTapper.STAFF_ID).isPresent()
			&& dev.elsewhere.palehollow.ArsenalLinks.item(dev.elsewhere.palehollow.ArsenalLinks.MOONSILK_THREAD).isPresent();
		check(offers.size() == (staff ? 4 : 3) && offers.stream().noneMatch(o -> o.getResult().isEmpty()),
			"Sap Tapper offers " + offers.size() + " trades (Rootcall Staff " + (staff ? "sold, 1 per player" : "absent in this build, skipped") + ")");
		check(offers.get(0).getBaseCostA().count() == 12 && offers.get(0).getMaxUses() == 16, "Sap Tapper: 12 resin clumps -> 1 emerald, 16 per restock");
		if (staff) check(offers.get(3).getMaxUses() == 1 && offers.get(3).getBaseCostA().count() == 30, "Sap Tapper: Rootcall Staff for 30 Pale Bark + 2 Moonsilk Thread, one use");
		var reg = hollow.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var holder = reg.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Elsewhere.id("resin_kiln")));
		check(holder.isPresent(), "resin_kiln structure is registered from data");
		if (holder.isEmpty()) return;
		var found = hollow.getChunkSource().getGenerator().findNearestMapStructure(hollow, net.minecraft.core.HolderSet.direct(holder.get()), new net.minecraft.core.BlockPos(30000 + new java.util.Random().nextInt(400000), 64, 30000 + new java.util.Random().nextInt(400000)), 100, false);
		check(found != null, "a Resin Kiln is found by the structure search from a fresh area");
		if (found == null) return;
		net.minecraft.core.BlockPos p = found.getFirst();
		var chunk = hollow.getChunk(p.getX() >> 4, p.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
		check(chunk.getStartForStructure(holder.get().value()) != null, "the Resin Kiln chunk generates with its structure start");
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) hollow.getChunk((p.getX() >> 4) + dx, (p.getZ() >> 4) + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
		var box = new net.minecraft.world.phys.AABB(p.getX(), 0, p.getZ(), p.getX() + 1, 250, p.getZ() + 1).inflate(24, 0, 24);
		check(!hollow.getEntitiesOfClass(dev.elsewhere.palehollow.entity.SapTapper.class, box).isEmpty(), "the Resin Kiln has a Sap Tapper");
	}
}
