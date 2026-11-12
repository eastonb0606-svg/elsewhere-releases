package dev.elsewhere.gear3.s3.galesling;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code elsewhere:gale_sling_stone}: a slung cobblestone or gravel pebble. A vanilla throwable (snowball physics, gravity 0.03)
 * drawn by the vanilla ThrownItemRenderer from its item stack, whose {@code minecraft:item_model} is the pebble sprite
 * ({@code elsewhere:gale_sling_stone} / {@code elsewhere:gale_sling_gravel}). It carries its charge-scaled damage and the
 * thrower's Marksman multiplier from the moment it is loosed; the ammo is used up (never dropped back).
 * Client only: a small gust where it leaves the sling, a crit trail on full-charge stones, a crumble of block particles on impact
 * (entity event 3, like a snowball).
 */
public class SlingStone extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Boolean> FULL = SynchedEntityData.defineId(SlingStone.class, EntityDataSerializers.BOOLEAN);
	public static final byte EVENT_CRUMBLE = 3;

	private float damage = GaleSling.DAMAGE;
	private float marksman = 1.0F;

	public SlingStone(EntityType<? extends SlingStone> type, Level level) {
		super(type, level);
	}

	public SlingStone(Level level, LivingEntity owner, boolean gravel, float power, float marksman) {
		super(GaleSling.STONE, owner, level, look(gravel));
		this.damage = GaleSling.stoneDamage(power);
		this.marksman = marksman;
		this.getEntityData().set(FULL, power >= 1.0F);
	}

	/** The thrown stack: the ammo's block item with the pebble sprite as its item model (what the client draws). */
	public static ItemStack look(boolean gravel) {
		ItemStack s = new ItemStack(gravel ? Items.GRAVEL : Items.COBBLESTONE);
		s.set(DataComponents.ITEM_MODEL, gravel ? GaleSling.GRAVEL_MODEL : GaleSling.STONE_MODEL);
		return s;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBBLESTONE; // runs inside the super constructor: a constant only
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(FULL, false);
	}

	public float damage() {
		return damage;
	}

	public float marksman() {
		return marksman;
	}

	public boolean isFull() {
		return this.getEntityData().get(FULL);
	}

	public boolean isGravel() {
		return getItem().is(Items.GRAVEL);
	}

	/** Damage against one target: the Marksman multiplier never applies to players (PvP stays calling-neutral, GEAR.md A1). */
	public float damageAgainst(Entity target) {
		return target instanceof Player ? damage : damage * marksman;
	}

	/** One hit: thrown damage from this stone, credited to the thrower. */
	public boolean strike(ServerLevel level, Entity target) {
		return target.hurtServer(level, damageSources().thrown(this, getOwner()), damageAgainst(target));
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() || isRemoved()) return;
		RandomSource r = getRandom();
		Vec3 v = getDeltaMovement();
		if (tickCount == 1) level().addParticle(ParticleTypes.SMALL_GUST, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0.0, 0.0, 0.0);
		if (isFull()) for (int i = 0; i < 2; i++)
			level().addParticle(ParticleTypes.CRIT, getX() - v.x * i * 0.5, getY() - v.y * i * 0.5, getZ() - v.z * i * 0.5,
				-v.x * 0.1 + (r.nextDouble() - 0.5) * 0.05, -v.y * 0.1 + 0.05, -v.z * 0.1 + (r.nextDouble() - 0.5) * 0.05);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel sl) strike(sl, hit.getEntity());
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel sl && !isRemoved()) {
			sl.broadcastEntityEvent(this, EVENT_CRUMBLE);
			sl.playSound(null, getX(), getY(), getZ(), GaleSling.HIT, SoundSource.PLAYERS, 0.7F, 0.9F + getRandom().nextFloat() * 0.3F);
			discard();
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != EVENT_CRUMBLE) {
			super.handleEntityEvent(id);
			return;
		}
		BlockState crumb = (isGravel() ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState();
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, crumb);
		RandomSource r = getRandom();
		for (int i = 0; i < 6; i++)
			level().addParticle(dust, getX(), getY(), getZ(), (r.nextDouble() - 0.5) * 0.15, r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.15);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putFloat("SlingDamage", damage);
		out.putFloat("SlingMarksman", marksman);
		out.putBoolean("SlingFull", isFull());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		damage = in.getFloatOr("SlingDamage", GaleSling.DAMAGE);
		marksman = in.getFloatOr("SlingMarksman", 1.0F);
		getEntityData().set(FULL, in.getBooleanOr("SlingFull", false));
	}
}
