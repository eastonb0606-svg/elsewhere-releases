package dev.elsewhere.summoner;

import dev.elsewhere.minion.Herding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Herding Crook (ARSENAL.md rev 3 §5b): a melee hit marks the target Herded for 5 s (one target per player). */
public class HerdingCrookItem extends Item {
	public HerdingCrookItem(Item.Properties p) {
		super(p);
	}

	/** The only enchantments a Herding Crook takes (0.4.0): Knockback, Unbreaking, Mending. */
	public static final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment>> ALLOWED_ENCHANTMENTS = java.util.Set.of(
		net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, net.minecraft.world.item.enchantment.Enchantments.MENDING);

	/** True if {@code enchantment} may go on a Herding Crook (enchanting table, anvil, loot, /enchant). */
	public static boolean allows(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
		for (var k : ALLOWED_ENCHANTMENTS) if (enchantment.is(k)) return true;
		return false;
	}

	/**
	 * Fabric's ALLOW_ENCHANTING covers the enchanting table, anvil, enchant_randomly loot and /enchant: the Crook stays
	 * in {@code #minecraft:enchantable/melee_weapon} and {@code durability} (for Knockback, Unbreaking, Mending) but
	 * refuses every other enchantment (Sharpness, Smite, Bane of Arthropods, Looting, Fire Aspect, Sweeping Edge, ...).
	 */
	public static void initEnchantRules() {
		net.fabricmc.fabric.api.item.v1.EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, target, context) -> {
			if (!(target.getItem() instanceof HerdingCrookItem)) return net.fabricmc.fabric.api.util.TriState.DEFAULT;
			return allows(enchantment) ? net.fabricmc.fabric.api.util.TriState.TRUE : net.fabricmc.fabric.api.util.TriState.FALSE;
		});
	}

	/** Sneak + use: call the minions off the Herded target. */
	@Override
	public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
		if (!player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
		if (player instanceof ServerPlayer sp) Herding.callOff(sp);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer p && target.isAlive()) Herding.mark(p, target);
	}
}
