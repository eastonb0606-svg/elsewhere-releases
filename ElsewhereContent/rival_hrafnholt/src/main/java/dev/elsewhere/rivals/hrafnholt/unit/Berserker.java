package dev.elsewhere.rivals.hrafnholt.unit;

import dev.elsewhere.kingdom.KingdomSoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Hrafnholt Berserker: no armor, two iron axes, always in Winter Rage (Strength I, shown by a red haze), never retreats and
 * ignores rout. 22 HP and speed 0.32 come from the unit file; fast and fragile. Counts against the soldier caps like any soldier.
 */
public class Berserker extends KingdomSoldier {
	public Berserker(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
	}

	@Override
	public void rout(int ticks) {
		// Winter Rage: a Berserker never flees
	}

	@Override
	public boolean isRouted() {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount % 20 == 0) addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false));
		if (tickCount % 10 == 0 && isActive()) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.9, getZ(), 1, 0.25, 0.1, 0.25, 0.0);
	}

	public boolean inWinterRage() {
		return hasEffect(MobEffects.STRENGTH);
	}
}
