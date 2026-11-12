package dev.elsewhere.lore;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Whispers of a Crown": hidden until the first step (the root tab is hidden and unlocks with the first patrol, fragment
 * or Proclamation). Item steps are JSON triggers; code steps use {@code minecraft:impossible} criteria awarded here.
 */
public final class LoreAdvancements {
	private LoreAdvancements() {}

	public static final String ROOT = "whispers/root";

	public static void init() {}

	/** Awards {@code <factionKey>_<step>} (steps: road, decree, border) for a rival; Aldermark has its own legacy steps. */
	public static boolean awardFaction(ServerPlayer p, net.minecraft.resources.Identifier faction, String step) {
		LoreFactions.Lore f = LoreFactions.of(faction);
		if (!f.rival()) return false;
		boolean r = award(p, f.key + "_" + step);
		if (!step.equals("road")) award(p, f.key + "_road");
		return r;
	}

	/** A player came near a border marker. Calls are idempotent. */
	public static void hintSeen(ServerPlayer p, net.minecraft.resources.Identifier faction, String kind) {
		LAST_HINT = LoreFactions.of(faction).key + ":" + kind;
		award(p, "border_hint");
		awardFaction(p, faction, "border");
	}

	/** Last hint routed (self-test: lets the test check routing without a real player's advancement state). */
	public static volatile String LAST_HINT = "";

	/** Awards the single criterion {@code done} of {@code elsewhere:whispers/<name>} (also the root, so the tab opens). */
	public static boolean award(ServerPlayer p, String name) {
		boolean r = grant(p, "whispers/" + name);
		if (!name.equals("root")) grant(p, ROOT);
		return r;
	}

	private static boolean grant(ServerPlayer p, String path) {
		AdvancementHolder h = p.level().getServer().getAdvancements().get(LoreContent.id(path));
		if (h == null) return false;
		boolean any = false;
		for (String c : h.value().criteria().keySet()) if (p.getAdvancements().award(h, c)) any = true;
		return any;
	}

	public static boolean isDone(ServerPlayer p, String name) {
		AdvancementHolder h = p.level().getServer().getAdvancements().get(LoreContent.id("whispers/" + name));
		return h != null && p.getAdvancements().getOrStartProgress(h).isDone();
	}
}
