package dev.elsewhere.stub.nether_end_bosses;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/** Stub of the core's boss music: an active boss within 64 blocks overrides the situational music with its track. */
public final class BossMusic {
	private static final Map<Holder<SoundEvent>, Music> CACHE = new HashMap<>();

	private BossMusic() {}

	public static @Nullable Music current(Minecraft mc) {
		if (mc.player == null || mc.level == null) return null;
		ElsewhereBoss best = null;
		double bestD = 64 * 64;
		for (ElsewhereBoss boss : mc.level.getEntitiesOfClass(ElsewhereBoss.class, mc.player.getBoundingBox().inflate(64))) {
			if (!boss.isAlive() || boss.isDormant() || boss.primary() != boss) continue;
			double d = boss.distanceToSqr(mc.player);
			if (d < bestD) {
				bestD = d;
				best = boss;
			}
		}
		if (best == null) return null;
		return CACHE.computeIfAbsent(best.music(), h -> new Music(h, 0, 0, true));
	}
}
