package dev.elsewhere.boss.kazehana;

import dev.elsewhere.petalwind_glasswaste.BossDamage;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:petal_mirage}: one of Kazehana's three Cherry Storm mirror copies. 1 HP, looks exactly like him but has
 * no shadow ring. It circles you and throws half-strength Draw Cuts.
 */
public class PetalMirage extends Monster {
	public static final float CUT_RAW = 18;
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(PetalMirage.class, EntityDataSerializers.INT);
	public final AnimationState iaidoAnimation = new AnimationState();
	public final AnimationState dashAnimation = new AnimationState();
	private @Nullable UUID owner;
	private int cooldown = 60 + 20 * (int) (Math.random() * 4), stateTicks;
	private Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
	private boolean hitDone;

	public PetalMirage(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, Kazehana.S_STANCE);
	}

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	public void setOwner(Kazehana k) {
		this.owner = k.getUUID();
		this.setTarget(k.getTarget());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		var o = owner == null ? null : level.getEntity(owner);
		if (!(o instanceof Kazehana k) || !k.isAlive() || !k.isFighting()) {
			poof(level);
			return;
		}
		LivingEntity target = k.getTarget();
		this.setTarget(target);
		stateTicks++;
		int s = state();
		if (s == Kazehana.S_IAIDO) {
			this.getNavigation().stop();
			if (stateTicks >= Kazehana.DRAW_CUT_WINDUP) {
				set(Kazehana.S_DASH, (byte) 72);
				hitDone = false;
			}
			return;
		}
		if (s == Kazehana.S_DASH) {
			Vec3 p = from.lerp(to, Math.min(1, stateTicks / 6.0));
			this.teleportTo(p.x, getY(), p.z);
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, p.x, getY() + 1.2, p.z, 3, 0.4, 0.4, 0.4, 0.02);
			if (!hitDone) for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(p, p).inflate(1.2, 2, 1.2))) {
				BossDamage.hit(level, k, pl, CUT_RAW);
				hitDone = true;
			}
			if (stateTicks >= 6) set(Kazehana.S_STANCE, (byte) 0);
			return;
		}
		if (target == null) return;
		this.getLookControl().setLookAt(target, 30, 30);
		double d = distanceTo(target);
		if (--cooldown <= 0 && d < 12) {
			cooldown = 140;
			from = position();
			Vec3 dir = target.position().subtract(from).multiply(1, 0, 1).normalize();
			to = from.add(dir.scale(Math.min(12, d + 4)));
			Telegraph.line(this, level, from, to, 1.4, Telegraph.WHITE, 16);
			this.setYRot((float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90.0F);
			set(Kazehana.S_IAIDO, (byte) 71);
			return;
		}
		if (d > 6) this.getNavigation().moveTo(target, 1.0);
		else this.getMoveControl().strafe(d < 4 ? -0.5F : 0.0F, (this.tickCount / 45) % 2 == 0 ? 0.5F : -0.5F);
	}

	private void set(int state, byte event) {
		this.entityData.set(DATA_STATE, state);
		stateTicks = 0;
		if (event != 0 && level() instanceof ServerLevel l) l.broadcastEntityEvent(this, event);
	}

	private void poof(ServerLevel level) {
		level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1.5, getZ(), 40, 0.5, 1, 0.5, 0.1);
		this.discard();
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel l) {
			l.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1.5, getZ(), 40, 0.5, 1, 0.5, 0.1);
			this.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.5F);
		}
		super.die(source);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 71) iaidoAnimation.start(this.tickCount);
		else if (id == 72) dashAnimation.start(this.tickCount);
		else super.handleEntityEvent(id);
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
