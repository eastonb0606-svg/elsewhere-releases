package dev.elsewhere.arsenal.anim;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * POLISH P6, weapon use animations: the pure math shared by the client renderers and the server self-test (no client
 * classes, so the dedicated-server self-test can check it).
 * <ul>
 * <li>First person: a hand-space pose (translation in blocks, rotations in degrees) used instead of vanilla's whack swing.</li>
 * <li>Third person: arm, body and leg angles (radians) used instead of vanilla's attack animation.</li>
 * <li>Trails: up to {@link #TRAIL_MAX} weapon-tip positions sampled back along the same curves. Nothing is stored per
 * player and nothing ticks: the ribbon is rebuilt from the swing progress each frame.</li>
 * </ul>
 * Every curve is the identity at progress 0 and 1, so the weapon always returns to the vanilla rest pose.
 */
public final class SwingCurves {
	private SwingCurves() {}

	public enum Style {
		NONE, FLAIL, RAPIER, CAST;

		/** Weapons that draw a swing trail. */
		public boolean trails() {
			return this == FLAIL || this == RAPIER;
		}
	}

	/** POLISH budget: a ribbon keeps at most 6 positions. */
	public static final int TRAIL_MAX = 6;
	/** Swing progress between two trail samples (a swing is 0..1 over ~6 ticks). */
	public static final float TRAIL_STEP = 0.055F;

	/** Weapon style of an item id path ({@code elsewhere:<path>}); other namespaces are always NONE. */
	public static Style styleOf(String namespace, String path) {
		if (!"elsewhere".equals(namespace)) return Style.NONE;
		return switch (path) {
			case "ironclad_flail" -> Style.FLAIL;
			case "gale_rapier" -> Style.RAPIER;
			case "amethyst_wand", "sunlance_staff", "stormcoil_staff" -> Style.CAST;
			default -> Style.NONE;
		};
	}

	// ------------------------------------------------------------------------------------------------ keyframes
	// First person rows: {progress, tx, ty, tz, rx, ry, rz}; right hand (the left hand mirrors tx, ry, rz).
	// Positive rx tips the weapon back toward the player, negative swings it forward and down.

	/** Heavy overhead arc: wind back and up, smash down across the body, slow recovery. */
	static final float[][] FP_FLAIL = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.20F, 0.06F, 0.14F, 0.06F, 40F, 12F, 10F},
		{0.50F, -0.26F, -0.24F, -0.16F, -110F, -18F, -42F},
		{0.72F, -0.20F, -0.20F, -0.10F, -96F, -12F, -32F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F}};
	/** Lunge: a short draw back, then a straight thrust toward the crosshair, held, then withdrawn. */
	static final float[][] FP_RAPIER = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.12F, 0.04F, -0.02F, 0.12F, 10F, 6F, 0F},
		{0.32F, -0.22F, 0.06F, -0.55F, -62F, -12F, 6F},
		{0.55F, -0.18F, 0.04F, -0.46F, -58F, -10F, 5F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F}};
	/** Caster flourish: point the focus forward, a small recoil as the spell leaves it, settle. */
	static final float[][] FP_CAST = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.22F, -0.08F, 0.06F, -0.14F, -30F, -6F, 0F},
		{0.42F, -0.10F, 0.09F, -0.22F, -42F, -8F, 4F},
		{0.62F, -0.06F, 0.11F, -0.05F, -14F, -4F, 2F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F}};

	// Third person rows: {progress, armX, armY, armZ, bodyY, legFront, legBack, weight}; right arm (the left arm mirrors
	// armY, armZ and bodyY). armX is added to by the head pitch for the thrusting styles. weight blends from the walk pose.
	static final float[][] TP_FLAIL = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.20F, -2.90F, 0.20F, 0.25F, 0.35F, 0F, 0F, 1F},
		{0.50F, -0.35F, -0.50F, -0.35F, -0.45F, 0F, 0F, 1F},
		{0.72F, -0.30F, -0.35F, -0.25F, -0.30F, 0F, 0F, 1F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F}};
	static final float[][] TP_RAPIER = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.12F, -0.90F, 0.30F, 0F, 0.25F, -0.10F, 0.10F, 1F},
		{0.32F, -1.55F, -0.15F, 0F, -0.50F, -0.60F, 0.45F, 1F},
		{0.55F, -1.50F, -0.12F, 0F, -0.45F, -0.55F, 0.40F, 1F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F}};
	static final float[][] TP_CAST = {
		{0.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
		{0.25F, -1.40F, -0.10F, 0F, -0.15F, 0F, 0F, 1F},
		{0.45F, -1.50F, -0.12F, 0F, -0.18F, 0F, 0F, 1F},
		{0.70F, -1.00F, -0.06F, 0F, -0.08F, 0F, 0F, 0.8F},
		{1.00F, 0F, 0F, 0F, 0F, 0F, 0F, 0F}};

	/** Trail window (swing progress) per style: the ribbon shows during the strike only. */
	public static float trailStart(Style s) {
		return s == Style.FLAIL ? 0.20F : 0.10F;
	}

	public static float trailEnd(Style s) {
		return s == Style.FLAIL ? 0.62F : 0.48F;
	}

	/** Smoothstep keyframe sampling: {@code out[i]} = column i+1 of the table at progress p (clamped to 0..1). */
	public static void sample(float[][] k, float p, float[] out) {
		float q = Math.max(0F, Math.min(1F, p));
		int cols = k[0].length - 1;
		for (int i = 1; i < k.length; i++) {
			if (q <= k[i][0]) {
				float span = k[i][0] - k[i - 1][0];
				float t = span <= 0 ? 1F : (q - k[i - 1][0]) / span;
				t = t * t * (3F - 2F * t);
				for (int c = 0; c < cols; c++) out[c] = k[i - 1][c + 1] + (k[i][c + 1] - k[i - 1][c + 1]) * t;
				return;
			}
		}
		for (int c = 0; c < cols; c++) out[c] = k[k.length - 1][c + 1];
	}

	public static float[][] firstPersonTable(Style s) {
		return switch (s) {
			case FLAIL -> FP_FLAIL;
			case RAPIER -> FP_RAPIER;
			case CAST -> FP_CAST;
			default -> null;
		};
	}

	public static float[][] thirdPersonTable(Style s) {
		return switch (s) {
			case FLAIL -> TP_FLAIL;
			case RAPIER -> TP_RAPIER;
			case CAST -> TP_CAST;
			default -> null;
		};
	}

	/** Reusable math objects (render thread or test thread only): keeps the per-frame trail math allocation-free. */
	public static final class Scratch {
		final Matrix4f m = new Matrix4f();
		final Quaternionf q = new Quaternionf();
		final Vector3f t = new Vector3f(), a = new Vector3f(), b = new Vector3f();
		final float[] v = new float[7];
	}

	/** First-person pose: {@code t} receives the translation (blocks), {@code rot} the rotation. invert = 1 right hand, -1 left. */
	public static Quaternionf firstPersonPose(Style s, float p, int invert, float[] v, Vector3f t, Quaternionf rot) {
		sample(firstPersonTable(s), p, v);
		t.set(invert * v[0], v[1], v[2]);
		return rot.rotationY(rad(invert * v[4])).rotateX(rad(v[3])).rotateZ(rad(invert * v[5]));
	}

	// ------------------------------------------------------------------------------------------------ weapon tip geometry

	/** Sprite points (0..1, origin bottom-left) of the trail edge: {innerU, innerV, outerU, outerV}. */
	static final float[] EDGE_FLAIL = {0.50F, 0.50F, 0.76F, 0.76F}, EDGE_BLADE = {0.45F, 0.45F, 0.97F, 0.97F};

	public static float[] edge(Style s) {
		return s == Style.FLAIL ? EDGE_FLAIL : EDGE_BLADE;
	}

	/**
	 * {@code minecraft:item/handheld} first-person display transform as {@code ItemTransform.apply} does it, then the
	 * model's -0.5 centring. The left-hand entry is the mirrored JSON plus the left-hand fix, which nets the same
	 * rotation with the x translation negated.
	 */
	public static Matrix4f handheldFirstPerson(Matrix4f m, boolean left) {
		return m.translate((left ? -1.13F : 1.13F) / 16F, 3.2F / 16F, 1.13F / 16F).rotateXYZ(0F, rad(-90F), rad(25F)).scale(0.68F).translate(-0.5F, -0.5F, -0.5F);
	}

	/** {@code minecraft:item/handheld} third-person display transform (either hand: the left-hand fix cancels the mirrored JSON). */
	public static Matrix4f handheldThirdPerson(Matrix4f m) {
		return m.translate(0F, 4F / 16F, 0.5F / 16F).rotateXYZ(0F, rad(-90F), rad(55F)).scale(0.85F).translate(-0.5F, -0.5F, -0.5F);
	}

	/** Writes sample {@code i} (inner xyz, outer xyz) of matrix {@code m} into {@code out}. */
	private static void edgePoints(Matrix4f m, float[] e, Scratch sc, float[] out, int i) {
		m.transformPosition(sc.a.set(e[0], e[1], 0.5F));
		m.transformPosition(sc.b.set(e[2], e[3], 0.5F));
		int o = i * 6;
		out[o] = sc.a.x;
		out[o + 1] = sc.a.y;
		out[o + 2] = sc.a.z;
		out[o + 3] = sc.b.x;
		out[o + 4] = sc.b.y;
		out[o + 5] = sc.b.z;
	}

	/**
	 * First-person trail: hand-space points (the pose before the swing transform) of the inner and outer edge at the
	 * last samples of the swing. {@code out} (6 floats per sample, at least 6 * n) gets inner xyz + outer xyz per
	 * sample, newest first; returns the sample count (0, or 2..n).
	 */
	public static int firstPersonTrail(Style s, float p, int invert, int n, Scratch sc, float[] out) {
		if (!s.trails() || n < 2) return 0;
		float[] e = edge(s);
		int count = 0;
		for (int k = 0; k < n; k++) {
			float pk = p - k * TRAIL_STEP;
			if (pk > trailEnd(s)) continue;
			if (pk < trailStart(s)) break;
			firstPersonPose(s, pk, invert, sc.v, sc.t, sc.q);
			handheldFirstPerson(sc.m.translation(sc.t).rotate(sc.q), invert < 0);
			edgePoints(sc.m, e, sc, out, count++);
		}
		return count >= 2 ? count : 0;
	}

	/** Third-person angles at progress p: {armX, armY, armZ, bodyY, legFront, legBack, weight} for the right arm. */
	public static void thirdPerson(Style s, float p, float headPitchRad, float[] out) {
		sample(thirdPersonTable(s), p, out);
		if (s == Style.RAPIER || s == Style.CAST) out[0] += headPitchRad * out[6] * 0.8F;
	}

	/**
	 * Third-person trail in model space (blocks, y down, facing -z: the pose the avatar's render layers get), from the
	 * same arm angles the model uses. Same output layout as {@link #firstPersonTrail}.
	 */
	public static int thirdPersonTrail(Style s, float p, boolean leftArm, float headPitchRad, boolean crouching, int n, Scratch sc, float[] out) {
		if (!s.trails() || n < 2) return 0;
		float[] e = edge(s);
		float[] a = sc.v;
		int count = 0;
		for (int k = 0; k < n; k++) {
			float pk = p - k * TRAIL_STEP;
			if (pk > trailEnd(s)) continue;
			if (pk < trailStart(s)) break;
			thirdPerson(s, pk, headPitchRad, a);
			float side = leftArm ? -1F : 1F;
			float bodyY = a[3] * side;
			// arm pivot (-5, 2, 0) right / (5, 2, 0) left, swung round with the body as in vanilla's attack animation and
			// UseAnims.thirdPersonSwing; then ItemInHandLayer's hand offset and the item's display transform
			sc.m.translation(-side * (float) Math.cos(bodyY) * 5F / 16F, (2F + (crouching ? 3.2F : 0F)) / 16F, side * (float) Math.sin(bodyY) * 5F / 16F)
				.rotate(sc.q.rotationZYX(a[2] * side, a[1] * side + bodyY, a[0]))
				.rotateX(rad(-90F)).rotateY(rad(180F)).translate(side / 16F, 2F / 16F, -10F / 16F);
			handheldThirdPerson(sc.m);
			edgePoints(sc.m, e, sc, out, count++);
		}
		return count >= 2 ? count : 0;
	}

	/** Ribbon alpha of sample k of n: bright at the weapon, fading to 0 at the tail. */
	public static float trailAlpha(int k, int n, float base) {
		return n <= 1 ? 0F : base * (1F - (float) k / (n - 1));
	}

	/** Trail sample count for a client effects level (0 low, 1 normal, 2 high). */
	public static int trailSamples(int level) {
		return level <= 0 ? 0 : level == 1 ? 5 : TRAIL_MAX;
	}

	/** Trail draw distance in blocks for a client effects level (POLISH: other players within 24, 32 on high). */
	public static double trailRange(int level) {
		return level <= 0 ? 0 : level == 1 ? 24 : 32;
	}

	public static float rad(float deg) {
		return deg * (float) (Math.PI / 180.0);
	}
}
