package dev.elsewhere.boss.mournmaw;

import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.Guards;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** {@code elsewhere:soul_tear}: a falling ghast tear (16 raw) that leaves a soul-fire patch where it lands. Blocked by cover (the bone arches). */
public class SoulTear extends ThrowableItemProjectile {
	private float damage = 16.0F;

	public SoulTear(EntityType<? extends SoulTear> type, Level level) {
		super(type, level);
	}

	public SoulTear(EntityType<? extends SoulTear> type, LivingEntity owner, Level level) {
		super(type, level);
		this.setOwner(owner);
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return MournmawContent.SOUL_TEAR_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.06;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof ElsewhereBoss || entity instanceof BossPart || entity.entityTags().contains(Guards.MINION_TAG));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel level && this.getOwner() instanceof ElsewhereBoss boss) {
			hit.getEntity().hurtServer(level, boss.source(StubFramework.BOSS_PROJECTILE, this), ElsewhereBoss.scaled(level, damage));
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (this.level() instanceof ServerLevel level) {
			BlockPos above = hit.getBlockPos().above();
			if (level.getBlockState(hit.getBlockPos()).is(Blocks.DRIED_GHAST)) {
				level.destroyBlock(hit.getBlockPos(), false); // tears smother the dried ghast: defend it
			} else if (level.getBlockState(above).isAir() && (level.getBlockState(hit.getBlockPos()).is(Blocks.SOUL_SAND) || level.getBlockState(hit.getBlockPos()).is(Blocks.SOUL_SOIL))) {
				level.setBlock(above, Blocks.SOUL_FIRE.defaultBlockState(), 3);
			}
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
			this.discard();
		}
	}
}
