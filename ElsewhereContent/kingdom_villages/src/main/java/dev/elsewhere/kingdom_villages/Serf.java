package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * {@code elsewhere:serf}: a labourer in a plain brown tunic. A true neutral mob, like a wolf (user, binding): it ignores
 * players until attacked; then it (and Serfs nearby that saw it) fights back, frightened and erratic like a hurt villager:
 * zigzag panic runs, then a turn and a punch (4-5 damage, bare-handed arm swing) when the attacker is close or it is
 * cornered, then off again. It calms down after 30 s without being hit. 18 HP (a little less than a player). Flees hostile mobs (not the kingdom's own
 * soldiers) and walks to the work position the village manager gives it.
 */
public class Serf extends PathfinderMob {
	public static final int CALM_TICKS = 600;
	public long villageId;
	public @Nullable BlockPos workPos;
	public int angerTicks;
	public @Nullable UUID angerTarget;
	public int punches;
	/** Serf-tier variant: serf, thrall (Hrafnholt), porter (Azmar) or peat_cutter (Mirewick), and the faction that employs it. */
	public String variant = "serf";
	public String faction = "";
	/** Thrall levy: armed with the faction's levy weapon (damage 5) for the length of a fight. */
	public boolean levyArmed;
	private int swingCooldown;
	private ItemStack carried = ItemStack.EMPTY;
	// 3.0 polish P9 (s-p9-idle): work pose (IdlePoses.SERF_CARRY / SERF_WORK, 0 = none) and the tier, both synced on change only.
	// The tier used to be a server-only field, so every client drew the plain serf skin for thralls, porters and peat-cutters.
	private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_WORK =
		net.minecraft.network.syncher.SynchedEntityData.defineId(Serf.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
	private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_TIER =
		net.minecraft.network.syncher.SynchedEntityData.defineId(Serf.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
	/** Serf tiers by synced index. */
	public static final java.util.List<String> TIERS = java.util.List.of("serf", "thrall", "porter", "peat_cutter");

	public Serf(EntityType<? extends Serf> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_WORK, (byte) 0);
		b.define(DATA_TIER, (byte) 0);
	}

	/** 3.0 P9: the synced work pose (0 none, {@code IdlePoses.SERF_CARRY} hauling, {@code IdlePoses.SERF_WORK} at the work spot). */
	public int workPose() {
		return this.entityData.get(DATA_WORK);
	}

	public void setWorkPose(int pose) {
		this.entityData.set(DATA_WORK, (byte) pose);
	}

	/** The tier as clients see it (skin and work animation). */
	public String syncedVariant() {
		int i = this.entityData.get(DATA_TIER);
		return i >= 0 && i < TIERS.size() ? TIERS.get(i) : "serf";
	}

	/** Copies {@link #variant} into the synced tier; a no-op when unchanged. Called on load and when a player starts seeing the serf. */
	public void syncLook() {
		this.entityData.set(DATA_TIER, (byte) Math.max(0, TIERS.indexOf(variant)));
	}

	@Override
	public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
		syncLook(); // the spawner sets the variant after construction: sync it before anyone draws the serf (sent with the next data update)
		super.startSeenByPlayer(player);
	}

	/** 3.0 P9: the work pose for a serf {@code distSqr} from its work spot: hauling its load there (if it has one), working once there. */
	public static int workPoseFor(double distSqr, boolean hasLoad) {
		if (distSqr > 9.0) return hasLoad ? dev.elsewhere.kingdom.idle.IdlePoses.SERF_CARRY : 0;
		return dev.elsewhere.kingdom.idle.IdlePoses.SERF_WORK;
	}

	public static AttributeSupplier.Builder createAttributes() {
		// RIVALS-DECISIONS 14: every Serf tier has 18 HP ("a little less health than a player"); the faction profile can override it
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 18.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	/** Max health from the employer's faction profile (FactionVillages.serfHealth); never heals. Used at spawn and on load. */
	public void applyProfileHealth() {
		var hp = getAttribute(Attributes.MAX_HEALTH);
		double want = FactionVillages.of(faction).serfHealth();
		if (hp == null || want <= 0 || hp.getBaseValue() == want) return;
		hp.setBaseValue(want);
		if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FrightenedFightGoal(this));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 1.0, 1.3, Serf::isThreat));
		this.goalSelector.addGoal(4, new WorkGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	private static boolean isThreat(LivingEntity e) {
		return !(e instanceof KingdomSoldier);
	}

	public boolean isAngry() {
		return angerTicks > 0 && angerTarget != null;
	}

	public @Nullable LivingEntity angerEntity() {
		if (angerTarget == null || !(level() instanceof ServerLevel sl)) return null;
		return sl.getEntity(angerTarget) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** Becomes hostile to {@code attacker} for 30 s (reset by every new hit). */
	public void provoke(LivingEntity attacker) {
		if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
		angerTarget = attacker.getUUID();
		angerTicks = CALM_TICKS;
		if (levyArmed) return; // the levy keeps its axe
		if (carried.isEmpty() && !getMainHandItem().isEmpty()) {
			carried = getMainHandItem().copy();
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // punches bare-handed
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Serf)) {
			provoke(attacker);
			// Serfs nearby that saw it join in
			for (Serf other : level.getEntitiesOfClass(Serf.class, new AABB(blockPosition()).inflate(12), s -> s != this && s.isAlive()))
				if (other.distanceToSqr(this) < 25 || other.hasLineOfSight(this)) other.provoke(attacker);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		float dmg = levyArmed ? 5.0F : 4.0F + getRandom().nextFloat(); // 4-5, like a wooden or stone sword; the thrall levy hits for 5
		boolean ok = target.hurtServer(level, damageSources().mobAttack(this), dmg);
		if (ok) setLastHurtMob(target);
		return ok;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (angerTicks > 0 && --angerTicks == 0) calmDown();
		if (angerTicks > 0 && angerEntity() == null) calmDown();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (Boolean.getBoolean("villages.selftest") && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			DamageSource ds = getLastDamageSource();
			System.out.println("[villages-selftest] serf removed " + reason + " at " + blockPosition().toShortString() + " variant " + variant + " hp " + getHealth() + " lastDamage " + (ds == null ? "none" : ds.getMsgId() + " by " + ds.getEntity()));
		}
		super.remove(reason);
	}

	/** Peat-Cutters walk off the job while their kingdom's strike lasts. */
	public boolean striking() {
		return variant.equals("peat_cutter") && level() instanceof ServerLevel sl && VillageManager.isStriking(sl, faction);
	}

	/** Thrall levy: picks up {@code weapon} (or drops it again with an empty stack) and keeps it until {@link #disarm}. */
	public void arm(ItemStack weapon) {
		levyArmed = true;
		setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
		setDropChance(EquipmentSlot.MAINHAND, 0F);
		carried = ItemStack.EMPTY;
	}

	public void disarm(ItemStack work) {
		levyArmed = false;
		setItemSlot(EquipmentSlot.MAINHAND, work.copy());
		setDropChance(EquipmentSlot.MAINHAND, 0F);
	}

	public void calmDown() {
		angerTicks = 0;
		angerTarget = null;
		setTarget(null);
		if (!carried.isEmpty()) {
			setItemSlot(EquipmentSlot.MAINHAND, carried);
			carried = ItemStack.EMPTY;
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putLong("SerfVillage", villageId);
		out.putString("SerfVariant", variant);
		out.putString("SerfFaction", faction);
		out.putBoolean("SerfLevy", levyArmed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		villageId = in.getLongOr("SerfVillage", 0L);
		variant = in.getStringOr("SerfVariant", "serf");
		syncLook(); // 3.0 P9: clients see the right tier
		faction = in.getStringOr("SerfFaction", "");
		levyArmed = in.getBooleanOr("SerfLevy", false);
		// Serfs saved by 2.0 (16 HP) or an early 3.0 build (20 HP) take the current profile health once on load
		if (level() instanceof ServerLevel) applyProfileHealth();
	}

	/** Panic in zigzags, turn and punch when the attacker is close or the Serf is cornered, then run again. */
	static final class FrightenedFightGoal extends Goal {
		private final Serf serf;
		private int phaseTicks, repath;
		private boolean fighting;

		FrightenedFightGoal(Serf serf) {
			this.serf = serf;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return serf.isAngry() && serf.angerEntity() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			fighting = false;
			phaseTicks = 20 + serf.getRandom().nextInt(20);
		}

		@Override
		public void stop() {
			serf.getNavigation().stop();
			serf.setTarget(null);
		}

		@Override
		public void tick() {
			LivingEntity foe = serf.angerEntity();
			if (foe == null) return;
			serf.setTarget(foe);
			double d2 = serf.distanceToSqr(foe);
			boolean cornered = !fighting && serf.getNavigation().isDone() && phaseTicks < 10;
			if (--phaseTicks <= 0 || !fighting && (d2 < 2.5 * 2.5 || cornered)) {
				fighting = !fighting || d2 < 2.5 * 2.5;
				phaseTicks = fighting ? 25 + serf.getRandom().nextInt(25) : 20 + serf.getRandom().nextInt(30);
				repath = 0;
			}
			serf.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			if (fighting) {
				if (--repath <= 0) {
					repath = 8;
					serf.getNavigation().moveTo(foe, 1.25);
				}
				if (serf.swingCooldown > 0) serf.swingCooldown--;
				if (d2 <= 2.4 * 2.4 && serf.swingCooldown <= 0 && serf.level() instanceof ServerLevel sl) {
					serf.swingCooldown = 18 + serf.getRandom().nextInt(10);
					serf.swingForAttack(InteractionHand.MAIN_HAND);
					serf.doHurtTarget(sl, foe);
					serf.punches++;
					if (serf.getRandom().nextInt(3) == 0) phaseTicks = 0; // a hit, then off again
				}
			} else if (--repath <= 0) {
				// zigzag: a fresh random point away from the attacker every half second
				repath = 8 + serf.getRandom().nextInt(6);
				Vec3 away = DefaultRandomPos.getPosAway(serf, 8, 4, foe.position());
				if (away != null) serf.getNavigation().moveTo(away.x, away.y, away.z, 1.35);
			}
		}
	}

	/**
	 * Walks to {@link #workPos} and works there. 3.0 P9: the synced work pose (hauling, then the tier's own work loop, animated
	 * client-side) is set when it changes; with {@code idle_poses} off the serf swings its arm there as before.
	 */
	static final class WorkGoal extends Goal {
		private final Serf serf;
		private int repath, shown = -1;

		WorkGoal(Serf serf) {
			this.serf = serf;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return serf.workPos != null && !serf.isAngry() && !serf.striking() && serf.level() instanceof ServerLevel;
		}

		@Override
		public void stop() {
			shown = -1;
			serf.setWorkPose(0);
		}

		@Override
		public void tick() {
			BlockPos p = serf.workPos;
			if (p == null) return;
			double d = serf.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			serf.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
			boolean poses = dev.elsewhere.kingdom.idle.IdlePoses.enabled;
			if (poses) {
				int want = workPoseFor(d, !serf.getMainHandItem().isEmpty());
				if (want != shown) {
					shown = want;
					serf.setWorkPose(want);
				}
			}
			if (d > 9.0) {
				if (--repath <= 0) {
					repath = 40 + serf.getRandom().nextInt(20);
					serf.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.8);
				}
			} else {
				serf.getNavigation().stop();
				if (!poses && --serf.swingCooldown <= 0) {
					serf.swingCooldown = 15 + serf.getRandom().nextInt(10);
					serf.swingForAttack(InteractionHand.MAIN_HAND);
				}
			}
		}
	}
}
