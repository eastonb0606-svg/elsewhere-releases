package dev.elsewhere.core.wayfinder;

import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.core.travel.RiftTravel;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Given on every accidental arrival. Its needle points at the nearest Return Rift; using it inside an Elsewhere
 * dimension opens one rift home, then it crumbles.
 */
public class FrayedWayfinderItem extends Item {
	public FrayedWayfinderItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!ElsewhereDimensions.isElsewhere(sp.level().dimension())) {
			sp.sendOverlayMessage(Component.translatable("message.elsewhere.frayed.home_already").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (!RiftTravel.openForcedRift(sp, null)) {
			return InteractionResult.FAIL;
		}
		sp.sendOverlayMessage(Component.translatable("message.elsewhere.frayed.used").withStyle(ChatFormatting.LIGHT_PURPLE));
		ItemStack stack = player.getItemInHand(hand);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(final ItemStack stack, final ServerLevel level, final Entity owner, final @Nullable EquipmentSlot slot) {
		if (level.getGameTime() % 40 != 0) {
			return;
		}
		if (ReturnRifts.enabledIn(level.dimension())) {
			BlockPos rift = ReturnRifts.nearestRift(level, owner.getBlockX(), owner.getBlockZ());
			GlobalPos target = GlobalPos.of(level.dimension(), rift);
			LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
			if (current == null || current.target().isEmpty() || !current.target().get().equals(target)) {
				stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
			}
		} else if (stack.has(DataComponents.LODESTONE_TRACKER)) {
			stack.remove(DataComponents.LODESTONE_TRACKER);
		}
	}

	@Override
	public boolean isFoil(final ItemStack stack) {
		return false;
	}

	@Override
	public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		builder.accept(Component.translatable("item.elsewhere.frayed_wayfinder.desc").withStyle(ChatFormatting.GRAY));
	}
}
