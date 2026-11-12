package dev.elsewhere.boss.strawgrim;

import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossPart;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:strawgrim_head}: Strawgrim's jack o'lantern in phase 3. It circles the arena 4-7 blocks up
 * (a bow is ideal), swoops low every few seconds, and every hit it takes is forwarded to the body: it is the only
 * hittable part. It has no health of its own.
 */
public class StrawgrimHead extends Mob implements BossPart, Enemy {
	private @Nullable StrawgrimEntity body;
	private double angle;
	private int swoop;
	private @Nullable ServerPlayer swoopTarget;
	private boolean swooping;
	public static final int SWOOP_CYCLE = 120, SWOOP_WARN = 80, SWOOP_DIVE = 90;

	/** True while the head is down at sword height (the 1.5 s swoop). */
	public boolean isSwooping() { return swooping; }

	/** Test hook: jumps the cycle to just before the warning. */
	public void testSkipToWarn() { this.swoop = SWOOP_WARN - 1; }

	public StrawgrimHead(EntityType<? extends StrawgrimHead> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.noPhysics = true;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 0.0);
	}

	public void setBody(StrawgrimEntity body) {
		this.body = body;
	}

	@Override
	public @Nullable ElsewhereBoss parentBoss() {
		return this.body;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) return;
		if (this.body == null || !this.body.isAlive() || this.body.isDormant() || !this.body.isHeadless()) {
			discard();
			return;
		}
		if (this.tickCount % 3 == 0) sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 2, 0.2, 0.2, 0.2, 0.01);
		Vec3 c = this.body.getArena() != null ? this.body.getArena().centerVec() : this.body.position();
		this.angle += 0.035;
		double r = 7.0 + Math.sin(this.tickCount * 0.02) * 2.0;
		double h = 5.0 + Math.sin(this.tickCount * 0.07) * 1.5;
		Vec3 want = c.add(Math.cos(this.angle) * r, h, Math.sin(this.angle) * r);
		// fun-strawgrim-head: every 6 s (120 ticks) the head comes down to sword height for 1.5 s (30 ticks).
		// 0..79 orbit, 80..89 candle flare warning above the chosen player, 90..119 swoop.
		this.swoop = (this.swoop + 1) % SWOOP_CYCLE;
		if (this.swoop == SWOOP_WARN) {
			List<ServerPlayer> ps = this.body.playersInArena(sl);
			this.swoopTarget = !ps.isEmpty() ? ps.get(this.random.nextInt(ps.size())) : this.body.getTarget() instanceof ServerPlayer tp ? tp : null;
			if (this.swoopTarget != null) sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.4F);
		}
		boolean warn = this.swoop >= SWOOP_WARN && this.swoop < SWOOP_DIVE, dive = this.swoop >= SWOOP_DIVE;
		this.swooping = dive && this.swoopTarget != null;
		if (this.swoopTarget != null && (warn || dive)) {
			ServerPlayer p = this.swoopTarget;
			if (p.isAlive() && !p.isSpectator()) {
				if (warn) {
					// candle flare: a flame ring over the player and a column rising to the head
					sl.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 2.6, p.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
					sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 6, 0.3, 0.4, 0.3, 0.04);
				}
				want = warn ? want : p.position().add(Vec3.directionFromRotation(0, this.tickCount * 7).scale(1.2)).add(0, 0.9, 0);
			}
		}
		Vec3 step = want.subtract(position());
		double max = dive ? 0.7 : 0.35;
		if (step.length() > max) step = step.normalize().scale(max);
		setPos(position().add(step));
		setDeltaMovement(step);
		if (this.body.getTarget() != null) getLookControl().setLookAt(this.body.getTarget(), 30.0F, 30.0F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.body == null || !this.body.isAlive()) return false;
		if (BossDamage.isBossDamage(source) && !(source.getEntity() instanceof ServerPlayer)) return false;
		boolean hurt = this.body.hurtFromHead(level, source, amount);
		if (hurt) {
			this.hurtTime = 10;
			level.broadcastDamageEvent(this, source);
			level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 3, 0.2, 0.2, 0.2, 0.0);
		}
		return hurt;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}
}
