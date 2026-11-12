package dev.elsewhere.boss.rimeback;

import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:glacier_boulder}: the packed-ice chunk from Glacier Hurl. Flies a fixed arc to the ring Rimeback
 * marked; the centre takes the big hit (40 raw), the splash 6 damage and Slowness.
 */
public class GlacierBoulder extends Entity {
	private @Nullable RimebackEntity boss;
	private Vec3 from = Vec3.ZERO;
	private Vec3 to = Vec3.ZERO;
	private int flight = 18;
	private int t;
	private float damage = 40.0F;

	public GlacierBoulder(EntityType<? extends GlacierBoulder> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public void launch(RimebackEntity boss, Vec3 from, Vec3 to, int flight, float damage) {
		this.boss = boss;
		this.from = from;
		this.to = to;
		this.flight = flight;
		this.damage = damage;
		setPos(from);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) return;
		if (this.boss == null) {
			discard();
			return;
		}
		this.t++;
		float f = Math.min(1.0F, this.t / (float) this.flight);
		Vec3 p = this.from.lerp(this.to, f).add(0, 6.0 * 4 * f * (1 - f), 0);
		setDeltaMovement(p.subtract(position()));
		setPos(p);
		setXRot(getXRot() + 12);
		if (this.t >= this.flight) impact(sl);
	}

	private void impact(ServerLevel sl) {
		sl.playSound(null, this.to.x, this.to.y, this.to.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
		sl.playSound(null, this.to.x, this.to.y, this.to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 1.2F);
		sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), this.to.x, this.to.y + 0.5, this.to.z, 120, 1.5, 0.5, 1.5, 0.3);
		sl.sendParticles(ParticleTypes.SNOWFLAKE, this.to.x, this.to.y + 0.5, this.to.z, 60, 3.0, 0.5, 3.0, 0.1);
		for (ServerPlayer p : sl.players()) {
			double d = Math.sqrt(p.distanceToSqr(this.to.x, p.getY(), this.to.z));
			if (d > 4.0 || Math.abs(p.getY() - this.to.y) > 3.0) continue;
			if (d <= 1.8) BossDamage.hit(this.boss, this, p, this.damage, BossRegistry.BOSS_CRUSH, true);
			else BossDamage.hit(this.boss, this, p, 6.0F, BossRegistry.BOSS_CRUSH, false);
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this.boss);
			Vec3 away = Geo.flatDir(this.to, p.position());
			Geo.push(p, new Vec3(away.x * 0.6, 0.4, away.z * 0.6));
		}
		discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
