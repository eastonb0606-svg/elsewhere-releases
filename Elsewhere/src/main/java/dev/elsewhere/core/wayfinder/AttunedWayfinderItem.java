package dev.elsewhere.core.wayfinder;

import dev.elsewhere.core.travel.RiftTravel;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * An attuned Wayfinder ({@code elsewhere:wayfinder_<dimension>}). Outside its dimension it opens a rift there; inside,
 * a rift home. Reusable; all Wayfinders share one 60 s cooldown (the {@code use_cooldown} component).
 * Content modules register theirs with {@code new AttunedWayfinderItem(props, DIMENSION_KEY, COLOR)}.
 */
public class AttunedWayfinderItem extends WayfinderItem {
	private final ResourceKey<Level> target;
	private final int color;

	public AttunedWayfinderItem(final Properties properties, final ResourceKey<Level> target, final int color) {
		super(properties);
		this.target = target;
		this.color = color;
	}

	public ResourceKey<Level> target() {
		return this.target;
	}

	public int color() {
		return this.color;
	}

	/** Dimensions whose Wayfinder supports sneak-use fast travel to the scaled Overworld spot (Pale Hollow: x4). */
	protected boolean fastTravelHome() {
		return this.target.equals(dev.elsewhere.palehollow.PaleHollowDimension.LEVEL);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			if (sp.isShiftKeyDown() && sp.level().dimension().equals(this.target) && this.fastTravelHome()) {
				// BALANCE.md R: sneak-use inside the Pale Hollow goes to the Overworld at X/Z x coordinate scale
				return RiftTravel.openScaledHomeRift(sp) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			if (sp.isShiftKeyDown() && !sp.level().dimension().equals(this.target)
				&& dev.elsewhere.core.travel.Anchors.waystoneRecord(sp, this.target) != null) {
				// new-boss-waystones: sneak-use outside the dimension warps to your latest waystone there
				return dev.elsewhere.core.travel.Anchors.warpToWaystone(sp, this.target) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			if (!RiftTravel.openWayfinderRift(sp, this.target)) {
				return InteractionResult.FAIL;
			}
			boolean home = sp.level().dimension().equals(this.target);
			sp.sendOverlayMessage(Component.translatable(home ? "message.elsewhere.rift.home" : "message.elsewhere.rift.open")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFoil(final ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		builder.accept(Component.translatable("item.elsewhere.attuned_wayfinder.desc").withStyle(ChatFormatting.GRAY));
	}
}
