package dev.elsewhere.rivals.hrafnholt.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hornbow of the Hunt (Huntmaster Eydis): full draw in 17 ticks (0.85 s; a bow needs 20) and every arrow it looses slows
 * the target (Slowness I, 2 s; applied in HrafnholtGear). 480 uses, no Infinity.
 */
public class HornbowItem extends BowItem {
	public static final int FULL_DRAW_TICKS = 17;

	public HornbowItem(Item.Properties p) {
		super(p);
	}

	/** Draw time scaled so that 17 held ticks count as the vanilla 20. */
	public static int effectiveHeld(int held) {
		return Math.min(held * 20 / FULL_DRAW_TICKS, 72000);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		int duration = getUseDuration(stack, entity);
		int held = duration - remainingTime;
		return super.releaseUsing(stack, level, entity, duration - effectiveHeld(held));
	}
}
