package dev.elsewhere.boss.gulpmother;

import dev.elsewhere.overworld_bosses.GearStats;
import dev.elsewhere.overworld_bosses.Geo;
import dev.elsewhere.overworld_bosses.OwbBlocks;
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
import java.util.UUID;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Old Gulpmother, the Bog Sovereign (BOSSES.md #1, Tier I). A bloated mangrove toad that keeps 6-10 blocks away and
 * fights with a telegraphed tongue (which swallows the player it hits), belly flops onto a shadow ring, spits
 * frogspawn that hatches Mire Biters (phase 2) and eats its own biters to heal (phase 3).
 */
public class GulpmotherEntity extends ElsewhereBoss {
	public static final String ID = "gulpmother";
	public static final int ACT_TONGUE = 1, ACT_FLOP = 2, ACT_SWALLOW = 3, ACT_SPIT = 4, ACT_EAT = 5, ACT_EMERGE = 6;
	public static final float TONGUE_RANGE = 12.0F;

	private static final EntityDataAccessor<Float> DATA_TONGUE = SynchedEntityData.defineId(GulpmotherEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_SWALLOWING = SynchedEntityData.defineId(GulpmotherEntity.class, EntityDataSerializers.BOOLEAN);

	private BossAttack<GulpmotherEntity> tongueLash;
	private Vec3 lashDir = new Vec3(0, 0, 1);
	private @Nullable UUID swallowed;
	private int swallowTicks;
	private int sneakPresses;
	private boolean lastSneak;
	private Vec3 flopFrom = Vec3.ZERO;
	private Vec3 flopTo = Vec3.ZERO;
	private boolean flying;
	private @Nullable Entity eatTarget;
	private int emergeTicks;

	public GulpmotherEntity(EntityType<? extends GulpmotherEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 340.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0)
			.add(Attributes.STEP_HEIGHT, 1.5)
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
		return BossEvent.BossBarColor.GREEN;
	}

	@Override
	protected @Nullable SoundEvent roarSound() {
		return OwbSounds.GULPMOTHER_CROAK;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TONGUE, 0.0F);
		builder.define(DATA_SWALLOWING, false);
	}

	public float getTongue() {
		return this.entityData.get(DATA_TONGUE);
	}

	public boolean isSwallowing() {
		return this.entityData.get(DATA_SWALLOWING);
	}

	// ---- attacks -------------------------------------------------------------------------------------------------

	@Override
	protected void registerAttacks() {
		this.tongueLash = BossAttack.<GulpmotherEntity>of(ID, "tongue_lash")
			.cooldown(80).windup(32).active(10)
			.when(b -> b.getTarget() != null && b.swallowed == null && b.distanceTo(b.getTarget()) < TONGUE_RANGE + 2)
			.onStart(b -> {
				b.setAction(ACT_TONGUE);
				b.lashDir = Geo.flatDir(b.position(), b.getTarget().position());
				b.playSound(OwbSounds.GULPMOTHER_GAPE, 2.0F, 1.0F);
			})
			.onWindup((b, t) -> {
				Geo.face(b, b.lashDir);
				// 0.6 s gape, then the pink line is drawn for the rest of the wind-up.
				if (t >= b.tongueLash.windup - 20 && t % 3 == 0) b.drawTongueLine(0xFF6FA8);
			})
			.onFire(GulpmotherEntity::fireTongue)
			.onActive((b, t) -> b.entityData.set(DATA_TONGUE, t <= 3 ? TONGUE_RANGE * t / 3F : Math.max(0, TONGUE_RANGE * (10 - t) / 7F)))
			.onEnd(b -> b.entityData.set(DATA_TONGUE, 0.0F));
		this.attacks.add(this.tongueLash);

		this.attacks.add(BossAttack.<GulpmotherEntity>of(ID, "belly_flop")
			.cooldown(240).windup(16).active(14).danger(BossAttack.Danger.BIG)
			.when(b -> b.getTarget() != null && b.swallowed == null && b.onGround())
			.onStart(b -> {
				b.setAction(ACT_FLOP);
				LivingEntity t = b.getTarget();
				b.flopFrom = b.position();
				Vec3 to = t.position();
				if (b.arena != null && !b.arena.contains(to)) to = b.arena.centerVec();
				b.flopTo = new Vec3(to.x, b.getY(), to.z);
				b.playSound(OwbSounds.GULPMOTHER_CROAK, 2.5F, 0.8F);
			})
			.onWindup((b, t) -> {
				Geo.face(b, Geo.flatDir(b.position(), b.flopTo));
				if (t % 3 == 0) b.drawFlopRing();
			})
			.onFire(b -> {
				b.flying = true;
				b.noPhysics = true;
				b.setNoGravity(true);
			})
			.onActive((b, t) -> {
				float f = t / 14.0F;
				double y = Mth_lerp(f, b.flopFrom.y, b.flopTo.y) + 8.0 * 4 * f * (1 - f);
				b.setPos(Mth_lerp(f, b.flopFrom.x, b.flopTo.x), y, Mth_lerp(f, b.flopFrom.z, b.flopTo.z));
				b.setDeltaMovement(Vec3.ZERO);
				if (t % 3 == 0) b.drawFlopRing();
				if (t == 14) b.landFlop();
			})
			.onEnd(b -> {
				if (b.flying) b.landFlop();
			}));

		this.attacks.add(BossAttack.<GulpmotherEntity>of(ID, "spawning_season")
			.cooldown(300).windup(20).active(0).phases(2, 3)
			.when(b -> b.swallowed == null && b.liveMinions() < b.minionCap(6))
			.onStart(b -> {
				b.setAction(ACT_SPIT);
				b.playSound(OwbSounds.GULPMOTHER_GULP, 2.0F, 0.7F);
			})
			.onFire(GulpmotherEntity::spitFrogspawn));

		this.attacks.add(BossAttack.<GulpmotherEntity>of(ID, "devour")
			.cooldown(120).windup(20).active(8).phases(3).danger(BossAttack.Danger.INTERACT)
			.when(b -> b.swallowed == null && b.findBiter() != null)
			.onStart(b -> {
				b.setAction(ACT_EAT);
				b.eatTarget = b.findBiter();
			})
			.onWindup((b, t) -> {
				if (b.eatTarget == null || !b.eatTarget.isAlive()) return;
				Geo.face(b, Geo.flatDir(b.position(), b.eatTarget.position()));
				if (t % 3 == 0 && b.level() instanceof ServerLevel sl) {
					Telegraph.line(sl, b.mouthPos(), b.eatTarget.position().add(0, 0.2, 0), 0.6, 0x3CFF4A);
				}
			})
			.onFire(b -> {
				if (b.eatTarget != null && b.eatTarget.isAlive() && b.distanceTo(b.eatTarget) <= TONGUE_RANGE + 3) {
					b.entityData.set(DATA_TONGUE, (float) b.distanceTo(b.eatTarget));
					b.eatTarget.discard();
					b.heal(b.getMaxHealth() * 0.05F);
					b.playSound(OwbSounds.GULPMOTHER_GULP, 2.5F, 1.0F);
					if (b.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX(), b.getY() + 2.5, b.getZ(), 12, 1.0, 0.5, 1.0, 0.0);
				}
				b.eatTarget = null;
			})
			.onEnd(b -> b.entityData.set(DATA_TONGUE, 0.0F)));
	}

	private static double Mth_lerp(float f, double a, double b) {
		return a + (b - a) * f;
	}

	public Vec3 mouthPos() {
		Vec3 fwd = Geo.dirOfYaw(getYRot());
		return position().add(fwd.scale(2.3)).add(0, 1.0, 0);
	}

	private void drawTongueLine(int color) {
		if (!(level() instanceof ServerLevel sl)) return;
		Vec3 start = position().add(this.lashDir.scale(2.2)).add(0, 0.15, 0);
		Vec3 end = start.add(this.lashDir.scale(TONGUE_RANGE - 2.2));
		Telegraph.line(sl, start, end, 1.2, color);
	}

	private void drawFlopRing() {
		if (!(level() instanceof ServerLevel sl)) return;
		Vec3 c = new Vec3(this.flopTo.x, groundAt(sl, this.flopTo) + 0.1, this.flopTo.z);
		Telegraph.ring(sl, c, 4.5, Telegraph.RED);
		Telegraph.ring(sl, c, 2.0, Telegraph.RED);
	}

	private double groundAt(ServerLevel sl, Vec3 p) {
		BlockPos.MutableBlockPos m = BlockPos.containing(p.x, p.y + 2, p.z).mutable();
		for (int i = 0; i < 8; i++) {
			BlockState s = sl.getBlockState(m);
			if (!s.isAir() && !s.getFluidState().isEmpty() || s.isSolid() || s.is(Blocks.LILY_PAD)) return m.getY() + (s.is(Blocks.LILY_PAD) ? 0.1 : 1.0);
			m.move(0, -1, 0);
		}
		return p.y;
	}

	private void fireTongue() {
		if (!(level() instanceof ServerLevel sl)) return;
		playSound(OwbSounds.GULPMOTHER_TONGUE, 2.0F, getPhase() >= 3 ? 1.3F : 1.0F);
		Vec3 start = position().add(this.lashDir.scale(2.0)).add(0, 1.0, 0);
		Vec3 end = start.add(this.lashDir.scale(TONGUE_RANGE - 2.0));
		ServerPlayer hitPlayer = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : playersInArena(sl)) {
			double d = Geo.flatDistToSegment(p.position(), start, end);
			double along = p.position().subtract(start).dot(this.lashDir);
			if (d <= 1.2 && along >= -1.0 && along < best && Math.abs(p.getY() - getY()) < 3.5) {
				best = along;
				hitPlayer = p;
			}
		}
		if (hitPlayer == null) return;
		float raw = num("tongue_lash", "damage", 16.0F);
		boolean hurt = BossDamage.hit(this, hitPlayer, raw, BossRegistry.BOSS_ATTACK, false);
		if (hurt && !hitPlayer.isBlocking()) startSwallow(hitPlayer);
	}

	private void landFlop() {
		this.flying = false;
		this.noPhysics = false;
		setNoGravity(false);
		if (!(level() instanceof ServerLevel sl)) return;
		double gy = groundAt(sl, this.flopTo);
		setPos(this.flopTo.x, Math.max(gy, this.flopTo.y - 3), this.flopTo.z);
		playSound(OwbSounds.GULPMOTHER_SPLAT, 3.0F, 0.8F);
		sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 120, 3.0, 0.3, 3.0, 0.2);
		sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 30, 3.0, 0.1, 3.0, 0.05);
		float center = num("belly_flop", "damage", 36.0F);
		float edge = num("belly_flop", "edge_damage", 18.0F);
		for (ServerPlayer p : playersInArena(sl)) {
			double d = Math.sqrt(p.distanceToSqr(getX(), p.getY(), getZ()));
			if (d > 4.5 || p.getY() - getY() > 3.0) continue;
			BossDamage.hit(this, p, d <= 2.0 ? center : edge, BossRegistry.BOSS_CRUSH, true);
			Vec3 away = Geo.flatDir(position(), p.position());
			Geo.push(p, new Vec3(away.x * 1.4, 0.55, away.z * 1.4));
		}
		// Lily pads it lands on sink, removing platforms (restored with the arena).
		BlockPos c = blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
			if (sl.getBlockState(q).is(Blocks.LILY_PAD) && q.distSqr(c) <= 20 && (this.arena == null || this.arena.containsBlock(q))) {
				sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
				sl.sendParticles(ParticleTypes.BUBBLE_POP, q.getX() + 0.5, q.getY(), q.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.02);
			}
		}
	}

	private void spitFrogspawn() {
		if (!(level() instanceof ServerLevel sl) || this.arena == null) return;
		List<BlockPos> spots = waterSpots(sl, 3);
		for (BlockPos spot : spots) {
			FrogspawnGlob glob = new FrogspawnGlob(OwbEntities.FROGSPAWN_GLOB, sl);
			glob.setOwner(this);
			Vec3 from = mouthPos().add(0, 1.0, 0);
			glob.setPos(from);
			glob.launchTo(Vec3.atBottomCenterOf(spot));
			glob.addTag(BossDamage.MINION_TAG);
			sl.addFreshEntity(glob);
		}
		playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.6F);
	}

	/** Water surface spots in the arena (air above water), spread out. */
	private List<BlockPos> waterSpots(ServerLevel sl, int n) {
		List<BlockPos> out = new ArrayList<>();
		BossArena a = this.arena;
		for (int tries = 0; tries < 80 && out.size() < n; tries++) {
			int dx = this.random.nextInt(a.radius * 2 + 1) - a.radius;
			int dz = this.random.nextInt(a.radius * 2 + 1) - a.radius;
			if (dx * dx + dz * dz > (a.radius - 2) * (a.radius - 2)) continue;
			BlockPos col = a.center.offset(dx, 0, dz);
			for (int y = a.floorY + 3; y >= a.floorY - 3; y--) {
				BlockPos w = new BlockPos(col.getX(), y, col.getZ());
				if (sl.getFluidState(w).isSource() && sl.getBlockState(w.above()).isAir()) {
					BlockPos above = w.above();
					if (out.stream().noneMatch(o -> o.distSqr(above) < 9)) out.add(above);
					break;
				}
			}
		}
		while (out.size() < n) {
			out.add(BlockPos.containing(a.centerVec().add(this.random.nextInt(9) - 4, 0, this.random.nextInt(9) - 4)));
		}
		return out;
	}

	private @Nullable Entity findBiter() {
		Entity best = null;
		double bd = Double.MAX_VALUE;
		for (Entity e : minions()) {
			if (e instanceof MireBiter && e.isAlive()) {
				double d = e.distanceToSqr(this);
				if (d < bd && d < (TONGUE_RANGE + 3) * (TONGUE_RANGE + 3)) {
					bd = d;
					best = e;
				}
			}
		}
		return best;
	}

	// ---- swallow --------------------------------------------------------------------------------------------------

	private void startSwallow(ServerPlayer p) {
		this.swallowed = p.getUUID();
		this.swallowTicks = 0;
		this.sneakPresses = 0;
		this.lastSneak = p.isShiftKeyDown();
		this.entityData.set(DATA_SWALLOWING, true);
		setAction(ACT_SWALLOW);
		playSound(OwbSounds.GULPMOTHER_GULP, 3.0F, 0.8F);
		p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.gulpmother.swallowed"));
	}

	public @Nullable ServerPlayer swallowedPlayer(ServerLevel level) {
		return this.swallowed == null ? null : level.getServer().getPlayerList().getPlayer(this.swallowed);
	}

	public void releaseSwallowed(boolean spit) {
		if (this.swallowed == null) return;
		ServerPlayer p = level() instanceof ServerLevel sl ? swallowedPlayer(sl) : null;
		this.swallowed = null;
		this.entityData.set(DATA_SWALLOWING, false);
		if (getAction() == ACT_SWALLOW) setAction(0);
		if (p != null) {
			p.removeEffect(MobEffects.DARKNESS);
			Vec3 fwd = Geo.dirOfYaw(getYRot());
			Vec3 out = position().add(fwd.scale(3.0)).add(0, 1.0, 0);
			p.teleportTo(out.x, out.y, out.z);
			if (spit) Geo.push(p, new Vec3(fwd.x * 1.0, 0.4, fwd.z * 1.0));
			playSound(SoundEvents.SLIME_SQUISH, 2.0F, 0.6F);
		}
	}

	private void tickSwallow(ServerLevel sl) {
		ServerPlayer p = swallowedPlayer(sl);
		if (p == null || !p.isAlive() || p.isSpectator()) {
			releaseSwallowed(false);
			return;
		}
		this.swallowTicks++;
		Vec3 belly = position().add(0, 0.6, 0);
		p.teleportTo(belly.x, belly.y, belly.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
		if (this.swallowTicks % 20 == 0) {
			p.hurtServer(sl, BossDamage.source(sl, BossRegistry.BOSS_DOT, this, this), BossDamage.scaled(sl, num("swallow", "dot", 1.0F)));
		}
		boolean sneak = p.isShiftKeyDown();
		if (sneak && !this.lastSneak) {
			this.sneakPresses++;
			sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.5, getZ(), 8, 1.0, 0.5, 1.0, 0.1);
		}
		this.lastSneak = sneak;
		if (this.sneakPresses >= 6 || this.swallowTicks >= 120) releaseSwallowed(true);
	}

	/** Struggle press, for tests and alternate input. */
	public void struggle() {
		this.sneakPresses++;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		// An ally hitting the glowing belly frees the swallowed player.
		if (hurt && this.swallowed != null && source.getEntity() instanceof ServerPlayer p && !p.getUUID().equals(this.swallowed)) {
			releaseSwallowed(true);
		}
		return hurt;
	}

	// ---- movement and phases ----------------------------------------------------------------------------------------

	@Override
	protected void bossTick(ServerLevel level) {
		if (this.emergeTicks > 0) {
			this.emergeTicks--;
			if (this.emergeTicks % 4 == 0) level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 30, 2.0, 0.5, 2.0, 0.2);
			if (this.emergeTicks == 0 && getAction() == ACT_EMERGE) setAction(0);
		}
		if (this.swallowed != null) {
			tickSwallow(level);
			getNavigation().stop();
			return;
		}
		if (this.tickCount % 10 == 0) level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.3, getZ(), 4, 2.0, 0.1, 2.0, 0.01);
		LivingEntity t = getTarget();
		if (t == null || this.flying || currentAttackId() != null) {
			if (!this.flying) getNavigation().stop();
			return;
		}
		getLookControl().setLookAt(t, 30.0F, 30.0F);
		double d = distanceTo(t);
		if (this.tickCount % 10 == 0) {
			if (d < 6.0) {
				Vec3 away = Geo.flatDir(t.position(), position()).scale(5.0);
				Vec3 dest = position().add(away);
				if (this.arena != null && !this.arena.contains(dest)) dest = this.arena.centerVec();
				getNavigation().moveTo(dest.x, dest.y, dest.z, 1.1);
			} else if (d > 10.0) {
				getNavigation().moveTo(t, 1.0);
			} else {
				getNavigation().stop();
			}
		}
	}

	@Override
	protected void onFightStart(ServerLevel level) {
		this.emergeTicks = 40;
		setAction(ACT_EMERGE);
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 200, 3.0, 1.0, 3.0, 0.3);
		this.tongueLash.windup = 32;
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int phase) {
		releaseSwallowed(true);
		level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 3, getZ(), 60, 2.0, 1.0, 2.0, 0.3);
		if (phase >= 3) this.tongueLash.windup = 21; // tongue speed x1.5
	}

	@Override
	protected void onFightReset(ServerLevel level) {
		releaseSwallowed(false);
		this.flying = false;
		this.noPhysics = false;
		setNoGravity(false);
		this.entityData.set(DATA_TONGUE, 0.0F);
	}

	@Override
	protected void onFightWon(ServerLevel level) {
		releaseSwallowed(false);
		this.noPhysics = false;
		setNoGravity(false);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel) releaseSwallowed(false);
		super.remove(reason);
	}

	@Override
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 14, 5, 14, p.getY() - 1, 6);
	}

	// ---- loot -------------------------------------------------------------------------------------------------------

	@Override
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("lilycrown", () -> new ItemStack(OwbItems.LILYCROWN)),
			dev.elsewhere.boss.kit.DropRule.other("gulp_tongue", () -> new ItemStack(OwbItems.GULP_TONGUE)))));
		out.add(new ItemStack(OwbItems.FROG_LEGS, GearStats.FOOD_DROP_MIN + this.random.nextInt(GearStats.FOOD_DROP_MAX - GearStats.FOOD_DROP_MIN + 1)));
		if (FabricLoader.getInstance().isModLoaded("greenleaf")) out.add(new ItemStack(OwbItems.BUFO_GLAND, 1 + this.random.nextInt(2)));
		return out;
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(net.minecraft.world.item.Items.SLIME_BALL, 3);
	}

	@Override
	protected @Nullable Item tierSignature() {
		return SignatureGear.CHALLENGERS_CLEAVER;
	}

	/** Frogspawn landed: called by the glob. */
	public static void placeFrogspawn(ServerLevel level, BlockPos at) {
		BlockPos p = at;
		for (int i = 0; i < 4 && !level.getBlockState(p).isAir(); i++) p = p.above();
		if (level.getBlockState(p).isAir()) {
			level.setBlock(p, OwbBlocks.GULPMOTHER_FROGSPAWN.defaultBlockState(), 3);
			level.playSound(null, p, SoundEvents.FROGSPAWN_PLACE, SoundSource.HOSTILE, 1.5F, 0.8F);
		}
	}
}
