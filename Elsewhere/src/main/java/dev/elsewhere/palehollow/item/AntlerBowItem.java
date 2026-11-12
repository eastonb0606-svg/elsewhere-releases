package dev.elsewhere.palehollow.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Antler Bow (Pale Hollow): 640 durability, innate Power II (+1.5 arrow damage), roots Stillness mobs 2 s (4 s per target). */
public class AntlerBowItem extends BowItem {
	public static final String ROOT_TAG = "elsewhere_antler_arrow";
	/** Power II adds 0.5 x (level + 1) = 1.5 base damage. */
	public static final double INNATE_POWER_II = 1.5;

	public AntlerBowItem(final Properties properties) {
		super(properties);
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile p = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		p.addTag(ROOT_TAG);
		if (p instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(2.0 + INNATE_POWER_II);
		}
		return p;
	}
}
