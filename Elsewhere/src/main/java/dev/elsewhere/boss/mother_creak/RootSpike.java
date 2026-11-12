package dev.elsewhere.boss.mother_creak;

import dev.elsewhere.core.boss.ElsewhereBoss;
import dev.elsewhere.palehollow.entity.CreakingHusk;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** {@code elsewhere:root_spike}: one root of a Root Burst line. Erupts, hits what stands on it once, sinks back. */
public class RootSpike extends Entity {
	public static final int LIFETIME = 26;
	public static final int HIT_TICK = 3;
	private @Nullable UUID owner;
	private float damage = 38.0F;
	private boolean hit;

	public RootSpike(final EntityType<? extends RootSpike> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setup(final double x, final double y, final double z, final float yaw, final ElsewhereBoss owner, final float damage) {
		this.snapTo(x, y, z, yaw + (float)(Math.random() * 60.0 - 30.0), 0.0F);
		this.owner = owner.getUUID();
		this.damage = damage;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount == 1) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PALE_OAK_WOOD.defaultBlockState()), this.getX(), this.getY() + 0.3,
				this.getZ(), 12, 0.3, 0.2, 0.3, 0.1);
		}
		if (this.tickCount == HIT_TICK && !this.hit) {
			this.hit = true;
			Entity ownerEntity = this.owner == null ? null : level.getEntity(this.owner);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.25),
				e -> e.isAlive() && !(e instanceof ElsewhereBoss) && !(e instanceof CreakingHusk))) {
				DamageSource src = ownerEntity instanceof LivingEntity le ? this.damageSources().mobAttack(le) : this.damageSources().magic();
				if (ownerEntity instanceof ElsewhereBoss boss) {
					boss.hitGuarded(level, e, this.damage, src);
				} else {
					e.hurtServer(level, src, this.damage);
				}
				e.push(0.0, 0.6, 0.0);
			}
		}
		if (this.tickCount >= LIFETIME) {
			this.discard();
		}
	}

	/** 0..1..0 eruption curve for the renderer. */
	public float height(final float partialTicks) {
		float t = this.tickCount + partialTicks;
		if (t < 4.0F) {
			return t / 4.0F;
		}
		if (t > LIFETIME - 6) {
			return Math.max(0.0F, (LIFETIME - t) / 6.0F);
		}
		return 1.0F;
	}

	@Override
	protected void defineSynchedData(final net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
	}
}
