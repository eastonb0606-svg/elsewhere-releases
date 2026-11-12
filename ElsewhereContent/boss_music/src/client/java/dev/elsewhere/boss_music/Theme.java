package dev.elsewhere.boss_music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * One boss theme in the game: binds a {@link Conductor} (the timeline) to real sound instances. Parts are the theme events
 * {@code <theme>.intro}, {@code .loop}, {@code .surge} and {@code .end} from sounds.json; a part the map has no data for is
 * never asked for (no surge -> phase stingers; no end -> victory stinger).
 */
final class Theme {
	final Identifier id;
	private final Conductor conductor;

	Theme(Minecraft mc, Identifier id, BossMusicMap m) {
		this.id = id;
		Conductor.Plan plan = m.plans.get(id);
		boolean legacy = !BossMusicConfig.overhaul() || plan == null;
		if (plan == null) plan = Conductor.Plan.legacy(m.introTicks.get(id));
		final Conductor.Plan p = plan;
		conductor = new Conductor(plan, new Conductor.Voices() {
			@Override
			public Conductor.Voice make(Conductor.Part part) {
				Identifier ev = switch (part) {
					case INTRO -> BossMusicMap.introOf(id);
					case LOOP -> BossMusicMap.loopOf(id);
					case SURGE -> id.withSuffix(".surge");
					case END -> id.withSuffix(".end");
				};
				int len = switch (part) {
					case INTRO -> p.introTicks() + Conductor.HANDOFF_RAMP;
					case END -> p.endTicks();
					default -> -1;
				};
				return new Live(mc, new FadingMusic(SoundEvent.createVariableRangeEvent(ev), part == Conductor.Part.LOOP || part == Conductor.Part.SURGE), len);
			}

			@Override
			public void sting(String which) {
				Identifier s = m.stingers.get(which);
				if (s != null) mc.getSoundManager().play(SimpleSoundInstance.forMusic(SoundEvent.createVariableRangeEvent(s)));
			}
		}, legacy, m.fleeTicks, m.defeatTicks, m.fadeIn);
	}

	/** Advance the timeline one tick (only while the game is not paused, like the sound engine). */
	void tick() {
		conductor.tick();
	}

	void phase(int index) {
		conductor.phase(index);
	}

	void victory() {
		conductor.victory();
	}

	void flee() {
		conductor.flee();
	}

	void defeat() {
		conductor.defeat();
	}

	boolean isStopped() {
		return conductor.done();
	}

	/** On its way out (ending, fading): a new theme waits for it instead of overlapping. */
	boolean fadingOut() {
		return conductor.leaving();
	}

	void stop(Minecraft mc) {
		conductor.stopNow();
	}

	/** A real sound instance as a conductor voice. A one-shot counts as finished once the engine dropped it after playing. */
	private static final class Live implements Conductor.Voice {
		private final Minecraft mc;
		private final FadingMusic s;
		private final int lenTicks;
		private int age = -1;
		private boolean seen, killed;

		Live(Minecraft mc, FadingMusic s, int lenTicks) {
			this.mc = mc;
			this.s = s;
			this.lenTicks = lenTicks;
		}

		@Override
		public void play() {
			age = 0;
			mc.getSoundManager().play(s);
		}

		@Override
		public void gain(float g) {
			s.gain(g);
		}

		@Override
		public void kill() {
			killed = true;
			s.kill();
			mc.getSoundManager().stop(s);
		}

		@Override
		public boolean finished() {
			if (killed) return true;
			if (age < 0) return false;
			age++;
			boolean active = mc.getSoundManager().isActive(s);
			if (active) seen = true;
			if (lenTicks < 0) return seen && !active && age > 40; // a loop only ends if the engine dropped it
			return (seen && !active) || age > lenTicks + 60;
		}
	}
}
