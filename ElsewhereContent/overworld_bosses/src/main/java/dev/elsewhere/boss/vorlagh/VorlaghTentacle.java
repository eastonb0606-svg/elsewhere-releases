package dev.elsewhere.boss.vorlagh;

import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossPart;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import dev.elsewhere.stub.overworld_bosses.Telegraph;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:vorlagh_tentacle}: one of Vorlagh's five tentacles, a child part with its own hitbox and 60 base
 * HP (x1.53 = 92 under Test Meter). Positioned by the body; runs Slam (1.2 s raise with a dust line, then the deck
 * breaks along it) and Grab (the tip glows cyan, then holds a player and drags them toward the beak; any hit on the
 * tentacle frees them, else the beak bites after 4 s). At 0 HP it is severed (a stump) until it regrows.
 */
public class VorlaghTentacle extends Mob implements BossPart, Enemy {
	public static final int STATE_IDLE = 0, STATE_RAISE = 1, STATE_SLAM = 2, STATE_GLOW = 3, STATE_GRAB = 4;
	public static final int SLAM_WINDUP = 24;
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(VorlaghTentacle.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SEVERED = SynchedEntityData.defineId(VorlaghTentacle.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(VorlaghTentacle.class, EntityDataSerializers.INT);

	public final AnimationState slamAnim = new AnimationState();
	public final AnimationState grabAnim = new AnimationState();
	private @Nullable VorlaghEntity body;
	private int index;
	private int stateTicks;
	private Vec3 slamDir = new Vec3(0, 0, 1);
	private float slamDamage = 44.0F;
	private @Nullable UUID grabbed;

	public VorlaghTentacle(EntityType<? extends VorlaghTentacle> type, Level level) {
		super(type, level);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ARMOR, 10.0).add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
		builder.define(DATA_SEVERED, false);
		builder.define(DATA_INDEX, 0);
	}

	public void bind(VorlaghEntity body, int index) {
		this.body = body;
		this.index = index;
		this.entityData.set(DATA_INDEX, index);
	}

	public int index() {
		return this.entityData.get(DATA_INDEX);
	}

	@Override
	public @Nullable ElsewhereBoss parentBoss() {
		return this.body;
	}

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	public boolean isSevered() {
		return this.entityData.get(DATA_SEVERED);
	}

	public boolean isIdle() {
		return state() == STATE_IDLE && this.stateTicks > 30;
	}

	public boolean isGrabbing() {
		return this.grabbed != null || state() == STATE_GLOW;
	}

	private void setState(int s) {
		this.entityData.set(DATA_STATE, s);
		this.stateTicks = 0;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_STATE.equals(accessor)) {
			int s = state();
			if (s == STATE_RAISE) this.slamAnim.start(this.tickCount);
			else if (s == STATE_IDLE) this.slamAnim.stop();
			if (s == STATE_GLOW) this.grabAnim.start(this.tickCount);
			else if (s == STATE_IDLE) this.grabAnim.stop();
		}
	}

	public void startSlam(LivingEntity target, float damage) {
		this.slamDir = Geo.flatDir(position(), target.position());
		this.slamDamage = damage;
		Geo.face(this, this.slamDir);
		setState(STATE_RAISE);
		playSound(SoundEvents.ELDER_GUARDIAN_FLOP, 2.0F, 0.6F);
	}

	public void startGrab(ServerPlayer target) {
		this.slamDir = Geo.flatDir(position(), target.position());
		Geo.face(this, this.slamDir);
		this.grabbed = target.getUUID();
		setState(STATE_GLOW);
	}

	public void releaseGrab() {
		this.grabbed = null;
		if (state() == STATE_GRAB || state() == STATE_GLOW) setState(STATE_IDLE);
	}

	public void regrow() {
		this.entityData.set(DATA_SEVERED, false);
		setHealth(getMaxHealth());
		setState(STATE_IDLE);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) return;
		if (this.body == null || !this.body.isAlive()) {
			discard();
			return;
		}
		this.stateTicks++;
		Vec3 a = this.body.anchor(this.index);
		setPos(a);
		setDeltaMovement(Vec3.ZERO);
		if (isSevered()) return;
		switch (state()) {
			case STATE_RAISE -> {
				Geo.face(this, this.slamDir);
				if (this.stateTicks % 3 == 0) Telegraph.line(sl, deckPoint(1.5), deckPoint(14.5), 1.6, Telegraph.RED);
				if (this.stateTicks >= SLAM_WINDUP) slam(sl);
			}
			case STATE_SLAM -> {
				if (this.stateTicks >= 10) setState(STATE_IDLE);
			}
			case STATE_GLOW -> {
				ServerPlayer p = grabbedPlayer(sl);
				if (p == null) {
					releaseGrab();
					return;
				}
				this.slamDir = Geo.flatDir(position(), p.position());
				Geo.face(this, this.slamDir);
				if (this.stateTicks % 3 == 0) sl.sendParticles(ParticleTypes.GLOW, getX() + this.slamDir.x * 2, getY() + 7, getZ() + this.slamDir.z * 2, 4, 0.3, 0.3, 0.3, 0.0);
				if (this.stateTicks >= 20) {
					if (p.distanceTo(this) <= 15 && this.body.getArena() != null && this.body.getArena().contains(p)) {
						setState(STATE_GRAB);
						playSound(SoundEvents.SLIME_SQUISH, 2.0F, 0.5F);
					} else {
						releaseGrab();
					}
				}
			}
			case STATE_GRAB -> tickGrab(sl);
			default -> {}
		}
	}

	private Vec3 deckPoint(double along) {
		double y = this.body != null && this.body.getArena() != null ? this.body.getArena().floorY + 1.1 : getY() + 1.6;
		return new Vec3(getX() + this.slamDir.x * along, y, getZ() + this.slamDir.z * along);
	}

	private void slam(ServerLevel sl) {
		setState(STATE_SLAM);
		playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
		Vec3 from = deckPoint(1.5), to = deckPoint(14.5);
		sl.sendParticles(ParticleTypes.SPLASH, (from.x + to.x) / 2, from.y, (from.z + to.z) / 2, 80, 3, 0.3, 3, 0.2);
		for (ServerPlayer p : this.body.playersInArena(sl)) {
			if (Geo.flatDistToSegment(p.position(), from, to) <= 1.3 && Math.abs(p.getY() - from.y) < 3) {
				BossDamage.hit(this.body, this, p, this.slamDamage, BossRegistry.BOSS_CRUSH, true);
			}
		}
		// The deck planks break into holes along the line (restored with the arena).
		for (double s = 3; s <= 14; s += 2.5) {
			if (this.random.nextFloat() > 0.55F) continue;
			BlockPos b = BlockPos.containing(deckPoint(s).add(0, -1.0, 0));
			if ((sl.getBlockState(b).is(BlockTags.PLANKS) || sl.getBlockState(b).is(BlockTags.LOGS)) && this.body.getArena() != null && this.body.getArena().containsBlock(b)) {
				sl.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
				sl.sendParticles(ParticleTypes.CRIT, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.1);
			}
		}
	}

	private @Nullable ServerPlayer grabbedPlayer(ServerLevel sl) {
		return this.grabbed == null ? null : sl.getServer().getPlayerList().getPlayer(this.grabbed);
	}

	private void tickGrab(ServerLevel sl) {
		ServerPlayer p = grabbedPlayer(sl);
		if (p == null || !p.isAlive()) {
			releaseGrab();
			return;
		}
		// Held at the tip, dragged toward the beak over 4 s.
		float f = Math.min(1.0F, this.stateTicks / 80.0F);
		Vec3 tip = position().add(this.slamDir.scale(9.0 * (1 - f))).add(0, 3.0 + 2.0 * (1 - f), 0);
		Vec3 beak = this.body.position().add(Geo.flatDir(this.body.position(), position()).scale(3.2)).add(0, 2.0, 0);
		Vec3 at = tip.lerp(beak, f * 0.8);
		p.teleportTo(at.x, at.y, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		if (this.stateTicks % 20 == 0) p.hurtServer(sl, BossDamage.source(sl, BossRegistry.BOSS_DOT, this, this.body), BossDamage.scaled(sl, 1.5F));
		if (this.stateTicks >= 80) {
			BossDamage.hit(this.body, p, this.body.num("grab", "bite_damage", 30.0F), BossRegistry.BOSS_ATTACK, true);
			this.body.setAction(VorlaghEntity.ACT_BITE);
			Geo.push(p, new Vec3(this.slamDir.x * 1.2, 0.6, this.slamDir.z * 1.2));
			releaseGrab();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isSevered() || this.body == null || !this.body.isAlive() || this.body.isDormant()) return false;
		if (BossDamage.isBossDamage(source) && !(source.getEntity() instanceof ServerPlayer)) return false;
		if (this.grabbed != null && source.getEntity() instanceof ServerPlayer) releaseGrab();
		float before = getHealth();
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof ServerPlayer p) this.body.creditPartDamage(p, Math.max(0, before - getHealth()));
		return hurt;
	}

	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		super.actuallyHurt(level, source, BossDamage.overpowerGuard(dmg));
	}

	@Override
	public void die(DamageSource source) {
		// Severed instead of dying: a stump until it regrows (phase 3) or the fight ends.
		if (level() instanceof ServerLevel sl && this.body != null && this.body.isAlive()) {
			releaseGrab();
			setHealth(1.0F);
			this.entityData.set(DATA_SEVERED, true);
			setState(STATE_IDLE);
			sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 3, getZ(), 60, 0.5, 2.0, 0.5, 0.1);
			playSound(SoundEvents.SQUID_DEATH, 3.0F, 0.4F);
			return;
		}
		super.die(source);
	}

	@Override
	public boolean isPickable() {
		return !isSevered();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean fromEffect) {
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}
}
