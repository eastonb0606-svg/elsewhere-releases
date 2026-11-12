package dev.elsewhere.verdigris_ironpeak;

import java.util.function.BiConsumer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * A boss projectile drawn as an item (hymn trident, wax shell, arc bolt). Damage is the raw value on Normal; the boss
 * framework's guards apply because the owner is the boss. Optional gravity, an optional landing hook, and it can be
 * shot down (wax shells: "kill the throw with arrows").
 */
public class BossShot extends ThrowableItemProjectile {
	private final Item display;
	private final boolean gravity;
	private final boolean shootable;
	private final ParticleOptions trail;
	public float damage;
	public @Nullable BiConsumer<BossShot, HitResult> onLand;
	private int life;

	public BossShot(EntityType<? extends BossShot> type, Level level, Item display, boolean gravity, boolean shootable, ParticleOptions trail) {
		super(type, level);
		this.display = display;
		DISPLAY.putIfAbsent(type, display);
		this.gravity = gravity;
		this.shootable = shootable;
		this.trail = trail;
		setNoGravity(!gravity);
	}

	/** Display items by entity type (getDefaultItem runs inside the super constructor, before fields are set). */
	public static final java.util.Map<EntityType<?>, Item> DISPLAY = new java.util.HashMap<>();

	@Override
	protected Item getDefaultItem() {
		Item i = DISPLAY.get(getType());
		return i != null ? i : net.minecraft.world.item.Items.SNOWBALL;
	}

	@Override
	protected double getDefaultGravity() {
		return gravity ? 0.04 : 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level) {
			if (++life > 200) discard();
			if (life % 3 == 0) level.sendParticles(trail, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof dev.elsewhere.stub.verdigris_ironpeak.StubBoss) && entity != getOwner();
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel level && hit.getEntity() instanceof LivingEntity target && damage > 0) {
			Entity owner = getOwner();
			DamageSource src = owner instanceof LivingEntity lo ? damageSources().mobProjectile(this, lo) : damageSources().thrown(this, owner);
			target.hurtServer(level, src, dev.elsewhere.stub.verdigris_ironpeak.BossGuards.scaled(level, damage));
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel level) {
			if (onLand != null) onLand.accept(this, hit);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.1);
			discard();
		}
	}

	@Override
	public boolean isPickable() {
		return shootable;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!shootable) return false;
		level.sendParticles(ParticleTypes.WAX_OFF, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.1);
		discard();
		return true;
	}

	public static BossShot create(EntityType<BossShot> type, ServerLevel level, LivingEntity owner, double x, double y, double z) {
		BossShot s = type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		s.setOwner(owner);
		s.setPos(x, y, z);
		s.setItem(new ItemStack(s.display));
		return s;
	}
}
