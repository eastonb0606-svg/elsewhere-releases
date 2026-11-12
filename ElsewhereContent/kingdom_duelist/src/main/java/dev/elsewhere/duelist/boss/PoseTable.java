package dev.elsewhere.duelist.boss;

/**
 * 3.0 (P4/B4): the keyframes behind each {@link MovePose} key and each kingdom's way of moving. Plain data in the common
 * source set so the self-test can prove every key has its own wind-up and strike (the client animation reads the same
 * arrays). Values are radians in model space (weapon arm on the right, mirrored for left-handers), crouch and lift in
 * pixels (lift negative is up).
 */
public final class PoseTable {
	private PoseTable() {}

	/** Pose values: weapon arm x/y/z, off arm x/y/z, torso twist, torso lean, crouch (px), weapon-side leg, off-side leg, head pitch, lift (px). */
	public static final int N = 13;
	public static final int WX = 0, WY = 1, WZ = 2, OX = 3, OY = 4, OZ = 5, TWIST = 6, LEAN = 7, CROUCH = 8, LEG_W = 9, LEG_O = 10, HEAD = 11, LIFT = 12;

	/** [key][0] = wind-up pose, [key][1] = strike pose. */
	public static final float[][][] POSES = new float[MovePose.NAMES.length][][];
	/** A rhythm laid over the wind-up: amplitude (radians on the arms, alternating) and rate (radians per tick). Drum beats, horn and rite trembles. */
	public static final float[] BEAT = new float[MovePose.NAMES.length], BEAT_RATE = new float[MovePose.NAMES.length];

	static {
		// weapon swings (P4, unchanged from the anim lane)
		POSES[MovePose.OVERHEAD] = new float[][] {
				{-2.75F, 0.15F, 0.30F, -2.45F, -0.35F, -0.20F, 0.15F, -0.18F, 0F, 0.10F, -0.10F, -0.12F, 0F},
				{-0.55F, -0.05F, 0.05F, -0.65F, 0.25F, 0.00F, -0.10F, 0.42F, 0F, -0.45F, 0.35F, 0.10F, 0F}};
		POSES[MovePose.SWEEP] = new float[][] {
				{-1.35F, 1.05F, 0.55F, -0.45F, 0.00F, -0.45F, 0.65F, 0.05F, 0F, 0.25F, -0.25F, 0F, 0F},
				{-1.45F, -1.15F, 0.05F, -0.35F, 0.00F, -0.55F, -0.75F, 0.18F, 0F, -0.35F, 0.30F, 0F, 0F}};
		POSES[MovePose.THRUST] = new float[][] {
				{-1.05F, 0.35F, 0.15F, -0.95F, -0.25F, -0.10F, 0.45F, -0.12F, 0F, 0.15F, -0.15F, 0F, 0F},
				{-1.62F, -0.10F, 0.00F, -0.20F, 0.00F, -0.40F, -0.35F, 0.38F, 0F, -0.75F, 0.55F, 0.05F, 0F}};
		POSES[MovePose.SLAM] = new float[][] {
				{-2.95F, -0.25F, 0.40F, -2.95F, 0.25F, -0.40F, 0F, -0.28F, 0F, 0.05F, -0.05F, -0.25F, 0F},
				{-0.75F, -0.30F, 0.15F, -0.75F, 0.30F, -0.15F, 0F, 0.65F, 1.5F, -0.55F, 0.40F, 0.25F, 0F}};
		POSES[MovePose.LEAP] = new float[][] {
				{0.55F, 0.00F, 0.35F, 0.55F, 0.00F, -0.35F, 0F, 0.45F, 1.5F, -0.45F, 0.45F, -0.30F, 0F},
				{-2.55F, 0.00F, 0.30F, -2.55F, 0.00F, -0.30F, 0F, -0.15F, 0F, -0.85F, -0.25F, -0.10F, -3.0F}};
		POSES[MovePose.BASH] = new float[][] {
				{-0.35F, 0.00F, 0.25F, -1.10F, 0.55F, 0.10F, -0.50F, 0.05F, 0F, 0.20F, -0.20F, 0F, 0F},
				{-0.45F, 0.00F, 0.30F, -1.55F, -0.20F, 0.00F, 0.45F, 0.40F, 0F, 0.35F, -0.65F, 0.05F, 0F}};
		POSES[MovePose.DRAW] = new float[][] {
				{-2.65F, 0.35F, 0.35F, -1.35F, 0.45F, 0.00F, 0.55F, -0.18F, 0F, 0.30F, -0.25F, 0F, 0F},
				{-1.15F, -0.45F, 0.00F, -0.45F, 0.00F, -0.30F, -0.45F, 0.28F, 0F, -0.40F, 0.35F, 0F, 0F}};
		POSES[MovePose.CAST] = new float[][] {
				{-2.25F, -0.35F, 0.55F, -2.25F, 0.35F, -0.55F, 0F, -0.20F, 0F, 0.00F, 0.00F, -0.35F, 0F},
				{-1.60F, 0.20F, 0.10F, -1.60F, -0.20F, -0.10F, 0F, 0.18F, 0F, -0.20F, 0.15F, 0.05F, 0F}};
		// B4 gestures: horn to the lips, then the blast with the chest thrown out and the weapon arm flung wide
		POSES[MovePose.HORN] = new float[][] {
				{-0.35F, 0.00F, 0.35F, -2.05F, 0.70F, 0.00F, -0.15F, -0.12F, 0F, 0.15F, -0.15F, -0.40F, 0F},
				{-0.20F, -0.20F, 0.85F, -2.45F, 0.55F, 0.00F, 0.00F, -0.30F, 0F, -0.25F, 0.25F, -0.70F, 0F}};
		// sink low with the head down, then rear up, arms spread, head flung back
		POSES[MovePose.HOWL] = new float[][] {
				{-0.55F, 0.20F, 0.15F, -0.55F, -0.20F, -0.15F, 0F, 0.45F, 2.0F, -0.35F, 0.35F, 0.35F, 0F},
				{0.35F, 0.00F, 1.15F, 0.35F, 0.00F, -1.15F, 0F, -0.35F, 0F, 0.10F, -0.10F, -0.85F, 0F}};
		// arm drawn across the chest, then pointed straight at the target; off hand on the hip
		POSES[MovePose.COMMAND] = new float[][] {
				{-1.25F, -0.95F, 0.00F, -0.25F, 0.00F, -0.35F, 0.30F, 0.00F, 0F, 0.00F, 0.00F, 0.05F, 0F},
				{-1.62F, 0.05F, 0.00F, -0.20F, 0.00F, -0.55F, -0.30F, 0.12F, 0F, -0.35F, 0.25F, -0.12F, 0F}};
		// both arms rise palms up from a half crouch, then thrown to the sky on the toes
		POSES[MovePose.INVOKE] = new float[][] {
				{-1.05F, -0.20F, 0.10F, -1.05F, 0.20F, -0.10F, 0F, 0.15F, 1.0F, 0.05F, -0.05F, 0.30F, 0F},
				{-2.95F, 0.00F, 0.35F, -2.95F, 0.00F, -0.35F, 0F, -0.22F, 0F, 0.00F, 0.00F, -0.60F, -1.0F}};
		// low wide guard, weapon two-handed in front, then driven forward
		POSES[MovePose.BRACE] = new float[][] {
				{-0.95F, -0.35F, 0.00F, -1.05F, 0.45F, 0.00F, 0.25F, 0.30F, 2.5F, -0.55F, 0.45F, -0.15F, 0F},
				{-1.40F, -0.25F, 0.00F, -1.45F, 0.35F, 0.00F, -0.15F, 0.50F, 1.5F, -0.80F, 0.60F, -0.20F, 0F}};
		// flask to the lips with the head back, then the empty flask flung down and the guard back up
		POSES[MovePose.DRINK] = new float[][] {
				{-0.45F, 0.00F, 0.25F, -2.15F, 0.65F, 0.00F, -0.10F, -0.15F, 0F, 0.10F, -0.10F, -0.55F, 0F},
				{-1.05F, 0.30F, 0.15F, -0.35F, -0.35F, -0.75F, 0.25F, 0.20F, 0.5F, -0.30F, 0.25F, 0.15F, 0F}};
		// hunched over the bog-drum, beating with alternate arms (BEAT), then both fists come down on the last beat
		POSES[MovePose.DRUM] = new float[][] {
				{-1.55F, -0.25F, 0.15F, -0.95F, 0.25F, -0.15F, 0F, 0.25F, 1.0F, -0.20F, 0.20F, 0.20F, 0F},
				{-0.55F, -0.30F, 0.10F, -0.55F, 0.30F, -0.10F, 0F, 0.45F, 1.5F, -0.35F, 0.35F, 0.30F, 0F}};
		// cloak or sleeve swept across the face in a crouch, then flared out wide with a side step
		POSES[MovePose.VEIL] = new float[][] {
				{-0.65F, 0.25F, 0.25F, -1.95F, 0.95F, 0.00F, -0.35F, 0.18F, 1.5F, 0.20F, -0.30F, 0.25F, 0F},
				{-1.15F, 0.50F, 0.40F, -1.35F, -0.85F, -0.95F, 0.45F, 0.05F, 0.5F, -0.35F, 0.45F, -0.05F, 0F}};
		BEAT[MovePose.DRUM] = 0.55F;
		BEAT_RATE[MovePose.DRUM] = 1.05F;
		BEAT[MovePose.HORN] = 0.04F;
		BEAT_RATE[MovePose.HORN] = 2.4F;
		BEAT[MovePose.INVOKE] = 0.06F;
		BEAT_RATE[MovePose.INVOKE] = 1.9F;
		BEAT[MovePose.HOWL] = 0.03F;
		BEAT_RATE[MovePose.HOWL] = 2.8F;
	}

	/**
	 * A kingdom's way of moving. {@code amp} scales the wind-up reach of the arms and the lean, {@code snap}/{@code hold}/{@code recover}
	 * are the strike timing in ticks, {@code lean}/{@code twist} scale the torso, {@code crouch} (px) and {@code stance} (radians of leg
	 * splay) set the guard, {@code head} is a constant head pitch (negative: chin up), {@code breath}/{@code breathRate} the idle breath.
	 */
	public record Style(String name, float amp, float snap, float hold, float recover, float lean, float twist, float crouch, float stance, float head,
			float breath, float breathRate) {}

	public static final int ALDERMARK = 0, HRAFNHOLT = 1, AZMAR = 2, MIREWICK = 3;
	public static final Style[] STYLES = {
			// the duelist and Aldermark baseline: clean fencing timing
			new Style("aldermark", 1.00F, 3F, 2F, 7F, 1.00F, 1.00F, 0.0F, 0.00F, 0.00F, 0.35F, 0.08F),
			// seafaring vikings: big committed blows, a wide sea-legs stance, long recoveries, heavy breathing
			new Style("hrafnholt", 1.12F, 3F, 3F, 9F, 1.25F, 0.90F, 0.5F, 0.10F, 0.06F, 0.55F, 0.06F),
			// Azmar: light and quick, low on the feet, whipping torso, snaps in two ticks and is back in five
			new Style("azmar", 0.95F, 2F, 1F, 5F, 0.85F, 1.35F, 1.0F, 0.04F, 0.00F, 0.25F, 0.11F),
			// Mirewick magnates: measured, upright and haughty (chin up), little lean, unhurried recovery
			new Style("mirewick", 0.90F, 3F, 2F, 8F, 0.70F, 0.80F, 0.0F, 0.00F, -0.10F, 0.20F, 0.07F)};

	/** The kingdom style of a boss from its entity type path ({@code hrafnholt_*}, {@code azmar_*}, {@code mirewick_*}; anything else is the baseline). */
	public static int kindOf(String typePath) {
		if (typePath == null) return ALDERMARK;
		if (typePath.startsWith("hrafnholt_")) return HRAFNHOLT;
		if (typePath.startsWith("azmar_")) return AZMAR;
		if (typePath.startsWith("mirewick_")) return MIREWICK;
		return ALDERMARK;
	}

	/** Sum of absolute differences between two poses (the self-test's distinctness measure). */
	public static float distance(float[] a, float[] b) {
		float d = 0F;
		for (int i = 0; i < N; i++) d += Math.abs(a[i] - b[i]) * (i == CROUCH || i == LIFT ? 0.1F : 1F);
		return d;
	}
}
