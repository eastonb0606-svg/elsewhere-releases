package dev.elsewhere.ironpeak;

import dev.elsewhere.boss.kit.KitStore;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;

/**
 * new-summit-race: climb from the foothills (Y 400) to the Summit (Y 1300) against the clock. The clock starts when you
 * cross Y 400 going up and stops at Y 1300; dropping below Y 400 or leaving Ironpeak cancels the run. Finishing pays
 * dimension currency and XP only (never gear), at most once per 20 minutes per player (KitStore cooldown, on disk);
 * under 10 minutes pays double. Your best time is kept with the climber data.
 */
public final class SummitRace {
	private SummitRace() {}

	public static final int START_Y = 400, FINISH_Y = 1300, PAR_TICKS = 12000, REWARD_COOLDOWN = 24000;

	public static long startTick(ServerPlayer p) {
		return Ironpeak.data(p).getLongOr("race_start", -1L);
	}

	public static int best(ServerPlayer p) {
		return Ironpeak.data(p).getIntOr("race_best", 0);
	}

	/** Runs every second for each player (called from {@link Ironpeak#serverTick}). */
	public static void tick(ServerPlayer p) {
		CompoundTag d = Ironpeak.data(p);
		long now = p.level().getGameTime();
		boolean here = p.level().dimension() == Ironpeak.DIM;
		long start = d.getLongOr("race_start", -1L);
		if (!here || p.getY() < START_Y - 20) {
			if (start >= 0) {
				d.putLong("race_start", -1L);
				if (here) p.sendOverlayMessage(Component.literal("Summit Race: run abandoned.").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (start < 0) {
			if (p.getY() >= START_Y && p.getY() < FINISH_Y - 50 && !p.isCreative() && !p.isSpectator()) {
				d.putLong("race_start", now);
				p.sendOverlayMessage(Component.literal("Summit Race: the clock is running. Reach Y " + FINISH_Y + ".").withStyle(ChatFormatting.AQUA));
			}
			return;
		}
		if (p.getY() >= FINISH_Y) finish(p, now - start);
	}

	public static void finish(ServerPlayer p, long ticks) {
		CompoundTag d = Ironpeak.data(p);
		d.putLong("race_start", -1L);
		int secs = (int) (ticks / 20);
		boolean record = best(p) == 0 || secs < best(p);
		if (record) d.putInt("race_best", Math.max(1, secs));
		p.sendSystemMessage(Component.literal(String.format("Summit Race: %d:%02d%s", secs / 60, secs % 60, record ? " (your best)" : "")).withStyle(ChatFormatting.GOLD));
		MinecraftServer server = p.level().getServer();
		KitStore store = KitStore.of(server);
		String key = "summit_race_reward|" + p.getUUID();
		if (!store.cooldownReady(key)) return;
		store.cooldownStart(key, REWARD_COOLDOWN);
		boolean fast = ticks <= PAR_TICKS;
		ServerLevel level = (ServerLevel) p.level();
		dev.elsewhere.boss.kit.BossKit.give(p, new ItemStack(IronpeakItems.SKYIRON, fast ? 4 : 2));
		ExperienceOrb.award(level, p.position(), fast ? 60 : 30);
	}
}
