package dev.elsewhere.boss.kit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Rolling uniques with {@link DropRule} (persistent pity) and handing out loot and XP on the ground. */
public final class BossRewards {
	private BossRewards() {}

	/** XP every boss kill drops, as orbs at the boss (not scaled by who landed the hit). */
	public static final int XP_FLOOR = 400;

	/** Rolls this player's uniques for one kill and records the kill and the pity counters on disk. */
	public static List<ItemStack> rollUniques(ServerPlayer player, String bossId, boolean firstKill, List<DropRule.Unique> uniques) {
		KitStore store = KitStore.of(player.level().getServer());
		List<ItemStack> out = DropRule.roll(firstKill, store.missesView(player.getUUID(), bossId), uniques, player.level().getRandom());
		store.markKilled(player.getUUID(), bossId); // saves the pity changes too
		return out;
	}

	/** Drops the items around {@code at} (items glow so they are easy to find). Anyone may pick them up. */
	public static void dropLoot(ServerLevel level, Vec3 at, List<ItemStack> items) {
		int i = 0;
		for (ItemStack s : items) {
			double a = i++ * 2.399963;
			ItemEntity e = new ItemEntity(level, at.x + Math.cos(a) * 1.2, at.y + 0.5, at.z + Math.sin(a) * 1.2, s);
			e.setExtendedLifetime();
			e.setGlowingTag(true);
			level.addFreshEntity(e);
		}
	}

	/** Always drops XP: {@code amount} (at least {@link #XP_FLOOR}) split into orbs. Returns the amount awarded. */
	public static int awardXp(ServerLevel level, Vec3 at, int amount) {
		int xp = Math.max(XP_FLOOR, amount);
		ExperienceOrb.award(level, at, xp);
		return xp;
	}

	public static List<ItemStack> copy(List<ItemStack> l) { return new ArrayList<>(l); }
}
