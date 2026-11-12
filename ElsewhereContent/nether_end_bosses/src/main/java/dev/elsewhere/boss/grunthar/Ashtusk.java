package dev.elsewhere.boss.grunthar;

import dev.elsewhere.nether_end_bosses.NetherEndSounds;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ashtusk, Grunthar's armored war-hoglin (190 base HP, armor 12). A linked body: it shares Grunthar's fight and phase
 * machine. Charge (1.2 s pawing, 38 raw, sidestep it) and Tusk Toss (20 + launch).
 */
public class Ashtusk extends ElsewhereBoss {
	public static final int FLAG_PAWING = FLAG_CUSTOM;
	public static final int FLAG_CHARGING = FLAG_CUSTOM << 1;
	public static final int FLAG_STUNNED = FLAG_CUSTOM << 2;

	private @Nullable UUID riderId;
	private Vec3 chargeDir = Vec3.ZERO;
	private final Set<UUID> chargeHit = new HashSet<>();
	private int stunTicks;

	public Ashtusk(EntityType<? extends Ashtusk> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 190.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.ATTACK_DAMAGE, 12.0)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	public void setRider(Grunthar rider) {
		this.riderId = rider.getUUID();
	}

	public @Nullable Grunthar rider() {
		if (riderId == null || !(this.level() instanceof ServerLevel level)) {
			for (Entity e : this.getPassengers()) if (e instanceof Grunthar g) return g;
			return null;
		}
		return level.getEntity(riderId) instanceof Grunthar g ? g : null;
	}

	@Override
	public ElsewhereBoss primary() {
		Grunthar g = rider();
		return g != null ? g : this;
	}

	@Override
	public String bossId() {
		return "ashtusk";
	}

	@Override
	public int tier() {
		return 1;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	@Override
	public BossEvent.BossBarOverlay barOverlay() {
		return BossEvent.BossBarOverlay.PROGRESS;
	}

	@Override
	public Holder<SoundEvent> music() {
		return NetherEndSounds.MUSIC_GRUNTHAR;
	}

	@Override
	protected int computePhase() {
		Grunthar g = rider();
		return g != null ? Math.max(getPhase(), g.getPhase()) : getPhase();
	}

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("charge", "Ashtusk Charge", 24, 110, AttackSpec.phases(1, 2, 3), 4, 38.0F),
			new AttackSpec("tusk_toss", "Tusk Toss", 10, 50, AttackSpec.phases(1, 2, 3), 3, 20.0F));
	}

	@Override
	protected boolean canStart(AttackSpec spec) {
		ServerPlayer t = nearestTarget();
		if (t == null) return false;
		double d = t.distanceTo(this);
		return spec.id().equals("tusk_toss") ? d < 4.5 : d > 5.0;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null; // Ashtusk steers itself; Grunthar only rides
	}

	@Override
	protected void bossTick(ServerLevel level) {
		if (stunTicks > 0) {
			stunTicks--;
			this.getNavigation().stop();
			if (stunTicks == 0) setFlag(FLAG_STUNNED, false);
			return;
		}
		if (current != null) return;
		ServerPlayer target = nearestTarget();
		if (target != null) {
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.tickCount % 10 == 0) this.getNavigation().moveTo(target, 1.0);
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "charge" -> charge(level, spec, t);
			case "tusk_toss" -> tuskToss(level, spec, t);
			default -> true;
		};
	}

	private boolean charge(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			ServerPlayer target = nextTarget();
			if (target == null) return true;
			Vec3 d = target.position().subtract(this.position());
			chargeDir = new Vec3(d.x, 0, d.z).normalize();
			chargeHit.clear();
			this.getNavigation().stop();
			setFlag(FLAG_PAWING, true);
			this.playSound(SoundEvents.HOGLIN_ANGRY, 3.0F, 0.6F);
			float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}
		if (t < spec.windup()) {
			// pawing: dust + scrape, red line shows the lane (38 raw = big hit)
			BlockPos below = this.blockPosition().below();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), this.getX(), this.getY() + 0.1, this.getZ(), 6, 1.0, 0.1, 1.0, 0.1);
			if (t % 6 == 0) {
				this.playSound(SoundEvents.HOGLIN_STEP, 2.0F, 0.6F);
				Decals.line(level, this.position(), this.position().add(chargeDir.scale(22)), Decals.RED);
			}
			return false;
		}
		int k = t - spec.windup();
		if (k == 0) {
			setFlag(FLAG_PAWING, false);
			setFlag(FLAG_CHARGING, true);
			this.playSound(SoundEvents.HOGLIN_ATTACK, 3.0F, 0.5F);
		}
		this.setDeltaMovement(chargeDir.x * 0.85, this.getDeltaMovement().y, chargeDir.z * 0.85);
		for (ServerPlayer p : targets()) {
			if (!chargeHit.contains(p.getUUID()) && p.getBoundingBox().intersects(this.getBoundingBox().inflate(0.6))) {
				chargeHit.add(p.getUUID());
				hit(level, p, spec.damage());
				p.push(chargeDir.x * 1.6, 0.6, chargeDir.z * 1.6);
				p.syncVelocity = true;
			}
		}
		if (this.horizontalCollision && k > 3 || k > 32) {
			setFlag(FLAG_CHARGING, false);
			if (this.horizontalCollision) {
				stunTicks = 30;
				setFlag(FLAG_STUNNED, true);
				this.playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 2.0F, 0.5F);
				level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.5, this.getZ(), 20, 0.8, 0.4, 0.8, 0.2);
			}
			this.setDeltaMovement(Vec3.ZERO);
			return true;
		}
		return false;
	}

	private boolean tuskToss(ServerLevel level, AttackSpec spec, int t) {
		ServerPlayer target = nearestTarget();
		if (target == null) return true;
		if (t == 0) this.playSound(SoundEvents.HOGLIN_ANGRY, 2.0F, 0.9F);
		this.getLookControl().setLookAt(target, 60.0F, 30.0F);
		if (t < spec.windup()) {
			if (t == 2) Decals.circle(level, this.position(), 4.0, Decals.YELLOW);
			return false;
		}
		this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		for (ServerPlayer p : targets()) {
			if (p.distanceTo(this) < 4.5) {
				hit(level, p, spec.damage());
				Vec3 away = p.position().subtract(this.position()).normalize();
				p.setDeltaMovement(away.x * 0.6, 1.15, away.z * 0.6);
				p.syncVelocity = true;
			}
		}
		this.playSound(SoundEvents.HOGLIN_ATTACK, 2.0F, 1.0F);
		return true;
	}

	@Override
	protected void onBossDeath(ServerLevel level) {
		Grunthar g = rider();
		if (g != null && g.isAlive()) {
			g.onMountDied(level);
		} else if (fight() != null) {
			fight().victory(level, null);
		}
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return GruntharArena.arena(origin);
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		return List.of();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (riderId != null) output.putString("Rider", riderId.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getString("Rider").ifPresent(s -> riderId = UUID.fromString(s));
	}

	@Override
	public boolean fireImmune() {
		return true;
	}
}
