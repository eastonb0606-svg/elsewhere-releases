package dev.elsewhere.rivals.hrafnholt.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The Raven Standard (Hersir's Standard Plant): a vanilla armor stand carrying a banner, with its own 60-point health pool. Vanilla breaks a stand in two hits, so every
 * hit is cancelled here and subtracted from the pool instead; at 0 the stand is removed and the buffs it gave end. It is an entity, never a block.
 */
public final class HrafnholtStandards {
	private HrafnholtStandards() {}

	private record Standard(UUID boss, float[] hp) {}

	private static final Map<UUID, Standard> STANDS = new HashMap<>();
	private static boolean done;

	public static void init() {
		done = true;
	}

	/** Called first thing by the armor-stand mixin: true when {@code st} is a Raven Standard and the hit was taken from its own 60-point pool (vanilla then ignores it). */
	public static boolean onHit(ArmorStand st, ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!st.entityTags().contains(HrafnholtBoss.STANDARD_TAG)) return false;
		Standard s = STANDS.get(st.getUUID());
		if (s == null || amount <= 0F) return false;
		s.hp[0] -= amount;
		level.sendParticles(ParticleTypes.CRIT, st.getX(), st.getY() + 1.5, st.getZ(), 6, 0.3, 0.4, 0.3, 0.1);
		level.playSound(null, st.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1F, 0.8F);
		if (s.hp[0] <= 0F) {
			STANDS.remove(st.getUUID());
			if (level.getEntity(s.boss) instanceof HrafnholtBoss b) b.standardCut(true);
			level.playSound(null, st.blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.HOSTILE, 1F, 1F);
			st.discard();
		}
		return true;
	}

	static void register(ArmorStand st, HrafnholtBoss boss, float hp) {
		STANDS.put(st.getUUID(), new Standard(boss.getUUID(), new float[] {hp}));
	}

	static void forget(UUID id) {
		if (id != null) STANDS.remove(id);
	}

	/** Remaining health of a stand (tests). */
	public static float hp(UUID id) {
		Standard s = STANDS.get(id);
		return s == null ? -1F : s.hp[0];
	}
}
