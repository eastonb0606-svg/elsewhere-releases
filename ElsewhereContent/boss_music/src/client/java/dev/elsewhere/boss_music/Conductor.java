package dev.elsewhere.boss_music;

import java.util.ArrayList;
import java.util.List;

/**
 * The timeline of one boss theme (3.0). Pure logic with no Minecraft types, so {@link SelfTest} can simulate every theme.
 * Units are ticks (20 per second); the clock only advances while the game (and so the sound engine) is not paused.
 * <ul>
 * <li><b>Intro</b>: the track's own opening, at full level from its first sample ({@code native}: the track swells in by
 * itself) or on a 2-tick anti-click ramp ({@code cold}: it opens on a hit). No generic fade.</li>
 * <li><b>Loop</b>: starts one tick before the intro's end on an 8-tick ramp over the intro's baked 0.4 s tail.</li>
 * <li><b>Phase layers</b> (P12 phase stems, {@code layers} in {@code boss_music.json}): each health threshold that has a
 * layer crossfades (equal power, one beat long, starting on the next beat of the playing section) into that layer, a
 * seamless loop cut from the same track (the {@code surge} is the threshold-0 layer). Never more than two streams: a swap
 * or the ending waits for a running crossfade to finish, and a newer threshold replaces a swap that has not started yet.
 * Thresholds without a layer play the phase stinger on the next beat.</li>
 * <li><b>Victory</b>: on the next free beat the body fades out over one beat while the track's own final cadence
 * ({@code end}) comes in; the cadence always plays to its natural end. A theme without one plays the victory stinger and
 * fades.</li>
 * <li><b>Flee</b> (out of range, reset, another boss) fades over {@code fleeTicks} from the next beat; <b>defeat</b> (player
 * died) fades slowly over {@code defeatTicks} at once. Nothing is cut while audible and no part plays twice.</li>
 * </ul>
 */
final class Conductor {
	enum Part { INTRO, LOOP, LAYER, END }

	enum State { INTRO, LOOP, LAYER, ENDING, FADING, DONE }

	/** One playable part. {@link #finished()} is true once a one-shot part has played to its end (or was killed). */
	interface Voice {
		void play();

		void gain(float g);

		void kill();

		boolean finished();
	}

	interface Voices {
		/** A voice for a part ({@code layer} is set for {@link Part#LAYER} only); null when the part cannot be played. */
		Voice make(Part p, Layer layer);

		void sting(String which);
	}

	/**
	 * One phase stem: a seamless loop of the same track ({@code <theme>.<name>} in sounds.json) that the theme crossfades into,
	 * on the beat, when boss health crosses threshold {@code phase} (0 = the first entry of {@code phase_thresholds}).
	 */
	record Layer(int phase, String name, float loopTicks, float phaseTicks) {}

	/** Timing of one theme, from {@code boss_music.json} {@code themes}. */
	record Plan(int introTicks, boolean cold, float beatTicks, float loopTicks, float surgeTicks, int endTicks, float loopPhase, float surgePhase,
		List<Layer> layers) {
		Plan {
			layers = List.copyOf(layers);
		}

		/** A plan without an explicit {@code layers} list: the surge (when there is one) is the threshold-0 layer. */
		Plan(int introTicks, boolean cold, float beatTicks, float loopTicks, float surgeTicks, int endTicks, float loopPhase, float surgePhase) {
			this(introTicks, cold, beatTicks, loopTicks, surgeTicks, endTicks, loopPhase, surgePhase,
				surgeTicks > 20 ? List.of(new Layer(0, "surge", surgeTicks, surgePhase)) : List.of());
		}

		/** Index in {@link #layers} of the layer for threshold {@code phase}, or -1. */
		int layerIndex(int phase) {
			for (int i = 0; i < layers.size(); i++) if (layers.get(i).phase() == phase) return i;
			return -1;
		}

		boolean hasLayer(int phase) {
			return layerIndex(phase) >= 0;
		}

		boolean hasEnd() {
			return endTicks > 0;
		}

		/** Legacy plan (no 3.0 data): intro then loop, stingers, plain fades. */
		static Plan legacy(Integer introTicks) {
			return new Plan(introTicks == null ? 0 : introTicks, false, 0, 0, 0, 0, 0, 0, List.of());
		}
	}

	private enum Curve { LIN, IN, OUT }

	private static final class Env {
		float from, to;
		int t0, t1;
		Curve curve = Curve.LIN;
		boolean killAtEnd;
	}

	static final int HANDOFF_RAMP = 8;
	/** Voice slots: intro, loop, ending, then one per layer. */
	private static final int S_INTRO = 0, S_LOOP = 1, S_END = 2, S_LAYER = 3;

	final Plan plan;
	private final Voices voices;
	private final boolean legacy;
	private final int fleeTicks, defeatTicks, legacyFadeIn;
	private final Voice[] live;
	private final Env[] env;
	private final int[] started;
	private final List<long[]> due = new ArrayList<>(); // {tick, action, layer}
	private State state = State.INTRO;
	private int t;
	private int origin; // tick at which the current section's sample 0 plays
	private float period; // length of the current looping section (0 while in the intro)
	private float phase; // ticks from the section's sample 0 to its first beat
	private boolean endQueued, fadeQueued;
	private int layerNow = -1; // index of the layer playing now (-1: intro or loop)
	private int busyUntil; // a crossfade runs until this tick: no other swap or ending starts before it
	private final boolean[] phaseDone = new boolean[8];

	private static final long A_LAYER = 1, A_END = 2, A_FLEE = 3, A_STING_PHASE = 4;

	Conductor(Plan plan, Voices voices, boolean legacy, int fleeTicks, int defeatTicks, int legacyFadeIn) {
		this.plan = plan;
		this.voices = voices;
		this.legacy = legacy || plan.beatTicks() <= 0;
		this.fleeTicks = Math.max(1, fleeTicks);
		this.defeatTicks = Math.max(1, defeatTicks);
		this.legacyFadeIn = Math.max(1, legacyFadeIn);
		int slots = S_LAYER + (this.legacy ? 0 : plan.layers().size());
		live = new Voice[slots];
		env = new Env[slots];
		started = new int[slots];
		java.util.Arrays.fill(started, -1);
		if (plan.introTicks() <= 0) {
			startPart(S_LOOP, 0, legacyFadeIn, Curve.LIN);
			state = State.LOOP;
			origin = 0;
			period = plan.loopTicks();
			phase = plan.loopPhase();
		} else {
			int ramp = this.legacy ? legacyFadeIn : plan.cold() ? 2 : 0;
			startPart(S_INTRO, 0, ramp, Curve.LIN);
			origin = plan.introTicks(); // the loop's beat grid, extended back over the intro
			phase = plan.loopPhase();
		}
	}

	State state() {
		return state;
	}

	int clock() {
		return t;
	}

	boolean done() {
		return state == State.DONE;
	}

	/** True once the theme is on its way out (ending, fading or done): a new fight may queue behind it. */
	boolean leaving() {
		return state == State.ENDING || state == State.FADING || state == State.DONE || fadeQueued || endQueued;
	}

	/** Index of the phase layer playing now, or -1 (intro or loop). */
	int layerNow() {
		return layerNow;
	}

	/** Tick at which layer {@code i} started, or -1. */
	int layerStarted(int i) {
		return i < 0 || S_LAYER + i >= started.length ? -1 : started[S_LAYER + i];
	}

	/** Parts alive now (playing or fading). */
	int liveCount() {
		int n = 0;
		for (Voice v : live) if (v != null) n++;
		return n;
	}

	// ---- events -------------------------------------------------------------------------------------------------------

	/** Health crossed phase threshold {@code index} (0 = first). Each index acts once. */
	void phase(int index) {
		if (index < 0 || index >= phaseDone.length || phaseDone[index] || leaving()) return;
		phaseDone[index] = true;
		if (legacy) {
			voices.sting("phase");
			return;
		}
		int li = plan.layerIndex(index);
		if (li >= 0 && started[S_LAYER + li] < 0) {
			due.removeIf(d -> d[1] == A_LAYER); // a newer threshold replaces a swap that has not started: never two swaps back to back
			due.add(new long[] {nextBeat(Math.max(t, busyUntil)), A_LAYER, li});
		} else {
			due.add(new long[] {nextBeat(t), A_STING_PHASE, -1});
		}
	}

	void victory() {
		if (leaving()) return;
		if (legacy || !plan.hasEnd()) {
			voices.sting("victory");
			fadeAll(Math.min(30, fleeTicks), Curve.LIN);
			state = State.FADING;
			due.clear();
			return;
		}
		endQueued = true;
		due.removeIf(d -> d[1] == A_LAYER || d[1] == A_STING_PHASE); // the fight is over: no swap or stinger under the ending
		due.add(new long[] {nextBeat(Math.max(t, busyUntil)), A_END, -1});
	}

	/** Out of range, boss reset or despawned, or another boss takes over. */
	void flee() {
		if (leaving()) return;
		fadeQueued = true;
		if (legacy) {
			fadeAll(fleeTicks, Curve.LIN);
			state = State.FADING;
			return;
		}
		due.add(new long[] {nextBeat(t), A_FLEE, -1});
	}

	/** The player died: a slow fade, starting now (once). An ending cadence already playing is left to finish. */
	void defeat() {
		if (state == State.DONE || state == State.FADING || state == State.ENDING) return; // an ending cadence finishes
		due.clear();
		endQueued = false;
		fadeQueued = true;
		fadeAll(defeatTicks, Curve.LIN);
		state = State.FADING;
	}

	/** Disconnect or toggle off: everything stops now (the sound engine is being torn down anyway). */
	void stopNow() {
		for (int s = 0; s < live.length; s++) {
			if (live[s] != null) live[s].kill();
			live[s] = null;
			env[s] = null;
		}
		due.clear();
		state = State.DONE;
	}

	// ---- clock ------------------------------------------------------------------------------------------------------

	void tick() {
		if (state == State.DONE) return;
		t++;
		if (state == State.INTRO && started[S_LOOP] < 0 && layerNow < 0 && plan.introTicks() > 0 && t >= plan.introTicks() - 1 && !leaving()) {
			startPart(S_LOOP, t, HANDOFF_RAMP, Curve.LIN);
			origin = plan.introTicks();
			period = plan.loopTicks();
			phase = plan.loopPhase();
			state = State.LOOP;
		}
		for (int i = 0; i < due.size(); i++) {
			long[] d = due.get(i);
			if (d[0] > t) continue;
			due.remove(i--);
			run((int) d[1], (int) d[2]);
		}
		boolean any = false;
		for (int s = 0; s < live.length; s++) {
			Voice v = live[s];
			if (v == null) continue;
			Env e = env[s];
			if (e != null) {
				v.gain(gainAt(e, t));
				if (t >= e.t1 && e.killAtEnd) {
					v.kill();
					live[s] = null;
					env[s] = null;
					continue;
				}
			}
			if (v.finished()) {
				live[s] = null;
				env[s] = null;
				continue;
			}
			any = true;
		}
		if (!any && (state == State.FADING || state == State.ENDING)) state = State.DONE;
		if (!any && due.isEmpty() && state != State.DONE && t > 2) state = State.DONE; // every part ended by itself
	}

	private void run(int action, int arg) {
		if (action == A_STING_PHASE) {
			if (!leaving()) voices.sting("phase");
			return;
		}
		int x = xfadeTicks();
		if (action == A_LAYER) {
			if (leaving() || arg < 0 || S_LAYER + arg >= started.length || started[S_LAYER + arg] >= 0) return;
			if (t < busyUntil) { // a crossfade is still running: take the next free beat
				due.add(new long[] {nextBeat(busyUntil), A_LAYER, arg});
				return;
			}
			Layer l = plan.layers().get(arg);
			if (!startPart(S_LAYER + arg, t, x, Curve.IN)) {
				voices.sting("phase"); // the layer's file is missing: the threshold still gets its cue
				return;
			}
			fadeBody(x, Curve.OUT, S_LAYER + arg);
			origin = t;
			period = l.loopTicks();
			phase = l.phaseTicks();
			state = State.LAYER;
			layerNow = arg;
			busyUntil = t + x;
		} else if (action == A_END) {
			if (state == State.FADING || state == State.DONE || started[S_END] >= 0) return;
			if (t < busyUntil) {
				due.add(new long[] {nextBeat(busyUntil), A_END, -1});
				return;
			}
			if (!startPart(S_END, t, 2, Curve.LIN)) { // no ending file: stinger and a short fade
				voices.sting("victory");
				fadeAll(Math.min(30, fleeTicks), Curve.LIN);
				state = State.FADING;
				due.clear();
				return;
			}
			fadeBody(x, Curve.OUT, S_END);
			state = State.ENDING;
			due.removeIf(d -> d[1] == A_LAYER || d[1] == A_STING_PHASE);
		} else if (action == A_FLEE) {
			if (state == State.FADING || state == State.ENDING || state == State.DONE) return;
			fadeAll(fleeTicks, Curve.LIN);
			due.clear();
			state = State.FADING;
		}
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** Ticks of a beat crossfade: one beat, 4..20 ticks. */
	int xfadeTicks() {
		return Math.max(4, Math.min(20, Math.round(plan.beatTicks())));
	}

	/** The first tick at or after {@code from} that falls on a beat of the section playing now (beats counted from its sample 0). */
	int nextBeat(int from) {
		float b = plan.beatTicks();
		if (b <= 0) return from;
		float p = from - origin;
		if (period > 0) p = ((p % period) + period) % period;
		else p = ((p % b) + b) % b - b; // in the intro: the loop's grid extended backwards
		float k = (float) Math.ceil((p - phase) / b - 1e-4);
		float nb = phase + k * b;
		if (period > 0 && nb > period - 0.5F) nb = period + phase; // wrapped: the first beat of the next pass
		return from + Math.max(0, Math.round(nb - p));
	}

	private boolean startPart(int slot, int at, int ramp, Curve c) {
		if (slot >= started.length || started[slot] >= 0) return false; // never twice
		Part p = slot == S_INTRO ? Part.INTRO : slot == S_LOOP ? Part.LOOP : slot == S_END ? Part.END : Part.LAYER;
		Voice v = voices.make(p, p == Part.LAYER ? plan.layers().get(slot - S_LAYER) : null);
		if (v == null) return false;
		started[slot] = at;
		Env e = new Env();
		e.from = ramp > 0 ? 0F : 1F;
		e.to = 1F;
		e.t0 = at;
		e.t1 = at + ramp;
		e.curve = c;
		env[slot] = e;
		live[slot] = v;
		v.gain(ramp > 0 ? 0.001F : 1F);
		v.play();
		return true;
	}

	/** Fades the intro, the loop and every layer except {@code keep} (the part coming in). */
	private void fadeBody(int ticks, Curve c, int keep) {
		for (int s = 0; s < live.length; s++) if (s != S_END && s != keep) fadePart(s, ticks, c);
	}

	private void fadeAll(int ticks, Curve c) {
		for (int s = 0; s < live.length; s++) fadePart(s, ticks, c);
	}

	private void fadePart(int slot, int ticks, Curve c) {
		Voice v = live[slot];
		if (v == null) return;
		Env old = env[slot];
		if (old != null && old.killAtEnd && old.to == 0F) return; // already on its way out: keep that (shorter) fade
		Env e = new Env();
		e.from = old == null ? 1F : gainAt(old, t);
		e.to = 0F;
		e.t0 = t;
		e.t1 = t + Math.max(1, ticks);
		e.curve = c;
		e.killAtEnd = true;
		env[slot] = e;
	}

	static float gainAt(Env e, int now) {
		if (now >= e.t1) return e.to;
		if (now <= e.t0) return e.from;
		float u = (now - e.t0) / (float) (e.t1 - e.t0);
		float s = switch (e.curve) {
			case LIN -> u;
			case IN -> (float) Math.sin(u * Math.PI / 2);
			case OUT -> 1F - (float) Math.cos(u * Math.PI / 2);
		};
		return e.from + (e.to - e.from) * s;
	}
}
