package dev.elsewhere.test;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.core.atlas.Atlas;
import dev.elsewhere.core.gear.GearRules;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.travel.Anchors;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.trophy.Trophies;
import dev.elsewhere.palehollow.LanternEvent;
import dev.elsewhere.palehollow.PaleHollowDimension;
import dev.elsewhere.palehollow.entity.CompanionEntity;
import dev.elsewhere.palehollow.world.PHSites;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import dev.elsewhere.v2.Bounties;
import dev.elsewhere.v2.V2Content;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import dev.elsewhere.core.travel.SafeLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** 2.0 core features: amber, gear rules, first trip, compass, anchors, atlas, trophies, fishing, bounties, companions, sites, lantern. */
final class V2SelfTest {
	private V2SelfTest() {}

	private static void check(boolean ok, String what) {
		SelfTest.check(ok, what);
	}

	private static void guard(String name, Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			Elsewhere.LOG.error("[selftest] {} threw", name, t);
			check(false, name + " threw " + t);
		}
	}

	private static FakePlayer fake(ServerLevel l, String n) {
		return FakePlayer.get(l, new GameProfile(UUID.randomUUID(), n));
	}

	static void run(MinecraftServer server, ServerLevel overworld, ServerLevel hollow) {
		guard("amber", () -> amber(hollow));
		guard("gear rules", () -> gear(hollow));
		guard("first trip + compass", () -> trips(overworld, hollow));
		guard("anchors", () -> anchors(hollow));
		guard("atlas + trophies", () -> trophies(hollow));
		guard("fishing", () -> fishing(server, hollow));
		guard("bounties", () -> bounties(server, hollow));
		guard("companions", () -> companions(hollow));
		guard("sites", () -> sites(server, hollow));
	}

	private static void amber(ServerLevel hollow) {
		var pf = hollow.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
		check(pf.get(ResourceKey.create(Registries.PLACED_FEATURE, Elsewhere.id("ore_amber"))).isPresent()
			&& pf.get(ResourceKey.create(Registries.PLACED_FEATURE, Elsewhere.id("ore_amber_buried"))).isPresent(), "amber ore placed features load from data");
		int ore = 0, chunks = 0;
		for (int cx = 40; cx < 48; cx++) for (int cz = 40; cz < 48; cz++) {
			var chunk = hollow.getChunk(cx, cz, ChunkStatus.FULL, true);
			chunks++;
			for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = hollow.getMinY(); y < 40; y++) {
				if (chunk.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z)).is(ModBlocks.AMBER_ORE)) ore++;
			}
		}
		check(ore > chunks / 2, "amber ore generates: " + ore + " blocks in " + chunks + " chunks (" + String.format("%.1f", ore / (double)chunks) + "/chunk)");
		FakePlayer p = fake(hollow, "Miner");
		BlockPos pos = new BlockPos(0, 120, 0);
		var drops = net.minecraft.world.level.block.Block.getDrops(ModBlocks.AMBER_ORE.defaultBlockState(), hollow, pos, null, p, new ItemStack(Items.IRON_PICKAXE));
		check(drops.stream().anyMatch(s -> s.is(ModItems.AMBER) && s.getCount() >= 1), "amber ore drops Amber with an iron pickaxe");
		check(ModBlocks.AMBER_ORE.defaultBlockState().requiresCorrectToolForDrops(), "amber ore needs a pickaxe");
	}

	private static void gear(ServerLevel hollow) {
		Item tagged = null;
		for (Item i : BuiltInRegistries.ITEM) if (new ItemStack(i).is(GearRules.SLOW_MENDING)) { tagged = i; break; }
		check(tagged != null, "#elsewhere:slow_mending has members");
		if (tagged != null) {
			check(GearRules.mendingRepair(new ItemStack(tagged), 2) == 1, "slow Mending: 1 durability per XP on a tagged item (vanilla 2)");
		}
		check(GearRules.mendingRepair(new ItemStack(Items.DIAMOND_PICKAXE), 2) == 2, "vanilla items keep 2 durability per XP");
		FakePlayer p = fake(hollow, "Totem");
		ItemStack halo = new ItemStack(dev.elsewhere.tiergear.TierGear.HALO_OF_THE_OUTER_END);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, halo);
		boolean prot = GearRules.isElsewhereProtection(halo);
		long now = hollow.getGameTime();
		GearRules.setLastProtection(p.getUUID(), now - 10 * 20);
		check(!prot || GearRules.lockedOut(p), "30 s lockout: still locked 10 s after a protection");
		GearRules.setLastProtection(p.getUUID(), now - 29 * 20);
		check(!prot || GearRules.lockedOut(p), "30 s lockout: still locked at 29 s");
		GearRules.setLastProtection(p.getUUID(), now - 31 * 20);
		check(!GearRules.lockedOut(p), "30 s lockout: free at 31 s");
		check(GearRules.LOCKOUT_TICKS == 600, "lockout is 600 ticks");
		check(MotherCreak.HEART_MINING_SPEED == 0.575, "Mother Creak: Resin Hearts mine at 57.5% speed");
	}

	private static void trips(ServerLevel overworld, ServerLevel hollow) {
		FakePlayer p = fake(overworld, "Newcomer");
		int ok = 0, n = 100;
		double min = 1e9, max = 0;
		for (int i = 0; i < n; i++) {
			p.snapTo(1500 + i * 37, 70, -900 + i * 11, 0F, 0F);
			var d = Anchors.firstTripDestination(p, PaleHollowDimension.LEVEL);
			if (d == null) continue;
			double sc = net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(overworld.dimensionType(), hollow.dimensionType());
			BlockPos rift = ReturnRifts.nearestRift(hollow, (int)Math.floor(p.getX() * sc), (int)Math.floor(p.getZ() * sc));
			double h = Math.sqrt(Math.pow(d.pos().x - rift.getX() - 0.5, 2) + Math.pow(d.pos().z - rift.getZ() - 0.5, 2));
			min = Math.min(min, h);
			max = Math.max(max, h);
			if (h >= 32 && h <= 128) ok++;
		}
		check(ok >= 95, "first trip lands 32-128 blocks from a rift: " + ok + "/" + n + " (min " + (int)min + ", max " + (int)max + ")");
		check("north".equals(Anchors.direction(0, -10)) && "east".equals(Anchors.direction(10, 0)) && "south_west".equals(Anchors.direction(-10, 10)), "arrival compass: 8-way directions (north = -Z)");
		var msg = Anchors.compass(new Vec3(0, 64, 0), new BlockPos(0, 64, -100));
		check(msg.getString().length() > 0 && msg.toString().contains("100"), "arrival compass names direction and distance");
		FakePlayer h = fake(hollow, "Arrived");
		h.snapTo(10, 100, 10, 0F, 0F);
		check(!Anchors.pointToRift(h), "arrival compass: silent before an arrival");
		Anchors.markArrived(h);
		check(Anchors.pointToRift(h), "arrival compass: points to the nearest rift right after arrival");
	}

	private static void anchors(ServerLevel hollow) {
		FakePlayer p = fake(hollow, "Anchorer");
		hollow.getChunk(2, 2, ChunkStatus.FULL, true);
		hollow.getChunk(2, 2, ChunkStatus.FULL, true);
		BlockPos pos = new BlockPos(40, hollow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40), 40);
		hollow.setBlockAndUpdate(pos, ModBlocks.RIFT_ANCHOR.defaultBlockState());
		p.snapTo(40, pos.getY() + 2, 40, 0F, 0F);
		Anchors.placeAnchor(p, pos);
		check(pos.equals(Anchors.anchorFor(p, hollow)), "Rift Anchor: recorded and found");
		var d = Anchors.anchorDestination(p, PaleHollowDimension.LEVEL);
		boolean beside = d != null && d.pos().distanceTo(Vec3.atBottomCenterOf(pos)) < 12 && SafeLanding.isSafe(hollow, BlockPos.containing(d.pos()));
		check(beside, "Rift Anchor: arrival lands beside the anchor");
		if (!beside) {
			System.out.println("DIAG Rift Anchor: anchor=" + pos + " arrival=" + (d == null ? "null" : d.pos() + " offset=" + d.pos().subtract(Vec3.atBottomCenterOf(pos))
				+ " dist=" + d.pos().distanceTo(Vec3.atBottomCenterOf(pos)) + " safe=" + SafeLanding.isSafe(hollow, BlockPos.containing(d.pos()))));
		}
		hollow.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		check(Anchors.anchorFor(p, hollow) == null, "Rift Anchor: broken anchor is forgotten");
		hollow.getChunk(6, 6, ChunkStatus.FULL, true);
		BlockPos at = new BlockPos(100, hollow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 100, 100), 100);
		BlockPos w = Anchors.placeWaystone(hollow, at);
		check(w != null && hollow.getBlockState(w).is(ModBlocks.BOSS_WAYSTONE), "Boss Waystone is placed");
		check(Anchors.waystoneDestination(p, PaleHollowDimension.LEVEL) == null, "Waystone: nothing remembered before attuning");
		if (w != null) {
			Anchors.rememberWaystone(p, PaleHollowDimension.LEVEL, w, false);
			var wd = Anchors.waystoneDestination(p, PaleHollowDimension.LEVEL);
			check(wd != null && wd.pos().distanceTo(Vec3.atBottomCenterOf(w)) < 14, "Waystone: destination is next to the stone");
			check(Anchors.warpToWaystone(p, PaleHollowDimension.LEVEL), "Waystone: sneak-use warp opens a rift");
		}
		check(Anchors.FIRST_TRIP_MIN == 32 && Anchors.FIRST_TRIP_MAX == 128, "first-trip window constants 32-128");
	}

	private static void trophies(ServerLevel hollow) {
		FakePlayer p = fake(hollow, "Hunter");
		check(Trophies.BOSSES.size() == 24, "24 bosses have trophies: " + Trophies.BOSSES.size());
		String id = "selenne";
		check(Trophies.BOSSES.containsKey(id), "Selenne is a trophy boss");
		check(Trophies.credit(p, id), "first kill credits a trophy");
		check(!Trophies.credit(p, id), "a repeat kill gives no second trophy");
		boolean held = p.getInventory().countItem(ModBlocks.BOSS_TROPHY.asItem()) >= 1
			|| !hollow.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8), e -> e.getItem().is(ModBlocks.BOSS_TROPHY.asItem())).isEmpty();
		check(held, "the Selenne trophy dropped or was handed over");
		check(id.equals(Trophies.bossOf(Trophies.stack(id))) && Trophies.killedCount(p) == 1, "trophy item remembers its boss; kill count 1");
		var payload = Atlas.open(p);
		check(payload.bosses().contains(id), "the Atlas lists the beaten boss");
		Atlas.discover(p, "companion:hollow_stag");
		check(Atlas.snapshot(p).found().contains("companion:hollow_stag"), "the Atlas records discoveries");
		check(hollow.getServer().getAdvancements().get(Elsewhere.id("boss/selenne")) != null, "boss advancement loads");
	}

	private static void fishing(MinecraftServer server, ServerLevel hollow) {
		check(V2Content.CATCHES.size() == 7, "7 per-dimension fishing tables");
		var table = server.reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_FISH);
		for (var e : V2Content.CATCHES.entrySet()) {
			ServerLevel lvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, Elsewhere.id(e.getKey())));
			if (lvl == null) {
				check(true, "fishing: " + e.getKey() + " dimension not in this build (its module is absent): skipped cleanly");
				continue;
			}
			LootParams params = new LootParams.Builder(lvl).withParameter(LootContextParams.ORIGIN, new Vec3(0, 64, 0)).withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).create(LootContextParamSets.FISHING);
			Set<Item> seen = new HashSet<>();
			for (int i = 0; i < 400; i++) table.getRandomItems(params).forEach(s -> seen.add(s.getItem()));
			check(seen.contains(e.getValue().fish()) && !seen.contains(Items.PUFFERFISH), "fishing in " + e.getKey() + " yields " + BuiltInRegistries.ITEM.getKey(e.getValue().fish()).getPath() + " and no vanilla-only fish");
		}
	}

	private static void bounties(MinecraftServer server, ServerLevel hollow) {
		check(Bounties.BOARDS.size() >= 1, "bounty boards load: " + Bounties.BOARDS.size());
		boolean capped = true;
		for (var b : Bounties.BOARDS.values()) for (var y : b.bounties()) {
			capped &= y.pay() <= Bounties.MAX_PAY && y.xp() <= Bounties.MAX_XP && y.pay() > 0;
		}
		check(capped, "bounty rewards are currency and XP only, within the caps");
		FakePlayer p = fake(hollow, "Bounty");
		for (var b : Bounties.BOARDS.values()) {
			var today = Bounties.today(server, b);
			for (int i = 0; i < today.size(); i++) {
				var y = today.get(i);
				if (!"deliver".equals(y.type())) continue;
				Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(y.target()));
				p.getInventory().add(new ItemStack(item, y.count()));
				int before = p.totalExperience;
				boolean paid = Bounties.claim(p, b.trader(), i);
				check(paid && p.getInventory().countItem(Bounties.currencyItem(b)) >= y.pay() && p.totalExperience >= before + y.xp() - 1, "bounty claim pays " + y.pay() + " " + b.currency() + " and " + y.xp() + " XP (" + b.trader() + ")");
				check(!Bounties.claim(p, b.trader(), i), "a bounty can be claimed once a day");
				return;
			}
		}
		check(false, "some board offers a deliver bounty today");
	}

	private static void companions(ServerLevel hollow) {
		check(CompanionEntity.types().size() == 3, "3 companion types");
		FakePlayer p = fake(hollow, "Tamer");
		int n = 0;
		for (var t : CompanionEntity.types()) {
			var c = t.create(hollow, EntitySpawnReason.COMMAND);
			if (c != null) {
				c.snapTo(0.5, 120, 0.5, 0F, 0F);
				if (hollow.addFreshEntity(c)) n++;
				c.tame(p);
				check(c.isTame() && c.isOwnedBy(p), BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath() + " can be tamed");
				c.discard();
			}
		}
		check(n == 3, "all 3 companions spawn");
	}

	private static void sites(MinecraftServer server, ServerLevel hollow) {
		var reg = hollow.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var tableChapel = server.reloadableRegistries().getLootTable(PHSites.loot("drowned_chapel"));
		boolean mantle = false;
		LootParams lp = new LootParams.Builder(hollow).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		for (int i = 0; i < 5; i++) mantle |= tableChapel.getRandomItems(lp).stream().anyMatch(s -> s.is(ModItems.PALEWEAVE_MANTLE));
		check(mantle, "the Drowned Chapel chest always holds a Paleweave Mantle");
		int rifts = 0;
		for (int i = 0; i < 300; i++) if (PHSites.roadHasRift(new BlockPos(i * 16, 0, (i * 5 + 1) * 16))) rifts++;
		check(rifts > 70 && rifts < 130, "about every third Lantern Road has a Return Rift: " + rifts + "/300");
		int seed = 40000 + new java.util.Random().nextInt(300000);
		for (String name : new String[] {"hollow_shrine", "drowned_chapel", "lantern_road"}) {
			var holder = reg.get(ResourceKey.create(Registries.STRUCTURE, Elsewhere.id(name)));
			check(holder.isPresent(), name + " structure is registered from data");
			if (holder.isEmpty()) continue;
			var found = hollow.getChunkSource().getGenerator().findNearestMapStructure(hollow, net.minecraft.core.HolderSet.direct(holder.get()), new BlockPos(seed, 64, seed), 100, false);
			check(found != null, name + " is found by the structure search from a fresh area");
			if (found == null) continue;
			BlockPos p = found.getFirst();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) hollow.getChunk((p.getX() >> 4) + dx, (p.getZ() >> 4) + dz, ChunkStatus.FULL, true);
			var start = hollow.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, true).getStartForStructure(holder.get().value());
			check(start != null && start.isValid(), name + " generates its structure start");
			if (start != null && start.getPieces().get(0) instanceof dev.elsewhere.palehollow.fit3.FitPiece fit) {
				// 3.0 fit starts (core_fit): the same promises, checked at the new layouts' own spots (CoreFitSelfTest measures the fit)
				switch (name) {
					case "hollow_shrine" -> check(hollow.getBlockState(dev.elsewhere.palehollow.fit3.ShrineKit.chest(fit)).is(Blocks.CHEST), "Hollow Shrine has its chest on the altar, origin " + fit.origin.toShortString());
					case "drowned_chapel" -> {
						var east = (dev.elsewhere.palehollow.fit3.FitPiece) start.getPieces().get(1);
						boolean altar = hollow.getBlockState(dev.elsewhere.palehollow.fit3.ChapelKit.altarChest(east)).is(Blocks.CHEST);
						int soul = (hollow.getBlockState(dev.elsewhere.palehollow.fit3.ChapelKit.lantern(east, -1)).is(Blocks.SOUL_LANTERN) ? 1 : 0) + (hollow.getBlockState(dev.elsewhere.palehollow.fit3.ChapelKit.lantern(east, 1)).is(Blocks.SOUL_LANTERN) ? 1 : 0);
						check(altar && soul == 2, "Drowned Chapel has its altar chest and lanterns (origin " + fit.origin.toShortString() + ") [altar chest " + altar + ", soul lanterns " + soul + "]");
					}
					default -> {
						var bb = fit.getBoundingBox();
						for (int cx = bb.minX() >> 4; cx <= bb.maxX() >> 4; cx++) for (int cz = bb.minZ() >> 4; cz <= bb.maxZ() >> 4; cz++) hollow.getChunk(cx, cz, ChunkStatus.FULL, true);
						int lanterns = 0, rift = 0;
						for (int x = bb.minX(); x <= bb.maxX(); x++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) for (int y = bb.minY(); y <= bb.maxY(); y++) {
							BlockState s = hollow.getBlockState(new BlockPos(x, y, z));
							if (s.is(Blocks.LANTERN)) lanterns++;
							if (s.is(ModBlocks.RETURN_RIFT)) rift++;
						}
						check(lanterns >= 4, "Lantern Road has lantern posts: " + lanterns);
						check(PHSites.roadHasRift(fit.origin) == (rift > 0), "Lantern Road Return Rift follows the every-third rule (rift blocks " + rift + ")");
						LanternEvent.begin(hollow, fit.origin.getX(), fit.origin.getZ(), null);
						for (int i = 0; i < 1400 && LanternEvent.active() > 0; i++) LanternEvent.tickWalks();
						BlockPos c = LanternEvent.chestOf(hollow, fit.origin.getX(), fit.origin.getZ());
						check(LanternEvent.active() == 0 && hollow.getBlockState(c).is(Blocks.CHEST), "Lantern Procession: spirits walk the contour road, then leave a resin chest at its end " + c.toShortString());
					}
				}
				continue;
			}
			if (start == null || !(start.getPieces().get(0) instanceof PHSites.Piece piece)) continue;
			int chests = 0, lanterns = 0, rift = 0, mantleChest = 0;
			for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) for (int y = -8; y <= 8; y++) {
				BlockPos q = piece.origin.offset(x, y, z);
				BlockState s = hollow.getBlockState(q);
				if (s.is(Blocks.CHEST)) {
					chests++;
				}
				if (s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) lanterns++;
				if (s.is(ModBlocks.RETURN_RIFT)) rift++;
			}
			switch (name) {
				case "hollow_shrine" -> check(chests >= 1, "Hollow Shrine has its chest (" + chests + ") origin " + piece.origin.toShortString() + " centre " + hollow.getBlockState(piece.origin) + " above " + hollow.getBlockState(piece.origin.above())
					+ " ground y " + hollow.getHeight(Heightmap.Types.WORLD_SURFACE, piece.origin.getX(), piece.origin.getZ()) + " search seed " + seed);
				case "drowned_chapel" -> {
					// the chapel's own altar chest and soul lanterns sit at fixed offsets; a neighbouring site in the +-15 scan box must not count
					BlockPos o = piece.origin;
					boolean altar = hollow.getBlockState(o.offset(0, -1, -7)).is(Blocks.CHEST);
					int soul = (hollow.getBlockState(o.offset(-2, -1, -7)).is(Blocks.SOUL_LANTERN) ? 1 : 0) + (hollow.getBlockState(o.offset(2, -1, -7)).is(Blocks.SOUL_LANTERN) ? 1 : 0);
					check(altar && soul == 2, String.format("Drowned Chapel has its altar chest and lanterns (scan box: chests %d, lanterns %d, origin %s, search seed %d)", chests, lanterns, piece.origin.toShortString(), seed) + " [altar chest " + altar + ", soul lanterns " + soul + "]");
				}
				default -> {
					check(lanterns >= 6, "Lantern Road has lantern posts: " + lanterns);
					check(PHSites.roadHasRift(piece.origin) == (rift > 0), "Lantern Road Return Rift follows the every-third rule (rift blocks " + rift + ")");
					int baseX = piece.origin.getX(), baseZ = piece.origin.getZ();
					for (int i = 0; i < 6; i++) hollow.getChunk((baseX >> 4) + 1, baseZ >> 4, ChunkStatus.FULL, true);
					LanternEvent.begin(hollow, baseX, baseZ, null);
					for (int i = 0; i < 700; i++) LanternEvent.tickWalks();
					BlockPos c = LanternEvent.chestOf(hollow, baseX, baseZ);
					check(LanternEvent.active() == 0 && hollow.getBlockState(c).is(Blocks.CHEST), "Lantern Procession: spirits walk the road, then leave a resin chest at its end");
				}
			}
		}
	}
}
