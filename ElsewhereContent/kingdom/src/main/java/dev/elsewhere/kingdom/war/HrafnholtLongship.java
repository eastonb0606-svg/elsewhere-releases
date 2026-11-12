package dev.elsewhere.kingdom.war;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:hrafnholt_longship}: the sailing longship of a Hrafnholt raid (RIVALS-DECISIONS 18). Built from block models ({@link ShipShape}, styled
 * by the castle file's {@code waterfront.longship}), it carries up to 8 raiders seated on its benches. The server only steps it along a precomputed water route
 * at boat speed ({@link #SPEED}); it never scans or pathfinds. Players cannot ride, steal or break it (it takes no damage, is not pickable and only takes
 * kingdom soldiers as passengers); it can be pushed, and projectiles pass it and hit the raiders. It drops nothing, is never saved (chunk unload or server
 * stop removes it with its crew) and is gone after landing or after {@link #LIFETIME} ticks.
 */
public class HrafnholtLongship extends Entity {
	/** Boat speed on water (the vanilla boat's top speed, blocks per tick). */
	public static final double SPEED = 0.36;
	public static final int LIFETIME = 12000, HOP_TICKS = 4;
	static final float TURN = 4.5F;
	private static final EntityDataAccessor<BlockState> HULL = def(), KEEL = def(), STEM = def(), TRIM = def(), BENCH = def(), MAST = def(), RAIL = def(), SAIL = def(),
		SAIL_ALT = def(), SHIELD = def(), SHIELD_ALT = def();
	/** Measured cost of the server-side ticks (tests and the lag report). */
	public static long tickNanos, ticks;

	private static EntityDataAccessor<BlockState> def() {
		return SynchedEntityData.defineId(HrafnholtLongship.class, EntityDataSerializers.BLOCK_STATE);
	}

	private @Nullable List<Vec3> path;
	private int next;
	private boolean beached;
	private int hopTimer;
	/** The heading the ship must have when it beaches (the shore axis). */
	private float beachYaw;

	public HrafnholtLongship(EntityType<? extends HrafnholtLongship> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		Longship.Style d = Longship.Style.defaults();
		b.define(HULL, d.hull().defaultBlockState());
		b.define(KEEL, d.keel().defaultBlockState());
		b.define(STEM, d.stem().defaultBlockState());
		b.define(TRIM, d.trim().defaultBlockState());
		b.define(BENCH, d.bench().defaultBlockState());
		b.define(MAST, d.mast().defaultBlockState());
		b.define(RAIL, d.rail().defaultBlockState());
		b.define(SAIL, d.sail().defaultBlockState());
		b.define(SAIL_ALT, d.sailAlt().defaultBlockState());
		b.define(SHIELD, d.shield().defaultBlockState());
		b.define(SHIELD_ALT, d.shieldAlt().defaultBlockState());
	}

	/** Sets the block style (synced to clients). */
	public void setStyle(Longship.Style s) {
		entityData.set(HULL, s.hull().defaultBlockState());
		entityData.set(KEEL, s.keel().defaultBlockState());
		entityData.set(STEM, s.stem().defaultBlockState());
		entityData.set(TRIM, s.trim().defaultBlockState());
		entityData.set(BENCH, s.bench().defaultBlockState());
		entityData.set(MAST, s.mast().defaultBlockState());
		entityData.set(RAIL, s.rail().defaultBlockState());
		entityData.set(SAIL, s.sail().defaultBlockState());
		entityData.set(SAIL_ALT, s.sailAlt().defaultBlockState());
		entityData.set(SHIELD, s.shield().defaultBlockState());
		entityData.set(SHIELD_ALT, s.shieldAlt().defaultBlockState());
	}

	/** The synced style (client renderer). */
	public Longship.Style style() {
		return new Longship.Style(entityData.get(HULL).getBlock(), entityData.get(KEEL).getBlock(), entityData.get(RAIL).getBlock(), entityData.get(MAST).getBlock(),
			entityData.get(SAIL).getBlock(), entityData.get(SAIL_ALT).getBlock(), entityData.get(TRIM).getBlock(), entityData.get(STEM).getBlock(), entityData.get(BENCH).getBlock(),
			entityData.get(SHIELD).getBlock(), entityData.get(SHIELD_ALT).getBlock());
	}

	/** Puts the ship at the start of {@code route} facing along it; it ends beached facing {@code endYaw}. */
	public void sail(List<Vec3> route, float endYaw) {
		this.path = List.copyOf(route);
		this.next = route.size() > 1 ? 1 : 0;
		this.beachYaw = endYaw;
		Vec3 s = route.get(0);
		float yaw = route.size() > 1 ? ShipShape.yaw(route.get(1).x - s.x, route.get(1).z - s.z) : endYaw;
		snapTo(s.x, s.y, s.z, yaw, 0);
		setYRot(yaw);
		yRotO = yaw;
	}

	public @Nullable List<Vec3> route() {
		return path;
	}

	public boolean isBeached() {
		return beached;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) return;
		long t0 = System.nanoTime();
		try {
			if (tickCount > LIFETIME) {
				Longship.lost(sl, this);
				return;
			}
			if (beached) {
				if (--hopTimer <= 0) {
					hopTimer = HOP_TICKS;
					if (!Longship.hopOff(sl, this)) Longship.landed(sl, this);
				}
				return;
			}
			if (path != null) move();
		} finally {
			tickNanos += System.nanoTime() - t0;
			ticks++;
		}
	}

	/** One step along the route: no scans, no pathfinding. External pushes (players, currents) nudge it; it then steers back to the route. */
	private void move() {
		Vec3 push = getDeltaMovement();
		setDeltaMovement(Vec3.ZERO);
		double px = Mth.clamp(push.x, -0.1, 0.1), pz = Mth.clamp(push.z, -0.1, 0.1);
		double x = getX() + px, z = getZ() + pz;
		double left = SPEED;
		Vec3 tgt = path.get(next);
		double dx = tgt.x - x, dz = tgt.z - z, d = Math.sqrt(dx * dx + dz * dz);
		boolean last = next == path.size() - 1;
		if (last) left = Math.min(SPEED, 0.08 + d * 0.06); // glide onto the beach
		float want = d > 0.05 ? ShipShape.yaw(dx, dz) : getYRot();
		if (last && d < 2.5) want = beachYaw;
		float yaw = getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -TURN, TURN);
		if (d <= left) {
			x = tgt.x;
			z = tgt.z;
			if (last) {
				snapTo(x, tgt.y, z, beachYaw, 0);
				setYRot(beachYaw);
				beached = true;
				hopTimer = HOP_TICKS * 2;
				return;
			}
			next++;
		} else {
			x += dx / d * left;
			z += dz / d * left;
		}
		double y = getY() + Mth.clamp(tgt.y - getY(), -0.05, 0.05);
		setPos(x, y, z);
		setYRot(yaw);
	}

	// ---- seats ------------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity e) {
		return e instanceof KingdomSoldier && getPassengers().size() < ShipShape.CAPACITY;
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction move) {
		int i = getPassengers().indexOf(passenger);
		if (i < 0) return;
		int[] s = ShipShape.SEATS[Math.min(i, ShipShape.CAPACITY - 1)];
		Vec3 w = local(s[0], s[1]);
		move.accept(passenger, getX() + w.x, getY() + 0.75, getZ() + w.z);
		if (passenger instanceof LivingEntity le) le.setYBodyRot(getYRot());
	}

	/** World offset of local (across, along) for the current heading. */
	public Vec3 local(double x, double z) {
		double a = Math.toRadians(getYRot()), c = Math.cos(a), s = Math.sin(a);
		return new Vec3(x * c - z * s, 0, x * s + z * c);
	}

	@Override
	public boolean dismountsUnderwater() {
		return false;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return position().add(0, 1.5, 0);
	}

	// ---- players: no riding, no breaking, no stealing; pushing and shooting the crew work -----------------------------------------

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean skipAttackInteraction(Entity attacker) {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !beached;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	/** Pushed like a boat even with a crew aboard (vanilla never pushes a vehicle). */
	@Override
	public void push(Entity other) {
		if (beached || other.isPassenger() && other.getVehicle() == this || hasPassenger(other) || other.noPhysics) return;
		double dx = getX() - other.getX(), dz = getZ() - other.getZ(), m = Math.max(Math.abs(dx), Math.abs(dz));
		if (m < 0.01) return;
		m = Math.sqrt(m);
		double f = Math.min(1.0, 1.0 / m) * 0.05;
		push(dx / m * f, 0, dz / m * f);
	}

	// ---- never saved: chunk unload or server stop removes the ship with its crew --------------------------------------------------

	@Override
	public boolean save(ValueOutput out) {
		return false;
	}

	@Override
	public boolean saveAsPassenger(ValueOutput out) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {}

	/** Any removal (landing, expiry, chunk unload, server stop) closes the voyage; an unsaved ship's crew is never saved either. */
	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (level() instanceof ServerLevel sl) Longship.onShipRemoved(sl, this, reason);
	}
}
