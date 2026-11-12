package dev.elsewhere.gear3.setbonus;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Shared tooltip wrap helper (A1): splits a long tooltip line into lines of at most {@link #MAX} characters (about 220 GUI px)
 * at spaces, keeping each character's style so every continuation line has the colour/style of the text it continues.
 * Character based, so it works on the common side without a Font.
 */
public final class TooltipWrap {
	private TooltipWrap() {}

	public static final int MAX = 40;

	/** {@code line} as one or more lines; the same instance when it already fits. */
	public static List<Component> wrap(Component line) {
		String plain = line.getString();
		if (plain.length() <= MAX) return List.of(line);
		StringBuilder text = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		line.visit((style, s) -> {
			for (int i = 0; i < s.length(); i++) styles.add(style);
			text.append(s);
			return java.util.Optional.empty();
		}, Style.EMPTY);
		String t = text.toString();
		int indent = 0;
		while (indent < t.length() && t.charAt(indent) == ' ') indent++;
		List<Component> out = new ArrayList<>();
		int start = 0;
		while (start < t.length()) {
			int limit = start == 0 ? MAX : MAX - indent;
			int end = start + limit;
			if (end >= t.length()) end = t.length();
			else {
				int sp = t.lastIndexOf(' ', end);
				end = sp > start + indent ? sp : end;
			}
			out.add(build(t, styles, start, end, start == 0 ? 0 : indent));
			start = end;
			while (start < t.length() && t.charAt(start) == ' ') start++;
		}
		return out;
	}

	private static Component build(String t, List<Style> styles, int from, int to, int indent) {
		MutableComponent c = Component.empty();
		if (indent > 0) c.append(Component.literal(" ".repeat(indent)));
		int i = from;
		while (i < to) {
			int j = i + 1;
			while (j < to && styles.get(j).equals(styles.get(i))) j++;
			c.append(Component.literal(t.substring(i, j)).withStyle(styles.get(i)));
			i = j;
		}
		return c;
	}

	/** Wraps every line after the name line, in place. */
	public static void wrapAll(List<Component> lines) {
		for (int i = 1; i < lines.size(); i++) {
			List<Component> w = wrap(lines.get(i));
			if (w.size() > 1) {
				lines.remove(i);
				lines.addAll(i, w);
				i += w.size() - 1;
			}
		}
	}
}
