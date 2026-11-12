package dev.elsewhere.kingdom.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Bodkin Bolt: an arrow (bows, crossbows, dispensers) that ignores 25% of the target's armor. */
public class BodkinBoltItem extends ArrowItem {
	public BodkinBoltItem(Properties p) {
		super(p);
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return new BodkinArrow(level, shooter, ammo.copyWithCount(1), weapon);
	}

	@Override
	public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction dir) {
		BodkinArrow a = new BodkinArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
		a.pickup = AbstractArrow.Pickup.ALLOWED;
		return a;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, java.util.function.Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
	}
}
