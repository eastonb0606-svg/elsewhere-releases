package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Squad LOD (KINGDOM.md 2.5): ring 0 = real soldiers with full AI (nearest {@code perf.maxRealSoldiersPerPlayer} per
 * player within {@code perf.activeRadius}), ring 1 = real but reduced AI, ring 2 = <b>virtual</b>: when no player has
 * been within {@link #VIRTUALIZE_RADIUS} of a squad for {@link #VIRTUALIZE_DELAY} ticks (and it is not fighting), its
 * soldiers are stored as data ({@link Squad.VirtualSoldier}: type, unit, health, offset) and the entities removed. A
 * virtual squad keeps marching/patrolling as a point and becomes real again with the same UUIDs and saved health (no
 * free healing; the dead stay dead) when a player comes within {@link #MATERIALIZE_RADIUS}, room under the caps allowing.
 */
public final class KingdomLod {
	private KingdomLod() {}

	public static final double VIRTUALIZE_RADIUS = 96, MATERIALIZE_RADIUS = 80;
	public static final int VIRTUALIZE_DELAY = 200;
	/** Blocks a virtual squad moves per second. */
	public static final double VIRTUAL_SPEED = 3.0;
	/** Tests: counters. */
	public static long virtualized, materialized, dismissedRemoved;

	static List<Vec3> observers(ServerLevel level) {
		List<Vec3> obs = new ArrayList<>(KingdomPerf.testObservers);
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) obs.add(p.position());
		return obs;
	}

	/** True when a player (or a test observer) is within {@code r} blocks horizontally of x/z. */
	public static boolean observerNear(ServerLevel level, double x, double z, double r) {
		for (Vec3 o : observers(level)) {
			double dx = o.x - x, dz = o.z - z;
			if (dx * dx + dz * dz <= r * r) return true;
		}
		return false;
	}

	static boolean observerWithin(ServerLevel level, Vec3 pos, double r) {
		for (Vec3 o : observers(level)) if (o.distanceToSqr(pos) < r * r) return true;
		return false;
	}

	/** Called every second for real squads. True when the squad was virtualized or removed. */
	static boolean maybeVirtualize(ServerLevel level, Squad q, @Nullable Mob lead, List<KingdomSoldier> mem, Vec3 center, long now) {
		if (observerWithin(level, center, VIRTUALIZE_RADIUS)) {
			q.lastObserved = now;
			return false;
		}
		if (KingdomPerf.forceActive || now - q.lastObserved < VIRTUALIZE_DELAY || !q.enemies().isEmpty()) return false;
		if (q.dismissed) {
			// a finished hunt out of everyone's sight: the soldiers go home (removed for good)
			for (KingdomSoldier s : mem) removeQuietly(s);
			if (lead instanceof KingdomSoldier ls) removeQuietly(ls);
			KingdomState.get(level).removeSquad(q);
			dismissedRemoved++;
			return true;
		}
		if (!q.virtualizable) return false;
		return virtualize(level, q);
	}

	private static void removeQuietly(KingdomSoldier s) {
		s.virtualizing = true;
		Entity v = s.getVehicle();
		s.stopRiding();
		if (v instanceof AbstractHorse h && !h.isVehicle()) v.discard();
		s.discard();
	}

	/** Stores the squad's loaded soldiers as data and removes the entities. False if a non-soldier (boss) leads it. */
	public static boolean virtualize(ServerLevel level, Squad q) {
		if (q.virtual) return true;
		Mob lead = q.leaderEntity(level);
		if (lead != null && !(lead instanceof KingdomSoldier)) return false;
		List<KingdomSoldier> all = new ArrayList<>();
		if (lead instanceof KingdomSoldier ls) all.add(ls);
		all.addAll(q.memberEntities(level));
		if (all.isEmpty()) return false;
		for (KingdomSoldier s : all) if (s.isRouted()) return false;
		Vec3 a = all.get(0).position();
		q.virtualMembers.clear();
		for (KingdomSoldier s : all) {
			Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(s.getType());
			q.virtualMembers.add(new Squad.VirtualSoldier(s.getUUID(), type, s.unitId(), s.getHealth(), s.getX() - a.x, s.getY() - a.y, s.getZ() - a.z));
		}
		for (KingdomSoldier s : all) removeQuietly(s);
		q.anchor = a;
		q.virtual = true;
		q.restingNow = false;
		KingdomState.get(level).setDirty();
		virtualized++;
		return true;
	}

	/** Called every 5 ticks (squad phase) for virtual squads: move as a point, materialize near players. */
	static void tickVirtual(ServerLevel level, Squad q, long now) {
		if (q.anchor == null || q.virtualMembers.isEmpty()) {
			if (q.virtualMembers.isEmpty()) KingdomState.get(level).removeSquad(q);
			return;
		}
		if ((now + q.phase) % 20 != 0) return;
		if (q.dismissed) {
			KingdomState.get(level).removeSquad(q);
			dismissedRemoved++;
			return;
		}
		q.updateRoutineObjective(level, q.anchor, now);
		if (q.objective != null) {
			Vec3 to = Vec3.atBottomCenterOf(q.objective);
			Vec3 d = to.subtract(q.anchor);
			double len = Math.sqrt(d.x * d.x + d.z * d.z);
			if (len > 1.0) {
				double step = Math.min(len, VIRTUAL_SPEED);
				double nx = q.anchor.x + d.x / len * step, nz = q.anchor.z + d.z / len * step;
				BlockPos bp = BlockPos.containing(nx, q.anchor.y, nz);
				double ny = level.isLoaded(bp) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ()) : q.anchor.y;
				q.anchor = new Vec3(nx, ny, nz);
			}
		}
		BlockPos ap = BlockPos.containing(q.anchor);
		if (observerWithin(level, q.anchor, MATERIALIZE_RADIUS) && level.isLoaded(ap) && level.isPositionEntityTicking(ap)) materialize(level, q);
	}

	/**
	 * Turns a virtual squad back into soldiers at its anchor (same UUIDs, saved health). Returns the number spawned, 0 when
	 * the caps leave no room for the whole squad (it stays virtual and retries).
	 */
	public static int materialize(ServerLevel level, Squad q) {
		if (!q.virtual || q.anchor == null) return 0;
		if (KingdomPerf.room(level, q.anchor) < q.virtualMembers.size()) return 0;
		int made = 0;
		List<UUID> failed = new ArrayList<>();
		for (Squad.VirtualSoldier v : q.virtualMembers) {
			EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(v.type());
			Entity e = t == null ? null : t.create(level, EntitySpawnReason.EVENT);
			if (!(e instanceof KingdomSoldier s)) {
				if (e != null) e.discard();
				failed.add(v.uuid());
				continue;
			}
			double x = q.anchor.x + v.dx(), z = q.anchor.z + v.dz();
			BlockPos bp = BlockPos.containing(x, q.anchor.y, z);
			double y = level.isLoaded(bp) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ()) : q.anchor.y + v.dy();
			s.setUUID(v.uuid());
			s.snapTo(x, y, z, level.getRandom().nextFloat() * 360F, 0F);
			s.setFaction(q.faction);
			s.setUnit(v.unit());
			s.setHealth(Math.min(v.health(), s.getMaxHealth()));
			s.setSquadId(q.id);
			if (!level.addFreshEntity(s)) {
				failed.add(v.uuid());
				continue;
			}
			KingdomPerf.onLoad(s);
			made++;
		}
		for (UUID u : failed) {
			q.members.remove(u);
			if (u.equals(q.leader)) q.leader = null;
		}
		q.virtualMembers.clear();
		q.virtual = false;
		q.lastObserved = level.getGameTime();
		if (q.leader == null) q.promote(level);
		KingdomState.get(level).setDirty();
		materialized++;
		return made;
	}

	/**
	 * Herald's horn: the nearest virtual squad of the same faction within 128 blocks marches to the herald (and becomes
	 * real at once when it can); without one, a small new squad (sergeant + 3) arrives from 20 blocks behind, caps allowing.
	 * Returns the number of soldiers called.
	 */
	public static int callReinforcements(ServerLevel level, KingdomSoldier herald) {
		KingdomState st = KingdomState.get(level);
		Squad best = null;
		double bd = 128 * 128;
		for (Squad q : st.squads()) {
			if (!q.virtual || q.anchor == null || !q.faction.equals(herald.factionId())) continue;
			double d = q.anchor.distanceToSqr(herald.position());
			if (d < bd) {
				bd = d;
				best = q;
			}
		}
		if (best != null) {
			int n = best.virtualMembers.size();
			best.objective = herald.blockPosition();
			BlockPos ap = BlockPos.containing(best.anchor);
			if (level.isLoaded(ap) && level.isPositionEntityTicking(ap)) materialize(level, best);
			return n;
		}
		Vec3 back = herald.getTarget() != null ? herald.position().subtract(herald.getTarget().position()).normalize() : herald.getLookAngle().reverse();
		BlockPos at = BlockPos.containing(herald.getX() + back.x * 20, herald.getY(), herald.getZ() + back.z * 20);
		if (!level.isLoaded(at)) return 0;
		at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, at, herald.factionId(), Squad.Formation.LINE, UnitTypes.SERGEANT,
			ArmyComposer.members(level.getRandom(), 3, false));
		if (!sq.isEmpty() && sq.get(0).squad() != null) sq.get(0).squad().setObjective(herald.blockPosition());
		return sq.size();
	}
}
