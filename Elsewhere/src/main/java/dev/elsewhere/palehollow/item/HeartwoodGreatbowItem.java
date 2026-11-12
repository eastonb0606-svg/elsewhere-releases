package dev.elsewhere.palehollow.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Mother Creak's bow: its arrows root what they hit (Slowness IV for 1 s, applied in {@code PaleHollow}). */
public class HeartwoodGreatbowItem extends BowItem {
	public static final String ROOT_TAG = "elsewhere_root_arrow";

	public HeartwoodGreatbowItem(final Properties properties) {
		super(properties);
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile p = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		p.addTag(ROOT_TAG);
		// C14: innate Power II (+1.5 base arrow damage)
		if (p instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) {
			arrow.setBaseDamage(2.0 + AntlerBowItem.INNATE_POWER_II);
		}
		return p;
	}
}
