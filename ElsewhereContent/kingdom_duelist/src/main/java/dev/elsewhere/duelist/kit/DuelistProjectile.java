package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.HumanBoss;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * X2 {@code ThrownGear} and X3 {@code SplashShot} in one projectile entity ({@code elsewhere:duelist_projectile}). It
 * carries an item (axe, javelin, lantern, flask), hits one entity for raw damage (through its thrower's caps) and then
 * sticks for 40 ticks and fades. It never damages a block and never ignites. A splash variant bursts over a radius,
 * applies effects and raw magic damage (shields do not stop it) and never kills: it leaves the victim at 1 HP at least.
 */
public class DuelistProjectile extends ThrowableItemProjectile {
	public static final int STICK_TICKS = 40;
	private static final EntityDataAccessor<Boolean> DATA_STUCK = SynchedEntityData.defineId(DuelistProjectile.class, EntityDataSerializers.BOOLEAN);

	/** Splash definition: effects, radius, raw magic damage. */
	public record Splash(List<MobEffectInstance> effects, double radius, float raw) {}

	public float raw;
	/** Which cap applies to a thrown hit (default MINOR). */
	public DamageCaps kind = DamageCaps.MINOR;
	public @Nullable Splash splash;
	public int stuck;
	private @Nullable HumanBoss boss;
	public boolean hitSomething;

	public DuelistProjectile(EntityType<? extends DuelistProjectile> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.IRON_AXE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_STUCK, false);
	}

	public boolean isStuck() {
		return this.entityData.get(DATA_STUCK);
	}

	@Override
	protected double getDefaultGravity() {
		return isStuck() ? 0.0 : 0.04;
	}

	/** Throws {@code stack} from {@code boss} at {@code target} (leading it by half its velocity). Returns the projectile. */
	public static DuelistProjectile throwAt(ServerLevel level, HumanBoss boss, LivingEntity target, ItemStack stack, float raw, float speed) {
		return throwAt(level, boss, target, stack, raw, speed, DamageCaps.MINOR);
	}

	/** As {@link #throwAt(ServerLevel, HumanBoss, LivingEntity, ItemStack, float, float)} with the cap {@code kind} the hit goes through (a signature throw uses SIGNATURE). */
	public static DuelistProjectile throwAt(ServerLevel level, HumanBoss boss, LivingEntity target, ItemStack stack, float raw, float speed, DamageCaps kind) {
		DuelistProjectile p = new DuelistProjectile(DuelistContent.PROJECTILE, level);
		p.kind = kind;
		p.setItem(stack.copyWithCount(1));
		p.raw = raw;
		p.boss = boss;
		p.setOwner(boss);
		p.setPos(boss.getX(), boss.getEyeY() - 0.1, boss.getZ());
		aim(p, boss, target, speed);
		level.addFreshEntity(p);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1F, 1F);
		return p;
	}

	/** A splash bottle (X3): bursts on impact. {@code stack} is the bottle shown in flight (a splash potion for the colour). */
	public static DuelistProjectile splashAt(ServerLevel level, HumanBoss boss, LivingEntity target, ItemStack stack, Splash splash, float speed) {
		DuelistProjectile p = new DuelistProjectile(DuelistContent.PROJECTILE, level);
		p.setItem(stack.copyWithCount(1));
		p.splash = splash;
		p.boss = boss;
		p.setOwner(boss);
		p.setPos(boss.getX(), boss.getEyeY() - 0.1, boss.getZ());
		aim(p, boss, target, speed);
		level.addFreshEntity(p);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 1F, 1F);
		return p;
	}

	private static void aim(DuelistProjectile p, HumanBoss boss, LivingEntity target, float speed) {
		Vec3 v = target.getDeltaMovement();
		double dist = boss.distanceTo(target);
		double t = dist / Math.max(0.5, speed);
		double dx = target.getX() + v.x * t * 0.5 - p.getX();
		double dz = target.getZ() + v.z * t * 0.5 - p.getZ();
		double dy = target.getY(0.5) - p.getY() + Math.sqrt(dx * dx + dz * dz) * 0.1;
		p.shoot(dx, dy, dz, speed, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel l) {
			if (isStuck()) {
				setDeltaMovement(Vec3.ZERO);
				if (++stuck >= STICK_TICKS) discard();
			} else if (tickCount > 200) discard();
			if (!isStuck() && tickCount % 3 == 0 && splash != null && boss != null) l.sendParticles(boss.style().particle(), getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private void stick() {
		this.entityData.set(DATA_STUCK, true);
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (!(level() instanceof ServerLevel level) || isStuck()) return;
		if (hit.getEntity() == getOwner() || !(hit.getEntity() instanceof LivingEntity victim)) return;
		if (splash != null) {
			burst(level);
			return;
		}
		if (boss != null && boss.isAlive()) {
			hitSomething = boss.hurtRaw(level, victim, raw, kind, level.damageSources().thrown(this, boss));
			boss.bump("thrown_hit");
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1F, 1F);
		stick();
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (!(level() instanceof ServerLevel level) || isStuck()) return;
		if (splash != null) {
			burst(level);
			return;
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 1F, 1F);
		stick();
	}

	/** The splash bursts: effects and magic damage to everything within the radius, never lethal, no block change. */
	private void burst(ServerLevel level) {
		Splash s = splash;
		if (s == null) return;
		level.levelEvent(2002, blockPosition(), 0x8844AA);
		List<LivingEntity> hit = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(s.radius())));
		for (LivingEntity v : hit) {
			if (v == getOwner() || v instanceof HumanBoss) continue;
			if (v.distanceToSqr(this) > s.radius() * s.radius() + 1) continue;
			for (MobEffectInstance e : s.effects()) v.addEffect(new MobEffectInstance(e), getOwner());
			if (s.raw() > 0F && boss != null && boss.isAlive()) {
				float cap = v.getHealth() - 1F;
				if (cap > 0F) {
					float amt = Math.min(Math.min(s.raw() * boss.damageMultiplier(), boss.profile.capSignature) * Kit.MAGIC_RAW_FACTOR, cap);
					v.hurtServer(level, level.damageSources().indirectMagic(this, boss), amt);
					boss.bump("splash_hit");
				}
			}
		}
		discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("Stuck", stuck);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		stuck = in.getIntOr("Stuck", 0);
	}
}
