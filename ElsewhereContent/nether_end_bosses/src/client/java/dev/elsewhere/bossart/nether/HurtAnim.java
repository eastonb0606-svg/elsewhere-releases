package dev.elsewhere.bossart.nether;

import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 3.0 (P8): the hurt flinch for the boss art models. The renderer stores the time since the last hit (from the
 * client's own {@code hurtTime}, no sync) and the model plays its short HURT keyframe animation over it. Nothing past
 * 64 blocks, nothing while dying. Kill switch: {@code "bossAnimations3": false} in {@code config/elsewhere-client.json}.
 */
public final class HurtAnim {
	private HurtAnim() {}

	/** Render state with the hurt clock; art states extend this instead of {@link BossRenderState}. */
	public static class State extends BossRenderState {
		/** Ticks since the hit (partial), or -1 when not flinching. */
		public float hurtAge = -1F;
	}

	private static Boolean enabled;

	/** The same client key as the human-boss move animations ({@code bossAnimations3}, default on). */
	public static boolean enabled() {
		Boolean e = enabled;
		if (e == null) {
			boolean on = !"false".equals(System.getProperty("elsewhere.bossAnimations3"));
			try {
				Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
				if (on && Files.exists(p)) {
					com.google.gson.JsonElement j = com.google.gson.JsonParser.parseString(Files.readString(p));
					if (j.isJsonObject() && j.getAsJsonObject().has("bossAnimations3")) on = j.getAsJsonObject().get("bossAnimations3").getAsBoolean();
				}
			} catch (Exception ignored) {
			}
			enabled = e = on;
		}
		return e;
	}

	public static void extract(LivingEntity e, BossRenderState s, float partialTicks) {
		if (!(s instanceof State h)) return;
		h.hurtAge = -1F;
		if (!enabled() || e.hurtTime <= 0 || e.isDeadOrDying() || s.distanceToCameraSq > 64 * 64) return;
		h.hurtAge = Math.max(0F, 10 - e.hurtTime + partialTicks);
	}

	/** Plays the model's HURT animation (a non-looping 0.5 s definition) if the boss is flinching. */
	public static void apply(KeyframeAnimation hurt, BossRenderState s) {
		if (s instanceof State h && h.hurtAge >= 0F && s.deathTime <= 0F) hurt.apply((long) (h.hurtAge * 50F), 1.0F);
	}
}
