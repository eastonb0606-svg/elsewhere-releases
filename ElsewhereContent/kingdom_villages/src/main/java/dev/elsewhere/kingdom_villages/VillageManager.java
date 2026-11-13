package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Serfs;
import dev.elsewhere.kingdom_villages.FactionVillages.Profile;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The village state machine. Runs on the overworld only, and only does work for villages with a player (or a self-test
 * observer) within {@link #NEAR} blocks.
 *
 * <pre>
 * distance to the castle      first load
 *   <= 275                    OCCUPIED (garrison + Serfs, then FORTIFYING -> FORTIFIED)
 *   400 .. 1000 (+40/week,    35% SIEGE (one per player, 2 at once), else FREE; re-rolled at most every 10 days
 *   capped 1400)
 *   otherwise                 FREE
 * any player trade, player block, player-built block or 1 h of player presence  ->  PROTECTED forever
 * SIEGE: 11 soldiers burn the village (1 block/s, siege fire never spreads) for 2 in-game days, then it falls (OCCUPIED).
 * Kill the siege force or the garrison: LIBERATED (Hero of the Village, 10% discount, sealed Coffer opens: Royal Steward).
 * </pre>
 */
public final class VillageManager {
	private VillageManager() {}

	public static final int OCCUPIED_RADIUS = 275, SIEGE_MIN = 400, SIEGE_MAX = 1000, SIEGE_MAX_CAP = 1400, CREEP_PER_WEEK = 40;
	public static final long DAY = 24000L, WEEK = DAY * 7, EVAL_INTERVAL = DAY * 10, SIEGE_LENGTH = DAY * 2;
	public static final double SIEGE_CHANCE = 0.35;
	public static final int NEAR = 64, MAX_ACTIVE_SIEGES = 2, OP_INTERVAL = 20;
	public static final long PRESENCE_TICKS = 72000L;

	/** Self-test observers (count as players for the "a player within 64 blocks" rule). */
	public static final List<Vec3> testObservers = new ArrayList<>();
	/** Self-test: run block ops every tick instead of every second. */
	public static int opInterval = OP_INTERVAL;
	/** Self-test: block ops per run (normal 1 = 1 block per second). */
	public static int opsPerRun = 1;
	/** Siege length in ticks; the QA {@code /aldermark rush} shortens it so a siege falls within a screenshot script. */
	public static long siegeLength = SIEGE_LENGTH;
	/** Self-test: siege chance override (-1 = normal). */
	public static double siegeChanceOverride = -1;

	private static final Map<UUID, Integer> patrolIndex = new HashMap<>();
	private static final Map<String, Long> warned = new HashMap<>();
	public static long nanosSpent, ticksRun;

	public static void reset() {
		WarBridge.reset();
		testObservers.clear();
		patrolIndex.clear();
		warned.clear();
		RealmLocator.reset();
		opInterval = OP_INTERVAL;
		opsPerRun = 1;
		siegeLength = SIEGE_LENGTH;
		siegeChanceOverride = -1;
	}

	public enum Band { OCCUPIED, GAP, SIEGE, FREE }

	public static int siegeOuter(long gameTime) {
		return (int) Math.min(SIEGE_MAX_CAP, SIEGE_MAX + CREEP_PER_WEEK * (gameTime / WEEK));
	}

	public static Band band(double dist, long gameTime) {
		if (dist < 0) return Band.FREE;
		if (dist <= OCCUPIED_RADIUS) return Band.OCCUPIED;
		if (dist < SIEGE_MIN) return Band.GAP;
		if (dist <= siegeOuter(gameTime)) return Band.SIEGE;
		return Band.FREE;
	}

	// ---- tick -----------------------------------------------------------------------------------------------------------

	public static void tickLevel(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) return;
		long t0 = System.nanoTime();
		long now = level.getGameTime();
		List<Vec3> obs = observers(level);
		if (obs.isEmpty()) return;
		VillageLedger ledger = VillageLedger.get(level);
		if (now % 40 == 0) for (Vec3 o : obs) scan(level, BlockPos.containing(o), observerPlayer(level, o));
		for (VillageRecord r : new ArrayList<>(ledger.villages.values())) {
			Vec3 near = nearest(r, obs);
			if (near != null) process(level, r, now, near);
		}
		Conquest.tick(level, ledger, obs, now);
		if (now % 40 == 20) Hints.tick(level, ledger);
		if (now % 1200 == 600) WarBridge.ensure(level.getServer(), ledger);
		nanosSpent += System.nanoTime() - t0;
		ticksRun++;
	}

	static List<Vec3> observers(ServerLevel level) {
		List<Vec3> obs = new ArrayList<>(testObservers);
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) obs.add(p.position());
		return obs;
	}

	static @Nullable UUID observerPlayer(ServerLevel level, Vec3 o) {
		for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(o) < 1) return p.getUUID();
		return null;
	}

	/** Nearest observer within NEAR blocks of the village's bounds, or null. */
	static @Nullable Vec3 nearest(VillageRecord r, List<Vec3> obs) {
		BoundingBox b = r.bounds;
		for (Vec3 o : obs) {
			double dx = Math.max(0, Math.max(b.minX() - o.x, o.x - b.maxX() - 1));
			double dz = Math.max(0, Math.max(b.minZ() - o.z, o.z - b.maxZ() - 1));
			if (dx * dx + dz * dz <= NEAR * NEAR) return o;
		}
		return null;
	}

	// ---- discovery ------------------------------------------------------------------------------------------------------

	/** Looks for village structure starts referenced by loaded chunks around {@code pos} (4-chunk radius). */
	static void scan(ServerLevel level, BlockPos pos, @Nullable UUID player) {
		VillageLedger ledger = VillageLedger.get(level);
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			LevelChunk c = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
			if (c == null) continue;
			for (Map.Entry<Structure, LongSet> e : c.getAllReferences().entrySet()) {
				if (e.getValue().isEmpty()) continue;
				Holder<Structure> h = structures.wrapAsHolder(e.getKey());
				if (!h.is(StructureTags.VILLAGE)) continue;
				for (long startChunk : e.getValue()) {
					if (ledger.villages.containsKey(startChunk)) continue;
					LevelChunk sc = level.getChunkSource().getChunkNow(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk));
					if (sc == null) continue;
					StructureStart start = sc.getStartForStructure(e.getKey());
					if (start != null && start.isValid()) discover(level, start, player);
				}
			}
		}
	}

	public static @Nullable VillageRecord discover(ServerLevel level, StructureStart start, @Nullable UUID player) {
		VillageLedger ledger = VillageLedger.get(level);
		long id = start.getChunkPos().pack();
		VillageRecord existing = ledger.villages.get(id);
		if (existing != null) return existing;
		VillageRecord r = new VillageRecord();
		r.id = id;
		r.bounds = start.getBoundingBox();
		BlockPos center = null;
		for (StructurePiece p : start.getPieces()) {
			int type = VillageRecord.OTHER;
			if (p instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement se) {
				String path = se.getTemplateLocation().getPath();
				if (path.contains("/houses/")) type = VillageRecord.HOUSE;
				else if (path.contains("/streets/")) type = VillageRecord.STREET;
				else if (path.contains("town_centers")) type = VillageRecord.CENTER;
			}
			BoundingBox b = p.getBoundingBox();
			if (b.getXSpan() <= 0 || b.getZSpan() <= 0) continue;
			r.pieces.add(new VillageRecord.Piece(b, type));
			if (type == VillageRecord.CENTER && center == null) center = b.getCenter();
		}
		if (r.pieces.isEmpty()) return null;
		// all piece chunks must be loaded before we judge the village
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			if (!level.isLoaded(new BlockPos(b.minX(), b.minY(), b.minZ())) || !level.isLoaded(new BlockPos(b.maxX(), b.minY(), b.maxZ()))) return null;
		}
		r.center = center != null ? center : r.bounds.getCenter();
		long now = level.getGameTime();
		r.since = now;
		RealmLocator.Realm realm = RealmLocator.findRealm(level, r.center);
		r.castle = realm == null ? null : realm.castle();
		r.owner = realm == null ? Factions.ALDERMARK.toString() : realm.faction().toString();
		r.castleDist = r.castle == null ? -1 : RealmLocator.horizontal(r.castle, r.center);
		ledger.villages.put(id, r);
		ledger.dirty();
		String why = assessProtection(level, r, true);
		if (why != null) {
			setState(level, r, VillageRecord.State.PROTECTED);
			r.reason = why;
			WarBridge.register(level.getServer(), r, null, false, true);
			return r;
		}
		evaluate(level, r, now, player);
		WarBridge.register(level.getServer(), r, warOwner(r), true, false);
		return r;
	}

	/** First-load / periodic decision for a FREE village. */
	static void evaluate(ServerLevel level, VillageRecord r, long now, @Nullable UUID player) {
		r.nextEval = now + EVAL_INTERVAL;
		VillageLedger ledger = VillageLedger.get(level);
		if (r.state == VillageRecord.State.FREE) {
			RealmLocator.Realm realm = RealmLocator.findRealm(level, r.center);
			if (realm != null) {
				r.castle = realm.castle();
				r.owner = realm.faction().toString();
				r.castleDist = RealmLocator.horizontal(r.castle, r.center);
			}
		}
		if (!ledger.conquestOn || r.castle == null) return;
		Band b = band(r.castleDist, now);
		if (b == Band.OCCUPIED) {
			setState(level, r, VillageRecord.State.OCCUPIED);
		} else if (b == Band.SIEGE) {
			double chance = siegeChanceOverride >= 0 ? siegeChanceOverride : SIEGE_CHANCE;
			if (level.getRandom().nextDouble() < chance) startSiege(level, r, player, false);
		}
		ledger.dirty();
	}

	/**
	 * Returns why the village must never be touched, or null. {@code full}: also scans villagers (trade history), the
	 * player-presence clock and the piece boxes for player-only blocks.
	 */
	public static @Nullable String assessProtection(ServerLevel level, VillageRecord r, boolean full) {
		VillageLedger ledger = VillageLedger.get(level);
		BoundingBox area = r.bounds.inflatedBy(8);
		for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
			long k = ChunkPos.pack(cx, cz);
			if (ledger.playerChunks.contains(k)) return "player block";
			if (ledger.tradeChunks.contains(k)) return "player trade";
		}
		// vanilla InhabitedTime: any chunk of the footprint with 1 h of players nearby = lived in (covers worlds played before the install)
		for (int cx = r.bounds.minX() >> 4; cx <= r.bounds.maxX() >> 4; cx++) for (int cz = r.bounds.minZ() >> 4; cz <= r.bounds.maxZ() >> 4; cz++) {
			LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
			if (c != null && c.getInhabitedTime() >= PRESENCE_TICKS) return "player presence";
		}
		if (!full) return null;
		AABB box = AABB.of(r.bounds.inflatedBy(16));
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, e -> true)) {
			if (v.getVillagerXp() > 0 || v.getVillagerData().level() > 1) return "villager trade history";
			for (MerchantOffer o : v.getOffers()) if (o.getUses() > 0) return "villager trade history";
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (VillageRecord.Piece p : r.pieces) {
			BoundingBox b = p.box();
			for (int y = b.minY(); y <= b.maxY(); y++) for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (BlockWriter.isPlayerBlock(level.getBlockState(m.set(x, y, z)))) return "player-built block";
			}
		}
		return null;
	}

	public static void protectAt(ServerLevel level, BlockPos pos, String why) {
		VillageLedger ledger = VillageLedger.get(level);
		for (VillageRecord r : ledger.villages.values()) {
			if (!r.bounds.inflatedBy(8).isInside(pos.getX(), r.bounds.minY(), pos.getZ())) continue;
			protect(level, r, why);
		}
		for (VillageRecord r : ledger.sites.values()) {
			if (r.kind == VillageRecord.Kind.ROAD) continue;
			if (!r.bounds.inflatedBy(4).isInside(pos.getX(), r.bounds.minY(), pos.getZ())) continue;
			protect(level, r, why);
		}
	}

	static void protect(ServerLevel level, VillageRecord r, String why) {
		if (r.state == VillageRecord.State.PROTECTED || r.state == VillageRecord.State.RESTORED || r.state == VillageRecord.State.LIBERATED) {
			if (r.reason.isEmpty()) r.reason = why;
			return;
		}
		BlockWriter.extinguish(level, r); // our own fire only
		r.plan.clear();
		r.planIndex = 0;
		r.planKind = 0;
		r.reason = why;
		releaseSerfs(level, r, false);
		setState(level, r, VillageRecord.State.PROTECTED);
	}

	public static void setState(ServerLevel level, VillageRecord r, VillageRecord.State s) {
		VillageRecord.State old = r.state;
		r.state = s;
		r.since = level.getGameTime();
		if (s == VillageRecord.State.OCCUPIED && old != VillageRecord.State.FORTIFYING && old != VillageRecord.State.FORTIFIED && old != s) r.heldSince = level.getGameTime();
		VillageLedger.get(level).dirty();
		if (old != s) VillageEvents.STATE_CHANGED.invoker().onStateChanged(level, r, old, s);
	}

	// ---- sieges ---------------------------------------------------------------------------------------------------------

	static int activeSieges(VillageLedger ledger, @Nullable UUID player) {
		int n = 0;
		for (VillageRecord r : ledger.villages.values()) if (r.state == VillageRecord.State.SIEGE && (player == null || player.equals(r.siegePlayer))) n++;
		return n;
	}

	/** Starts a siege if every safety rule allows it. {@code force}: ignores only the "one at a time" limits (op command). */
	public static boolean startSiege(ServerLevel level, VillageRecord r, @Nullable UUID player, boolean force) {
		VillageLedger ledger = VillageLedger.get(level);
		if (!ledger.conquestOn || r.kind != VillageRecord.Kind.VILLAGE) return false;
		if (r.state != VillageRecord.State.FREE) return false;
		String why = assessProtection(level, r, true);
		if (why != null) {
			r.reason = why;
			setState(level, r, VillageRecord.State.PROTECTED);
			return false;
		}
		if (!force && (activeSieges(ledger, null) >= MAX_ACTIVE_SIEGES || player != null && activeSieges(ledger, player) > 0)) return false;
		long now = level.getGameTime();
		r.siegePlayer = player;
		r.siegeEnd = now + siegeLength;
		r.forceSpawned = r.forceDead = 0;
		r.forceSpawnedOnce = false;
		r.squads.clear();
		r.plan.clear();
		if (FactionVillages.of(r.owner).siegeBurns()) r.plan.addAll(Plans.burn(level, r, level.getRandom()));
		r.planIndex = 0;
		r.planKind = 1;
		setState(level, r, VillageRecord.State.SIEGE);
		return true;
	}

	static void process(ServerLevel level, VillageRecord r, long now, Vec3 observer) {
		switch (r.state) {
			case FREE -> {
				if (now >= r.nextEval) {
					String why = assessProtection(level, r, true);
					if (why != null) protect(level, r, why);
					else evaluate(level, r, now, observerPlayer(level, observer));
				}
			}
			case SIEGE -> {
				if (cheapProtect(level, r)) return;
				if (!r.forceSpawnedOnce) spawnSiegeForce(level, r);
				if (now % 100 == 0) aimAtGolems(level, r);
				if (now % 80 == 0) siegeStance(level, r);
				runOps(level, r, now);
				if (now >= r.siegeEnd && r.forceDead < r.forceSpawned) fall(level, r);
			}
			case OCCUPIED -> {
				if (cheapProtect(level, r)) return;
				if (!r.forceSpawnedOnce) spawnGarrison(level, r);
				startFortify(level, r);
			}
			case FORTIFYING -> {
				if (cheapProtect(level, r)) return;
				if (!r.forceSpawnedOnce) spawnGarrison(level, r);
				ensureSerfs(level, r);
				if ((now + r.id) % 20 == 0) levy(level, r, FactionVillages.of(r.owner));
				runOps(level, r, now);
				if (r.planIndex >= r.plan.size()) {
					releaseSerfs(level, r, false);
					r.plan.clear();
					r.planIndex = 0;
					r.planKind = 0;
					setState(level, r, VillageRecord.State.FORTIFIED);
					if (r.towerTop != null && r.archerSquad == null) {
						KingdomSoldier a = spawnSingle(level, r, UnitTypes.ARCHER, r.towerTop);
						if (a != null) r.archerSquad = a.squadId();
					}
					Conquest.roadFrom(level, r);
				}
				garrison(level, r, now);
			}
			case FORTIFIED -> {
				if (!r.forceSpawnedOnce) spawnGarrison(level, r); // new owner after a flip
				garrison(level, r, now);
				if (r.planKind >= 3 && !cheapProtect(level, r)) runOps(level, r, now); // cosmetic swap or wall growth in progress
				maintenance(level, r, now);
			}
			default -> {}
		}
		if (r.forceSpawnedOnce && r.forceSpawned > 0 && r.forceDead >= r.forceSpawned
			&& (r.state == VillageRecord.State.SIEGE || r.isOccupiedLike())) liberate(level, r);
		// belt and braces: the whole force is gone (killed, or removed without a death event): same as killing it
		else if (r.forceSpawnedOnce && r.forceSpawned > 0 && r.forceDead > 0 && now % 20 == 0 && r.state != VillageRecord.State.LIBERATED
			&& (r.state == VillageRecord.State.SIEGE && now < r.siegeEnd || r.isOccupiedLike()) && level.isLoaded(r.center)) {
			boolean alive = false;
			for (KingdomSoldier s : soldiers(level, r)) if (s.squadId() != null && !s.squadId().equals(r.taxSquad)) alive = true;
			if (!alive) liberate(level, r);
		}
	}

	static boolean cheapProtect(ServerLevel level, VillageRecord r) {
		String why = assessProtection(level, r, false);
		if (why == null) return false;
		protect(level, r, why);
		return true;
	}

	public static BlockPos edgeToward(ServerLevel level, VillageRecord r) {
		double ang;
		if (r.castle != null) ang = Math.atan2(r.castle.getZ() - r.center.getZ(), r.castle.getX() - r.center.getX());
		else ang = level.getRandom().nextDouble() * Math.PI * 2;
		double rad = Math.max(r.bounds.getXSpan(), r.bounds.getZSpan()) / 2.0 + 6;
		BlockPos p = r.center.offset((int) (Math.cos(ang) * rad), 0, (int) (Math.sin(ang) * rad));
		if (!level.isLoaded(p)) p = r.center;
		return Plans.surface(level, p);
	}

	public static Identifier factionOf(VillageRecord r) {
		return r.owner == null || r.owner.isEmpty() ? Factions.ALDERMARK : Identifier.parse(r.owner);
	}

	/** The faction the war layer should see as holder: the occupier while the village is held, null otherwise. */
	public static @Nullable Identifier warOwner(VillageRecord r) {
		return r.isOccupiedLike() ? factionOf(r) : null;
	}

	static final List<Identifier> SIEGE_A = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER);
	static final List<Identifier> SIEGE_B = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.CROSSBOWMAN);

	public static void spawnSiegeForce(ServerLevel level, VillageRecord r) {
		BlockPos at = edgeToward(level, r);
		Identifier f = factionOf(r);
		Profile p = FactionVillages.of(f);
		List<Identifier> ma = FactionVillages.members(f, p, FactionVillages.scaled(p, SIEGE_A.size()), level.getRandom(), SIEGE_A);
		List<Identifier> mb = FactionVillages.members(f, p, FactionVillages.scaled(p, SIEGE_B.size()), level.getRandom(), SIEGE_B);
		List<KingdomSoldier> a = KingdomApi.spawnSquad(level, at, f, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT, ma);
		List<KingdomSoldier> b = KingdomApi.spawnSquad(level, at.offset(3, 0, 3), f, Squad.Formation.LINE, UnitTypes.SERGEANT, mb);
		for (List<KingdomSoldier> sq : List.of(a, b)) {
			if (sq.isEmpty()) continue;
			Squad s = sq.get(0).squad();
			if (s != null) {
				s.setObjective(r.center);
				r.squads.add(s.id());
			}
			r.forceSpawned += sq.size();
		}
		r.forceSpawnedOnce = true;
		VillageLedger.get(level).dirty();
		aimAtGolems(level, r);
	}

	static void aimAtGolems(ServerLevel level, VillageRecord r) {
		if (FactionVillages.of(r.owner).protectsGolems()) return; // Azmar: golems are neutral until they attack
		List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, AABB.of(r.bounds.inflatedBy(16)), g -> g.isAlive() && !g.isPlayerCreated());
		if (golems.isEmpty()) return;
		for (KingdomSoldier s : soldiers(level, r)) if (s.getTarget() == null) s.setTarget(golems.get(s.getRandom().nextInt(golems.size())));
	}

	public static List<KingdomSoldier> soldiers(ServerLevel level, VillageRecord r) {
		List<KingdomSoldier> out = new ArrayList<>();
		if (r.squads.isEmpty() && r.taxSquad == null) return out;
		for (KingdomSoldier s : level.getEntitiesOfClass(KingdomSoldier.class, AABB.of(r.bounds.inflatedBy(48)), e -> e.isAlive()))
			if (s.squadId() != null && (r.squads.contains(s.squadId()) || s.squadId().equals(r.taxSquad))) out.add(s);
		return out;
	}

	/** The rivals' sieges break nothing: the force stands in the square. Hrafnholt beats shields and blows its war horn (intimidation, readable from afar). */
	static void siegeStance(ServerLevel level, VillageRecord r) {
		Profile p = FactionVillages.of(r.owner);
		if (p.siegeBurns() || !level.isLoaded(r.center)) return;
		if (p.shape() == FactionVillages.Shape.HRAFNHOLT) {
			level.playSound(null, r.center, net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.7F);
			if (level.getRandom().nextInt(4) == 0) level.playSound(null, r.center, net.minecraft.sounds.SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.8F); // the war horn
		}
		for (KingdomSoldier s : soldiers(level, r)) if (s.getTarget() == null && s.squadId() != null && !s.squadId().equals(r.taxSquad)) s.getNavigation().stop();
	}

	/** Runs this tick's block ops: the burn and the rebuild at the village's pace; a rebuild halts while the owner's Peat-Cutters strike. */
	public static void runOps(ServerLevel level, VillageRecord r, long now) {
		if (now % opInterval != 0) return;
		if (r.planKind != 1 && r.planIndex < r.plan.size() && isStriking(level, r.owner)) return;
		double n = opsPerRun;
		if (r.planKind != 1) n *= FactionVillages.of(r.owner).rebuildSpeed();
		r.opCarry += n;
		int k = (int) r.opCarry;
		r.opCarry -= k;
		for (int i = 0; i < k; i++) runOp(level, r);
	}

	/** One block op from the plan (the writer enforces every safety rule; refused ops are skipped). */
	static void runOp(ServerLevel level, VillageRecord r) {
		int tries = 0;
		while (r.planIndex < r.plan.size() && tries++ < 8) {
			VillageRecord.Op op = r.plan.get(r.planIndex++);
			BlockPos pos = BlockPos.of(op.pos());
			if (!op.state().isAir() && !op.state().canSurvive(level, pos)) continue;
			if (BlockWriter.write(level, r, pos, op.state())) {
				if (r.planKind == 1 && op.state().is(VillagesContent.SIEGE_FIRE))
					level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
				if (r.planKind >= 2) workSerfs(level, r, pos);
				break;
			}
		}
		VillageLedger.get(level).dirty();
	}

	/** The siege timer ran out: the village falls. The siege force stays as its garrison. */
	static void fall(ServerLevel level, VillageRecord r) {
		BlockWriter.extinguish(level, r);
		r.plan.clear();
		r.planIndex = 0;
		r.planKind = 0;
		setState(level, r, VillageRecord.State.OCCUPIED);
		for (KingdomSoldier s : soldiers(level, r)) s.setTarget(null);
		spawnTaxCollector(level, r);
		WarBridge.register(level.getServer(), r, warOwner(r), true, true);
		VillageEvents.OWNER_CHANGED.invoker().onOwnerChanged(level, r, r.owner, "", "siege");
	}

	// ---- occupation -----------------------------------------------------------------------------------------------------

	static void spawnGarrison(ServerLevel level, VillageRecord r) {
		BlockPos at = Plans.surface(level, r.center);
		Identifier f = factionOf(r);
		Profile p = FactionVillages.of(f);
		int extra = FactionVillages.scaled(p, 2 + level.getRandom().nextInt(3));
		List<Identifier> mix = new ArrayList<>();
		for (int i = 0; i < extra; i++) mix.add(UnitTypes.MAN_AT_ARMS);
		List<Identifier> members = FactionVillages.members(f, p, extra, level.getRandom(), mix);
		List<KingdomSoldier> g = KingdomApi.spawnSquad(level, at, f, Squad.Formation.COLUMN, UnitTypes.SERGEANT, members);
		if (!g.isEmpty() && g.get(0).squad() != null) r.squads.add(g.get(0).squad().id());
		r.forceSpawned += g.size();
		r.forceSpawnedOnce = true;
		spawnTaxCollector(level, r);
		VillageLedger.get(level).dirty();
	}

	/** The tax collector at the bell. Not part of the fighting force: on liberation he surrenders and becomes the Steward. */
	static void spawnTaxCollector(ServerLevel level, VillageRecord r) {
		if (r.taxSquad != null) return;
		BlockPos at = Plans.surface(level, r.center).offset(1, 0, 1);
		Identifier f = factionOf(r);
		Squad sq = KingdomApi.createSquad(level, f, Squad.Formation.COLUMN);
		KingdomSoldier tax = KingdomApi.spawnSoldier(level, UnitTypes.MAN_AT_ARMS, f, Vec3.atBottomCenterOf(at), sq);
		if (tax == null) return;
		tax.setCustomName(Component.translatable("entity.elsewhere.village.tax_collector"));
		tax.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		sq.setObjective(at);
		r.taxSquad = sq.id();
	}

	static @Nullable KingdomSoldier spawnSingle(ServerLevel level, VillageRecord r, Identifier unit, BlockPos at) {
		Identifier f = factionOf(r);
		Squad sq = KingdomApi.createSquad(level, f, Squad.Formation.COLUMN);
		KingdomSoldier s = KingdomApi.spawnSoldier(level, unit, f, Vec3.atBottomCenterOf(at), sq);
		if (s == null) return null;
		sq.setObjective(at);
		r.squads.add(sq.id());
		r.forceSpawned++;
		return s;
	}

	public static void startFortify(ServerLevel level, VillageRecord r) {
		Profile prof = FactionVillages.of(r.owner);
		Plans.Fortify f = prof.isAldermark() ? Plans.fortify(level, r, level.getRandom()) : RivalPlans.fortify(level, r, level.getRandom(), prof);
		r.plan.clear();
		r.plan.addAll(f.ops());
		r.planIndex = 0;
		r.planKind = 2;
		r.coffer = f.coffer();
		r.towerTop = f.towerTop();
		r.hint = prof.isAldermark() ? f.towerTop() : RivalPlans.lastHint;
		r.taxPost = f.taxPost();
		setState(level, r, VillageRecord.State.FORTIFYING);
		ensureSerfs(level, r);
		Conquest.roadFrom(level, r);
	}

	public static void ensureSerfs(ServerLevel level, VillageRecord r) {
		if (!r.serfs.isEmpty()) return;
		Profile p = FactionVillages.of(r.owner);
		int base = 3 + level.getRandom().nextInt(3);
		int n = p.isAldermark() ? base : Math.max(1, (int) Math.round(base * p.serfCount()));
		BlockPos at = edgeToward(level, r);
		for (int i = 0; i < n; i++) {
			Serf s = VillagesContent.SERF.create(level, EntitySpawnReason.EVENT);
			if (s == null) continue;
			BlockPos pos = Plans.surface(level, at.offset(i % 3, 0, i / 3));
			if (!level.isLoaded(pos)) pos = Plans.surface(level, r.center.offset(i % 3, 0, i / 3)); // an unloaded column reads as the world floor: never spawn there
			s.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
			s.villageId = r.id;
			s.variant = p.serfKind().key;
			s.faction = r.owner;
			// every faction, Aldermark included: RIVALS-DECISIONS 14 overrides "Aldermark unchanged" for health
			s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(p.serfHealth());
			s.setHealth((float) p.serfHealth());
			s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 2 == 0 ? Items.SPRUCE_LOG : Items.COBBLESTONE));
			s.setDropChance(EquipmentSlot.MAINHAND, 0F);
			level.addFreshEntity(s);
			r.serfs.add(s.getUUID());
		}
		VillageLedger.get(level).dirty();
	}

	public static List<Serf> serfs(ServerLevel level, VillageRecord r) {
		List<Serf> out = new ArrayList<>();
		for (UUID u : r.serfs) if (level.getEntity(u) instanceof Serf s && s.isAlive()) out.add(s);
		return out;
	}

	static void workSerfs(ServerLevel level, VillageRecord r, BlockPos pos) {
		List<Serf> ss = serfs(level, r);
		if (ss.isEmpty()) return;
		Serf s = ss.get((int) (level.getGameTime() / 200 % ss.size()));
		s.workPos = pos;
	}

	/** Serfs leave (rebuild done) or, when freed, about half become ordinary villagers again. */
	static void releaseSerfs(ServerLevel level, VillageRecord r, boolean freed) {
		for (Serf s : serfs(level, r)) {
			if (freed && level.getRandom().nextBoolean()) {
				Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.CONVERSION);
				if (v != null) {
					v.snapTo(s.getX(), s.getY(), s.getZ(), s.getYRot(), 0F);
					level.addFreshEntity(v);
				}
			}
			s.discard();
		}
		r.serfs.clear();
		VillageLedger.get(level).dirty();
	}

	// ---- garrison: patrols, night watch, warnings -----------------------------------------------------------------------

	public static List<BlockPos> waypoints(ServerLevel level, VillageRecord r) {
		List<BlockPos> w = new ArrayList<>();
		for (VillageRecord.Piece p : r.pieces) if (p.type() == VillageRecord.STREET) w.add(p.box().getCenter());
		w.sort((a, b) -> Double.compare(Math.atan2(a.getZ() - r.center.getZ(), a.getX() - r.center.getX()), Math.atan2(b.getZ() - r.center.getZ(), b.getX() - r.center.getX())));
		if (w.isEmpty()) w.add(r.center);
		return w;
	}

	/** Held-village policing per faction (FIELD-TACTICS 3.2, 4, 5). */
	static void policing(ServerLevel level, VillageRecord r, Profile p, KingdomState ks) {
		boolean night = level.isDarkOutside();
		List<BlockPos> w = waypoints(level, r);
		BlockPos gate = w.get(0);
		for (BlockPos q : w) if (q.distSqr(r.center) > gate.distSqr(r.center)) gate = q;
		for (int i = 0; i < r.squads.size(); i++) {
			Squad s = ks.squad(r.squads.get(i));
			if (s == null || s.id().equals(r.archerSquad)) continue;
			switch (p.shape()) {
				case AZMAR -> {
					// picket routine: the squad holds the gate by day (the obelisk on a border village), patrols the streets at night
					if (night) patrol(level, s, i, w);
					else s.setObjective(r.hint != null && isBorder(level, r) ? Plans.surface(level, r.hint) : Plans.surface(level, gate));
				}
				case MIREWICK -> {
					// hold the boardwalk by day; at night patrol with a lantern in the leader's hand (the readable sign)
					if (night) {
						if (s.leaderEntity(level) instanceof KingdomSoldier ls && !ls.getOffhandItem().is(Items.LANTERN)) ls.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
						patrol(level, s, i, w);
					} else {
						if (s.leaderEntity(level) instanceof KingdomSoldier ls && ls.getOffhandItem().is(Items.LANTERN)) ls.setUnit(ls.unitId());
						s.setObjective(Plans.surface(level, r.center));
					}
				}
				default -> {
					// Hrafnholt: patrols the streets by day, gathers at the hall fire at night
					if (night) s.setObjective(r.center);
					else patrol(level, s, i, w);
				}
			}
		}
	}

	static void patrol(ServerLevel level, Squad s, int i, List<BlockPos> w) {
		var leader = s.leaderEntity(level);
		int idx = patrolIndex.getOrDefault(s.id(), i * 3);
		BlockPos goal = w.get(Math.floorMod(idx, w.size()));
		if (leader == null || leader.blockPosition().distSqr(goal) < 16 || s.objective() == null) {
			patrolIndex.put(s.id(), ++idx);
			s.setObjective(Plans.surface(level, w.get(Math.floorMod(idx, w.size()))));
		}
	}

	/** The garrison defends its village: hostile mobs (raiders, zombies...) near it become targets; unprovoked creepers are left alone (2.0 rule). */
	static void defend(ServerLevel level, VillageRecord r) {
		List<KingdomSoldier> sol = soldiers(level, r);
		if (sol.isEmpty()) return;
		List<net.minecraft.world.entity.monster.Monster> threats = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, AABB.of(r.bounds.inflatedBy(24)),
			m -> m.isAlive() && !(m instanceof net.minecraft.world.entity.monster.Creeper c && c.getTarget() == null) && !(m instanceof KingdomSoldier));
		if (threats.isEmpty()) return;
		for (KingdomSoldier s : sol) {
			if (s.getTarget() != null && s.getTarget().isAlive() || s.squadId() != null && s.squadId().equals(r.taxSquad)) continue;
			var best = threats.stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(s))).orElse(null);
			if (best != null) s.setTarget(best);
		}
	}

	public static void garrison(ServerLevel level, VillageRecord r, long now) {
		if (now % 100 == 0) {
			Profile prof = FactionVillages.of(r.owner);
			if (!prof.isAldermark()) {
				policing(level, r, prof, KingdomState.get(level));
				defend(level, r);
			} else {
				List<BlockPos> w = null;
				KingdomState ks = KingdomState.get(level);
				boolean night = level.isDarkOutside();
				for (int i = 0; i < r.squads.size(); i++) {
					Squad s = ks.squad(r.squads.get(i));
					if (s == null) continue;
					if (s.id().equals(r.archerSquad)) {
						s.setObjective(r.towerTop != null ? r.towerTop : r.center);
						continue;
					}
					if (night) {
						s.setObjective(r.center);
						continue;
					}
					if (w == null) w = waypoints(level, r);
					var leader = s.leaderEntity(level);
					int idx = patrolIndex.getOrDefault(s.id(), i * 3);
					BlockPos goal = w.get(Math.floorMod(idx, w.size()));
					if (leader == null || leader.blockPosition().distSqr(goal) < 16 || s.objective() == null) {
						idx++;
						patrolIndex.put(s.id(), idx);
						s.setObjective(Plans.surface(level, w.get(Math.floorMod(idx, w.size()))));
					}
				}
			}
		}
		if (r.taxSquad != null && r.taxPost != null) {
			Squad t = KingdomState.get(level).squad(r.taxSquad);
			if (t != null) t.setObjective(r.taxPost);
		}
		if (now % 40 == 0) warn(level, r, now);
	}

	static boolean armed(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.TRIDENT) || s.is(Items.MACE);
	}

	static void warn(ServerLevel level, VillageRecord r, long now) {
		List<KingdomSoldier> sol = null;
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || !armed(p.getMainHandItem())) continue;
			if (!r.bounds.inflatedBy(12).isInside(p.blockPosition().getX(), r.bounds.minY(), p.blockPosition().getZ())) continue;
			String key = p.getUUID() + "/" + r.id;
			if (now - warned.getOrDefault(key, Long.MIN_VALUE / 2) < 2400) continue;
			if (sol == null) sol = soldiers(level, r);
			for (KingdomSoldier s : sol) {
				if (s.distanceToSqr(p) > 100 || s.getTarget() != null) continue;
				s.getNavigation().stop();
				s.getLookControl().setLookAt(p);
				p.sendSystemMessage(Component.translatable("message.elsewhere.village.warn", s.getDisplayName()));
				warned.put(key, now);
				break;
			}
		}
	}

	// ---- deaths and liberation ------------------------------------------------------------------------------------------

	public static void onSoldierDied(ServerLevel level, KingdomSoldier soldier) {
		UUID sq = soldier.squadId();
		if (sq == null || level.dimension() != Level.OVERWORLD) return;
		VillageLedger ledger = VillageLedger.get(level);
		for (VillageRecord r : ledger.villages.values()) {
			if (!r.squads.contains(sq)) continue;
			r.forceDead++;
			ledger.dirty();
			if (r.forceDead >= r.forceSpawned && (r.state == VillageRecord.State.SIEGE || r.isOccupiedLike())) liberate(level, r);
			return;
		}
		Conquest.onSoldierDied(level, ledger, sq);
	}

	public static void liberate(ServerLevel level, VillageRecord r) {
		if (r.state == VillageRecord.State.LIBERATED) return;
		BlockWriter.extinguish(level, r);
		r.plan.clear();
		r.planIndex = 0;
		r.planKind = 0;
		releaseSerfs(level, r, true);
		// the sealed coffer opens: the surrendered tax collector stays on as the Royal Steward (vanilla job-site claim). Rival villages
		// have no coffer (their shops are the court's NPCs); their tax collector simply surrenders and goes home as a villager.
		boolean opened = false;
		if (r.coffer != null && level.isLoaded(r.coffer)) {
			BlockState c = level.getBlockState(r.coffer);
			if (c.is(VillagesContent.ROYAL_COFFER) && c.getValue(RoyalCofferBlock.SEALED)) {
				BlockWriter.write(level, r, r.coffer, c.setValue(RoyalCofferBlock.SEALED, false));
				opened = true;
			}
		}
		if (opened || r.coffer == null && r.taxSquad != null) {
			for (KingdomSoldier s : soldiers(level, r)) if (s.squadId() != null && s.squadId().equals(r.taxSquad)) s.discard();
			Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (v != null) {
				BlockPos p = r.coffer != null ? r.coffer.above() : Plans.surface(level, r.center);
				v.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0F, 0F);
				v.setPersistenceRequired();
				level.addFreshEntity(v);
			}
		}
		setState(level, r, VillageRecord.State.LIBERATED);
		WarBridge.register(level.getServer(), r, null, true, true);
		VillageEvents.OWNER_CHANGED.invoker().onOwnerChanged(level, r, null, r.owner, "liberated");
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(Vec3.atCenterOf(r.center)) > 96 * 96) continue;
			p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 0));
			p.sendSystemMessage(Component.translatable("message.elsewhere.village.liberated"));
		}
		Item frag = VillagesContent.optionalItem("lore_survey_map_fragment", "lore_survey_fragment", "survey_map_fragment");
		if (frag != null) {
			BlockPos p = Plans.surface(level, r.center);
			level.addFreshEntity(new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(frag)));
		}
		loreHook(level, r);
		VillageEvents.LIBERATED.invoker().onLiberated(level, r);
	}

	/** K-D: liberated villages' cartographers sell the Survey Map ({@code LoreApi.markLiberated}); optional, by reflection. */
	static void loreHook(ServerLevel level, VillageRecord r) {
		try {
			Class<?> api = Class.forName("dev.elsewhere.lore.LoreApi");
			try {
				api.getMethod("markLiberated", ServerLevel.class, BlockPos.class, Identifier.class).invoke(null, level, r.center, factionOf(r));
			} catch (NoSuchMethodException e) {
				api.getMethod("markLiberated", ServerLevel.class, BlockPos.class).invoke(null, level, r.center);
			}
		} catch (ClassNotFoundException e) {
			// lore module not present (dev)
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[kingdom_villages] LoreApi.markLiberated failed: " + e);
		}
	}

	// ---- rivals: held time, wall growth, unrest, flips, strikes ----------------------------------------------------------

	public static double daysHeld(ServerLevel level, VillageRecord r) {
		return r.heldSince == 0 ? 0 : Math.max(0, (level.getGameTime() - r.heldSince) / (double) DAY);
	}

	/** Fort bonus (WAR-AI): {@code min(cap, rate * days held)}; border-only factions earn none off the border. */
	public static double fort(ServerLevel level, VillageRecord r) {
		Profile p = FactionVillages.of(r.owner);
		FactionVillages.Policy pol = FactionVillages.livePolicy(level.getServer(), p);
		if (pol.borderOnly() && !isBorder(level, r)) return 0;
		return Math.min(pol.fortCap(), pol.fortRate() * daysHeld(level, r));
	}

	/** Unrest after a conquest: starts at the faction's {@code unrest} and falls 1/integration-days per day (WAR-AI). */
	public static double unrest(ServerLevel level, VillageRecord r) {
		Profile p = FactionVillages.of(r.owner);
		if (r.heldSince == 0 || !r.isOccupiedLike() || r.prevOwner.isEmpty()) return 0;
		FactionVillages.Policy pol = FactionVillages.livePolicy(level.getServer(), p);
		return Math.max(0, pol.unrest() - daysHeld(level, r) / Math.max(1, pol.integDays()));
	}

	/** A border village has another faction's occupied village or castle within 1,100 blocks (the war layer's supply reach). */
	public static boolean isBorder(ServerLevel level, VillageRecord r) {
		for (VillageRecord o : VillageLedger.get(level).villages.values()) {
			if (o == r || !o.isOccupiedLike() || o.owner.equals(r.owner)) continue;
			if (RealmLocator.horizontal(o.center, r.center) <= 1100) return true;
		}
		Identifier mine = factionOf(r);
		for (var e : RealmLocator.castles(level).entrySet()) {
			if (e.getKey().equals(mine)) continue;
			if (RealmLocator.horizontal(e.getValue(), r.center) <= 1500) return true;
		}
		return false;
	}

	/** Every 200 ticks per held village: finish queued swaps, grow walls (Azmar), show or clear unrest, arm the levy. */
	public static void maintenance(ServerLevel level, VillageRecord r, long now) {
		Profile p = FactionVillages.of(r.owner);
		if ((now + r.id) % 20 == 0) levy(level, r, p);
		if ((now + r.id) % 200 != 0) return;
		if (r.planKind >= 3) {
			ensureSerfs(level, r);
			if (r.planIndex >= r.plan.size()) {
				r.plan.clear();
				r.planIndex = 0;
				r.planKind = 0;
				releaseSerfs(level, r, false);
			}
			return;
		}
		if (r.swapQueued) {
			r.swapQueued = false;
			queuePlan(r, RivalPlans.swap(level, r, p, level.getRandom()), 3);
			return;
		}
		if (p.shape() == FactionVillages.Shape.AZMAR) {
			int target = (int) Math.min(9, Math.floor(fort(level, r) * 10 + 1e-9));
			if (target > r.wallSteps) {
				r.wallSteps++;
				queuePlan(r, RivalPlans.growth(level, r, p, r.wallSteps, level.getRandom()), 4);
				return;
			}
		}
		if (p.shape() == FactionVillages.Shape.HRAFNHOLT) {
			boolean show = unrest(level, r) > 0.3;
			if (show != r.unrestShown) {
				r.unrestShown = show;
				queuePlan(r, RivalPlans.recolor(level, r, show ? net.minecraft.world.item.DyeColor.GRAY : p.banner()), 3);
				return;
			}
		}
	}

	static void queuePlan(VillageRecord r, List<VillageRecord.Op> ops, int kind) {
		r.plan.clear();
		r.plan.addAll(ops);
		r.planIndex = 0;
		r.planKind = ops.isEmpty() ? 0 : kind;
	}

	/** Thrall levy: while the garrison is under attack, a third of the Thralls pick up stone axes; they put them away again once it is quiet. */
	static void levy(ServerLevel level, VillageRecord r, Profile p) {
		if (p.levyFraction() <= 0 || r.serfs.isEmpty()) return;
		boolean attacked = false;
		for (KingdomSoldier s : soldiers(level, r)) {
			if (s.getLastHurtByMob() != null && s.tickCount - s.getLastHurtByMobTimestamp() < 200) attacked = true;
		}
		List<Serf> ss = serfs(level, r);
		Item w = FactionVillages.levyWeapon(p);
		if (attacked && w != null) {
			int want = (int) Math.ceil(ss.size() * p.levyFraction() - 1e-6), have = 0;
			for (Serf s : ss) if (s.levyArmed) have++;
			for (Serf s : ss) {
				if (have >= want) break;
				if (!s.levyArmed) {
					s.arm(new ItemStack(w));
					have++;
				}
			}
		} else for (Serf s : ss) if (s.levyArmed) s.disarm(new ItemStack(Items.SPRUCE_LOG));
	}

	/** Peat-Cutters of {@code faction} are on strike (rebuilding stops) until this game time; 0 when not. */
	public static boolean isStriking(ServerLevel level, String faction) {
		Long u = VillageLedger.get(level).strikeUntil.get(faction);
		if (u != null && level.getGameTime() < u) return true;
		return u == null && Serfs.isOnStrike(level.getServer(), Identifier.parse(faction)); // the war layer's own flag (day granularity)
	}

	/** Mirewick's Counting-House was raided (vault opened, Clerk killed, or the keep's outpost sacked): its Peat-Cutters walk off for {@code days} in-game days. */
	public static void strike(ServerLevel level, String faction, int days) {
		VillageLedger l = VillageLedger.get(level);
		long until = level.getGameTime() + days * DAY;
		l.strikeUntil.merge(faction, until, Math::max);
		l.dirty();
		Serfs.strike(level.getServer(), Identifier.parse(faction));
		VillageEvents.STRIKE.invoker().onStrike(level, faction, until);
		for (VillageRecord r : l.villages.values()) {
			if (!r.owner.equals(faction) || r.serfs.isEmpty()) continue;
			for (Serf s : serfs(level, r)) if (s.variant.equals("peat_cutter")) s.getNavigation().stop();
		}
	}

	/** War layer: a village changed owner ({@code newOwner} null = went free). Ownership always changes; the cosmetic swap is queued and runs when a player loads the village. */
	public static void onVillageChanged(ServerLevel level, String key, BlockPos pos, @Nullable Identifier newOwner, @Nullable Identifier oldOwner, String reason) {
		long id;
		try {
			id = Long.parseLong(key);
		} catch (NumberFormatException e) {
			return;
		}
		VillageLedger ledger = VillageLedger.get(level);
		VillageRecord r = ledger.villages.get(id);
		if (r == null || r.kind != VillageRecord.Kind.VILLAGE) return;
		if (r.state == VillageRecord.State.PROTECTED || r.state == VillageRecord.State.RESTORED) return;
		long now = level.getGameTime();
		String old = r.owner;
		if (newOwner == null) {
			// went free (revolt): the occupier's people leave, the walls stay
			for (KingdomSoldier s : soldiers(level, r)) s.discard();
			releaseSerfs(level, r, true);
			r.squads.clear();
			r.taxSquad = null;
			r.archerSquad = null;
			r.forceSpawned = r.forceDead = 0;
			r.forceSpawnedOnce = false;
			r.plan.clear();
			r.planIndex = 0;
			r.planKind = 0;
			r.prevOwner = old;
			r.reason = reason;
			BlockWriter.extinguish(level, r);
			setState(level, r, VillageRecord.State.FREE);
			r.nextEval = now + EVAL_INTERVAL;
			r.swapQueued = false;
		} else {
			if (r.state == VillageRecord.State.SIEGE) {
				BlockWriter.extinguish(level, r);
				r.plan.clear();
				r.planIndex = 0;
				r.planKind = 0;
			}
			for (KingdomSoldier s : soldiers(level, r)) s.discard();
			releaseSerfs(level, r, false);
			r.squads.clear();
			r.taxSquad = null;
			r.archerSquad = null;
			r.forceSpawned = r.forceDead = 0;
			r.forceSpawnedOnce = false;
			r.prevOwner = old;
			r.owner = newOwner.toString();
			r.heldSince = now;
			r.lockUntil = now + 3 * DAY;
			r.wallSteps = 0;
			r.unrestShown = false;
			r.reason = reason;
			if (r.state == VillageRecord.State.FORTIFIED) {
				r.swapQueued = true; // a held, finished village changing hands: banners and signature pieces only, never houses or walls
			} else if (r.state != VillageRecord.State.OCCUPIED) {
				setState(level, r, VillageRecord.State.OCCUPIED); // taken from nobody (or mid-rebuild): the occupation rebuild in the new owner's style
				r.plan.clear();
				r.planIndex = 0;
				r.planKind = 0;
			}
		}
		ledger.dirty();
		VillageEvents.OWNER_CHANGED.invoker().onOwnerChanged(level, r, newOwner == null ? null : newOwner.toString(), old, reason);
	}

	// ---- villagers ------------------------------------------------------------------------------------------------------

	public static @Nullable VillageRecord villageAt(ServerLevel level, BlockPos pos) {
		for (VillageRecord r : VillageLedger.get(level).villages.values())
			if (r.bounds.inflatedBy(16).isInside(pos.getX(), r.bounds.minY(), pos.getZ())) return r;
		return null;
	}

	/** fx-siegeperf siege shelter: is this villager inside a village that is under siege right now? */
	public static boolean underSiege(ServerLevel level, Villager v) {
		if (level.dimension() != Level.OVERWORLD) return false;
		VillageRecord r = villageAt(level, v.blockPosition());
		return r != null && r.state == VillageRecord.State.SIEGE;
	}

	/** Villagers of a besieged or occupied village are captured: no trading until it is freed. */
	public static boolean isCaptured(ServerLevel level, Villager v) {
		if (level.dimension() != Level.OVERWORLD) return false;
		VillageRecord r = villageAt(level, v.blockPosition());
		return r != null && (r.state == VillageRecord.State.SIEGE || r.isOccupiedLike());
	}

	/** Villager.updateSpecialPrices (TAIL): Steward floors, or the permanent 10% discount of a liberated village. */
	public static void applyPrices(Villager v) {
		if (!(v.level() instanceof ServerLevel level)) return;
		if (Steward.isSteward(v)) {
			Steward.applyPriceRules(v);
			return;
		}
		VillageRecord r = villageAt(level, v.blockPosition());
		if (r == null || r.state != VillageRecord.State.LIBERATED) return;
		for (MerchantOffer o : v.getOffers()) {
			int d = Math.round(o.getBaseCostA().getCount() * 0.10F);
			if (d > 0) o.addToSpecialPriceDiff(-d);
		}
	}

	/** Self-test helper: mean manager time per tick in ms. */
	public static double meanMs() {
		return ticksRun == 0 ? 0 : nanosSpent / 1e6 / ticksRun;
	}

	static RandomSource rnd(ServerLevel level) {
		return level.getRandom();
	}

	static void unused(Entity e, Blocks b) {}
}
