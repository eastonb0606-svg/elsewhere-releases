package dev.elsewhere.boss_music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * One boss theme in the game: binds a {@link Conductor} (the timeline) to real sound instances. Parts are the theme events
 * {@code <theme>.intro}, {@code .loop}, {@code .end} and one {@code .<name>} per phase layer (P12; the surge is the layer named
 * {@code surge}) from sounds.json; a part the map has no data for is never asked for (no layer for a threshold -> phase
 * stinger; no end -> victory stinger), and a part whose event is missing from sounds.json falls back the same way.
 */
final class Theme {
	final Identifier id;
	private final Conductor conductor;
	private final java.util.List<Live> voices = new java.util.ArrayList<>();

	/** The sound event of a phase layer: {@code <theme>.<layer name>} (the surge is the layer named {@code surge}). */
	static Identifier layerOf(Identifier theme, Conductor.Layer layer) {
		return theme.withSuffix("." + layer.name());
	}

	Theme(Minecraft mc, Identifier id, BossMusicMap m) {
		this.id = id;
		Conductor.Plan plan = m.plans.get(id);
		boolean legacy = !BossMusicConfig.overhaul() || plan == null;
		if (plan == null) plan = Conductor.Plan.legacy(m.introTicks.get(id));
		final Conductor.Plan p = plan;
		conductor = new Conductor(plan, new Conductor.Voices() {
			@Override
			public Conductor.Voice make(Conductor.Part part, Conductor.Layer layer) {
				Identifier ev = switch (part) {
					case INTRO -> BossMusicMap.introOf(id);
					case LOOP -> BossMusicMap.loopOf(id);
					case LAYER -> layerOf(id, layer);
					case END -> id.withSuffix(".end");
				};
				if (mc.getSoundManager().getSoundEvent(ev) == null) { // no sounds.json event: the conductor falls back (stinger), never a silent part
					BossMusicService.LOGGER.warn("boss music: no sound event {}", ev);
					return null;
				}
				int len = switch (part) {
					case INTRO -> p.introTicks() + Conductor.HANDOFF_RAMP;
					case END -> p.endTicks();
					default -> -1;
				};
				Live v = new Live(mc, new FadingMusic(SoundEvent.createVariableRangeEvent(ev), part == Conductor.Part.LOOP || part == Conductor.Part.LAYER), len);
				voices.add(v);
				return v;
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

	/** The phase layer playing now ({@code null} in the intro or loop). For the self-test. */
	Conductor.Layer layerNow() {
		int i = conductor.layerNow();
		return i < 0 ? null : conductor.plan.layers().get(i);
	}

	Conductor.Plan plan() {
		return conductor.plan;
	}

	Conductor.State state() {
		return conductor.state();
	}

	/** How many of this theme's parts the sound engine is playing right now (at most 2: a crossfade). For the self-test. */
	int activeStreams(Minecraft mc) {
		int n = 0;
		for (Live v : voices) if (!v.killed && mc.getSoundManager().isActive(v.s)) n++;
		return n;
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
