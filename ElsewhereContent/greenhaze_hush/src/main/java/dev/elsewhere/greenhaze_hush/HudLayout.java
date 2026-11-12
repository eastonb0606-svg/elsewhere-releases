package dev.elsewhere.greenhaze_hush;

/**
 * fix-hud-and-jei: vertical layout of the stacked Haze and Resonance meters (QA #2: the Haze label drew on top of the Resonance
 * bar because the stack pitch, 14, was smaller than one meter: a 10 px label above a 7 px bar). Pure maths so it is tested on the server.
 */
public final class HudLayout {
	private HudLayout() {}

	public static final int LABEL_HEIGHT = 10, BAR_HEIGHT = 7, PITCH = 20;

	/** y of the bar at stack slot {@code slot} (0 = lowest). */
	public static int slotY(int guiHeight, int slot) {
		return guiHeight - 22 - slot * PITCH;
	}

	/** Top (label) and bottom (bar) edge of the slot's footprint. */
	public static int top(int guiHeight, int slot) { return slotY(guiHeight, slot) - LABEL_HEIGHT; }

	public static int bottom(int guiHeight, int slot) { return slotY(guiHeight, slot) + BAR_HEIGHT; }

	public static boolean overlaps(int guiHeight, int a, int b) {
		return a != b && top(guiHeight, a) < bottom(guiHeight, b) && top(guiHeight, b) < bottom(guiHeight, a);
	}
}
