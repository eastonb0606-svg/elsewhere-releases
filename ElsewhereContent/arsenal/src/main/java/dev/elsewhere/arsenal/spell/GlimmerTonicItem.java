package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalEffects;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Glimmer Tonic / Strong Glimmer Tonic: restore Glimmer, regen ×2 for 10 s, then Dazed (ARSENAL §4c, rev 3). */
public class GlimmerTonicItem extends Item {
	private final float restore;

	public GlimmerTonicItem(Properties p, float restore) {
		super(p);
		this.restore = restore;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof ServerPlayer sp) drink(sp, restore);
		return super.finishUsingItem(stack, level, entity);
	}

	public static void drink(ServerPlayer sp, float restore) {
		Glimmer.restore(sp, restore);
		Glimmer.surge(sp, ArsenalStats.TONIC_SURGE_TICKS);
		MobEffectInstance cur = sp.getEffect(ArsenalEffects.DAZED);
		int dur = cur == null ? ArsenalStats.DAZED_TICKS : Math.min(ArsenalStats.DAZED_MAX_TICKS, cur.getDuration() + ArsenalStats.DAZED_TICKS);
		sp.removeEffect(ArsenalEffects.DAZED);
		sp.addEffect(new MobEffectInstance(ArsenalEffects.DAZED, dur, 0));
	}
}
