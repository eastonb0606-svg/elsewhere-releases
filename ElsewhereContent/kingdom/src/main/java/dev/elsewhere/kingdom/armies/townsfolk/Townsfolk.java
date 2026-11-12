package dev.elsewhere.kingdom.armies.townsfolk;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:townsfolk} (ARMIES.md 2.5): a cheap town resident of one kingdom and one role (Aldermark peasants and craftsmen,
 * Hrafnholt thralls and fisherfolk, Azmar priests, scribes, porters and merchants, Mirewick clerks, bargemen, lamplighters and
 * peat-cutters). Not a villager: no trades, brain, POI search, gossip or breeding. It exists only near a player
 * ({@link TownsfolkManager} spawns it from its town's logical count and folds it back when nobody is near) and is never saved to
 * disk: the town's logical count is the save.
 * <p>AI: {@link ScheduleGoal} walks the town's precomputed {@link StreetGraph} to the place of its current activity (set only at
 * hour changes and events, never polled), mills about there and takes the activity's pose; a Serf-style zigzag panic when hurt (its
 * neighbours flee too); a look at the nearest player at most once a second. Pushing is evaluated every 4th tick.
 */
public class Townsfolk extends PathfinderMob implements dev.elsewhere.kingdom.idle.IdlePoses.Posable {
	private static final EntityDataAccessor<String> DATA_LOOK = SynchedEntityData.defineId(Townsfolk.class, EntityDataSerializers.STRING);
	/** 3.0 P9 (s-p9-idle): the routine pose id, sent only when it changes; animated by the client (IdleAnim). */
	private static final EntityDataAccessor<Byte> DATA_IDLE = SynchedEntityData.defineId(Townsfolk.class, EntityDataSerializers.BYTE);
	public static final String DEFAULT_LOOK = "aldermark/peasant/0";

	/** Town key ({@link Towns.Town#key}), empty for a summoned stray. */
	public String townKey = "";
	public Identifier faction = dev.elsewhere.kingdom.Factions.ALDERMARK;
	public String role = "peasant";

	// ---- the plan (set by TownsfolkManager on spawn, at hour changes and events) ----
	/** Where the current activity is (null = no plan, mill about). */
	public @Nullable BlockPos target;
	/** Remaining street-graph waypoints to the target (positions), index of the next one. */
	@Nullable BlockPos[] waypoints;
	int nextWaypoint;
	/** Activity id and the routine pose name to take on arrival ({@code NONE} for none). */
	public String activity = "", pose = "NONE";
	/** True: walking indoors (home, sleep, siesta, folded by the LOD); vanishes on arrival or when nobody is near. */
	public boolean goingHome;
	boolean arrived, poseSet;
	int panicTicks;
	@Nullable Vec3 panicFrom;
	/** Diagnostics for the self-test: plans received. */
	public int plans;

	public Townsfolk(EntityType<? extends Townsfolk> type, Level level) {
		super(type, level);
		setPersistenceRequired(); // skips Mob.checkDespawn's nearest-player lookup every tick; the manager folds us instead
	}

	public static AttributeSupplier.Builder createAttributes() {
		// human health below a Serf (18): townsfolk are not fighters
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_LOOK, DEFAULT_LOOK);
		b.define(DATA_IDLE, (byte) 0);
	}

	/** {@code "<kingdom>/<role>/<variant>"}, e.g. {@code mirewick/peat_cutter/2} (TOWNSFOLK-IDS.md). */
	public String look() {
		return entityData.get(DATA_LOOK);
	}

	/** 3.0 P9: the synced routine pose id (RoutinePoses.Pose id; 0 = none). */
	@Override
	public int idlePose() {
		return entityData.get(DATA_IDLE);
	}

	@Override
	public void setIdlePose(int pose) {
		entityData.set(DATA_IDLE, (byte) pose);
	}

	/** Texture of a look: {@code elsewhere:textures/entity/townsfolk/<kingdom>/<role>_<variant>.png}. */
	public static Identifier skinPath(String look) {
		String[] p = look.split("/");
		if (p.length != 3) p = DEFAULT_LOOK.split("/");
		return Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/townsfolk/" + p[0] + "/" + p[1] + "_" + p[2] + ".png");
	}

	/** Sets kingdom, role, skin variant and the role's held item (drop chance 0: townsfolk drop nothing). */
	public void setup(String townKey, Identifier faction, TownsfolkRoles.Role r, int variant) {
		this.townKey = townKey;
		this.faction = faction;
		this.role = r.id();
		entityData.set(DATA_LOOK, faction.getPath() + "/" + r.id() + "/" + Math.floorMod(variant, TownsfolkRoles.VARIANTS));
		ItemStack held = r.held();
		setItemSlot(EquipmentSlot.MAINHAND, held.isEmpty() ? ItemStack.EMPTY : held.copy());
		for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 0F);
	}

	/** New plan from the manager: walk the waypoints to {@code to}, then take {@code pose}. */
	public void plan(@Nullable BlockPos to, @Nullable BlockPos[] path, String activity, String pose, boolean home) {
		this.target = to;
		this.waypoints = path;
		this.nextWaypoint = 0;
		this.activity = activity;
		this.pose = pose == null ? "NONE" : pose;
		this.goingHome = home;
		this.arrived = false;
		if (poseSet) clearPose();
		plans++;
		getNavigation().stop();
	}

	void clearPose() {
		poseSet = false;
		TownsfolkRoutineLink.setPose(this, "NONE");
	}

	public boolean panicking() {
		return panicTicks > 0;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this));
		this.goalSelector.addGoal(3, new ScheduleGoal(this));
		this.goalSelector.addGoal(7, new GlanceGoal(this));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && isAlive()) {
			Entity attacker = source.getEntity();
			Vec3 from = attacker != null ? attacker.position() : source.getSourcePosition();
			startPanic(from == null ? position() : from);
			// Serf-style: the neighbours who saw it run too (the town's live list, not an entity scan)
			TownsfolkManager.alertNeighbours(this, from == null ? position() : from);
		}
		return hurt;
	}

	/** Runs away from {@code from} in zigzags for 6-9 seconds. */
	public void startPanic(Vec3 from) {
		panicTicks = 120 + getRandom().nextInt(60);
		panicFrom = from;
		if (poseSet) clearPose();
		getNavigation().stop();
	}

	@Override
	public void die(DamageSource source) {
		boolean was = !isRemoved() && !dead;
		super.die(source);
		if (was && level() instanceof ServerLevel sl) TownsfolkManager.onDeath(sl, this, source);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) TownsfolkManager.onRemoved(this);
		super.remove(reason);
	}

	/** Never written to disk: the logical count in {@link TownsfolkState} is the save, the manager respawns them near players. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** Not a villager: no trades, no menus. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!level().isClientSide()) getLookControl().setLookAt(player, 30F, 30F);
		return InteractionResult.PASS;
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.elsewhere.townsfolk." + role);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null; // a crowd of 48 humming residents would drown the town; their bustle is the stalls' and the crowds' sounds
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	/** Pushing in a crowd is an entity query per mob; townsfolk do it every 4th tick (the vanilla call stays for everyone else). */
	@Override
	protected void pushEntities() {
		if ((tickCount + getId()) % 4 == 0) super.pushEntities();
	}

	// ---- goals ----------------------------------------------------------------------------------------------------------------

	/** Serf-style panic: zigzag runs away from the attacker, a new leg every 1-2 s, for 6-9 s; then the plan resumes. */
	static final class PanicGoal extends Goal {
		private final Townsfolk mob;
		private int leg;

		PanicGoal(Townsfolk mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return mob.panicTicks > 0;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.panicTicks > 0;
		}

		@Override
		public void start() {
			leg = 0;
		}

		@Override
		public void stop() {
			mob.panicTicks = 0;
			mob.panicFrom = null;
			mob.arrived = false; // walk back to the plan
			if (mob.waypoints != null) mob.nextWaypoint = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			mob.panicTicks--;
			if (--leg > 0 && !mob.getNavigation().isDone()) return;
			leg = 20 + mob.getRandom().nextInt(20);
			Vec3 from = mob.panicFrom != null ? mob.panicFrom : mob.position();
			Vec3 to = DefaultRandomPos.getPosAway(mob, 12, 5, from);
			if (to != null) mob.getNavigation().moveTo(to.x, to.y, to.z, 1.45);
		}
	}

	/**
	 * Walks the plan: street-graph waypoints with short vanilla paths between them (node to node, at most about 16 blocks), then the
	 * target. On arrival it takes the pose and mills about within 3 blocks every 10-20 s. Going home: vanishes at the door.
	 */
	static final class ScheduleGoal extends Goal {
		private final Townsfolk mob;
		private int cooldown, stuck;

		ScheduleGoal(Townsfolk mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return mob.target != null && mob.panicTicks <= 0;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (--cooldown > 0) return;
			cooldown = 10; // decisions twice a second; vanilla navigation does the walking in between
			BlockPos t = mob.target;
			if (t == null) return;
			if (!mob.arrived) {
				BlockPos next = mob.waypoints != null && mob.nextWaypoint < mob.waypoints.length ? mob.waypoints[mob.nextWaypoint] : t;
				double d2 = mob.distanceToSqr(Vec3.atBottomCenterOf(next));
				if (d2 < 2.5 * 2.5) {
					if (next != t && mob.waypoints != null && mob.nextWaypoint < mob.waypoints.length) mob.nextWaypoint++;
					else arrive();
					stuck = 0;
					return;
				}
				if (mob.getNavigation().isDone()) {
					boolean ok = mob.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, mob.goingHome ? 0.65 : 0.6);
					if (!ok || ++stuck > 12) {
						// unreachable hop: skip it (or, at the last one, settle where we are)
						stuck = 0;
						if (mob.waypoints != null && mob.nextWaypoint < mob.waypoints.length) mob.nextWaypoint++;
						else arrive();
					}
				}
				return;
			}
			// arrived: mill about, cheaply
			if (mob.getNavigation().isDone() && mob.getRandom().nextInt(3) == 0) {
				cooldown = 200 + mob.getRandom().nextInt(200);
				int dx = mob.getRandom().nextInt(7) - 3, dz = mob.getRandom().nextInt(7) - 3;
				if (mob.poseSet && !"NONE".equals(mob.pose)) return; // working, praying, drinking: stay put
				mob.getNavigation().moveTo(t.getX() + 0.5 + dx, t.getY(), t.getZ() + 0.5 + dz, 0.45);
			}
		}

		private void arrive() {
			mob.arrived = true;
			mob.getNavigation().stop();
			if (mob.goingHome) {
				TownsfolkManager.foldIn(mob, "home");
				return;
			}
			if (!"NONE".equals(mob.pose)) {
				mob.poseSet = TownsfolkRoutineLink.setPose(mob, mob.pose);
			}
		}
	}

	/** Looks at the nearest player within 8 blocks for 2-4 s; the lookup runs at most once a second (staggered by entity id). */
	static final class GlanceGoal extends Goal {
		private final Townsfolk mob;
		private @Nullable Player at;
		private int time;

		GlanceGoal(Townsfolk mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if ((mob.tickCount + mob.getId()) % 20 != 0 || mob.panicTicks > 0) return false;
			at = mob.level().getNearestPlayer(mob, 8.0);
			return at != null && !at.isSpectator() && mob.getRandom().nextInt(3) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return at != null && at.isAlive() && time > 0 && mob.distanceToSqr(at) < 100;
		}

		@Override
		public void start() {
			time = 40 + mob.getRandom().nextInt(40);
		}

		@Override
		public void stop() {
			at = null;
		}

		@Override
		public void tick() {
			time--;
			if (at != null) mob.getLookControl().setLookAt(at.getX(), at.getEyeY(), at.getZ());
		}
	}

	/** True when {@code e} is a living townsperson (helper for other lanes, e.g. crowd clusters). */
	public static boolean is(@Nullable LivingEntity e) {
		return e instanceof Townsfolk t && t.isAlive();
	}
}
