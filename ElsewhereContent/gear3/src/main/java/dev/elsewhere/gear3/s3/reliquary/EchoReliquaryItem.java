package dev.elsewhere.gear3.s3.reliquary;

import dev.elsewhere.gear3.minion.MinionSlots;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionStaffItem;
import dev.elsewhere.summoner.GearStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Echo Reliquary: a summoner minion staff (the 1.0 s call at bow-draw speed, 10 Glimmer and 1 durability per call, damage
 * interrupts it, sneak-use sends its knights home) that calls an {@link EchoKnight}. The knight fills 2 minion slots:
 * with fewer than 2 slots the call is refused before any Glimmer is spent; otherwise older minions go home until the
 * weights fit (gear3 {@link MinionSlots}, s-waryak's weighted rule). Catalogue mechanic {@code "echo_reliquary"} (Gear3Mechanics).
 */
public class EchoReliquaryItem extends MinionStaffItem {
	public EchoReliquaryItem(Properties props) {
		super(() -> EchoReliquary.ECHO_KNIGHT, props);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown() && player instanceof ServerPlayer sp && !canCall(sp)) return InteractionResult.FAIL;
		if (!EchoReliquary.enabled && !player.isShiftKeyDown()) return InteractionResult.FAIL; // client side of the switch
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer sp) call(sp, stack, sp.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		return stack;
	}

	@Override
	public @Nullable MinionEntity summonNow(ServerPlayer sp, ItemStack stack) {
		return call(sp, stack, EquipmentSlot.MAINHAND);
	}

	/** The call: 2 free slots needed (older minions make room), 10 Glimmer, 1 durability. */
	static @Nullable MinionEntity call(ServerPlayer sp, ItemStack stack, EquipmentSlot slot) {
		if (!canCall(sp) || EchoReliquary.ECHO_KNIGHT == null) return null;
		MinionSlots.track(sp, EchoReliquary.ECHO_KNIGHT); // the summon's own slot check then counts by weight
		MinionEntity m = MinionManager.summon(sp, EchoReliquary.ECHO_KNIGHT, GearStats.SUMMON_GLIMMER_COST);
		if (m == null) return null;
		stack.hurtAndBreak(1, sp, slot);
		return m;
	}

	/** Kill switch and the 2-slot rule; tells the player why when it says no. */
	static boolean canCall(ServerPlayer sp) {
		if (!EchoReliquary.enabled) {
			sp.sendOverlayMessage(Component.translatableWithFallback("message.elsewhere.echo_reliquary.off", "The Echo Reliquary is switched off on this server."));
			return false;
		}
		if (EchoReliquary.ECHO_KNIGHT != null && !MinionSlots.fits(sp, EchoReliquary.ECHO_KNIGHT)) {
			sp.sendOverlayMessage(Component.translatableWithFallback("message.elsewhere.echo_reliquary.slots",
				"The Echo Knight needs %s minion slots; you have %s.", EchoReliquary.SLOTS, MinionApi.maxSlots(sp)));
			return false;
		}
		return true;
	}
}
