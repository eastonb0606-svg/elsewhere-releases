package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom_villages.AldermarkCommand;
import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Conquest;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.RealmLocator;
import dev.elsewhere.kingdom_villages.RoyalCofferBlock;
import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.SiegeFireBlock;
import dev.elsewhere.kingdom_villages.Steward;
import dev.elsewhere.kingdom_villages.VillageLedger;
import dev.elsewhere.kingdom_villages.VillageManager;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesContent;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tick-driven self-test for the villages module (dev only, dropped at merge). Builds real plains villages (StructureStart
 * registered in the chunks, exactly like worldgen), then walks the whole life cycle and the safety rules. Logs
 * "[villages-selftest] PASS|FAIL ..." and a TOTAL, plus a LOAD line (full siege + rebuild in progress + 30 soldiers), and
 * writes run/selftest-villages.log.
 */
public final class SelfTest {
	private SelfTest() {}

	public static final List<String> placeholders = new ArrayList<>();
	static final List<String> LOG = new ArrayList<>();
	static int pass, fail, tick, phase;
	static long phaseStart;
	static ServerLevel level;
	static VillageRecord A, B, C, D, E;
	static StructureStart sA, sB, sC, sD, sE;
	static int fortWait;
	static final boolean PROFILE = true;
	static BlockPos farmTest;
	static long inhabitedRoadChunk = Long.MIN_VALUE;
	static BlockPos glow, fireAt, castle;
	static final Map<Long, BlockState> burnOriginals = new HashMap<>();
	static final List<Long> SAMPLES = new ArrayList<>();
	/** Server-thread CPU per tick: unlike wall time it does not grow when other JVMs on a shared machine take the cores. */
	static final List<Long> CPU_SAMPLES = new ArrayList<>();
	static final java.lang.management.ThreadMXBean MX = java.lang.management.ManagementFactory.getThreadMXBean();
	static long tickStart, cpuStart;
	static boolean measuring;
	static VillageRecord outpost, road;

	static void check(String name, boolean ok, @Nullable Object detail) {
		String line = "[villages-selftest] " + (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " :: " + detail);
		if (ok) pass++;
		else fail++;
		LOG.add(line);
		System.out.println(line);
	}

	static void info(String s) {
		String line = "[villages-selftest] " + s;
		LOG.add(line);
		System.out.println(line);
	}

	public static void startTick(MinecraftServer srv) {
		if (measuring) {
			tickStart = System.nanoTime();
			cpuStart = MX.getCurrentThreadCpuTime();
		}
	}

	public static void tick(MinecraftServer srv) {
		if (measuring && tickStart != 0) {
			SAMPLES.add(System.nanoTime() - tickStart);
			if (cpuStart >= 0) CPU_SAMPLES.add(MX.getCurrentThreadCpuTime() - cpuStart);
		}
		tick++;
		if (tick < 40 || phase < 0) return;
		try {
			step(srv);
		} catch (Throwable t) {
			check("phase " + phase + " threw", false, t);
			t.printStackTrace();
			finish(srv);
		}
	}

	static void next() {
		phase++;
		phaseStart = tick;
	}

	static int age() {
		return tick - (int) phaseStart;
	}

	static void step(MinecraftServer srv) {
		switch (phase) {
			case 0 -> setup(srv);
			case 1 -> discovery();
			case 2 -> siegeRunning();
			case 3 -> restoreAndFall();
			case 4 -> fortifying();
			case 5 -> liberation();
			case 6 -> steward();
			case 7 -> serfs();
			case 8 -> conquest();
			case 9 -> conquestCapture();
			case 10 -> finalRestore();
			case 11 -> loadSetup();
			case 12 -> loadMeasure(srv);
			default -> {
				if (RivalsTest.step(srv)) finish(srv);
			}
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	public static StructureStart village(ServerLevel l, int cx, int cz) {
		var reg = l.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> h = reg.getOrThrow(BuiltinStructures.VILLAGE_PLAINS);
		var gen = l.getChunkSource().getGenerator();
		var rs = l.getChunkSource().randomState();
		ChunkPos cp = new ChunkPos(cx, cz);
		for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
			l.setChunkForced(cx + dx, cz + dz, true);
			l.getChunk(cx + dx, cz + dz);
		}
		StructureStart start = h.value().generate(h, l.dimension(), l.registryAccess(), gen, gen.getBiomeSource(), rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), rs,
			l.getServer().getStructureTemplateManager(), l.getSeed(), cp, 0, l, b -> true);
		if (!start.isValid()) return start;
		BoundingBox bb = start.getBoundingBox();
		l.getChunk(cx, cz).setStartForStructure(h.value(), start);
		for (int x = bb.minX() >> 4; x <= bb.maxX() >> 4; x++) for (int z = bb.minZ() >> 4; z <= bb.maxZ() >> 4; z++) {
			LevelChunk c = l.getChunk(x, z);
			c.addReferenceForStructure(h.value(), cp.pack());
			BoundingBox cb = new BoundingBox(x << 4, l.getMinY(), z << 4, (x << 4) + 15, l.getMaxY(), (z << 4) + 15);
			start.placeInChunk(l, l.structureManager(), gen, l.getRandom(), cb, new ChunkPos(x, z));
		}
		return start;
	}

	static BlockPos houseBlock(VillageRecord r, boolean wantAir) {
		for (VillageRecord.Piece p : r.pieces) {
			if (p.type() != VillageRecord.HOUSE) continue;
			BoundingBox b = p.box();
			for (int y = b.maxY(); y >= b.minY(); y--) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				BlockPos q = new BlockPos(x, y, z);
				BlockState s = level.getBlockState(q);
				if (wantAir ? s.isAir() && !level.getBlockState(q.below()).isAir() : s.is(net.minecraft.tags.BlockTags.PLANKS)) return q;
			}
		}
		return r.center;
	}

	static void observe(VillageRecord r) {
		observe(Vec3.atCenterOf(r.center));
	}

	static int count(VillageRecord r, net.minecraft.world.level.block.Block block, int up) {
		int n = 0;
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY() + up; y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++)
				if (level.getBlockState(new BlockPos(x, y, z)).is(block)) n++;
		}
		return n;
	}

	static Villager villager(BlockPos at, Optional<net.minecraft.resources.ResourceKey<VillagerProfession>> prof, int lvl) {
		Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
		prof.ifPresent(p -> v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), p).withLevel(lvl)));
		v.setNoAi(true);
		level.addFreshEntity(v);
		return v;
	}

	// ---- phases -------------------------------------------------------------------------------------------------------

	static void setup(MinecraftServer srv) {
		level = srv.overworld();
		sA = village(level, 0, 14);
		sB = village(level, 18, 14);
		sC = village(level, -18, 14);
		check("test villages generated (plains, StructureStart in chunks)", sA.isValid() && sB.isValid() && sC.isValid(),
			sA.getPieces().size() + "/" + sB.getPieces().size() + "/" + sC.getPieces().size() + " pieces");
		BlockPos bc = sB.getBoundingBox().getCenter();
		castle = new BlockPos(bc.getX(), -60, bc.getZ() + 600);
		RealmLocator.testRealm = castle;
		VillageManager.siegeChanceOverride = 1.0;
		VillageManager.opInterval = 1;
		// no golems in B / C (keeps the scripted flow deterministic; the load test keeps them)
		for (StructureStart s : List.of(sB, sA, sC))
			for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, AABB.of(s.getBoundingBox().inflatedBy(32)), e -> true)) g.discard();

		// A: a real player block placement (FakePlayer through BlockItem.place, so the mixin path is exercised)
		FakePlayer fp = FakePlayer.get(level);
		BlockPos airSpot = null;
		for (var p : sA.getPieces()) {
			BoundingBox b = p.getBoundingBox();
			for (int x = b.minX() + 1; x < b.maxX() && airSpot == null; x++) for (int z = b.minZ() + 1; z < b.maxZ() && airSpot == null; z++) {
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos q = new BlockPos(x, top, z);
				if (b.isInside(q) && level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) airSpot = q;
			}
			if (airSpot != null) break;
		}
		if (airSpot == null) airSpot = sA.getBoundingBox().getCenter();
		glow = airSpot;
		fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOWSTONE));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(glow.below()).add(0, 0.5, 0), Direction.UP, glow.below(), false);
		InteractionResult res = ((BlockItem) Items.GLOWSTONE).place(new BlockPlaceContext(fp, InteractionHand.MAIN_HAND, fp.getItemInHand(InteractionHand.MAIN_HAND), hit));
		check("player places a block inside village A (BlockItem mixin)", level.getBlockState(glow).is(Blocks.GLOWSTONE), res + " at " + glow.toShortString());
		check("player placement marked A's chunk", VillageLedger.get(level).playerChunks.contains(ChunkPos.pack(glow)), null);

		// C: a completed trade (AbstractVillager.notifyTrade mixin)
		List<Villager> cv = level.getEntitiesOfClass(Villager.class, AABB.of(sC.getBoundingBox().inflatedBy(8)), e -> true);
		Villager trader = cv.isEmpty() ? villager(sC.getBoundingBox().getCenter(), Optional.empty(), 1) : cv.get(0);
		trader.notifyTrade(new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
		check("trade with a villager in C marked its chunk", VillageLedger.get(level).tradeChunks.contains(ChunkPos.pack(trader.blockPosition())), null);

		// siege fire spread rig: planks floor, leaves, wool and hay around a siege fire; random ticks sped up
		fireAt = new BlockPos(40, -60, -120);
		level.setChunkForced(fireAt.getX() >> 4, fireAt.getZ() >> 4, true);
		level.getChunk(fireAt.getX() >> 4, fireAt.getZ() >> 4);
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
			level.setBlock(fireAt.offset(dx, -1, dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
			if (Math.abs(dx) == 3 || Math.abs(dz) == 3) level.setBlock(fireAt.offset(dx, 0, dz), (dx + dz) % 2 == 0 ? Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true) : Blocks.WOOL.white().defaultBlockState(), 3);
		}
		level.setBlock(fireAt.offset(1, 0, 0), Blocks.HAY_BLOCK.defaultBlockState(), 3);
		level.setBlock(fireAt.offset(0, 0, 1), Blocks.OAK_LOG.defaultBlockState(), 3);
		SiegeFireBlock.testLife = 100;
		level.setBlock(fireAt, VillagesContent.SIEGE_FIRE.defaultBlockState(), 3);
		SiegeFireBlock.testLife = 0;
		level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 300, srv);
		check("siege fire is not a fire block and never random-ticks", !(VillagesContent.SIEGE_FIRE instanceof BaseFireBlock)
			&& !VillagesContent.SIEGE_FIRE.defaultBlockState().isRandomlyTicking(), null);

		// pure distance rules (user: 250-300 occupied, 400-1000 siege, slow creep)
		long w = VillageManager.WEEK;
		check("band: 250 blocks = occupied", VillageManager.band(250, 0) == VillageManager.Band.OCCUPIED, null);
		check("band: 350 blocks = neither", VillageManager.band(350, 0) == VillageManager.Band.GAP, null);
		check("band: 600 blocks = siege band", VillageManager.band(600, 0) == VillageManager.Band.SIEGE, null);
		check("band: 1100 blocks = free at first", VillageManager.band(1100, 0) == VillageManager.Band.FREE, null);
		check("front creeps slowly: 1100 in siege band after 3 weeks", VillageManager.band(1100, 3 * w) == VillageManager.Band.SIEGE, VillageManager.siegeOuter(3 * w));
		check("front is capped: 1500 stays free after a year", VillageManager.band(1500, 52 * w) == VillageManager.Band.FREE, VillageManager.siegeOuter(52 * w));

		for (StructureStart s : List.of(sA, sB, sC)) observe(Vec3.atCenterOf(s.getBoundingBox().getCenter()));
		next();
	}

	static void discovery() {
		VillageLedger l = VillageLedger.get(level);
		A = l.villages.get(sA.getChunkPos().pack());
		B = l.villages.get(sB.getChunkPos().pack());
		C = l.villages.get(sC.getChunkPos().pack());
		if ((A == null || B == null || C == null) && age() < 400) return;
		check("villages discovered from chunk structure references", A != null && B != null && C != null, l.villages.size());
		if (A == null || B == null || C == null) {
			phase = 99;
			finish(level.getServer());
			return;
		}
		check("pieces recorded with types (houses, streets, centre)", B.pieces.stream().anyMatch(p -> p.type() == VillageRecord.HOUSE)
			&& B.pieces.stream().anyMatch(p -> p.type() == VillageRecord.STREET), B.pieces.size());
		check("A with a player-placed block is PROTECTED", A.state == VillageRecord.State.PROTECTED, A.state + " " + A.reason);
		check("C with player trade history is PROTECTED", C.state == VillageRecord.State.PROTECTED, C.state + " " + C.reason);
		check("B (600 blocks from the castle, untouched) is under SIEGE", B.state == VillageRecord.State.SIEGE, B.state + " dist " + (int) B.castleDist);
		check("traded-with village is never besieged (forced siege refused)", !VillageManager.startSiege(level, C, null, true) && C.state == VillageRecord.State.PROTECTED, C.state);
		check("player-built village is never besieged (forced siege refused)", !VillageManager.startSiege(level, A, null, true), A.state);
		// the writer itself refuses A even with a hand-made burn plan
		List<VillageRecord.Op> ops = Plans.burn(level, A, level.getRandom());
		long w0 = BlockWriter.writes;
		for (VillageRecord.Op op : ops) BlockWriter.write(level, A, BlockPos.of(op.pos()), op.state());
		check("block writer refuses every op on a protected village", BlockWriter.writes == w0 && A.journal.isEmpty(), ops.size() + " ops tried");
		check("burn plan stays inside B's pieces", B.plan.stream().allMatch(o -> B.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP)), B.plan.size() + " ops");
		check("no Coffer and no Steward in ordinary villages A and C", count(A, VillagesContent.ROYAL_COFFER, 8) == 0 && count(C, VillagesContent.ROYAL_COFFER, 8) == 0
			&& level.getEntitiesOfClass(Villager.class, AABB.of(sA.getBoundingBox().inflatedBy(16)), Steward::isSteward).isEmpty()
			&& level.getEntitiesOfClass(Villager.class, AABB.of(sC.getBoundingBox().inflatedBy(16)), Steward::isSteward).isEmpty(), null);
		VillageManager.opsPerRun = 2;
		next();
	}

	static void siegeRunning() {
		if (age() == 5) {
			check("siege force spawned (8-12 soldiers + sergeants)", B.forceSpawned >= 8 && B.forceSpawned <= 13, B.forceSpawned);
			Villager v = level.getEntitiesOfClass(Villager.class, AABB.of(B.bounds.inflatedBy(8)), e -> true).stream().findFirst().orElse(null);
			if (v != null) check("villagers in a besieged village are captured (no trading)", VillageManager.isCaptured(level, v), null);
		}
		if (age() == 120) {
			check("fire placed only where nothing spread: no vanilla fire around the rig", noVanillaFire(fireAt, 6), null);
			check("siege fire rig: planks, leaves, wool, hay and log untouched", level.getBlockState(fireAt.offset(1, 0, 0)).is(Blocks.HAY_BLOCK)
				&& level.getBlockState(fireAt.offset(0, 0, 1)).is(Blocks.OAK_LOG) && level.getBlockState(fireAt.offset(3, 0, 3)).is(Blocks.OAK_LEAVES)
				&& level.getBlockState(fireAt.offset(-3, 0, -2)).is(Blocks.WOOL.white()) && level.getBlockState(fireAt.below()).is(Blocks.OAK_PLANKS), null);
		}
		if (B.planIndex < B.plan.size() && age() < 600) return;
		int fires = 0, outside = 0;
		for (var e : B.journal.long2ObjectEntrySet()) {
			if (e.getValue().written().is(VillagesContent.SIEGE_FIRE)) fires++;
			if (!B.insideFootprint(BlockPos.of(e.getLongKey()), BlockWriter.UP)) outside++;
			burnOriginals.put(e.getLongKey(), e.getValue().original());
		}
		check("siege burned B (charred / collapsed blocks journaled)", B.journal.size() > 30, B.journal.size() + " blocks, plan " + B.planIndex + "/" + B.plan.size());
		check("siege lit siege fire", fires > 0, fires);
		check("every changed block is inside B's generated pieces", outside == 0, outside);
		check("A's player block untouched, A and C unchanged", level.getBlockState(glow).is(Blocks.GLOWSTONE) && A.journal.isEmpty() && C.journal.isEmpty(), null);
		next();
	}

	static void observe(Vec3 v) {
		VillageManager.testObservers.add(v);
		KingdomPerf.testObservers.add(v);
	}

	static boolean noVanillaFire(BlockPos c, int r) {
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 3, r))) if (level.getBlockState(p).is(Blocks.FIRE)) return false;
		return true;
	}

	/** Restored farmland is set to full moisture so it stays farmland; every other block must match exactly. */
	static boolean sameRestored(BlockState got, BlockState want) {
		if (got == want) return true;
		return want.is(Blocks.FARMLAND) && got.is(Blocks.FARMLAND);
	}

	static void restoreAndFall() {
		// restore the burned layout (same code as /aldermark restore), check every block, then let the siege fall
		// grass spreads onto the dirt the siege left where farmland was (random ticks): force it so every run exercises that path
		int farmN = 0, grassed = 0;
		for (var e : burnOriginals.entrySet()) {
			if (!e.getValue().is(Blocks.FARMLAND)) continue;
			farmN++;
			BlockPos fp0 = BlockPos.of(e.getKey());
			if (level.getBlockState(fp0).is(Blocks.DIRT)) { level.setBlock(fp0, Blocks.GRASS_BLOCK.defaultBlockState(), 2); grassed++; }
		}
		// deterministic farmland case whatever the random layout: a journaled farmland -> dirt -> grass block inside B
		farmTest = houseBlock(B, true);
		if (farmTest != null) {
			level.setBlock(farmTest, Blocks.FARMLAND.defaultBlockState(), 2);
			boolean w = BlockWriter.write(level, B, farmTest, Blocks.DIRT.defaultBlockState());
			level.setBlock(farmTest, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			farmN++;
			if (!w) farmTest = null;
		}
		int farmBack = 0;
		int[] res = BlockWriter.restore(level, B);
		if (farmTest != null && level.getBlockState(farmTest).is(Blocks.FARMLAND)) farmBack++;
		check("restore: a journaled dirt where farmland was, grassed over by spread, comes back as farmland", farmTest != null && level.getBlockState(farmTest).is(Blocks.FARMLAND), farmTest + " " + (farmTest == null ? "" : level.getBlockState(farmTest)));
		for (var e : burnOriginals.entrySet()) if (e.getValue().is(Blocks.FARMLAND) && level.getBlockState(BlockPos.of(e.getKey())).is(Blocks.FARMLAND)) farmBack++;
		check("restore: farmland the siege turned to dirt (then grass) comes back as farmland", farmBack == farmN, farmBack + "/" + farmN + " back, " + grassed + " were grassed");
		int bad = 0;
		StringBuilder dbg = new StringBuilder();
		for (var e : burnOriginals.entrySet()) if (!sameRestored(level.getBlockState(BlockPos.of(e.getKey())), e.getValue())) { bad++; if (bad <= 3) dbg.append(" [").append(BlockPos.of(e.getKey()).toShortString()).append(" want ").append(e.getValue()).append(" got ").append(level.getBlockState(BlockPos.of(e.getKey()))).append("]"); }
		check("restore puts the original layout back", bad == 0 && res[0] > 0, res[0] + " restored, " + res[1] + " skipped, " + bad + " differ" + dbg);
		B.siegeEnd = level.getGameTime();
		next();
	}

	static void fortifying() {
		// a player taking B back walks through the whole town: keep every garrison squad real (kingdom LOD virtualizes a squad
		// after 200 ticks with no observer within 96 blocks, and B's single centre observer can't see a large layout's edges)
		if (age() == 1) for (VillageRecord.Piece p : B.pieces) KingdomPerf.testObservers.add(Vec3.atCenterOf(p.box().getCenter()));
		if (B.state == VillageRecord.State.SIEGE && age() < 100) return;
		if (age() == 1 || B.state != VillageRecord.State.FORTIFYING && B.state != VillageRecord.State.FORTIFIED && age() < 100) return;
		if (phaseStart >= 0 && B.planKind == 2 && B.planIndex < 3) {
			int serfs = level.getEntitiesOfClass(Serf.class, AABB.of(B.bounds.inflatedBy(32)), e -> true).size();
			int fl = farmTest != null && level.getBlockState(farmTest).is(Blocks.FARMLAND) ? 1 : 0, fw = farmTest != null ? 1 : 0;
			for (var fe : burnOriginals.entrySet()) if (fe.getValue().is(Blocks.FARMLAND)) { fw++; if (level.getBlockState(BlockPos.of(fe.getKey())).is(Blocks.FARMLAND)) fl++; }
			check("restored farmland is still farmland after the siege fell (a rebuild or random ticks did not turn it to dirt/grass)", fl == fw, fl + "/" + fw);
			check("village fell after the siege timer: OCCUPIED then FORTIFYING", B.state == VillageRecord.State.FORTIFYING, B.state);
			serfs = Math.max(serfs, VillageManager.serfs(level, B).size()); // an entity added this very tick may not show in the area query yet
			check("3-5 Serfs arrive to rebuild", serfs >= 3 && serfs <= 5, serfs + " in area, registered " + B.serfs.size());
			List<Serf> rebuilders = VillageManager.serfs(level, B);
			check("rebuild Serfs have 18 HP (RIVALS-DECISIONS 14, Aldermark included)", !rebuilders.isEmpty() && rebuilders.stream().allMatch(s -> s.getMaxHealth() == 18F),
				rebuilders.stream().map(s -> s.getHealth() + "/" + s.getMaxHealth()).toList());
			check("rebuild plan stays in B's footprint", B.plan.stream().allMatch(o -> B.insideFootprint(BlockPos.of(o.pos()), BlockWriter.UP)), B.plan.size());
			check("rebuild plans a sealed Royal Coffer (tax office)", B.coffer != null, B.coffer);
			check("tax collector posted", B.taxSquad != null, null);
			Villager v = level.getEntitiesOfClass(Villager.class, AABB.of(B.bounds.inflatedBy(8)), e -> true).stream().findFirst().orElse(null);
			if (v != null) check("villagers of an occupied village are captured", VillageManager.isCaptured(level, v), null);
			VillageManager.opsPerRun = 40;
			phaseStart = -1_000_000; // checked once
		}
		if (B.state != VillageRecord.State.FORTIFIED && ++fortWait == 2500) info("fortify stalled: " + B.state + " " + B.planIndex + "/" + B.plan.size() + " reason " + B.reason + " serfs " + level.getEntitiesOfClass(Serf.class, AABB.of(B.bounds.inflatedBy(32)), e -> true).size());
		if (B.state != VillageRecord.State.FORTIFIED && fortWait < 6000) return;
		int serfs = level.getEntitiesOfClass(Serf.class, AABB.of(B.bounds.inflatedBy(32)), e -> true).size();
		check("rebuild finished: FORTIFIED", B.state == VillageRecord.State.FORTIFIED, B.state + " " + B.planIndex + "/" + B.plan.size());
		check("Serfs leave when the rebuild is done", serfs == 0, serfs);
		int stone = 0;
		for (var e : B.journal.values()) if (e.written().is(Blocks.STONE_BRICKS) || e.written().is(Blocks.SPRUCE_PLANKS) || e.written().is(Blocks.STONE_BRICK_STAIRS)) stone++;
		check("rebuilt in the Aldermark style (stone and timber)", stone > 20, stone);
		check("palisade / banners / tower built", count(B, Blocks.SPRUCE_FENCE, 8) + count(B, Blocks.BANNER.blue(), 8) > 4, null);
		BlockState cof = B.coffer == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(B.coffer);
		check("Royal Coffer built sealed", cof.is(VillagesContent.ROYAL_COFFER) && cof.getValue(RoyalCofferBlock.SEALED), cof);
		check("a sealed Coffer is no job site (no Steward before liberation)", B.coffer != null && level.getPoiManager().getType(B.coffer).isEmpty(), null);
		check("every rebuilt block in B's footprint", B.journal.keySet().longStream().allMatch(p -> B.insideFootprint(BlockPos.of(p), BlockWriter.UP)), null);
		check("A's player block still untouched", level.getBlockState(glow).is(Blocks.GLOWSTONE), null);
		next();
	}

	static void liberation() {
		if (age() == 1) {
			for (KingdomSoldier s : VillageManager.soldiers(level, B)) if (B.taxSquad == null || !B.taxSquad.equals(s.squadId())) s.kill(level);
			return;
		}
		if (age() < 10) return;
		check("killing the garrison liberates the village", B.state == VillageRecord.State.LIBERATED, B.state + " lod virtualized " + dev.elsewhere.kingdom.KingdomLod.virtualized + " dead " + B.forceDead + "/" + B.forceSpawned + " sol=" + VillageManager.soldiers(level, B).size() + " squads=" + B.squads.size() + " allKS=" + level.getEntitiesOfClass(KingdomSoldier.class, new AABB(-3000, -64, -3000, 3000, 320, 3000), e -> true).stream().map(e -> e.squadId() == null ? "null" : (B.squads.contains(e.squadId()) ? "B" : "o") + (e.isAlive() ? "" : "x")).toList());
		BlockState cof = B.coffer == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(B.coffer);
		check("liberation unseals the Coffer", cof.is(VillagesContent.ROYAL_COFFER) && !cof.getValue(RoyalCofferBlock.SEALED), cof);
		check("unsealed Coffer is the Steward's job site", B.coffer != null && level.getPoiManager().getType(B.coffer).map(h -> h.is(VillagesContent.COFFER_POI)).orElse(false), null);
		// distinct positions: piece boxes (plus the 8 blocks above each) can overlap, so a per-piece count sees one block twice
		java.util.TreeMap<Long, String> cofs = new java.util.TreeMap<>();
		for (VillageRecord.Piece p : B.pieces) {
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY() + 8; y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				BlockState s = level.getBlockState(new BlockPos(x, y, z));
				if (s.is(VillagesContent.ROYAL_COFFER)) cofs.merge(BlockPos.asLong(x, y, z), (s.getValue(RoyalCofferBlock.SEALED) ? "sealed" : "open") + " in " + p.type(), (a, c) -> a + "+" + c);
			}
		}
		int cofBlocks = cofs.size();
		long cofPoi = level.getPoiManager().getCountInRange(h -> h.is(VillagesContent.COFFER_POI), B.center, 128, PoiManager.Occupancy.ANY);
		StringBuilder cofDbg = new StringBuilder();
		cofs.forEach((k, v) -> cofDbg.append(' ').append(BlockPos.of(k).toShortString()).append(' ').append(v));
		check("one Steward job site per liberated town", cofBlocks == 1 && cofPoi == 1, "coffer blocks " + cofBlocks + ", coffer POIs within 128: " + cofPoi + " (plan coffer " + B.coffer + ";" + cofDbg + ")");
		check("the surrendered tax collector stays on (villager at the Coffer)", B.coffer != null
			&& !level.getEntitiesOfClass(Villager.class, new AABB(B.coffer).inflate(3), e -> true).isEmpty(), null);
		check("tax collector soldier is gone", VillageManager.soldiers(level, B).isEmpty(), VillageManager.soldiers(level, B).size());
		Villager v = level.getEntitiesOfClass(Villager.class, AABB.of(B.bounds.inflatedBy(8)), e -> true).stream().findFirst().orElse(null);
		if (v != null) check("liberated villagers trade again", !VillageManager.isCaptured(level, v), null);
		check("freed Serfs gone (some became villagers)", level.getEntitiesOfClass(Serf.class, AABB.of(B.bounds.inflatedBy(32)), e -> true).isEmpty(), null);
		next();
	}

	static void steward() {
		BlockPos at = B.center.above(2);
		var STEW = Optional.of(VillagesContent.STEWARD);
		Villager n = villager(at, STEW, 1), ap = villager(at, STEW, 2), jm = villager(at, STEW, 3), ex = villager(at, STEW, 4), ma = villager(at, STEW, 5);
		MerchantOffers o1 = n.getOffers(), o4 = ex.getOffers(), o5 = ma.getOffers();
		ap.getOffers();
		jm.getOffers();
		var crown = Steward.crown();
		check("Steward novice: Crown -> Purse exchange at par (55)", o1.stream().anyMatch(o -> o.getResult().is(VillagesContent.ROYAL_PURSE) && o.getBaseCostA().is(crown) && o.getBaseCostA().getCount() == 55), o1.size());
		check("Steward novice: Bodkin Bolts x8 for 27 Crowns", o1.stream().anyMatch(o -> o.getResult().is(Steward.bodkin()) && o.getResult().getCount() == 8 && o.getBaseCostA().getCount() == 27), null);
		check("Steward cosmetics 20-40 Crowns (pattern, shield, trim)", List.of(o1, ap.getOffers(), jm.getOffers()).stream().flatMap(List::stream).filter(o -> !o.getResult().is(VillagesContent.ROYAL_PURSE) && !o.getResult().is(Steward.bodkin()))
			.allMatch(o -> o.getBaseCostA().getCount() >= 20 && o.getBaseCostA().getCount() <= 40) && ap.getOffers().size() == 1 && jm.getOffers().size() == 1, null);
		check("Steward expert: Oathkeeper's Seal 220 Crowns (4 purses), 2 per week", o4.stream().anyMatch(o -> o.getResult().is(Steward.seal()) && Steward.listedCrowns(o) == 220 && o.getMaxUses() == 2), null);
		long books = o5.stream().filter(o -> o.getResult().is(Items.ENCHANTED_BOOK)).count();
		check("Steward master: rolls exactly 2 of the 4 books", books == 2, books);
		check("Steward master: books 165 Crowns + 3 emerald blocks, 2 per week", o5.stream().filter(o -> o.getResult().is(Items.ENCHANTED_BOOK))
			.allMatch(o -> Steward.listedCrowns(o) == 165 && o.getCostB().is(Items.EMERALD_BLOCK) && o.getCostB().getCount() == 3 && o.getMaxUses() == 2), null);
		check("Steward master: Netherite template 275 Crowns + 20 diamonds, 2 per week", o5.stream().anyMatch(o -> o.getResult().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
			&& Steward.listedCrowns(o) == 275 && o.getCostB().is(Items.DIAMOND) && o.getCostB().getCount() == 20 && o.getMaxUses() == 2), null);
		// price floors: simulate a huge reputation + Hero of the Village discount, then the TAIL hook
		int below = 0, offers = 0;
		for (Villager v : List.of(n, ap, jm, ex, ma)) for (MerchantOffer o : v.getOffers()) {
			o.setSpecialPriceDiff(-10000);
			VillageManager.applyPrices(v);
			offers++;
			if (Steward.effectiveCrowns(o) < Steward.floorCrowns(o)) below++;
		}
		check("no Steward price drops below its floor after discounts", below == 0 && offers >= 8, offers + " offers, " + below + " below");
		// weekly limits
		MerchantOffers mo = ma.getOffers();
		mo.add(new MerchantOffer(new ItemCost(crown, 10), new ItemStack(Items.BREAD), 12, 1, 0F));
		for (MerchantOffer o : mo) o.increaseUses();
		ma.restock();
		boolean weeklyHeld = mo.stream().filter(Steward::weekly).allMatch(o -> o.getUses() == 1 && !o.isOutOfStock());
		boolean dailyBack = mo.stream().filter(o -> !Steward.weekly(o)).allMatch(o -> o.getUses() == 0);
		check("weekly limit: a restock within the week does not give weekly trades back", weeklyHeld, null);
		check("ordinary Steward offers restock normally", dailyBack, null);
		for (MerchantOffer o : mo) if (Steward.weekly(o)) o.increaseUses();
		ma.restock();
		check("weekly limit: 2 per in-game week, then sold out until the week ends", mo.stream().filter(Steward::weekly).allMatch(MerchantOffer::isOutOfStock), null);
		VillageLedger.get(level).stewardWeekly.put(ma.getUUID(), level.getGameTime() - Steward.WEEK - 1);
		ma.restock();
		check("weekly offers restock after an in-game week", mo.stream().filter(Steward::weekly).allMatch(o -> o.getUses() == 0), null);
		// Master gate
		try {
			Method m = Villager.class.getDeclaredMethod("shouldIncreaseLevel");
			m.setAccessible(true);
			ex.setVillagerXp(260);
			boolean before = (boolean) m.invoke(ex);
			var prev = Steward.LORDS_DEAD;
			Steward.LORDS_DEAD = s -> 2;
			boolean after = (boolean) m.invoke(ex);
			Steward.LORDS_DEAD = prev;
			check("Steward can't reach Master until 2 Lords are dead", !before && after, before + " -> " + after);
		} catch (ReflectiveOperationException e) {
			check("Master gate reflection", false, e);
		}
		// liberation discount for ordinary villagers (10%), none outside liberated towns
		Villager farmer = villager(B.center.above(2), Optional.of(VillagerProfession.FARMER), 1);
		Villager farmerA = villager(A.center.above(2), Optional.of(VillagerProfession.FARMER), 1);
		boolean disc = true, none = true;
		for (MerchantOffer o : farmer.getOffers()) o.resetSpecialPriceDiff();
		VillageManager.applyPrices(farmer);
		for (MerchantOffer o : farmer.getOffers()) {
			int d = Math.round(o.getBaseCostA().getCount() * 0.10F);
			if (o.getSpecialPriceDiff() != -d) disc = false;
		}
		for (MerchantOffer o : farmerA.getOffers()) o.resetSpecialPriceDiff();
		VillageManager.applyPrices(farmerA);
		for (MerchantOffer o : farmerA.getOffers()) if (o.getSpecialPriceDiff() != 0) none = false;
		check("liberated village: permanent 10% discount", disc && !farmer.getOffers().isEmpty(), farmer.getOffers().size());
		check("no discount outside liberated villages", none, null);
		for (Villager v : List.of(n, ap, jm, ex, ma, farmer, farmerA)) v.discard();
		next();
	}


	// ---- Serfs: true neutral mobs (user, 14:55) -------------------------------------------------------------------------

	static Serf s1, s2;
	static IronGolem foe;
	static float foeHp;
	static double maxFlee;

	static Serf serf(BlockPos at) {
		Serf s = VillagesContent.SERF.create(level, EntitySpawnReason.COMMAND);
		s.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
		s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.SPRUCE_LOG));
		level.addFreshEntity(s);
		return s;
	}

	static void serfs() {
		BlockPos at = Plans.surface(level, A.center.offset(0, 0, 0));
		if (age() == 1) {
			s1 = serf(at);
			s2 = serf(at.offset(1, 0, 0));
			foe = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
			foe.snapTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
			foe.setNoAi(true);
			level.addFreshEntity(foe);
			foeHp = foe.getHealth();
			check("Serf: 18 HP (RIVALS-DECISIONS 14)", s1.getMaxHealth() == 18F, s1.getMaxHealth());
			// a Serf saved by 2.0 (16 HP base) or an early 3.0 build (20) takes the profile health on load, without healing
			var hpAttr = s2.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			hpAttr.setBaseValue(20.0);
			s2.setHealth(20F);
			s2.applyProfileHealth();
			float old20 = s2.getMaxHealth(), hp20 = s2.getHealth();
			hpAttr.setBaseValue(16.0);
			s2.setHealth(16F);
			s2.applyProfileHealth();
			check("old saved Serfs (20 or 16 HP) load with 18 HP, never healed", old20 == 18F && hp20 == 18F && s2.getMaxHealth() == 18F && s2.getHealth() == 16F,
				old20 + "/" + hp20 + ", " + s2.getHealth() + "/" + s2.getMaxHealth());
			s2.setHealth(18F);
			var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			pig.snapTo(at.getX() + 0.5, at.getY(), at.getZ() - 2.5, 0F, 0F);
			level.addFreshEntity(pig);
			float h0 = pig.getHealth();
			s1.doHurtTarget(level, pig);
			float dmg = h0 - pig.getHealth();
			check("Serf punch deals 4-5 damage", dmg >= 4F && dmg <= 5F, dmg);
			pig.discard();
			return;
		}
		if (age() == 60) {
			check("Serf is neutral: ignores others until attacked", !s1.isAngry() && !s2.isAngry() && foe.getHealth() == foeHp && s1.punches == 0, null);
			s1.hurtServer(level, level.damageSources().mobAttack(foe), 1F);
			check("attacked Serf fights back", s1.isAngry() && s1.angerTarget != null && s1.angerTarget.equals(foe.getUUID()), null);
			check("nearby Serf that saw it joins in", s2.isAngry(), null);
			check("angry Serf punches bare-handed (log put away)", s1.getMainHandItem().isEmpty(), s1.getMainHandItem());
			check("Serfs calm down after 30 s without a hit", Serf.CALM_TICKS == 600, Serf.CALM_TICKS);
			return;
		}
		if (age() > 60 && age() < 360) {
			maxFlee = Math.max(maxFlee, Math.max(s1.distanceTo(foe), s2.distanceTo(foe)));
			if (age() % 40 == 0) s1.hurtServer(level, level.damageSources().mobAttack(foe), 0.5F); // keep it provoked
			return;
		}
		if (age() == 360) {
			check("frightened Serfs both flee and punch (erratic)", s1.punches + s2.punches > 0 && maxFlee > 4.0 && foe.getHealth() < foeHp,
				"punches " + (s1.punches + s2.punches) + ", max distance " + String.format("%.1f", maxFlee));
			s1.angerTicks = 3;
			s2.angerTicks = 3;
			return;
		}
		if (age() < 370) return;
		check("Serfs calm down when the anger runs out", !s1.isAngry() && !s2.isAngry() && s1.getTarget() == null, null);
		check("calm Serf picks its log back up", s1.getMainHandItem().is(Items.SPRUCE_LOG), s1.getMainHandItem());
		s1.discard();
		s2.discard();
		foe.discard();
		next();
	}

	static void conquest() {
		VillageLedger l = VillageLedger.get(level);
		l.outpostsFounded = Conquest.MAX_OUTPOSTS;
		l.lastOutpostTime = -1_000_000_000L;
		check("outpost cap: none after 10", !Conquest.mayFound(l, level.getGameTime()), null);
		l.outpostsFounded = 0;
		l.lastOutpostTime = level.getGameTime() - 5 * VillageManager.DAY;
		check("outposts are slow: none within 10 days of the last", !Conquest.mayFound(l, level.getGameTime()), null);
		l.lastOutpostTime = -1_000_000_000L;
		check("outpost allowed after 10 days when below the cap", Conquest.mayFound(l, level.getGameTime()), null);
		BlockPos oc = new BlockPos(-200, -60, -200);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			level.setChunkForced((oc.getX() >> 4) + dx, (oc.getZ() >> 4) + dz, true);
			level.getChunk((oc.getX() >> 4) + dx, (oc.getZ() >> 4) + dz);
		}
		// a player-marked chunk is never "fresh"
		l.playerChunks.add(ChunkPos.pack(oc.offset(-40, 0, 0)));
		check("never builds on a player-marked chunk", !Conquest.freshChunkPublic(level, oc.offset(-40, 0, 0)), null);
		outpost = Conquest.foundOutpost(level, l, Plans.surface(level, oc), castle, level.getRandom(), level.getGameTime());
		check("outpost built (palisade, tower, banner, mess fire)", outpost.journal.size() > 40 && count(outpost, Blocks.BANNER.blue(), 0) == 1, outpost.journal.size());
		check("outpost counted toward the cap", l.outpostsFounded == 1, l.outpostsFounded);
		road = null;
		for (VillageRecord s : l.sites.values()) if (s.kind == VillageRecord.Kind.ROAD && s.roadTarget == castle.asLong() && "royal".equals(s.reason)) road = s;
		check("royal road started from the outpost toward the castle", road != null && road.roadLength > 0, road == null ? null : road.roadLength);
		// mark one chunk on the road's path: the road must skip it
		if (road != null) {
			BlockPos t = BlockPos.of(road.roadTarget);
			double len = RealmLocator.horizontal(road.center, t);
			BlockPos mid = road.center.offset((int) ((t.getX() - road.center.getX()) / len * 40), 0, (int) ((t.getZ() - road.center.getZ()) / len * 40));
			l.playerChunks.add(ChunkPos.pack(mid));
			// a chunk 20 blocks along that was lived in before the install (vanilla InhabitedTime >= 72000, no mark): skipped too
			BlockPos near = road.center.offset((int) ((t.getX() - road.center.getX()) / len * 20), 0, (int) ((t.getZ() - road.center.getZ()) / len * 20));
			inhabitedRoadChunk = ChunkPos.pack(near);
			level.getChunk(near.getX() >> 4, near.getZ() >> 4).setInhabitedTime(VillageManager.PRESENCE_TICKS);
		}
		if (road != null) {
			BlockPos t = BlockPos.of(road.roadTarget);
			double len = RealmLocator.horizontal(road.center, t);
			double ux = (t.getX() - road.center.getX()) / len, uz = (t.getZ() - road.center.getZ()) / len;
			for (int i = 0; i < 76; i++) for (int w = -1; w <= 2; w++) {
				int x = road.center.getX() + (int) Math.round(ux * i) + (int) Math.round(-uz) * w, z = road.center.getZ() + (int) Math.round(uz * i) + (int) Math.round(ux) * w;
				level.getChunk(x >> 4, z >> 4);
				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos g = new BlockPos(x, top - 1, z);
				if (!level.getBlockState(g).isAir() && level.getBlockState(g).getFluidState().isEmpty()) level.setBlock(g, i % 7 == 3 ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
		}
		Conquest.testFastRoads = true;
		observe(Vec3.atCenterOf(oc));
		next();
	}

	static VillageRecord hamletSite;

	static void conquestCapture() {
		if (age() == 12 && hamletSite != null) {
			List<Villager> refugees = level.getEntitiesOfClass(Villager.class, AABB.of(hamletSite.bounds.inflatedBy(4)), e -> true);
			check("burned hamlet with 1-2 refugees (subtle names)", hamletSite.journal.size() > 3 && refugees.size() >= 1 && refugees.size() <= 2
				&& refugees.stream().allMatch(v -> v.hasCustomName() && !v.isCustomNameVisible()), refugees.size() + " journal " + hamletSite.journal.size() + " " + Conquest.hamletDbg);
		}
		if (age() == 3) {
			BlockPos oc = new BlockPos(-200, -60, -200);
			var l = VillageLedger.get(level);
		hamletSite = Conquest.buildHamlet(level, l, Plans.surface(level, oc.offset(30, 0, -30)), castle, level.getRandom());
		}
		if (age() == 30) {
			check("outpost garrison appears when a player comes near", outpost.forceSpawned >= 2, outpost.forceSpawned + " state " + outpost.state + " once " + outpost.forceSpawnedOnce + " loaded " + level.isLoaded(outpost.center) + " cap " + KingdomPerf.canSpawn(level, Vec3.atCenterOf(outpost.center), false) + " obs " + VillageManager.testObservers.size() + " center " + outpost.center);
			for (KingdomSoldier s : VillageManager.soldiers(level, outpost)) s.kill(level);
		}
		if (age() < 140) return;
		check("killing the outpost garrison captures it (white banner)", outpost.state == VillageRecord.State.LIBERATED && count(outpost, Blocks.BANNER.white(), 0) == 1, outpost.state);
		VillageLedger l = VillageLedger.get(level);
		boolean natural = true, markedSkipped = true;
		for (var e : road.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			BlockState o = e.getValue().original(), w = e.getValue().written();
			if (!(w.isAir() || w.is(Blocks.GRAVEL) || w.is(Blocks.DIRT_PATH) || w.is(Blocks.COBBLESTONE) || w.is(Blocks.OAK_SIGN))) natural = false;
			if ((w.is(Blocks.GRAVEL) || w.is(Blocks.DIRT_PATH)) && !(o.is(Blocks.GRASS_BLOCK) || o.is(net.minecraft.tags.BlockTags.DIRT) || o.is(Blocks.SAND) || o.is(Blocks.GRAVEL))) natural = false;
			if (o.is(Blocks.ROOTED_DIRT) && !w.isAir()) natural = false;
			if (ChunkPos.pack(p) == inhabitedRoadChunk) markedSkipped = false;
			if (l.playerChunks.contains(ChunkPos.pack(p))) markedSkipped = false;
		}
		check("royal road built slowly, gravel and cobble on natural ground only", road.journal.size() > 20 && natural, road.journal.size() + " blocks, step " + road.roadStep + "/" + road.roadLength + " nat " + natural + " c " + road.center + " tgt " + BlockPos.of(road.roadTarget) + " kind " + road.kind + " dbg " + java.util.Arrays.toString(Conquest.roadDbg));
		check("road skips player-marked chunks and chunks lived in (InhabitedTime >= 72000)", markedSkipped, null);
		Map<Long, BlockState> orig = new HashMap<>();
		for (var e : outpost.journal.long2ObjectEntrySet()) orig.put(e.getLongKey(), e.getValue().original());
		int[] res = BlockWriter.restore(level, outpost);
		int bad = 0;
		for (var e : orig.entrySet()) if (level.getBlockState(BlockPos.of(e.getKey())) != e.getValue()) bad++;
		check("/aldermark undo removes an outpost completely", bad == 0, res[0] + " restored, " + bad + " differ");
		int[] rr = BlockWriter.restore(level, road);
		check("road undo", rr[1] == 0, rr[0] + " restored");
		Conquest.testFastRoads = false;
		next();
	}

	static void finalRestore() {
		Map<Long, BlockState> orig = new HashMap<>();
		for (var e : B.journal.long2ObjectEntrySet()) orig.put(e.getLongKey(), e.getValue().original());
		int[] res = AldermarkCommand.restoreVillage(level, B);
		int bad = 0;
		StringBuilder dbg = new StringBuilder();
		for (var e : orig.entrySet()) if (!sameRestored(level.getBlockState(BlockPos.of(e.getKey())), e.getValue())) { bad++; if (bad <= 4) dbg.append(" [").append(BlockPos.of(e.getKey()).toShortString()).append(" want ").append(e.getValue()).append(" got ").append(level.getBlockState(BlockPos.of(e.getKey()))).append("]"); }
		check("/aldermark restore rebuilds the fortified town's original layout", bad == 0 && res[0] > 0, res[0] + " restored, " + res[1] + " skipped, " + bad + " differ" + dbg);
		check("restored village is immune", B.state == VillageRecord.State.RESTORED && !VillageManager.startSiege(level, B, null, true), B.state);
		check("siege fire burned out on its own (no spread, no leftovers)", !level.getBlockState(fireAt).is(VillagesContent.SIEGE_FIRE) && noVanillaFire(fireAt, 6), level.getBlockState(fireAt) + " sched " + level.getBlockTicks().hasScheduledTick(fireAt, VillagesContent.SIEGE_FIRE) + " t " + level.getGameTime() + " forced " + level.getForceLoadedChunks().contains(ChunkPos.pack(fireAt.getX() >> 4, fireAt.getZ() >> 4)));
		check("A's player block untouched at the end", level.getBlockState(glow).is(Blocks.GLOWSTONE), null);
		// ledger survives a save round trip
		var tag = VillageLedger.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, VillageLedger.get(level)).getOrThrow();
		VillageLedger back = VillageLedger.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		check("ledger saves and loads", back.villages.size() == VillageLedger.get(level).villages.size() && back.playerChunks.contains(ChunkPos.pack(glow)), back.villages.size());
		next();
	}

	// ---- load test: a full siege (with golems) + a rebuild in progress + 30 soldiers ----------------------------------

	static void loadSetup() {
		// clean room: the earlier phases forced ~1350 chunks and sped random ticks up to 300; that is not a siege cost
		level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 3, level.getServer());
		for (long c : new ArrayList<>(level.getForceLoadedChunks())) level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-5000, -128, -5000, 5000, 400, 5000), x -> true))
			if (!(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
		sD = village(level, 18, -18);
		sE = village(level, -18, -18);
		VillageManager.testObservers.clear();
		KingdomPerf.testObservers.clear();
		VillageManager.opInterval = VillageManager.OP_INTERVAL;
		VillageManager.opsPerRun = 1;
		VillageLedger l = VillageLedger.get(level);
		// TEST SETUP: earlier phases injected artificial marks (outpost/road chunks at around -240,-200, 72000 InhabitedTime) into this
		// shared world and ledger; a randomly laid out E (chunk -18,-18) can reach them and was then "protected" by our own test marks.
		l.playerChunks.clear();
		l.tradeChunks.clear();
		if (inhabitedRoadChunk != Long.MIN_VALUE) level.getChunk(ChunkPos.getX(inhabitedRoadChunk), ChunkPos.getZ(inhabitedRoadChunk)).setInhabitedTime(0);
		// a village lived in before the install: one footprint chunk (not the centre) has InhabitedTime >= 72000, nothing marked
		StructureStart sF = village(level, 0, -18);
		VillageRecord F = VillageManager.discover(level, sF, null);
		if (F == null) check("inhabited-chunk village generated", false, null);
		else {
			check("fresh village has no protection reason", VillageManager.assessProtection(level, F, true) == null, VillageManager.assessProtection(level, F, true));
			var bb = F.bounds;
			level.getChunk(bb.minX() >> 4, bb.minZ() >> 4).setInhabitedTime(VillageManager.PRESENCE_TICKS);
			check("village with a lived-in footprint chunk (InhabitedTime >= 72000) is protected", "player presence".equals(VillageManager.assessProtection(level, F, true)), VillageManager.assessProtection(level, F, true));
			check("lived-in village is never besieged (forced siege refused)", !VillageManager.startSiege(level, F, null, true) && F.state != VillageRecord.State.SIEGE, F.state);
		}
		D = VillageManager.discover(level, sD, null);
		E = VillageManager.discover(level, sE, null);
		if (E != null) check("fresh village E is not protected at discovery (no stray player marks, presence or trade history)", E.state != VillageRecord.State.PROTECTED && VillageManager.assessProtection(level, E, true) == null, E.state + " " + E.reason);
		if (D == null || E == null) {
			check("load test villages", false, null);
			next();
			return;
		}
		if (D.state != VillageRecord.State.SIEGE) VillageManager.startSiege(level, D, null, true);
		if (E.state == VillageRecord.State.FREE || E.state == VillageRecord.State.SIEGE) {
			E.state = VillageRecord.State.OCCUPIED;
		}
		observe(Vec3.atCenterOf(D.center));
		observe(Vec3.atCenterOf(E.center));
		KingdomPerf.testObservers.add(Vec3.atCenterOf(D.center));
		KingdomPerf.testObservers.add(Vec3.atCenterOf(E.center));
		// extra squads near the siege to reach ~30 real soldiers
		KingdomApi.spawnSquad(level, Plans.surface(level, D.center.offset(20, 0, 0)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
			List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.ARCHER));
		KingdomApi.spawnSquad(level, Plans.surface(level, D.center.offset(-20, 0, 0)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
			List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.CROSSBOWMAN, UnitTypes.ARCHER));
		l.dirty();
		next();
	}

	static void loadMeasure(MinecraftServer srv) {
		if (age() % 10 == 0) for (KingdomSoldier ks : level.getEntitiesOfClass(KingdomSoldier.class, AABB.of(D.bounds.inflatedBy(48)), e -> true)) ks.setHealth(ks.getMaxHealth()); // keep the siege alive under golem fire
		if (PROFILE) {
			if (age() == 40) {
				for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-5000, -128, -5000, 5000, 400, 5000), x -> true))
					if (e instanceof net.minecraft.world.entity.item.FallingBlockEntity || e instanceof net.minecraft.world.entity.item.ItemEntity) e.discard(); // world-gen leftovers, not siege cost
				SAMPLES.clear();
				CPU_SAMPLES.clear();
				measuring = true;
				java.util.Map<String, Integer> cen = new java.util.TreeMap<>();
				int tot = 0;
				for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-5000, -128, -5000, 5000, 400, 5000), x -> true)) { cen.merge(e.getType().toShortString(), 1, Integer::sum); tot++; }
				info("PROFILE census " + tot + " entities " + cen + " forced chunks " + level.getForceLoadedChunks().size());
			}
			if (age() == 640 || age() == 840 || age() == 1040 || age() == 1240) {
				List<Long> w = new ArrayList<>(SAMPLES), cw = new ArrayList<>(CPU_SAMPLES);
				SAMPLES.clear();
				CPU_SAMPLES.clear();
				w.sort(Long::compare);
				cw.sort(Long::compare);
				double wm = w.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6, wp = w.get((int) (w.size() * 0.95)) / 1e6;
				double cm = cw.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6, cp = cw.isEmpty() ? -1 : cw.get((int) (cw.size() * 0.95)) / 1e6;
				double loadAvg = java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
				info(String.format("PROFILE window ending age %d: mean %.2f ms p95 %.2f max %.2f; server-thread CPU mean %.2f p95 %.2f; machine load %.1f", age(), wm, wp, w.get(w.size() - 1) / 1e6, cm, cp, loadAvg));
				if (age() == 640) {
					int serfsN = level.getEntitiesOfClass(Serf.class, AABB.of(E.bounds.inflatedBy(32)), e -> true).size();
					info(String.format("LOAD full siege D=%s (force %d, burned %d) + rebuild E=%s (plan %d/%d, serfs %d), soldiers loaded %d, golems %d: MSPT mean %.2f p95 %.2f over %d ticks; villages manager %.3f ms/tick; block writes %d",
						D.state, D.forceSpawned, D.journal.size(), E.state, E.planIndex, E.plan.size(), serfsN, KingdomPerf.loaded(level).size(),
						level.getEntitiesOfClass(IronGolem.class, AABB.of(D.bounds.inflatedBy(32)), e -> true).size(), wm, wp, w.size(), VillageManager.meanMs(), BlockWriter.writes));
					check("load test: siege running and rebuild in progress", D.state == VillageRecord.State.SIEGE && E.state == VillageRecord.State.FORTIFYING && serfsN >= 3, D.state + "/" + E.state + " Ereason=" + E.reason + " Dreason=" + D.reason + " Eassess=" + VillageManager.assessProtection(level, E, true));
					check("load test: about 20+ real soldiers", KingdomPerf.loaded(level).size() >= 20, KingdomPerf.loaded(level).size());
					check("load test: full siege mean MSPT under 10", wm < 10, String.format("%.2f", wm));
					// lag budget (BUILD-3.0): p95 <= 8 ms. Judged on the server thread's CPU per tick, so other JVMs on the shared iMac
					// (load 40+ in the merged 4-JVM run: wall p95 29.9) can't fail it; the wall numbers are in the LOAD line above
					check("load test: full siege p95 tick cost at most 8 ms (server-thread CPU; lag budget)", cp >= 0 && cp <= 8,
						String.format("CPU p95 %.2f mean %.2f; wall p95 %.2f mean %.2f; machine load %.1f", cp, cm, wp, wm, loadAvg));
					check("load test: villages manager under 1 ms per tick", VillageManager.meanMs() < 1.0, String.format("%.3f", VillageManager.meanMs()));
				}
				for (var e : new ArrayList<>(level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-5000, -128, -5000, 5000, 400, 5000), x -> true))) {
					if (e instanceof net.minecraft.world.entity.player.Player) continue;
					boolean kill = switch (age()) {
						case 840 -> !(e.position().distanceTo(Vec3.atCenterOf(D.center)) < 80 || e.position().distanceTo(Vec3.atCenterOf(E.center)) < 80);
						case 1040 -> e instanceof KingdomSoldier || e instanceof IronGolem;
						default -> true;
					};
					if (kill) e.discard();
				}
				if (age() == 1240) {
					measuring = false;
					next();
					info("ALDERMARK part " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
					VillageManager.testObservers.clear();
				}
				return;
			}
			return;
		}
		if (age() == 40) {
			SAMPLES.clear();
			measuring = true;
			BlockWriter.writes = 0;
			VillageManager.nanosSpent = 0;
			VillageManager.ticksRun = 0;
		}
		if (age() < 640) return;
		measuring = false;
		List<Long> s = new ArrayList<>(SAMPLES);
		s.sort(Long::compare);
		double mean = s.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
		double p95 = s.isEmpty() ? 0 : s.get((int) (s.size() * 0.95)) / 1e6;
		double max = s.isEmpty() ? 0 : s.get(s.size() - 1) / 1e6;
		int soldiers = KingdomPerf.loaded(level).size();
		int serfs = level.getEntitiesOfClass(Serf.class, AABB.of(E.bounds.inflatedBy(32)), e -> true).size();
		info(String.format("LOAD siege D=%s (force %d, burned %d) + rebuild E=%s (plan %d/%d, serfs %d), soldiers loaded %d, golems %d: MSPT mean %.2f p95 %.2f max %.2f over %d ticks; villages manager %.3f ms/tick; block writes %d",
			D.state, D.forceSpawned, D.journal.size(), E.state, E.planIndex, E.plan.size(), serfs, soldiers,
			level.getEntitiesOfClass(IronGolem.class, AABB.of(D.bounds.inflatedBy(32)), e -> true).size(), mean, p95, max, s.size(), VillageManager.meanMs(), BlockWriter.writes));
		check("load test: siege running and rebuild in progress", D.state == VillageRecord.State.SIEGE && E.state == VillageRecord.State.FORTIFYING && serfs >= 3, D.state + "/" + E.state + " reason " + E.reason + " D " + D.reason);
		check("load test: about 30 real soldiers", soldiers >= 20, soldiers);
		// the soldier AI (K-A base, ~28 real soldiers + golems) dominates the tick; the villages manager itself must stay tiny
		check("load test: mean MSPT under 60 (shared iMac, ~28 soldiers + golems)", mean < 60, String.format("%.2f", mean));
		check("load test: villages manager under 1 ms per tick", VillageManager.meanMs() < 1.0, String.format("%.3f", VillageManager.meanMs()));
		next();
		finish(srv);
	}

	static void finish(MinecraftServer srv) {
		String total = "[villages-selftest] TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		LOG.add(total);
		System.out.println(total);
		phase = -1;
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-villages.log")))) {
			LOG.forEach(w::println);
		} catch (Exception e) {
			System.out.println("[villages-selftest] could not write log: " + e);
		}
		if (Boolean.getBoolean("villages.selftest.exit")) srv.halt(false);
	}

	static BlockState unused(SiegeFireBlock b) {
		return b.defaultBlockState();
	}
}
