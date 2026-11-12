package dev.elsewhere.rivals.mirewick.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Fowler's Marshbow: a bow whose arrows keep full speed and damage underwater and float on the surface (MirewickPassives). */
public class MarshbowItem extends BowItem {
	public static final String TAG = "elsewhere_mirewick_marsh";

	public MarshbowItem(Item.Properties p) {
		super(p);
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean crit) {
		Projectile p = super.createProjectile(level, shooter, weapon, ammo, crit);
		if (p instanceof AbstractArrow a) {
			a.addTag(TAG);
			MirewickPassives.track(a);
		}
		return p;
	}
}
