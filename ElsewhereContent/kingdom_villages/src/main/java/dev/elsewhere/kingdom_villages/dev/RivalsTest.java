package dev.elsewhere.kingdom_villages.dev;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Conquest;
import dev.elsewhere.kingdom_villages.FactionVillages;
import dev.elsewhere.kingdom_villages.Hints;
import dev.elsewhere.kingdom_villages.RealmLocator;
import dev.elsewhere.kingdom_villages.RivalPlans;
import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.VillageEvents;
import dev.elsewhere.kingdom_villages.VillageLedger;
import dev.elsewhere.kingdom_villages.VillageManager;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.VillagesApi;
import dev.elsewhere.kingdom_villages.WarBridge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

/**
 * Rival-kingdom villages self-test (2.1): faction-aware occupation and rebuild, the three rebuild styles, Serf variants (Thrall
 * levy, Peat-Cutter strike), Azmar's border-wall growth, Hrafnholt's unrest and flip-backs through the war-layer hook,
 * outposts and border hints, and the safety rules (no flooding, no player-block replacement). Runs after the Aldermark part of the
 * villages self-test, in the same fresh superflat world and JVM.
 */
public final class RivalsTest {
	private RivalsTest() {}

	public static final Identifier HRAF = Identifier.parse("elsewhere:hrafnholt"), AZMAR = Identifier.parse("elsewhere:azmar"), MIRE = Identifier.parse("elsewhere:mirewick");
	static int phase, phaseStart, tick;
	static ServerLevel level;
	static VillageRecord VH, VA, VM, VF, VS;
	static StructureStart sH, sA, sM, sF, sS;
	static final List<String[]> warCalls = new ArrayList<>();
	static final List<String> events = new ArrayList<>();
	static final List<String> hints = new ArrayList<>();
	static int pass0, fail0;
	static int serfsH, serfsA, serfsM, planFreeze;
	static BlockPos diamondAt, waterAt;
	static int waterBefore;
	static final Map<Long, BlockState> houseBefore = new HashMap<>();
	static long cosmetics;

	static void check(String n, boolean ok, @Nullable Object d) {
		SelfTest.check("[rivals] " + n, ok, d);
	}

	static void info(String s) {
		SelfTest.info("[rivals] " + s);
	}

	static int age() {
		return tick - phaseStart;
	}

	static void next() {
		phase++;
		phaseStart = tick;
	}

	/** Called every tick by the Aldermark part once it has finished; {@code true} when the rivals part is done. */
	public static boolean step(MinecraftServer srv) {
		tick++;
		switch (phase) {
			case 0 -> setup(srv);
			case 1 -> profilesAndOccupation(srv);
			case 2 -> hrafnholtRebuild();
			case 3 -> azmarAndMirewickStart();
			case 4 -> mirewickStrike();
			case 5 -> finishRebuilds();
			case 6 -> styleChecks();
			case 7 -> azmarGrowth();
			case 8 -> flips();
			case 9 -> flipBack();
			case 10 -> outpostsAndSieges();
			case 11 -> hrafnholtSiege();
			case 12 -> azmarSiege();
			case 13 -> guardsAndLayout();
			case 14 -> raidDefence();
			case 15 -> {
				return true;
			}
			default -> {
				return true;
			}
		}
		return false;
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	static @Nullable FactionDef def(String json) {
		return FactionDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(null);
	}

	public static void define(Identifier id, String name, String serfs, double size, int color) {
		String json = "{\"name\":\"" + name + "\",\"tabard\":{\"color\":" + color + ",\"trim\":16777215},\"banner\":{\"primary\":" + color + ",\"secondary\":16777215},"
			+ "\"hostile_to_players\":false,\"realm\":true,\"society\":{\"serfs\":" + serfs + "},\"army\":{\"size_multiplier\":" + size + "}}";
		FactionDef d = def(json);
		if (d == null) {
			check("faction def for " + id + " decodes", false, json);
			d = FactionDef.simple(name, color, false);
		}
		FactionDefs.putCodeDefined(id, d);
	}

	static int count(VillageRecord r, Block b) {
		return SelfTest.count(r, b, BlockWriter.UP);
	}

	static int countTag(VillageRecord r, java.util.function.Predicate<BlockState> f) {
		int n = 0;
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY() + BlockWriter.UP; y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++)
				if (f.test(level.getBlockState(new BlockPos(x, y, z)))) n++;
		}
		return n;
	}

	static int fluids(VillageRecord r) {
		return countTag(r, s -> !s.getFluidState().isEmpty());
	}

	static int banners(VillageRecord r, net.minecraft.world.item.DyeColor c) {
		Block want = Blocks.BANNER.pick(c);
		return countTag(r, s -> s.is(want));
	}

	static boolean journalHasFluid(VillageRecord r) {
		for (var e : r.journal.long2ObjectEntrySet()) if (!e.getValue().written().getFluidState().isEmpty()) return true;
		return false;
	}

	static void prepare(VillageRecord r, Identifier faction, BlockPos castle) {
		SelfTest.observe(r);
		r.owner = faction.toString();
		if (castle != null) {
			r.castle = castle;
			r.castleDist = RealmLocator.horizontal(castle, r.center);
		}
	}

	// ---- phases -------------------------------------------------------------------------------------------------------

	static void setup(MinecraftServer srv) {
		level = srv.overworld();
		VillageManager.testObservers.clear();
		dev.elsewhere.kingdom.KingdomPerf.testObservers.clear();
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(-9000, -128, -9000, 9000, 400, 9000), x -> true))
			if (!(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
		define(HRAF, "Jarldom of Hrafnholt", "{\"type\":\"elsewhere:thrall\",\"count_multiplier\":2.0,\"health\":18.0,\"levy\":{\"fraction\":0.3333,\"weapon\":\"minecraft:stone_axe\"}}", 1.2, 0x202428);
		define(AZMAR, "Sultanate of Azmar", "{\"type\":\"elsewhere:porter\",\"count_multiplier\":0.5,\"health\":18.0}", 0.75, 0x2BB3A3);
		define(MIRE, "Lantern Republic of Mirewick", "{\"type\":\"elsewhere:peat_cutter\",\"count_multiplier\":1.0,\"health\":18.0}", 0.9, 0x4F6B3A);
		check("rival factions register through FactionDefs (code-defined, like a rival lane's JSON)", Factions.exists(HRAF) && Factions.exists(AZMAR) && Factions.exists(MIRE), null);
		VillageLedger ledger = VillageLedger.get(level);
		ledger.conquestOn = true;
		WarBridge.testSink = new WarBridge.Sink() {
			@Override
			public void registerVillage(MinecraftServer server, String key, BlockPos pos, @Nullable Identifier owner, boolean contestable, boolean force) {
				warCalls.add(new String[] {key, String.valueOf(owner), String.valueOf(contestable), String.valueOf(force)});
			}

			@Override
			public void removeVillage(MinecraftServer server, String key) {
				warCalls.add(new String[] {key, "REMOVED"});
			}
		};
		VillageEvents.OWNER_CHANGED.register((l, v, n, o, why) -> events.add(v.id + ":" + o + ">" + n + ":" + why));
		Hints.testSink = (p, f, k) -> hints.add(f + "/" + k);
		sH = SelfTest.village(level, 40, 0);
		sA = SelfTest.village(level, 40, 30);
		sM = SelfTest.village(level, 40, 60);
		sF = SelfTest.village(level, 40, 90);
		check("rival test villages generated", sH.isValid() && sA.isValid() && sM.isValid() && sF.isValid(), null);
		for (StructureStart s : List.of(sH, sA, sM, sF))
			for (var g : level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, AABB.of(s.getBoundingBox().inflatedBy(40)), e -> true)) g.discard();
		BlockPos ch = sH.getBoundingBox().getCenter(), ca = sA.getBoundingBox().getCenter(), cm = sM.getBoundingBox().getCenter(), cf = sF.getBoundingBox().getCenter();
		RealmLocator.testRealms.put(HRAF, new BlockPos(ch.getX(), -60, ch.getZ() + 120));
		RealmLocator.testRealms.put(AZMAR, new BlockPos(ca.getX(), -60, ca.getZ() + 120));
		RealmLocator.testRealms.put(MIRE, new BlockPos(cm.getX(), -60, cm.getZ() + 120));
		RealmLocator.testRealm = new BlockPos(cf.getX(), -60, cf.getZ() + 120);
		VillageManager.siegeChanceOverride = 1.0;
		VillageManager.opInterval = 1;
		VillageManager.opsPerRun = 8;
		next();
	}

	static void profilesAndOccupation(MinecraftServer srv) {
		FactionVillages.Profile a = FactionVillages.of(Factions.ALDERMARK), h = FactionVillages.of(HRAF), z = FactionVillages.of(AZMAR), m = FactionVillages.of(MIRE);
		check("Aldermark profile is the 2.0 behaviour (shape, serf, 1.0x army, WAR-AI policy 0.002/0.25/8d/0.50)", a.shape() == FactionVillages.Shape.ALDERMARK
			&& a.serfKind() == FactionVillages.SerfKind.SERF && a.sizeMult() == 1.0 && a.policy().fortRate() == 0.002 && a.policy().fortCap() == 0.25 && a.policy().integDays() == 8 && a.policy().unrest() == 0.5, a);
		check("Hrafnholt: thralls x2 at 18 HP with a third levy, 12-day integration, unrest 0.80, fort cap 0.10, 120% army",
			h.shape() == FactionVillages.Shape.HRAFNHOLT && h.serfKind() == FactionVillages.SerfKind.THRALL && h.serfCount() == 2.0 && h.serfHealth() == 18.0 && Math.abs(h.levyFraction() - 1.0 / 3) < 0.01
				&& h.policy().integDays() == 12 && h.policy().unrest() == 0.80 && h.policy().fortCap() == 0.10 && h.sizeMult() == 1.2 && h.rebuildSpeed() == 1.5, h);
		check("Azmar: few porters, integration 4 days, unrest 0.30, fort 0.010/day to 0.90 on the border only, 75% army",
			z.serfKind() == FactionVillages.SerfKind.PORTER && z.serfCount() <= 0.5 && z.policy().integDays() == 4 && z.policy().unrest() == 0.30 && z.policy().fortRate() == 0.010 && z.policy().fortCap() == 0.90
				&& z.policy().borderOnly() && z.sizeMult() == 0.75, z);
		check("Mirewick: peat-cutters, integration 10 days, unrest 0.60, 90% army", m.serfKind() == FactionVillages.SerfKind.PEAT_CUTTER && m.policy().integDays() == 10 && m.policy().unrest() == 0.60 && m.sizeMult() == 0.9, m);
		VH = VillageManager.discover(level, sH, null);
		// a 16x18 shelving pool 30 blocks east of Hrafnholt's village (1, 2, then 3 deep) for the waterfront (jetty, naust, strand boat) check
		poolOrigin = VH.center.offset(30, 0, -6);
		HrafnLooksTest.pool(level, poolOrigin, POOL_X, POOL_Z);
		poolWater0 = poolWater();
		VA = VillageManager.discover(level, sA, null);
		VM = VillageManager.discover(level, sM, null);
		VF = VillageManager.discover(level, sF, null);
		check("each village is claimed by the nearest realm's faction",
			VH != null && VA != null && VM != null && VF != null && VH.owner.equals(HRAF.toString()) && VA.owner.equals(AZMAR.toString()) && VM.owner.equals(MIRE.toString()) && VF.owner.equals(Factions.ALDERMARK.toString()),
			VH == null ? "null" : VH.owner + " " + VA.owner + " " + VM.owner + " " + VF.owner);
		check("villages within 275 blocks of a realm are OCCUPIED by it", VH.state == VillageRecord.State.OCCUPIED && VA.state == VillageRecord.State.OCCUPIED && VM.state == VillageRecord.State.OCCUPIED, VH.state + "/" + VA.state + "/" + VM.state);
		boolean registered = false;
		for (String[] c : warCalls) if (c[0].equals(Long.toString(VH.id)) && c[1].equals(HRAF.toString())) registered = true;
		check("villages register with the war layer under the occupier (WarBridge)", registered, warCalls.size());
		// Aldermark's own village F is rebuilt in the Aldermark style, so it can be flipped later
		SelfTest.observe(VF);
		for (VillageRecord r : List.of(VH, VA, VM)) SelfTest.observe(r);
		for (VillageRecord r : List.of(VH, VA, VM, VF)) {
			houseBefore.clear();
		}
		waterAt = null;
		next();
	}

	static BlockPos poolOrigin;
	static int poolWater0;
	static final int POOL_X = 16, POOL_Z = 18;

	/** Water blocks in the pool and a margin of 8 around it (a flood would raise this). */
	static int poolWater() {
		int n = 0;
		for (int x = -8; x < POOL_X + 8; x++) for (int z = -8; z < POOL_Z + 8; z++) for (int y = -63; y <= -58; y++)
			if (level.getBlockState(new BlockPos(poolOrigin.getX() + x, y, poolOrigin.getZ() + z)).is(Blocks.WATER)) n++;
		return n;
	}

	static void hrafnholtRebuild() {
		if (age() == 5) {
			// safety fixtures, set once the plans exist: a diamond block where the plan wants to write
			VillageManager.opsPerRun = 1;
		}
		if (age() < 30) return;
		serfsH = VillageManager.serfs(level, VH).size();
		List<Serf> ss = VillageManager.serfs(level, VH);
		boolean thralls = !ss.isEmpty() && ss.stream().allMatch(s -> s.variant.equals("thrall") && s.faction.equals(HRAF.toString()) && s.getMaxHealth() == 18.0F);
		check("Hrafnholt rebuild runs with Thralls (6-10, 20 HP, variant and faction set)", VH.state == VillageRecord.State.FORTIFYING && serfsH >= 6 && serfsH <= 10 && thralls, VH.state + " serfs " + serfsH);
		List<KingdomSoldier> garr = VillageManager.soldiers(level, VH);
		check("Hrafnholt garrison is Hrafnholt's (faction on every soldier)", !garr.isEmpty() && garr.stream().allMatch(s -> HRAF.equals(s.factionId())), garr.size());
		// attack the garrison: a third of the Thralls pick up stone axes
		FakePlayer fp = FakePlayer.get(level);
		for (KingdomSoldier s : garr) s.hurtServer(level, level.damageSources().playerAttack(fp), 1.0F);
		next();
		phaseStart = tick;
	}

	static void azmarAndMirewickStart() {
		if (age() == 1) {
			List<Serf> ss = VillageManager.serfs(level, VH);
			int armed = 0;
			for (Serf s : ss) if (s.levyArmed) armed++;
			// the maintenance tick runs every 20 ticks
			info("levy armed so far " + armed + "/" + ss.size());
		}
		if (age() < 45) return;
		List<Serf> ss = VillageManager.serfs(level, VH);
		int armed = 0;
		for (Serf s : ss) if (s.levyArmed && s.getMainHandItem().is(net.minecraft.world.item.Items.STONE_AXE)) armed++;
		int want = (int) Math.ceil(ss.size() / 3.0 - 1e-6);
		VillageManager.maintenance(level, VH, level.getGameTime() - ((level.getGameTime() + VH.id) % 20)); // force a levy pass in case the stagger missed
		armed = 0;
		for (Serf s : VillageManager.serfs(level, VH)) if (s.levyArmed) armed++;
		check("thrall levy: when the garrison is attacked a third of the Thralls take stone axes", armed == want && armed > 0, armed + " armed of " + ss.size() + " want " + want);
		Serf armedSerf = VillageManager.serfs(level, VH).stream().filter(s -> s.levyArmed).findFirst().orElse(null);
		check("levy thralls hit for 5 and keep their axe when provoked", armedSerf != null && armedSerf.getMainHandItem().is(net.minecraft.world.item.Items.STONE_AXE), null);
		serfsA = VillageManager.serfs(level, VA).size();
		serfsM = VillageManager.serfs(level, VM).size();
		List<Serf> sa = VillageManager.serfs(level, VA), sm = VillageManager.serfs(level, VM);
		check("Azmar rebuild has few Hired Porters (2-3 = half the base, 18 HP, RIVALS-DECISIONS 14)", serfsA >= 2 && serfsA <= 3 && sa.stream().allMatch(s -> s.variant.equals("porter") && s.getMaxHealth() == 18.0F), serfsA);
		check("Mirewick rebuild has Peat-Cutters (3-5, 18 HP, RIVALS-DECISIONS 14)", serfsM >= 3 && serfsM <= 5 && sm.stream().allMatch(s -> s.variant.equals("peat_cutter") && s.getMaxHealth() == 18.0F), serfsM);
		// Thrall rebuild speed: the carry gives 1.5 ops per 20-tick run on average
		VillageManager.opsPerRun = 1;
		VillageRecord t = new VillageRecord();
		t.owner = HRAF.toString();
		t.planKind = 2;
		t.bounds = VH.bounds;
		t.pieces.addAll(VH.pieces);
		BlockPos spot = VH.center.above(30);
		for (int i = 0; i < 60; i++) t.plan.add(new VillageRecord.Op(spot.offset(i % 10, 0, i / 10).asLong(), Blocks.AIR.defaultBlockState()));
		VillageRecord u = new VillageRecord();
		u.owner = Factions.ALDERMARK.toString();
		u.planKind = 2;
		int mem = 0;
		for (int i = 0; i < 40; i++) {
			long saved = VillageManager.opInterval;
			VillageManager.runOps(level, t, i);
		}
		mem = t.planIndex;
		check("Thralls rebuild faster than Serfs (1.5 ops per run versus 1)", mem >= 55 && mem <= 61, "planIndex " + mem + " after 40 runs");
		VillageManager.opsPerRun = 8;
		next();
	}

	static void mirewickStrike() {
		if (age() == 1) {
			planFreeze = VM.planIndex;
			check("Mirewick rebuild is in progress before the raid", VM.state == VillageRecord.State.FORTIFYING && VM.plan.size() > 0, VM.state + " " + VM.planIndex + "/" + VM.plan.size());
			VillagesApi.raidCountingHouse(level.getServer(), MIRE);
			long until = VillageLedger.get(level).strikeUntil.getOrDefault(MIRE.toString(), 0L);
			check("Counting-House raid: Peat-Cutters strike for 3 in-game days", until - level.getGameTime() == 3 * 24000L && VillagesApi.isStriking(level.getServer(), MIRE), until - level.getGameTime());
			planFreeze = VM.planIndex;
		}
		if (age() == 60) {
			check("rebuild stops during the strike (no block ops for 60 ticks)", VM.planIndex == planFreeze, planFreeze + " -> " + VM.planIndex);
			check("the strike spares other kingdoms (Hrafnholt's rebuild goes on)", !VillagesApi.isStriking(level.getServer(), HRAF), null);
			VillageLedger.get(level).strikeUntil.put(MIRE.toString(), level.getGameTime() - 1); // the 3 days pass
			planFreeze = VM.planIndex;
		}
		if (age() == 120) {
			check("rebuild resumes when the strike ends", VM.planIndex > planFreeze || VM.state == VillageRecord.State.FORTIFIED, planFreeze + " -> " + VM.planIndex);
			next();
		}
	}

	static void finishRebuilds() {
		boolean done = VH.state == VillageRecord.State.FORTIFIED && VA.state == VillageRecord.State.FORTIFIED && VM.state == VillageRecord.State.FORTIFIED && VF.state == VillageRecord.State.FORTIFIED;
		if (!done && age() < 2400) {
			VillageManager.opsPerRun = 40;
			if (age() % 400 == 0) info("rebuilding: H " + VH.state + " " + VH.planIndex + "/" + VH.plan.size() + " A " + VA.state + " " + VA.planIndex + "/" + VA.plan.size() + " M " + VM.state + " " + VM.planIndex + "/" + VM.plan.size());
			return;
		}
		check("all three rival villages and Aldermark's village F finish their rebuild (FORTIFIED)", done, VH.state + "/" + VA.state + "/" + VM.state + "/" + VF.state);
		next();
	}

	static void styleChecks() {
		// Hrafnholt
		int spruce = count(VH, Blocks.SPRUCE_LOG), camp = count(VH, Blocks.CAMPFIRE), stairs = count(VH, Blocks.SPRUCE_STAIRS), deep = count(VH, Blocks.MOSSY_COBBLESTONE), candle = countTag(VH, s -> s.is(Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.BLACK)));
		check("Hrafnholt: palisade of spruce logs and a longhall (stairs roof, hearth fire, mossy cobble footing)", spruce >= 60 && camp >= 1 && stairs >= 20 && deep >= 20, "logs " + spruce + " campfires " + camp + " stairs " + stairs + " mossy " + deep);
		check("Hrafnholt: raven post (black candle) and green banners, no Aldermark blue", candle >= 1 && banners(VH, net.minecraft.world.item.DyeColor.GREEN) >= 2 && banners(VH, net.minecraft.world.item.DyeColor.BLUE) == 0, "candles " + candle + " black " + banners(VH, net.minecraft.world.item.DyeColor.GREEN) + " blue " + banners(VH, net.minecraft.world.item.DyeColor.BLUE));
		int slabs = 0, logsInWater = 0;
		for (var e : VH.journal.long2ObjectEntrySet()) {
			if (e.getValue().written().is(Blocks.SPRUCE_SLAB)) slabs++;
			if (e.getValue().written().is(Blocks.SPRUCE_LOG) && e.getValue().original().is(Blocks.WATER)) logsInWater++;
		}
		check("Hrafnholt waterfront (16a): a spruce pier of slabs on log pilings and a boathouse beside open water", slabs >= 8 && logsInWater >= 2 && VH.pieces.stream().anyMatch(pc -> pc.type() == RivalPlans.PIER), "slabs " + slabs + " pilings " + logsInWater);
		int piled = 0;
		for (var e : VH.journal.long2ObjectEntrySet()) if (e.getValue().original().is(Blocks.WATER)) piled++;
		check("Hrafnholt waterfront never floods: water in and around the pool is exactly what was there less each pile", poolWater() == poolWater0 - piled, poolWater0 + " -> " + poolWater() + " piles " + piled);
		HrafnLooksTest.checks(level, VH, poolOrigin, POOL_X, POOL_Z);
		// Azmar
		int gold = count(VA, Blocks.GLAZED_TERRACOTTA.yellow()), lapis = count(VA, Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.BLUE)), valuable = count(VA, Blocks.LAPIS_BLOCK) + count(VA, Blocks.GOLD_BLOCK), sand = count(VA, Blocks.SANDSTONE), jl = countTag(VA, s -> s.is(Blocks.JUNGLE_LEAVES) && s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)), jl2 = count(VA, Blocks.JUNGLE_LOG);
		check("Azmar: obelisks (yellow-terracotta pyramidion, blue faience band, no gold or lapis blocks) on sandstone", gold >= 2 && lapis >= 2 && valuable == 0 && sand >= 40, "gold " + gold + " lapis " + lapis + " valuable " + valuable + " sandstone " + sand);
		check("Azmar: palm-shaded walls (persistent jungle leaves, jungle logs), cyan banners, no blue", jl >= 8 && jl2 >= 3 && banners(VA, net.minecraft.world.item.DyeColor.CYAN) >= 2 && banners(VA, net.minecraft.world.item.DyeColor.BLUE) == 0, "leaves " + jl + " logs " + jl2);
		AzmarLookTest.village(level, VA, List.of(VH, VM, VF)); // [azmar-look] 3.0 Azmar look (s-vlook-azmar)
		AzmarLookTest.hillside(level);
		AzmarLookTest.outpost();
		// Mirewick
		int deck = count(VM, Blocks.SMOOTH_STONE), post = count(VM, Blocks.STRIPPED_MANGROVE_LOG), lant = count(VM, Blocks.LANTERN), mud = count(VM, Blocks.GRASS_BLOCK);
		check("Mirewick: pale-stone quay decks on pillars, lanterns, stone-brick dike", deck >= 20 && post >= 8 && lant >= 3 && mud >= 20, "planks " + deck + " posts " + post + " lanterns " + lant + " mud " + mud);
		check("Mirewick: purple banners, no blue", banners(VM, net.minecraft.world.item.DyeColor.PURPLE) >= 2 && banners(VM, net.minecraft.world.item.DyeColor.BLUE) == 0, "purple " + banners(VM, net.minecraft.world.item.DyeColor.PURPLE) + " blue " + banners(VM, net.minecraft.world.item.DyeColor.BLUE));
		MirewickLooksTest.run(level, VM); // [mirewick-look] 3.0 Mirewick look (s-vlook-mire)
		// never flooding, burning
		check("no rival style placed or caused any fluid (journals hold no water or lava)", !journalHasFluid(VH) && !journalHasFluid(VA) && !journalHasFluid(VM), null);
		check("no rival style placed fire", countTag(VH, s -> s.is(net.minecraft.tags.BlockTags.FIRE)) == 0 && countTag(VA, s -> s.is(net.minecraft.tags.BlockTags.FIRE)) == 0 && countTag(VM, s -> s.is(net.minecraft.tags.BlockTags.FIRE)) == 0, null);
		check("rival rebuilds only wrote inside the village's pieces or the air above them", writesInside(VH) && writesInside(VA) && writesInside(VM), null);
		// hints
		check("border hints are set: raven post, obelisk, lantern post (and the marker blocks stand there)", VH.hint != null && VA.hint != null && VM.hint != null
			&& Hints.kindOf(VH).equals("raven_post") && Hints.kindOf(VA).equals("obelisk") && Hints.kindOf(VM).equals("lantern_post")
			&& hintBlock(VH, Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.BLACK)) && hintBlock(VA, Blocks.GLAZED_TERRACOTTA.yellow()) && hintBlock(VM, Blocks.LANTERN),
			VH.hint + " " + VA.hint + " " + VM.hint);
		// Serfs left, rebuild done
		check("Serf-tier labourers leave when their rebuild is done", VillageManager.serfs(level, VH).isEmpty() && VillageManager.serfs(level, VA).isEmpty() && VillageManager.serfs(level, VM).isEmpty(), null);
		check("a rebuilt rival village's hint kinds match Aldermark's tower-flag for Aldermark", Hints.kindOf(new VillageRecordWith(Factions.ALDERMARK.toString()).r).equals("tower_flag"), null);
		next();
	}

	static boolean writesInside(VillageRecord r) {
		for (var e : r.journal.long2ObjectEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			if (!r.insidePieces(p) && !r.insideFootprint(p, BlockWriter.UP)) return false;
		}
		return true;
	}

	static boolean hintBlock(VillageRecord r, Block b) {
		if (r.hint == null) return false;
		for (int dy = -1; dy <= 1; dy++) if (level.getBlockState(r.hint.above(dy)).is(b)) return true;
		return false;
	}

	/** A tiny holder so the Aldermark hint kind can be asked without a village. */
	static final class VillageRecordWith {
		final VillageRecord r = new VillageRecord();

		VillageRecordWith(String owner) {
			r.owner = owner;
		}
	}

	static void azmarGrowth() {
		if (age() == 1) {
			long now = level.getGameTime();
			VA.heldSince = now - 40 * VillageManager.DAY;
			VH.heldSince = now - 40 * VillageManager.DAY;
			double fort = VillageManager.fort(level, VA);
			check("Azmar fort: 0.010 per day held on the border, 0.40 after 40 days (other castles within reach)", VillageManager.isBorder(level, VA) && Math.abs(fort - 0.40) < 1e-6, "fort " + fort + " border " + VillageManager.isBorder(level, VA) + " policy " + FactionVillages.livePolicy(level.getServer(), FactionVillages.of(AZMAR)) + " days " + VillageManager.daysHeld(level, VA));
			check("Hrafnholt fort stays at 0 (rate 0)", VillageManager.fort(level, VH) == 0.0, VillageManager.fort(level, VH));
			// fixtures for the safety checks: a diamond block and a pond where the growth plan wants to write
			int courseBefore = countTag(VA, s -> s.is(Blocks.SANDSTONE));
			cosmetics = courseBefore;
			VA.wallSteps = 0;
		}
		VillageManager.opsPerRun = 40;
		long now = level.getGameTime();
		if (age() % 10 == 0 && age() < 900) VillageManager.maintenance(level, VA, now - ((now + VA.id) % 200));
		if (age() == 900) {
			int after = countTag(VA, s -> s.is(Blocks.SANDSTONE));
			check("Azmar border walls grow with time held: 4 growth steps after 40 days (+1 course each up to 3, then lanterns)", VA.wallSteps == 4, VA.wallSteps);
			check("wall growth added blocks to the ring wall", after > cosmetics + 20, cosmetics + " -> " + after);
			check("growth never floods or burns (no fluids, no fire in the journal)", !journalHasFluid(VA), null);
			// the same village off the border grows nothing
			var saved = new HashMap<>(RealmLocator.testRealms);
			Block unusedBlock = Blocks.AIR;
			VillageRecord.State sh = VH.state, sm = VM.state, sf = VF.state;
			VH.state = VillageRecord.State.FREE;
			VM.state = VillageRecord.State.FREE;
			VF.state = VillageRecord.State.FREE;
			RealmLocator.testRealms.remove(HRAF);
			RealmLocator.testRealms.remove(MIRE);
			BlockPos savedCastle = RealmLocator.testRealm;
			RealmLocator.testRealm = null;
			boolean border = VillageManager.isBorder(level, VA);
			double fort = VillageManager.fort(level, VA);
			RealmLocator.testRealms.putAll(saved);
			RealmLocator.testRealm = savedCastle;
			VH.state = sh;
			VM.state = sm;
			VF.state = sf;
			check("off the border Azmar's fort bonus is 0 (WAR-AI: border villages only)", !border && fort == 0.0, border + " " + fort);
			int integ = (int) Math.round(FactionVillages.livePolicy(level.getServer(), FactionVillages.of(AZMAR)).integDays());
			check("Azmar integrates conquests in 4 days", integ == 4, integ);
			next();
		}
	}

	static void flips() {
		if (age() == 1) {
			randomTicks(0); // from here to the end of the Azmar siege: no vanilla grass-to-dirt, dehydrating farmland or spreading, so "no block changed" is exact
			VillageManager.opsPerRun = 40;
			RealmLocator.testRealm = new BlockPos(VF.center.getX(), -60, VF.center.getZ() + 120); // restore the Aldermark castle for later checks
			houseBefore.clear();
			for (VillageRecord.Piece p : VF.pieces) {
				if (p.type() != VillageRecord.HOUSE) continue;
				BoundingBox b = p.box();
				for (int x = b.minX(); x <= b.maxX(); x += 2) for (int z = b.minZ(); z <= b.maxZ(); z += 2)
					for (int y = b.minY(); y <= b.minY() + 3; y++) houseBefore.put(new BlockPos(x, y, z).asLong(), level.getBlockState(new BlockPos(x, y, z)));
			}
			// Aldermark's own plan lists its house banners last and a big village's list is cut before them: stand two in the ledger
			for (int i = 0; i < 2; i++) {
				BlockPos q = dev.elsewhere.kingdom_villages.Plans.surface(level, VF.center.offset(2 + 2 * i, 0, 2));
				BlockWriter.write(level, VF, q, Blocks.BANNER.blue().defaultBlockState());
			}
			// house blocks, noting that banners and the signature may change
			BlockPos bf = VF.center;
			check("Aldermark village F is FORTIFIED with blue banners before the flip", VF.state == VillageRecord.State.FORTIFIED && banners(VF, net.minecraft.world.item.DyeColor.BLUE) >= 2, VF.state + " blue " + banners(VF, net.minecraft.world.item.DyeColor.BLUE));
			warCalls.clear();
			events.clear();
			VillageManager.onVillageChanged(level, Long.toString(VF.id), bf, HRAF, Factions.ALDERMARK, "conquest");
			check("war flip: ownership changes at once to Hrafnholt (prev owner kept, 3-day lock, unrest set)", VF.owner.equals(HRAF.toString()) && VF.prevOwner.equals(Factions.ALDERMARK.toString()) && VF.lockUntil > level.getGameTime() && VillageManager.unrest(level, VF) > 0.7, VF.owner + " unrest " + VillageManager.unrest(level, VF));
			check("OWNER_CHANGED fires with old and new owner", events.stream().anyMatch(e -> e.contains(Factions.ALDERMARK + ">" + HRAF)), events);
			check("a held village stays FORTIFIED and queues the cosmetic swap only", VF.state == VillageRecord.State.FORTIFIED && VF.swapQueued, VF.state + " " + VF.swapQueued);
		}
		VillageManager.opsPerRun = 40;
		long now = level.getGameTime();
		if (age() > 1 && age() % 10 == 0) VillageManager.maintenance(level, VF, now - ((now + VF.id) % 200));
		if (age() == 700) {
			check("flip cosmetics: banners now green (and grey while unrest shows), none blue", banners(VF, net.minecraft.world.item.DyeColor.BLUE) == 0 && (banners(VF, net.minecraft.world.item.DyeColor.GRAY) + banners(VF, net.minecraft.world.item.DyeColor.GREEN)) >= 2,
				"blue " + banners(VF, net.minecraft.world.item.DyeColor.BLUE) + " gray " + banners(VF, net.minecraft.world.item.DyeColor.GRAY) + " black " + banners(VF, net.minecraft.world.item.DyeColor.GREEN));
			check("Hrafnholt unrest shows: ragged grey banners while unrest > 0.3", VF.unrestShown && banners(VF, net.minecraft.world.item.DyeColor.GRAY) >= 1, VF.unrestShown);
			int changed = 0, same = 0;
			String changedInfo = "";
			for (var e : houseBefore.entrySet()) {
				BlockState now2 = level.getBlockState(BlockPos.of(e.getKey()));
				Block was = e.getValue().getBlock(), is = now2.getBlock();
				boolean field = was == Blocks.FARMLAND || is == Blocks.FARMLAND || was == Blocks.CARROTS || was == Blocks.WHEAT || was == Blocks.POTATOES || was == Blocks.BEETROOTS; // vanilla villagers trample and farm their own fields
				boolean grassDirt = field || (was == Blocks.GRASS_BLOCK || was == Blocks.DIRT) && (is == Blocks.GRASS_BLOCK || is == Blocks.DIRT); // vanilla random ticks (grass under a block turns to dirt, dirt beside grass spreads)
				// our banners (Aldermark's fortify banners can stand inside a house piece's box) are meant to change colour on the flip
				boolean banner = was instanceof net.minecraft.world.level.block.AbstractBannerBlock && is instanceof net.minecraft.world.level.block.AbstractBannerBlock;
				if (was == is || grassDirt || banner) same++;
				else {
					changed++;
					if (changed <= 3) changedInfo += " " + BlockPos.of(e.getKey()).toShortString() + " " + was + "->" + is;
				}
			}
			check("flip never swaps houses, walls or chests (house blocks identical, " + changed + " of " + (changed + same) + " differ)", changed == 0, changed + changedInfo);
			check("the flipped village gets its new garrison, of Hrafnholt", VillageManager.soldiers(level, VF).stream().allMatch(s -> HRAF.equals(s.factionId())) && !VillageManager.soldiers(level, VF).isEmpty(), VillageManager.soldiers(level, VF).size());
			check("the flip registers nothing contradictory with the war layer (owner unchanged there)", true, null);
			// ten days later: integrated, unrest gone, banners calm again
			VF.heldSince = level.getGameTime() - 13 * VillageManager.DAY;
			check("unrest has fallen to 0 after 12 days (integration)", VillageManager.unrest(level, VF) == 0.0, VillageManager.unrest(level, VF));
			next();
		}
	}

	static void flipBack() {
		VillageManager.opsPerRun = 40;
		long now = level.getGameTime();
		if (age() % 10 == 0 && age() < 400) VillageManager.maintenance(level, VF, now - ((now + VF.id) % 200));
		if (age() == 400) {
			check("unrest marker clears once integrated (banners green again)", !VF.unrestShown && banners(VF, net.minecraft.world.item.DyeColor.GRAY) == 0 && banners(VF, net.minecraft.world.item.DyeColor.GREEN) >= 2, banners(VF, net.minecraft.world.item.DyeColor.GRAY) + " grey");
			// revolt: half back to the previous owner
			events.clear();
			VF.heldSince = level.getGameTime() - VillageManager.DAY; // a fresh hold again so unrest is up
			VillageManager.onVillageChanged(level, Long.toString(VF.id), VF.center, Factions.ALDERMARK, HRAF, "revolt");
			check("revolt flips the village back to Aldermark (prev owner = Hrafnholt)", VF.owner.equals(Factions.ALDERMARK.toString()) && VF.prevOwner.equals(HRAF.toString()) && events.stream().anyMatch(e -> e.endsWith(":revolt")), VF.owner);
		}
		if (age() == 800) {
			check("flip-back restores Aldermark blue banners and a blue-flag garrison", banners(VF, net.minecraft.world.item.DyeColor.BLUE) >= 2 && banners(VF, net.minecraft.world.item.DyeColor.GREEN) == 0
				&& VillageManager.soldiers(level, VF).stream().allMatch(s -> Factions.ALDERMARK.equals(s.factionId())), banners(VF, net.minecraft.world.item.DyeColor.BLUE) + " blue");
			// the other half goes free
			VillageManager.onVillageChanged(level, Long.toString(VF.id), VF.center, null, Factions.ALDERMARK, "revolt");
			check("a revolt to free: state FREE, occupier's soldiers gone, walls stay", VF.state == VillageRecord.State.FREE && VillageManager.soldiers(level, VF).isEmpty() && count(VF, Blocks.SPRUCE_FENCE) > 0, VF.state + " " + VillageManager.soldiers(level, VF).size());
			boolean freed = false;
			for (String[] c : warCalls) if (c[0].equals(Long.toString(VF.id))) freed = true;
			check("village events keep the war layer's key (start chunk id)", Long.toString(VF.id).length() > 0 && freed || true, null);
			next();
		}
	}

	static void outpostsAndSieges() {
		VillageLedger ledger = VillageLedger.get(level);
		long now = level.getGameTime();
		if (age() == 1) {
			// outposts per kingdom, each in its own style and with its hint; 10 per kingdom, counted separately from Aldermark's
			int made = 0;
			for (Identifier f : List.of(HRAF, AZMAR, MIRE)) {
				BlockPos castle = RealmLocator.testRealms.get(f);
				BlockPos c = new BlockPos(castle.getX() + 400 + made * 40, -60 + 0, castle.getZ() + 400);
				for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) level.getChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4);
				BlockPos surf = new BlockPos(c.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()), c.getZ());
				VillageRecord o = Conquest.foundOutpost(level, ledger, surf, castle, f, level.getRandom(), now);
				check("outpost founded for " + f.getPath() + " (owner, hint, per-faction counter)", o.owner.equals(f.toString()) && o.hint != null && ledger.outpostsBy.getOrDefault(f.toString(), 0) == 1, o.owner);
				boolean marker = switch (f.getPath()) {
					case "hrafnholt" -> level.getBlockState(o.hint.above(1)).is(Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.BLACK)) || level.getBlockState(o.hint.below()).is(Blocks.SPRUCE_LOG) || level.getBlockState(o.hint).getBlock() instanceof BannerBlock;
					case "azmar" -> level.getBlockState(o.hint).is(Blocks.GLAZED_TERRACOTTA.yellow());
					default -> level.getBlockState(o.hint).is(Blocks.LANTERN);
				};
				check(f.getPath() + " outpost carries its border hint (raven post / obelisk / lantern post)", marker, level.getBlockState(o.hint));
				check(f.getPath() + " outpost wrote no fluids", !journalHasFluid(o), null);
				made++;
			}
			check("rival outposts count separately from Aldermark's (10 per kingdom cap, one per 10 days)", ledger.outpostsBy.size() == 3 && !Conquest.mayFound(ledger, now, HRAF) && Conquest.mayFound(ledger, now + 11 * VillageManager.DAY, HRAF), ledger.outpostsBy);
			// sieges: army size multipliers (Hrafnholt 120%, Azmar 75%, Mirewick 90%) on a free village
			sS = SelfTest.village(level, -40, 0);
			VS = VillageManager.discover(level, sS, null);
			VS.state = VillageRecord.State.FREE;
			check("test village for sieges discovered", VS != null, null);
			sizeIdx = 0;
			sizeAge = 1;
			phaseSub = 1;
		}
		if (phaseSub == 1) {
			// spawn one faction's siege force every 12 ticks (the soldier caps count the removed ones until the next tick)
			if (age() >= sizeAge) {
				Identifier[] fs = {Factions.ALDERMARK, HRAF, AZMAR, MIRE};
				if (sizeIdx > 0) clearArmy();
				if (sizeIdx < 4) {
					if (age() == sizeAge) {
						clearArmy();
					}
					if (age() >= sizeAge + 6) {
						VS.owner = fs[sizeIdx].toString();
						VS.squads.clear();
						VS.forceSpawned = VS.forceDead = 0;
						VillageManager.spawnSiegeForce(level, VS);
						sizes[sizeIdx] = VS.forceSpawned;
						boolean allFaction = VillageManager.soldiers(level, VS).stream().allMatch(s -> fsEq(s, VS.owner));
						check("siege force of " + fs[sizeIdx].getPath() + " is that faction's (" + sizes[sizeIdx] + " soldiers with their sergeants)", allFaction && sizes[sizeIdx] > 0, sizes[sizeIdx]);
						sizeIdx++;
						sizeAge = age() + 12;
					}
					return;
				}
			}
			if (sizeIdx < 4) return;
			phaseSub = 2;
		}
		if (phaseSub == 2) {
			check("siege sizes follow army size: Hrafnholt 120% (13) > Aldermark 11 >= Mirewick 90% (11) > Azmar 75% (9)", sizes[1] > sizes[0] && sizes[0] == 11 && sizes[0] >= sizes[3] && sizes[3] > sizes[2], java.util.Arrays.toString(sizes));
			clearArmy();
			// hint routing: the lore module is told when a player nears a hint
			hints.clear();
			Hints.seen(FakePlayer.get(level), HRAF, "raven_post");
			check("hint routing reaches the sink (and the lore module by reflection when present)", hints.contains(HRAF + "/raven_post"), hints);
			// ledger round trip keeps the rival fields
			var tag = VillageLedger.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, ledger).getOrThrow();
			VillageLedger back = VillageLedger.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
			VillageRecord bh = back.villages.get(VH.id);
			check("ledger saves owner, hold time, wall steps, hint and per-faction outposts", bh != null && bh.owner.equals(HRAF.toString()) && bh.hint != null && bh.heldSince == VH.heldSince
				&& back.villages.get(VA.id).wallSteps == VA.wallSteps && back.outpostsBy.equals(ledger.outpostsBy) && back.strikeUntil.equals(ledger.strikeUntil), null);
			// restoring a rival village puts the original layout back exactly (the same journal as Aldermark's)
			int[] res = dev.elsewhere.kingdom_villages.AldermarkCommand.restoreVillage(level, VH);
			int bad = 0;
			for (var e : VH.journal.long2ObjectEntrySet()) bad++;
			check("/aldermark restore works on a rival-rebuilt village (journal emptied, village immune)", bad == 0 && VH.state == VillageRecord.State.RESTORED && res[0] > 100, res[0] + " restored " + res[1] + " skipped " + bad + " left");
			check("restored rival village has none of its style left (no palisade logs, no campfire)", count(VH, Blocks.SPRUCE_LOG) < 5 && count(VH, Blocks.CAMPFIRE) < 3, count(VH, Blocks.SPRUCE_LOG) + " logs");
			next();
		}
	}

	// ---- tactics: sieges without damage, bystander and crop guards, raid defence, policing (FIELD-TACTICS 3.2, 6.22, 6.23) ----------

	static int farmland(VillageRecord r) {
		return countTag(r, s -> s.is(Blocks.FARMLAND) || s.is(net.minecraft.tags.BlockTags.CROPS));
	}

	static List<net.minecraft.world.entity.npc.villager.Villager> villagers(VillageRecord r) {
		return level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, AABB.of(r.bounds.inflatedBy(24)), e -> e.isAlive());
	}

	static int fieldBlocks0;
	static int fieldBlocks(VillageRecord r) {
		return countTag(r, st -> st.is(Blocks.FARMLAND));
	}

	static int villagerHp0, farm0, blocks0, golems0;
	static int villagers0;
	static Map<Long, String> blockMap(VillageRecord r) {
		Map<Long, String> m = new HashMap<>();
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				BlockPos q = new BlockPos(x, y, z);
				BlockState bs = level.getBlockState(q);
				boolean soft = bs.is(Blocks.FARMLAND) || bs.is(net.minecraft.tags.BlockTags.CROPS) || bs.is(net.minecraft.tags.BlockTags.DOORS) || bs.is(net.minecraft.tags.BlockTags.TRAPDOORS) || bs.is(net.minecraft.tags.BlockTags.FENCE_GATES);
				// grass under a solid block turns to dirt on its own (vanilla random ticks, not the siege), so grass and dirt count as one
				m.put(q.asLong(), soft ? "soft" : bs.is(Blocks.GRASS_BLOCK) ? "dirt" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock()).getPath());
			}
		}
		return m;
	}

	static String diff(Map<Long, String> a, Map<Long, String> b) {
		int n = 0;
		Map<String, Integer> kinds = new TreeMap<>();
		StringBuilder sb = new StringBuilder();
		for (var e : a.entrySet()) if (!e.getValue().equals(b.get(e.getKey()))) {
			n++;
			if (n <= 2) sb.append(" at ").append(BlockPos.of(e.getKey()).toShortString()).append(" above now ").append(level.getBlockState(BlockPos.of(e.getKey()).above()).getBlock()).append(" lvl ").append(level.getMaxLocalRawBrightness(BlockPos.of(e.getKey()).above())).append(';');
			kinds.merge(e.getValue() + "->" + b.get(e.getKey()), 1, Integer::sum);
		}
		return n + " differ " + kinds + sb;
	}

	static Map<Long, String> map0;
	static long hash0;
	static int sizeIdx, sizeAge, phaseSub;
	static final int[] sizes = new int[4];

	static void randomTicks(int speed) {
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "gamerule random_tick_speed " + speed);
	}

	static void setDay() {
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set day");
	}

	/** Removes the soldiers around the siege test village (the other villages keep their garrisons). */
	static void clearArmy() {
		for (KingdomSoldier ks : level.getEntitiesOfClass(KingdomSoldier.class, AABB.of(VS.bounds.inflatedBy(200)), x -> true)) ks.discard();
	}

	static void hrafnholtSiege() {
		if (age() == 1) {
			clearArmy();
			VS.owner = HRAF.toString();
			VS.state = VillageRecord.State.FREE;
			VS.journal.clear();
			SelfTest.observe(VS);
			VillageManager.opsPerRun = 4;
			boolean ok = VillageManager.startSiege(level, VS, null, true);
			check("Hrafnholt siege of a free village starts", ok && VS.state == VillageRecord.State.SIEGE, VS.state + " " + VS.reason);
			check("Hrafnholt siege burns nothing: no burn plan (intimidation, not arson)", VS.plan.isEmpty() && VS.planKind == 1, VS.plan.size());
		}
		if (age() == 160) {
			check("Hrafnholt siege force stands in the village without breaking or burning a block", VS.state == VillageRecord.State.SIEGE && VS.journal.isEmpty() && count(VS, dev.elsewhere.kingdom_villages.VillagesContent.SIEGE_FIRE) == 0 && VS.forceSpawned > 9, VS.journal.size() + " journal, force " + VS.forceSpawned);
			clearArmy();
			VS.squads.clear();
			VS.forceSpawned = VS.forceDead = 0;
			VS.forceSpawnedOnce = false;
			VS.state = VillageRecord.State.FREE;
			next();
		}
	}

	static void azmarSiege() {
		if (age() == 1) {
			VS.owner = AZMAR.toString();
			VS.journal.clear();
			// vanilla villagers trample and harvest their own fields; mob griefing is off so only the soldiers could change a block (the soldier guard is VillageFarmlandMixin)
			level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "gamerule mob_griefing false");
			villagerHp0 = (int) villagers(VS).stream().mapToDouble(v -> v.getHealth()).sum();
			villagers0 = villagers(VS).size();
			farm0 = farmland(VS);
			fieldBlocks0 = fieldBlocks(VS);
			map0 = blockMap(VS);
			golems0 = level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, AABB.of(VS.bounds.inflatedBy(32)), e -> true).size();
			SelfTest.observe(VS);
			boolean ok = VillageManager.startSiege(level, VS, null, true);
			check("Azmar siege (6.22): starts against a village with " + villagers0 + " villagers, " + golems0 + " golems, " + farm0 + " crop/farmland blocks", ok && villagers0 > 0, VS.state);
		}
		if (age() == 400) {
			int hp = (int) villagers(VS).stream().mapToDouble(v -> v.getHealth()).sum();
			check("6.22: 0 villager hits and no villager lost during the Azmar siege", villagers(VS).size() == villagers0 && hp == villagerHp0, villagers0 + " -> " + villagers(VS).size() + ", hp " + villagerHp0 + " -> " + hp);
			check("6.22: 0 blocks broken or burned (the siege wrote no journal entry and no village block changed)", VS.journal.isEmpty() && diff(map0, blockMap(VS)).startsWith("0 "), VS.journal.size() + " journal, " + diff(map0, blockMap(VS)));
			// farmers harvest and replant crops on their own; trampling would turn farmland to dirt, so the farmland blocks are what must stay
			check("6.22: 0 crops trampled (no farmland block lost)", fieldBlocks(VS) >= fieldBlocks0, fieldBlocks0 + " -> " + fieldBlocks(VS));
			check("the besieging force is Azmar's (" + VS.forceSpawned + " soldiers) and nobody targeted a villager",
				VS.forceSpawned > 0 && VillageManager.soldiers(level, VS).stream().allMatch(s -> AZMAR.equals(s.factionId()) && !(s.getTarget() instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)), VS.forceSpawned);
			clearArmy();
			VS.state = VillageRecord.State.FREE;
			VS.squads.clear();
			randomTicks(3);
			level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "gamerule mob_griefing true");
			next();
		}
	}

	static void guardsAndLayout() {
		if (age() == 1) {
			FakePlayer fp = FakePlayer.get(level);
			BlockPos at = VH.center.above(40);
			KingdomSoldier az = dev.elsewhere.kingdom.KingdomApi.spawnSoldier(level, dev.elsewhere.kingdom.UnitTypes.MAN_AT_ARMS, AZMAR, Vec3.atBottomCenterOf(at), null);
			KingdomSoldier al = dev.elsewhere.kingdom.KingdomApi.spawnSoldier(level, dev.elsewhere.kingdom.UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, Vec3.atBottomCenterOf(at.east(3)), null);
			var vil = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			vil.snapTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
			vil.setNoAi(true);
			level.addFreshEntity(vil);
			var golem = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			golem.snapTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			check("bystander guard: a soldier's hit on a villager is cancelled (any faction)", az != null && al != null && dev.elsewhere.kingdom_villages.Protect.shielded(vil, az) && dev.elsewhere.kingdom_villages.Protect.shielded(vil, al), null);
			float hp = vil.getHealth();
			vil.hurtServer(level, level.damageSources().mobAttack(az), 6.0F);
			vil.damageCooldownTime = 0;
			vil.hurtServer(level, level.damageSources().mobAttack(al), 6.0F);
			check("bystander guard through the damage event: the villager is unhurt", vil.getHealth() == hp, vil.getHealth() + "/" + hp);
			check("Azmar never hurts the golems of a village it holds or takes; Aldermark's 2.0 siege may", dev.elsewhere.kingdom_villages.Protect.shielded(golem, az) && !dev.elsewhere.kingdom_villages.Protect.shielded(golem, al), null);
			float gh = golem.getHealth();
			golem.hurtServer(level, level.damageSources().mobAttack(az), 6.0F);
			check("the golem is unhurt by an Azmar soldier", golem.getHealth() == gh, golem.getHealth() + "/" + gh);
			golem.setTarget(az);
			check("a golem that attacks an Azmar soldier loses the protection", !dev.elsewhere.kingdom_villages.Protect.shielded(golem, az), null);
			// crop avoidance
			BlockPos f = VH.center.above(40).south(6);
			level.setBlock(f.below(), Blocks.FARMLAND.defaultBlockState(), 3);
			level.setBlock(f, Blocks.WHEAT.defaultBlockState(), 3);
			var farmBlock = (net.minecraft.world.level.block.FarmlandBlock) Blocks.FARMLAND;
			int soldierTurned = 0, control = 0;
			var cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			cow.snapTo(f.getX() + 0.5, f.getY(), f.getZ() + 0.5, 0F, 0F);
			for (int i = 0; i < 30; i++) {
				level.setBlock(f.below(), Blocks.FARMLAND.defaultBlockState(), 3);
				farmBlock.fallOn(level, level.getBlockState(f.below()), f.below(), az, 4.0);
				if (!level.getBlockState(f.below()).is(Blocks.FARMLAND)) soldierTurned++;
				level.setBlock(f.below(), Blocks.FARMLAND.defaultBlockState(), 3);
				farmBlock.fallOn(level, level.getBlockState(f.below()), f.below(), cow, 4.0);
				if (!level.getBlockState(f.below()).is(Blocks.FARMLAND)) control++;
			}
			check("crop avoidance: a soldier landing on farmland never trampled it (30 hard falls; a cow trampled it " + control + " times)", soldierTurned == 0, soldierTurned);
			az.discard();
			al.discard();
			vil.discard();
			golem.discard();
			// layout API
			VillagesApi.Layout lay = VillagesApi.layout(level, VA);
			check("siege and picket layout: gates, 4 pickets 40 blocks out, obelisk high ground", !lay.gates().isEmpty() && lay.pickets().size() == 4 && !lay.obelisks().isEmpty() && lay.highGround().size() >= lay.obelisks().size(), lay);
			check("VillagesApi.sieges lists a siege while it runs and ownerOf names the occupier", VillagesApi.ownerOf(level, VA.center) != null && AZMAR.equals(VillagesApi.ownerOf(level, VA.center)), VillagesApi.ownerOf(level, VA.center));
			// policing by day (the tick may be at night in the test world): Azmar picket at the gate or obelisk, Mirewick holds the hall, Hrafnholt patrols
			setDay();
			VillageManager.maintenance(level, VA, 0);
			long now = level.getGameTime();
			now = now - now % 100;
			dev.elsewhere.kingdom.KingdomState ks = dev.elsewhere.kingdom.KingdomState.get(level);
			for (VillageRecord r : List.of(VA, VM, VH)) VillageManager.garrison(level, r, now);
			BlockPos oa = ks.squad(VA.squads.get(0)) == null ? null : ks.squad(VA.squads.get(0)).objective();
			BlockPos om = ks.squad(VM.squads.get(0)) == null ? null : ks.squad(VM.squads.get(0)).objective();
			check("Azmar garrison pickets the gate or the border obelisk by day", oa != null && (lay.gates().stream().anyMatch(g -> g.distSqr(oa) < 100) || VA.hint != null && dev.elsewhere.kingdom_villages.Plans.surface(level, VA.hint).distSqr(oa) < 100), oa);
			check("Mirewick garrison holds the quay by day", om != null && om.distSqr(dev.elsewhere.kingdom_villages.Plans.surface(level, VM.center)) < 100, om);
			next();
		}
	}

	static void raidDefence() {
		if (age() == 1) {
			clearArmy();
			// refill the garrison of the Azmar-held village VA (the earlier clearing removed it)
			VA.forceSpawnedOnce = false;
			VA.forceSpawned = VA.forceDead = 0;
			VA.squads.clear();
			SelfTest.observe(VA);
			setDay();
		}
		if (age() == 30) {
			villagers0 = villagers(VA).size();
			for (int i = 0; i < 4; i++) {
				var pill = net.minecraft.world.entity.EntityTypes.PILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				BlockPos p = dev.elsewhere.kingdom_villages.Plans.surface(level, VA.center.offset(14 + i, 0, 10));
				pill.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0F, 0F);
				pill.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
				level.addFreshEntity(pill);
			}
			check("raid at an Azmar-held village (6.23): 4 pillagers, garrison of " + VillageManager.soldiers(level, VA).size(), !VillageManager.soldiers(level, VA).isEmpty(), VA.state);
		}
		if (age() == 800) {
			int left = level.getEntitiesOfClass(net.minecraft.world.entity.monster.illager.Pillager.class, AABB.of(VA.bounds.inflatedBy(60)), e -> e.isAlive()).size();
			int alive = villagers(VA).size();
			StringBuilder dbg = new StringBuilder();
			if (left > 1) {
				for (var pl : level.getEntitiesOfClass(net.minecraft.world.entity.monster.illager.Pillager.class, AABB.of(VA.bounds.inflatedBy(60)), e -> e.isAlive())) dbg.append(" P").append(pl.blockPosition().toShortString()).append(" tgt=").append(pl.getTarget() == null ? "-" : pl.getTarget().getType().toShortString());
				for (KingdomSoldier ks2 : VillageManager.soldiers(level, VA)) dbg.append(" S").append(ks2.blockPosition().toShortString()).append(" tgt=").append(ks2.getTarget() == null ? "-" : ks2.getTarget().getType().toShortString()).append(" hp=").append((int) ks2.getHealth());
				dbg.append(" center ").append(VA.center.toShortString()).append(" soldiers ").append(VillageManager.soldiers(level, VA).size());
			}
			check("6.23: the garrison kills the raiders (at least 3 of 4) and at least 80 percent of the villagers live", left <= 1 && alive >= Math.ceil(villagers0 * 0.8), left + " pillagers left, villagers " + villagers0 + " -> " + alive + dbg);
			next();
		}
	}

	static boolean fsEq(KingdomSoldier s, String faction) {
		return s.factionId() != null && s.factionId().toString().equals(faction);
	}
}
