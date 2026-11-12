package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.info;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.war.HrafnholtLongship;
import dev.elsewhere.kingdom.war.Longship;
import dev.elsewhere.kingdom.war.ShipShape;
import dev.elsewhere.kingdom.war.WarState;
import dev.elsewhere.kingdom.war.WaterPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sailing longship tests (RIVALS-DECISIONS 18), on a test fjord in force-loaded chunks away from the other tests: route found on the lake (around an island
 * wall), the budget and node cap, the ship reaching the shore in the expected time with 8 seated raiders, the raiders hopping off, the beached prop and its
 * full restore, the route cache, no route (walk fallback), no leftover ship after expiry, chunk unload or server stop, players unable to mount or break it,
 * pushing, the caps counting the raiders, the flag and the kill switch, and the measured per-tick cost.
 */
final class SelfTestLongship {
	private SelfTestLongship() {}

	static final Identifier SEA = Identifier.fromNamespaceAndPath("elsewhere", "test_sea_raiders");
	static final int CX0 = -2, CX1 = 1, CZ0 = 18, CZ1 = 26, FAR_CX = 190, FAR_CZ = 0;
	static int yy, waterY;
	static BlockPos target, pondTarget;
	static Longship.Shore shore;
	static Longship.Voyage voyage;
	static long expectTicks;
	static int roomBefore, loadedBefore;
	static final Map<String, Integer> CALLS = new HashMap<>();
	static final Map<BlockPos, BlockState> SNAP = new HashMap<>();
	static final List<UUID> CREW = new ArrayList<>();

	static Longship.Crew crew(String tag) {
		return new Longship.Crew() {
			@Override
			public List<KingdomSoldier> spawn(BlockPos at) {
				CALLS.merge(tag + ".spawn", 1, Integer::sum);
				List<KingdomSoldier> s = KingdomApi.spawnSquad(currentLevel, at, SEA, Squad.Formation.LINE, UnitTypes.SERGEANT, Collections.nCopies(7, UnitTypes.MAN_AT_ARMS));
				for (KingdomSoldier k : s) CREW.add(k.getUUID());
				if (!s.isEmpty() && s.get(0).squad() != null && target != null) s.get(0).squad().setObjective(target); // as a raid: march inland after landing
				return s;
			}

			@Override
			public void noRoute() {
				CALLS.merge(tag + ".noRoute", 1, Integer::sum);
			}

			@Override
			public void noCrew() {
				CALLS.merge(tag + ".noCrew", 1, Integer::sum);
			}

			@Override
			public void landed() {
				CALLS.merge(tag + ".landed", 1, Integer::sum);
				// where the crew is the moment the last raider has hopped off
				int ashore = 0, wet = 0, aboard = 0;
				for (UUID u : CREW) {
					if (!(currentLevel.getEntity(u) instanceof KingdomSoldier k) || !k.isAlive()) continue;
					if (k.isPassenger()) aboard++;
					else if (shore != null && k.getZ() >= shore.land().getZ() - 0.5 && k.position().distanceTo(Vec3.atBottomCenterOf(shore.land())) < 14) ashore++;
					if (currentLevel.getFluidState(k.blockPosition()).is(FluidTags.WATER)) wet++;
				}
				CALLS.put(tag + ".ashore", ashore);
				CALLS.put(tag + ".wet", wet);
				CALLS.put(tag + ".aboard", aboard);
			}

			@Override
			public void lost() {
				CALLS.merge(tag + ".lost", 1, Integer::sum);
			}
		};
	}

	static ServerLevel currentLevel;

	static FactionDef seaDef() {
		FactionDef base = TestFactions.def();
		FactionDef.Army a = base.army();
		FactionDef.Army army = new FactionDef.Army(a.units(), a.uniqueUnits(), a.makeup(), a.healthMultiplier(), a.damageMultiplier(), a.speedMultiplier(), a.sizeMultiplier(),
			a.patrolDay(), a.patrolNight(), a.brain(), Optional.of("longship"));
		return new FactionDef("Test Sea Raiders", Optional.empty(), base.banner(), base.tabard(), base.trim(), false, List.of(), List.of(), base.roster(), base.rulers(), List.of(),
			Optional.empty(), true, base.society(), base.economy(), army);
	}

	static void set(ServerLevel l, int x, int y, int z, BlockState s) {
		l.setBlock(new BlockPos(x, y, z), s, 2);
	}

	static int count(ServerLevel l, AABB box) {
		return l.getEntities(KingdomEntities.LONGSHIP, box, e -> true).size();
	}

	static AABB area() {
		return new AABB(CX0 * 16, yy - 10, CZ0 * 16, CX1 * 16 + 16, yy + 20, CZ1 * 16 + 16);
	}

	/** A ship with no route (it stays where it is) and {@code raiders} seated soldiers. */
	static HrafnholtLongship standalone(ServerLevel l, Vec3 at, int raiders) {
		HrafnholtLongship s = KingdomEntities.LONGSHIP.create(l, EntitySpawnReason.EVENT);
		s.snapTo(at.x, at.y, at.z, 0, 0);
		l.addFreshEntity(s);
		for (int i = 0; i < raiders; i++) {
			KingdomSoldier k = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, SEA, s.position().add(0, 1, 0), null);
			if (k != null) {
				Longship.board(s, k);
				CREW.add(k.getUUID());
			}
		}
		return s;
	}

	static void add() {
		// ---- set-up: a flag-on test faction, a fjord 25 wide and 97 long with an island wall, and a separate pond -------------------------------
		STEPS.add((srv, l) -> {
			currentLevel = l;
			SelfTest.clean(l);
			if (!Factions.exists(SEA)) Factions.register(SEA, 0x2E5A2C, false);
			FactionDefs.putCodeDefined(SEA, seaDef());
			for (int cx = CX0; cx <= CX1; cx++) for (int cz = CZ0; cz <= CZ1; cz++) l.setChunkForced(cx, cz, true);
			l.setChunkForced(FAR_CX, FAR_CZ, true);
			KingdomPerf.testObservers.add(OBSERVER); // a watcher at the village, as a player would be at a real raid
			return 60;
		});
		STEPS.add((srv, l) -> {
			currentLevel = l;
			yy = SelfTest.y;
			waterY = yy - 1;
			BlockState w = Blocks.WATER.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
			for (int x = -12; x <= 12; x++) for (int z = 300; z <= 396; z++) for (int d = 1; d <= 3; d++) set(l, x, yy - d, z, w);
			for (int x = -12; x <= 1; x++) for (int z = 338; z <= 342; z++) for (int d = 1; d <= 3; d++) set(l, x, yy - d, z, stone); // island wall
			for (int x = 20; x <= 28; x++) for (int z = 300; z <= 310; z++) for (int d = 1; d <= 3; d++) set(l, x, yy - d, z, w); // a pond with no way out
			target = new BlockPos(0, yy, 420);
			pondTarget = new BlockPos(24, yy, 318);
			SNAP.clear();
			for (BlockPos q : BlockPos.betweenClosed(new BlockPos(-16, yy - 4, 374), new BlockPos(16, yy + 12, 402))) SNAP.put(q.immutable(), l.getBlockState(q));
			Longship.Style ws = Longship.parse(com.google.gson.JsonParser.parseString("{\"waterfront\": {\"longship\": {\"hull\": \"minecraft:dark_oak_planks\", \"keel\": \"minecraft:gold_block\","
				+ " \"sail\": [\"minecraft:green_wool\", \"minecraft:brown_wool\"], \"shield\": \"minecraft:green_banner\", \"shield_alt\": \"minecraft:brown_banner\"}}}").getAsJsonObject());
			Longship.Style def = Longship.parse(new com.google.gson.JsonObject());
			check("longship: waterfront.longship styles the ship (hull, green and brown sail, banner colours to shields; a valuable keel refused), defaults otherwise",
				ws.hull() == Blocks.DARK_OAK_PLANKS && ws.keel() == def.keel() && ws.sail() == Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.GREEN) && ws.sailAlt() == Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN) && ws.shield() == Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.GREEN)
					&& ws.shieldAlt() == Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BROWN) && def.hull() == Blocks.SPRUCE_PLANKS && def.sail() == Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.GREEN), ws);
			check("longship: the test faction carries army.landing = longship, Aldermark does not", Longship.enabled(SEA) && !Longship.enabled(Factions.ALDERMARK), null);
			shore = Longship.findShore(l, target);
			check("longship: shore search on the fjord finds the beach facing the village (z 397, bow pointing +z)", shore != null && shore.land().getZ() == 397 && shore.dz() == 1 && shore.waterY() == waterY,
				shore);
			return 2;
		});
		// ---- flag off, kill switch, and a route that does not exist (walk fallback) ---------------------------------------------------------------
		STEPS.add((srv, l) -> {
			currentLevel = l;
			if (shore == null) return 1;
			Longship.Voyage off = Longship.sail(l, Factions.ALDERMARK, target, shore, 0, -1, 900, crew("off"));
			check("longship: flag off means no voyage and no ship", off == null && Longship.voyages().isEmpty() && count(l, area()) == 0, null);
			check("longship: kill switch war.sailingLongships exists, default on", KingdomConfig.sailingLongships && KingdomConfig.asMap().containsKey("war.sailingLongships"), null);
			Longship.Shore ps = Longship.findShore(l, pondTarget);
			Longship.Voyage pond = ps == null ? null : Longship.sail(l, SEA, pondTarget, ps, 0, -1, 899, crew("pond"));
			check("longship: a pond landing starts a route search (the run-in is water)", pond != null && pond.phase == Longship.Phase.ROUTING, ps);
			return 30;
		});
		STEPS.add((srv, l) -> {
			currentLevel = l;
			WaterPath.Job j = Longship.lastJob;
			check("longship: no water route out of the pond: the voyage gives up, the raiders walk (no ship, no crew, no prop)", CALLS.getOrDefault("pond.noRoute", 0) == 1
				&& CALLS.getOrDefault("pond.spawn", 0) == 0 && count(l, area()) == 0 && Longship.voyages().isEmpty()
				&& WarState.get(l).data.ships.stream().noneMatch(r -> r.battle == 899), CALLS);
			check("longship: the failed search stayed under the node cap and the per-tick budget (worst slice under 4 ms of server-thread CPU)", j != null && j.expanded <= WaterPath.MAX_NODES && j.maxSliceCpu < 4_000_000L,
				j == null ? "no job" : j.expanded + " nodes, " + j.slices + " slices, max slice " + j.maxSliceCpu / 1000 + " us CPU, " + j.maxSlice / 1000 + " us wall");
			// the real voyage on the fjord
			Vec3 startGuess = new Vec3(0.5, waterY, 306.5);
			roomBefore = KingdomPerf.room(l, startGuess);
			loadedBefore = KingdomPerf.loadedCount();
			voyage = Longship.sail(l, SEA, target, shore, 0, -1, 901, crew("fjord"));
			check("longship: flag on: a voyage starts at the hand-off (route search queued)", voyage != null && voyage.phase == Longship.Phase.ROUTING && voyage.job != null, null);
			HrafnholtLongship.tickNanos = 0;
			HrafnholtLongship.ticks = 0;
			Longship.voyageNanos = 0;
			Longship.voyageTicks = 0;
			return 25;
		});
		// ---- the route, the launch, seats, caps, players --------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			currentLevel = l;
			if (voyage == null) return 1;
			WaterPath.Job j = voyage.job;
			List<Vec3> p = voyage.path;
			boolean allWater = p != null;
			if (p != null) for (Vec3 q : p) if (!l.getFluidState(BlockPos.containing(q.x, waterY, q.z)).is(FluidTags.WATER)) allWater = false;
			double startDist = p == null ? 0 : p.get(0).distanceTo(Vec3.atCenterOf(Longship.beachCentre(shore)));
			boolean aroundWall = false;
			if (p != null) for (int i = 1; i < p.size(); i++) {
				Vec3 a = p.get(i - 1), b = p.get(i);
				if ((a.z - 340) * (b.z - 340) <= 0) {
					double t = (340 - a.z) / (b.z - a.z == 0 ? 1 : b.z - a.z), x = a.x + (b.x - a.x) * t;
					if (x >= 2) aroundWall = true;
				}
			}
			check("longship: route found on the test fjord, all on water, around the island wall, entering ~80 blocks out", p != null && allWater && aroundWall && startDist >= 70,
				(p == null ? "none" : p.size() + " points, start " + p.get(0) + " dist " + Math.round(startDist)) + " entries " + (j == null ? "-" : j.entries()));
			check("longship: route search capped and budgeted (<= 2000 nodes, <= 200 nodes and ~0.5 ms per tick)", j != null && j.expanded <= WaterPath.MAX_NODES && j.maxSliceCpu < 4_000_000L,
				j == null ? "none" : j.expanded + " nodes in " + j.slices + " ticks, total " + j.nanos / 1000 + " us, max slice " + j.maxSliceCpu / 1000 + " us CPU, " + j.maxSlice / 1000 + " us wall");
			HrafnholtLongship ship = voyage.ship == null ? null : l.getEntity(voyage.ship) instanceof HrafnholtLongship s ? s : null;
			int seated = 0;
			if (ship != null) for (Entity e : ship.getPassengers()) if (e instanceof KingdomSoldier k && k.getVehicle() == ship) seated++;
			check("longship: the ship sails with 8 raiders seated aboard", ship != null && voyage.phase == Longship.Phase.SAILING && seated == 8 && voyage.raiders.size() == 8,
				ship == null ? voyage.phase : seated + " seated");
			check("longship: the 15/30 caps count the raiders (8 fewer free places, 8 more loaded soldiers)", KingdomPerf.loadedCount() >= loadedBefore + 8
				&& KingdomPerf.room(l, ship == null ? Vec3.ZERO : ship.position()) <= roomBefore - 8, roomBefore + " -> " + (ship == null ? -1 : KingdomPerf.room(l, ship.position())));
			if (ship == null || p == null) return 1;
			FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("longship-test".getBytes()), "longship-test"));
			fp.snapTo(ship.getX() + 1, ship.getY() + 1, ship.getZ());
			boolean rode = fp.startRiding(ship) || fp.startRiding(ship, true, true);
			var res = ship.interact(fp, InteractionHand.MAIN_HAND, ship.position());
			check("longship: a player cannot ride it (startRiding refused even forced, interact passes)", !rode && fp.getVehicle() == null && !res.consumesAction(), res);
			boolean hurt = ship.hurtServer(l, l.damageSources().playerAttack(fp), 1000F);
			int items = l.getEntities((Entity) null, ship.getBoundingBox().inflate(16), e -> e instanceof ItemEntity).size();
			Entity raider = ship.getFirstPassenger();
			check("longship: it cannot be broken or stolen: no damage, not pickable, not attackable, arrows pass it, nothing dropped", !hurt && ship.isAlive() && !ship.isPickable()
				&& !ship.isAttackable() && !ship.canBeHitByProjectile() && items == 0, null);
			check("longship: its raiders can be shot (pickable, hit by projectiles)", raider != null && raider.isPickable() && raider.canBeHitByProjectile(), null);
			Vec3 before = ship.getDeltaMovement();
			ship.push(fp);
			check("longship: it can be pushed (a player's push moves it even with a crew aboard)", ship.isPushable() && ship.getDeltaMovement().lengthSqr() > before.lengthSqr(), ship.getDeltaMovement());
			fp.discard();
			double len = WaterPath.length(p);
			double cur = ship.position().distanceTo(p.get(0));
			expectTicks = (long) Math.ceil(len / HrafnholtLongship.SPEED) + 40; // boat speed plus the slow glide onto the beach
			long allowed = (long) (expectTicks * 1.25) + 2L * ShipShape.CAPACITY * HrafnholtLongship.HOP_TICKS + 40;
			info("longship route " + Math.round(len) + " blocks, expected sailing " + expectTicks + " ticks, already at " + Math.round(cur) + " blocks");
			return (int) Math.max(1, allowed - (l.getGameTime() - voyage.launched));
		});
		// ---- beached: raiders ashore, ship gone, prop placed, per-tick cost ---------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			currentLevel = l;
			if (voyage == null) return 1;
			long sail = voyage.landedAt - voyage.launched - 2L * HrafnholtLongship.HOP_TICKS - (long) ShipShape.CAPACITY * HrafnholtLongship.HOP_TICKS;
			check("longship: reaches the shore within the expected time (boat speed)", voyage.phase == Longship.Phase.LANDED && CALLS.getOrDefault("fjord.landed", 0) == 1
				&& sail <= expectTicks * 1.25, voyage.phase + " sailed ~" + sail + " ticks, expected " + expectTicks);
			int ashore = CALLS.getOrDefault("fjord.ashore", -1), wet = CALLS.getOrDefault("fjord.wet", -1), aboard = CALLS.getOrDefault("fjord.aboard", -1);
			check("longship: all 8 raiders hopped off onto the beach (none aboard, none in the water when the ship goes)", ashore == 8 && wet == 0 && aboard == 0,
				ashore + " ashore, " + wet + " wet, " + aboard + " aboard");
			check("longship: the ship is gone after landing (despawned, none left in the area)", voyage.ship != null && l.getEntity(voyage.ship) == null && count(l, area()) == 0, null);
			Longship.ShipRec rec = WarState.get(l).data.ships.stream().filter(r -> r.battle == 901).findFirst().orElse(null);
			BlockPos stem = shore.land().offset(0, 1, -1);
			check("longship: the beached prop replaces it in the same place (ShipShape blocks, bow stem on the last water column)", rec != null && rec.blocks.size() >= 80
				&& l.getBlockState(stem).is(Blocks.DARK_OAK_LOG), rec == null ? "none" : rec.blocks.size() + " blocks, stem " + l.getBlockState(stem));
			double perTick = HrafnholtLongship.ticks == 0 ? -1 : HrafnholtLongship.tickNanos / 1000.0 / HrafnholtLongship.ticks;
			double hook = Longship.voyageTicks == 0 ? -1 : Longship.voyageNanos / 1000.0 / Longship.voyageTicks;
			WaterPath.Job j = voyage.job;
			info(String.format(java.util.Locale.ROOT, "BENCH longship: ship tick avg %.2f us over %d ticks; war-hook voyage step avg %.2f us over %d ticks; route search %d nodes, %d ticks, total %.2f ms, max slice %.3f ms wall / %.3f ms CPU",
				perTick, HrafnholtLongship.ticks, hook, Longship.voyageTicks, j == null ? 0 : j.expanded, j == null ? 0 : j.slices, j == null ? 0 : j.nanos / 1e6, j == null ? 0 : j.maxSlice / 1e6,
				j == null ? 0 : j.maxSliceCpu / 1e6));
			check("longship: per tick the ship only steps along its route (average under 50 us per ship tick)", perTick >= 0 && perTick < 50, String.format(java.util.Locale.ROOT, "%.2f us", perTick));
			return 2;
		});
		// ---- restore, cache, expiry, server stop -------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			currentLevel = l;
			Longship.restoreBattle(l, 901);
			int diff = 0;
			for (Map.Entry<BlockPos, BlockState> e : SNAP.entrySet()) if (l.getBlockState(e.getKey()) != e.getValue()) diff++;
			check("longship: raid end restores every block of the beached prop (water included)", diff == 0 && WarState.get(l).data.ships.stream().noneMatch(r -> r.battle == 901), diff + " differ");
			int hits = Longship.routeCacheHits;
			Longship.Voyage again = shore == null ? null : Longship.sail(l, SEA, target, shore, 0, -1, 902, crew("again"));
			check("longship: the route is cached per realm and target (second raid reuses it, no new search)", again != null && again.job == null && again.path != null && Longship.routeCacheHits == hits + 1, null);
			Longship.cancel(l, 902);
			check("longship: a cancelled raid leaves no voyage", Longship.voyages().isEmpty(), null);
			// expiry: 10 minutes at sea without landing
			Vec3 route = new Vec3(0.5, waterY, 320.5);
			HrafnholtLongship old = standalone(l, route, 2);
			List<UUID> oldCrew = new ArrayList<>();
			for (Entity e : old.getPassengers()) oldCrew.add(e.getUUID());
			old.tickCount = HrafnholtLongship.LIFETIME + 1;
			// server stop: the hook removes ships and their crews
			HrafnholtLongship stop = standalone(l, route, 1);
			List<UUID> stopCrew = new ArrayList<>();
			for (Entity e : stop.getPassengers()) stopCrew.add(e.getUUID());
			Longship.onServerStopping(srv);
			boolean stopGone = stop.isRemoved();
			for (UUID u : stopCrew) if (l.getEntity(u) != null) stopGone = false;
			check("longship: server stop removes every ship and its crew (nothing saved, nothing left)", stopGone && stopCrew.size() == 1 && Longship.voyages().isEmpty(), null);
			// old was also removed by the stop hook; spawn a fresh one for the expiry check
			HrafnholtLongship exp = standalone(l, route, 2);
			oldCrew.clear();
			for (Entity e : exp.getPassengers()) oldCrew.add(e.getUUID());
			exp.tickCount = HrafnholtLongship.LIFETIME + 1;
			expShip = exp.getUUID();
			expCrew = oldCrew;
			return 3;
		});
		STEPS.add((srv, l) -> {
			currentLevel = l;
			boolean gone = l.getEntity(expShip) == null;
			for (UUID u : expCrew) if (l.getEntity(u) != null) gone = false;
			int items = l.getEntities((Entity) null, area(), e -> e instanceof ItemEntity).size();
			check("longship: no leftover ship after its 10 minutes (ship and crew gone, nothing dropped)", gone && expCrew.size() == 2 && count(l, area()) == 0 && items == 0, null);
			// chunk unload: a ship in a far forced chunk, then the chunk is released
			BlockPos far = new BlockPos(FAR_CX * 16 + 8, yy, FAR_CZ * 16 + 8);
			HrafnholtLongship u = standalone(l, Vec3.atBottomCenterOf(far), 1);
			unloadShip = u.getUUID();
			unloadCrew = new ArrayList<>();
			unloadRefs = new ArrayList<>();
			unloadRefs.add(u);
			for (Entity e : u.getPassengers()) {
				unloadCrew.add(e.getUUID());
				unloadRefs.add(e);
			}
			l.setChunkForced(FAR_CX, FAR_CZ, false);
			return 200;
		});
		STEPS.add((srv, l) -> {
			currentLevel = l;
			boolean gone = l.getEntity(unloadShip) == null;
			for (UUID u : unloadCrew) if (l.getEntity(u) != null) gone = false;
			StringBuilder left = new StringBuilder();
			for (Entity e : unloadRefs) if (!e.isRemoved()) left.append(e.getClass().getSimpleName()).append(' ');
			check("longship: chunk unload removes the ship with its crew (the entities are removed, not just hidden)", gone && left.isEmpty() && unloadCrew.size() == 1,
				left.isEmpty() ? null : "still in memory: " + left);
			l.setChunkForced(FAR_CX, FAR_CZ, true);
			return 60;
		});
		STEPS.add((srv, l) -> {
			currentLevel = l;
			AABB box = new AABB(FAR_CX * 16 - 8, yy - 10, FAR_CZ * 16 - 8, FAR_CX * 16 + 24, yy + 20, FAR_CZ * 16 + 24);
			boolean back = l.getEntity(unloadShip) != null || l.getEntities(KingdomEntities.LONGSHIP, box, e -> true).size() > 0;
			StringBuilder came = new StringBuilder();
			for (UUID u : unloadCrew) {
				Entity e = l.getEntity(u);
				if (e == null) continue;
				back = true;
				came.append(e.getClass().getSimpleName()).append(e.isPassenger() ? " aboard" : "").append(e.entityTags()).append(' ');
			}
			if (l.getEntity(unloadShip) != null) came.append("the ship");
			check("longship: reloading that chunk brings nothing back (ships and crews are never saved)", !back, came.isEmpty() ? null : came.toString());
			l.setChunkForced(FAR_CX, FAR_CZ, false);
			// clean up: restore the ground, release chunks, forget the test faction
			BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
			for (int x = -12; x <= 28; x++) for (int z = 300; z <= 396; z++) {
				set(l, x, yy - 1, z, grass);
				set(l, x, yy - 2, z, dirt);
				set(l, x, yy - 3, z, dirt);
			}
			for (UUID u : CREW) {
				Entity e = l.getEntity(u);
				if (e != null) e.discard();
			}
			for (Entity e : l.getEntities((Entity) null, area(), e -> e instanceof KingdomSoldier || e instanceof HrafnholtLongship)) e.discard();
			for (int cx = CX0; cx <= CX1; cx++) for (int cz = CZ0; cz <= CZ1; cz++) l.setChunkForced(cx, cz, false);
			Longship.resetVoyages();
			WarState.get(l).data.ships.removeIf(r -> r.battle >= 899 && r.battle <= 902);
			FactionDefs.removeCodeDefined(SEA);
			KingdomPerf.testObservers.remove(OBSERVER);
			SelfTest.clean(l);
			info("longship tests done");
			return 1;
		});
	}

	static final Vec3 OBSERVER = new Vec3(0.5, -60, 418.5);
	static UUID expShip, unloadShip;
	static List<UUID> expCrew = List.of(), unloadCrew = List.of();
	static List<Entity> unloadRefs = List.of();
}
