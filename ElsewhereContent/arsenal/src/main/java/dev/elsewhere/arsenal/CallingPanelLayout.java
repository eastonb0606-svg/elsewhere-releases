package dev.elsewhere.arsenal;

/**
 * Where the Calling panel (client {@code CallingPanel}, top-left) sits. Green Leaf draws its high meter in the same corner:
 * a 22 px box from (4, 4), so its bottom edge is y 26. With Green Leaf loaded the panel starts below that box and the two
 * never overlap; the gap stays while sober so the panel doesn't jump. Pure maths, in common code so the server-side Green Leaf
 * compat suite can check it. Kill switch {@code calling_panel_greenleaf} (config/elsewhere-overhaul3.json).
 */
public final class CallingPanelLayout {
	private CallingPanelLayout() {}

	/** y of the panel's first text row; its dark background starts {@link #PAD} px above. */
	public static final int TOP = 6, TOP_BELOW_GREEN_LEAF = 32, PAD = 3;
	/** Green Leaf's high meter box: (4, 4) to (4 + width, 26). */
	public static final int GREEN_LEAF_METER_TOP = 4, GREEN_LEAF_METER_BOTTOM = 26;

	private static Boolean belowGreenLeaf;

	/** This run's panel top (read once). */
	public static int top() {
		if (belowGreenLeaf == null) belowGreenLeaf = topFor(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("greenleaf"),
			Overhaul3.on("calling_panel_greenleaf")) != TOP;
		return belowGreenLeaf ? TOP_BELOW_GREEN_LEAF : TOP;
	}

	public static int topFor(boolean greenLeafLoaded, boolean switchOn) {
		return greenLeafLoaded && switchOn ? TOP_BELOW_GREEN_LEAF : TOP;
	}

	/** True when the panel's background would cover part of Green Leaf's meter. */
	public static boolean overlapsGreenLeafMeter(int panelTop, int rows, int rowHeight) {
		int bgTop = panelTop - PAD, bgBottom = panelTop + 14 + rows * rowHeight;
		return bgTop < GREEN_LEAF_METER_BOTTOM && GREEN_LEAF_METER_TOP < bgBottom;
	}
}
