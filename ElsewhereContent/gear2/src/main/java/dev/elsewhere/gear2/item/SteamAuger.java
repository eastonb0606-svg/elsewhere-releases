package dev.elsewhere.gear2.item;

import dev.elsewhere.gear2.Gear2Items;
import dev.elsewhere.gear2.Gear2Stats;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * gear-drill-a Steam Auger: 2.5x netherite pickaxe speed on stone/ores/deepslate, 2-block reach (attribute), Silk Touch
 * yes / Fortune no / Mending no (too hot). Burns 1 coal or charcoal from your inventory per 100 blocks; without fuel it
 * mines like a bare hand.
 */
public class SteamAuger extends Item {
	public SteamAuger(net.minecraft.world.item.Item.Properties p) {
		super(p);
	}

	public static int fuel(ItemStack stack) {
		return stack.getOrDefault(Gear2Items.AUGER_FUEL, 0);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return fuel(stack) > 0 ? super.getDestroySpeed(stack, state) : 1.0F;
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F && fuel(stack) > 0) stack.set(Gear2Items.AUGER_FUEL, fuel(stack) - 1);
		return super.mineBlock(stack, level, state, pos, miner);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayer p) || level.getGameTime() % 10 != 0 || fuel(stack) > 0) return;
		if (refuel(p, stack)) return;
		if (slot == EquipmentSlot.MAINHAND && level.getGameTime() % 40 == 0) p.sendOverlayMessage(Component.translatable("message.elsewhere.steam_auger.cold"));
	}

	/** Burns one coal/charcoal from the inventory into 100 blocks of steam. */
	public static boolean refuel(Player p, ItemStack auger) {
		if (p.getAbilities().instabuild) {
			auger.set(Gear2Items.AUGER_FUEL, Gear2Stats.AUGER_BLOCKS_PER_COAL);
			return true;
		}
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.is(ItemTags.COALS)) {
				s.shrink(1);
				auger.set(Gear2Items.AUGER_FUEL, Gear2Stats.AUGER_BLOCKS_PER_COAL);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		if (enchantment.is(Enchantments.FORTUNE) || enchantment.is(Enchantments.MENDING)) return false;
		return super.canBeEnchantedWith(stack, enchantment, context);
	}
}
