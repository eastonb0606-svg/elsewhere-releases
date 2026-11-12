package dev.elsewhere.gear2.item;

import dev.elsewhere.gear2.Gear2Stats;
import dev.elsewhere.gear2.mixin.Gear2ArrowAccess;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * gear-ranged-a Mirrorbolt Arbalest: a heavy crossbow. Bolts deal 14, pierce 3 mobs built in (so Multishot still works;
 * the Piercing enchantment is refused), 2.5 s reload (Quick Charge still shortens it), normal arrows only, no fireworks.
 */
public class MirrorboltArbalest extends CrossbowItem {
	public MirrorboltArbalest(net.minecraft.world.item.Item.Properties p) {
		super(p);
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return ARROW_ONLY;
	}

	@Override
	public Predicate<ItemStack> getSupportedHeldProjectiles() {
		return ARROW_ONLY;
	}

	/** Reload time in ticks: 2.5 s, shortened by Quick Charge like the vanilla crossbow's 1.25 s. */
	public static int chargeTicks(ItemStack stack, LivingEntity user) {
		return Mth.floor(EnchantmentHelper.modifyCrossbowChargingTime(stack, user, Gear2Stats.ARBALEST_RELOAD_SECONDS) * 20.0F);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return chargeTicks(stack, user) + 3;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
		if (this.getUseDuration(stack, user) - timeLeft < chargeTicks(stack, user)) return false;
		return super.releaseUsing(stack, level, user, timeLeft);
	}

	/** Vanilla plays the loading sounds on its own 1.25 s clock; feed it our progress scaled to that clock. */
	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		int dur = this.getUseDuration(stack, user);
		int scaled = (dur - remaining) * CrossbowItem.getChargeDuration(stack, user) / Math.max(1, chargeTicks(stack, user));
		super.onUseTick(level, user, stack, dur - scaled);
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean crit) {
		Projectile p = super.createProjectile(level, shooter, weapon, ammo, crit);
		if (p instanceof AbstractArrow a) configure(a);
		return p;
	}

	/** 14 damage at crossbow speed (no random crit bonus), pierces 3 mobs. */
	public static void configure(AbstractArrow a) {
		a.setBaseDamage(Gear2Stats.ARBALEST_DAMAGE / Gear2Stats.ARBALEST_POWER - 0.01);
		a.setCritArrow(false);
		((Gear2ArrowAccess) a).gear2$setPierceLevel((byte) Gear2Stats.ARBALEST_PIERCE);
	}

	@Override
	public int getDefaultProjectileRange() {
		return Gear2Stats.ARBALEST_RANGE;
	}

	@Override
	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		if (enchantment.is(Enchantments.PIERCING) || enchantment.is(Enchantments.INFINITY)) return false;
		return super.canBeEnchantedWith(stack, enchantment, context);
	}
}
