package dev.elsewhere.gear3.s3.starpiercer;

import dev.elsewhere.gear3.Gear3Hooks;
import dev.elsewhere.gear3.Gear3Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Starpiercer bow (see {@link Starpiercer}). Built on the vanilla bow, so enchantments, ammo rules, the Quickdraw Quiver
 * (its BowItem.releaseUsing hook) and the Fowler's Bandolier (ProjectileWeaponItem.useAmmo) keep working:
 * <ul>
 * <li>draw: the held time is scaled so a full draw takes 1.6 s ({@link Starpiercer#effectiveHeld});</li>
 * <li>speed x1.6 and pierce +2 in {@link #shootProjectile};</li>
 * <li>plain arrows become {@link StarpiercerArrow}; every arrow gets 32 weightless blocks ({@link Starpiercer#fly});</li>
 * <li>sprinting: no draw starts, and a draw in progress is cancelled by the server (the client follows the synced flag);</li>
 * <li>a chime when the draw is full (server, heard nearby: a sniper's tell).</li>
 * </ul>
 */
public class StarpiercerItem extends BowItem {
	public StarpiercerItem(Properties properties) {
		super(properties.enchantable(15).repairable(Gear3Items.repairTag(Starpiercer.ID)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSprinting()) return InteractionResult.FAIL;
		return super.use(level, player, hand);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (level.isClientSide()) return;
		if (user.isSprinting()) {
			user.stopUsingItem();
			level.playSound(null, user.getX(), user.getY(), user.getZ(), Starpiercer.CANCEL.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
			return;
		}
		int used = getUseDuration(stack, user) - ticksRemaining; // ticks drawn before this one
		if (fullAt(used + 1, user) && !fullAt(used, user))
			level.playSound(null, user.getX(), user.getY(), user.getZ(), Starpiercer.DRAW_FULL.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
	}

	/** True when releasing after {@code held} ticks shoots at full power (same steps as {@link #releaseUsing}, the Quiver included). */
	public boolean fullAt(int held, LivingEntity user) {
		int duration = getUseDuration(ItemStack.EMPTY, user);
		int timeLeft = Gear3Hooks.bowTimeLeft(duration - Starpiercer.effectiveHeld(held), duration, user);
		return getPowerForTime(duration - timeLeft) >= 1.0F;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		int duration = getUseDuration(stack, entity);
		boolean shot = super.releaseUsing(stack, level, entity, duration - Starpiercer.effectiveHeld(duration - remainingTime));
		if (shot && level instanceof ServerLevel)
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Starpiercer.SHOOT.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
		return shot;
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean crit) {
		if (!ammo.is(Items.ARROW)) return super.createProjectile(level, shooter, weapon, ammo, crit);
		StarpiercerArrow arrow = new StarpiercerArrow(level, shooter, ammo.copyWithCount(1), weapon);
		if (crit) arrow.setCritArrow(true);
		return arrow;
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle, @Nullable LivingEntity target) {
		super.shootProjectile(shooter, projectile, index, power * Starpiercer.SPEED, uncertainty, angle, target);
		if (projectile instanceof AbstractArrow arrow) Starpiercer.fly(arrow);
	}
}
