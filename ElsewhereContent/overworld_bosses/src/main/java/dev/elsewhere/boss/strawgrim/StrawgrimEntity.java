package dev.elsewhere.boss.strawgrim;

import dev.elsewhere.overworld_bosses.GearStats;
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
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Strawgrim, the Hollow Shepherd (BOSSES.md #2, Tier I). A scarecrow on a fence post that hops between furrows: a
 * 270-degree Reap, Furrow fang lines, Murder Crows diving at a glowing-marked player; in phase 2 it burns and fires
 * seed-fireball volleys and sets hay fires; in phase 3 the body collapses (invulnerable, reaping blindly) and the
 * pumpkin head flies as its own entity, the only hittable part.
 */
public class StrawgrimEntity extends ElsewhereBoss {
	public static final String ID = "strawgrim";
	public static final int ACT_REAP = 1, ACT_FURROW = 2, ACT_CROWS = 3, ACT_VOLLEY = 4, ACT_HOP = 5, ACT_IGNITE = 6;
	public static final double REAP_RADIUS = 4.5;

	private static final EntityDataAccessor<Boolean> DATA_HEADLESS = SynchedEntityData.defineId(StrawgrimEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(StrawgrimEntity.class, EntityDataSerializers.BOOLEAN);

	private float reapYaw;
	private Vec3 hopFrom = Vec3.ZERO;
	private Vec3 hopTo = Vec3.ZERO;
	private int hopTick = -1;
	private int hopCooldown = 60;
	private @Nullable StrawgrimHead head;
	private boolean headForward;

	public StrawgrimEntity(EntityType<? extends StrawgrimEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 350.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0)
			.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	public String bossId() {
		return ID;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.65F, 0.3F};
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	protected @Nullable SoundEvent roarSound() {
		return OwbSounds.STRAWGRIM_CAW;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HEADLESS, false);
		builder.define(DATA_BURNING, false);
	}

	public boolean isHeadless() {
		return this.entityData.get(DATA_HEADLESS);
	}

	public boolean isEffigyBurning() {
		return this.entityData.get(DATA_BURNING);
	}

	@Override
	public boolean displayFireAnimation() {
		return isEffigyBurning() && !isHeadless();
	}

	// ---- attacks -------------------------------------------------------------------------------------------------

	@Override
	protected void registerAttacks() {
		this.attacks.add(BossAttack.<StrawgrimEntity>of(ID, "reap")
			.cooldown(90).windup(20).active(6).danger(BossAttack.Danger.BIG).weight(3)
			.when(b -> b.isHeadless() || (b.getTarget() != null && b.distanceTo(b.getTarget()) <= REAP_RADIUS + 1.0))
			.onStart(b -> {
				b.setAction(ACT_REAP);
				b.reapYaw = b.isHeadless() || b.getTarget() == null ? b.random.nextFloat() * 360F : Geo.yawOf(Geo.flatDir(b.position(), b.getTarget().position()));
				Geo.face(b, Geo.dirOfYaw(b.reapYaw));
				b.playSound(OwbSounds.STRAWGRIM_CREAK, 2.0F, 0.7F);
			})
			.onWindup((b, t) -> {
				Geo.face(b, Geo.dirOfYaw(b.reapYaw));
				if (t % 3 == 0 && b.level() instanceof ServerLevel sl) {
					Telegraph.arc(sl, b.position().add(0, 0.15, 0), REAP_RADIUS, b.reapYaw - 135, b.reapYaw + 135, ParticleTypes.CRIT);
					Telegraph.arc(sl, b.position().add(0, 0.12, 0), REAP_RADIUS, b.reapYaw - 135, b.reapYaw + 135, Telegraph.dust(Telegraph.RED, 1.0F));
				}
			})
			.onFire(StrawgrimEntity::sweep));

		this.attacks.add(BossAttack.<StrawgrimEntity>of(ID, "furrow")
			.cooldown(140).windup(16).active(10).phases(1, 2)
			.when(b -> b.getTarget() != null && b.distanceTo(b.getTarget()) > 3.0)
			.onStart(b -> {
				b.setAction(ACT_FURROW);
				b.reapYaw = Geo.yawOf(Geo.flatDir(b.position(), b.getTarget().position()));
			})
			.onWindup((b, t) -> Geo.face(b, Geo.dirOfYaw(b.reapYaw)))
			.onFire(StrawgrimEntity::furrow));

		this.attacks.add(BossAttack.<StrawgrimEntity>of(ID, "crow_murder")
			.cooldown(300).windup(20).active(0)
			.when(b -> b.getTarget() != null && b.countCrows() < b.minionCap(5))
			.onStart(b -> {
				b.setAction(ACT_CROWS);
				b.playSound(OwbSounds.STRAWGRIM_CAW, 3.0F, 0.9F);
			})
			.onFire(StrawgrimEntity::releaseCrows));

		this.attacks.add(BossAttack.<StrawgrimEntity>of(ID, "fire_volley")
			.cooldown(40).windup(8).active(0).phases(2, 3).silent()
			.when(b -> b.getTarget() != null)
			.onStart(b -> b.setAction(b.isHeadless() ? 0 : ACT_VOLLEY))
			.onFire(StrawgrimEntity::volley));

		this.attacks.add(BossAttack.<StrawgrimEntity>of(ID, "hay_fire")
			.cooldown(220).windup(24).active(0).phases(2)
			.when(b -> b.getTarget() != null)
			.onWindup((b, t) -> {
				if (t % 4 == 0 && b.level() instanceof ServerLevel sl) {
					for (ServerPlayer p : b.playersInArena(sl)) sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 6, 2.0, 0.05, 2.0, 0.01);
				}
			})
			.onFire(StrawgrimEntity::hayFires));
	}

	private void sweep() {
		if (!(level() instanceof ServerLevel sl)) return;
		playSound(OwbSounds.STRAWGRIM_SWEEP, 2.5F, 0.8F);
		sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.0, getZ(), 8, 2.5, 0.2, 2.5, 0.0);
		float raw = num("reap", "damage", 36.0F);
		for (ServerPlayer p : playersInArena(sl)) {
			double d = Math.sqrt(p.distanceToSqr(getX(), p.getY(), getZ()));
			if (d > REAP_RADIUS || Math.abs(p.getY() - getY()) > 3.0) continue;
			float yawTo = Geo.yawOf(Geo.flatDir(position(), p.position()));
			float diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(yawTo - this.reapYaw));
			if (diff <= 135.0F || d < 1.0) BossDamage.hit(this, p, raw, BossRegistry.BOSS_ATTACK, true);
		}
	}

	private void furrow() {
		if (!(level() instanceof ServerLevel sl)) return;
		Vec3 dir = Geo.dirOfYaw(this.reapYaw);
		playSound(SoundEvents.EVOKER_CAST_SPELL, 2.0F, 0.7F);
		for (int i = 2; i <= 14; i++) {
			Vec3 p = position().add(dir.scale(i));
			BlockPos ground = BlockPos.containing(p.x, getY() - 0.5, p.z);
			for (int k = 0; k < 3 && !sl.getBlockState(ground).isSolid(); k++) ground = ground.below();
			BlockState g = sl.getBlockState(ground);
			if ((g.is(Blocks.FARMLAND) || g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT)) && (this.arena == null || this.arena.containsBlock(ground))) {
				sl.setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), 3);
			}
			EvokerFangs fangs = new EvokerFangs(sl, p.x, ground.getY() + 1, p.z, (float) Math.toRadians(this.reapYaw + 90), 4 + i * 2, this);
			fangs.addTag(BossDamage.MINION_TAG);
			sl.addFreshEntity(fangs);
		}
	}

	private int countCrows() {
		int n = 0;
		for (var e : minions()) if (e instanceof MurderCrow) n++;
		return n;
	}

	private void releaseCrows() {
		if (!(level() instanceof ServerLevel sl) || getTarget() == null) return;
		LivingEntity marked = getTarget();
		marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), this);
		int n = Math.min(5, minionCap(5) - countCrows());
		for (int i = 0; i < n; i++) {
			MurderCrow crow = OwbEntities.MURDER_CROW.create(sl, EntitySpawnReason.MOB_SUMMONED);
			if (crow == null) continue;
			double a = Math.PI * 2 * i / n;
			crow.setPos(getX() + Math.cos(a) * 2, getY() + 4.5, getZ() + Math.sin(a) * 2);
			crow.finalizeSpawn(sl, sl.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			crow.setMarked(marked);
			addMinion(sl, crow);
		}
	}

	private void volley() {
		if (!(level() instanceof ServerLevel sl) || getTarget() == null) return;
		Vec3 from = isHeadless() && this.head != null && this.head.isAlive() ? this.head.position().add(0, 0.4, 0) : position().add(0, 3.5, 0);
		LivingEntity t = getTarget();
		for (int i = 0; i < 3; i++) {
			Vec3 aim = t.position().add(0, 1.0, 0).add((i - 1) * 1.2, 0, (i - 1) * -0.6).subtract(from).normalize();
			SeedFireball f = new SeedFireball(OwbEntities.SEED_FIREBALL, sl);
			f.setOwner(this);
			f.setPos(from.add(aim.scale(1.0)));
			f.shoot(aim.x, aim.y, aim.z, 0.9F, 2.0F);
			sl.addFreshEntity(f);
		}
		playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.8F);
	}

	private void hayFires() {
		if (!(level() instanceof ServerLevel sl) || this.arena == null) return;
		playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.7F);
		for (ServerPlayer p : playersInArena(sl)) {
			for (int i = 0; i < 4; i++) {
				BlockPos at = p.blockPosition().offset(this.random.nextInt(7) - 3, 0, this.random.nextInt(7) - 3);
				for (int k = 0; k < 3 && !sl.getBlockState(at).isAir(); k++) at = at.above();
				if (sl.getBlockState(at).isAir() && sl.getBlockState(at.below()).isSolid() && this.arena.containsBlock(at)) {
					sl.setBlock(at, BaseFireBlock.getState(sl, at), 3);
				}
			}
		}
	}

	// ---- movement, phases ---------------------------------------------------------------------------------------

	@Override
	protected void bossTick(ServerLevel level) {
		if (this.tickCount % 6 == 0) level.sendParticles(isEffigyBurning() ? ParticleTypes.SMALL_FLAME : ParticleTypes.WHITE_ASH, getX(), getY() + 2.8, getZ(), 3, 0.5, 0.8, 0.5, 0.01);
		if (isHeadless()) {
			if (this.head == null || !this.head.isAlive()) spawnHead(level);
			return;
		}
		LivingEntity t = getTarget();
		if (t != null && currentAttackId() == null && this.hopTick < 0) getLookControl().setLookAt(t, 20.0F, 20.0F);
		if (this.hopTick >= 0) {
			this.hopTick++;
			float f = Math.min(1.0F, this.hopTick / 12.0F);
			double y = this.hopFrom.y + (this.hopTo.y - this.hopFrom.y) * f + 2.5 * 4 * f * (1 - f);
			setPos(this.hopFrom.x + (this.hopTo.x - this.hopFrom.x) * f, y, this.hopFrom.z + (this.hopTo.z - this.hopFrom.z) * f);
			setDeltaMovement(Vec3.ZERO);
			if (f >= 1.0F) {
				this.hopTick = -1;
				this.noPhysics = false;
				setNoGravity(false);
				if (getAction() == ACT_HOP) setAction(0);
				level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.4, 0.05, 0.4, 0.02);
				playSound(OwbSounds.STRAWGRIM_CREAK, 1.5F, 1.2F);
			}
			return;
		}
		if (--this.hopCooldown <= 0 && currentAttackId() == null && t != null && this.arena != null) {
			this.hopCooldown = 70 + this.random.nextInt(40);
			double d = distanceTo(t);
			if (d > REAP_RADIUS || this.random.nextFloat() < 0.4F) {
				// Hop to a furrow near the target, 3-5 blocks away from it.
				Vec3 away = Geo.flatDir(t.position(), position());
				double ang = (this.random.nextFloat() - 0.5) * 1.6;
				Vec3 rot = new Vec3(away.x * Math.cos(ang) - away.z * Math.sin(ang), 0, away.x * Math.sin(ang) + away.z * Math.cos(ang));
				Vec3 dest = t.position().add(rot.scale(3.0 + this.random.nextFloat() * 2.0));
				if (!this.arena.contains(dest)) dest = this.arena.centerVec();
				this.hopFrom = position();
				this.hopTo = new Vec3(dest.x, Geo.groundY(level, dest.x, dest.z), dest.z);
				if (Math.abs(this.hopTo.y - this.arena.floorY - 1) > 3) this.hopTo = new Vec3(dest.x, this.arena.floorY + 1, dest.z);
				this.hopTick = 0;
				this.noPhysics = true;
				setNoGravity(true);
				setAction(ACT_HOP);
			}
		}
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int phase) {
		if (phase == 2) {
			this.entityData.set(DATA_BURNING, true);
			setAction(ACT_IGNITE);
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 2.5, getZ(), 80, 0.6, 1.4, 0.6, 0.08);
			playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
		} else if (phase == 3) {
			endHop();
			this.entityData.set(DATA_HEADLESS, true);
			spawnHead(level);
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 3.8, getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		}
	}

	private void endHop() {
		if (this.hopTick >= 0) {
			setPos(this.hopTo);
			this.hopTick = -1;
		}
		this.noPhysics = false;
		setNoGravity(false);
	}

	private void spawnHead(ServerLevel level) {
		if (this.head != null && this.head.isAlive()) return;
		StrawgrimHead h = OwbEntities.STRAWGRIM_HEAD.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (h == null) return;
		h.setPos(getX(), getY() + 3.8, getZ());
		h.setBody(this);
		addMinion(level, h);
		this.head = h;
	}

	public @Nullable StrawgrimHead head() {
		return this.head;
	}

	/** While headless, only the flying head can be hurt; its damage is forwarded here. */
	@Override
	protected boolean isDamageGated(ServerLevel level, DamageSource source) {
		return isHeadless() && !this.headForward;
	}

	public boolean hurtFromHead(ServerLevel level, DamageSource source, float amount) {
		this.headForward = true;
		try {
			return hurtServer(level, source, amount);
		} finally {
			this.headForward = false;
		}
	}

	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
		return amount;
	}

	@Override
	protected void onFightStart(ServerLevel level) {
		this.entityData.set(DATA_HEADLESS, false);
		this.entityData.set(DATA_BURNING, false);
		this.hopCooldown = 60;
	}

	@Override
	protected void onFightReset(ServerLevel level) {
		endHop();
		this.entityData.set(DATA_HEADLESS, false);
		this.entityData.set(DATA_BURNING, false);
		clearFire();
		if (this.head != null) this.head.discard();
		this.head = null;
	}

	@Override
	protected void onFightWon(ServerLevel level) {
		endHop();
		if (this.head != null) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.head.getX(), this.head.getY(), this.head.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
			this.head.discard();
		}
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), getX(), getY() + 2, getZ(), 120, 0.6, 1.2, 0.6, 0.2);
	}

	@Override
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 12, 4, 14, p.getY() - 1, 6);
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	// ---- loot -------------------------------------------------------------------------------------------------------

	@Override
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("harvest_scythe", () -> new ItemStack(OwbItems.HARVEST_SCYTHE)),
			dev.elsewhere.boss.kit.DropRule.other("scarecrows_grin", () -> new ItemStack(OwbItems.SCARECROWS_GRIN)))));
		out.add(new ItemStack(OwbItems.HARVEST_MOON_SEEDS, 2 + this.random.nextInt(4)));
		if (FabricLoader.getInstance().isModLoaded("testmeter")) out.add(new ItemStack(OwbItems.HARVEST_LOAF, GearStats.FOOD_DROP_MIN));
		if (FabricLoader.getInstance().isModLoaded("greenleaf")) out.add(new ItemStack(OwbItems.HARVEST_MOON_BUD, 1));
		return out;
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN);
	}

	@Override
	protected @Nullable Item tierSignature() {
		return SignatureGear.CHALLENGERS_CLEAVER;
	}
}
