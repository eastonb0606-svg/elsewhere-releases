package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_villages.FactionVillages.Profile;
import net.minecraft.resources.Identifier;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Quiet expansion (slow): at most {@value #MAX_OUTPOSTS} outposts per world, one per {@value #OUTPOST_DAYS} in-game days,
 * built only in never-visited chunks 300-800 blocks from Aldermark territory, never on player chunks or structures.
 * Royal roads (gravel and cobble, with milestones) grow slowly from outposts toward the castle, and simple dirt paths from
 * newly occupied villages; both are built a block at a time while a player is near the road head. Rarely (1 in 4) an
 * outpost has a burned hamlet nearby with 1-2 refugees.
 */
public final class Conquest {
	private Conquest() {}

	public static final int MAX_OUTPOSTS = 10, OUTPOST_DAYS = 10, ROAD_MAX = 200, MILESTONE_EVERY = 100;
	public static final long FRESH_TICKS = 1200L, ROAD_INHABITED_LIMIT = 72000L;
	public static boolean testFastRoads;
	public static String hamletDbg = "";
	public static final int[] roadDbg = new int[6];

	static void tick(ServerLevel level, VillageLedger ledger, List<Vec3> obs, long now) {
		if (now % 20 == 0) {
			for (VillageRecord s : ledger.sites.values()) {
				if (s.kind == VillageRecord.Kind.OUTPOST) outpostTick(level, s, obs);
			}
		}
		if (now % (testFastRoads ? 1 : 40) == 0) {
			for (VillageRecord s : ledger.sites.values()) if (s.kind == VillageRecord.Kind.ROAD && s.roadStep < s.roadLength) roadStep(level, s, obs);
		}
		if (now % 600 == 0 && ledger.conquestOn) {
			for (Vec3 o : obs) {
				RealmLocator.Realm realm = RealmLocator.findRealm(level, BlockPos.containing(o));
				if (realm == null || !mayFound(ledger, now, realm.faction())) continue;
				tryFoundOutpost(level, ledger, BlockPos.containing(o), level.getRandom(), now);
			}
		}
	}

	/** At most 10 outposts per world, at most one per 10 in-game days, never while conquest is off. */
	public static boolean mayFound(VillageLedger ledger, long now) {
		return mayFound(ledger, now, Factions.ALDERMARK);
	}

	/** Per kingdom: at most 10 outposts, one per 10 in-game days (Aldermark's counters are the 2.0 fields). */
	public static boolean mayFound(VillageLedger ledger, long now, Identifier faction) {
		if (!ledger.conquestOn) return false;
		if (faction.equals(Factions.ALDERMARK)) return ledger.outpostsFounded < MAX_OUTPOSTS && now - ledger.lastOutpostTime >= OUTPOST_DAYS * VillageManager.DAY;
		String k = faction.toString();
		return ledger.outpostsBy.getOrDefault(k, 0) < MAX_OUTPOSTS && now - ledger.lastOutpostBy.getOrDefault(k, Long.MIN_VALUE / 2) >= OUTPOST_DAYS * VillageManager.DAY;
	}

	// ---- outposts -----------------------------------------------------------------------------------------------------

	/** Nearest Aldermark place (castle, outposts, occupied villages) to {@code p}, or -1 when no realm is known. */
	static double territoryDist(ServerLevel level, VillageLedger ledger, BlockPos p, BlockPos castle) {
		return territoryDist(level, ledger, p, castle, Factions.ALDERMARK.toString());
	}

	static double territoryDist(ServerLevel level, VillageLedger ledger, BlockPos p, BlockPos castle, String faction) {
		double best = RealmLocator.horizontal(castle, p);
		for (VillageRecord s : ledger.sites.values()) if (s.kind == VillageRecord.Kind.OUTPOST && s.owner.equals(faction)) best = Math.min(best, RealmLocator.horizontal(s.center, p));
		for (VillageRecord v : ledger.villages.values()) if (v.isOccupiedLike() && v.owner.equals(faction)) best = Math.min(best, RealmLocator.horizontal(v.center, p));
		return best;
	}

	/** True if the chunk at {@code p} (and its neighbours) is loaded, never visited, unmarked and holds no structure. */
	static boolean freshChunk(ServerLevel level, VillageLedger ledger, BlockPos p) {
		int cx = p.getX() >> 4, cz = p.getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			LevelChunk c = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
			if (c == null || c.getInhabitedTime() > FRESH_TICKS) return false;
			if (ledger.playerChunks.contains(ChunkPos.pack(cx + dx, cz + dz))) return false;
			if (!c.getAllReferences().values().stream().allMatch(s -> s.isEmpty())) return false;
		}
		for (VillageRecord v : ledger.villages.values()) if (v.bounds.inflatedBy(48).isInside(p.getX(), v.bounds.minY(), p.getZ())) return false;
		for (VillageRecord s : ledger.sites.values()) if (s.kind != VillageRecord.Kind.ROAD && RealmLocator.horizontal(s.center, p) < 64) return false;
		return true;
	}

	public static boolean freshChunkPublic(ServerLevel level, BlockPos p) {
		return freshChunk(level, VillageLedger.get(level), p);
	}

	/** Flat open natural ground: a {@code r}-radius square within 1 block of the centre height, no water, no trees. */
	static @Nullable BlockPos flatSpot(ServerLevel level, BlockPos p, int r) {
		BlockPos c = Plans.surface(level, p);
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
			int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX() + dx, c.getZ() + dz);
			if (Math.abs(h - c.getY()) > 1) return null;
			BlockState g = level.getBlockState(new BlockPos(c.getX() + dx, h - 1, c.getZ() + dz));
			if (!(g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT) || g.is(Blocks.SAND) || g.is(Blocks.SNOW_BLOCK) || g.is(Blocks.STONE) || g.is(Blocks.GRAVEL))) return null;
		}
		return c;
	}

	public static @Nullable VillageRecord tryFoundOutpost(ServerLevel level, VillageLedger ledger, BlockPos from, RandomSource rnd, long now) {
		RealmLocator.Realm realm = RealmLocator.findRealm(level, from);
		if (realm == null || !mayFound(ledger, now, realm.faction())) return null;
		BlockPos castle = realm.castle();
		for (int tries = 0; tries < 6; tries++) {
			double ang = rnd.nextDouble() * Math.PI * 2, d = 48 + rnd.nextInt(49);
			BlockPos p = from.offset((int) (Math.cos(ang) * d), 0, (int) (Math.sin(ang) * d));
			double td = territoryDist(level, ledger, p, castle, realm.faction().toString());
			if (td < 300 || td > 800) continue;
			if (!freshChunk(level, ledger, p)) continue;
			BlockPos c = FactionVillages.of(realm.faction()).isAldermark() && dev.elsewhere.kingdom_villages.s3.ald.AldLook.FORT ? dev.elsewhere.kingdom_villages.s3.ald.AldFort.site(level, p, castle) : flatSpot(level, p, 6); // s-vlook-ald
			if (c == null) continue;
			return foundOutpost(level, ledger, c, castle, realm.faction(), rnd, now);
		}
		return null;
	}

	public static VillageRecord foundOutpost(ServerLevel level, VillageLedger ledger, BlockPos c, BlockPos castle, RandomSource rnd, long now) {
		return foundOutpost(level, ledger, c, castle, Factions.ALDERMARK, rnd, now);
	}

	public static VillageRecord foundOutpost(ServerLevel level, VillageLedger ledger, BlockPos c, BlockPos castle, Identifier faction, RandomSource rnd, long now) {
		Profile prof = FactionVillages.of(faction);
		dev.elsewhere.kingdom_villages.s3.ald.AldFort.Plan fort = prof.isAldermark() && dev.elsewhere.kingdom_villages.s3.ald.AldLook.FORT ? dev.elsewhere.kingdom_villages.s3.ald.AldFort.plan(level, c, castle) : null; // s-vlook-ald: motte-and-bailey
		VillageRecord s = newSite(ledger, VillageRecord.Kind.OUTPOST, fort != null ? fort.yard() : c, fort != null ? fort.box() : new BoundingBox(c.getX() - 6, c.getY() - 1, c.getZ() - 6, c.getX() + 6, c.getY() + 8, c.getZ() + 6));
		s.castle = castle;
		s.owner = faction.toString();
		s.state = VillageRecord.State.OCCUPIED;
		s.builtAt = now;
		Direction gate = Direction.getApproximateNearest((double) (castle.getX() - c.getX()), 0.0, (double) (castle.getZ() - c.getZ()));
		for (VillageRecord.Op op : fort != null ? fort.ops() : prof.isAldermark() ? Plans.outpost(c, gate) : RivalPlans.outpost(level, prof, c, gate)) BlockWriter.write(level, s, BlockPos.of(op.pos()), op.state());
		s.hint = fort != null ? fort.flag() : prof.isAldermark() ? c.offset(-1, 6, -1) : RivalPlans.hintPos(level, prof, c); // s-asm-vill: legacy outposts sit on their own ground
		if (faction.equals(Factions.ALDERMARK)) {
			ledger.outpostsFounded++;
			ledger.lastOutpostTime = now;
		} else {
			ledger.outpostsBy.merge(faction.toString(), 1, Integer::sum);
			ledger.lastOutpostBy.put(faction.toString(), now);
		}
		ledger.dirty();
		// royal road from the gate toward the castle
		newRoad(level, ledger, fort != null ? fort.roadStart() : c.relative(gate, 6), castle, true);
		if (rnd.nextInt(4) == 0) tryHamlet(level, ledger, c, castle, rnd);
		return s;
	}

	static VillageRecord newSite(VillageLedger ledger, VillageRecord.Kind kind, BlockPos c, BoundingBox box) {
		VillageRecord s = new VillageRecord();
		s.id = ledger.nextSiteId++;
		s.kind = kind;
		s.center = c;
		s.bounds = box;
		s.pieces.add(new VillageRecord.Piece(box, VillageRecord.OTHER));
		ledger.sites.put(s.id, s);
		ledger.dirty();
		return s;
	}

	static void outpostTick(ServerLevel level, VillageRecord s, List<Vec3> obs) {
		if (s.state != VillageRecord.State.OCCUPIED || s.forceSpawnedOnce) return;
		boolean near = false;
		for (Vec3 o : obs) if (o.distanceToSqr(s.center.getX() + 0.5, o.y, s.center.getZ() + 0.5) < VillageManager.NEAR * VillageManager.NEAR) near = true;
		if (!near || !level.isLoaded(s.center)) return;
		List<KingdomSoldier> g = KingdomApi.spawnSquad(level, s.center.offset(2, 0, -2), VillageManager.factionOf(s), Squad.Formation.COLUMN, UnitTypes.SERGEANT,
			List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
		if (!g.isEmpty() && g.get(0).squad() != null) {
			s.squads.add(g.get(0).squad().id());
			g.get(0).squad().setObjective(s.center);
		}
		s.forceSpawned = g.size();
		s.forceSpawnedOnce = true;
		VillageLedger.get(level).dirty();
	}

	static void onSoldierDied(ServerLevel level, VillageLedger ledger, UUID squad) {
		for (VillageRecord s : ledger.sites.values()) {
			if (!s.squads.contains(squad)) continue;
			s.forceDead++;
			if (s.forceDead >= s.forceSpawned && s.state == VillageRecord.State.OCCUPIED) {
				// captured: the Aldermark banner comes down and a white one goes up
				BlockPos b = s.center.offset(-1, 6, -1);
				if (s.hint != null && s.owner.equals(Factions.ALDERMARK.toString()) && !(level.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BannerBlock)) b = s.hint; // s-vlook-ald: the fort's flag
				if (level.isLoaded(b) && level.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BannerBlock) BlockWriter.write(level, s, b, Blocks.BANNER.white().defaultBlockState());
				s.state = VillageRecord.State.LIBERATED;
				for (ServerPlayer p : level.players())
					if (p.distanceToSqr(Vec3.atCenterOf(s.center)) < 64 * 64) p.sendSystemMessage(Component.translatable("message.elsewhere.village.outpost_captured"));
			}
			ledger.dirty();
			return;
		}
	}

	// ---- refugees -----------------------------------------------------------------------------------------------------

	static @Nullable VillageRecord tryHamlet(ServerLevel level, VillageLedger ledger, BlockPos outpost, BlockPos castle, RandomSource rnd) {
		double away = Math.atan2(outpost.getZ() - castle.getZ(), outpost.getX() - castle.getX()) + (rnd.nextDouble() - 0.5);
		double d = 30 + rnd.nextInt(21);
		BlockPos p = outpost.offset((int) (Math.cos(away) * d), 0, (int) (Math.sin(away) * d));
		if (!freshChunk(level, ledger, p) && !ledger.sites.isEmpty() && RealmLocator.horizontal(p, outpost) < 20) return null;
		BlockPos c = dev.elsewhere.kingdom_villages.s3.ald.AldLook.HAMLET ? dev.elsewhere.kingdom_villages.s3.ald.AldHamlet.site(level, p) : flatSpot(level, p, 4); // s-vlook-ald
		if (c == null) return null;
		return buildHamlet(level, ledger, c, castle, rnd);
	}

	public static VillageRecord buildHamlet(ServerLevel level, VillageLedger ledger, BlockPos c, BlockPos castle, RandomSource rnd) {
		dev.elsewhere.kingdom_villages.s3.ald.AldHamlet.Plan hp = dev.elsewhere.kingdom_villages.s3.ald.AldLook.HAMLET ? dev.elsewhere.kingdom_villages.s3.ald.AldHamlet.plan(level, c, rnd) : null; // s-vlook-ald: burnt farmstead per column
		if (hp != null) c = hp.refuge();
		VillageRecord s = newSite(ledger, VillageRecord.Kind.HAMLET, c, hp != null ? hp.box() : new BoundingBox(c.getX() - 5, c.getY() - 1, c.getZ() - 5, c.getX() + 5, c.getY() + 4, c.getZ() + 5));
		s.state = VillageRecord.State.FREE;
		for (VillageRecord.Op op : hp != null ? hp.ops() : Plans.hamlet(c, rnd)) BlockWriter.write(level, s, BlockPos.of(op.pos()), op.state());
		String dir = direction(c, castle);
		int n = 1 + rnd.nextInt(2);
		for (int i = 0; i < n; i++) {
			Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
			if (v == null) continue;
			v.snapTo(c.getX() + 0.5 + i, c.getY(), c.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
			v.setPersistenceRequired();
			// subtle: the name only shows when looked at closely, and as the trade screen title
			v.setCustomName(Component.translatable("entity.elsewhere.village.refugee", Component.translatable("direction.elsewhere." + dir)));
			v.setCustomNameVisible(false);
			boolean added = level.addFreshEntity(v);
			hamletDbg += " add=" + added + " at " + v.blockPosition().toShortString() + " loaded=" + level.isLoaded(v.blockPosition());
		}
		return s;
	}

	static String direction(BlockPos from, BlockPos to) {
		double a = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
		String[] names = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
		int i = (int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8;
		return names[i];
	}

	// ---- roads --------------------------------------------------------------------------------------------------------

	/** A simple dirt path from a newly occupied village toward the castle (built slowly by its Serfs). */
	static void roadFrom(ServerLevel level, VillageRecord v) {
		if (v.castle == null) return;
		VillageLedger ledger = VillageLedger.get(level);
		for (VillageRecord s : ledger.sites.values()) if (s.kind == VillageRecord.Kind.ROAD && s.roadTarget == v.castle.asLong() && s.squads.isEmpty() && s.builtAt == v.id) return;
		double ang = Math.atan2(v.castle.getZ() - v.center.getZ(), v.castle.getX() - v.center.getX());
		double rad = Math.max(v.bounds.getXSpan(), v.bounds.getZSpan()) / 2.0 + 2;
		BlockPos start = v.center.offset((int) (Math.cos(ang) * rad), 0, (int) (Math.sin(ang) * rad));
		VillageRecord r = newRoad(level, ledger, start, v.castle, false);
		r.builtAt = v.id;
	}

	static VillageRecord newRoad(ServerLevel level, VillageLedger ledger, BlockPos start, BlockPos target, boolean royal) {
		VillageRecord r = newSite(ledger, VillageRecord.Kind.ROAD, start, new BoundingBox(start));
		r.pieces.clear();
		r.roadTarget = target.asLong();
		r.roadLength = (int) Math.min(ROAD_MAX, Math.max(0, RealmLocator.horizontal(start, target) - 12));
		r.reason = royal ? "royal" : "path";
		r.state = VillageRecord.State.OCCUPIED;
		return r;
	}

	/** Advances a road by one step while a player is within 96 blocks of its head. */
	static void roadStep(ServerLevel level, VillageRecord r, List<Vec3> obs) {
		BlockPos target = BlockPos.of(r.roadTarget);
		double len = RealmLocator.horizontal(r.center, target);
		if (len < 1) {
			r.roadStep = r.roadLength;
			return;
		}
		double ux = (target.getX() - r.center.getX()) / len, uz = (target.getZ() - r.center.getZ()) / len;
		int i = r.roadStep;
		int x = r.center.getX() + (int) Math.round(ux * i), z = r.center.getZ() + (int) Math.round(uz * i);
		BlockPos head = new BlockPos(x, r.center.getY(), z);
		boolean near = false;
		for (Vec3 o : obs) if (o.distanceToSqr(head.getX(), o.y, head.getZ()) < 96 * 96) near = true;
		if (!near || !level.isLoaded(head)) { roadDbg[0]++; return; }
		r.roadStep++;
		VillageLedger ledger = VillageLedger.get(level);
		LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
		if (c == null || c.getInhabitedTime() >= ROAD_INHABITED_LIMIT || ledger.playerChunks.contains(ChunkPos.pack(x >> 4, z >> 4))) { roadDbg[1]++; return; }
		if (!c.getAllReferences().values().stream().allMatch(s -> s.isEmpty())) { roadDbg[2]++; return; } // never through structures (villages too)
		roadDbg[3]++;
		boolean royal = "royal".equals(r.reason);
		int px = (int) Math.round(-uz), pz = (int) Math.round(ux);
		for (int w = 0; w < 2; w++) pave(level, r, x + px * w, z + pz * w, royal);
		if (i > 0 && i % MILESTONE_EVERY == 0) milestone(level, r, x + px * 2, z + pz * 2, target);
		if (royal == false) for (VillageRecord v : ledger.villages.values()) if (v.id == r.builtAt) {
			List<Serf> ss = VillageManager.serfs(level, v);
			if (!ss.isEmpty()) ss.get(0).workPos = head;
		}
		ledger.dirty();
	}

	static void pave(ServerLevel level, VillageRecord r, int x, int z, boolean royal) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos g = new BlockPos(x, top - 1, z);
		BlockState gs = level.getBlockState(g), above = level.getBlockState(g.above());
		if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.COARSE_DIRT) || gs.is(Blocks.PODZOL) || gs.is(Blocks.SAND) || gs.is(Blocks.GRAVEL) || gs.is(Blocks.DIRT_PATH))) return;
		boolean soft = gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.COARSE_DIRT) || gs.is(Blocks.PODZOL);
		if (!(above.isAir() || above.is(Blocks.SHORT_GRASS) || above.is(Blocks.FERN) || above.is(Blocks.SNOW))) return;
		if (!above.isAir()) BlockWriter.write(level, r, g.above(), Blocks.AIR.defaultBlockState());
		BlockState mat = royal || !soft ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState(); // only path or gravel
		BlockWriter.write(level, r, g, mat);
	}

	static void milestone(ServerLevel level, VillageRecord r, int x, int z, BlockPos target) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos g = new BlockPos(x, top, z);
		if (!level.getBlockState(g).isAir() || !level.getBlockState(g.above()).isAir()) return;
		if (!level.getBlockState(g.below()).isFaceSturdy(level, g.below(), Direction.UP)) return;
		if (!BlockWriter.write(level, r, g, Blocks.COBBLESTONE.defaultBlockState())) return;
		int rot = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(target.getX() - x, -(target.getZ() - z))) / 22.5F), 16);
		if (BlockWriter.write(level, r, g.above(), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot))
			&& level.getBlockEntity(g.above()) instanceof SignBlockEntity sign) {
			int dist = (int) RealmLocator.horizontal(g, target);
			sign.setText(new SignText(List.of(Component.empty(), Component.translatable("sign.elsewhere.village.milestone"),
				Component.translatable("sign.elsewhere.village.milestone_dist", dist), Component.empty()),
				List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()), DyeColor.BLACK, false), SignTextSlot.FRONT);
		}
	}
}
