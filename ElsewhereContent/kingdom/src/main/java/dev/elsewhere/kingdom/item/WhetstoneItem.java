package dev.elsewhere.kingdom.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Whetstone: restores 15% durability to a sword, axe, spear or tool. Use it with the tool in the other hand, or
 * right-click the tool with the whetstone on the cursor in an inventory. Consumed on use.
 */
public class WhetstoneItem extends Item {
	public static final float REPAIR = 0.15F;

	public WhetstoneItem(Properties p) {
		super(p);
	}

	public static boolean canSharpen(ItemStack s) {
		if (s.isEmpty() || !s.isDamageableItem() || !s.isDamaged()) return false;
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES)
			|| s.is(ItemTags.SPEARS) || s.has(DataComponents.TOOL) || s.has(DataComponents.WEAPON);
	}

	/** Repairs 15% of the max durability (at least 1). Returns the points restored. */
	public static int sharpen(ItemStack s) {
		int amount = Math.max(1, (int) Math.ceil(s.getMaxDamage() * REPAIR));
		int before = s.getDamageValue();
		s.setDamageValue(Math.max(0, before - amount));
		return before - s.getDamageValue();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack self = player.getItemInHand(hand);
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!canSharpen(other)) {
			if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.elsewhere.whetstone.nothing"));
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			sharpen(other);
			self.consume(1, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
			player.sendOverlayMessage(Component.translatable("message.elsewhere.whetstone.done", other.getHoverName()));
		}
		player.getCooldowns().addCooldown(self, 10);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY) return false;
		ItemStack target = slot.getItem();
		if (!canSharpen(target)) return false;
		sharpen(target);
		self.shrink(1);
		player.playSound(SoundEvents.GRINDSTONE_USE, 0.8F, 1.3F);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, java.util.function.Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
	}
}
