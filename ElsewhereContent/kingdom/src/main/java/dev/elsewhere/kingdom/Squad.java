package dev.elsewhere.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A group of soldiers led by one officer (any {@link Mob} can lead: a {@link KingdomSoldier} or a boss).
 * <p><b>Brain:</b> runs every 5 ticks on its own phase. Only the leader pathfinds (to {@link #objective()}, repath every
 * 10-20 ticks through the global path budget). Members get a formation slot from the leader's heading and walk to it
 * with plain move control (no A*); they only path locally when they engage a target. Enemy scans are done once per
 * squad every 20 ticks and shared with the members.
 * <p><b>Morale:</b> 0-100. When the leader dies: morale -50, each member routs with chance
 * {@code morale.routChanceOnOfficerDeath}, and the first non-routed officer (else member) is promoted.
 * <p>Saved per level in {@link KingdomState}; members keep their squad id in their own NBT.
 */
public final class Squad {
	public enum Formation implements StringRepresentable {
		/** Melee in one rank beside the leader, ranged 3.5 blocks behind, riders on the flanks. */
		LINE,
		/** Tight melee rank 1.5 blocks in front of the leader with shields up when enemies are near, ranged behind. */
		SHIELD_WALL,
		/** Two-wide marching column behind the leader. */
		COLUMN;

		public static final Codec<Formation> CODEC = StringRepresentable.fromEnum(Formation::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** 0.2.0 daily routine of a field squad (see {@link #setRoutine}). */
	public enum Routine implements StringRepresentable {
		/** No schedule: the squad only follows its objective. */
		NONE,
		/** By day walk the waypoints (or roam within 16 blocks of home); at dusk return home; at night half the squad rests. */
		PATROL,
		/** Hold at home by day; at night half the squad rests. */
		GUARD;

		public static final Codec<Routine> CODEC = StringRepresentable.fromEnum(Routine::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A soldier stored as data while its squad is virtual (LOD ring 2): entity type, unit, saved health and offset. */
	public record VirtualSoldier(UUID uuid, Identifier type, Identifier unit, float health, double dx, double dy, double dz) {
		public static final Codec<VirtualSoldier> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("uuid").forGetter(VirtualSoldier::uuid),
			Identifier.CODEC.fieldOf("type").forGetter(VirtualSoldier::type),
			Identifier.CODEC.fieldOf("unit").forGetter(VirtualSoldier::unit),
			Codec.FLOAT.fieldOf("health").forGetter(VirtualSoldier::health),
			Codec.DOUBLE.optionalFieldOf("dx", 0.0).forGetter(VirtualSoldier::dx),
			Codec.DOUBLE.optionalFieldOf("dy", 0.0).forGetter(VirtualSoldier::dy),
			Codec.DOUBLE.optionalFieldOf("dz", 0.0).forGetter(VirtualSoldier::dz)
		).apply(i, VirtualSoldier::new));
	}

	/** Saved 0.2.0 fields (routine, LOD, command, hunt), nested so the 0.1.0 layout stays readable. */
	record Ext(Routine routine, Optional<BlockPos> home, List<BlockPos> waypoints, int wp, boolean virtual, List<VirtualSoldier> virtualMembers,
		Optional<Vec3> anchor, boolean virtualizable, Optional<UUID> commander, Optional<UUID> hunt, long huntUntil, int peak, boolean dismissed) {
		static final Codec<Ext> CODEC = RecordCodecBuilder.create(i -> i.group(
			Routine.CODEC.optionalFieldOf("routine", Routine.NONE).forGetter(Ext::routine),
			BlockPos.CODEC.optionalFieldOf("home").forGetter(Ext::home),
			BlockPos.CODEC.listOf().optionalFieldOf("waypoints", List.of()).forGetter(Ext::waypoints),
			Codec.INT.optionalFieldOf("wp", 0).forGetter(Ext::wp),
			Codec.BOOL.optionalFieldOf("virtual", false).forGetter(Ext::virtual),
			VirtualSoldier.CODEC.listOf().optionalFieldOf("virtual_members", List.of()).forGetter(Ext::virtualMembers),
			Vec3.CODEC.optionalFieldOf("anchor").forGetter(Ext::anchor),
			Codec.BOOL.optionalFieldOf("virtualizable", true).forGetter(Ext::virtualizable),
			UUIDUtil.CODEC.optionalFieldOf("commander").forGetter(Ext::commander),
			UUIDUtil.CODEC.optionalFieldOf("hunt").forGetter(Ext::hunt),
			Codec.LONG.optionalFieldOf("hunt_until", 0L).forGetter(Ext::huntUntil),
			Codec.INT.optionalFieldOf("peak", 0).forGetter(Ext::peak),
			Codec.BOOL.optionalFieldOf("dismissed", false).forGetter(Ext::dismissed)
		).apply(i, Ext::new));
	}

	Ext ext() {
		return new Ext(routine, Optional.ofNullable(home), List.copyOf(waypoints), wp, virtual, List.copyOf(virtualMembers), Optional.ofNullable(anchor),
			virtualizable, Optional.ofNullable(commander), Optional.ofNullable(hunt), huntUntil, peak, dismissed);
	}

	void readExt(Ext e) {
		routine = e.routine();
		home = e.home().orElse(null);
		waypoints.addAll(e.waypoints());
		wp = e.wp();
		virtual = e.virtual();
		virtualMembers.addAll(e.virtualMembers());
		anchor = e.anchor().orElse(null);
		virtualizable = e.virtualizable();
		commander = e.commander().orElse(null);
		hunt = e.hunt().orElse(null);
		huntUntil = e.huntUntil();
		peak = e.peak();
		dismissed = e.dismissed();
	}

	public static final Codec<Squad> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(s -> s.id),
		Identifier.CODEC.fieldOf("faction").forGetter(s -> s.faction),
		UUIDUtil.CODEC.optionalFieldOf("leader").forGetter(s -> Optional.ofNullable(s.leader)),
		UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(s -> s.members),
		Formation.CODEC.optionalFieldOf("formation", Formation.LINE).forGetter(s -> s.formation),
		Codec.INT.optionalFieldOf("morale", 100).forGetter(s -> s.morale),
		BlockPos.CODEC.optionalFieldOf("objective").forGetter(s -> Optional.ofNullable(s.objective)),
		Codec.FLOAT.optionalFieldOf("heading", 0F).forGetter(s -> s.heading),
		Ext.CODEC.optionalFieldOf("ext").forGetter(s -> Optional.of(s.ext()))
	).apply(i, (id, f, l, m, fo, mo, ob, h, ex) -> {
		Squad s = new Squad(id, f, fo);
		s.leader = l.orElse(null);
		s.members.addAll(m);
		s.morale = mo;
		s.objective = ob.orElse(null);
		s.heading = h;
		ex.ifPresent(s::readExt);
		return s;
	}));

	final UUID id;
	final Identifier faction;
	@Nullable UUID leader;
	final List<UUID> members = new ArrayList<>();
	Formation formation;
	int morale = 100;
	@Nullable BlockPos objective;
	float heading;
	// 0.2.0 saved
	Routine routine = Routine.NONE;
	@Nullable BlockPos home;
	final List<BlockPos> waypoints = new ArrayList<>();
	int wp;
	boolean virtual;
	final List<VirtualSoldier> virtualMembers = new ArrayList<>();
	@Nullable Vec3 anchor;
	boolean virtualizable = true;
	@Nullable UUID commander;
	@Nullable UUID hunt;
	long huntUntil;
	int peak;
	boolean dismissed;
	boolean halvedHit;

	// runtime only
	long lastObserved = -1;
	boolean restingNow;
	long noRestUntil;
	private final Map<UUID, Long> provoked = new HashMap<>();
	private List<LivingEntity> enemies = List.of();
	private long lastScan = -100;
	private @Nullable Vec3 lastLeaderPos;
	final int phase;

	Squad(UUID id, Identifier faction, Formation formation) {
		this.id = id;
		this.faction = faction;
		this.formation = formation;
		this.phase = Math.floorMod(id.hashCode(), 5);
	}

	public UUID id() {
		return id;
	}

	public Identifier faction() {
		return faction;
	}

	public @Nullable UUID leader() {
		return leader;
	}

	/** Member UUIDs (leader not included). */
	public List<UUID> members() {
		return List.copyOf(members);
	}

	public int size() {
		return members.size() + (leader != null ? 1 : 0);
	}

	public boolean hasRoom() {
		return size() < KingdomConfig.maxSquadSize;
	}

	public Formation formation() {
		return formation;
	}

	public void setFormation(Formation f) {
		this.formation = f;
	}

	public int morale() {
		return morale;
	}

	/** Sets morale (0-100), e.g. for scripted events. */
	public void setMorale(int m) {
		this.morale = Math.clamp(m, 0, 100);
	}

	public @Nullable BlockPos objective() {
		return objective;
	}

	/** Orders the leader to march to {@code pos} (null = hold here). Members follow in formation. */
	public void setObjective(@Nullable BlockPos pos) {
		this.objective = pos;
	}

	// ---- 0.2.0: routine, LOD, command, hunt ------------------------------------------------------------------------

	/**
	 * Gives the squad a daily routine around {@code home}: PATROL walks {@code waypoints} by day (or roams within 16 blocks of
	 * home when empty), GUARD holds home; both return home at dusk and rest half the squad at night (woken by any attack),
	 * with a bell at dawn. Works for virtual squads too (they move as a point).
	 */
	public void setRoutine(Routine r, @Nullable BlockPos home, List<BlockPos> waypoints) {
		this.routine = r;
		this.home = home;
		this.waypoints.clear();
		this.waypoints.addAll(waypoints);
		this.wp = 0;
	}

	public Routine routine() {
		return routine;
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** True while the squad is stored as data (no entities; LOD ring 2). */
	public boolean isVirtual() {
		return virtual;
	}

	/** Soldiers stored in the virtual record. */
	public List<VirtualSoldier> virtualMembers() {
		return List.copyOf(virtualMembers);
	}

	/** Where a virtual squad is (null when real). */
	public @Nullable Vec3 anchor() {
		return virtual ? anchor : null;
	}

	/** False keeps the squad real however far players are (boss guards, set pieces). Default true. */
	public void setVirtualizable(boolean v) {
		this.virtualizable = v;
	}

	public boolean isVirtualizable() {
		return virtualizable;
	}

	/** A captain (any mob) this squad follows as a wing when it has no objective of its own. */
	public void setCommander(@Nullable LivingEntity c) {
		this.commander = c == null ? null : c.getUUID();
	}

	public @Nullable UUID commander() {
		return commander;
	}

	/** The player this squad is hunting (aggression), or null. */
	public @Nullable UUID huntTarget() {
		return hunt;
	}

	/** Sets a hunt: the squad marches on {@code player}, treats them as provoking until {@code until}, then is dismissed. */
	public void setHunt(UUID player, long until) {
		this.hunt = player;
		this.huntUntil = until;
		provoke(player, until);
	}

	/** Dismissed squads are deleted (not stored) when the LOD system would make them virtual. */
	public void dismiss() {
		this.dismissed = true;
	}

	public boolean isDismissed() {
		return dismissed;
	}

	/** Wakes every resting soldier of the squad. */
	public void wake(ServerLevel level) {
		restingNow = false;
		noRestUntil = level.getGameTime() + 400; // stay up after being woken
		Mob l = leaderEntity(level);
		if (l instanceof KingdomSoldier ks) ks.setResting(false);
		for (KingdomSoldier s : memberEntities(level)) s.setResting(false);
	}

	/** Enemies seen by the last squad scan (shared target list). */
	public List<LivingEntity> enemies() {
		return enemies;
	}

	public void provoke(UUID attacker, long until) {
		provoked.put(attacker, until);
	}

	public boolean isProvokedBy(UUID e, long now) {
		Long t = provoked.get(e);
		return t != null && t >= now;
	}

	/** The loaded leader entity, or null. */
	public @Nullable Mob leaderEntity(ServerLevel level) {
		if (leader == null) return null;
		Entity e = level.getEntity(leader);
		return e instanceof Mob m && m.isAlive() ? m : null;
	}

	/** Loaded, living member soldiers (leader excluded). */
	public List<KingdomSoldier> memberEntities(ServerLevel level) {
		List<KingdomSoldier> out = new ArrayList<>(members.size());
		for (UUID u : members) if (level.getEntity(u) instanceof KingdomSoldier s && s.isAlive()) out.add(s);
		return out;
	}

	/** The squad-tactics brain (null for pass-through factions such as Aldermark); see {@code tactics.SquadBrain}. */
	public transient dev.elsewhere.kingdom.tactics.SquadBrain brain;

	void tick(ServerLevel level, long now) {
		if ((now + phase) % 5 != 0) return;
		if (now % 100 == phase) provoked.values().removeIf(t -> t < now);
		if (virtual) {
			KingdomLod.tickVirtual(level, this, now);
			return;
		}
		Mob lead = leaderEntity(level);
		List<KingdomSoldier> mem = memberEntities(level);
		if (lead == null && mem.isEmpty()) return; // unloaded or wiped out
		peak = Math.max(peak, mem.size() + (lead != null ? 1 : 0));
		boolean anyActive = KingdomPerf.forceActive;
		if (lead instanceof KingdomSoldier ks && ks.isActive()) anyActive = true;
		for (KingdomSoldier s : mem) if (s.isActive()) anyActive = true;
		Vec3 center = lead != null ? lead.position() : mem.get(0).position();
		// shared enemy scan
		if (anyActive && now - lastScan >= 20) {
			lastScan = now;
			KingdomSoldier self = lead instanceof KingdomSoldier ks ? ks : (mem.isEmpty() ? null : mem.get(0));
			enemies = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(24, 8, 24),
				e -> KingdomTargeting.canAttack(faction, this, self, e));
		} else if (!enemies.isEmpty()) {
			enemies.removeIf(e -> !e.isAlive() || e.isRemoved());
		}
		boolean slow = (now + phase) % 20 == 0;
		if (slow) {
			Vec3 lp = lead != null ? lead.position() : center;
			if (lastObserved < 0) lastObserved = now;
			if (KingdomLod.maybeVirtualize(level, this, lead, mem, lp, now)) return;
			tickCommandAndHunt(level, lp, now);
			tickRoutine(level, lp, now);
			tickMorale(level, mem, now);
		}
		if (lead == null) return;
		// heading: movement direction of the leader, else towards objective/enemy, else keep
		if (lastLeaderPos != null) {
			double dx = center.x - lastLeaderPos.x, dz = center.z - lastLeaderPos.z;
			if (dx * dx + dz * dz > 0.04) heading = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
			else if (!enemies.isEmpty()) {
				Vec3 e = enemies.get(0).position();
				heading = (float) Math.toDegrees(Math.atan2(-(e.x - center.x), e.z - center.z));
			}
		}
		lastLeaderPos = center;
		dev.elsewhere.kingdom.tactics.TacticsRuntime.tick(level, this, lead, mem, now);
		if (!KingdomConfig.useFormations) {
			for (KingdomSoldier s : mem) s.formationTarget = null;
			return;
		}
		double rad = Math.toRadians(heading);
		double fx = -Math.sin(rad), fz = Math.cos(rad); // forward
		double rx = -fz, rz = fx; // right
		int melee = 0, ranged = 0, riders = 0, second = 0;
		// the sergeant (or any officer leader) calls the shield wall against archers; SHIELD_WALL squads always wall up
		boolean rangedThreat = false;
		for (LivingEntity e : enemies) if (e instanceof net.minecraft.world.entity.monster.RangedAttackMob || (e.isUsingItem()
			&& (e.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem || e.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem))) rangedThreat = true;
		boolean officerLead = lead instanceof KingdomSoldier lk && lk.isOfficer();
		boolean wall = !enemies.isEmpty() && (formation == Formation.SHIELD_WALL || (officerLead && rangedThreat));
		for (KingdomSoldier s : mem) {
			double f, r;
			UnitType.Role role = s.role();
			if (s.isPassenger()) {
				int k = riders++;
				r = (k % 2 == 0 ? 1 : -1) * (5.5 + (k / 2) * 2.5);
				f = 0;
			} else if (formation == Formation.COLUMN) {
				int k = melee++;
				r = (k % 2 == 0 ? -0.8 : 0.8);
				f = -(1.6 + (k / 2) * 1.6);
			} else if (role.ranged()) {
				// archers keep back (but stay catchable)
				int k = ranged++;
				r = slotLateral(k, 1.5);
				f = formation == Formation.SHIELD_WALL ? -3.5 : -4.5;
			} else if (s.hasShield()) {
				// shield-bearers form the front rank and press in
				int k = melee++;
				r = slotLateral(k + 1, formation == Formation.SHIELD_WALL ? 1.1 : 1.6);
				f = formation == Formation.SHIELD_WALL ? 1.5 : 1.0;
			} else {
				// everyone else (pikemen, support) in between
				int k = second++;
				r = slotLateral(k, 1.6);
				f = formation == Formation.SHIELD_WALL ? -1.0 : -1.5;
			}
			s.formationTarget = new Vec3(center.x + fx * f + rx * r, center.y, center.z + fz * f + rz * r);
			s.formationYaw = heading;
			s.wallUp = wall && !role.ranged();
		}
	}

	private void tickCommandAndHunt(ServerLevel level, Vec3 pos, long now) {
		if (hunt != null) {
			net.minecraft.world.entity.player.Player p = level.getPlayerByUUID(hunt);
			if (p == null || now > huntUntil || p.isSpectator() || p.isCreative() || p.distanceToSqr(pos) > 160 * 160) {
				hunt = null;
				objective = null;
				dismissed = true;
			} else {
				objective = p.blockPosition();
				provoke(hunt, huntUntil);
			}
			return;
		}
		if (commander != null && enemies.isEmpty()) {
			Entity c = level.getEntity(commander);
			if (c instanceof LivingEntity lc && lc.isAlive()) {
				double rad = Math.toRadians(c.getYRot());
				double fx = -Math.sin(rad), fz = Math.cos(rad);
				int side = (id.hashCode() & 1) == 0 ? 1 : -1;
				objective = BlockPos.containing(c.getX() - fz * 7 * side - fx * 3, c.getY(), c.getZ() + fx * 7 * side - fz * 3);
			} else if (c == null && level.getEntity(commander) == null && now % 200 == phase) {
				commander = null; // captain gone (dead or unloaded): hold position
				objective = null;
			}
		}
	}

	/** Day/dusk/night schedule. Returns true at night. Updates the objective for real and virtual squads alike. */
	boolean updateRoutineObjective(ServerLevel level, Vec3 pos, long now) {
		if (routine == Routine.NONE || hunt != null) return false;
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean night = t >= 13000 && t < 23000;
		boolean dusk = t >= 12000 && t < 13000;
		if ((night || dusk) && home != null) {
			objective = home;
			return night;
		}
		if (routine == Routine.GUARD) objective = home;
		else if (!waypoints.isEmpty()) {
			if (objective == null || objective.distToCenterSqr(pos) < 3.5 * 3.5 || !waypoints.contains(objective)) {
				wp = Math.floorMod(wp + (objective != null && waypoints.contains(objective) ? 1 : 0), waypoints.size());
				objective = waypoints.get(wp);
			}
		} else if (home != null && (objective == null || objective.distToCenterSqr(pos) < 3.5 * 3.5)) {
			var r = level.getRandom();
			int x = home.getX() + r.nextInt(33) - 16, z = home.getZ() + r.nextInt(33) - 16;
			int y = level.isLoaded(new BlockPos(x, 0, z)) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : home.getY();
			objective = new BlockPos(x, y, z);
		}
		return false;
	}

	private void tickRoutine(ServerLevel level, Vec3 pos, long now) {
		if (routine == Routine.NONE) return;
		if (!enemies.isEmpty()) {
			if (restingNow) wake(level);
			return;
		}
		boolean night = updateRoutineObjective(level, pos, now);
		if (night && !restingNow && now >= noRestUntil && home != null && home.distToCenterSqr(pos) < 5 * 5) {
			restingNow = true;
			List<KingdomSoldier> mem = memberEntities(level);
			for (int i = 0; i < mem.size(); i += 2) mem.get(i).setResting(true); // half the squad sleeps, the rest keep watch
		} else if (!night && restingNow) {
			wake(level);
			if (home != null && level.getNearestPlayer(home.getX(), home.getY(), home.getZ(), 48, false) != null)
				level.playSound(null, home, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.5F, 1.0F);
		}
	}

	/** Morale regenerates out of combat (faster with a living standard-bearer in the squad). */
	private void tickMorale(ServerLevel level, List<KingdomSoldier> mem, long now) {
		if (!enemies.isEmpty() || morale >= 100 || (now + phase) % 100 != 0) return;
		boolean standard = false;
		for (KingdomSoldier s : mem) if (s.unit().ability() == UnitType.Ability.STANDARD) standard = true;
		morale = Math.min(100, morale + (standard ? 5 : 2));
	}

	/** Living soldiers of this squad (leader included if a soldier). */
	int livingCount(ServerLevel level) {
		return memberEntities(level).size() + (leaderEntity(level) != null ? 1 : 0);
	}

	/** 0 -> 0, 1 -> +w, 2 -> -w, 3 -> +2w ... */
	private static double slotLateral(int k, double w) {
		if (k == 0) return 0;
		int n = (k + 1) / 2;
		return (k % 2 == 1 ? 1 : -1) * n * w;
	}

	/** Promotes a new leader from the members (officers first, routed last). */
	void promote(ServerLevel level) {
		KingdomSoldier best = null;
		for (KingdomSoldier s : memberEntities(level)) {
			if (best == null || score(s) > score(best)) best = s;
		}
		leader = best != null ? best.getUUID() : null;
		if (best != null) members.remove(best.getUUID());
	}

	private static int score(KingdomSoldier s) {
		return (s.isOfficer() ? 4 : 0) + (s.isRouted() ? 0 : 2) + (s.role().ranged() ? 0 : 1);
	}
}
