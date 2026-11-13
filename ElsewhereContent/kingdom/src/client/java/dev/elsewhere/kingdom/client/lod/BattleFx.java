package dev.elsewhere.kingdom.client.lod;

import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.armies.lod.RenderLod;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Battle particle and sound limits (ARMIES.md 3.7), client only, active only while a battle is around: at least
 * {@link RenderLod#BATTLE_MIN_SOLDIERS} kingdom soldiers tracked by this client (counted on entity load/unload, never by scanning).
 * Outside battles vanilla behaviour is untouched. Within {@link RenderLod#OWN_RANGE} blocks of the camera nothing is ever limited (the
 * player's own fight).
 * <ul>
 * <li>Particles ({@code ParticleEngineLodMixin}): crit, enchanted-hit, sweep and damage-indicator particles only within 24 blocks; all
 * combat particles (those plus death poofs and sprint dust) share a budget of 400 per second. Vanilla already drops particles past 32
 * blocks, so no death poofs beyond 32.</li>
 * <li>Sounds ({@code SoundEngineLodMixin}): at most 12 battle sounds (hits, shield blocks, bow and crossbow shots, hurt and death)
 * playing at once; past 32 blocks single hits are replaced by one battle din ({@code elsewhere:army.battle_din}, vanilla clash sounds
 * re-pitched in sounds.json) at the centre of the far fighting, at most every 350 ms.</li>
 * </ul>
 */
public final class BattleFx {
	private BattleFx() {}

	public static final Identifier DIN_ID = KingdomContent.id("army.battle_din");
	/** Sound events that count as battle noise. */
	static final Set<Identifier> BATTLE_SOUNDS = Set.of(
		Identifier.withDefaultNamespace("entity.player.attack.strong"), Identifier.withDefaultNamespace("entity.player.attack.crit"),
		Identifier.withDefaultNamespace("entity.player.attack.knockback"), Identifier.withDefaultNamespace("entity.player.attack.sweep"),
		Identifier.withDefaultNamespace("entity.player.attack.weak"), Identifier.withDefaultNamespace("entity.player.attack.nodamage"),
		Identifier.withDefaultNamespace("entity.player.hurt"), Identifier.withDefaultNamespace("entity.player.death"),
		Identifier.withDefaultNamespace("item.shield.block"), Identifier.withDefaultNamespace("item.shield.break"),
		Identifier.withDefaultNamespace("entity.arrow.shoot"), Identifier.withDefaultNamespace("entity.arrow.hit"),
		Identifier.withDefaultNamespace("item.crossbow.shoot"), Identifier.withDefaultNamespace("item.crossbow.hit"));

	private static final IntOpenHashSet SOLDIERS = new IntOpenHashSet();
	private static ClientLevel level;
	static final RenderLod.Window PARTICLES = new RenderLod.Window(RenderLod.PARTICLE_BUDGET, 20, 50);
	static final RenderLod.Slots<SoundInstance> SOUNDS = new RenderLod.Slots<>(RenderLod.SOUND_SLOTS);
	static final RenderLod.Din DIN_ACC = new RenderLod.Din();
	private static final RandomSource RANDOM = RandomSource.create();
	public static long particlesRefusedRange, soundsRefusedSlots;

	/** Entity load on the client: count kingdom soldiers (a new level clears the count). */
	static void onLoad(Entity e, ClientLevel lv) {
		if (lv != level) {
			level = lv;
			SOLDIERS.clear();
			SOUNDS.clear();
			SoldierLod.reset();
		}
		if (e instanceof KingdomSoldier) SOLDIERS.add(e.getId());
	}

	static void onUnload(Entity e, ClientLevel lv) {
		if (lv == level && e instanceof KingdomSoldier) SOLDIERS.remove(e.getId());
	}

	public static int trackedSoldiers() {
		return SOLDIERS.size();
	}

	public static boolean battle() {
		return RenderLod.enabled() && SOLDIERS.size() >= RenderLod.BATTLE_MIN_SOLDIERS;
	}

	/** 0 = not a combat particle, 1 = hit particle (24-block rule + budget), 2 = other combat particle (budget only). */
	static int classify(ParticleType<?> t) {
		if (t == ParticleTypes.CRIT || t == ParticleTypes.ENCHANTED_HIT || t == ParticleTypes.SWEEP_ATTACK || t == ParticleTypes.DAMAGE_INDICATOR) return 1;
		if (t == ParticleTypes.POOF || t == ParticleTypes.BLOCK) return 2;
		return 0;
	}

	/** From {@code ParticleEngine.createParticle}: false drops the particle. */
	public static boolean allowParticle(ParticleOptions p, double x, double y, double z) {
		if (!battle()) return true;
		int cls = classify(p.getType());
		if (cls == 0) return true;
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double d2 = cam.distanceToSqr(x, y, z);
		if (d2 <= RenderLod.OWN_RANGE * RenderLod.OWN_RANGE) return true;
		if (cls == 1 && d2 > (double) RenderLod.HIT_PARTICLE_RANGE * RenderLod.HIT_PARTICLE_RANGE) {
			particlesRefusedRange++;
			return false;
		}
		return PARTICLES.tryAcquire(Util.getMillis());
	}

	/** From {@code SoundEngine.play}: false means "not started" (the din may have been played instead). */
	public static boolean allowSound(SoundEngine engine, SoundInstance s) {
		if (!battle() || s.isRelative() || s.getSource() == SoundSource.MASTER || s.getSource() == SoundSource.MUSIC) return true;
		Identifier id = s.getIdentifier();
		if (id.equals(DIN_ID) && s.getAttenuation() != SoundInstance.Attenuation.NONE) {
			// the server's distant-battle din (our own is unattenuated): inside the din range the real hits are heard instead
			Vec3 c0 = Minecraft.getInstance().gameRenderer.mainCamera().position();
			return c0.distanceToSqr(s.getX(), s.getY(), s.getZ()) > (double) RenderLod.DIN_RANGE * RenderLod.DIN_RANGE;
		}
		if (!BATTLE_SOUNDS.contains(id)) return true;
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double d2 = cam.distanceToSqr(s.getX(), s.getY(), s.getZ());
		if (d2 <= RenderLod.OWN_RANGE * RenderLod.OWN_RANGE) return true;
		if (d2 > (double) RenderLod.DIN_RANGE * RenderLod.DIN_RANGE) {
			if (DIN_ACC.feed(s.getX(), s.getY(), s.getZ(), Util.getMillis())) {
				// unattenuated but positioned (it comes from the battle's direction); loudness by activity and by distance to the fighting
				double dc = Math.sqrt(cam.distanceToSqr(DIN_ACC.x, DIN_ACC.y, DIN_ACC.z));
				float vol = DIN_ACC.takeVolume() * (float) Math.max(0.2, Math.min(1.0, 1.0 - (dc - RenderLod.DIN_RANGE) / 160.0)) * 0.7F;
				engine.play(new SimpleSoundInstance(DIN_ID, SoundSource.HOSTILE, vol, 0.9F + RANDOM.nextFloat() * 0.2F, RANDOM, false, 0,
					SoundInstance.Attenuation.NONE, DIN_ACC.x, DIN_ACC.y, DIN_ACC.z, false));
			}
			return false;
		}
		if (SOUNDS.tryTake(s, engine::isActive)) return true;
		soundsRefusedSlots++;
		return false;
	}

	/** Battle sounds playing right now (for /kingdomlod). */
	public static int soundsPlaying() {
		var sm = Minecraft.getInstance().getSoundManager();
		return SOUNDS.busy(sm::isActive);
	}

	public static int particlesThisSecond() {
		return PARTICLES.inWindow(Util.getMillis());
	}
}
