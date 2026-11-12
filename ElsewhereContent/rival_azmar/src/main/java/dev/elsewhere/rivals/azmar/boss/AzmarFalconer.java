package dev.elsewhere.rivals.azmar.boss;

import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.duelist.kit.Companion;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.kit.Timers;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarFalcon;
import dev.elsewhere.rivals.azmar.AzmarFx;
import dev.elsewhere.rivals.azmar.AzmarGear;
import dev.elsewhere.rivals.azmar.AzmarItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Royal Falconer Leyla Dunmar, Mistress of the Falcons (RIVAL-BOSSES A3, identity card azmar_falconer). Lord, tier 2, 320 HP plus a falcon.
 * Only generic behaviour: temp_blocks (the Perch Post). Movement is the High-Ground Kite, defence is Falcon Intercept, recovery Perch Rest,
 * reach control Dust Dodge, last stand Falcon Storm (at 20 percent, once). Signature moves: Falcon Mark, Triple Stoop, Perch Post.
 */
public final class AzmarFalconer extends AzmarBoss {
	public static final float MARK_BONUS = 0.25F;
	public static final int DUST_DODGE_COOLDOWN = 123, INTERCEPT_COOLDOWN = 160, REST_CAP_PERCENT = 15, STORM_FALCONS = 4;

	public static final SignatureMove FALCON_MARK = new SignatureMove() {
		public String id() { return "falcon_mark"; }
		public String label() { return "Falcon Mark"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 20; }
		public int cooldown() { return 260; }
		public float raw() { return 0F; }
		public double minRange() { return 4; }
		public double maxRange() { return 32; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b instanceof AzmarFalconer f && f.falconAlive() && b.hasLineOfSight(t); }
		public void begin(HumanBoss b, LivingEntity t) {
			if (b.level() instanceof ServerLevel l) l.playSound(null, b.blockPosition(), SoundEvents.PARROT_IMITATE_PHANTOM, SoundSource.HOSTILE, 1.5F, 1.8F);   // the falcon shrieks
		}
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarFalconer f && b.level() instanceof ServerLevel l) f.dive(l, t); }
	};

	public static final SignatureMove TRIPLE_STOOP = new SignatureMove() {
		public String id() { return "triple_stoop"; }
		public String label() { return "Triple Stoop"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 20; }
		public int cooldown() { return 80; }
		public float raw() { return 14F; }
		public double minRange() { return 6; }
		public double maxRange() { return 30; }
		public boolean canUse(HumanBoss b, LivingEntity t) { return b.hasLineOfSight(t); }
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarFalconer f && b.level() instanceof ServerLevel l) f.stoop(l, t); }
	};

	public static final SignatureMove PERCH_POST = new SignatureMove() {
		public String id() { return "perch_post"; }
		public String label() { return "Perch Post"; }
		public Telegraph.Weight weight() { return Telegraph.Weight.SIGNATURE; }
		public int windup() { return 16; }
		public int cooldown() { return 400; }
		public float raw() { return 0F; }
		public double minRange() { return 0; }
		public double maxRange() { return 30; }
		public int priority() { return 30; }
		/** She builds when you climb to within 4 blocks of her height, and only if she is not already perched or standing on a post. */
		public boolean canUse(HumanBoss b, LivingEntity t) {
			return b instanceof AzmarFalconer f && b.onGround() && !f.perched && t.getY() >= b.getY() - 4.0;
		}
		public void fire(HumanBoss b, LivingEntity t) { if (b instanceof AzmarFalconer f && b.level() instanceof ServerLevel l) f.buildPost(l, t); }
	};

	/** Gold-white firework streak and a rising falcon cry (the card's telegraph look). */
	private static final TelegraphStyle STYLE = TelegraphStyle.of("firework", 0xF2E3A0, "entity.phantom.swoop", TelegraphStyle.Shape.SPIRAL, 1.6F);

	public static final DuelistProfile PROFILE = DuelistProfile.builder("azmar_falconer")
		.hp(171).walkSpeed(0.30).swing(12F, 20, 8).reach(3.0)
		.melee(() -> new ItemStack(AzmarItems.GILDED_KHOPESH))
		.ranged(() -> compositeBow())
		.swap(new Behaviours.Swap(3.5, 14.0, 7, 40, 0, 0, 0F))
		.bow(new Behaviours.Bow(20, 30, 2.0, 14.0, 0.10, 6.0, 0.5, 14F))
		.tempBlocks(new Behaviours.TempBlocks(8, 600, 5, 0, 5))
		.phases(0.5, 0.2)
		.caps(18F, 18F)
		.signature(FALCON_MARK).signature(TRIPLE_STOOP).signature(PERCH_POST)
		.telegraph(STYLE)
		.barColor(BossEvent.BossBarColor.GREEN)
		.skin("kingdom/azmar/falconer").build();

	private Companion falcon, falcon2;
	public LivingEntity marked;
	public boolean arrowBonusReady;
	public int marks, stoops, lastStoop, arrows, dodges, intercepts, posts, stormHits, stormSpawned, stormsDone, restedPoints;
	/** Healing from Perch Rest so far, in percent of max HP. */
	public float restedPercent;
	public boolean perched, stormPending;
	public final List<AzmarFalcon> stormFalcons = new ArrayList<>();
	public @Nullable BlockPos postTop;
	private @Nullable Vec3 perchGoal;
	private long nextPerchSearch, lastDamageTick, halfDodgeUntil, nextRest;
	private final DuelistEvents.Hit hitListener;

	/** The vanilla bow (the kit's bow move needs Items.BOW) shown with the composite-bow model. */
	static ItemStack compositeBow() {
		ItemStack s = new ItemStack(Items.BOW);
		s.set(DataComponents.ITEM_MODEL, AzmarContent.id("azmar_composite_bow"));
		return s;
	}

	public AzmarFalconer(EntityType<? extends HumanBoss> type, Level level) {
		super(type, level, PROFILE);
		setEnabled(dev.elsewhere.duelist.kit.Move.BLOCKS, false);   // opted in for the audit, but she only builds the Perch Post, never bridges or pillars on her own
		hitListener = (boss, victim, amount, kind, landed) -> {
			if (boss != this || !landed || victim != marked || !arrowBonusReady || kind != DamageCaps.MINOR || distanceTo(victim) < 4.0) return;
			arrowBonusReady = false;
			if (level() instanceof ServerLevel l) victim.hurtServer(l, l.damageSources().mobAttack(this), amount * MARK_BONUS);   // the marked next arrow does +25 percent
		};
		DuelistEvents.HIT.add(hitListener);
	}

	static Companion makeFalcon(AzmarFalconer owner, String title) {
		return new Companion(owner, level -> {
			AzmarFalcon f = AzmarFalcon.TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (f == null) return null;
			f.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20.0);
			f.setHealth(20.0F);
			f.setCustomName(Component.literal("Falcon of the Falconer"));
			f.setPersistenceRequired();
			return f;
		}, 14.0, 600, 3, Component.literal(title));
	}

	@Override
	protected void applyLoadout() {
		if (level() instanceof ServerLevel l) {
			AzmarGear.wear(this, EquipmentSlot.HEAD, AzmarGear.dress(l, new ItemStack(AzmarItems.FALCON_HELM), "wayfinder", "gold", 0x2BB3A3));
			AzmarGear.wear(this, EquipmentSlot.CHEST, AzmarGear.dress(l, new ItemStack(Items.CHAINMAIL_CHESTPLATE), "dune", "copper", -1));
			AzmarGear.wear(this, EquipmentSlot.LEGS, AzmarGear.dress(l, new ItemStack(Items.LEATHER_LEGGINGS), "dune", "gold", 0xEDE3CC));
			AzmarGear.wear(this, EquipmentSlot.FEET, AzmarGear.dress(l, new ItemStack(Items.LEATHER_BOOTS), "wayfinder", "copper", 0x8C5A38));
		}
		super.applyLoadout();
		setDropChance(EquipmentSlot.MAINHAND, 0F);
	}

	public boolean falconAlive() {
		return falcon != null && falcon.ally() != null || falcon2 != null && falcon2.ally() != null;
	}

	public Mob falconMob() {
		if (falcon != null && falcon.ally() != null) return falcon.ally();
		return falcon2 != null ? falcon2.ally() : null;
	}

	// ---- Falcon Mark ------------------------------------------------------------------------------------------------------------

	/** The falcon dives in a gold streak: Glowing 8 s, the next arrow +25 percent, and the court reads the player at once. Needs a clear line; cover breaks the dive. */
	public void dive(ServerLevel level, LivingEntity t) {
		Mob f = falconMob();
		if (f == null || !t.isAlive()) return;
		Vec3 from = f.position().add(0, 0.6, 0), to = t.getBoundingBox().getCenter();
		if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, f)).getType() != HitResult.Type.MISS) return;
		AzmarFx.falconStreak(level, from, to);
		Vec3 home = f.position();
		f.snapTo(to.x, to.y + 0.8, to.z, f.getYRot(), f.getXRot());
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
		marked = t;
		arrowBonusReady = true;
		marks++;
		AzmarBoss.mark(t.getUUID(), level.getGameTime() + 400);
		level.playSound(null, t.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE, 1.0F, 1.7F);
		Timers.after(level.getServer(), 10, () -> { if (f.isAlive()) f.snapTo(getX(), getY() + 1.5, getZ(), f.getYRot(), 0F); });
	}

	// ---- Triple Stoop -----------------------------------------------------------------------------------------------------------

	/** Raw 14 each, three arrows 4 ticks apart (five in phase 2), each leading the target at the moment it leaves. */
	public void stoop(ServerLevel level, LivingEntity t) {
		int n = phaseIndex() >= 1 ? 5 : 3;
		lastStoop = n;
		stoops++;
		for (int i = 0; i < n; i++) {
			Runnable shot = () -> {
				if (!isAlive() || !t.isAlive()) return;
				DuelistProjectile.throwAt(level, this, t, new ItemStack(Items.ARROW), 14F, 1.8F);
				arrows++;
			};
			if (i == 0) shot.run();
			else Timers.after(level.getServer(), i * 4, shot);
		}
	}

	// ---- Perch Post -------------------------------------------------------------------------------------------------------------

	/** A sandstone-look temporary pillar up to 5 high, one block per 8 ticks, beside her; she climbs it in 40 ticks. Soft, ledger-stored, never replaces a block. */
	public void buildPost(ServerLevel level, LivingEntity t) {
		Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
		BlockPos base = BlockPos.containing(getX() + away.x * 1.6, getY(), getZ() + away.z * 1.6);
		if (!level.getBlockState(base.below()).isSolid()) base = blockPosition();
		if (!level.getBlockState(base.below()).isSolid()) return;
		final BlockPos b0 = base;
		final java.util.UUID owner = getUUID();
		final int[] built = {0};
		final boolean[] stop = {false};
		posts++;
		for (int i = 0; i < 5; i++) {
			int k = i;
			Timers.after(level.getServer(), i * 8, () -> {
				if (stop[0] || !isAlive()) return;
				if (DuelBlocks.place(level, b0.above(k), owner, this, 600)) {
					built[0] = k + 1;
					level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xD8C28A, 1.2F), b0.getX() + 0.5, b0.getY() + k + 0.5, b0.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
				} else stop[0] = true;                                  // blocked: the post stays as high as it got
			});
		}
		Timers.after(level.getServer(), 40, () -> {
			if (!isAlive() || built[0] < 2) return;
			BlockPos top = b0.above(built[0] - 1);
			if (!DuelBlocks.known(level, top)) return;
			postTop = top;
			teleportTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5);
			getNavigation().stop();
		});
	}

	// ---- movement: the High-Ground Kite -----------------------------------------------------------------------------------------

	/** The highest standable spot within 20 blocks of the target that has a clear line to it, at least 1.5 blocks above her; null if no rise exists. */
	public @Nullable Vec3 findPerch(ServerLevel level, LivingEntity t) {
		BlockPos tp = t.blockPosition();
		double bestScore = -1e9;
		Vec3 best = null;
		for (int dx = -20; dx <= 20; dx += 3) {
			for (int dz = -20; dz <= 20; dz += 3) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 20.0) continue;
				int x = tp.getX() + dx, z = tp.getZ() + dz;
				if (!level.hasChunkAt(new BlockPos(x, tp.getY(), z))) continue;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos feet = new BlockPos(x, y, z);
				if (!level.getBlockState(feet.below()).isSolid() || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
				if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
				if (!home.equals(BlockPos.ZERO) && home.distSqr(feet) > (double) arenaRadius * arenaRadius) continue;
				if (y < getY() + 1.5) continue;
				Vec3 spot = Vec3.atBottomCenterOf(feet);
				if (level.clip(new ClipContext(spot.add(0, 1.6, 0), t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
				double band = Math.max(0, Math.max(12.0 - d, d - 18.0));
				double score = y * 2.0 - band * 0.5 - spot.distanceTo(position()) * 0.05;
				if (score > bestScore) {
					bestScore = score;
					best = spot;
				}
			}
		}
		return best;
	}

	/** Seeks the high ground and holds 12 to 18 blocks from you; on the flat she backs off to that band instead of closing. */
	@Override
	protected void move(LivingEntity t, double dist) {
		if (!(level() instanceof ServerLevel level)) return;
		long now = level.getGameTime();
		if (now >= nextPerchSearch) {
			nextPerchSearch = now + 40;
			perchGoal = findPerch(level, t);
		}
		boolean walking = !getNavigation().isDone();
		if (perchGoal != null && position().distanceToSqr(perchGoal) > 6.25) {
			if (!walking || now % 10 == 0) {
				if (!getNavigation().moveTo(perchGoal.x, perchGoal.y, perchGoal.z, 1.0)) {
					perchGoal = null;
					nextPerchSearch = now + 100;
				}
			}
			return;
		}
		if (dist < 12.0) {
			if (!walking || now % 10 == 0) {
				Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize().scale(6);
				getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
			}
		} else if (dist > 18.0) {
			if (!walking || now % 10 == 0) getNavigation().moveTo(t, 1.0);
		} else getNavigation().stop();
	}

	// ---- passive defence and recovery, every tick ------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		LivingEntity target = getTarget();
		if (falcon == null) falcon = makeFalcon(this, "Falcon of the Falconer");
		falcon.tick(level);
		if (phaseIndex() >= 1) {
			if (falcon2 == null) falcon2 = makeFalcon(this, "Second Falcon");
			falcon2.tick(level);
		}
		// where she stands: on a post top, or three or more blocks above you
		boolean onPost = postTop != null && DuelBlocks.known(level, postTop) && Math.abs(getX() - (postTop.getX() + 0.5)) < 1.3 && Math.abs(getZ() - (postTop.getZ() + 0.5)) < 1.3 && getY() >= postTop.getY() + 0.9;
		if (postTop != null && !DuelBlocks.known(level, postTop)) postTop = null;
		perched = onPost || (target != null && getY() >= target.getY() + 3.0);
		// Perch Rest: on a perch with no damage for 100 ticks, 1 percent of max HP per 20 ticks, at most 15 percent a fight
		if (perched && !isDeadOrDying() && now - lastDamageTick >= 100 && now >= nextRest && restedPercent < REST_CAP_PERCENT) {
			nextRest = now + 20;
			float before = getHealth();
			heal(getMaxHealth() * 0.01F);
			restedPercent += (getHealth() - before) / getMaxHealth() * 100F;
			restedPoints++;
		}
		// Falcon Intercept: the falcon dives into the first projectile aimed at her, once per 160 ticks; no falcon, no intercept
		if (falconAlive() && ready("intercept")) {
			for (Projectile p : level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(12.0), x -> x.getOwner() instanceof Player && !(x instanceof DuelistProjectile))) {
				Vec3 v = p.getDeltaMovement();
				Vec3 toMe = position().add(0, 1, 0).subtract(p.position());
				if (v.lengthSqr() < 0.01 || v.dot(toMe) <= 0 || toMe.length() > 11.0 || v.normalize().dot(toMe.normalize()) < 0.85) continue;
				Mob f = falconMob();
				AzmarFx.falconStreak(level, f.position().add(0, 0.6, 0), p.position());
				p.discard();
				setCooldown("intercept", INTERCEPT_COOLDOWN);
				intercepts++;
				level.playSound(null, blockPosition(), SoundEvents.PARROT_IMITATE_PHANTOM, SoundSource.HOSTILE, 1.2F, 1.9F);
				break;
			}
		}
		// Dust Dodge: a 5-block backward dodge in a sand puff with 8 ticks of i-frame when you get inside 3 blocks
		if (target != null && distanceTo(target) < 3.0 && ready("dust_dodge") && idle() && onGround()) dustDodge(level, target);
		// Falcon Storm waits for a free moment
		if (stormPending && idle() && !isInLastStandWindow()) {
			stormPending = false;
			startStorm(level);
		}
	}

	private boolean isInLastStandWindow() {
		return inLastStand();
	}

	private void dustDodge(ServerLevel level, LivingEntity target) {
		Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
		if (!level.noCollision(this, getBoundingBox().move(away.scale(2.0)))) return;
		setCooldown("dust_dodge", level.getGameTime() < halfDodgeUntil ? DUST_DODGE_COOLDOWN / 2 : DUST_DODGE_COOLDOWN);
		startDodge(8);
		setDeltaMovement(away.x * 1.25, 0.12, away.z * 1.25);
		needsSync = true;
		dodges++;
		level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1F, 1.4F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 12, 0.4, 0.1, 0.4, 0.02);
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xD8C28A, 1.4F), getX(), getY() + 0.4, getZ(), 18, 0.5, 0.2, 0.5, 0.01);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean r = super.hurtServer(level, source, damage);
		if (r) lastDamageTick = level.getGameTime();
		return r;
	}

	// ---- last stand: Falcon Storm -----------------------------------------------------------------------------------------------------

	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		super.onPhaseChange(level, newPhase);
		if (newPhase >= 2 && stormsDone == 0 && !stormPending) stormPending = true;
	}

	/** A 24-tick shrill whistle (she cannot move), then four falcons (10 HP, one hit each) dive in turn 20 ticks apart, each raw 8 along a gold streak drawn 24 ticks ahead. */
	private void startStorm(ServerLevel level) {
		stormsDone++;
		LivingEntity t = getTarget();
		startTelegraph("falcon_storm", "Falcon Storm", 24, Telegraph.Weight.HEAVY, 8F, STYLE.cue(false, true), true, () -> {
			if (!isAlive()) return;
			level.playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2.0F, 1.9F);
			LivingEntity target = t != null && t.isAlive() ? t : getTarget();
			for (int i = 0; i < STORM_FALCONS; i++) Timers.after(level.getServer(), i * 20, () -> stormDive(level, target));
			Timers.after(level.getServer(), (STORM_FALCONS - 1) * 20 + 30, () -> halfDodgeUntil = level.getGameTime() + 200);
		});
		if (t != null) dev.elsewhere.duelist.foresight.Foresight.hint(() -> level.sendParticles(STYLE.particle(), getX(), getY() + 2.4, getZ(), 12, 0.4, 0.3, 0.4, 0.02));   // B5: wind-up hint, foresight gear only
	}

	private void stormDive(ServerLevel level, @Nullable LivingEntity target) {
		if (!isAlive() || target == null || !target.isAlive()) return;
		Vec3 lock = target.position();
		double a = random.nextDouble() * Math.PI * 2;
		AzmarFalcon f = AzmarFalcon.spawn(level, lock.add(Math.cos(a) * 6, 8, Math.sin(a) * 6), 10F, true);
		if (f == null) return;
		stormFalcons.add(f);
		stormSpawned++;
		Vec3 start = f.position();
		for (int tk = 0; tk < 24; tk += 4) Timers.after(level.getServer(), tk, () -> { if (f.isAlive()) dev.elsewhere.duelist.foresight.Foresight.hint(() -> AzmarFx.falconStreak(level, start, lock.add(0, 0.5, 0))); });   // the streak is drawn 24 ticks ahead (B5: a hint, foresight gear only)
		Timers.after(level.getServer(), 24, () -> {
			if (!f.isAlive()) return;                                   // a falcon shot down in its wind-up never lands
			f.snapTo(lock.x, lock.y + 0.8, lock.z, f.getYRot(), 0F);
			level.playSound(null, f.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE, 1.2F, 1.8F);
			for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(lock, lock).inflate(1.6, 2.0, 1.6))) {
				if (p.isSpectator() || p.isCreative()) continue;
				if (hurtRaw(level, p, 8F, DamageCaps.MINOR, level.damageSources().mobAttack(this))) stormHits++;
			}
			Timers.after(level.getServer(), 4, f::discard);
		});
	}

	// ---- end of fight ---------------------------------------------------------------------------------------------------------------------

	private void cleanup(boolean level) {
		DuelistEvents.HIT.remove(hitListener);
		if (falcon != null) falcon.dismiss();
		if (falcon2 != null) falcon2.dismiss();
		for (AzmarFalcon f : stormFalcons) if (f.isAlive()) f.discard();
		stormFalcons.clear();
		if (level && level() instanceof ServerLevel sl) DuelBlocks.clearOwner(sl, getUUID());
		postTop = null;
	}

	@Override
	public void die(DamageSource source) {
		cleanup(true);
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy()) cleanup(true);
		else DuelistEvents.HIT.remove(hitListener);
		super.remove(reason);
	}
}
