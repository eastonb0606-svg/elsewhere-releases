package dev.elsewhere.gear3.s3.willo;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionStaffItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Will-o'-Lantern item: a summoner staff for the Marsh Wisp (hold use for the 1 s call, 10 Glimmer, 1 slot, 1 durability;
 * sneak-use sends the wisp home). One wisp per player: a new call puts out the old one. With the {@code will_o_lantern} kill
 * switch off it does nothing but say so.
 */
public class WillOLanternItem extends MinionStaffItem {
	public WillOLanternItem(Item.Properties props) {
		super(() -> WillOLantern.WISP, props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!WillOLantern.ENABLED) {
			if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.elsewhere.will_o_lantern.off"));
			return InteractionResult.FAIL;
		}
		if (player.isShiftKeyDown() && player instanceof ServerPlayer sp && MinionManager.dismissType(sp, minionType()) > 0)
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer sp)
			call(sp, stack, sp.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		return stack;
	}

	@Override
	public @Nullable MinionEntity summonNow(ServerPlayer sp, ItemStack stack) {
		return call(sp, stack, EquipmentSlot.MAINHAND);
	}

	/** The call: the summoner rules (Glimmer, slots), then any older wisp of this player goes out, and a hint says what it will echo. */
	@Nullable MinionEntity call(ServerPlayer sp, ItemStack stack, EquipmentSlot slot) {
		if (!WillOLantern.ENABLED) return null;
		MinionEntity m = MinionManager.summon(sp, minionType(), WillOLantern.SUMMON_COST);
		if (m == null) return null;
		for (MinionEntity o : MinionManager.minions(sp)) if (o != m && o instanceof MarshWisp) MinionManager.dismiss(o);
		sp.level().playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.9F, 1.6F);
		Item last = WillOLantern.lastSpell(sp);
		sp.sendOverlayMessage(last == null
			? Component.translatable("message.elsewhere.will_o_lantern.nothing")
			: Component.translatable("message.elsewhere.will_o_lantern.echo", new ItemStack(last).getHoverName()));
		stack.hurtAndBreak(1, sp, slot);
		return m;
	}
}
