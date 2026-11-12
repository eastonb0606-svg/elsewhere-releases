package dev.elsewhere.core.gear;

import dev.elsewhere.Elsewhere;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * polish-gear-rules. G3 slow Mending: items in {@code #elsewhere:slow_mending} repair 1 durability per XP point (vanilla 2),
 * via {@code EnchantmentHelperMixin}. G4 lockout: an Elsewhere death-protection item (Resin Heart Locket, Ankh, Mournmaw's
 * Blessing, ...) does not trigger within 30 s of any death protection (vanilla totems included) for that player, via
 * {@code LivingEntityMixin.checkTotemDeathProtection}. Vanilla totems themselves are unchanged.
 */
public final class GearRules {
	private GearRules() {}

	public static final TagKey<Item> SLOW_MENDING = TagKey.create(Registries.ITEM, Elsewhere.id("slow_mending"));
	public static final int LOCKOUT_TICKS = 30 * 20;
	private static final Map<UUID, Long> LAST_PROTECTION = new HashMap<>();

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_PROTECTION.clear());
	}

	/** Mending repair amount for {@code stack}: halved for slow-Mending items (at least 1 when vanilla gives any). */
	public static int mendingRepair(final ItemStack stack, final int vanilla) {
		if (vanilla <= 0 || !stack.is(SLOW_MENDING)) {
			return vanilla;
		}
		return Math.max(1, vanilla / 2);
	}

	/** The stack vanilla would use for death protection (first hand that has the component), or empty. */
	public static ItemStack protectionStack(final LivingEntity e) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack s = e.getItemInHand(hand);
			if (s.has(DataComponents.DEATH_PROTECTION)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	public static boolean isElsewhereProtection(final ItemStack s) {
		return !s.isEmpty() && s.has(DataComponents.DEATH_PROTECTION) && BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(Elsewhere.MOD_ID);
	}

	public static boolean lockedOut(final LivingEntity e) {
		Long last = LAST_PROTECTION.get(e.getUUID());
		return last != null && e.level().getGameTime() - last < LOCKOUT_TICKS;
	}

	/** True if this death protection must not trigger now (Elsewhere item inside the 30 s lockout). */
	public static boolean blocks(final LivingEntity e) {
		if (!(e instanceof ServerPlayer p)) {
			return false;
		}
		ItemStack s = protectionStack(e);
		if (isElsewhereProtection(s) && lockedOut(e)) {
			p.sendSystemMessage(Component.translatable("message.elsewhere.gear.lockout", s.getHoverName()).withStyle(ChatFormatting.RED));
			return true;
		}
		return false;
	}

	/** Called when any death protection triggered for {@code e}. */
	public static void triggered(final LivingEntity e) {
		if (e instanceof ServerPlayer) {
			LAST_PROTECTION.put(e.getUUID(), e.level().getGameTime());
		}
	}

	/** Self-test hook. */
	public static void setLastProtection(final UUID id, final long gameTime) {
		LAST_PROTECTION.put(id, gameTime);
	}
}
