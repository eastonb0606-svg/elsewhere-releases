package dev.elsewhere.boss.rimeback;

import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.overworld_bosses.OwbEntities;
import dev.elsewhere.overworld_bosses.OwbItems;
import dev.elsewhere.overworld_bosses.OwbSounds;
import dev.elsewhere.stub.overworld_bosses.BossArena;
import dev.elsewhere.stub.overworld_bosses.BossAttack;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import dev.elsewhere.stub.overworld_bosses.SignatureGear;
import dev.elsewhere.stub.overworld_bosses.Telegraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rimeback, the Avalanche Colossus (BOSSES.md #4, Tier II). Glacier Hurl (rip, 1.5 s overhead hold with a landing
 * ring, throw), Frost Breath cone, fist slam; phase 2 avalanche waves down the slope (hide behind ice outcrops) and
 * Frostbound Revenants; phase 3 whiteout: freezing builds away from lit campfires, it stomps the campfires out, sheds
 * armor (-6 armor, +30% speed). Fire deals x1.5.
 */
public class RimebackEntity extends ElsewhereBoss {
	public static final String ID = "rimeback";
	public static final int ACT_HURL = 1, ACT_THROW = 2, ACT_SLAM = 3, ACT_BREATH = 4, ACT_ROAR = 5, ACT_STOMP = 6;
	private static final Identifier SHATTER_ARMOR = BossRegistry.id("rimeback_shatter_armor");
	private static final Identifier SHATTER_SPEED = BossRegistry.id("rimeback_shatter_speed");

	private static final EntityDataAccessor<Boolean> DATA_HOLDING = SynchedEntityData.defineId(RimebackEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHATTERED = SynchedEntityData.defineId(RimebackEntity.class, EntityDataSerializers.BOOLEAN);

	private Vec3 hurlTarget = Vec3.ZERO;
	private float breathYaw;
	private double waveZ;
	private final Set<UUID> waveHit = new HashSet<>();
	private @Nullable BlockPos stompTarget;

	public RimebackEntity(EntityType<? extends RimebackEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 460.0)
			.add(Attributes.ARMOR, 16.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ATTACK_DAMAGE, 14.0)
			.add(Attributes.STEP_HEIGHT, 2.0)
			.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	public String bossId() {
		return ID;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.6F, 0.3F};
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	protected @Nullable SoundEvent roarSound() {
		return OwbSounds.RIMEBACK_GROAN;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HOLDING, false);
		builder.define(DATA_SHATTERED, false);
	}

	public boolean isHolding() {
		return this.entityData.get(DATA_HOLDING);
	}

	public boolean isShattered() {
		return this.entityData.get(DATA_SHATTERED);
	}

	@Override
	protected void registerAttacks() {
		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "glacier_hurl")
			.cooldown(160).windup(40).active(0).danger(BossAttack.Danger.BIG)
			.when(b -> b.getTarget() != null)
			.onStart(b -> {
				b.setAction(ACT_HURL);
				b.hurlTarget = b.getTarget().position();
			})
			.onWindup((b, t) -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				if (t == 10) {
					b.entityData.set(DATA_HOLDING, true);
					if (b.getTarget() != null) b.hurlTarget = b.getTarget().position();
					sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), b.getX(), b.getY() + 0.5, b.getZ(), 40, 1.0, 0.3, 1.0, 0.2);
					b.playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
				}
				if (t >= 10 && t % 3 == 0) {
					Vec3 c = b.hurlTarget.add(0, 0.1, 0);
					Telegraph.ring(sl, c, 3.0, Telegraph.RED);
					Telegraph.ring(sl, c, 1.5, ParticleTypes.SNOWFLAKE);
				}
				if (b.getTarget() != null) b.getLookControl().setLookAt(b.getTarget(), 30, 30);
			})
			.onFire(b -> {
				b.entityData.set(DATA_HOLDING, false);
				b.setAction(ACT_THROW);
				if (b.level() instanceof ServerLevel sl) {
					GlacierBoulder boulder = new GlacierBoulder(OwbEntities.GLACIER_BOULDER, sl);
					boulder.launch(b, b.position().add(0, 7.5, 0), b.hurlTarget, 18, b.num("glacier_hurl", "damage", 40.0F));
					sl.addFreshEntity(boulder);
					b.playSound(SoundEvents.WIND_CHARGE_THROW, 3.0F, 0.4F);
				}
			}));

		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "frost_breath")
			.cooldown(200).windup(16).active(30)
			.when(b -> b.getTarget() != null && b.distanceTo(b.getTarget()) < 16)
			.onStart(b -> {
				b.setAction(ACT_BREATH);
				b.breathYaw = Geo.yawOf(Geo.flatDir(b.position(), b.getTarget().position()));
			})
			.onWindup((b, t) -> {
				Geo.face(b, Geo.dirOfYaw(b.breathYaw));
				if (t % 4 == 0 && b.level() instanceof ServerLevel sl) Telegraph.cone(sl, b.position().add(0, 0.1, 0), b.breathYaw, 22, 12, Telegraph.YELLOW);
			})
			.onActive((b, t) -> {
				Geo.face(b, Geo.dirOfYaw(b.breathYaw));
				if (t % 2 == 0 && b.level() instanceof ServerLevel sl) {
					for (int i = 0; i < 3; i++) {
						float yaw = b.breathYaw + (b.random.nextFloat() - 0.5F) * 44F;
						Vec3 dir = Geo.dirOfYaw(yaw).add(0, -0.12 + b.random.nextFloat() * 0.1, 0);
						FrostShard s = new FrostShard(OwbEntities.FROST_SHARD, sl);
						s.setOwner(b);
						s.setPos(b.getX() + dir.x * 2, b.getY() + 5.0, b.getZ() + dir.z * 2);
						s.shoot(dir.x, dir.y - 0.25, dir.z, 1.1F, 1.0F);
						sl.addFreshEntity(s);
					}
					sl.sendParticles(ParticleTypes.SNOWFLAKE, b.getX(), b.getY() + 5, b.getZ(), 6, 0.5, 0.3, 0.5, 0.2);
				}
			}));

		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "fist_slam")
			.cooldown(80).windup(20).active(0).weight(2)
			.when(b -> b.getTarget() != null && b.distanceTo(b.getTarget()) < 5.5)
			.onStart(b -> b.setAction(ACT_SLAM))
			.onWindup((b, t) -> {
				if (t % 3 == 0 && b.level() instanceof ServerLevel sl) Telegraph.ring(sl, b.position().add(0, 0.1, 0), 4.5, Telegraph.YELLOW);
			})
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				b.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 3.0F, 0.6F);
				sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()), b.getX(), b.getY() + 0.2, b.getZ(), 80, 2.5, 0.2, 2.5, 0.2);
				for (ServerPlayer p : b.playersInArena(sl)) {
					if (p.distanceTo(b) <= 4.8 && Math.abs(p.getY() - b.getY()) < 3) {
						BossDamage.hit(b, p, b.num("fist_slam", "damage", 28.0F), BossRegistry.BOSS_CRUSH, true);
						Geo.push(p, new Vec3(0, 1.0, 0));
					}
				}
			}));

		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "avalanche")
			.cooldown(420).windup(60).active(50).phases(2, 3).danger(BossAttack.Danger.BIG)
			.when(b -> b.arena != null)
			.onStart(b -> {
				b.setAction(ACT_ROAR);
				b.playSound(OwbSounds.RIMEBACK_GROAN, 4.0F, 0.5F);
			})
			.onWindup((b, t) -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				int secs = 3 - t / 20;
				if (t % 20 == 1) {
					b.showCast(Component.translatable("attack.elsewhere.rimeback.avalanche_in", secs), BossAttack.Danger.BIG, secs / 3F);
					for (ServerPlayer p : b.playersInArena(sl)) sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 0.4F, 0.5F);
				}
				if (t % 4 == 0) {
					for (ServerPlayer p : b.playersInArena(sl)) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, Blocks.SNOW_BLOCK.defaultBlockState()), p.getX(), p.getY(), p.getZ(), 10, 3, 0.1, 3, 0.05);
				}
			})
			.onFire(b -> {
				b.waveZ = b.arena.center.getZ() - b.arena.radius - 2;
				b.waveHit.clear();
			})
			.onActive((b, t) -> b.tickAvalanche()));

		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "revenants")
			.cooldown(500).windup(30).active(0).phases(2, 3)
			.when(b -> b.liveMinions() < b.minionCap(3))
			.onStart(b -> b.setAction(ACT_ROAR))
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl)) return;
				for (int i = 0; i < 2; i++) {
					FrostboundRevenant r = OwbEntities.FROSTBOUND_REVENANT.create(sl, EntitySpawnReason.MOB_SUMMONED);
					if (r == null) continue;
					Vec3 at = b.position().add(Geo.dirOfYaw(b.getYRot() + (i == 0 ? 60 : -60)).scale(4));
					r.setPos(at.x, Geo.groundY(sl, at.x, at.z), at.z);
					r.finalizeSpawn(sl, sl.getCurrentDifficultyAt(b.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
					b.addMinion(sl, r);
				}
			}));

		this.attacks.add(BossAttack.<RimebackEntity>of(ID, "campfire_stomp")
			.cooldown(40).windup(14).active(0).phases(3).weight(4)
			.when(b -> b.stompTarget != null && b.distanceToSqr(Vec3.atCenterOf(b.stompTarget)) < 9.0)
			.onStart(b -> b.setAction(ACT_STOMP))
			.onFire(b -> {
				if (!(b.level() instanceof ServerLevel sl) || b.stompTarget == null) return;
				BlockState s = sl.getBlockState(b.stompTarget);
				if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
					sl.setBlock(b.stompTarget, s.setValue(CampfireBlock.LIT, false), 3);
					sl.sendParticles(ParticleTypes.LARGE_SMOKE, b.stompTarget.getX() + 0.5, b.stompTarget.getY() + 0.5, b.stompTarget.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
					b.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 2.0F, 0.6F);
				}
				b.stompTarget = null;
			}));
	}

	private void tickAvalanche() {
		if (!(level() instanceof ServerLevel sl) || this.arena == null) return;
		this.waveZ += 0.75;
		BossArena a = this.arena;
		// P13: the wall is drawn every 2nd tick (z spread widened to cover the 1.5-block step), was every tick
		if (this.tickCount % 2 == 0) for (int dx = -a.radius; dx <= a.radius; dx += 1) { // 1-block columns with 0.8 x spread: no stripes between them
			double x = a.center.getX() + dx + 0.5;
			sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SNOW_BLOCK.defaultBlockState()), x, a.floorY + 2.5, this.waveZ, 2, 0.8, 1.2, 0.55, 0.0);
			sl.sendParticles(ParticleTypes.SNOWFLAKE, x, a.floorY + 1.5, this.waveZ, 2, 0.8, 0.8, 0.55, 0.05);
		}
		for (ServerPlayer p : playersInArena(sl)) {
			if (this.waveHit.contains(p.getUUID()) || Math.abs(p.getZ() - this.waveZ) > 1.2) continue;
			this.waveHit.add(p.getUUID());
			if (sheltered(sl, p)) continue;
			BossDamage.hit(this, p, num("avalanche", "damage", 30.0F), BossRegistry.BOSS_CRUSH, true);
			p.setTicksFrozen(Math.max(p.getTicksFrozen(), 220));
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), this);
			Geo.push(p, new Vec3(0, 0.3, 0.8));
		}
	}

	/** Sheltered if a solid block stands between the player and the slope (north) within 3 blocks. */
	public static boolean sheltered(ServerLevel sl, LivingEntity p) {
		BlockPos b = p.blockPosition();
		for (int k = 1; k <= 3; k++) {
			BlockState s0 = sl.getBlockState(b.north(k));
			BlockState s1 = sl.getBlockState(b.north(k).above());
			if ((s0.isSolid() && !s0.is(Blocks.SNOW)) || (s1.isSolid() && !s1.is(Blocks.SNOW))) return true;
		}
		return false;
	}

	@Override
	protected void bossTick(ServerLevel level) {
		if (this.tickCount % 5 == 0) level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 6.2, getZ(), 2, 0.3, 0.1, 0.3, 0.02);
		LivingEntity t = getTarget();
		if (getPhase() >= 3) whiteout(level);
		if (currentAttackId() != null) {
			getNavigation().stop();
			return;
		}
		if (getPhase() >= 3 && this.tickCount % 20 == 0) {
			this.stompTarget = nearestLitCampfire(level);
			if (this.stompTarget != null) getNavigation().moveTo(this.stompTarget.getX() + 0.5, this.stompTarget.getY(), this.stompTarget.getZ() + 0.5, 1.0);
		}
		if (this.stompTarget == null && t != null && this.tickCount % 10 == 0) {
			if (distanceTo(t) > 4.0) getNavigation().moveTo(t, 1.0);
			else getNavigation().stop();
		}
		if (t != null) getLookControl().setLookAt(t, 20, 20);
	}

	private @Nullable BlockPos nearestLitCampfire(ServerLevel level) {
		if (this.arena == null) return null;
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		BlockPos c = this.arena.center;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-this.arena.radius, -2, -this.arena.radius), c.offset(this.arena.radius, 4, this.arena.radius))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
				double d = p.distSqr(blockPosition());
				if (d < bd) {
					bd = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** Phase 3 blizzard: freeze builds anywhere more than 4 blocks from a lit campfire. */
	private void whiteout(ServerLevel level) {
		if (this.tickCount % 10 != 0 || this.arena == null) return;
		for (ServerPlayer p : playersInArena(level)) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.5, p.getZ(), 12, 3.0, 1.5, 3.0, 0.05);
			if (!nearLitCampfire(level, p.blockPosition(), 4)) p.setTicksFrozen(Math.min(p.getTicksFrozen() + 32, 300));
		}
	}

	public static boolean nearLitCampfire(ServerLevel level, BlockPos at, int r) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT) && p.distSqr(at) <= r * r) return true;
		}
		return false;
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int phase) {
		this.entityData.set(DATA_HOLDING, false);
		if (phase == 3) {
			this.entityData.set(DATA_SHATTERED, true);
			AttributeInstance armor = getAttribute(Attributes.ARMOR);
			if (armor != null && armor.getModifier(SHATTER_ARMOR) == null) armor.addPermanentModifier(new AttributeModifier(SHATTER_ARMOR, -6.0, AttributeModifier.Operation.ADD_VALUE));
			AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && speed.getModifier(SHATTER_SPEED) == null) speed.addPermanentModifier(new AttributeModifier(SHATTER_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), getX(), getY() + 4, getZ(), 120, 1.2, 2.0, 1.2, 0.3);
			playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.4F);
		}
	}

	private void unshatter() {
		this.entityData.set(DATA_SHATTERED, false);
		this.entityData.set(DATA_HOLDING, false);
		AttributeInstance armor = getAttribute(Attributes.ARMOR);
		if (armor != null) armor.removeModifier(SHATTER_ARMOR);
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(SHATTER_SPEED);
	}

	@Override
	protected void onFightStart(ServerLevel level) {
		unshatter();
	}

	@Override
	protected void onFightReset(ServerLevel level) {
		unshatter();
		this.stompTarget = null;
	}

	/** Burn it: fire and Fire Aspect deal x1.5. */
	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		if (!fire && source.getEntity() instanceof LivingEntity attacker) {
			var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT);
			fire = EnchantmentHelper.getItemEnchantmentLevel(holder, attacker.getMainHandItem()) > 0;
		}
		return fire ? amount * 1.5F : amount;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 16, 4, 20, p.getY() - 1, 6);
	}

	@Override
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("rimeheart_maul", () -> new ItemStack(OwbItems.RIMEHEART_MAUL)),
			dev.elsewhere.boss.kit.DropRule.other("rimewalker_boots", () -> new ItemStack(OwbItems.RIMEWALKER_BOOTS)))));
		out.add(new ItemStack(OwbItems.GLACIER_WATER, 1 + this.random.nextInt(3)));
		return out;
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(dev.elsewhere.overworld_bosses.OwbBlocks.FROZEN_HEART.asItem());
	}

	@Override
	protected @Nullable Item tierSignature() {
		return SignatureGear.AEGIS_OF_THE_LADDER;
	}
}
