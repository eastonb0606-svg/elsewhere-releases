package dev.elsewhere.boss.tempestra;

import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.petalwind_glasswaste.BossDamage;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.Telegraph;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestra, Eye of the Gale (BOSSES.md #9, Tier III). A storm elemental hovering in the eye. Phase 1 Orbit: shielded by
 * her rings (they open for 8 s every 20 s), Gust Volley and Downburst. Phase 2 Updraft: levitation pulses, cyclone bolts,
 * Skywhales to ride. Phase 3 Stormrim: lightning on marked spots, Gale Kites. HP 520 base / 796 effective, armor 8, toughness 4.
 */
public class Tempestra extends BossBase {
	public static final float BASE_HP = 520, ARMOR = 8, TOUGHNESS = 4, DOWNBURST_RAW = 42, BOLT_RAW = 16, LIGHTNING_RAW = 30;
	public static final float SHIELD_FACTOR = 0.2F;
	private static final EntityDataAccessor<Boolean> DATA_SHIELD = SynchedEntityData.defineId(Tempestra.class, EntityDataSerializers.BOOLEAN);
	public static final byte EV_VOLLEY = 80, EV_DOWNBURST = 81, EV_PHASE = 82, EV_BOLT = 83, EV_STRIKE = 84;
	public final AnimationState volleyAnimation = new AnimationState();
	public final AnimationState downburstAnimation = new AnimationState();
	public final AnimationState phaseAnimation = new AnimationState();
	/** Boss art hooks: cyclone bolt cast and Stormrim strike wind-up. */
	public final AnimationState boltAnimation = new AnimationState();
	public final AnimationState strikeAnimation = new AnimationState();
	private int volleyCd = 60, downCd = 140, shieldCycle, boltCd = 60, levCd = 80, strikeCd = 100, kiteCd = 200;
	private Vec3 pending = null;
	private int pendingTicks, pendingKind;
	private boolean whalesSpawned;
	public int volleys, downbursts, strikes;

	public Tempestra(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.BLUE);
		this.setNoGravity(true);
		this.arenaRadius = EyeOfTheGale.ARENA_RADIUS;
		this.arenaDown = 18;
		this.arenaUp = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, BOLT_RAW).add(Attributes.FLYING_SPEED, 0.4).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHIELD, true);
	}

	public boolean shielded() {
		return this.entityData.get(DATA_SHIELD);
	}

	@Override
	public String bossId() {
		return "tempestra";
	}

	@Override
	public int tier() {
		return 3;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.65F, 0.3F};
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.BREEZE_IDLE_AIR;
	}

	@Override
	protected void idleTick(ServerLevel level) {
		hover(level, 0.0);
	}

	private void hover(ServerLevel level, double radius) {
		var c = arenaCenter();
		if (c == null) return;
		double a = this.tickCount * 0.01;
		Vec3 want = new Vec3(c.getX() + 0.5 + Math.cos(a) * radius, c.getY() + 4 + Math.sin(this.tickCount * 0.05), c.getZ() + 0.5 + Math.sin(a) * radius);
		Vec3 d = want.subtract(this.position());
		this.setDeltaMovement(d.scale(0.08));
		if (this.tickCount % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 3, 1.2, 0.5, 1.2, 0.02);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (shielded() && phase() == 1 && !source.isCreativePlayer()) {
			damage *= SHIELD_FACTOR;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 4, getZ(), 10, 1.5, 1.5, 1.5, 0.1);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void fightTick(ServerLevel level) {
		hover(level, phase() == 3 ? 4.0 : 2.0);
		var target = pickTarget(level);
		if (target != null) this.getLookControl().setLookAt(target);
		if (pending != null && --pendingTicks <= 0) resolvePending(level);
		if (phase() == 1) {
			if (++shieldCycle % 400 == 300) {
				this.entityData.set(DATA_SHIELD, false);
				announce("Her rings open", 40);
				Telegraph.ring(this, level, position(), 3.0, Telegraph.CYAN, 160);
			} else if (shieldCycle % 400 == 0) this.entityData.set(DATA_SHIELD, true);
		}
		if (target == null) return;
		if (--volleyCd <= 0) {
			volleyCd = 120;
			volleys++;
			level.broadcastEntityEvent(this, EV_VOLLEY);
			announce("Gust Volley", 30);
			for (int i = 0; i < 5; i++) {
				Vec3 from = this.position().add(0, 4, 0);
				Vec3 dir = target.position().add((i - 2) * 1.2, 0.5, 0).subtract(from).normalize();
				WindCharge charge = new WindCharge(level, from.x + dir.x * 2.5, from.y + dir.y * 2.5, from.z + dir.z * 2.5, dir);
				charge.setOwner(this);
				dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, charge);
			}
		}
		if (pending == null && --downCd <= 0) {
			downCd = 200;
			pending = target.position();
			pendingTicks = 30;
			pendingKind = 0;
			Telegraph.circle(this, level, pending, 3.0, Telegraph.RED, 30);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pending.x, pending.y + 0.3, pending.z, 30, 1.5, 0.1, 1.5, 0);
			level.broadcastEntityEvent(this, EV_DOWNBURST);
			announce("Downburst", 30);
		}
		if (phase() >= 2) {
			if (--levCd <= 0) {
				levCd = 80;
				for (ServerPlayer p : fighters(level)) if (!p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1), this);
			}
			if (--boltCd <= 0) {
				boltCd = 60;
				cycloneBolt(level, target);
			}
			if (!whalesSpawned) {
				whalesSpawned = true;
				for (int i = 0; i < 2; i++) {
					var whale = ModEntities.SKYWHALE.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (whale == null) continue;
					whale.snapTo(getX() + (i == 0 ? 8 : -8), getY() - 2, getZ());
					whale.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY, new ItemStack(net.minecraft.world.item.Items.HARNESS.white()));
					dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, whale);
				}
			}
		}
		if (phase() >= 3) {
			if (pending == null && --strikeCd <= 0) {
				strikeCd = 70;
				pending = target.position();
				pendingTicks = 30;
				pendingKind = 1;
				Telegraph.circle(this, level, pending, 2.5, Telegraph.RED, 30);
				level.broadcastEntityEvent(this, EV_STRIKE);
				announce("Stormrim", 20);
			}
			if (--kiteCd <= 0) {
				kiteCd = 300;
				if (level.getEntitiesOfClass(dev.elsewhere.petalwind.GaleKite.class, arenaBox()).size() < 3) {
					var kite = ModEntities.GALE_KITE.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (kite != null) {
						kite.snapTo(getX(), getY() + 6, getZ());
						dev.elsewhere.stub.petalwind_glasswaste.BossSummons.spawn(this, level, kite);
					}
				}
			}
		}
	}

	private void cycloneBolt(ServerLevel level, ServerPlayer target) {
		level.broadcastEntityEvent(this, EV_BOLT);
		Vec3 from = this.position().add(0, 4, 0);
		Vec3 to = target.getEyePosition();
		for (int i = 0; i <= 20; i++) {
			Vec3 p = from.lerp(to, i / 20.0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
		}
		if (this.hasLineOfSight(target)) {
			BossDamage.hit(level, this, target, BOLT_RAW);
			Vec3 away = to.subtract(from).multiply(1, 0, 1).normalize();
			target.push(away.x * 1.2, 0.5, away.z * 1.2);
		}
		level.playSound(null, target.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
	}

	private void resolvePending(ServerLevel level) {
		Vec3 p = pending;
		pending = null;
		if (pendingKind == 0) {
			downbursts++;
			level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
			for (ServerPlayer pl : fighters(level)) {
				if (pl.position().distanceTo(p) <= 3.2) {
					BossDamage.hit(level, this, pl, DOWNBURST_RAW);
					pl.push(0, -1.2, 0);
				}
			}
		} else {
			strikes++;
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(p.x, p.y, p.z);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			for (ServerPlayer pl : fighters(level)) if (pl.position().distanceTo(p) <= 2.6) BossDamage.hit(level, this, pl, LIGHTNING_RAW);
		}
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		level.broadcastEntityEvent(this, EV_PHASE);
		this.entityData.set(DATA_SHIELD, false);
	}

	@Override
	protected void onReset(ServerLevel level) {
		this.entityData.set(DATA_SHIELD, true);
		whalesSpawned = false;
		pending = null;
	}

	public static final float STORMSAGE_FIRST_KILL = 0.5F, STORMSAGE_REPEAT = 0.2F;

	/** Stormsage Mantle chance for one player: first kill of Tempestra (per-player BossLoot tag) or a repeat kill. */
	public static float stormsageChance(boolean firstKill) {
		return firstKill ? STORMSAGE_FIRST_KILL : STORMSAGE_REPEAT;
	}

	@Override
	protected List<ItemStack> rollDrops(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(PetalwindItems.ZEPHYR_FEATHER, 3 + player.getRandom().nextInt(4)));
		java.util.List<dev.elsewhere.boss.kit.DropRule.Unique> uniques = new ArrayList<>(List.of(
			dev.elsewhere.boss.kit.DropRule.signature("gale_pinion", () -> new ItemStack(dev.elsewhere.petalwind_glasswaste.PGStructures.GALE_PINION)),
			dev.elsewhere.boss.kit.DropRule.other("gale_heart", () -> new ItemStack(PetalwindItems.GALE_HEART))));
		// ARSENAL.md: Stormsage Mantle (arsenal module), now a normal "other" unique (50% first, 33% repeat, pity); skipped if not registered.
		if (!dev.elsewhere.petalwind_glasswaste.ArsenalHooks.stack("stormsage_mantle", 1).isEmpty()) {
			uniques.add(dev.elsewhere.boss.kit.DropRule.other("stormsage_mantle", () -> dev.elsewhere.petalwind_glasswaste.ArsenalHooks.stack("stormsage_mantle", 1)));
		}
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, uniques));
		return out;
	}

	@Override
	protected ItemStack summonItem() {
		return new ItemStack(net.minecraft.world.item.Items.WIND_CHARGE, 3);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EV_VOLLEY -> volleyAnimation.start(this.tickCount);
			case EV_DOWNBURST -> downburstAnimation.start(this.tickCount);
			case EV_PHASE -> phaseAnimation.start(this.tickCount);
			case EV_BOLT -> boltAnimation.start(this.tickCount);
			case EV_STRIKE -> strikeAnimation.start(this.tickCount);
			default -> super.handleEntityEvent(id);
		}
	}
}
