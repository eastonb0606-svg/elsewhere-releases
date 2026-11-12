package dev.elsewhere.gear3.setbonus.client;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Set and pair bonus tooltip lines for every piece of every registered bonus (s-setbonus-a and -b): a header
 * {@code Set bonus: <name> (worn/needed)} (gold when on for you, grey otherwise), then with Shift the detail lines and a
 * pair's hybrid strength; without Shift a "Hold Shift" hint. Built only while an item is hovered. Off with the bonus's own
 * kill switch (the line goes away with the bonus).
 */
public final class SetBonusTooltips {
	private SetBonusTooltips() {}

	private static boolean registered;
	/** Self-test / gametest hook: the test cannot press Shift. */
	public static volatile boolean forceShift;

	public static void init() {
		if (registered) return;
		registered = true;
		ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
			if (stack.isEmpty() || SetBonuses.ALL.isEmpty()) return;
			Minecraft mc = Minecraft.getInstance();
			List<Component> add = build(stack, mc.player, forceShift || mc.hasShiftDown());
			if (add.isEmpty()) return;
			lines.addAll(Math.min(lines.size(), 1), add);
		});
		// A1: wrap long lines last, after every other listener has added its own
		net.fabricmc.fabric.api.event.Event<ItemTooltipCallback> ev = ItemTooltipCallback.EVENT;
		net.minecraft.resources.Identifier late = net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "gear3_tooltip_wrap");
		ev.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, late);
		ev.register(late, (stack, ctx, flag, lines) -> dev.elsewhere.gear3.setbonus.TooltipWrap.wrapAll(lines));
	}

	/** The lines for {@code stack} as seen by {@code viewer} (may be null: counts as wearing nothing). */
	public static List<Component> build(ItemStack stack, Player viewer, boolean shift) {
		List<Component> out = new ArrayList<>();
		for (SetBonus b : SetBonuses.ALL) {
			if (!b.enabled() || !b.isPiece(stack)) continue;
			int worn = viewer == null ? 0 : SetBonuses.worn(viewer, b);
			boolean on = viewer != null && SetBonuses.active(viewer, b);
			out.add(Component.translatable(b.isHybrid() ? "setbonus.elsewhere.header_pair" : "setbonus.elsewhere.header",
				Component.translatable(b.langKey()), worn, b.pieceCount()).withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			if (!shift) {
				out.add(Component.translatable("setbonus.elsewhere.shift").withStyle(ChatFormatting.DARK_GRAY));
				continue;
			}
			for (int i = 1; i <= b.detailLines(); i++)
				out.add(Component.literal("  ").append(Component.translatable(b.langKey() + ".detail" + i)).withStyle(on ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
			if (b.isHybrid() && viewer != null)
				out.add(Component.literal("  ").append(Component.translatable("setbonus.elsewhere.pair", Math.round(SetBonuses.hybrid(viewer, b) * 100F),
					Calling.name(b.hybridA()), Calling.name(b.hybridB()))).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (viewer != null && SetBonuses.tier(viewer, b) >= 2)
				out.add(Component.literal("  ").append(Component.translatable("setbonus.elsewhere.tier2")).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return out;
	}
}
