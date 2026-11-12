package dev.elsewhere.kingdom.armies.lod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Self-test checks for the client render LOD (ARMIES.md 3.7). The rules live in {@link RenderLod} with no client classes, so the
 * armies server self-test runs them: tiers and hysteresis, the nearest-N full-detail cap, the draw distance, the particle window, the
 * sound slots, the battle din, the baked-skin index and atlas, and a CPU bench of the per-frame decision for a 300-soldier battle.
 * Called from {@code ArmiesSelfTest.build()}; each result becomes one PASS/FAIL line.
 */
public final class LodSelfTest {
	private LodSelfTest() {}

	public record Result(String name, boolean ok, Object detail) {}

	public static List<Result> run() {
		List<Result> out = new ArrayList<>();
		RenderLod.load();
		out.add(new Result("client LOD: kill switch client_lod is on by default (config/elsewhere_armies.json)", RenderLod.enabled(), null));

		// ---- tiers -----------------------------------------------------------------------------------------------------------
		boolean raw = RenderLod.tier(20 * 20, -1) == RenderLod.FULL && RenderLod.tier(30 * 30, -1) == RenderLod.NEAR && RenderLod.tier(60 * 60, -1) == RenderLod.FAR
			&& RenderLod.tier(100 * 100, -1) == RenderLod.BEYOND && RenderLod.tier(24 * 24, -1) == RenderLod.FULL && RenderLod.tier(96 * 96, -1) == RenderLod.FAR;
		boolean hyst = RenderLod.tier(25 * 25, RenderLod.FULL) == RenderLod.FULL && RenderLod.tier(26.5 * 26.5, RenderLod.FULL) == RenderLod.NEAR
			&& RenderLod.tier(23 * 23, RenderLod.NEAR) == RenderLod.NEAR && RenderLod.tier(21.5 * 21.5, RenderLod.NEAR) == RenderLod.FULL
			&& RenderLod.tier(97 * 97, RenderLod.FAR) == RenderLod.FAR && RenderLod.tier(60 * 60, RenderLod.FULL) == RenderLod.FAR
			&& RenderLod.tier(10 * 10, RenderLod.FAR) == RenderLod.FULL && RenderLod.tier(49 * 49, RenderLod.FAR) == RenderLod.FAR;
		out.add(new Result("client LOD: distance tiers full <= 24, near 24-48, far 48-96, beyond 96 (not drawn)", raw, null));
		out.add(new Result("client LOD: a tier changes only 2 blocks past its edge (no flicker)", hyst, null));
		int t = RenderLod.tier(23.6 * 23.6, -1), changes = 0;
		for (int i = 0; i < 200; i++) {
			double d = 24 + (i % 2 == 0 ? 0.6 : -0.6);
			int n = RenderLod.tier(d * d, t);
			if (n != t) changes++;
			t = n;
		}
		out.add(new Result("client LOD: a soldier pacing across the 24-block edge keeps one tier for 200 frames", changes == 0, changes + " changes"));
		out.add(new Result("client LOD: soldier detail sets the full-detail cap (Low 24, Medium 48, High 72)",
			RenderLod.fullDetailCap(0) == 24 && RenderLod.fullDetailCap(1) == 48 && RenderLod.fullDetailCap(2) == 72, null));
		out.add(new Result("client LOD: live soldiers are drawn to 96 blocks, scaled by the entity-distance slider",
			RenderLod.drawDistance(1.0) == 96 && RenderLod.drawDistance(1.5) == 144 && RenderLod.drawDistance(0.5) == 48 && RenderLod.drawDistance(0.1) == 48, null));

		// ---- the nearest-48 cap ------------------------------------------------------------------------------------------------
		Random r = new Random(7);
		int n = 300;
		float[] d2 = new float[n];
		for (int i = 0; i < n; i++) {
			float d = 2 + r.nextFloat() * 21; // all 300 inside 24 blocks: every one wants full detail
			d2[i] = d * d;
		}
		RenderLod.Budget b = new RenderLod.Budget();
		b.endFrame(48);
		boolean[] full = new boolean[n];
		int first = frame(b, d2, full, new boolean[n]);
		b.endFrame(48);
		int second = frame(b, d2, full, new boolean[n]);
		float[] sorted = d2.clone();
		Arrays.sort(sorted);
		boolean nearest = true;
		for (int i = 0; i < n; i++) if (full[i] && d2[i] > sorted[47]) nearest = false;
		b.endFrame(48);
		boolean[] was = full.clone();
		int third = frame(b, d2, full, was);
		out.add(new Result("client LOD: at most 48 soldiers at full detail per frame, and from the second frame on they are the nearest 48 (300 in range)",
			first <= 48 && second == 48 && nearest && third <= 48, first + "/" + second + "/" + third + " granted, nearest " + nearest));

		// ---- particles, sounds, din ----------------------------------------------------------------------------------------------
		RenderLod.Window w = new RenderLod.Window(RenderLod.PARTICLE_BUDGET, 20, 50);
		int got = 0;
		for (int i = 0; i < 1000; i++) if (w.tryAcquire(10_000 + i)) got++; // 1000 asks within one second
		int later = 0;
		for (int i = 0; i < 50; i++) if (w.tryAcquire(11_100 + i)) later++;
		out.add(new Result("client LOD: battle particles capped at 400 per second, and the budget refills", got == 400 && later == 50, got + " then " + later));
		RenderLod.Slots<boolean[]> slots = new RenderLod.Slots<>(RenderLod.SOUND_SLOTS);
		List<boolean[]> playing = new ArrayList<>();
		int took = 0;
		for (int i = 0; i < 15; i++) {
			boolean[] s = {true};
			if (slots.tryTake(s, x -> x[0])) {
				took++;
				playing.add(s);
			}
		}
		for (int i = 0; i < 3; i++) playing.get(i)[0] = false; // three sounds finish
		int again = 0;
		for (int i = 0; i < 5; i++) if (slots.tryTake(new boolean[] {true}, x -> x[0])) again++;
		out.add(new Result("client LOD: at most 12 battle sounds at once; a finished sound frees its slot", took == 12 && again == 3, took + " then " + again));
		RenderLod.Din din = new RenderLod.Din();
		int dins = 0;
		float vmin = 2, vmax = -1;
		for (int i = 0; i < 100; i++) {
			if (din.feed(40 + i % 5, 64, 40, 50_000 + i * 10L)) {
				dins++;
				float v = din.takeVolume();
				vmin = Math.min(vmin, v);
				vmax = Math.max(vmax, v);
			}
		}
		out.add(new Result("client LOD: 100 far hits in one second become 2-3 battle din sounds (one every 350 ms), volume 0.35-1.0",
			dins >= 2 && dins <= 3 && vmin >= 0.35F && vmax <= 1.0F && din.absorbed == 100, dins + " dins, volume " + vmin + "-" + vmax));

		// ---- baked LOD skins --------------------------------------------------------------------------------------------------
		out.add(bakedSkins());

		// ---- bench: per-frame decision cost for a 300-soldier battle ---------------------------------------------------------
		out.add(bench());
		return out;
	}

	/** One frame of the cap for soldiers at d2 (all want full detail); returns how many got it. */
	private static int frame(RenderLod.Budget b, float[] d2, boolean[] full, boolean[] wasFull) {
		int g = 0;
		for (int i = 0; i < d2.length; i++) {
			b.offer(d2[i]);
			full[i] = b.grant(d2[i], wasFull[i]);
			if (full[i]) g++;
		}
		return g;
	}

	private static Result bakedSkins() {
		String name = "client LOD: every unit type has a baked LOD skin (64x64, plus a one-layer copy for ranks) in the index and the 1024 atlas exists";
		try (InputStream in = LodSelfTest.class.getResourceAsStream("/assets/elsewhere/lod/lod_skins.json")) {
			if (in == null) return new Result(name, false, "lod_skins.json missing");
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Set<String> units = new HashSet<>();
			List<String> problems = new ArrayList<>();
			for (JsonElement el : o.getAsJsonArray("skins")) {
				JsonObject s = el.getAsJsonObject();
				units.add(s.get("unit").getAsString());
				Identifier baked = Identifier.parse(s.get("baked").getAsString());
				if (LodSelfTest.class.getResource("/assets/" + baked.getNamespace() + "/" + baked.getPath()) == null) problems.add("no png " + baked);
				Identifier flat = s.has("flat") ? Identifier.parse(s.get("flat").getAsString()) : null;
				if (flat == null || LodSelfTest.class.getResource("/assets/" + flat.getNamespace() + "/" + flat.getPath()) == null) problems.add("no flat png " + flat);
			}
			if (LodSelfTest.class.getResource("/assets/elsewhere/textures/entity/kingdom/lod/atlas.png") == null) problems.add("no atlas");
			if (o.get("atlas_size").getAsInt() != 1024 || units.size() > 256) problems.add("atlas size");
			for (var e : UnitTypes.all().entrySet()) {
				UnitType u = e.getValue();
				String skin = u.skin() == null ? "" : u.skin().toString();
				if (!skin.startsWith("elsewhere:textures/entity/kingdom/") || skin.contains("/testfaction/")) continue; // test-only units
				if (!units.contains(e.getKey().toString())) problems.add("unbaked " + e.getKey());
			}
			return new Result(name, problems.isEmpty(), problems.isEmpty() ? units.size() + " baked" : problems);
		} catch (Exception ex) {
			return new Result(name, false, ex.toString());
		}
	}

	private static Result bench() {
		Random r = new Random(11);
		int n = 300, warm = 2000, frames = 4000;
		float[] x = new float[n], z = new float[n];
		int[] prev = new int[n];
		Arrays.fill(prev, -1);
		for (int i = 0; i < n; i++) {
			x[i] = -110 + r.nextFloat() * 220;
			z[i] = -110 + r.nextFloat() * 220;
		}
		RenderLod.Budget b = new RenderLod.Budget();
		int[] tiers = new int[4];
		long spent = 0;
		for (int f = 0; f < frames; f++) {
			for (int i = 0; i < n; i++) { // the battle mills about (not timed)
				x[i] += (r.nextFloat() - 0.5F) * 0.3F;
				z[i] += (r.nextFloat() - 0.5F) * 0.3F;
			}
			long t0 = System.nanoTime();
			for (int i = 0; i < n; i++) {
				float d2 = x[i] * x[i] + z[i] * z[i];
				int t = RenderLod.tier(d2, prev[i]);
				if (t == RenderLod.FULL) {
					b.offer(d2);
					if (!b.grant(d2, prev[i] == RenderLod.FULL)) t = RenderLod.NEAR;
				}
				prev[i] = t;
				if (f == frames - 1) tiers[t]++;
			}
			b.endFrame(48);
			if (f >= warm) spent += System.nanoTime() - t0; // after the JIT warm-up
		}
		double perFrameMs = spent / 1e6 / (frames - warm);
		return new Result(String.format("client LOD bench: tier and full-detail decisions for 300 soldiers cost <= 0.25 ms per frame (%.4f ms; last frame %s)", perFrameMs,
			Arrays.toString(tiers)), perFrameMs <= 0.25 && tiers[RenderLod.FULL] <= 48, null);
	}
}
