package dev.elsewhere.verdigris_ironpeak;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Beta 2 boss drops of other modules' items, looked up by id and skipped when absent (ARSENAL.md §5b/§5c):
 * Archdeacon Verdigrim drops the Brassback Key (50% on a player's first kill, 20% after); Galvanox drops the Engineer's
 * Harness (the boss class already rolls 50% on a first kill; this adds the 20% on later kills). Called per eligible
 * player from {@code StubBoss.deliverLoot}.
 */
public final class ArsenalLoot {
	private ArsenalLoot() {}

	/** The item id this module adds for a boss (null = none). */
	public static String itemFor(String bossId) {
		return switch (bossId) {
			case "verdigrim" -> "brassback_key";
			case "galvanox" -> "engineers_harness";
			default -> null;
		};
	}

	/** The other module's item as a kit "other" unique (50% first kill, 33% repeat, pity), or null when that module is absent. */
	public static dev.elsewhere.boss.kit.DropRule.Unique unique(String bossId) {
		String id = itemFor(bossId);
		if (id == null) return null;
		var item = BuiltInRegistries.ITEM.getOptional(Reg.id(id));
		if (item.isEmpty()) return null;
		return dev.elsewhere.boss.kit.DropRule.other(bossId + "_unique", () -> new ItemStack(item.get()));
	}

	/** Rolls the kit uniques for one player: this boss's own list plus the other module's item. */
	public static List<ItemStack> roll(net.minecraft.server.level.ServerPlayer player, String bossId, boolean firstKill, List<dev.elsewhere.boss.kit.DropRule.Unique> own) {
		List<dev.elsewhere.boss.kit.DropRule.Unique> all = new ArrayList<>(own);
		var extra = unique(bossId);
		if (extra != null) all.add(extra);
		return dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId, firstKill, all);
	}
}
