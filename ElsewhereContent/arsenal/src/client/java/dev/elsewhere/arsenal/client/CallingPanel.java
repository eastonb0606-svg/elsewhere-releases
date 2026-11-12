package dev.elsewhere.arsenal.client;

import dev.elsewhere.arsenal.spell.Calling;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Calling panel (GEAR.md A1 UI): a small HUD panel with the four paths, rank and mastery bar, shown while toggled with its key
 * (unbound by default). Off switch: {@code calling_panel} in config/elsewhere-overhaul3.json. Reads the synced pair; draws only when shown.
 */
public final class CallingPanel {
	private CallingPanel() {}

	public static boolean shown;
	private static final int[] COLORS = {0xFFE0A050, 0xFF8FD16A, 0xFFB38CFF, 0xFF6AC6D1};

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!shown) return;
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null) return;
		Font font = mc.font;
		int x = 6, y = dev.elsewhere.arsenal.CallingPanelLayout.top(), w = 164, rowH = 12; // below Green Leaf's high meter when it is loaded
		g.fill(x - 3, y - 3, x + w + 3, y + 14 + 4 * rowH, 0xA0101018);
		g.text(font, "Callings", x, y, 0xFFE8E0FF, true);
		y += 12;
		for (Calling.Path path : Calling.Path.values()) {
			float m = Calling.mastery(p, path);
			String label = Calling.name(path) + " " + rank(Calling.rank(p, path));
			g.text(font, label, x, y, 0xFFD0D0D0, true);
			int bx = x + 116, bw = 44;
			g.fill(bx, y + 2, bx + bw, y + 7, 0xFF2A2A33);
			g.fill(bx, y + 2, bx + Math.round(bw * m), y + 7, COLORS[path.ordinal()]);
			y += rowH;
		}
	}

	static String rank(Calling.Rank r) {
		return switch (r) {
			case PRIMARY -> "Primary";
			case SECONDARY -> "Secondary";
			case OPEN -> "Open";
			case UNATTUNED -> "Unattuned";
		};
	}

	/** Tooltip line for a calling item or Leaning piece, or null. */
	public static Component tooltip(Player p, ItemStack stack) {
		Calling.Path path = Calling.itemPath(stack);
		boolean lean = false;
		if (path == null) {
			for (Calling.Path q : Calling.Path.values()) if (q.leans(stack)) {
				path = q;
				lean = true;
				break;
			}
		}
		if (path == null || p == null) return null;
		float m = Calling.mastery(p, path);
		String s = Calling.name(path) + ": " + switch (Calling.rank(p, path)) {
			case PRIMARY -> "Primary";
			case SECONDARY -> "Secondary";
			case OPEN -> "Open";
			case UNATTUNED -> "Unattuned";
		} + " " + Math.round(m * 100) + "% (x" + String.format("%.2f", Calling.damageOf(m)) + ")" + (lean ? ", leans +5%" : "");
		return Component.literal(s);
	}
}
