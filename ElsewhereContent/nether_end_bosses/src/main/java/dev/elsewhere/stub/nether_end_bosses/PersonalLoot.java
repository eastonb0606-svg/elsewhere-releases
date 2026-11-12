package dev.elsewhere.stub.nether_end_bosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Per-player rolls (own first-kill record and pity counters) that drop on the ground at the boss like any mob's loot. First kill of each boss guarantees the tier's signature item; 10% after that.
 */
public final class PersonalLoot {
	private PersonalLoot() {}

	/** Tier item (Cleaver, Aegis, Halo): 100% first kill, low repeat rate. */
	public static final float TIER_SIGNATURE_REPEAT = 0.10F;

	public static String killTag(String bossId) {
		return "elsewhere_killed_" + bossId;
	}

	public static List<ItemStack> roll(ServerLevel level, ElsewhereBoss boss, ServerPlayer player, @Nullable String variant) {
		boolean firstKill = !player.entityTags().contains(killTag(boss.bossId()));
		List<ItemStack> loot = new ArrayList<>(boss.rollDrops(player, level.getRandom(), firstKill));
		if (variant != null) loot = new ArrayList<>(boss.rollVariantDrops(variant, player, level.getRandom(), firstKill, loot));
		if (firstKill || level.getRandom().nextFloat() < TIER_SIGNATURE_REPEAT) {
			ItemStack signature = StubFramework.signatureFor(boss.tier());
			if (!signature.isEmpty()) loot.add(signature);
		}
		player.addTag(killTag(boss.bossId()));
		return loot;
	}

	public static List<ItemStack> deliver(ServerLevel level, ElsewhereBoss boss, ServerPlayer player, @Nullable String variant) {
		List<ItemStack> loot = roll(level, boss, player, variant);
		// polish-loot-chest = No: loot drops on the ground at the boss like any mob's, anyone can pick it up
		dev.elsewhere.boss.kit.BossRewards.dropLoot(level, boss.fight() != null ? boss.fight().dropPoint() : boss.position(), loot);
		player.sendSystemMessage(Component.translatable("boss.elsewhere.loot", loot.size()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
		return loot;
	}
}
