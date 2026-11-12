package dev.elsewhere.boss_music;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates every theme's {@link Conductor} against fake voices (no sound engine) and checks the transition rules:
 * no part plays twice; at most two parts are audible at once, and two only during a crossfade of at most 21 ticks;
 * nothing is stopped while still audible (gain above 0.02) except on a disconnect; the surge and the ending start on a
 * beat of the playing section (within 1 tick); the ending always plays to its natural end; every scenario finishes.
 * Scenarios: full fight (intro, loop, phase 1 + 2, victory), early events with duplicates, flee, defeat, defeat during
 * the ending, the 2.x legacy plan, and a disconnect.
 */
final class ConductorSim {
	private ConductorSim() {}

	static final class Fake implements Conductor.Voice {
		final Conductor.Part part;
		final int len; // one-shot length in ticks, -1 = loops
		int playAt = -1, killAt = -1, plays;
		float gain, gainAtKill = -1;
		int now;

		Fake(Conductor.Part part, int len) {
			this.part = part;
			this.len = len;
		}

		@Override
		public void play() {
			plays++;
			playAt = now;
		}

		@Override
		public void gain(float g) {
			gain = g;
		}

		@Override
		public void kill() {
			if (killAt < 0) {
				killAt = now;
				gainAtKill = gain;
			}
		}

		@Override
		public boolean finished() {
			return killAt >= 0 || (len >= 0 && playAt >= 0 && now - playAt >= len);
		}

		boolean audible(int introTicks) {
			if (playAt < 0 || finished() || gain <= 0.05F) return false;
			return part != Conductor.Part.INTRO || now < introTicks - 1; // the intro's last 8 ticks are its baked fading tail
		}
	}

	static final class Rig implements Conductor.Voices {
		final Map<Conductor.Part, List<Fake>> made = new EnumMap<>(Conductor.Part.class);
		final Conductor.Plan plan;
		int stingPhase, stingVictory, now;

		Rig(Conductor.Plan plan) {
			this.plan = plan;
		}

		@Override
		public Conductor.Voice make(Conductor.Part p) {
			int len = switch (p) {
				case INTRO -> plan.introTicks() + Conductor.HANDOFF_RAMP;
				case END -> plan.endTicks();
				default -> -1;
			};
			Fake f = new Fake(p, len);
			f.now = now;
			made.computeIfAbsent(p, k -> new ArrayList<>()).add(f);
			return f;
		}

		@Override
		public void sting(String which) {
			if (which.equals("phase")) stingPhase++;
			else if (which.equals("victory")) stingVictory++;
		}

		List<Fake> all() {
			List<Fake> out = new ArrayList<>();
			for (List<Fake> l : made.values()) out.addAll(l);
			return out;
		}
	}

	/** Runs every scenario for one plan; adds a line per broken rule to {@code problems}. Returns the number of checks run. */
	static int check(String name, Conductor.Plan plan, List<String> problems) {
		int checks = 0;
		String[] scen = {"full", "early", "flee", "defeat", "defeat_in_ending", "legacy", "disconnect"};
		for (String sc : scen) {
			Conductor.Plan p = sc.equals("legacy") ? Conductor.Plan.legacy(plan.introTicks()) : plan;
			Rig rig = new Rig(p);
			Conductor c = new Conductor(p, rig, sc.equals("legacy"), 50, 80, 40);
			int overlapRun = 0, maxOverlap = 0;
			boolean disconnect = sc.equals("disconnect");
			int t = 0, limit = p.introTicks() + 12000;
			int evAt1 = switch (sc) {
				case "early" -> 5;
				case "flee" -> p.introTicks() + 50;
				case "defeat" -> Math.max(3, p.introTicks() / 2);
				case "disconnect" -> p.introTicks() + 40;
				default -> p.introTicks() + (int) Math.min(2 * p.loopTicks(), 1600) + 7;
			};
			int[] surgeGrid = null;
			while (t < limit && !c.done()) {
				t++;
				rig.now = t;
				for (Fake f : rig.all()) f.now = t;
				int originBefore = c.state() == Conductor.State.LOOP || c.state() == Conductor.State.INTRO ? p.introTicks() : -1;
				switch (sc) {
					case "full", "legacy", "defeat_in_ending" -> {
						if (t == evAt1) c.phase(0);
						if (t == evAt1 + 200) c.phase(1);
						if (t == evAt1 + 300) c.victory();
						if (sc.equals("defeat_in_ending") && t == evAt1 + 330) c.defeat();
					}
					case "early" -> {
						if (t == evAt1) { c.phase(0); c.phase(0); c.phase(1); }
						if (t == evAt1 + 30) { c.victory(); c.victory(); c.flee(); }
					}
					case "flee" -> { if (t == evAt1) { c.flee(); c.flee(); c.phase(0); c.victory(); } }
					case "defeat" -> { if (t == evAt1) { c.defeat(); c.defeat(); c.victory(); } }
					case "disconnect" -> { if (t == evAt1) c.stopNow(); }
					default -> {}
				}
				c.tick();
				int aud = 0;
				for (Fake f : rig.all()) if (f.audible(p.introTicks())) aud++;
				if (aud > 2) problems.add(name + "/" + sc + ": " + aud + " parts audible at tick " + t);
				overlapRun = aud == 2 ? overlapRun + 1 : 0;
				maxOverlap = Math.max(maxOverlap, overlapRun);
				checks++;
			}
			if (!c.done()) problems.add(name + "/" + sc + ": never finished (" + c.state() + " after " + t + " ticks)");
			if (maxOverlap > 21) problems.add(name + "/" + sc + ": two parts overlapped for " + maxOverlap + " ticks");
			for (Fake f : rig.all()) {
				if (f.plays > 1) problems.add(name + "/" + sc + ": " + f.part + " played " + f.plays + " times");
				if (!disconnect && f.killAt >= 0 && f.gainAtKill > 0.02F && !(f.len >= 0 && f.killAt - f.playAt >= f.len))
					problems.add(name + "/" + sc + ": " + f.part + " cut at gain " + f.gainAtKill);
				if (f.part == Conductor.Part.END && f.killAt >= 0 && !disconnect)
					problems.add(name + "/" + sc + ": ending stopped before its natural end");
			}
			for (Conductor.Part part : Conductor.Part.values()) {
				List<Fake> l = rig.made.get(part);
				if (l != null && l.size() > 1) problems.add(name + "/" + sc + ": " + part + " made " + l.size() + " times");
			}
			boolean modern = !sc.equals("legacy") && p.beatTicks() > 0;
			if (sc.equals("full") && modern) {
				boolean surge = rig.made.containsKey(Conductor.Part.SURGE);
				if (surge != p.hasSurge()) problems.add(name + "/full: surge " + (surge ? "played without data" : "missing"));
				if (p.hasEnd() && !rig.made.containsKey(Conductor.Part.END)) problems.add(name + "/full: no ending on victory");
				if (!p.hasEnd() && rig.stingVictory != 1) problems.add(name + "/full: victory stinger count " + rig.stingVictory);
				int wantPhase = p.hasSurge() ? 1 : 2;
				if (rig.stingPhase != wantPhase) problems.add(name + "/full: phase stingers " + rig.stingPhase + " (want " + wantPhase + ")");
				// beat sync: the surge starts on a beat of the loop (grid from the loop start + phase)
				Fake s = surge ? rig.made.get(Conductor.Part.SURGE).get(0) : null;
				if (s != null) {
					float b = p.beatTicks();
					float pos = (s.playAt - p.introTicks()) % p.loopTicks();
					float r = ((pos - p.loopPhase()) % b + b) % b;
					boolean ok = r <= 1.01F || b - r <= 1.01F || Math.abs(pos - p.loopTicks()) <= 1.01F || pos <= 1.01F + p.loopPhase();
					if (!ok) problems.add(name + "/full: surge off the beat (" + r + " of " + b + " ticks)");
				}
				Fake e = rig.made.containsKey(Conductor.Part.END) ? rig.made.get(Conductor.Part.END).get(0) : null;
				if (e != null && e.playAt < evAt1 + 300) problems.add(name + "/full: ending started before the kill");
				if (e != null && e.playAt > evAt1 + 300 + Math.ceil(b(p)) + 1) problems.add(name + "/full: ending waited more than a beat");
			}
			if ((sc.equals("flee") || sc.equals("defeat")) && rig.made.containsKey(Conductor.Part.END)) problems.add(name + "/" + sc + ": ending played after flee/defeat");
			if (sc.equals("defeat_in_ending") && modern && p.hasEnd()) {
				Fake e = rig.made.get(Conductor.Part.END).get(0);
				if (e.killAt >= 0) problems.add(name + "/defeat_in_ending: ending cut by the defeat fade");
			}
		}
		return checks;
	}

	private static float b(Conductor.Plan p) {
		return p.beatTicks() > 0 ? p.beatTicks() : 1;
	}
}
