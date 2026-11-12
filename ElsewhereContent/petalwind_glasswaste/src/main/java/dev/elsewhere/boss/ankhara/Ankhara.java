package dev.elsewhere.boss.ankhara;

import dev.elsewhere.glasswaste.Burrowing;
import dev.elsewhere.glasswaste.GlasswasteItems;
import dev.elsewhere.petalwind_glasswaste.BossDamage;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Ankhara, the Sunpit Devourer (BOSSES.md #11, Tier IV). A 14-segment sand serpent: untargetable while it swims under
 * the pit; Erupt (1.5 s bubbling circle, then a breach), Lunge (bait it into a glass pillar to stun it for 5 s and expose
 * the gizzards, x1.5 damage). Phase 2 Collapse: the outer sand ring falls into magma and Glass Scarabs pour out.
 * Phase 3 Sunswallow: the middle ring collapses; below 10% it erupts every 4 s. HP 560 base / 857 effective.
 * Beta: the body segments are drawn by the client from the head's trail (shared SerpentChain); the separate segment
 * hitboxes of the design are not built yet.
 */
public class Ankhara extends BossBase implements Burrowing {
	public static final float BASE_HP = 560, ARMOR = 12, TOUGHNESS = 6, ERUPT_RAW = 45, LUNGE_RAW = 32;
	public static final int SEGMENTS = 13;
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Ankhara.class, EntityDataSerializers.INT);
	public static final int M_BURROWED = 0, M_SURFACED = 1, M_LUNGE = 2, M_STUNNED = 3;
	public static final byte EV_ERUPT = 100, EV_LUNGE = 101, EV_STUN = 102, EV_COLLAPSE = 103, EV_WINDUP = 104;
	public final AnimationState eruptAnimation = new AnimationState();
	public final AnimationState lungeAnimation = new AnimationState();
	public final AnimationState stunAnimation = new AnimationState();
	public final AnimationState windupAnimation = new AnimationState(); // boss art hook (Lunge wind-up)
	private int eruptCd = 100, modeTicks, collapseTicks = -1, collapseRing;
	private Vec3 eruptAt, lungeDir;
	public int erupts, lunges, stuns, collapses;

	public Ankhara(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.arenaRadius = Sunpit.ARENA_RADIUS;
		this.arenaDown = 6;
		this.arenaUp = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, LUNGE_RAW).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, M_BURROWED);
	}

	public int mode() {
		return this.entityData.get(DATA_MODE);
	}

	private void setMode(int m) {
		this.entityData.set(DATA_MODE, m);
		modeTicks = 0;
	}

	@Override
	public boolean isBurrowed() {
		return mode() == M_BURROWED;
	}

	@Override
	public String bossId() {
		return "ankhara";
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
		return dev.elsewhere.petalwind_glasswaste.Sfx3.pick("ankhara", "roar", SoundEvents.RAVAGER_ROAR);
	}

	@Override
	public void tick() {
		this.noPhysics = true;
		super.tick();
		double min = level().getMinY() + MIN_MARGIN;
		if (!level().isClientSide() && getY() < min) {
			this.setPos(getX(), min, getZ());
			this.setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
		}
	}

	/** Lowest allowed y above the level's minimum build height (the clamp margin). */
	public static final int MIN_MARGIN = 2;

	/** Standing level of the arena, never lower than the level's minimum build height plus a burrow depth and the margin. */
	private double floorY() {
		double y = arenaCenter() != null ? arenaCenter().getY() : getY();
		return Math.max(y, level().getMinY() + MIN_MARGIN + BURROW_DEPTH);
	}

	public static final double BURROW_DEPTH = 4.5;

	/** Burrow depth clamped to the local ground: never below the world floor plus the margin. */
	private double burrowY() {
		return Math.max(floorY() - BURROW_DEPTH, level().getMinY() + MIN_MARGIN);
	}

	@Override
	protected void idleTick(ServerLevel level) {
		if (mode() != M_BURROWED || surfaceHold > 0) {
			if (surfaceHold > 0) surfaceHold--;
			this.setDeltaMovement(Vec3.ZERO);
			this.setPos(getX(), floorY(), getZ());
			return;
		}
		swimToward(level, arenaCenter() != null ? Vec3.atBottomCenterOf(arenaCenter()) : position(), 0.05);
	}

	private void swimToward(ServerLevel level, Vec3 goal, double speed) {
		Vec3 flat = new Vec3(goal.x - getX(), 0, goal.z - getZ());
		Vec3 move = flat.lengthSqr() > 0.25 ? flat.normalize().scale(speed) : Vec3.ZERO;
		double wantY = burrowY();
		this.setDeltaMovement(move.x, (wantY - getY()) * 0.2, move.z);
		if (move.lengthSqr() > 0) face(move);
		if (this.tickCount % (move.lengthSqr() > 0 ? 4 : 8) == 0) { // P13: was 4 every 2 ticks, moving or not
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), getX(), floorY() + 0.1, getZ(), 3,
				1.0, 0.05, 1.0, 0);
		}
	}

	private void face(Vec3 dir) {
		this.setYRot((float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isBurrowed() && !source.isCreativePlayer()) return false;
		if (mode() == M_STUNNED) damage *= 1.5F;
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void fightTick(ServerLevel level) {
		modeTicks++;
		if (collapseTicks >= 0) collapseTick(level);
		ServerPlayer target = pickTarget(level);
		boolean enraged = this.getHealth() / this.getMaxHealth() <= 0.1F;
		switch (mode()) {
			case M_BURROWED -> {
				if (eruptAt != null) {
					this.setDeltaMovement(Vec3.ZERO);
					if (modeTicks % 3 == 0) level.sendParticles(ParticleTypes.LAVA, eruptAt.x, floorY(), eruptAt.z, 3, 1.5, 0, 1.5, 0);
					if (modeTicks >= 30) breach(level);
					return;
				}
				if (target != null) swimToward(level, target.position(), 0.35);
				if (--eruptCd <= 0 && target != null) {
					eruptCd = enraged ? 80 : 160;
					eruptAt = new Vec3(target.getX(), floorY(), target.getZ());
					modeTicks = 0;
					this.teleportTo(eruptAt.x, burrowY(), eruptAt.z);
					Telegraph.circle(this, level, eruptAt, 3.5, Telegraph.RED, 30);
					announce("Erupt", 30);
				}
			}
			case M_SURFACED -> {
				this.setDeltaMovement(Vec3.ZERO);
				if (modeTicks == 15 && target != null) {
					lungeDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
					face(lungeDir);
					Telegraph.line(this, level, position(), position().add(lungeDir.scale(12)), 2.0, Telegraph.YELLOW, 20);
					announce("Lunge", 20);
					level.broadcastEntityEvent(this, EV_WINDUP);
				}
				if (modeTicks == 35 && lungeDir != null) {
					setMode(M_LUNGE);
					lunges++;
					level.broadcastEntityEvent(this, EV_LUNGE);
				} else if (modeTicks > 80) dive(level);
			}
			case M_LUNGE -> {
				Vec3 next = position().add(lungeDir.scale(0.9));
				BlockPos ahead = BlockPos.containing(next.x, floorY() + 1, next.z);
				if (level.getBlockState(ahead).is(Blocks.GLASS) || level.getBlockState(ahead.above()).is(Blocks.GLASS)) {
					for (int dy = 0; dy < 6; dy++) level.destroyBlock(BlockPos.containing(next.x, floorY() + dy, next.z), false);
					setMode(M_STUNNED);
					stuns++;
					level.broadcastEntityEvent(this, EV_STUN);
					announce("Stunned! Gizzards exposed", 60);
					return;
				}
				this.teleportTo(next.x, floorY(), next.z);
				for (ServerPlayer p : fighters(level)) if (p.distanceTo(this) < 2.6) BossDamage.hit(level, this, p, LUNGE_RAW);
				if (modeTicks > 14 || !insideArena(this)) dive(level);
			}
			case M_STUNNED -> {
				this.setDeltaMovement(Vec3.ZERO);
				if (modeTicks % 5 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.5, getZ(), 4, 1, 0.3, 1, 0);
				if (modeTicks >= 100) dive(level);
			}
			default -> {}
		}
	}

	private void breach(ServerLevel level) {
		erupts++;
		this.teleportTo(eruptAt.x, floorY(), eruptAt.z);
		eruptAt = null;
		setMode(M_SURFACED);
		level.broadcastEntityEvent(this, EV_ERUPT);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), floorY() + 1, getZ(), 80, 2, 1, 2, 0.4);
		level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 1.4F);
		for (ServerPlayer p : fighters(level)) {
			if (p.position().distanceTo(position()) <= 3.6) {
				BossDamage.hit(level, this, p, ERUPT_RAW);
				p.push(0, 1.2, 0);
			}
		}
	}

	private void dive(ServerLevel level) {
		setMode(M_BURROWED);
		lungeDir = null;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), floorY() + 0.5, getZ(), 40, 1.5, 0.5, 1.5, 0.2);
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		collapseRing = phase == 2 ? 0 : 1;
		collapseTicks = 0;
		announce(phase == 2 ? "Collapse" : "Sunswallow", 60);
		level.broadcastEntityEvent(this, EV_COLLAPSE);
		if (phase == 2) for (int i = 0; i < 3; i++) {
			var scarab = ModEntities.GLASS_SCARAB.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (scarab == null) continue;
			scarab.snapTo(getX() + i * 2 - 2, floorY(), getZ() + 3);
			dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, scarab);
		}
	}

	/** 3 s crumble warning, then the ring's sand drops into the magma below. */
	private void collapseTick(ServerLevel level) {
		var c = arenaCenter();
		if (c == null) {
			collapseTicks = -1;
			return;
		}
		int in = collapseRing == 0 ? Sunpit.OUTER_IN : Sunpit.MID_IN, out = collapseRing == 0 ? Sunpit.OUTER_OUT : Sunpit.MID_OUT;
		collapseTicks++;
		if (collapseTicks % 10 == 0 && collapseTicks < 60) Telegraph.ring(this, level, Vec3.atBottomCenterOf(c), (in + out) / 2.0, Telegraph.RED, 10);
		if (collapseTicks == 60) {
			collapses++;
			for (int dx = -out; dx <= out; dx++) for (int dz = -out; dz <= out; dz++) {
				BlockPos p = c.offset(dx, -1, dz);
				if (Sunpit.inRing(c, p, in, out) && level.getBlockState(p).is(Blocks.SAND)) level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
			}
			collapseTicks = -1;
		}
	}

	@Override
	protected void onReset(ServerLevel level) {
		setMode(M_BURROWED);
		eruptAt = null;
		collapseTicks = -1;
	}

	@Override
	protected List<ItemStack> rollDrops(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(GlasswasteItems.SANDSWIMMER_SCALE, 3 + player.getRandom().nextInt(3)));
		out.add(new ItemStack(GlasswasteItems.GIZZARD_STONE)); // always (BALANCE): it is also the Sunpit Lure's key ingredient
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("glassfang", () -> new ItemStack(GlasswasteItems.GLASSFANG)))));
		return out;
	}

	@Override
	protected ItemStack summonItem() {
		return new ItemStack(dev.elsewhere.petalwind_glasswaste.PGStructures.SUNPIT_LURE);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EV_ERUPT -> eruptAnimation.start(this.tickCount);
			case EV_LUNGE -> lungeAnimation.start(this.tickCount);
			case EV_STUN -> stunAnimation.start(this.tickCount);
			case EV_WINDUP -> windupAnimation.start(this.tickCount);
			default -> super.handleEntityEvent(id);
		}
	}

	/** Screenshot/test hook: surface at the pit floor now. */
	public void debugSurface() {
		surfaceHold = 400;
		setMode(M_SURFACED);
		this.setDeltaMovement(Vec3.ZERO);
		this.teleportTo(getX(), floorY(), getZ());
	}

	private int surfaceHold;

	@Override
	public boolean isPushable() {
		return false;
	}
}
