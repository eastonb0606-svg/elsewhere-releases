package dev.elsewhere.rivals.azmar;

import dev.elsewhere.duelist.adapt.Guardable;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Azmar's special soldiers on one class: the Sand-Dancer (sidestep dodge with an 8-tick i-frame once per 5 s, a 5-block leap
 * into melee, a linen trail), the Naffatun (keeps 9 to 14 blocks off and lobs a fire pot every 8 s, never near a village block,
 * villager or golem) and the Jackal Guard (a shield-wall guard that obeys {@link Guardable} orders). The unit id picks the behaviour.
 */
public class AzmarSoldier extends KingdomSoldier implements Guardable {
	public static final Identifier SAND_DANCER = AzmarContent.id("azmar_sand_dancer"), NAFFATUN = AzmarContent.id("azmar_naffatun"), JACKAL_GUARD = AzmarContent.id("azmar_jackal_guard");
	public static final int DODGE_COOLDOWN = 100, DODGE_IFRAMES = 8, LEAP_COOLDOWN = 80, POT_INTERVAL = 160;
	public static final double LEAP_RANGE = 5.0;

	public int dodgeCd, iframes, leapCd, potCd = 40;
	/** Counters for the self-test. */
	public int dodges, leaps, pots;

	public AzmarSoldier(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
	}

	public boolean isSandDancer() {
		return SAND_DANCER.equals(unitId());
	}

	public boolean isNaffatun() {
		return NAFFATUN.equals(unitId());
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(2, new PotGoal(this));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isSandDancer() && source.getEntity() != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (iframes > 0) return false;
			if (dodgeCd <= 0 && !isRouted()) {
				dodgeCd = DODGE_COOLDOWN;
				iframes = DODGE_IFRAMES;
				dodges++;
				Vec3 from = source.getEntity().position();
				Vec3 away = position().subtract(from).multiply(1, 0, 1);
				if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
				Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(getRandom().nextBoolean() ? 1.1 : -1.1);
				setDeltaMovement(side.x, 0.15, side.z);
				needsSync = true;
				AzmarFx.linenTrail(level, position());
				return false;
			}
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (dodgeCd > 0) dodgeCd--;
		if (iframes > 0) iframes--;
		if (leapCd > 0) leapCd--;
		if (potCd > 0) potCd--;
		if (isSandDancer()) {
			LivingEntity t = getTarget();
			if (t != null && leapCd <= 0 && onGround() && !isRouted()) {
				double d = distanceTo(t);
				if (d > 3.0 && d <= LEAP_RANGE + 1.0 && hasLineOfSight(t)) {
					Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
					setDeltaMovement(dir.x * 0.85, 0.42, dir.z * 0.85);
					needsSync = true;
					leapCd = LEAP_COOLDOWN;
					leaps++;
					AzmarFx.linenTrail(level, position());
				}
			}
			if (tickCount % 3 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.02) AzmarFx.linenTrail(level, position());
		}
	}

	/** True when a fire pot may be lobbed at {@code pos}: no villager, golem or village point of interest within 10 blocks. */
	public static boolean safeToBurn(ServerLevel level, Vec3 pos) {
		AABB box = new AABB(pos, pos).inflate(10);
		if (!level.getEntitiesOfClass(Villager.class, box).isEmpty() || !level.getEntitiesOfClass(IronGolem.class, box).isEmpty()) return false;
		BlockPos p = BlockPos.containing(pos);
		return level.getPoiManager().getCountInRange(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME) || h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), p, 10,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY) == 0;
	}

	/** The Naffatun's behaviour: hold 9 to 14 blocks off, lob a pot every 8 s. */
	static final class PotGoal extends Goal {
		private final AzmarSoldier s;

		PotGoal(AzmarSoldier s) {
			this.s = s;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return s.isNaffatun() && s.getTarget() != null && s.getTarget().isAlive() && !s.isRouted();
		}

		@Override
		public void tick() {
			LivingEntity t = s.getTarget();
			if (t == null || !(s.level() instanceof ServerLevel level)) return;
			s.getLookControl().setLookAt(t, 30, 30);
			double d = s.distanceTo(t);
			boolean repath = (s.tickCount + s.getId()) % 10 == 0 || s.getNavigation().isDone();
			if (!repath) {
				// keep the current path between decision ticks
			} else if (d < 7.0) {
				Vec3 away = s.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(8);
				s.getNavigation().moveTo(s.getX() + away.x, s.getY(), s.getZ() + away.z, 1.15);
			} else if (d > 14.0) {
				s.getNavigation().moveTo(t, 1.0);
			} else {
				s.getNavigation().stop();
			}
			if (s.potCd <= 0 && d >= 5.0 && d <= 18.0 && s.hasLineOfSight(t) && safeToBurn(level, t.position())) {
				s.potCd = POT_INTERVAL;
				s.pots++;
				FirePots.lob(level, s, t.position().add(0, 0.2, 0));
			}
		}
	}
}
