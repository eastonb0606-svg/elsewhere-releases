package dev.elsewhere.gear2.client;

import dev.elsewhere.gear2.Gear2Content;
import dev.elsewhere.gear2.Gear2Tooltips;
import dev.elsewhere.gear2.Overhaul3;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

/**
 * gear2 client entry (3.0 s-p5-tooltips). The merge (ElsewhereBeta/tools/merge.py) calls the FIRST gear2
 * {@code *Client.java} with {@code public static void initClient()}, so this is the only one: other gear2 client code adds
 * one line below (claim it first). The dev run uses it as the module's client entrypoint.
 * P5: puts {@link Gear2Tooltips} into gear2 tooltips, in a phase after every default listener (boss_ui's generic polish
 * block runs first), next to the item's lore and above the vanilla attribute block; the "hold Shift" hint is added only
 * when no Elsewhere hint is there yet. Kill switch: {@code "p5_tooltips": false} in config/elsewhere-overhaul3.json.
 */
public final class Gear2Client implements ClientModInitializer {
	public static final boolean TOOLTIPS = Overhaul3.on("p5_tooltips");
	public static final Identifier PHASE = Gear2Content.id("p5_tooltips_gear2");
	/** Test hook for the client gametest (it cannot hold Shift). */
	public static volatile boolean forceShift;
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		if (TOOLTIPS) {
			ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
			ItemTooltipCallback.EVENT.register(PHASE, (stack, ctx, flag, lines) -> {
				Gear2Tooltips.Lines l = Gear2Tooltips.of(stack);
				if (l.isEmpty() || lines.isEmpty()) return;
				insert(lines, l.summary(), l.detail(), forceShift || Minecraft.getInstance().hasShiftDown());
			});
		}
	}

	/** Summary (and with Shift the detail) after the lore: before the blank line that opens the attribute block, else before the advanced lines. */
	static void insert(List<Component> lines, List<Component> summary, List<Component> detail, boolean shift) {
		int at = lines.size();
		boolean hinted = false;
		for (int i = 1; i < lines.size(); i++) {
			String k = lines.get(i).getContents() instanceof TranslatableContents t ? t.getKey() : null;
			if (k == null) continue;
			if (k.equals(Gear2Tooltips.HOLD) || k.equals("elsewhere.polish.tooltip.hold")) hinted = true;
			if (at == lines.size() && (k.startsWith("item.modifiers.") || k.startsWith("elsewhere.polish.tooltip.") || k.equals("item.durability")
				|| k.equals("item.components"))) {
				at = k.startsWith("item.modifiers.") && i > 1 && blank(lines.get(i - 1)) ? i - 1 : i;
			}
		}
		lines.addAll(at, summary);
		at += summary.size();
		if (shift) lines.addAll(at, detail);
		else if (!detail.isEmpty() && !hinted) lines.add(at, Gear2Tooltips.hint());
	}

	private static boolean blank(Component c) {
		return c.getSiblings().isEmpty() && c.getContents() instanceof PlainTextContents p && p.text().isEmpty();
	}
}
