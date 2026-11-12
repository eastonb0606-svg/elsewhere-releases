package dev.elsewhere.gear3.s3.harpoon;

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
 * Harpoon Bolt item (catalogue mechanic {@code harpoon_bolt}): an arrow item, so crossbows load it ({@code #minecraft:arrows});
 * bows skip it ({@code #elsewhere:crossbow_only_ammo}, HarpoonBowMixin). Shots and dispensers make a {@link HarpoonBolt}.
 */
public class HarpoonBoltItem extends ArrowItem {
	public HarpoonBoltItem(Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return new HarpoonBolt(level, shooter, ammo.copyWithCount(1), weapon);
	}

	/** Dispensers (registered in HarpoonBolts.init): no shooter, so the hit pulls the target back toward the dispenser. */
	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
		HarpoonBolt bolt = new HarpoonBolt(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
		bolt.pickup = AbstractArrow.Pickup.ALLOWED;
		return bolt;
	}
}
