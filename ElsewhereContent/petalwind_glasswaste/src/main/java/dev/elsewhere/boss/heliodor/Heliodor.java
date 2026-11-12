package dev.elsewhere.boss.heliodor;

import dev.elsewhere.glasswaste.BurningGlassItem;
import dev.elsewhere.glasswaste.GlasswasteItems;
import dev.elsewhere.glasswaste.Heat;
import dev.elsewhere.glasswaste.SunMirrorBlock;
import dev.elsewhere.petalwind_glasswaste.BossDamage;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.petalwind_glasswaste.Reg;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Heliodor, the Sunwright Engine (BOSSES.md #10, Tier IV). Armor 20 that only the mirror puzzle strips: every sun pulse,
 * each Sun Mirror throws its reflected beam along its aim; a beam that crosses Heliodor cracks 4 armor and stuns him.
 * Phase 1 Focus (Solar Lance: 2 s charge, shade blocks it; fist slam; Glare), phase 2 Overheat (Heat x2, magma vents,
 * Sun Motes), phase 3 Noon Eternal (a sweeping beam on a scorched path; mirrors on the path turn it back at him).
 * HP 500 base / 765 effective, toughness 8.
 */
public class Heliodor extends BossBase {
	public static final float BASE_HP = 500, ARMOR = 20, TOUGHNESS = 8, LANCE_RAW = 44, SLAM_RAW = 30, SWEEP_RAW = 44;
	public static final int LANCE_CHARGE = 40, SUN_PULSE = 160, STUN = 60;
	private static final EntityDataAccessor<Integer> DATA_CRACKS = SynchedEntityData.defineId(Heliodor.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_STUNNED = SynchedEntityData.defineId(Heliodor.class, EntityDataSerializers.BOOLEAN);
	public static final byte EV_LANCE = 90, EV_SLAM = 91, EV_CRACK = 92, EV_VENT = 93, EV_SWEEP = 94;
	public final AnimationState lanceAnimation = new AnimationState();
	public final AnimationState slamAnimation = new AnimationState();
	public final AnimationState crackAnimation = new AnimationState();
	public final AnimationState ventAnimation = new AnimationState();
	public final AnimationState sweepAnimation = new AnimationState(); // boss art hook (Noon Eternal wind-up)
	private int lanceCd = 60, lanceTicks = -1, slamCd = 40, slamTicks = -1, pulseCd = SUN_PULSE, stunTicks, ventCd = 100, sweepCd = 120, sweepTicks = -1;
	private ServerPlayer lanceTarget;
	private Vec3 sweepFrom, sweepTo;
	public int mirrorHits, lances;

	public Heliodor(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW);
		this.arenaRadius = 10;
		this.arenaDown = 4;
		this.arenaUp = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, SLAM_RAW).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 48)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CRACKS, 0);
		builder.define(DATA_STUNNED, false);
	}

	public int cracks() {
		return this.entityData.get(DATA_CRACKS);
	}

	public boolean stunned() {
		return this.entityData.get(DATA_STUNNED);
	}

	@Override
	public String bossId() {
		return "heliodor";
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.6F, 0.3F};
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.BEACON_ACTIVATE;
	}

	private Vec3 lens() {
		return this.position().add(0, 5.0, 0).add(this.getLookAngle().multiply(1, 0, 1).scale(0.9));
	}

	@Override
	protected void fightTick(ServerLevel level) {
		if (phase() >= 2 && this.tickCount % 40 == 0) for (ServerPlayer p : fighters(level)) Heat.boost(p, 60);
		if (--pulseCd <= 0) {
			pulseCd = SUN_PULSE;
			sunPulse(level);
		}
		if (stunTicks > 0) {
			stunTicks--;
			this.getNavigation().stop();
			if (stunTicks == 0) this.entityData.set(DATA_STUNNED, false);
			if (this.tickCount % 4 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 5, getZ(), 4, 0.6, 0.4, 0.6, 0.1);
			return;
		}
		ServerPlayer target = lanceTarget != null && lanceTicks >= 0 ? lanceTarget : pickTarget(level);
		if (target == null) return;
		this.getLookControl().setLookAt(target);
		double d = this.distanceTo(target);
		if (lanceTicks >= 0) {
			lanceTick(level);
			return;
		}
		if (slamTicks >= 0) {
			slamTick(level);
			return;
		}
		if (sweepTicks >= 0) {
			sweepTick(level);
			return;
		}
		if (phase() >= 2 && --ventCd <= 0) {
			ventCd = 400;
			vent(level);
		}
		if (phase() >= 3 && --sweepCd <= 0) {
			sweepCd = 200;
			startSweep(level, target);
			return;
		}
		if (d < 4.5 && --slamCd <= 0) {
			slamCd = 60;
			slamTicks = 0;
			Telegraph.circle(this, level, position(), 3.5, Telegraph.YELLOW, 20);
			level.broadcastEntityEvent(this, EV_SLAM);
			return;
		}
		if (--lanceCd <= 0) {
			lanceCd = 140;
			lanceTicks = 0;
			lanceTarget = target;
			lances++;
			level.broadcastEntityEvent(this, EV_LANCE);
			announce("Solar Lance", 40);
			return;
		}
		if (d > 5) this.getNavigation().moveTo(target, 0.9);
		else this.getNavigation().stop();
	}

	private void lanceTick(ServerLevel level) {
		this.getNavigation().stop();
		lanceTicks++;
		if (lanceTarget == null || !lanceTarget.isAlive()) {
			lanceTicks = -1;
			return;
		}
		Vec3 from = lens(), to = lanceTarget.getEyePosition();
		if (lanceTicks % 3 == 0) {
			int n = Math.max(1, (int) from.distanceTo(to)); // P13: one dust per block, was 1.5
			DustParticleOptions dust = new DustParticleOptions(0xFF3020, 0.6F + lanceTicks / (float) LANCE_CHARGE);
			for (int i = 0; i <= n; i++) {
				Vec3 p = from.lerp(to, i / (double) n);
				level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
		}
		if (lanceTicks >= LANCE_CHARGE) {
			lanceTicks = -1;
			HitResult clip = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			BurningGlassItem.beam(level, from, clip.getType() == HitResult.Type.MISS ? to : clip.getLocation());
			level.playSound(null, lanceTarget.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 1.5F);
			if (clip.getType() == HitResult.Type.MISS) {
				BossDamage.hit(level, this, lanceTarget, LANCE_RAW);
				lanceTarget.igniteForSeconds(3);
			}
			lanceTarget = null;
		}
	}

	private void slamTick(ServerLevel level) {
		this.getNavigation().stop();
		if (++slamTicks < 20) return;
		slamTicks = -1;
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.5, 0.1, 1.5, 0);
		level.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (ServerPlayer p : fighters(level)) if (p.distanceTo(this) <= 3.8) BossDamage.hit(level, this, p, SLAM_RAW);
	}

	/** Each mirror reflects sunlight along its aim; a beam crossing Heliodor cracks his armor and stuns him. */
	private void sunPulse(ServerLevel level) {
		if (arenaCenter() == null) return;
		for (BlockPos m : SunwrightZiggurat.mirrors(arenaCenter())) {
			var state = level.getBlockState(m);
			if (!state.is(dev.elsewhere.glasswaste.GlasswasteBlocks.SUN_MIRROR)) continue;
			Vec3 dir = SunMirrorBlock.direction(state.getValue(SunMirrorBlock.AIM));
			Vec3 from = Vec3.atCenterOf(m).add(0, 0.6, 0);
			Vec3 to = from.add(dir.scale(18));
			BurningGlassItem.beam(level, from, to);
			if (this.getBoundingBox().inflate(0.6).clip(from, to).isPresent()) crack(level, 0);
		}
	}

	public void crack(ServerLevel level, float bonusDamage) {
		mirrorHits++;
		int c = Math.min(5, cracks() + 1);
		this.entityData.set(DATA_CRACKS, c);
		AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(CRACK_ID);
			armor.addTransientModifier(new AttributeModifier(CRACK_ID, -4.0 * c, AttributeModifier.Operation.ADD_VALUE));
		}
		stunTicks = STUN;
		this.entityData.set(DATA_STUNNED, true);
		level.broadcastEntityEvent(this, EV_CRACK);
		announce("Armor cracked (" + (int) this.getArmorValue() + ")", 40);
		level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
		if (bonusDamage > 0) this.hurtServer(level, this.damageSources().magic(), bonusDamage);
	}

	private static final net.minecraft.resources.Identifier CRACK_ID = Reg.id("heliodor_cracked_armor");

	private void vent(ServerLevel level) {
		level.broadcastEntityEvent(this, EV_VENT);
		announce("Overheat", 40);
		var c = arenaCenter();
		if (c == null) return;
		for (int i = 0; i < 6; i++) {
			BlockPos p = c.offset(this.random.nextInt(15) - 7, -1, this.random.nextInt(15) - 7);
			if (level.getBlockState(p).isSolid()) level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
		}
		if (level.getEntitiesOfClass(dev.elsewhere.glasswaste.SunMote.class, arenaBox()).size() < 3) {
			var mote = ModEntities.SUN_MOTE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (mote != null) {
				mote.snapTo(getX() + 3, getY() + 3, getZ());
				dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, mote);
			}
		}
	}

	private void startSweep(ServerLevel level, ServerPlayer target) {
		Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 mid = target.position();
		sweepFrom = mid.add(side.scale(-9));
		sweepTo = mid.add(side.scale(9));
		Telegraph.line(this, level, sweepFrom, sweepTo, 1.5, Telegraph.SCORCH, 30);
		sweepTicks = 0;
		level.broadcastEntityEvent(this, EV_SWEEP);
		announce("Noon Eternal", 40);
	}

	private void sweepTick(ServerLevel level) {
		this.getNavigation().stop();
		if (++sweepTicks < 30) return;
		int t = sweepTicks - 30;
		Vec3 p = sweepFrom.lerp(sweepTo, Math.min(1.0, t / 12.0));
		BurningGlassItem.beam(level, lens(), p);
		for (ServerPlayer pl : fighters(level)) if (pl.position().distanceTo(p) < 1.6) BossDamage.hit(level, this, pl, SWEEP_RAW);
		if (arenaCenter() != null) for (BlockPos m : SunwrightZiggurat.mirrors(arenaCenter())) {
			if (Vec3.atCenterOf(m).distanceTo(p) < 1.8) {
				var state = level.getBlockState(m);
				if (state.is(dev.elsewhere.glasswaste.GlasswasteBlocks.SUN_MIRROR)) {
					Vec3 dir = SunMirrorBlock.direction(state.getValue(SunMirrorBlock.AIM));
					Vec3 from = Vec3.atCenterOf(m);
					BurningGlassItem.beam(level, from, from.add(dir.scale(18)));
					if (this.getBoundingBox().inflate(0.6).clip(from, from.add(dir.scale(18))).isPresent()) crack(level, 20);
				}
			}
		}
		if (t >= 12) sweepTicks = -1;
	}

	@Override
	protected void onReset(ServerLevel level) {
		this.entityData.set(DATA_CRACKS, 0);
		this.entityData.set(DATA_STUNNED, false);
		AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) armor.removeModifier(CRACK_ID);
		lanceTicks = slamTicks = sweepTicks = -1;
		stunTicks = 0;
	}

	@Override
	protected List<ItemStack> rollDrops(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(GlasswasteItems.SUNGLASS_SHARD, 4 + player.getRandom().nextInt(5)));
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("heliograph_lens", () -> new ItemStack(GlasswasteItems.HELIOGRAPH_LENS)),
			dev.elsewhere.boss.kit.DropRule.other("sunwright_plate", () -> new ItemStack(GlasswasteItems.SUNWRIGHT_PLATE)))));
		if (firstKill) out.add(new ItemStack(GlasswasteItems.SUNWRIGHT_TEMPLATE)); // the way into the Sunwright smithing line (BALANCE G9); more from the Ziggurat chest or the duplicate recipe
		return out;
	}

	@Override
	protected ItemStack summonItem() {
		return new ItemStack(dev.elsewhere.petalwind_glasswaste.PGStructures.SUNWRIGHT_MIRROR_KEY);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EV_LANCE -> lanceAnimation.start(this.tickCount);
			case EV_SLAM -> slamAnimation.start(this.tickCount);
			case EV_CRACK -> crackAnimation.start(this.tickCount);
			case EV_VENT -> ventAnimation.start(this.tickCount);
			case EV_SWEEP -> sweepAnimation.start(this.tickCount);
			default -> super.handleEntityEvent(id);
		}
	}
}
