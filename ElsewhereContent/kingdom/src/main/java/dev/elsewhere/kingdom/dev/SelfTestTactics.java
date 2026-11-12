package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.SAMPLES;
import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;
import static dev.elsewhere.kingdom.dev.SelfTest.info;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.item.KingdomItems;
import dev.elsewhere.kingdom.tactics.Behaviour;
import dev.elsewhere.kingdom.tactics.Behaviours;
import dev.elsewhere.kingdom.tactics.SimpleTacticsProfile;
import dev.elsewhere.kingdom.tactics.SquadBrain;
import dev.elsewhere.kingdom.tactics.TacticsProfile;
import dev.elsewhere.kingdom.tactics.TacticsRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Squad-tactics framework checks (FIELD-TACTICS section 6): registry and typed profile, the shared library, 6.1 (Aldermark pass-through writes no Orders), 6.12 (no aimbot),
 * 6.17 (passive animals), 6.18 (non-provoking player), 6.20 (15 v 15 MSPT with a profile), 6.24 (fairness: no wall-hacks, no hidden stats), plus the faction-currency drop and Free
 * Company unit mapping of the 2.1 war lane.
 */
public final class SelfTestTactics {
	private SelfTestTactics() {}

	static final Identifier A = Factions.ALDERMARK, M = TestFactions.MARCHES;
	static Runnable tickHook = () -> { };
	static boolean hooked;
	static FakePlayer fp;
	// measurements filled by the tick hook
	static int arrows, hits, targetedPlayer, ticks;
	static final Set<UUID> seenArrows = new HashSet<>(), hitArrows = new HashSet<>();
	static float lastHp;

	static void hook() {
		if (hooked) return;
		hooked = true;
		ServerTickEvents.END_SERVER_TICK.register(s -> tickHook.run());
	}

	static TacticsProfile testProfile() {
		return SimpleTacticsProfile.builder(M).interval(10).minOdds(1.2).retreatOdds(0.55).blunder(0.1).behaviours(Behaviours.library()).build();
	}

	static void bench(String name, boolean tactics) {
		STEPS.add((srv, l) -> {
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			if (tactics) TacticsRegistry.register(testProfile());
			else TacticsRegistry.unregister(M);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			int madeA = 0, madeM = 0;
			for (int i = 0; i < 3 && madeA < 15; i++) madeA += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, -9)), A, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, SelfTestFactions.mix(Math.min(4, 15 - madeA - 1))).size();
			for (int i = 0; i < 3 && madeM < 15; i++) madeM += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 9)), M, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, SelfTestFactions.mix(Math.min(4, 15 - madeM - 1))).size();
			info("BENCH start: " + name + " (spawned " + madeA + " + " + madeM + ")");
			SAMPLES.clear();
			SelfTest.measuring = true;
			SelfTest.tickStart = 0;
			return 400;
		});
		STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			long decisions = 0, orders = 0, failures = 0;
			double maxPaths = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				Squad sq = k.squad();
				if (sq != null && sq.brain != null && k.isSquadLeader()) {
					decisions += sq.brain.decisions;
					orders += sq.brain.ordersIssued;
					failures += sq.brain.failures;
					maxPaths = Math.max(maxPaths, sq.brain.lastPathRequests);
				}
			}
			String line = String.format("BENCH %s: MSPT avg %.2f ms, p95 %.2f over %d ticks | brains: %d decisions, %d orders, %d failures, max path requests per decision %.0f", name, avg, p95, s.length, decisions,
				orders, failures, maxPaths);
			info(line);
			SelfTest.BENCH.add(line);
			check("6.20 " + name + ": MSPT average <= 2.0 ms and p95 <= 3.0 ms", s.length >= 390 && avg <= 2.0 && p95 <= 3.0, String.format("avg %.2f p95 %.2f", avg, p95));
			if (tactics) check("6.20 brains made decisions without failures and at most 4 path requests per decision", decisions > 0 && failures == 0 && maxPaths <= 4, decisions + " " + failures + " " + maxPaths);
			clean(l);
			return 2;
		});
	}

	static void add() {
		// ---- registry and typed profile ----------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			TestFactions.register();
			TacticsProfile p = SimpleTacticsProfile.builder(M).interval(3).build();
			check("builder clamps the decision interval to 10..20", p.decisionInterval() == 10 && SimpleTacticsProfile.builder(M).interval(99).build().decisionInterval() == 20, p.decisionInterval());
			TacticsProfile minimal = () -> M;
			check("a profile that implements only faction() has the documented defaults (20 ticks, odds 1.2/0.55, blunder 0.08, not pass-through)", minimal.decisionInterval() == 20
				&& minimal.minLocalOdds() == 1.2 && minimal.retreatOdds() == 0.55 && minimal.blunderRate() == 0.08 && !minimal.passThrough() && minimal.behaviours().isEmpty(), null);
			TacticsRegistry.unregister(M);
			check("no profile registered: of() gives Aldermark's baseline, a pass-through", TacticsRegistry.of(M) == TacticsRegistry.Baseline.PROFILE && TacticsRegistry.Baseline.PROFILE.passThrough(), null);
			KingdomApi.registerTactics(M, testProfile());
			check("KingdomApi.registerTactics registers; of() and get() return it", TacticsRegistry.of(M).decisionInterval() == 10 && TacticsRegistry.get(M) != null && TacticsRegistry.all().containsKey(M), null);
			TacticsRegistry.unregister(M);
			List<Behaviour> lib = Behaviours.library();
			check("the shared library has the 14 behaviours with unique ids", lib.size() == 14 && lib.stream().map(Behaviour::id).distinct().count() == 14, lib.size());
			check("Aldermark's baseline: interval 20, min odds 1.2, retreat 0.55, blunder 0.08, reaction 8-20", TacticsRegistry.Baseline.PROFILE.decisionInterval() == 20
				&& TacticsRegistry.Baseline.PROFILE.reactionTicksMin() == 8 && TacticsRegistry.Baseline.PROFILE.reactionTicksMax() == 20 && TacticsRegistry.Baseline.PROFILE.blunderRate() == 0.08, null);
			// 6.24 static part: the perception code reads no Standing and no inventory
			boolean clean = true;
			StringBuilder why = new StringBuilder();
			for (String cn : new String[] {"Perceiver", "Perception", "SquadMemory"}) {
				try (var in = SelfTestTactics.class.getResourceAsStream("/dev/elsewhere/kingdom/tactics/" + cn + ".class")) {
					String bytes = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
					for (String bad : new String[] {"dev/elsewhere/kingdom/Standing", "getInventory", "kingdom/war/WarState"}) if (bytes.contains(bad)) {
						clean = false;
						why.append(cn).append(" uses ").append(bad).append("; ");
					}
				}
			}
			check("6.24 fairness (static): Perception, Perceiver and SquadMemory reference no Standing, no player inventory and no war state", clean, why);
			return 2;
		});
		// ---- loot currency and Free Company unit mapping --------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomSoldier k = SelfTest.soldier(l, UnitTypes.KNIGHT, M, at(0, 0));
			ItemEntity drop = k.spawnAtLocation(l, new ItemStack(KingdomItems.ALDERMARK_CROWN, 3));
			check("a soldier of a faction with its own currency drops that instead of Aldermark Crowns (test faction: gold nugget, count kept)", drop != null && drop.getItem().is(net.minecraft.world.item.Items.GOLD_NUGGET)
				&& drop.getItem().getCount() == 3, drop == null ? "null" : drop.getItem());
			KingdomSoldier a = SelfTest.soldier(l, UnitTypes.KNIGHT, A, at(2, 0));
			ItemEntity ad = a.spawnAtLocation(l, new ItemStack(KingdomItems.ALDERMARK_CROWN, 2));
			check("an Aldermark soldier still drops Crowns", ad != null && ad.getItem().is(KingdomItems.ALDERMARK_CROWN), null);
			var r = l.getRandom();
			check("Free Company mapping falls back to the base company units when the Mirewick units are not loaded", dev.elsewhere.kingdom.FreeCompanies.skinned(r, dev.elsewhere.kingdom.FreeCompanies.BOLT)
				.equals(UnitTypes.exists(Identifier.fromNamespaceAndPath("elsewhere", "mirewick_fc_crossbow")) ? Identifier.fromNamespaceAndPath("elsewhere", "mirewick_fc_crossbow") : dev.elsewhere.kingdom.FreeCompanies.BOLT), null);
			clean(l);
			return 2;
		});
		// ---- 6.1 Aldermark pass-through ---------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			hook();
			TacticsRegistry.unregister(M);
			KingdomPerf.forceActive = true;
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 0)), A, Squad.Formation.LINE, UnitTypes.SERGEANT, SelfTestFactions.mix(4));
			for (int i = 0; i < 5; i++) SelfTest.zombie(l, at(-6 + i * 3, 14), true);
			info("6.1 spawned " + sq.size() + " Aldermark");
			return 400;
		});
		STEPS.add((srv, l) -> {
			long orders = 0;
			int brains = 0, withOrder = 0, alive = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				if (!A.equals(k.factionId())) continue;
				alive++;
				if (k.squad() != null && k.squad().brain != null) brains++;
				if (k.tacticsOrder != null) withOrder++;
				if (k.squad() != null && k.squad().brain != null) orders += k.squad().brain.ordersIssued;
			}
			long zl = l.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(-80, SelfTest.y - 20, -80, 80, SelfTest.y + 40, 80)).size();
			check("6.1 Aldermark with the baseline profile: no brain, 0 Orders, no soldier holds an order; the soldiers fought (zombies 5 -> " + zl + ")", brains == 0 && orders == 0 && withOrder == 0 && alive > 0 && zl < 5,
				brains + " " + orders + " " + withOrder + " alive " + alive + " zombies " + zl);
			clean(l);
			return 2;
		});
		// ---- 6.18 non-provoking player ------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			TacticsRegistry.register(testProfile());
			KingdomApi.setFactionHostileToPlayers(srv, M, false);
			fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("kingdom-tactics".getBytes()), "kingdom-tactics"));
			Vec3 pos = at(0, 0);
			fp.snapTo(pos.x, pos.y, pos.z, 0, 0);
			fp.setHealth(fp.getMaxHealth());
			Standing.set(l, fp.getUUID(), M, 0);
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 8)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, SelfTestFactions.mix(5));
			targetedPlayer = 0;
			ticks = 0;
			lastHp = fp.getHealth();
			tickHook = () -> {
				ticks++;
				for (KingdomSoldier k : KingdomPerf.loaded(l)) {
					if (k.getTarget() == fp) targetedPlayer++;
					if (k.tacticsOrder != null && fp.getUUID().equals(k.tacticsOrder.target())) targetedPlayer++;
				}
			};
			info("6.18 squad of " + sq.size() + " beside a Neutral player");
			return 1200;
		});
		STEPS.add((srv, l) -> {
			tickHook = () -> { };
			boolean anyBrain = false;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) if (k.squad() != null && k.squad().brain != null) anyBrain = true;
			check("6.18 a Neutral survival player beside a tactics squad for 1200 ticks: 0 targets, 0 orders at them, no damage (brain was active: " + anyBrain + ")", targetedPlayer == 0 && fp.getHealth() >= fp.getMaxHealth() - 0.01F && anyBrain,
				targetedPlayer + " hp " + fp.getHealth());
			clean(l);
			return 2;
		});
		// ---- 6.17 passive animals --------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			fp.snapTo(at(60, 60).x, at(60, 60).y, at(60, 60).z, 0, 0);
			List<LivingEntity> passives = new ArrayList<>();
			for (var t : List.of(EntityTypes.SHEEP, EntityTypes.COW, EntityTypes.VILLAGER, EntityTypes.PIG, EntityTypes.CHICKEN, EntityTypes.SHEEP)) {
				LivingEntity e = (LivingEntity) t.create(l, EntitySpawnReason.COMMAND);
				int i = passives.size();
				e.snapTo(at(-5 + i * 2, i % 2 == 0 ? 1 : -1).x, SelfTest.y, at(-5 + i * 2, i % 2 == 0 ? 1 : -1).z, 0, 0);
				((net.minecraft.world.entity.Mob) e).setPersistenceRequired();
				l.addFreshEntity(e);
				passives.add(e);
			}
			SelfTestTactics.passives = passives;
			List<Identifier> melee = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT);
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -9)), A, Squad.Formation.LINE, UnitTypes.SERGEANT, melee);
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 9)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, melee);
			return 1200;
		});
		STEPS.add((srv, l) -> {
			int hurt = 0;
			for (LivingEntity e : passives) if (!e.isAlive() || e.getHealth() < e.getMaxHealth()) hurt++;
			check("6.17 two kingdoms fighting each other (one with a tactics profile) around 6 passive animals and a villager for 1200 ticks: 0 hurt", hurt == 0, hurt);
			clean(l);
			return 2;
		});
		// ---- 6.12 no aimbot -----------------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Standing.set(l, fp.getUUID(), M, -60);
			fp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			Vec3 base = at(0, 0);
			fp.snapTo(base.x, base.y, base.z, 0, 0);
			fp.setHealth(fp.getMaxHealth());
			for (int i = 0; i < 4; i++) {
				KingdomSoldier ar = SelfTest.soldier(l, UnitTypes.ARCHER, M, at(-3 + i * 2, 15));
				ar.setNoAi(false);
			}
			arrows = 0;
			hits = 0;
			ticks = 0;
			seenArrows.clear();
			hitArrows.clear();
			lastHp = fp.getMaxHealth();
			tickHook = () -> {
				ticks++;
				// A FakePlayer is not in the level's entity index, so the soldiers' area scan cannot see it (a real player is found; the sight and
				// Standing rules are checked below with canAttack). The test hands them the target, so it measures aim and fire, not the scan.
				if (ticks % 10 == 1) for (KingdomSoldier k : KingdomPerf.loaded(l)) if (M.equals(k.factionId()) && k.getTarget() != fp && KingdomTargeting.canAttack(k, fp)) k.setTarget(fp);
				// the player sprint-strafes: 0.28 blocks a tick (sprint speed), reversing every 1.5 s (an 8.4-block sweep, a player running across an archer's front)
				int phase = (ticks / 30) % 2;
				fp.snapTo(base.x + (phase == 0 ? 1 : -1) * 0.28 * (ticks % 30) + (phase == 0 ? 0 : 8.4), base.y, base.z, 0, 0);
				// FakePlayer is invulnerable, so a hit is an arrow whose swept box touches the player's box
				for (Entity e : l.getEntities((Entity) null, new AABB(-40, SelfTest.y - 5, -40, 40, SelfTest.y + 20, 40), x -> x instanceof AbstractArrow)) {
					if (!(((AbstractArrow) e).getOwner() instanceof KingdomSoldier)) continue;
					if (seenArrows.add(e.getUUID())) arrows++;
					if (!hitArrows.contains(e.getUUID()) && e.getBoundingBox().expandTowards(e.getDeltaMovement()).inflate(0.1).intersects(fp.getBoundingBox())) {
						hitArrows.add(e.getUUID());
						hits++;
					}
				}
			};
			return 600;
		});
		STEPS.add((srv, l) -> {
			tickHook = () -> { };
			double rate = arrows == 0 ? 0 : hits / (double) arrows;
			int tgt = 0, shots = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				if (k.getTarget() == fp) tgt++;
				shots += k.shots;
			}
			KingdomSoldier probe = KingdomPerf.loaded(l).stream().filter(k -> M.equals(k.factionId())).findFirst().orElse(null);
			info("6.12 diagnostics: canAttack " + (probe != null && dev.elsewhere.kingdom.KingdomTargeting.canAttack(probe, fp)) + " creative " + fp.isCreative() + " spectator " + fp.isSpectator()
				+ " invulnerable " + fp.isInvulnerable() + " alive " + fp.isAlive() + " band " + Standing.bandOf(l, fp.getUUID(), M) + " loadedM " + (probe != null) + " active " + (probe != null && probe.isActive())
				+ " los " + (probe != null && probe.getSensing().hasLineOfSight(fp)) + " squad " + (probe != null && probe.squad() != null) + " resting " + (probe != null && probe.isResting()) + " routed " + (probe != null && probe.isRouted()) + " noAi " + (probe != null && probe.isNoAi()) + " tgt " + (probe == null ? "-" : String.valueOf(probe.getTarget()))
				+ " fpPos " + fp.position() + " probePos " + (probe == null ? "-" : probe.position().toString()));
			info("6.12 diagnostics: archers targeting the player " + tgt + ", shots " + shots + ", arrows seen " + arrows);
			check("6.12 archers vs a sprint-strafing player at 15 blocks for 30 s: they shoot (" + arrows + " arrows) and at most 35 percent hit (" + hits + ")", arrows >= 8 && rate <= 0.35, hits + "/" + arrows);
			fp.snapTo(at(60, 60).x, at(60, 60).y, at(60, 60).z, 0, 0);
			clean(l);
			return 2;
		});
		// ---- 6.24 fairness (ray part) -----------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Standing.set(l, fp.getUUID(), M, -60);
			for (int x = -6; x <= 6; x++) for (int yy = 0; yy < 5; yy++) l.setBlock(new BlockPos(BlockPos.containing(at(x, 0)).getX(), SelfTest.y + yy, BlockPos.containing(at(0, 0)).getZ()), Blocks.STONE.defaultBlockState(), 3);
			fp.snapTo(at(0, -6).x, SelfTest.y, at(0, -6).z, 0, 0);
			SelfTestTactics.wallSquad = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 6)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
			for (KingdomSoldier k : wallSquad) k.setNoAi(true); // brains keep deciding, the soldiers stand still behind the wall
			return 120;
		});
		STEPS.add((srv, l) -> {
			int seen = 0, decisions = 0;
			for (KingdomSoldier k : wallSquad) {
				SquadBrain b = k.squad() == null ? null : k.squad().brain;
				if (b != null && b.last != null) {
					decisions = (int) b.decisions;
					for (var c : b.last.contacts()) if (c.visibleNow() && c.id().equals(fp.getUUID())) seen++;
				}
			}
			check("6.24 fairness (rays): a player on the other side of a 5-high stone wall is never a visible contact of the squad (decisions made: " + decisions + ")", seen == 0, seen);
			for (int x = -6; x <= 6; x++) for (int yy = 0; yy < 5; yy++) l.setBlock(new BlockPos(BlockPos.containing(at(x, 0)).getX(), SelfTest.y + yy, BlockPos.containing(at(0, 0)).getZ()), Blocks.AIR.defaultBlockState(), 3);
			fp.snapTo(at(60, 60).x, at(60, 60).y, at(60, 60).z, 0, 0);
			clean(l);
			return 2;
		});
		// ---- 6.20 performance -----------------------------------------------------------------------------------------------------------------
		bench("15 v 15 field battle, Aldermark vs the test faction (tactics profile with the full library)", true);
		STEPS.add((srv, l) -> {
			TacticsRegistry.unregister(M);
			return 1;
		});
	}

	static List<LivingEntity> passives = new ArrayList<>();
	static List<KingdomSoldier> wallSquad = new ArrayList<>();
}
