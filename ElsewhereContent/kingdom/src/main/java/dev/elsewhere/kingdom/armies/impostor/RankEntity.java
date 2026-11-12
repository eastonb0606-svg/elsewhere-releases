package dev.elsewhere.kingdom.armies.impostor;

import java.util.ArrayDeque;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:army_rank}: a rank impostor (ARMIES.md 3.6). One cheap entity stands for up to 16 soldiers of one kingdom: the reserve
 * ranks behind a wave battle's front, or a battle squad folded at R3 (more than 104 blocks from every player). It has no AI, no physics and
 * no pathfinding; the server moves it in 10-tick steps along a straight line and the client dead-reckons between updates.
 * <p><b>Synced</b> (entity data, sent only when it changes): shape, state, slot count, the present-figure mask, formation width, spacing and
 * jitter, march speed, tabard and trim colours, up to 4 looks (unit skin + armor, baked by the client into one LOD skin) and the slot-to-look
 * map. Figures keep their slots, so a figure breaking out leaves its gap instead of shuffling the rank.
 * <p><b>Never invulnerable scenery:</b> a player's blade or arrow breaks the figure it hits out into a real soldier on the spot, who takes the
 * hit; NPC arrows and blasts wound figures (saved health, the dead stay dead). Never saved: chunk unload or server stop removes it, and the
 * heads it carried stay with their battle (reserve numbers or the virtual squad).
 */
public class RankEntity extends Entity {
	public enum State { MARCH, HOLD, FIGHT, BREAK, ROUT }

	/** What the rank stands for. */
	public enum Kind {
		/** Reserve heads of a wave battle side, behind the front. */
		RESERVE,
		/** A battle squad folded at R3 (its soldiers are stored as the squad's virtual record). */
		FOLDED,
		/** After the battle: walking off, scenery that still breaks out when hit. */
		LOOSE
	}

	private static final EntityDataAccessor<Byte> SHAPE = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> SLOTS = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> WIDTH = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> PRESENT = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LOOK_MAP = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TABARD = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TRIM = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SPACING = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> JITTER = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.FLOAT);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<String>[] LOOKS = new EntityDataAccessor[] {
		SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.STRING), SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.STRING),
		SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.STRING), SynchedEntityData.defineId(RankEntity.class, EntityDataSerializers.STRING)};

	/** Measured server cost of every rank's ticks (self-test, {@code /kingdom armies}). */
	public static long tickNanos, ticks;

	// ---- server state --------------------------------------------------------------------------------------------------------------
	/** The soldiers carried, by slot (null = empty slot). */
	final Figure[] figures = new Figure[RankLayout.MAX];
	public Identifier faction = dev.elsewhere.kingdom.Factions.ALDERMARK;
	public Kind kind = Kind.RESERVE;
	/** Who owns the bookkeeping of this rank's heads (the wave battle, the fold); null for loose scenery. */
	public @Nullable RankController controller;
	/** FOLDED: the virtual squad this rank shows. */
	public @Nullable UUID squad;
	/** Where the rank marches to (null = stand) and the heading it takes on arrival. */
	public @Nullable Vec3 goal;
	public float goalYaw;
	/** March speed in blocks per tick. */
	public float pace = 0.12F;
	/** Slots waiting to break out, one per tick. */
	final ArrayDeque<Integer> breaking = new ArrayDeque<>();
	/** The squad a break-out joins (created with its first soldier). */
	public @Nullable UUID breakSquad;
	/** Slots reserved for wounded men walking back (line relief). */
	int reserved;
	public long bornTick;
	final int phase;
	private final float[] layout = new float[2 * RankLayout.MAX];
	private int layoutKey = Integer.MIN_VALUE;

	// ---- client state --------------------------------------------------------------------------------------------------------------
	/** Client: ticks since the last position update (dead reckoning). */
	public int drTicks;
	private double lastX = Double.NaN, lastY, lastZ;
	/** Client: smoothed heading for drawing. */
	public float drawYaw, drawYawO;
	/** Client: ground height under each figure, relative to the entity, and when it was measured. */
	public final float[] groundDy = new float[RankLayout.MAX];
	private int groundAge = 1000;

	public RankEntity(EntityType<? extends RankEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.phase = Math.floorMod(getUUID().hashCode(), 10);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(SHAPE, (byte) 0);
		b.define(STATE, (byte) State.HOLD.ordinal());
		b.define(SLOTS, (byte) 0);
		b.define(WIDTH, (byte) 4);
		b.define(PRESENT, 0);
		b.define(LOOK_MAP, 0);
		b.define(TABARD, 0x2E4C9E);
		b.define(TRIM, 0xE8D9A0);
		b.define(SPACING, 1.25F);
		b.define(JITTER, 0F);
		b.define(SPEED, 0F);
		for (EntityDataAccessor<String> a : LOOKS) b.define(a, "");
	}

	// ---- synced accessors ----------------------------------------------------------------------------------------------------------

	public RankLayout.Shape shape() {
		return RankLayout.Shape.of(entityData.get(SHAPE));
	}

	public State state() {
		State[] v = State.values();
		return v[Math.floorMod(entityData.get(STATE), v.length)];
	}

	public int slots() {
		return Math.min(RankLayout.MAX, entityData.get(SLOTS));
	}

	public int width() {
		return entityData.get(WIDTH);
	}

	public float spacing() {
		return entityData.get(SPACING);
	}

	public float jitter() {
		return entityData.get(JITTER);
	}

	public float speed() {
		return entityData.get(SPEED);
	}

	public int presentMask() {
		return entityData.get(PRESENT);
	}

	public boolean present(int slot) {
		return (presentMask() >>> slot & 1) != 0;
	}

	/** Figures drawn right now. */
	public int count() {
		return Integer.bitCount(presentMask());
	}

	public int tabard() {
		return entityData.get(TABARD);
	}

	public int trim() {
		return entityData.get(TRIM);
	}

	/** Look of a slot (0-3), see {@link #look(int)}. */
	public int lookOf(int slot) {
		return entityData.get(LOOK_MAP) >>> (2 * slot) & 3;
	}

	/** Encoded look {@code skin;head;chest;legs;feet;dye} (see {@link Figure#look}). */
	public String look(int i) {
		return entityData.get(LOOKS[i & 3]);
	}

	/** A seed shared by server and client (jitter, walk phases): the UUID. */
	public long seed() {
		return getUUID().getMostSignificantBits() ^ getUUID().getLeastSignificantBits();
	}

	// ---- server setup --------------------------------------------------------------------------------------------------------------

	/** Sets the formation look (synced). */
	public void setFormation(RankLayout.Shape shape, int slots, int width, float spacing, float jitter) {
		entityData.set(SHAPE, (byte) shape.ordinal());
		entityData.set(SLOTS, (byte) Math.max(0, Math.min(RankLayout.MAX, slots)));
		entityData.set(WIDTH, (byte) Math.max(1, Math.min(RankLayout.MAX, width)));
		entityData.set(SPACING, spacing);
		entityData.set(JITTER, jitter);
		refreshBox();
	}

	public void setShape(RankLayout.Shape shape) {
		if (shape() != shape) {
			entityData.set(SHAPE, (byte) shape.ordinal());
			refreshBox();
		}
	}

	public void setState(State s) {
		if (state() != s) entityData.set(STATE, (byte) s.ordinal());
	}

	public void setColours(int tabard, int trim) {
		entityData.set(TABARD, tabard);
		entityData.set(TRIM, trim);
	}

	/** Puts a figure into a free slot with the given look string; returns the slot or -1 when full. Up to 4 looks per rank (a 5th reuses the 1st). */
	public int addFigure(Figure f, String look) {
		int slot = -1;
		for (int i = 0; i < slots(); i++) if (figures[i] == null) {
			slot = i;
			break;
		}
		if (slot < 0) return -1;
		int li = -1;
		for (int i = 0; i < 4; i++) {
			String l = look(i);
			if (l.equals(look)) {
				li = i;
				break;
			}
			if (l.isEmpty()) {
				entityData.set(LOOKS[i], look);
				li = i;
				break;
			}
		}
		if (li < 0) li = 0;
		f.look = li;
		figures[slot] = f;
		int map = entityData.get(LOOK_MAP) & ~(3 << (2 * slot)) | li << (2 * slot);
		entityData.set(LOOK_MAP, map);
		entityData.set(PRESENT, presentMask() | 1 << slot);
		return slot;
	}

	/** Takes the figure out of a slot (it broke out, died or left); empty ranks are removed. */
	public @Nullable Figure removeFigure(int slot) {
		Figure f = slot >= 0 && slot < RankLayout.MAX ? figures[slot] : null;
		if (f == null) return null;
		figures[slot] = null;
		entityData.set(PRESENT, presentMask() & ~(1 << slot));
		return f;
	}

	public @Nullable Figure figure(int slot) {
		return slot >= 0 && slot < RankLayout.MAX ? figures[slot] : null;
	}

	/** Free slots (counting slots reserved for men walking back). */
	public int freeSlots() {
		int n = 0;
		for (int i = 0; i < slots(); i++) if (figures[i] == null) n++;
		return Math.max(0, n - reserved);
	}

	/** Holds ({@code +1}) or frees ({@code -1}) a slot for a wounded man walking back to this rank. */
	public void reserveSlot(int delta) {
		reserved = Math.max(0, reserved + delta);
		if (delta < 0 && count() == 0 && reserved == 0 && kind == Kind.RESERVE && !level().isClientSide()) discard();
	}

	public int reservedSlots() {
		return reserved;
	}

	/** Figures not already committed to a break-out. */
	public int available() {
		return count() - breaking.size();
	}

	public int queued() {
		return breaking.size();
	}

	/** Queues up to {@code n} figures to break out, front-most first; returns how many were queued. */
	public int queueBreakOut(int n) {
		layout();
		int done = 0;
		while (done < n) {
			int best = -1;
			for (int i = 0; i < slots(); i++) {
				if (figures[i] == null || breaking.contains(i)) continue;
				if (best < 0 || layout[2 * i + 1] > layout[2 * best + 1]) best = i;
			}
			if (best < 0) break;
			breaking.add(best);
			done++;
		}
		if (done > 0) setState(State.BREAK);
		return done;
	}

	public void cancelBreakOut() {
		breaking.clear();
		if (state() == State.BREAK) setState(State.HOLD);
	}

	/** The local layout (across, along per slot), recomputed only when the shape changes. */
	public float[] layout() {
		long sd = seed();
		int key = (((entityData.get(SHAPE) * 31 + slots()) * 31 + width()) * 31 + Float.floatToIntBits(spacing()) * 7 + Float.floatToIntBits(jitter())) * 31
			+ (int) (sd ^ sd >>> 32); // the client learns the UUID (the jitter seed) only after construction
		if (key != layoutKey) {
			layoutKey = key;
			RankLayout.offsets(shape(), slots(), width(), spacing(), jitter(), seed(), layout);
		}
		return layout;
	}

	/** World position of a slot's figure (feet at the rank's height; the server snaps it to the ground when it breaks out). */
	public Vec3 slotPos(int slot, float yaw) {
		float[] l = layout();
		return new Vec3(getX() + RankLayout.worldX(l[2 * slot], l[2 * slot + 1], yaw), getY(), getZ() + RankLayout.worldZ(l[2 * slot], l[2 * slot + 1], yaw));
	}

	/** The present slot nearest to a point (horizontal), or -1. */
	public int nearestSlot(Vec3 p, double maxDist) {
		int best = -1;
		double bd = maxDist * maxDist;
		for (int i = 0; i < slots(); i++) {
			if (!present(i)) continue;
			Vec3 s = slotPos(i, getYRot());
			double dx = s.x - p.x, dz = s.z - p.z, d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	/** The present slot whose figure (a 0.6 x 1.8 body) lies nearest a ray, within {@code tolerance}; -1 if none. */
	public int slotOnRay(Vec3 from, Vec3 dir, double maxLen, double tolerance) {
		int best = -1;
		double bd = tolerance * tolerance;
		Vec3 d = dir.normalize();
		for (int i = 0; i < slots(); i++) {
			if (!present(i)) continue;
			Vec3 s = slotPos(i, getYRot()).add(0, 0.9, 0);
			double t = Mth.clamp(s.subtract(from).dot(d), 0, maxLen);
			Vec3 c = from.add(d.scale(t));
			double dx = c.x - s.x, dz = c.z - s.z, dy = Math.max(0, Math.abs(c.y - s.y) - 0.9);
			double dist = dx * dx + dz * dz + dy * dy;
			if (dist < bd) {
				bd = dist;
				best = i;
			}
		}
		return best;
	}

	// ---- bounding box: the whole formation, so blades and arrows can find a figure; no collisions ---------------------------------------

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		if (layout == null || entityData == null) return super.makeBoundingBox(pos); // during the super constructor
		int n = slots();
		if (n == 0) return super.makeBoundingBox(pos);
		float[] l = layout();
		float yaw = getYRot();
		double minX = 0, maxX = 0, minZ = 0, maxZ = 0;
		for (int i = 0; i < n; i++) {
			double x = RankLayout.worldX(l[2 * i], l[2 * i + 1], yaw), z = RankLayout.worldZ(l[2 * i], l[2 * i + 1], yaw);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		return new AABB(pos.x + minX - 0.4, pos.y - 0.5, pos.z + minZ - 0.4, pos.x + maxX + 0.4, pos.y + 2.0, pos.z + maxZ + 0.4);
	}

	void refreshBox() {
		setBoundingBox(makeBoundingBox(position()));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (key == SHAPE || key == SLOTS || key == WIDTH || key == SPACING || key == JITTER) {
			if (layout != null) refreshBox();
			groundAge = 1000;
		}
	}

	// ---- ticking: no AI, no physics; a 10-tick march step on the server --------------------------------------------------------------

	@Override
	public void tick() {
		// deliberately no super.tick(): no fluid, fire, portal or block-inside checks over a formation-sized box
		if (level() instanceof ServerLevel sl) {
			long t0 = System.nanoTime();
			try {
				serverTick(sl);
			} finally {
				tickNanos += System.nanoTime() - t0;
				ticks++;
			}
		} else {
			clientTick();
		}
	}

	private void serverTick(ServerLevel sl) {
		if (!breaking.isEmpty()) Ranks.breakOutNext(sl, this);
		if (isRemoved() || (tickCount + phase) % 10 != 0) return;
		step(sl);
		if (!isRemoved() && (tickCount + phase) % 20 == 0) {
			if (controller != null) controller.slowTick(sl, this);
			else Ranks.looseTick(sl, this);
		}
	}

	/** One march step (every 10 ticks): straight toward the goal at {@link #pace}, turning at most 45 degrees a step. */
	void step(ServerLevel sl) {
		float speed = 0;
		if (goal != null) {
			double dx = goal.x - getX(), dz = goal.z - getZ(), d = Math.sqrt(dx * dx + dz * dz);
			if (d > 0.3) {
				float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float yaw = getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -45F, 45F);
				double len = Math.min(d, pace * 10);
				double nx = getX() + dx / d * len, nz = getZ() + dz / d * len;
				BlockPos col = BlockPos.containing(nx, getY(), nz);
				if (!sl.isLoaded(col) || !sl.isPositionEntityTicking(col)) {
					// battles never force-load chunks: a rank reaching the edge of the loaded area becomes abstract
					Ranks.abstractRank(sl, this);
					return;
				}
				int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
				BlockPos under = new BlockPos(col.getX(), gy - 1, col.getZ());
				if (!sl.getFluidState(under).isEmpty() || Math.abs(gy - getY()) > 6) {
					goal = null; // never into water or up a cliff: hold here
				} else {
					setYRot(yaw);
					setPos(nx, gy, nz);
					speed = (float) (len / 10.0);
				}
			} else {
				goal = null;
				setYRot(goalYaw);
				refreshBox();
			}
		}
		if (entityData.get(SPEED) != speed) entityData.set(SPEED, speed);
		if (goal == null && (state() == State.MARCH)) setState(State.HOLD);
	}

	/** Marches to {@code to}, facing {@code yaw} on arrival. */
	public void marchTo(Vec3 to, float yaw) {
		this.goal = to;
		this.goalYaw = yaw;
		State s = state();
		if (s == State.HOLD) setState(State.MARCH);
	}

	private void clientTick() {
		if (Double.isNaN(lastX) || getX() != lastX || getY() != lastY || getZ() != lastZ) {
			lastX = getX();
			lastY = getY();
			lastZ = getZ();
			drTicks = 0;
		} else if (drTicks < 12) {
			drTicks++;
		}
		drawYawO = drawYaw;
		if (tickCount <= 1) drawYawO = drawYaw = getYRot();
		else drawYaw = drawYaw + Mth.wrapDegrees(getYRot() - drawYaw) * 0.25F;
		boolean moving = speed() > 0;
		if (++groundAge >= (moving ? 2 : 20)) {
			groundAge = 0;
			fitGround();
		}
	}

	/** Dead-reckoned offset along the heading (client): speed x ticks since the last update, at most one 10-tick step. */
	public double reckon(float partial) {
		return speed() * Math.min(10F, drTicks + partial);
	}

	/** Client: stand every figure on the ground under it (scans a few blocks around the rank's height), so nobody floats or sinks. */
	private void fitGround() {
		Level lv = level();
		float[] l = layout();
		double r = reckon(0);
		double fx = -Math.sin(Math.toRadians(getYRot())) * r, fz = Math.cos(Math.toRadians(getYRot())) * r;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int baseY = Mth.floor(getY());
		for (int i = 0; i < slots(); i++) {
			double x = getX() + fx + RankLayout.worldX(l[2 * i], l[2 * i + 1], drawYaw), z = getZ() + fz + RankLayout.worldZ(l[2 * i], l[2 * i + 1], drawYaw);
			float dy = 0;
			for (int y = baseY + 2; y >= baseY - 4; y--) {
				m.set(Mth.floor(x), y, Mth.floor(z));
				net.minecraft.world.level.block.state.BlockState st = lv.getBlockState(m);
				if (!st.isAir() && !st.getCollisionShape(lv, m).isEmpty()) {
					dy = (float) (y + 1 - getY());
					break;
				}
			}
			groundDy[i] = dy;
		}
	}

	// ---- players: hitting a figure breaks it out; nothing else interacts ------------------------------------------------------------

	@Override
	public boolean isPickable() {
		return !isRemoved() && presentMask() != 0;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return isPickable();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return Ranks.hurt(level, this, source, amount);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		double r = 192 * getViewScale();
		return d < r * r;
	}

	// ---- never saved -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean save(ValueOutput out) {
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

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (level() instanceof ServerLevel sl) Ranks.onRemoved(sl, this);
	}
}
