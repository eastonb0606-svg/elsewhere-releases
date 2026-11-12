package dev.elsewhere.stub.overworld_bosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player boss loot (BOSSES.md "Loot per player"): every participant who dealt at least 5% of the damage or
 * survived 60 s inside the seal gets an own roll; a player's first kill of each boss guarantees the tier signature
 * item; duplicate uniques become Boss Tokens. Delivery: owner-locked item entities (only that player can pick them
 * up). polish-loot-chest = No: items lie on the ground at the boss, anyone may take them. The first-kill / unique records are scoreboard
 * tags on the player so they persist without extra save data.
 */
public final class BossLoot {
	public static final float SIGNATURE_REPEAT_CHANCE = 0.10F;

	private BossLoot() {}

	public interface Roll {
		/** The boss's own drops for one player. */
		List<ItemStack> roll(ServerPlayer player, boolean firstKill);
	}

	public static String killTag(String bossId) {
		return "elsewhere_killed_" + bossId;
	}

	public static boolean hasKilled(ServerPlayer player, String bossId) {
		return player.entityTags().contains(killTag(bossId));
	}

	/** Returns what each player received (for the self-test). */
	public static List<List<ItemStack>> distribute(ServerLevel level, Vec3 at, List<ServerPlayer> players, String bossId, Item tierSignature, Roll roll) {
		List<List<ItemStack>> out = new ArrayList<>();
		int i = 0;
		for (ServerPlayer player : players) {
			boolean first = !hasKilled(player, bossId);
			player.addTag(killTag(bossId));
			List<ItemStack> drops = new ArrayList<>(roll.roll(player, first));
			if (tierSignature != null && (first || level.getRandom().nextFloat() < SIGNATURE_REPEAT_CHANCE)) drops.add(new ItemStack(tierSignature));
			List<ItemStack> given = new ArrayList<>();
			for (ItemStack stack : drops) {
				ItemStack s = stack;
				if (s.getMaxStackSize() == 1) {
					String tag = "elsewhere_got_" + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
					if (player.entityTags().contains(tag)) s = new ItemStack(BossRegistry.BOSS_TOKEN);
					else player.addTag(tag);
				}
				given.add(s);
				double a = (i * 0.9) + given.size() * 0.4;
				ItemEntity e = new ItemEntity(level, at.x + Math.cos(a) * 1.2, at.y + 0.5, at.z + Math.sin(a) * 1.2, s);
				e.setExtendedLifetime();
				e.setGlowingTag(true);
				level.addFreshEntity(e);
			}
			out.add(given);
			i++;
		}
		return out;
	}
}
