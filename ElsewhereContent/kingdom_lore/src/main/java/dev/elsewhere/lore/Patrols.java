package dev.elsewhere.lore;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomEvents;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Royal patrols: a lore encounter for explorers. A Sergeant (the courier) leads a small column that marches across the
 * explorer's path. They stay neutral unless provoked; right-clicking the courier hands over the Royal Proclamation, and
 * a slain courier drops the pouch ({@code chests/kingdom_courier}). Rates: {@link PatrolRate}.
 */
public final class Patrols {
	private Patrols() {}

	public static final String TAG = "lore_patrol", COURIER_TAG = "lore_courier";
	public static final int LIFETIME_TICKS = 9000, MAX_ACTIVE = 8;

	public static final class Patrol {
		final Squad squad;
		final ServerLevel level;
		final long born;
		final Vec3 dir;
		final List<UUID> ids = new ArrayList<>();
		final List<KingdomSoldier> mobs = new ArrayList<>();
		Patrol(Squad squad, ServerLevel level, long born, Vec3 dir) {
			this.squad = squad;
			this.level = level;
			this.born = born;
			this.dir = dir;
		}
	}

	static final class PState {
		long exploreTicks;
		long cooldownUntil;
		boolean owed;
	}

	public static final List<Patrol> ACTIVE = new ArrayList<>();
	static final Map<UUID, PState> STATE = new HashMap<>();
	/** courier entity -> players already handed a Proclamation by that courier. */
	static final Map<UUID, Set<UUID>> GIVEN = new HashMap<>();
	/** Squad ids of patrols we know, so orphans from a crash are removed on load. */
	static final Set<UUID> KNOWN_SOLDIERS = new HashSet<>();
	static final List<Entity> ORPHANS = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD) return;
			if (!ORPHANS.isEmpty()) {
				for (Entity e : ORPHANS) if (!e.isRemoved()) e.discard();
				ORPHANS.clear();
			}
			if (level.getGameTime() % 20 != 7) return;
			tickSecond(level);
		});
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof KingdomSoldier && e.entityTags().contains(TAG) && !KNOWN_SOLDIERS.contains(e.getUUID())) ORPHANS.add(e);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			for (Patrol p : ACTIVE) disband(p);
			ACTIVE.clear();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			LoreState st = LoreState.get(srv.overworld());
			if (!st.stanceSet()) {
				// Aldermark is "Neutral until you provoke it" (KINGDOM.md section 3): set once, K-A's aggression may change it later.
				KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, false);
				st.setStanceSet();
			}
		});
		KingdomEvents.SOLDIER_DIED.register((level, s, src) -> {
			LoreFactions.Lore lf = LoreFactions.of(s.squad() != null ? s.squad().faction() : Factions.ALDERMARK);
			if (s.entityTags().contains(COURIER_TAG)) LoreLoot.dropCourierPouch(level, s, lf);
			else if (s.entityTags().contains(TAG) && level.getRandom().nextFloat() < 0.04F) s.spawnAtLocation(level, new ItemStack(lf.fragment != null ? lf.fragment : LoreItems.SURVEY_FRAGMENT));
			KNOWN_SOLDIERS.remove(s.getUUID());
			GIVEN.remove(s.getUUID());
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && entity instanceof KingdomSoldier s && s.entityTags().contains(COURIER_TAG)) {
				if (handProclamation(sl, sp, s)) return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	static final String[] COURIER_LINES = {
		"message.elsewhere.lore_courier_1", "message.elsewhere.lore_courier_2", "message.elsewhere.lore_courier_3", "message.elsewhere.lore_courier_4"};

	/** The courier hands a copy of the Proclamation to a player it is not fighting. */
	public static boolean handProclamation(ServerLevel level, ServerPlayer p, KingdomSoldier courier) {
		if (courier.getTarget() == p || courier.isRouted()) return false;
		Set<UUID> given = GIVEN.computeIfAbsent(courier.getUUID(), k -> new HashSet<>());
		if (!given.add(p.getUUID())) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.lore_courier_done"), true);
			return true;
		}
		Identifier fac = courier.squad() != null ? courier.squad().faction() : Factions.ALDERMARK;
		LoreFactions.Lore lf = LoreFactions.of(fac);
		ItemStack book = new ItemStack(LoreItems.proclamationFor(fac));
		if (!p.getInventory().add(book)) p.spawnAtLocation(level, book);
		if (lf.rival()) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.lore_courier_" + lf.key + "_" + (1 + level.getRandom().nextInt(2))));
			LoreAdvancements.awardFaction(p, fac, "decree");
		} else {
			p.sendSystemMessage(Component.translatable(COURIER_LINES[level.getRandom().nextInt(COURIER_LINES.length)]));
			LoreAdvancements.award(p, "by_royal_decree");
		}
		return true;
	}

	static void tickSecond(ServerLevel level) {
		long now = level.getGameTime();
		// Patrol upkeep
		for (Iterator<Patrol> it = ACTIVE.iterator(); it.hasNext();) {
			Patrol p = it.next();
			if (p.level != level) continue;
			if (!upkeep(p, now)) {
				disband(p);
				it.remove();
			}
		}
		if (!LoreConfig.patrolsEnabled) return;
		BlockPos spawn = level.getRespawnData().pos();
		for (ServerPlayer pl : level.players()) {
			if (pl.isSpectator() || pl.isCreative() || pl.isRemoved() || !pl.isAlive()) continue;
			detectMeeting(level, pl);
			double d = Math.sqrt(horizDistSqr(pl.blockPosition(), spawn));
			PState st = STATE.computeIfAbsent(pl.getUUID(), k -> new PState());
			if (!exploring(level, pl) || PatrolRate.ramp(d) <= 0) continue;
			st.exploreTicks += 20;
			if (st.owed) {
				if (now >= st.cooldownUntil && trySpawn(level, pl, st)) st.owed = false;
				continue;
			}
			if (st.exploreTicks >= PatrolRate.ATTEMPT_TICKS) {
				st.exploreTicks -= PatrolRate.ATTEMPT_TICKS;
				if (now >= st.cooldownUntil && PatrolRate.roll(level.getRandom(), d)) {
					if (!trySpawn(level, pl, st)) st.owed = true;
				}
			}
		}
	}

	static double horizDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** On or near the surface in the Overworld, not swimming. */
	public static boolean exploring(ServerLevel level, Player pl) {
		if (pl.isUnderWater() || pl.isPassenger() && pl.getVehicle().isUnderWater()) return false;
		BlockPos b = pl.blockPosition();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, b.getX(), b.getZ());
		return b.getY() >= top - 6;
	}

	/** Spawns a patrol for {@code pl}: true when one was placed. */
	public static boolean trySpawn(ServerLevel level, ServerPlayer pl, PState st) {
		if (ACTIVE.size() >= MAX_ACTIVE) return false;
		for (Patrol p : ACTIVE) if (p.level == level && p.squad.leaderEntity(level) != null && p.squad.leaderEntity(level).distanceToSqr(pl) < 160 * 160) return false;
		RandomSource r = level.getRandom();
		Vec3 look = pl.getLookAngle();
		double base = Math.atan2(look.z, look.x);
		for (int i = 0; i < 10; i++) {
			double ang = base + Math.toRadians(r.nextInt(121) - 60);
			double dist = 56 + r.nextInt(25);
			int x = Mth.floor(pl.getX() + Math.cos(ang) * dist), z = Mth.floor(pl.getZ() + Math.sin(ang) * dist);
			BlockPos site = surfaceSite(level, x, z);
			if (site == null) continue;
			// march across the player's heading
			Vec3 dir = new Vec3(-Math.sin(base) * (r.nextBoolean() ? 1 : -1), 0, Math.cos(base) * (r.nextBoolean() ? 1 : -1)).normalize();
			if (spawnPatrol(level, site, dir, r, pickFaction(level, pl.blockPosition())) != null) {
				if (st != null) st.cooldownUntil = level.getGameTime() + PatrolRate.COOLDOWN_TICKS;
				return true;
			}
		}
		return false;
	}

	/** A dry, open standing spot at x,z or null. */
	static @Nullable BlockPos surfaceSite(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, 64, z))) return null;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.isPositionEntityTicking(pos)) return null;
		BlockState below = level.getBlockState(pos.below());
		if (!below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()) return null;
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return null;
		if (!level.getFluidState(pos).isEmpty()) return null;
		return pos;
	}

	static final Identifier SA = UnitTypes.SERGEANT;

	/** Builds the column. Package-visible so the self-test can place one at a known spot. */
	public static @Nullable List<KingdomSoldier> spawnPatrol(ServerLevel level, BlockPos site, Vec3 dir, RandomSource r) {
		return spawnPatrol(level, site, dir, r, Factions.ALDERMARK);
	}

	/** Horizontal reach of a realm for patrols (same rule as the villages: 2,600 blocks). */
	public static final int REALM_REACH = 2600;

	/** The kingdom whose realm is nearest {@code pos} (within {@link #REALM_REACH}, not fallen, registered); Aldermark otherwise. */
	public static Identifier pickFaction(ServerLevel level, BlockPos pos) {
		Identifier best = Factions.ALDERMARK;
		double bd = Double.MAX_VALUE;
		for (LoreFactions.Lore f : LoreFactions.ALL.values()) {
			if (f.rival() && (!Factions.exists(f.id) || LoreMaps.courtFallen(level.getServer(), f.id))) continue;
			BlockPos c = LoreMaps.courtRealmCenter(level.getServer(), f.id);
			if (c == null) continue;
			double d = Math.sqrt(horizDistSqr(c, pos));
			if (d <= REALM_REACH && d < bd) {
				bd = d;
				best = f.id;
			}
		}
		return best;
	}

	/** Patrol size from the faction's data (day/night), with a sane floor. */
	static int sizeFor(ServerLevel level, FactionDef def) {
		int n = level.isDarkOutside() ? def.army().patrolNight() : def.army().patrolDay();
		return n >= 3 ? Math.min(n, 10) : 6;
	}

	static List<Identifier> membersFor(FactionDef def, RandomSource r, int count) {
		List<Identifier> pool = new ArrayList<>();
		float total = 0;
		for (var e : def.army().makeup().entrySet()) if (!e.getKey().equals(SA) && e.getValue() > 0) total += e.getValue();
		List<Identifier> out = new ArrayList<>();
		if (total > 0) {
			for (int i = 0; i < count; i++) {
				float roll = r.nextFloat() * total;
				Identifier pick = null;
				for (var e : def.army().makeup().entrySet()) {
					if (e.getKey().equals(SA) || e.getValue() <= 0) continue;
					pick = e.getKey();
					roll -= e.getValue();
					if (roll <= 0) break;
				}
				if (pick != null) out.add(pick);
			}
			return out;
		}
		pool.addAll(List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.CROSSBOWMAN, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
		for (int i = 0; i < count; i++) out.add(pool.get(i % pool.size()));
		return out;
	}

	public static @Nullable List<KingdomSoldier> spawnPatrol(ServerLevel level, BlockPos site, Vec3 dir, RandomSource r, Identifier faction) {
		List<Identifier> members;
		if (faction.equals(Factions.ALDERMARK) || !Factions.exists(faction)) {
			faction = Factions.ALDERMARK;
			members = new ArrayList<>(List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER));
			if (r.nextInt(3) == 0) members.add(UnitTypes.CROSSBOWMAN);
		} else {
			FactionDef def = Factions.def(faction);
			members = membersFor(def, r, sizeFor(level, def) - 1);
		}
		List<KingdomSoldier> out = KingdomApi.spawnSquad(level, site, faction, Squad.Formation.COLUMN, SA, members);
		if (!out.isEmpty() && faction.equals(LoreFactions.MIREWICK) && level.isDarkOutside() && out.size() > 1)
			out.get(out.size() - 1).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN)); // a readable sign at night
		if (out.isEmpty()) return null;
		Squad sq = out.get(0).squad();
		if (sq == null) return null;
		for (KingdomSoldier s : out) {
			s.addTag(TAG);
			KNOWN_SOLDIERS.add(s.getUUID());
		}
		out.get(0).addTag(COURIER_TAG);
		Patrol pt = new Patrol(sq, level, level.getGameTime(), dir);
		for (KingdomSoldier s : out) {
			pt.ids.add(s.getUUID());
			pt.mobs.add(s);
		}
		ACTIVE.add(pt);
		sq.setObjective(waypoint(level, site, dir));
		return out;
	}

	static @Nullable BlockPos waypoint(ServerLevel level, BlockPos from, Vec3 dir) {
		int x = Mth.floor(from.getX() + dir.x * 32), z = Mth.floor(from.getZ() + dir.z * 32);
		if (!level.isLoaded(new BlockPos(x, 64, z))) return null;
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** One second of patrol upkeep. Returns false when the patrol is over. */
	static boolean upkeep(Patrol p, long now) {
		var leader = p.squad.leaderEntity(p.level);
		if (leader == null) return !p.squad.memberEntities(p.level).isEmpty() && now - p.born < LIFETIME_TICKS;
		ServerPlayer near = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer pl : p.level.players()) {
			double d = pl.distanceToSqr(leader);
			if (d < best) {
				best = d;
				near = pl;
			}
		}
		double nd = near == null ? Double.MAX_VALUE : Math.sqrt(best);
		boolean fighting = leader instanceof KingdomSoldier ks && ks.getTarget() != null;
		if (!fighting) {
			if (nd > 200) return false;
			if (now - p.born > LIFETIME_TICKS && nd > 64) return false;
		}
		BlockPos obj = p.squad.objective();
		if (!fighting && (obj == null || leader.blockPosition().distSqr(obj) < 16)) {
			BlockPos next = waypoint(p.level, leader.blockPosition(), p.dir);
			if (next != null) p.squad.setObjective(next);
		}
		return true;
	}

	public static void disbandForTest(Object p) {
		disband((Patrol) p);
	}

	static void disband(Patrol p) {
		for (UUID id : p.ids) {
			KNOWN_SOLDIERS.remove(id);
			GIVEN.remove(id);
			Entity e = p.level.getEntity(id);
			if (e != null && !e.isRemoved()) e.discard();
		}
		for (KingdomSoldier m : p.mobs) if (!m.isRemoved()) m.discard();
	}

	/** First sight of a patrol (within 40 blocks, line of sight) opens the hidden advancement tab. */
	static void detectMeeting(ServerLevel level, ServerPlayer pl) {
		for (Patrol p : ACTIVE) {
			if (p.level != level) continue;
			var l = p.squad.leaderEntity(level);
			if (l != null && l.distanceToSqr(pl) < 40 * 40 && pl.hasLineOfSight(l)) {
				Identifier fac = p.squad.faction();
				LoreAdvancements.award(pl, "heralds_on_the_road");
				LoreAdvancements.awardFaction(pl, fac, "road");
				return;
			}
		}
	}

	public static int activeCount() {
		return ACTIVE.size();
	}
}
