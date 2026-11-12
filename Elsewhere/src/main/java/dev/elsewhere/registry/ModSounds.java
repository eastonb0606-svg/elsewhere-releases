package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Sound events. Their sounds.json entries layer re-pitched vanilla sound files, so nothing is copied into the jar. */
public final class ModSounds {
	public static final Holder.Reference<SoundEvent> RIFT_OPEN = register("rift.open");
	public static final Holder.Reference<SoundEvent> RIFT_TRAVEL = register("rift.travel");
	public static final Holder.Reference<SoundEvent> RIFT_HUM = register("rift.hum");
	public static final Holder.Reference<SoundEvent> TRIP_PULL = register("trip.pull");
	public static final Holder.Reference<SoundEvent> MOTHER_CREAK_ROAR = register("boss.mother_creak.roar");
	public static final Holder.Reference<SoundEvent> MOTHER_CREAK_STUN = register("boss.mother_creak.stun");
	public static final Holder.Reference<SoundEvent> SELENNE_OPEN = register("boss.selenne.open");
	public static final Holder.Reference<SoundEvent> SELENNE_CLOSE = register("boss.selenne.close");
	public static final Holder.Reference<SoundEvent> SELENNE_BEAM = register("boss.selenne.beam");
	public static final Holder.Reference<SoundEvent> BOSS_SEAL = register("boss.seal");

	private ModSounds() {
	}

	private static Holder.Reference<SoundEvent> register(final String name) {
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, Elsewhere.id(name), SoundEvent.createVariableRangeEvent(Elsewhere.id(name)));
	}

	public static void init() {
	}
}
