package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Trail Biscuit: food (2 hunger, 0.4 saturation); eaten with minions out, all of them get Well Fed 3 min and heal 50%. */
public class TrailBiscuitItem extends Item {
	public TrailBiscuitItem(Item.Properties p) {
		super(p);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer sp) feedMinions(sp);
		return super.finishUsingItem(stack, level, entity);
	}

	/** Gives every minion of the owner Well Fed (refreshes, never stacks) and heals half its max HP. Returns the count. */
	public static int feedMinions(ServerPlayer owner) {
		int n = 0;
		for (MinionEntity m : MinionManager.minions(owner)) {
			m.removeEffect(SummonerEffects.WELL_FED);
			m.addEffect(new MobEffectInstance(SummonerEffects.WELL_FED, GearStats.WELL_FED_TICKS, 0), owner);
			m.heal((float) (m.getMaxHealth() * GearStats.WELL_FED_HEAL_FRACTION));
			if (m.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 5, 0.3, 0.2, 0.3, 0);
			n++;
		}
		return n;
	}
}
