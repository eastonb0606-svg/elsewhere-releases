package dev.elsewhere.minion;

import dev.elsewhere.summoner.GearStats;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A minion staff (ARSENAL.md rev 2 §5a/5b): hold right-click for the 1.0 s call, then its minion appears for 10 Glimmer
 * (1 durability); sneak + right-click
 * dismisses every minion from this staff. Melee with it is the player's base 1 damage (no attack modifiers).
 */
public class MinionStaffItem extends Item {
	private final Supplier<EntityType<? extends MinionEntity>> minion;

	public MinionStaffItem(Supplier<EntityType<? extends MinionEntity>> minion, Item.Properties props) {
		super(props);
		this.minion = minion;
	}

	public EntityType<? extends MinionEntity> minionType() {
		return minion.get();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer sp) MinionManager.dismissType(sp, minionType());
			return InteractionResult.SUCCESS;
		}
		// the 1.0 s call: you move at bow-draw speed; damage interrupts it (MinionManager / SummonerPerks)
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return GearStats.SUMMON_CALL_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer sp) {
			MinionEntity m = MinionManager.summon(sp, minionType(), GearStats.SUMMON_GLIMMER_COST);
			if (m != null) stack.hurtAndBreak(1, sp, sp.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		}
		return stack;
	}

	/** Self-test / API: completes a call at once (same rules as finishing the 1 s use). */
	public @org.jspecify.annotations.Nullable MinionEntity summonNow(ServerPlayer sp, ItemStack stack) {
		MinionEntity m = MinionManager.summon(sp, minionType(), GearStats.SUMMON_GLIMMER_COST);
		if (m != null) stack.hurtAndBreak(1, sp, EquipmentSlot.MAINHAND);
		return m;
	}
}
