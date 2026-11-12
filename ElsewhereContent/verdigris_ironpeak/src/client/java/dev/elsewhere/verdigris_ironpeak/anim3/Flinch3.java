package dev.elsewhere.verdigris_ironpeak.anim3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.LivingEntity;

/**
 * 3.0 (P8): hurt flinch for this module's bosses. Their art models already carry idle, attack and death keyframes;
 * the hit reaction was missing. A hit rocks the whole boss back from its feet and squashes it a little, then it
 * settles with one small overshoot, eased over the client's own {@code hurtTime} (no sync, no packets), within 64
 * blocks, never while dying. Kill switch: {@code "bossAnimations3": false} in {@code config/elsewhere-client.json}.
 */
public final class Flinch3 {
	private Flinch3() {}

	private static Boolean enabled;

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

	/** True for the bosses this module animates. */
	public static boolean isBoss(LivingEntity e) {
		return e instanceof dev.elsewhere.stub.verdigris_ironpeak.StubBoss || e instanceof dev.elsewhere.boss.hrolf.GrimhornEntity;
	}

	/** Flinch clock: ticks since the hit (0..10), or -1. */
	public static float clock(LivingEntity e, float partialTicks, double distSq) {
		if (e.hurtTime <= 0 || e.isDeadOrDying() || distSq > 64 * 64 || !enabled() || !isBoss(e)) return -1F;
		return Math.max(0F, 10 - e.hurtTime + partialTicks);
	}

	/** Applied at the end of {@code setupRotations} (unflipped space, origin at the feet). */
	public static void apply(PoseStack pose, float clock) {
		if (clock < 0F) return;
		float t = clock / 10F;
		// snap back in 1.5 ticks, swing slightly past rest, settle by 10 ticks
		float w = t < 0.15F ? t / 0.15F : (float) (Math.cos((t - 0.15F) / 0.85F * Math.PI * 1.5) * Math.pow(1F - (t - 0.15F) / 0.85F, 1.6));
		// +X rotation tips the top toward +Z, which is behind the boss here (models face -Z in this space, as vanilla's
		// riptide spin shows), so the boss recoils away from the hit (3.0 s-p8: was -7.5, a forward bow)
		pose.rotateDegrees(Axis.XP, 7.5F * w);
		float sq = Math.max(0F, w);
		pose.scale(1F + 0.035F * sq, 1F - 0.06F * sq, 1F + 0.035F * sq);
	}
}
