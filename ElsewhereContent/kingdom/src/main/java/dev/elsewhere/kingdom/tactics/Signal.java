package dev.elsewhere.kingdom.tactics;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** A visible and audible announcement of a plan change (FIELD-TACTICS 1.6). Receivers delay their reaction by {@link #delay()} ticks. */
public interface Signal {
	Types.SignalKind kind();

	/** Lang key of the shout line (per faction: {@code tactics.<faction>.<signal>.<n>}); may be empty. */
	default String shoutKey() {
		return "";
	}

	SoundEvent sound();

	@Nullable
	default ParticleOptions particle() {
		return null;
	}

	default int range() {
		return 48;
	}

	/** Ticks (8-20) receivers wait before acting on the order that comes with this signal. */
	default int delay() {
		return 12;
	}

	/** The standard signals. Horn variants use the goat horn sounds. */
	record Std(Types.SignalKind kind, SoundEvent sound, @Nullable ParticleOptions particle, int range, int delay, String shoutKey) implements Signal {}

	Signal HORN_LONG = new Std(Types.SignalKind.HORN_LONG, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), ParticleTypes.NOTE, 48, 14, "");
	Signal HORN_SHORT2 = new Std(Types.SignalKind.HORN_SHORT2, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), ParticleTypes.NOTE, 48, 10, "");
	Signal HORN_SHORT3 = new Std(Types.SignalKind.HORN_SHORT3, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), ParticleTypes.NOTE, 64, 10, "");
	Signal BANNER_RAISE = new Std(Types.SignalKind.BANNER_RAISE, SoundEvents.WOOL_PLACE, ParticleTypes.FIREWORK, 40, 12, "");
	Signal FOCUS_MARK = new Std(Types.SignalKind.FOCUS_MARK, SoundEvents.AMETHYST_BLOCK_CHIME, ParticleTypes.CRIT, 32, 8, "");
}
