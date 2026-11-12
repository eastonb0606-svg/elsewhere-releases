package dev.elsewhere.gear3.s3.sunglass;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Sunglass Arrow item (catalogue mechanic "sunglass_arrow"): ammo for bows and crossbows (it is in {@code #minecraft:arrows}) and a
 * dispenser projectile ({@link SunglassArrows#init()} registers the behaviour). Name and lore come from the catalogue.
 */
public class SunglassArrowItem extends ArrowItem {
	public SunglassArrowItem(Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return new SunglassArrow(level, shooter, ammo.copyWithCount(1), weapon);
	}

	/** Dispenser shot: like vanilla's arrow, it can be picked up again. */
	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		SunglassArrow arrow = new SunglassArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
