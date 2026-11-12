package dev.elsewhere.gear3.s3.echoarrow;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item {@code elsewhere:echo_arrow} (gear3 catalogue mechanic "echo_arrow"). An ArrowItem in {@code #minecraft:arrows}, so bows and
 * crossbows load it like a spectral arrow; dispensers fire it ({@link EchoArrows#init()} registers the projectile behaviour).
 */
public class EchoArrowItem extends ArrowItem {
	public EchoArrowItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner, ItemStack firedFromWeapon) {
		return new EchoArrowEntity(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
		EchoArrowEntity arrow = new EchoArrowEntity(level, position.x(), position.y(), position.z(), itemStack.copyWithCount(1), null);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
