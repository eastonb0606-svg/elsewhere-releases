package dev.elsewhere.arsenal.client;

import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.Overhaul3;
import dev.elsewhere.arsenal.spell.ArsenalTooltips;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

/**
 * 3.0 polish P5 client hook (s-p5-tooltips): puts {@link ArsenalTooltips} into arsenal (and gear3 caster) tooltips.
 * Runs in a phase after every default tooltip listener (ArsenalClient's Calling/cost lines, boss_ui's generic polish
 * block), so it can place its lines with the item's description, above the vanilla attribute block, and add the
 * "hold Shift" hint only when no other Elsewhere hint is there yet. Hover-only work; the lines are cached per item.
 * Kill switch: {@code "p5_tooltips": false} in config/elsewhere-overhaul3.json.
 */
public final class P5Tooltips {
	private P5Tooltips() {}

	public static final boolean ENABLED = Overhaul3.on("p5_tooltips");
	public static final Identifier PHASE = ArsenalContent.id("p5_tooltips");
	/** Test hook for the client gametest (it cannot hold Shift). */
	public static volatile boolean forceShift;
	private static boolean registered;

	public static void init() {
		if (registered || !ENABLED) return;
		registered = true;
		ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		ItemTooltipCallback.EVENT.register(PHASE, (stack, ctx, flag, lines) -> {
			ArsenalTooltips.Lines l = ArsenalTooltips.of(stack);
			if (l.isEmpty() || lines.isEmpty()) return;
			boolean shift = forceShift || Minecraft.getInstance().hasShiftDown();
			insert(lines, l.summary(), l.detail(), shift, ArsenalTooltips.HOLD);
		});
	}

	/**
	 * Inserts {@code summary} (and with Shift the {@code detail}) after the description lines: before the blank line that
	 * opens the vanilla attribute block, else before the advanced (F3+H) lines, else at the end. Without Shift a hint
	 * line follows, unless an Elsewhere hint (this one or boss_ui's) is already in the tooltip.
	 */
	static void insert(List<Component> lines, List<Component> summary, List<Component> detail, boolean shift, String holdKey) {
		int at = lines.size();
		boolean hinted = false;
		for (int i = 1; i < lines.size(); i++) {
			String k = key(lines.get(i));
			if (k == null) continue;
			if (k.equals(holdKey) || k.equals("elsewhere.polish.tooltip.hold")) hinted = true;
			if (at == lines.size() && (k.startsWith("item.modifiers.") || k.startsWith("elsewhere.polish.tooltip.") || k.equals("item.durability")
				|| k.equals("item.components"))) {
				at = k.startsWith("item.modifiers.") && i > 1 && blank(lines.get(i - 1)) ? i - 1 : i;
			}
		}
		lines.addAll(at, summary);
		at += summary.size();
		if (shift) lines.addAll(at, detail);
		else if (!detail.isEmpty() && !hinted) lines.add(at, ArsenalTooltips.hint());
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
	}

	private static boolean blank(Component c) {
		return c.getSiblings().isEmpty() && c.getContents() instanceof PlainTextContents p && p.text().isEmpty();
	}
}
