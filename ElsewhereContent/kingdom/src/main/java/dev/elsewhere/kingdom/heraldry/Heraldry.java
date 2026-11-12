package dev.elsewhere.kingdom.heraldry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.KingdomContent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 polish P10: kingdom heraldry on soldiers. Data and maths only (no client classes), shared by the client layers
 * ({@code client.heraldry.SoldierHeraldry}, {@code TabardLayer}, {@code SoldierCapeLayer}) and the self-test.
 * <ul>
 * <li><b>Emblem</b>: a full-colour 64x32 overlay in the tabard's UV layout (chest and back charge, belt, skirt hem), drawn
 * on the tabard's own model right after the tinted tabard. Under an emblem the tabard uses {@link #PLAIN_TABARD} (the
 * white tabard without the generic grey tower motif). Aldermark: tower under a crown on a red chief; Hrafnholt: a brown
 * raven in profile on green (Norse raven banner); Azmar: winged scarab under a sun disc with an usekh collar; Mirewick: a
 * gold lantern with crimson glass and a crimson sash.</li>
 * <li><b>Cape</b>: officers (rank 1) wear a short cape, captains (rank 2) a long one with the emblem; one baked 8-segment
 * quad strip hung from the shoulders, waved by a travelling sine (see {@link #jointDeg}).</li>
 * <li>A fallen kingdom's remnant ({@code <kingdom>_remnant}) keeps its heraldry, faded by {@link #REMNANT_TINT}. Other
 * factions (rebels, free companies, data-pack factions) keep the plain tabard and no cape.</li>
 * </ul>
 * Kill switches: {@code "polishEmblems": false} and {@code "polishCapes": false} in {@code config/elsewhere-client.json}.
 */
public final class Heraldry {
	private Heraldry() {}

	public static final String DIR = "textures/entity/kingdom/heraldry/";
	/** The tinted white tabard (64x32), with its generic grey tower motif. */
	public static final Identifier BASE_TABARD = KingdomContent.id("textures/entity/kingdom/tabard.png");
	/** The same tabard without the motif, drawn under an emblem. */
	public static final Identifier PLAIN_TABARD = KingdomContent.id(DIR + "tabard_plain.png");
	/** ARGB multiplier for a remnant's faded heraldry (the remnant tabard itself is grey). */
	public static final int REMNANT_TINT = 0xFFA8A29A;
	public static final int NO_TINT = 0xFFFFFFFF;
	public static final String REMNANT_SUFFIX = "_remnant";

	public enum Kingdom {
		ALDERMARK("aldermark"), HRAFNHOLT("hrafnholt"), AZMAR("azmar"), MIREWICK("mirewick");

		public final String path;
		/** 64x32 tabard overlay. */
		public final Identifier emblem;
		/** 32x16 captain cape (long, with the emblem) and officer cape (short). Box UV: lining u0-9, outer u10-19. */
		public final Identifier cape, capeShort;
		final Style style, remnant;

		Kingdom(String path) {
			this.path = path;
			this.emblem = KingdomContent.id(DIR + path + "_emblem.png");
			this.cape = KingdomContent.id(DIR + path + "_cape.png");
			this.capeShort = KingdomContent.id(DIR + path + "_cape_short.png");
			this.style = new Style(this, false);
			this.remnant = new Style(this, true);
		}
	}

	/** One faction's heraldry: its kingdom, and whether it is that kingdom's remnant (faded). */
	public record Style(Kingdom kingdom, boolean remnant) {
		public int tint() {
			return remnant ? REMNANT_TINT : NO_TINT;
		}

		/** The cape for a soldier rank (0 rank and file: none, 1 officer: short, 2 captain: long). */
		public @Nullable Identifier cape(int rank) {
			return rank >= 2 ? kingdom.cape : rank == 1 ? kingdom.capeShort : null;
		}
	}

	/**
	 * Heraldry for a synced faction id ({@code KingdomSoldier.syncedFaction()}, e.g. {@code "elsewhere:hrafnholt"} or
	 * {@code "elsewhere:hrafnholt_remnant"}), or null when the faction has none. Never allocates for a kingdom id.
	 */
	public static @Nullable Style resolve(@Nullable String factionId) {
		if (factionId == null) return null;
		int c = factionId.indexOf(':');
		if (c != KingdomContent.NS.length() || !factionId.startsWith(KingdomContent.NS)) return null;
		for (Kingdom k : Kingdom.values()) {
			if (!factionId.startsWith(k.path, c + 1)) continue;
			int end = c + 1 + k.path.length();
			if (factionId.length() == end) return k.style;
			if (factionId.length() == end + REMNANT_SUFFIX.length() && factionId.startsWith(REMNANT_SUFFIX, end)) return k.remnant;
		}
		return null;
	}

	// ---- distance LOD (POLISH.md section 3 rule 4: full under 24 blocks, reduced under 48-64, none beyond 64) ------
	// The tabard itself is gated first by the soldier-detail tabard distance (KingdomClientConfig); these cap it further.

	/** No emblem beyond this many blocks (its pixels are sub-pixel there). */
	public static final double EMBLEM_RANGE = 64;
	/** No cape beyond this many blocks, and none at all on LOW soldier detail. */
	public static final double CAPE_RANGE = 64;
	/** The waving 8-segment cape within this many blocks; beyond it the one-piece cape (leans, no wave). */
	public static final double WAVE_RANGE = 24;
	public static final int CAPE_NONE = 0, CAPE_FLAT = 1, CAPE_WAVE = 2;

	public static boolean emblemVisible(double distSq) {
		return distSq < EMBLEM_RANGE * EMBLEM_RANGE;
	}

	/** {@link #CAPE_NONE}, {@link #CAPE_FLAT} or {@link #CAPE_WAVE} for a soldier at this squared distance. */
	public static int capeLod(double distSq, boolean lowDetail) {
		if (lowDetail || distSq >= CAPE_RANGE * CAPE_RANGE) return CAPE_NONE;
		return distSq < WAVE_RANGE * WAVE_RANGE ? CAPE_WAVE : CAPE_FLAT;
	}

	// ---- cape geometry (model pixels, body space: y down from the shoulder line, z backwards) ------------------------

	public static final int CAPE_WIDTH = 10, CAPE_HEIGHT = 16, CAPE_SEGMENTS = 8, CAPE_TEX_W = 32, CAPE_TEX_H = 16;
	/** Top edge of the cape: just under the shoulder line, behind the tabard (body back z 2, armor 3.0, tabard 3.08). */
	public static final float CAPE_Y = -0.6F, CAPE_Z = 3.3F;

	// ---- cape motion (degrees; positive swings the hem backwards, away from the legs) ------------------------------

	/** Rest angle of the cape from vertical. */
	public static final float REST_DEG = 5F;
	/** Extra lift on horseback (the hem clears the horse's back). */
	public static final float RIDING_DEG = 18F;
	/** Idle breeze phase speed, radians per tick; walking adds phase through the walk distance. */
	public static final float BREEZE = 0.16F, STRIDE = 0.55F, WAVELENGTH = 0.85F;

	/** Backward lean (vanilla's capeLean) from the smoothed walk speed (0..1) and the share of it that is forwards (-1..1). */
	public static float lean(float speed, float forward) {
		float s = Mth.clamp(speed, 0F, 1F);
		return s * (18F + 34F * Mth.clamp(forward, 0F, 1F));
	}

	/** Sideways swing (vanilla's capeLean2): the hem trails away from a sideways step. */
	public static float sideLean(float speed, float side) {
		return Mth.clamp(Mth.clamp(speed, 0F, 1F) * side * 20F, -20F, 20F);
	}

	/** Lift from vertical motion in blocks per tick: falling lifts the hem, climbing drops it a little. */
	public static float flap(float dy) {
		return Mth.clamp(-dy * 60F, -6F, 24F);
	}

	/** Angle of the top segment from vertical. */
	public static float rootDeg(float lean, float flap, boolean riding) {
		return Mth.clamp(REST_DEG + lean * 0.5F + flap + (riding ? RIDING_DEG : 0F), 2F, 80F);
	}

	/**
	 * Bend at joint {@code joint} (1..segments-1) between two segments: a travelling sine wave whose phase advances with
	 * time (breeze) and with the walk distance (stride), so a speed change never jumps the phase; the amplitude grows with
	 * speed and towards the hem, plus a small backward drag while moving.
	 *
	 * @param t ticks (render age plus a per-soldier phase offset)
	 * @param walk walk-animation distance (the vehicle's when riding)
	 * @param speed smoothed walk speed 0..1
	 */
	public static float jointDeg(int joint, int segments, float t, float walk, float speed) {
		float s = Mth.clamp(speed, 0F, 1F);
		float amp = 1.6F + 5.0F * s;
		float taper = 0.45F + 0.55F * joint / (float) Math.max(1, segments - 1);
		float phase = BREEZE * t + STRIDE * walk - WAVELENGTH * joint;
		return amp * taper * Mth.sin(phase) + 2.2F * s;
	}

	/** The one-piece far cape: the chord of the near cape's mean curve (its drag, without the wave). */
	public static float flatDeg(float rootDeg, float speed) {
		return rootDeg + 2.2F * Mth.clamp(speed, 0F, 1F) * (CAPE_SEGMENTS - 1) * 0.5F;
	}

	// ---- kill switches -----------------------------------------------------------------------------------------------

	public static final String EMBLEM_KEY = "polishEmblems", CAPE_KEY = "polishCapes";

	/** {emblems, capes} from the text of config/elsewhere-client.json; both default on (missing file, key or bad JSON). */
	public static boolean[] switches(@Nullable String json) {
		boolean emblems = true, capes = true;
		if (json != null && !json.isBlank()) {
			try {
				JsonObject o = JsonParser.parseString(json).getAsJsonObject();
				if (o.has(EMBLEM_KEY)) emblems = o.get(EMBLEM_KEY).getAsBoolean();
				if (o.has(CAPE_KEY)) capes = o.get(CAPE_KEY).getAsBoolean();
			} catch (RuntimeException e) {
				emblems = capes = true;
			}
		}
		return new boolean[] {emblems, capes};
	}
}
