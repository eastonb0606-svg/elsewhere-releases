package dev.elsewhere.gear3.s3.wolfcall;

import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionStaffItem;
import dev.elsewhere.summoner.GearStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Wolf-Call Horn: a summoner staff you blow like a goat horn (toot pose, 1 s call that damage interrupts, like every staff).
 * When the call completes one Warg answers through {@code MinionManager.summon} (10 Glimmer, slot cap, Herd focus); sneak-use
 * sends your Wargs home. Repaired with Hacksilver.
 */
public class WolfcallHornItem extends MinionStaffItem {
	public WolfcallHornItem(Properties props) {
		super(() -> Wolfcall.WARG, props.repairable(Gear3Items.repairTag(Wolfcall.ID)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Wolfcall.ENABLED) {
			if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.elsewhere.hrafnholt_wolfcall_horn.disabled"));
			return InteractionResult.FAIL;
		}
		InteractionResult r = super.use(level, player, hand);
		// the horn blast starts with the call (everyone around hears it); sneak-use only dismisses
		if (!player.isShiftKeyDown() && !level.isClientSide())
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), Wolfcall.HORN_BLOW, SoundSource.PLAYERS, 2.0F, 1.0F);
		return r;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (Wolfcall.ENABLED && entity instanceof ServerPlayer sp) {
			MinionEntity m = MinionManager.summon(sp, minionType(), GearStats.SUMMON_GLIMMER_COST);
			if (m != null) {
				stack.hurtAndBreak(1, sp, sp.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
				Wolfcall.onCalled(sp, m);
			}
		}
		return stack;
	}

	/** Self-test / API: completes a call at once (same rules as finishing the 1 s blow). */
	@Override
	public @Nullable MinionEntity summonNow(ServerPlayer sp, ItemStack stack) {
		if (!Wolfcall.ENABLED) return null;
		MinionEntity m = super.summonNow(sp, stack);
		if (m != null) Wolfcall.onCalled(sp, m);
		return m;
	}
}
