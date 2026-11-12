package dev.elsewhere.rivals.mirewick.boss;

import dev.elsewhere.rivals.mirewick.MirewickState;
import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Chair table (RIVAL-BOSSES): whoever holds the Chair when killed drops, in addition to the faction file's own drops
 * (the court rolls those), the Lantern Coronet (100 percent on the first Chair kill, 67 percent after) and the
 * Marsh-Lantern Standard (50 percent, 33 percent after), each with 10 percent pity per miss up to +60. A duel yield never
 * holds the Chair. Counted per player.
 */
public final class MagnateDrops {
	private MagnateDrops() {}

	/** Drop chance with pity: base plus 10 percent per consecutive miss, capped at base + 60 percent and 100. */
	public static double chance(double base, int misses) {
		return Math.min(1.0, base + Math.min(0.60, misses * 0.10));
	}

	public static List<ItemStack> roll(MirewickState st, boolean firstChairKill, String who, long seed) {
		Random r = new Random(seed);
		List<ItemStack> out = new ArrayList<>();
		int missC = st.pity.getOrDefault(who + ":coronet", 0);
		if (firstChairKill || r.nextDouble() < chance(0.67, missC)) {
			out.add(new ItemStack(MirewickItems.LANTERN_CORONET));
			st.pity.put(who + ":coronet", 0);
		} else st.pity.put(who + ":coronet", missC + 1);
		int missS = st.pity.getOrDefault(who + ":standard", 0);
		if (r.nextDouble() < chance(firstChairKill ? 0.50 : 0.33, missS)) {
			out.add(new ItemStack(MirewickItems.MARSH_LANTERN_STANDARD));
			st.pity.put(who + ":standard", 0);
		} else st.pity.put(who + ":standard", missS + 1);
		return out;
	}

	public static List<ItemStack> chairTable(ServerLevel level, MagnateBoss b, List<ServerPlayer> credited) {
		MirewickState st = MirewickState.get(level.getServer());
		boolean first = st.kills.getOrDefault("chair", 0) == 0;
		st.kills.merge("chair", 1, Integer::sum);
		List<ItemStack> all = new ArrayList<>();
		if (credited.isEmpty()) {
			for (ItemStack s : roll(st, first, "none", level.getRandom().nextLong())) {
				b.spawnAtLocation(level, s);
				all.add(s);
			}
		}
		for (ServerPlayer p : credited) {
			for (ItemStack s : roll(st, first, p.getUUID().toString(), level.getRandom().nextLong())) {
				b.spawnAtLocation(level, s);
				all.add(s);
			}
			first = false;
		}
		st.setDirty();
		return all;
	}
}
