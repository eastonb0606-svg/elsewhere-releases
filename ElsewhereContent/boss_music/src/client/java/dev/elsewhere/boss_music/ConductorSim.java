package dev.elsewhere.boss_music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates every theme's {@link Conductor} against fake voices (no sound engine) and checks the transition rules:
 * no part plays twice; at most two parts stream at once (P12: no extra streams beyond 2, the intro's baked tail counted)
 * and at most two are audible, two only during a
 * crossfade of at most 21 ticks; nothing is stopped while still audible (gain above 0.02) except on a disconnect; every
 * phase layer and the ending start on a beat of the section playing (within 1 tick); a threshold with a layer swaps into it
 * and one without plays the stinger; the ending always plays to its natural end; every scenario finishes.
 * Scenarios: full fight (intro, loop, phase 1 + 2, victory), early events with duplicates, joined low (both thresholds at
 * once), rapid (thresholds and the kill a few ticks apart), at the intro hand-off, flee, defeat, defeat during the ending,
 * the 2.x legacy plan, and a disconnect.
 */
final class ConductorSim {
	private ConductorSim() {}

	static final String[] SCENARIOS = {"full", "early", "join_low", "rapid", "at_handoff", "flee", "defeat", "defeat_in_ending", "legacy", "disconnect"};

	static final class Fake implements Conductor.Voice {
		final Conductor.Part part;
		final Conductor.Layer layer;
		final int len; // one-shot length in ticks, -1 = loops
		int playAt = -1, killAt = -1, plays;
		float gain, gainAtKill = -1;
		int now;

		Fake(Conductor.Part part, Conductor.Layer layer, int len) {
			this.part = part;
			this.layer = layer;
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

		String key() {
			return part == Conductor.Part.LAYER ? "LAYER:" + layer.name() : part.name();
		}

		boolean looping() {
			return part == Conductor.Part.LOOP || part == Conductor.Part.LAYER;
		}
	}

	static final class Rig implements Conductor.Voices {
		final Map<String, List<Fake>> made = new LinkedHashMap<>();
		final List<Fake> order = new ArrayList<>();
		final Conductor.Plan plan;
		int stingPhase, stingVictory, now;

		Rig(Conductor.Plan plan) {
			this.plan = plan;
		}

		@Override
		public Conductor.Voice make(Conductor.Part p, Conductor.Layer layer) {
			int len = switch (p) {
				case INTRO -> plan.introTicks() + Conductor.HANDOFF_RAMP;
				case END -> plan.endTicks();
				default -> -1;
			};
			Fake f = new Fake(p, layer, len);
			f.now = now;
			made.computeIfAbsent(f.key(), k -> new ArrayList<>()).add(f);
			order.add(f);
			return f;
		}

		@Override
		public void sting(String which) {
			if (which.equals("phase")) stingPhase++;
			else if (which.equals("victory")) stingVictory++;
		}

		Fake first(String key) {
			List<Fake> l = made.get(key);
			return l == null || l.isEmpty() ? null : l.get(0);
		}
	}

	/** Runs every scenario for one plan; adds a line per broken rule to {@code problems}. Returns the number of checks run. */
	static int check(String name, Conductor.Plan plan, List<String> problems) {
		int checks = 0;
		for (String sc : SCENARIOS) {
			Conductor.Plan p = sc.equals("legacy") ? Conductor.Plan.legacy(plan.introTicks()) : plan;
			Rig rig = new Rig(p);
			Conductor c = new Conductor(p, rig, sc.equals("legacy"), 50, 80, 40);
			int overlapRun = 0, maxOverlap = 0;
			boolean disconnect = sc.equals("disconnect");
			int t = 0, limit = p.introTicks() + 12000;
			int evAt1 = switch (sc) {
				case "early", "join_low" -> 5;
				case "at_handoff" -> Math.max(3, p.introTicks() - 2);
				case "flee" -> p.introTicks() + 50;
				case "defeat" -> Math.max(3, p.introTicks() / 2);
				case "disconnect" -> p.introTicks() + 40;
				default -> p.introTicks() + (int) Math.min(2 * p.loopTicks(), 1600) + 7;
			};
			while (t < limit && !c.done()) {
				t++;
				rig.now = t;
				for (Fake f : rig.order) f.now = t;
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
					case "join_low" -> {
						if (t == evAt1) { c.phase(0); c.phase(1); }
						if (t == evAt1 + p.introTicks() + 400) c.victory();
					}
					case "at_handoff" -> { // thresholds right at the intro -> loop hand-off (its tail still streams)
						if (t == evAt1) c.phase(0);
						if (t == evAt1 + 6) c.phase(1);
						if (t == evAt1 + 300) c.victory();
					}
					case "rapid" -> {
						if (t == evAt1) c.phase(0);
						if (t == evAt1 + 2) c.phase(1);
						if (t == evAt1 + 4) c.victory();
					}
					case "flee" -> { if (t == evAt1) { c.flee(); c.flee(); c.phase(0); c.victory(); } }
					case "defeat" -> { if (t == evAt1) { c.defeat(); c.defeat(); c.victory(); } }
					case "disconnect" -> { if (t == evAt1) c.stopNow(); }
					default -> {}
				}
				c.tick();
				int aud = 0, alive = 0;
				for (Fake f : rig.order) {
					if (f.audible(p.introTicks())) aud++;
					if (f.playAt >= 0 && !f.finished()) alive++;
				}
				if (aud > 2) problems.add(name + "/" + sc + ": " + aud + " parts audible at tick " + t);
				if (alive > 2) problems.add(name + "/" + sc + ": " + alive + " streams at tick " + t + " (P12 cap: 2)");
				overlapRun = aud == 2 ? overlapRun + 1 : 0;
				maxOverlap = Math.max(maxOverlap, overlapRun);
				checks++;
			}
			if (!c.done()) problems.add(name + "/" + sc + ": never finished (" + c.state() + " after " + t + " ticks)");
			if (maxOverlap > 21) problems.add(name + "/" + sc + ": two parts overlapped for " + maxOverlap + " ticks");
			for (Fake f : rig.order) {
				if (f.plays > 1) problems.add(name + "/" + sc + ": " + f.key() + " played " + f.plays + " times");
				if (!disconnect && f.killAt >= 0 && f.gainAtKill > 0.02F && !(f.len >= 0 && f.killAt - f.playAt >= f.len))
					problems.add(name + "/" + sc + ": " + f.key() + " cut at gain " + f.gainAtKill);
				if (f.part == Conductor.Part.END && f.killAt >= 0 && !disconnect)
					problems.add(name + "/" + sc + ": ending stopped before its natural end");
			}
			for (var e : rig.made.entrySet()) if (e.getValue().size() > 1) problems.add(name + "/" + sc + ": " + e.getKey() + " made " + e.getValue().size() + " times");
			boolean modern = !sc.equals("legacy") && p.beatTicks() > 0;
			if (modern) checkBeats(name + "/" + sc, p, rig, problems);
			if (sc.equals("full") && modern) {
				int wantPhase = 0;
				for (int i = 0; i < 2; i++) {
					int li = p.layerIndex(i);
					if (li < 0) {
						wantPhase++;
						continue;
					}
					if (rig.first("LAYER:" + p.layers().get(li).name()) == null) problems.add(name + "/full: threshold " + i + " never brought in layer " + p.layers().get(li).name());
				}
				for (var e : rig.made.keySet()) {
					if (!e.startsWith("LAYER:")) continue;
					boolean known = false;
					for (Conductor.Layer l : p.layers()) if (e.equals("LAYER:" + l.name()) && l.phase() < 2) known = true;
					if (!known) problems.add(name + "/full: " + e + " played without a threshold");
				}
				if (p.hasEnd() && rig.first("END") == null) problems.add(name + "/full: no ending on victory");
				if (!p.hasEnd() && rig.stingVictory != 1) problems.add(name + "/full: victory stinger count " + rig.stingVictory);
				if (rig.stingPhase != wantPhase) problems.add(name + "/full: phase stingers " + rig.stingPhase + " (want " + wantPhase + ")");
				Fake e = rig.first("END");
				if (e != null && e.playAt < evAt1 + 300) problems.add(name + "/full: ending started before the kill");
				if (e != null && e.playAt > evAt1 + 300 + Math.ceil(p.beatTicks()) + 1) problems.add(name + "/full: ending waited more than a beat");
			}
			if (sc.equals("join_low") && modern) { // both thresholds at once: only the deeper threshold's layer (if any) comes in
				int l0 = p.layerIndex(0), l1 = p.layerIndex(1);
				if (l1 >= 0 && l0 >= 0 && rig.first("LAYER:" + p.layers().get(l0).name()) != null)
					problems.add(name + "/join_low: superseded layer " + p.layers().get(l0).name() + " still played");
				int want = l1 >= 0 ? l1 : l0;
				if (want >= 0 && rig.first("LAYER:" + p.layers().get(want).name()) == null)
					problems.add(name + "/join_low: layer " + p.layers().get(want).name() + " missing");
			}
			if ((sc.equals("flee") || sc.equals("defeat")) && rig.made.containsKey("END")) problems.add(name + "/" + sc + ": ending played after flee/defeat");
			if (sc.equals("defeat_in_ending") && modern && p.hasEnd()) {
				Fake e = rig.first("END");
				if (e == null) problems.add(name + "/defeat_in_ending: no ending");
				else if (e.killAt >= 0) problems.add(name + "/defeat_in_ending: ending cut by the defeat fade");
			}
		}
		return checks;
	}

	/**
	 * Every layer and the ending start on a beat of the section that was playing (the loop's grid, extended back over the
	 * intro, or the previous layer's own grid from its start), within one tick.
	 */
	private static void checkBeats(String tag, Conductor.Plan p, Rig rig, List<String> problems) {
		float b = p.beatTicks();
		Fake prev = null; // the looping section playing before each start
		for (Fake f : rig.order) {
			if (f.part == Conductor.Part.LAYER || f.part == Conductor.Part.END) {
				float origin, period, ph;
				if (prev == null || prev.part == Conductor.Part.LOOP) {
					origin = p.introTicks() > 0 ? p.introTicks() : 0;
					period = p.loopTicks();
					ph = p.loopPhase();
				} else {
					origin = prev.playAt;
					period = prev.layer.loopTicks();
					ph = prev.layer.phaseTicks();
				}
				float pos = f.playAt - origin;
				pos = pos >= 0 && period > 0 ? pos % period : pos;
				float r = (((pos - ph) % b) + b) % b;
				boolean ok = r <= 1.01F || b - r <= 1.01F || (period > 0 && Math.abs(pos - period) <= 1.01F) || (pos >= 0 && pos <= 1.01F + ph);
				if (!ok) problems.add(tag + ": " + f.key() + " off the beat (" + r + " of " + b + " ticks)");
			}
			if (f.looping()) prev = f;
		}
	}
}
