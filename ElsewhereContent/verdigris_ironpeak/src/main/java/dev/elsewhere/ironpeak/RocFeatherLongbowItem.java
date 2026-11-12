package dev.elsewhere.ironpeak;

import dev.elsewhere.verdigris_ironpeak.GearStats;
import java.util.List;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Roc-Feather Longbow (ARSENAL.md §6): a non-magic archer bow. Arrows fly x1.4 faster (flatter, farther, and vanilla
 * arrow damage scales with speed); `elsewhere:projectile_pierce` +1 is added by id when the arsenal registers it. Cons:
 * a full draw takes 1.4 s instead of 1.0 s, and it takes every bow enchantment except Infinity (it always uses an arrow).
 */
public class RocFeatherLongbowItem extends BowItem {
	public static final float SPEED = GearStats.LONGBOW_ARROW_SPEED, DRAW_SECONDS = GearStats.LONGBOW_DRAW_SECONDS;
	public static final int FULL_DRAW_TICKS = Math.round(20 * DRAW_SECONDS);

	public RocFeatherLongbowItem(Properties properties) {
		super(properties);
	}

	public static float power(int timeHeld) {
		return getPowerForTime(Math.round(timeHeld / DRAW_SECONDS));
	}

	/** Arrow launch speed: vanilla BowItem uses power x 3.0; the longbow x1.4 (ARSENAL.md §6). */
	public static float arrowVelocity(float pow) {
		return pow * 3.0F * SPEED;
	}

	/** Draw progress 0..1 over the 1.4 s draw. */
	public static float drawProgress(int ticksUsing) {
		return Math.max(0F, Math.min(ticksUsing / (20.0F * DRAW_SECONDS), 1.0F));
	}

	/**
	 * Longbow zoom (user decision): the zoom factor eases in with the square of the draw, to 1.5x at full draw and to 2x
	 * while also sneaking at full draw ({@code sneakBlend} 0..1 blends between them). Never more than 2x. The FOV modifier
	 * vanilla multiplies in is {@code 1 / zoom}.
	 */
	public static float zoom(float progress, float sneakBlend) {
		float p = Math.max(0F, Math.min(progress, 1F));
		float s = Math.max(0F, Math.min(sneakBlend, 1F));
		float target = GearStats.LONGBOW_ZOOM + (GearStats.LONGBOW_ZOOM_SNEAK - GearStats.LONGBOW_ZOOM) * s;
		float z = 1.0F + p * p * (target - 1.0F);
		return Math.min(z, GearStats.LONGBOW_ZOOM_SNEAK);
	}

	/** No Infinity (ARSENAL.md con); every other bow enchantment as vanilla decides. Covers table, anvil and /enchant. */
	@Override
	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		if (enchantment.is(Enchantments.INFINITY)) return false;
		return super.canBeEnchantedWith(stack, enchantment, context);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (!(entity instanceof Player player)) return false;
		ItemStack projectile = player.getProjectile(stack);
		if (projectile.isEmpty()) return false;
		float pow = power(getUseDuration(stack, entity) - remainingTime);
		if (pow < 0.1) return false;
		ItemStack arrow = takeArrow(projectile, player);
		if (level instanceof ServerLevel server && !arrow.isEmpty()) {
			shoot(server, player, player.getUsedItemHand(), stack, List.of(arrow), arrowVelocity(pow), 1.0F, pow == 1.0F, null);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F + pow * 0.3F);
		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	/** Always spends one arrow (an Infinity enchantment that got on anyway is ignored); creative players spend none. */
	static ItemStack takeArrow(ItemStack projectile, Player player) {
		if (player.hasInfiniteMaterials()) {
			ItemStack copy = projectile.copyWithCount(1);
			copy.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
			return copy;
		}
		if (player.level().isClientSide()) return projectile.copyWithCount(1);
		ItemStack used = projectile.split(1);
		if (projectile.isEmpty()) player.getInventory().removeItem(projectile);
		return used;
	}
}
