package dev.elsewhere.duelist.boss;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A boss's own telegraph look: a particle, a colour and a sound, drawn in a {@link Shape}. There is no default style:
 * every {@link DuelistProfile} must give one. The colour tints a dust accent that is always drawn with the particle,
 * so two bosses that share a vanilla particle still read differently.
 */
public record TelegraphStyle(ParticleOptions particle, int colour, SoundEvent sound, float pitch, Shape shape) {
	public enum Shape {
		/** Above the head. */
		HALO,
		/** A ring at the feet. */
		RING,
		/** A rising spiral around the body. */
		SPIRAL,
		/** A column from the feet to above the head. */
		COLUMN
	}

	public TelegraphStyle withParticle(ParticleOptions p) {
		return new TelegraphStyle(p, colour, sound, pitch, shape);
	}

	public TelegraphStyle withShape(Shape s) {
		return new TelegraphStyle(particle, colour, sound, pitch, s);
	}

	public TelegraphStyle withPitch(float p) {
		return new TelegraphStyle(particle, colour, sound, p, shape);
	}

	public Telegraph.Cue cue(boolean crouch, boolean raised) {
		return new Telegraph.Cue(this, crouch, raised);
	}

	/**
	 * Builds a style from data names: a vanilla particle id ("flame", "minecraft:end_rod"; "dust" and "dust_color_transition"
	 * take the colour) and a sound event id. There is no fallback look: an unknown particle throws.
	 */
	public static TelegraphStyle of(String particleId, int colour, String soundId, Shape shape, float pitch) {
		return new TelegraphStyle(particleOf(particleId, colour), colour, soundOf(soundId), pitch, shape);
	}

	/**
	 * Reads the {@code telegraph} object of a {@code boss_identity} card: {@code particle}, {@code colour} ("#RRGGBB") and
	 * {@code sound} (an event id, or {@code {"event": id, "pitch": n}}) are all required; {@code shape} is optional data a lane
	 * may add, otherwise the caller's {@code shape} is used. Throws if anything is missing: no default look.
	 */
	public static TelegraphStyle fromJson(JsonObject o, Shape shape) {
		for (String k : new String[] {"particle", "colour", "sound"}) if (!o.has(k)) throw new IllegalArgumentException("telegraph needs particle, colour and sound (missing " + k + ")");
		String sound;
		float pitch = 1.0F;
		if (o.get("sound").isJsonObject()) {
			JsonObject so = o.getAsJsonObject("sound");
			sound = so.get("event").getAsString();
			pitch = so.get("pitch").getAsFloat();
		} else sound = o.get("sound").getAsString();
		if (o.has("shape")) shape = Shape.valueOf(o.get("shape").getAsString().toUpperCase(java.util.Locale.ROOT));
		return of(o.get("particle").getAsString(), parseColour(o.get("colour").getAsString()), sound, shape, pitch);
	}

	/** "#RRGGBB", "0xRRGGBB" or a decimal number. */
	public static int parseColour(String s) {
		String t = s.trim();
		if (t.startsWith("#")) return Integer.parseInt(t.substring(1), 16) & 0xFFFFFF;
		if (t.startsWith("0x") || t.startsWith("0X")) return Integer.parseInt(t.substring(2), 16) & 0xFFFFFF;
		return Integer.parseInt(t) & 0xFFFFFF;
	}

	/** The particle for a data name, or an exception: a boss never falls back to somebody else's look. */
	public static ParticleOptions particleOf(String id, int colour) {
		Identifier rid = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (rid != null && rid.getNamespace().equals("minecraft")) {
			if (rid.getPath().equals("dust")) return new DustParticleOptions(colour, 1.2F);
			if (rid.getPath().equals("dust_color_transition")) return new DustColorTransitionOptions(colour, 0xFFFFFF, 1.2F);
		}
		if (rid != null && BuiltInRegistries.PARTICLE_TYPE.containsKey(rid)) {
			ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(rid);
			if (t instanceof SimpleParticleType sp) return sp;
		}
		throw new IllegalArgumentException("telegraph particle '" + id + "' is not a simple registered particle (build the TelegraphStyle in code for a custom one)");
	}

	private static SoundEvent soundOf(String id) {
		Identifier rid = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (rid == null) throw new IllegalArgumentException("telegraph sound '" + id + "' is not a valid id");
		if (BuiltInRegistries.SOUND_EVENT.containsKey(rid)) return BuiltInRegistries.SOUND_EVENT.getValue(rid);
		return SoundEvent.createVariableRangeEvent(rid);
	}

	/** Draws one burst of this look around a boss. {@code strength} 1 for an ordinary wind-up, more for a heavy one. */
	public void emit(ServerLevel level, Vec3 base, double height, int strength, long tick) {
		DustParticleOptions dust = new DustParticleOptions(colour, 1.0F);
		switch (shape) {
			case HALO -> {
				for (int i = 0; i < 4 * strength; i++) {
					double a = tick * 0.5 + i * Math.PI * 2 / (4 * strength);
					double x = base.x + Math.cos(a) * 0.45, z = base.z + Math.sin(a) * 0.45;
					level.sendParticles(i % 2 == 0 ? particle : dust, x, base.y + height + 0.25, z, 1, 0, 0, 0, 0.0);
				}
			}
			case RING -> {
				int n = 8 * strength;
				for (int i = 0; i < n; i++) {
					double a = i * Math.PI * 2 / n + tick * 0.15;
					level.sendParticles(i % 2 == 0 ? particle : dust, base.x + Math.cos(a) * 1.1, base.y + 0.1, base.z + Math.sin(a) * 1.1, 1, 0, 0, 0, 0.0);
				}
			}
			case SPIRAL -> {
				for (int i = 0; i < 3 * strength; i++) {
					double a = tick * 0.6 + i * 2.1;
					double y = base.y + ((tick * 0.12 + i * 0.4) % height);
					level.sendParticles(i % 2 == 0 ? particle : dust, base.x + Math.cos(a) * 0.7, y, base.z + Math.sin(a) * 0.7, 1, 0, 0, 0, 0.0);
				}
			}
			case COLUMN -> {
				for (int i = 0; i < 4 * strength; i++) {
					double y = base.y + (i + 0.5) * (height + 0.5) / (4 * strength);
					level.sendParticles(i % 2 == 0 ? particle : dust, base.x, y, base.z, 1, 0.15, 0.0, 0.15, 0.0);
				}
			}
		}
	}
}
