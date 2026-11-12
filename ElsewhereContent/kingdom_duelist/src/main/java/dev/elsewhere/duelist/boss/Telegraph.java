package dev.elsewhere.duelist.boss;

import dev.elsewhere.duelist.kit.Kit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wind-up state machine. Every attack of raw 16 or more has a wind-up of at least 8 ticks, signature moves at
 * least 14, area and heavy moves at least 20, each with a pose, a sound and a particle so a player can read the move.
 * A wind-up is cancelled only by a stagger. {@link #check} enforces the minimums when a move is registered.
 */
public final class Telegraph {
	/** ORDINARY: the 8-tick minimum applies from raw 16; SIGNATURE: always 14; HEAVY (area and heavy moves): always 20. */
	public enum Weight {
		ORDINARY(Kit.WINDUP_NORMAL), SIGNATURE(Kit.WINDUP_SIGNATURE), HEAVY(Kit.WINDUP_HEAVY);

		public final int minTicks;

		Weight(int t) {
			this.minTicks = t;
		}
	}

	/**
	 * What the player sees and hears: the boss's own {@link TelegraphStyle} plus the pose. {@code crouch}: the boss crouches.
	 * {@code raised}: the weapon arm lifts overhead. There are no preset cues: every look comes from a boss's style.
	 */
	public record Cue(TelegraphStyle style, boolean crouch, boolean raised) {}

	/** Throws if a move of this weight, raw damage and wind-up breaks the telegraph rule. */
	public static void check(String id, Weight w, float raw, int windup) {
		int min = w == Weight.ORDINARY ? (raw >= Kit.TELEGRAPH_RAW ? Kit.WINDUP_NORMAL : 0) : w.minTicks;
		if (windup < min) throw new IllegalArgumentException("move " + id + " (" + w + ", raw " + raw + ") needs a wind-up of at least " + min + " ticks, has " + windup);
	}

	/** Test audit: every wind-up started while non-null is logged as {id, weight, raw, ticks}. */
	public static java.util.List<Object[]> audit;

	public String id = "", label = "";
	public int total, left;
	public Weight weight = Weight.ORDINARY;
	@Nullable Runnable onFire;
	Cue cue;
	/** True while the boss must stand (no strafe) during the wind-up. */
	public boolean freeze;
	/** 3.0 P4/B4: this wind-up's synced pose byte ({@link MovePose#pack}); the strike bit is added when it fires, 0 after a cancel. */
	public byte pose;
	/** Boss tick of the last strike (for {@link MovePose#synced}). */
	public int struckAt = Integer.MIN_VALUE / 2;
	private boolean seq;

	public boolean active() {
		return left > 0;
	}

	/** 0..1 progress of the wind-up. */
	public float progress() {
		return total <= 0 ? 0F : 1F - (float) left / total;
	}

	void start(HumanBoss b, String id, String label, int ticks, Weight w, float raw, Cue cue, boolean freeze, Runnable onFire) {
		check(id, w, raw, ticks);
		if (audit != null) audit.add(new Object[] {id, w, raw, ticks});
		this.id = id;
		this.label = label;
		this.total = this.left = Math.max(1, ticks);
		this.weight = w;
		this.cue = cue;
		this.freeze = freeze;
		this.onFire = onFire;
		this.seq = !this.seq;
		this.pose = MovePose.pack(b.poseFor(id, cue), this.seq);
		if (b.level() instanceof ServerLevel l) l.playSound(null, b.getX(), b.getY(), b.getZ(), cue.style().sound(), SoundSource.HOSTILE, w == Weight.ORDINARY ? 0.8F : 1.6F, cue.style().pitch() * 0.9F);
		b.telegraphStarted(this);
	}

	/** Returns true on the tick the move fires. */
	boolean tick(HumanBoss b) {
		if (left <= 0) return false;
		if (b.level() instanceof ServerLevel l) {
			TelegraphStyle st = cue.style();
			if (left == total) dev.elsewhere.duelist.foresight.Foresight.refreshNear(l, b);   // B5: one gear re-check per wind-up
			if (left % 2 == 0 || weight != Weight.ORDINARY) dev.elsewhere.duelist.foresight.Foresight.hint(() -> st.emit(l, new Vec3(b.getX(), b.getY(), b.getZ()), cue.crouch() ? 0.9 : b.getBbHeight(), weight == Weight.ORDINARY ? 1 : 2, total - left));
			if (weight != Weight.ORDINARY && left == total / 2) l.playSound(null, b.getX(), b.getY(), b.getZ(), st.sound(), SoundSource.HOSTILE, 1.0F, st.pitch() * 1.2F);
		}
		if (--left == 0) {
			pose = (byte) (pose | MovePose.STRIKE);
			struckAt = b.tickCount;
			Runnable r = onFire;
			onFire = null;
			if (r != null) r.run();
			return true;
		}
		return false;
	}

	void cancel() {
		left = 0;
		onFire = null;
		label = "";
		pose = 0;
	}

	public Cue cue() {
		return cue;
	}
}
