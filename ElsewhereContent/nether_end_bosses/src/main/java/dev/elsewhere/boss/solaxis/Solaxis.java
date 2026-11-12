package dev.elsewhere.boss.solaxis;

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
import net.minecraft.server.level.ServerBossEvent;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Solaxis, the Ninefold Pyre (Tier I). A hovering blaze lord with 9 orbiting rods; 3 rods eject per phase and each
 * becomes a Pyre Shard. Phase 1 Rod Volley + Solar Flare, phase 2 deflectable Eclipse orbs, phase 3 the Supernova
 * DPS timer (kill it or take cover).
 */
public class Solaxis extends ElsewhereBoss {
	public static final int FLAG_RODS_GLOW = FLAG_CUSTOM;
	public static final int FLAG_CORE_PULSE = FLAG_CUSTOM << 1;
	public static final float SNOWBALL_DAMAGE = 6.0F;
	public static final float SUPERNOVA_DPS_SHARE = 0.18F;

	private Vec3 flareCenter;
	private final Set<UUID> flareHit = new HashSet<>();
	private @Nullable ServerBossEvent supernovaBar;
	private float supernovaStartHealth;

	public Solaxis(EntityType<? extends Solaxis> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 200;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 350.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.25)
			.add(Attributes.FLYING_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	public String bossId() {
		return "solaxis";
	}

	@Override
	public int tier() {
		return 1;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	public BossEvent.BossBarOverlay barOverlay() {
		return BossEvent.BossBarOverlay.NOTCHED_6;
	}

	@Override
	public Holder<SoundEvent> music() {
		return NetherEndSounds.MUSIC_SOLAXIS;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.66F, 0.33F};
	}

	/** 9 rods in phase 1, 6 in phase 2, 3 in phase 3 (synced through the phase). */
	public int rodsLeft() {
		return Math.max(0, 9 - 3 * (getPhase() - 1));
	}

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("rod_volley", "Rod Volley", 20, 60, AttackSpec.phases(1, 2, 3), 4, 6.0F),
			new AttackSpec("solar_flare", "Solar Flare", 30, 140, AttackSpec.phases(1, 2), 3, 30.0F),
			new AttackSpec("eclipse_orb", "Eclipse Core", 25, 70, AttackSpec.phases(2, 3), 4, 28.0F),
			new AttackSpec("supernova", "Supernova", 0, 500, AttackSpec.phases(3), 6, 40.0F));
	}

	// ---- movement ----

	@Override
	protected void bossTick(ServerLevel level) {
		Vec3 home = homeCenter();
		double height = getPhase() == 3 ? 3.0 : 4.5;
		double t = this.tickCount * 0.02;
		double r = current != null && current.id().equals("supernova") ? 0.0 : 4.0;
		Vec3 goal = new Vec3(home.x + Math.cos(t) * r, home.y + height + Math.sin(this.tickCount * 0.07) * 0.4, home.z + Math.sin(t) * r);
		Vec3 delta = goal.subtract(this.position());
		Vec3 v = delta.scale(0.04);
		if (v.length() > 0.25) v = v.normalize().scale(0.25);
		this.setDeltaMovement(v);
		ServerPlayer target = nearestTarget();
		if (target != null) {
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}
		if (this.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 2.5, this.getZ(), 3, 1.2, 1.5, 1.2, 0.01);
			level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 4.5, this.getZ(), 1, 0.6, 0.4, 0.6, 0);
		}
	}

	@Override
	public void travel(Vec3 input) {
		this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	// ---- attacks ----

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "rod_volley" -> rodVolley(level, spec, t);
			case "solar_flare" -> solarFlare(level, spec, t);
			case "eclipse_orb" -> eclipseOrb(level, spec, t);
			case "supernova" -> supernova(level, spec, t);
			default -> true;
		};
	}

	/** Rod positions (world space), matching the model's three rings. */
	public Vec3 rodPosition(int i, float partial) {
		int ring = i / 3;
		float age = this.tickCount + partial;
		double[] radius = {2.0, 2.55, 2.1}; // the concept art's rings (3.6 / 4.6 / 3.8 art units at 0.555 blocks each)
		double[] height = {5.77, 4.44, 2.89};
		double speed = ring == 1 ? -0.05 : 0.06;
		double a = age * speed + i * (Mth.TWO_PI / 3) + ring * 0.7;
		return new Vec3(this.getX() + Math.cos(a) * radius[ring], this.getY() + height[ring], this.getZ() + Math.sin(a) * radius[ring]);
	}

	private boolean rodVolley(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			setFlag(FLAG_RODS_GLOW, true);
			this.playSound(SoundEvents.BLAZE_AMBIENT, 3.0F, 0.6F);
		}
		if (t < spec.windup()) return false;
		int shot = (t - spec.windup()) / 3;
		if ((t - spec.windup()) % 3 == 0 && shot < rodsLeft()) {
			ServerPlayer target = nextTarget();
			if (target != null) {
				Vec3 from = rodPosition(shot, 0);
				Vec3 dir = target.getEyePosition().subtract(from).normalize();
				PyreBolt bolt = new PyreBolt(SolaxisContent.PYRE_BOLT, this, dir, level);
				bolt.setPos(from);
				bolt.setDamage(spec.damage());
				level.addFreshEntity(bolt);
				this.playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.8F + this.random.nextFloat() * 0.3F);
			}
		}
		if (shot >= rodsLeft()) {
			setFlag(FLAG_RODS_GLOW, false);
			return true;
		}
		return false;
	}

	private boolean solarFlare(ServerLevel level, AttackSpec spec, int t) {
		Vec3 home = homeCenter();
		if (t == 0) {
			flareCenter = new Vec3(this.getX(), home.y, this.getZ());
			flareHit.clear();
			this.playSound(SoundEvents.BLAZE_BURN, 4.0F, 0.5F);
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) {
				Decals.circle(level, flareCenter, 2.0 + t * 0.2, Decals.YELLOW);
				Decals.circle(level, flareCenter, 12.0, Decals.YELLOW);
			}
			return false;
		}
		int k = t - spec.windup();
		double radius = 1.0 + k * 0.3;
		if (radius > 12.5) return true;
		int points = (int) (radius * 8);
		for (int i = 0; i < points; i++) {
			double a = i * Mth.TWO_PI / points;
			level.sendParticles(ParticleTypes.FLAME, flareCenter.x + Math.cos(a) * radius, flareCenter.y + 0.2, flareCenter.z + Math.sin(a) * radius, 1, 0.05, 0.15, 0.05, 0.0);
		}
		if (k % 4 == 0) level.playSound(null, BlockPos.containing(flareCenter), SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.6F);
		for (ServerPlayer p : targets()) {
			if (flareHit.contains(p.getUUID())) continue;
			double d = Math.sqrt(Mth.square(p.getX() - flareCenter.x) + Mth.square(p.getZ() - flareCenter.z));
			boolean grounded = p.getY() - flareCenter.y < 0.9; // jump the ring line
			if (Math.abs(d - radius) < 0.8 && grounded) {
				flareHit.add(p.getUUID());
				hit(level, p, spec.damage(), StubFramework.BOSS_FIRE, null);
				p.igniteForSeconds(4);
			}
		}
		return false;
	}

	private boolean eclipseOrb(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) this.playSound(SoundEvents.GHAST_WARN, 3.0F, 0.5F);
		if (t < spec.windup()) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 3.0, this.getZ(), 6, 0.4, 0.4, 0.4, 0.05);
			return false;
		}
		ServerPlayer target = nextTarget();
		if (target != null) {
			Vec3 from = this.position().add(0, 3.0, 0);
			Vec3 dir = target.getEyePosition().subtract(from).normalize();
			SunOrb orb = new SunOrb(SolaxisContent.SUN_ORB, this, dir, level);
			orb.setPos(from.add(dir.scale(2.4)));
			orb.setDamage(spec.damage());
			level.addFreshEntity(orb);
			this.playSound(SoundEvents.GHAST_SHOOT, 3.0F, 0.6F);
		}
		return true;
	}

	/** 20 s DPS check. Deal {@link #SUPERNOVA_DPS_SHARE} of max HP before the timer ends, or take cover from the core. */
	private boolean supernova(ServerLevel level, AttackSpec spec, int t) {
		BossFight fight = fight();
		int duration = 400;
		if (t == 0) {
			supernovaStartHealth = this.getHealth();
			setFlag(FLAG_CORE_PULSE, true);
			if (fight != null) supernovaBar = fight.addBar(Component.translatable("attack.elsewhere.solaxis.supernova_timer"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
			this.playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F);
		}
		float needed = this.getMaxHealth() * SUPERNOVA_DPS_SHARE;
		float done = supernovaStartHealth - this.getHealth();
		if (supernovaBar != null) supernovaBar.setProgress(Math.max(0, 1 - t / (float) duration));
		if (t % 20 == 0) {
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE080), this.getX(), this.getY() + 3.0, this.getZ(), 1, 0, 0, 0, 0);
			this.playSound(SoundEvents.BEACON_AMBIENT, 4.0F, 0.5F + t / (float) duration);
			Decals.circle(level, homeCenter(), 11.5, Decals.RED);
		}
		if (done >= needed) {
			endSupernova();
			this.playSound(SoundEvents.FIRE_EXTINGUISH, 4.0F, 0.5F);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 3.0, this.getZ(), 40, 1.0, 1.0, 1.0, 0.05);
			if (fight != null) fight.flash(Component.translatable("attack.elsewhere.solaxis.supernova_fizzled"), 60);
			return true;
		}
		if (t >= duration) {
			endSupernova();
			Vec3 core = this.position().add(0, 3.0, 0);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 3, 1.0, 1.0, 1.0, 0);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.5F);
			for (ServerPlayer p : targets()) {
				if (hasCover(level, core, p)) continue;
				hit(level, p, spec.damage(), StubFramework.BOSS_BLAST, null);
				p.igniteForSeconds(5);
			}
			return true;
		}
		return false;
	}

	private void endSupernova() {
		setFlag(FLAG_CORE_PULSE, false);
		if (supernovaBar != null && fight() != null) fight().removeBar(supernovaBar);
		supernovaBar = null;
	}

	/** Cover = a solid block between the core and the player's eyes. */
	public boolean hasCover(ServerLevel level, Vec3 core, ServerPlayer player) {
		HitResult hit = level.clip(new ClipContext(core, player.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.BLOCK;
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		endSupernova();
		setFlag(FLAG_RODS_GLOW, false);
		// three rods eject and become Pyre Shards
		for (int i = 0; i < 3; i++) {
			Vec3 at = rodPosition(rodsLeft() + i, 0);
			Blaze shard = EntityTypes.BLAZE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (shard == null) continue;
			shard.setPos(at);
			shard.setCustomName(Component.translatable("entity.elsewhere.pyre_shard"));
			shard.getAttribute(Attributes.SCALE).setBaseValue(0.6);
			shard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(16.0);
			shard.setHealth(16.0F);
			shard.setPersistenceRequired();
			if (fight() != null) fight().minion(shard);
			level.addFreshEntity(shard);
			level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0);
		}
		this.playSound(SoundEvents.BLAZE_DEATH, 4.0F, 0.5F);
	}

	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float damage, @Nullable BossPart part) {
		if (source.getDirectEntity() instanceof Snowball) return SNOWBALL_DAMAGE;
		return damage;
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.BLAZE_HURT;
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return new BossArena(origin, 12, origin.getY() - 1, origin.getY() + 14, false, Vec3.atBottomCenterOf(origin.south(8)));
	}

	@Override
	public void rebuildArena(ServerLevel level, BlockPos origin) {
		dev.elsewhere.nether_end_bosses.fit3.NetherFit.rebuild(level, "solaxis", origin); // 3.0: the generator that built this arena (2.x or fit3)
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.signature("ninefold_rod", () -> new ItemStack(SolaxisContent.NINEFOLD_ROD)),
			DropRule.other("pyre_crown", () -> new ItemStack(SolaxisContent.PYRE_CROWN)))));
		out.add(new ItemStack(SolaxisContent.SOLAXIS_EMBER));
		return out;
	}

	@Override
	protected void onBossDeath(ServerLevel level) {
		endSupernova();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 3, this.getZ(), 2, 0.5, 0.5, 0.5, 0);
		super.onBossDeath(level);
	}

	@Override
	public void remove(RemovalReason reason) {
		endSupernova();
		super.remove(reason);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}
}
