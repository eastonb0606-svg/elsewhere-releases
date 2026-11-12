package dev.elsewhere.core.wayfinder;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The base Wayfinder: craft it with a dimension's five themed items to attune it. Using it lists the known attunements. */
public class WayfinderItem extends Item {
	public WayfinderItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer sp && sp.isShiftKeyDown()) {
			// new-boss-waystones: sneak-use warps to your latest waystone in this dimension (Overworld, Nether, End arenas)
			return dev.elsewhere.core.travel.Anchors.warpToWaystone(sp, sp.level().dimension()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer sp && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(sp, dev.elsewhere.core.atlas.AtlasPayload.TYPE)) {
			dev.elsewhere.core.atlas.Atlas.open(sp); // polish-atlas-a: the full-screen Atlas
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.translatable("message.elsewhere.wayfinder.atlas").withStyle(ChatFormatting.LIGHT_PURPLE));
			for (Attunements.Def def : Attunements.all().values()) {
				StringBuilder sb = new StringBuilder();
				for (Item item : def.ingredients()) {
					if (!sb.isEmpty()) {
						sb.append(", ");
					}
					sb.append(new ItemStack(item).getHoverName().getString());
				}
				Component name = Component.translatable("dimension." + def.dimension().identifier().getNamespace() + "." + def.dimension().identifier().getPath());
				sp.sendSystemMessage(Component.literal(" - ").append(name.copy().withStyle(ChatFormatting.GOLD)).append(Component.literal(": " + sb))
					.withStyle(ChatFormatting.GRAY));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		builder.accept(Component.translatable("item.elsewhere.wayfinder.desc").withStyle(ChatFormatting.GRAY));
	}
}
