package dev.elsewhere.kingdom_court.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ashgrove Longbow (Huntsman): a full draw takes 1.5 s (bow 1.0 s), arrows carry +25% base damage, and a fully drawn
 * hit lays the Hunter's Mark (Glowing 6 s; your arrows deal +15% to a marked target). Durability 576 (bow 384).
 */
public class AshgroveLongbowItem extends BowItem {
	public static final float DRAW_SCALE = 1.5F;
	public static final double DAMAGE_SCALE = 1.25;
	public static final String TAG = "elsewhere_court_longbow";
	public static final String TAG_FULL = "elsewhere_court_longbow_full";

	public AshgroveLongbowItem(Item.Properties p) {
		super(p);
	}

	/** Ticks of real drawing counted as {@code held / 1.5}: full power needs 30 ticks. */
	public static int effectiveHeld(int held) {
		return (int) (held / DRAW_SCALE);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		int duration = getUseDuration(stack, entity);
		int held = duration - remainingTime;
		return super.releaseUsing(stack, level, entity, duration - effectiveHeld(held));
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean crit) {
		Projectile p = super.createProjectile(level, shooter, weapon, ammo, crit);
		if (p instanceof AbstractArrow a) {
			a.setBaseDamage(2.0 * DAMAGE_SCALE);
			a.addTag(TAG);
			if (crit) a.addTag(TAG_FULL);
		}
		return p;
	}
}
