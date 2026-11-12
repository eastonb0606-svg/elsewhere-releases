package dev.elsewhere.boss.aphelion;

import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.NetherEndSounds;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.BossFight;
import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Aphelion, the Coil Beyond the End (Tier VI, 1500 base HP, armor 16 / toughness 8). A 22-segment void wyrm: the head
 * is the hitbox; the body follows its path (rendered from the head's trail on the client). 1 Coil (100-70%): it rings
 * a player (tightening portal ring, jump through the glowing gap, 48 raw), Void Breath lines, bites; outside a coil the
 * scales shrug off 65% of damage. 2 Devour (70-35%): dives through islands after a 2 s purple beam, Voidborn join.
 * 3 Collapse (35-0%): islands fall into the void, gravity 0.5x, it eats end crystals to heal 10% each.
 */
public class Aphelion extends ElsewhereBoss {
	public static final int SEGMENTS = 22;
	public static final int FLAG_COILING = FLAG_CUSTOM;
	public static final int FLAG_DIVING = FLAG_CUSTOM << 1;
	public static final float OUTSIDE_COIL_DAMAGE = 0.35F;

	private Vec3 goal = Vec3.ZERO;
	private Vec3 attackCenter = Vec3.ZERO;
	private Vec3 lineFrom = Vec3.ZERO, lineTo = Vec3.ZERO;
	private final Set<UUID> hitOnce = new HashSet<>();
	private @Nullable EndCrystal meal;

	public Aphelion(EntityType<? extends Aphelion> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.xpReward = 800;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 1300.0)
			.add(Attributes.ARMOR, 16.0)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override public String bossId() { return "aphelion"; }
	@Override public int tier() { return 6; }
	@Override public BossEvent.BossBarColor barColor() { return BossEvent.BossBarColor.PURPLE; }
	@Override public BossEvent.BossBarOverlay barOverlay() { return BossEvent.BossBarOverlay.NOTCHED_20; }
	@Override public Holder<SoundEvent> music() { return NetherEndSounds.MUSIC_APHELION; }
	@Override protected float[] phaseThresholds() { return new float[] {0.70F, 0.35F}; }
	@Override protected SoundEvent roarSound() { return dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "growl", SoundEvents.ENDER_DRAGON_GROWL); }

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("coil", "Coil", 20, 220, AttackSpec.phases(1), 5, 48.0F),
			new AttackSpec("void_breath", "Void Breath", 30, 120, AttackSpec.phases(1, 2), 4, 6.0F),
			new AttackSpec("bite", "Bite", 20, 60, AttackSpec.phases(1, 2, 3), 4, 36.0F),
			new AttackSpec("devour_dive", "Devour", 40, 140, AttackSpec.phases(2), 5, 42.0F),
			new AttackSpec("voidborn", "Voidborn", 20, 400, AttackSpec.phases(2), 3, 0.0F),
			new AttackSpec("collapse", "Collapse", 60, 200, AttackSpec.phases(3), 4, 0.0F),
			new AttackSpec("crystal_feast", "Crystal Feast", 10, 160, AttackSpec.phases(3), 6, 0.0F));
	}

	@Override
	protected boolean canStart(AttackSpec spec) {
		return switch (spec.id()) {
			case "voidborn" -> fight() != null && fight().minionCount((ServerLevel) this.level()) < fight().minionCap(2);
			case "crystal_feast" -> !crystals().isEmpty();
			default -> true;
		};
	}

	public List<EndCrystal> crystals() {
		BossFight f = fight();
		if (f == null) return List.of();
		return this.level().getEntitiesOfClass(EndCrystal.class, f.arena.box(), EndCrystal::isAlive);
	}

	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float damage, @Nullable BossPart part) {
		return getPhase() == 1 && !hasFlag(FLAG_COILING) ? damage * OUTSIDE_COIL_DAMAGE : damage;
	}

	// ---- flight ----

	@Override
	protected void bossTick(ServerLevel level) {
		if (current == null) {
			Vec3 h = homeCenter();
			double a = this.tickCount * 0.02;
			goal = new Vec3(h.x + Math.cos(a) * 18, h.y + 8 + Math.sin(this.tickCount * 0.05) * 4, h.z + Math.sin(a) * 18);
		}
		steer(0.55);
		if (this.tickCount % 3 == 0) level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.5, this.getZ(), 4, 1.5, 1.0, 1.5, 0.2);
	}

	private void steer(double maxSpeed) {
		Vec3 want = goal.subtract(this.position());
		Vec3 desired = want.length() > maxSpeed ? want.normalize().scale(maxSpeed) : want;
		Vec3 v = this.getDeltaMovement().lerp(desired, 0.12);
		this.setDeltaMovement(v);
		if (v.lengthSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) (-(Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
			this.setYRot(yaw);
			this.setXRot(pitch);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}
	}

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	private void biteAround(ServerLevel level, float raw, double radius) {
		for (ServerPlayer p : targets()) {
			if (!hitOnce.contains(p.getUUID()) && p.distanceTo(this) < radius) {
				hitOnce.add(p.getUUID());
				hit(level, p, raw);
			}
		}
	}

	// ---- attacks ----

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "coil" -> coil(level, spec, t);
			case "void_breath" -> voidBreath(level, spec, t);
			case "bite" -> bite(level, spec, t);
			case "devour_dive" -> devourDive(level, spec, t);
			case "voidborn" -> voidborn(level, spec, t);
			case "collapse" -> collapse(level, spec, t);
			case "crystal_feast" -> crystalFeast(level, spec, t);
			default -> true;
		};
	}

	/** Rings a player, tightening from 14 to 4 blocks over 6 s; the gap behind the head glows. Inside at the end: 48 raw. */
	private boolean coil(ServerLevel level, AttackSpec spec, int t) {
		int duration = 120;
		if (t == 0) {
			ServerPlayer target = nextTarget();
			attackCenter = target != null ? target.position() : homeCenter();
			setFlag(FLAG_COILING, true);
			this.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "growl", SoundEvents.ENDER_DRAGON_GROWL), 5.0F, 0.6F);
		}
		float k = Math.min(1, t / (float) duration);
		double r = 14 - 10 * k;
		double a = t * 0.12;
		goal = attackCenter.add(Math.cos(a) * r, 1.0, Math.sin(a) * r);
		steer(1.4);
		if (t % 4 == 0) {
			int pts = 40;
			for (int i = 0; i < pts; i++) {
				double ang = a - 0.5 - i * (Mth.TWO_PI - 1.2) / pts; // leaves a gap just ahead of the head
				level.sendParticles(ParticleTypes.PORTAL, attackCenter.x + Math.cos(ang) * r, attackCenter.y + 0.5, attackCenter.z + Math.sin(ang) * r, 1, 0, 0.3, 0, 0.1);
			}
			level.sendParticles(ParticleTypes.END_ROD, attackCenter.x + Math.cos(a + 0.3) * r, attackCenter.y + 1, attackCenter.z + Math.sin(a + 0.3) * r, 4, 0.3, 0.6, 0.3, 0.01);
		}
		if (t < duration) return false;
		setFlag(FLAG_COILING, false);
		this.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "coil", SoundEvents.ENDER_DRAGON_FLAP), 5.0F, 0.5F);
		for (ServerPlayer p : targets()) {
			double d = Math.sqrt(Mth.square(p.getX() - attackCenter.x) + Mth.square(p.getZ() - attackCenter.z));
			if (d < r + 0.5 && Math.abs(p.getY() - attackCenter.y) < 4) hit(level, p, spec.damage());
		}
		return true;
	}

	/** A marked line, then it strafes along it dropping void breath pools (6 per 0.5 s while you stand in them). */
	private boolean voidBreath(ServerLevel level, AttackSpec spec, int t) {
		Vec3 h = homeCenter();
		if (t == 0) {
			ServerPlayer target = nextTarget();
			Vec3 through = target != null ? target.position() : h;
			double a = this.random.nextDouble() * Mth.TWO_PI;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			lineFrom = through.add(dir.scale(-12));
			lineTo = through.add(dir.scale(12));
			this.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "breath", SoundEvents.ENDER_DRAGON_SHOOT), 4.0F, 0.6F);
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) Decals.line(level, lineFrom, lineTo, Decals.YELLOW);
			goal = lineFrom.add(0, 7, 0);
			steer(1.0);
			return false;
		}
		int k = t - spec.windup();
		float f = Math.min(1, k / 50.0F);
		goal = lineFrom.lerp(lineTo, f).add(0, 7, 0);
		steer(1.0);
		if (k % 6 == 0) {
			VoidBreath breath = new VoidBreath(AphelionContent.VOID_BREATH, this, new Vec3(0, -1, 0), level);
			breath.setPos(this.getX(), this.getY() - 0.5, this.getZ());
			breath.setDamage(spec.damage());
			level.addFreshEntity(breath);
		}
		return k >= 55;
	}

	private boolean bite(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			hitOnce.clear();
			ServerPlayer target = nextTarget();
			attackCenter = target != null ? target.position().add(0, 1, 0) : homeCenter();
			this.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "growl", SoundEvents.ENDER_DRAGON_GROWL), 4.0F, 1.0F);
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) Decals.line(level, this.position(), attackCenter, Decals.PURPLE);
			goal = this.position();
			steer(0.2);
			return false;
		}
		goal = attackCenter;
		steer(1.6);
		biteAround(level, spec.damage(), 3.0);
		return this.position().distanceTo(attackCenter) < 2.0 || t > spec.windup() + 40;
	}

	/** 2 s purple warning beam, then it dives through the island, deleting a tunnel (restored after the fight). */
	private boolean devourDive(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			hitOnce.clear();
			ServerPlayer target = nextTarget();
			Vec3 at = target != null ? target.position() : homeCenter();
			lineFrom = at.add(0, 16, 0);
			lineTo = at.add(0, Math.max(-14, level.getMinY() + 4 - at.y), 0); // never dives below the level floor + margin
			setFlag(FLAG_DIVING, true);
		}
		if (t < spec.windup()) {
			if (t % 4 == 0) Decals.column(level, lineTo, 30, Decals.PURPLE);
			goal = lineFrom;
			steer(1.2);
			return false;
		}
		int k = t - spec.windup();
		goal = lineTo;
		steer(1.5);
		BlockPos c = this.blockPosition();
		BossFight f = fight();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
			if (p.distSqr(c) <= 5 && f != null && f.arena.contains(Vec3.atCenterOf(p)) && !level.getBlockState(p).isAir()
				&& !level.getBlockState(p).is(AphelionContent.HALO_ALTAR) && !level.getBlockState(p).is(Blocks.BEDROCK)) {
				level.removeBlock(p, false);
			}
		}
		biteAround(level, spec.damage(), 3.5);
		if (k >= 40 || this.position().distanceTo(lineTo) < 1.5) {
			setFlag(FLAG_DIVING, false);
			return true;
		}
		return false;
	}

	private boolean voidborn(ServerLevel level, AttackSpec spec, int t) {
		if (t < spec.windup()) return false;
		BossFight f = fight();
		if (f == null) return true;
		Vec3 h = homeCenter();
		for (int i = 0; i < 1 + f.activePlayers().size(); i++) {
			Enderman e = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (e == null) continue;
			double a = this.random.nextDouble() * Mth.TWO_PI;
			e.snapTo(h.x + Math.cos(a) * 5, h.y, h.z + Math.sin(a) * 5, 0, 0);
			e.getAttribute(Attributes.SCALE).setBaseValue(1.2);
			e.setCustomName(Component.translatable("entity.elsewhere.voidborn"));
			e.setPersistenceRequired();
			ServerPlayer target = nextTarget();
			if (target != null) e.setTarget(target);
			f.minion(e);
			level.addFreshEntity(e);
		}
		return true;
	}

	/** An outer island shudders for 3 s, then falls into the void. */
	private boolean collapse(ServerLevel level, AttackSpec spec, int t) {
		BlockPos o = home();
		if (o == null) return true;
		if (t == 0) {
			int i = this.random.nextInt(AphelionArena.ISLANDS);
			attackCenter = Vec3.atCenterOf(AphelionArena.islandCenter(o, i));
		}
		if (t < spec.windup()) {
			if (t % 4 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, attackCenter.x, attackCenter.y, attackCenter.z, 30, 3, 1, 3, 0.05);
			return false;
		}
		BlockPos c = BlockPos.containing(attackCenter);
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-5, -4, -5), c.offset(5, 2, 5))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.END_STONE) || s.is(Blocks.END_STONE_BRICKS)) FallingBlockEntity.fall(level, p.immutable(), s);
		}
		this.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "coil", SoundEvents.ENDER_DRAGON_FLAP), 5.0F, 0.4F);
		return true;
	}

	/** Flies to an end crystal and eats it: +10% max HP. Destroy them first. */
	private boolean crystalFeast(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			List<EndCrystal> list = crystals();
			meal = list.isEmpty() ? null : list.get(this.random.nextInt(list.size()));
		}
		if (meal == null || !meal.isAlive()) return true;
		goal = meal.position();
		steer(1.2);
		if (this.distanceTo(meal) < 3.0 || t > 160) {
			if (this.distanceTo(meal) < 3.0) {
				meal.discard();
				this.heal(this.getMaxHealth() * 0.10F);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 5.0F, 0.5F);
				level.sendParticles(ParticleTypes.EXPLOSION, meal.getX(), meal.getY(), meal.getZ(), 3, 1, 1, 1, 0);
			}
			return true;
		}
		return false;
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		setFlag(FLAG_COILING, false);
		setFlag(FLAG_DIVING, false);
		if (phase == 3) {
			for (ServerPlayer p : targets()) {
				AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
				if (g != null) g.addOrUpdateTransientModifier(new AttributeModifier(StubFramework.FIGHT_GRAVITY, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return AphelionArena.arena(origin);
	}

	@Override
	public void rebuildArena(ServerLevel level, BlockPos origin) {
		AphelionArena.INSTANCE.build(level, origin, level.getRandom());
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.signature("void_scale", () -> new ItemStack(AphelionContent.VOID_SCALE)),
			DropRule.other("wyrmfang", () -> new ItemStack(AphelionContent.WYRMFANG)))));
		out.add(new ItemStack(AphelionContent.APHELION_HEART));
		return out;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	/** Client: the head's recent path; the renderer lays the 21 body segments along it. */
	public final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			if (trail.isEmpty() || trail.peekFirst().distanceToSqr(this.position()) > 0.04) trail.addFirst(this.position());
			while (trail.size() > 400) trail.removeLast();
		}
	}

	/** Point at arc length {@code dist} behind the head along the trail (falls back to a straight tail). */
	public Vec3 alongTrail(Vec3 head, double dist) {
		Vec3 prev = head;
		double left = dist;
		for (Vec3 p : trail) {
			double d = prev.distanceTo(p);
			if (d >= left && d > 1.0E-6) return prev.lerp(p, left / d);
			left -= d;
			prev = p;
		}
		Vec3 back = Vec3.directionFromRotation(0, this.getYRot()).scale(-1);
		return prev.add(back.scale(left));
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}
}
