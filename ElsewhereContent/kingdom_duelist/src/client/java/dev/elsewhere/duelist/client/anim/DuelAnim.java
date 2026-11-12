package dev.elsewhere.duelist.client.anim;

import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.MovePose;
import dev.elsewhere.duelist.boss.PoseTable;
import dev.elsewhere.duelist.client.DuelistRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 3.0 (P4/B4): keyframed move animations for the human bosses. The server syncs one pose byte per boss ({@link MovePose}:
 * key, strike bit, sequence bit), changed only when a wind-up starts, when the move fires and when it clears. Each change
 * starts a clock here: the wind-up pose eases in and builds tension (drum beats and trembles on top for the gestures),
 * the strike snaps with an ease-out-back, holds and eases back; a stagger (byte cleared without a strike) eases straight
 * out. Each kingdom moves its own way ({@link PoseTable#STYLES}: amplitude, timing, stance, lean, twist, head, breath).
 * A hurt flinch and the breath run on top. Pure model math on the render state: no packets, no ticking, nothing past 48
 * blocks. Kill switch: {@code "bossAnimations3": false} in {@code config/elsewhere-client.json}.
 */
public final class DuelAnim {
	private DuelAnim() {}

	public static final int N = PoseTable.N;
	private static final int WX = PoseTable.WX, WY = PoseTable.WY, WZ = PoseTable.WZ, OX = PoseTable.OX, OY = PoseTable.OY, OZ = PoseTable.OZ,
			TWIST = PoseTable.TWIST, LEAN = PoseTable.LEAN, CROUCH = PoseTable.CROUCH, LEG_W = PoseTable.LEG_W, LEG_O = PoseTable.LEG_O, HEAD = PoseTable.HEAD,
			LIFT = PoseTable.LIFT;
	private static final double LOD_SQ = 48 * 48;

	/** Per-boss clock (client only; weak keys so unloaded bosses go away). */
	private static final class Track {
		byte last;
		byte key, strikeKey, cancelKey;
		boolean winding;
		float start, total = 1, strikeAt = -100, cancelAt = -100;
		PoseTable.Style style;
		int kind = -1;
	}

	private static final Map<HumanBoss, Track> TRACKS = new WeakHashMap<>();

	// ------------------------------------------------------------------ kill switches

	private static Boolean enabled, aura;

	/** One client key, {@code bossAnimations3} in {@code config/elsewhere-client.json} (default on); {@code -Delsewhere.bossAnimations3=false} also works. */
	public static boolean enabled() {
		Boolean e = enabled;
		if (e == null) enabled = e = readKey("bossAnimations3");
		return e;
	}

	/** The phase aura ({@link PhaseAuraLayer}): {@code bossAura3} in the same file (default on), or {@code -Delsewhere.bossAura3=false}. */
	public static boolean auraEnabled() {
		Boolean a = aura;
		if (a == null) aura = a = readKey("bossAura3");
		return a;
	}

	private static boolean readKey(String key) {
		boolean on = !"false".equals(System.getProperty("elsewhere." + key));
		try {
			Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
			if (on && Files.exists(p)) {
				com.google.gson.JsonElement j = com.google.gson.JsonParser.parseString(Files.readString(p));
				if (j.isJsonObject() && j.getAsJsonObject().has(key)) on = j.getAsJsonObject().get(key).getAsBoolean();
			}
		} catch (Exception ignored) {
		}
		return on;
	}

	/** For tests. */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	// ------------------------------------------------------------------ extract (render thread, once per boss per frame)

	public static void extract(HumanBoss e, DuelistRenderer.BossRenderState s, float partialTicks) {
		s.animOn = false;
		s.animW = 0F;
		s.hurtW = 0F;
		s.auraKind = -1;
		s.auraLevel = 0;
		boolean anim = enabled();
		if (!anim && !auraEnabled()) return;
		Track t = TRACKS.computeIfAbsent(e, k -> new Track());
		if (t.kind < 0) {
			t.kind = PoseTable.kindOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
			t.style = PoseTable.STYLES[t.kind];
		}
		if (e.isDeadOrDying() || s.distanceToCameraSq > LOD_SQ) return;
		if (auraEnabled()) {
			s.auraKind = t.kind;
			s.auraLevel = Math.min(2, Math.max(0, e.syncedPhase()));
		}
		if (!anim) return;
		float age = e.tickCount + partialTicks;
		byte b = e.poseByte();
		if (b != t.last) {
			byte k = MovePose.key(b);
			if (k == MovePose.NONE) {
				if (t.winding && t.key != MovePose.NONE) {
					t.cancelKey = t.key; // cleared without a strike: a stagger or a cancel, ease out of the wind-up
					t.cancelAt = age;
				}
				t.winding = false;
				t.key = MovePose.NONE;
			} else if (MovePose.struck(b)) {
				t.strikeKey = k;
				t.strikeAt = age;
				t.cancelKey = MovePose.NONE;
				t.winding = false;
				t.key = MovePose.NONE;
			} else {
				t.key = k;
				t.start = age;
				t.total = Math.max(1, e.actionTotal());
				t.winding = true;
				t.strikeKey = MovePose.NONE;
				t.cancelKey = MovePose.NONE;
			}
			t.last = b;
		}
		s.animOn = true;
		s.animStyle = t.kind;
		PoseTable.Style st = t.style;
		float[] v = s.animV;
		if (t.winding && t.key != MovePose.NONE) {
			// wind-up: ease into the pose within a few ticks, then build tension until the move fires
			float in = Math.max(st.snap(), Math.min(8F, t.total * 0.5F));
			float u = age - t.start;
			s.animW = easeOutCubic(Mth.clamp(u / in, 0F, 1F));
			float tension = 1F + 0.12F * Mth.clamp(u / t.total, 0F, 1F);
			float[] p = PoseTable.POSES[t.key][0];
			for (int i = 0; i < N; i++) v[i] = p[i];
			scale(v, st, st.amp() * tension);
			float beat = PoseTable.BEAT[t.key];
			if (beat > 0F) {
				float osc = Mth.sin(u * PoseTable.BEAT_RATE[t.key]) * beat * s.animW;
				v[WX] += osc;
				v[OX] -= osc;
				v[HEAD] += osc * 0.25F;
			}
			s.animKey = t.key;
		} else if (t.strikeKey != MovePose.NONE) {
			float u = age - t.strikeAt;
			float snap = st.snap(), hold = st.hold(), rec = st.recover();
			if (u >= snap + hold + rec) {
				t.strikeKey = MovePose.NONE;
			} else {
				float[] a = PoseTable.POSES[t.strikeKey][0], c = PoseTable.POSES[t.strikeKey][1];
				float f = u < snap ? easeOutBack(Mth.clamp(u / snap, 0F, 1F)) : 1F;
				for (int i = 0; i < N; i++) v[i] = Mth.lerp(f, a[i], c[i]);
				scale(v, st, st.amp());
				s.animW = u < snap + hold ? 1F : 1F - easeInOutSine((u - snap - hold) / rec);
				s.animKey = t.strikeKey;
			}
		} else if (t.cancelKey != MovePose.NONE) {
			float u = age - t.cancelAt;
			if (u >= st.recover()) {
				t.cancelKey = MovePose.NONE;
			} else {
				float[] p = PoseTable.POSES[t.cancelKey][0];
				for (int i = 0; i < N; i++) v[i] = p[i];
				scale(v, st, st.amp());
				s.animW = 1F - easeInOutSine(u / st.recover());
				s.animKey = t.cancelKey;
			}
		}
		if (e.hurtTime > 0) {
			float h = 1F - Mth.clamp((e.hurtTime - partialTicks) / 10F, 0F, 1F); // 0 at the hit, 1 when over
			s.hurtW = h < 0.2F ? easeOutCubic(h / 0.2F) : 1F - easeInOutSine((h - 0.2F) / 0.8F);
		}
	}

	/** The kingdom's reach on the arms and lean, its twist, plus its guard (crouch) and head carriage. */
	private static void scale(float[] v, PoseTable.Style st, float amp) {
		v[WX] *= amp;
		v[OX] *= amp;
		v[LEAN] *= amp * st.lean();
		v[TWIST] *= st.twist();
		v[CROUCH] += st.crouch();
	}

	// ------------------------------------------------------------------ apply (end of PlayerModel.setupAnim)

	public static void apply(PlayerModel m, DuelistRenderer.BossRenderState s) {
		float w = s.animW;
		float[] v = s.animV;
		PoseTable.Style st = PoseTable.STYLES[Math.clamp(s.animStyle, 0, PoseTable.STYLES.length - 1)];
		boolean left = s.mainArm == HumanoidArm.LEFT;
		float mir = left ? -1F : 1F;
		ModelPart wa = left ? m.leftArm : m.rightArm, oa = left ? m.rightArm : m.leftArm;
		ModelPart wl = left ? m.leftLeg : m.rightLeg, ol = left ? m.rightLeg : m.leftLeg;
		float twist = 0F, lean = 0F, crouch = 0F, lift = 0F;
		if (w > 0F) {
			wa.xRot = Mth.lerp(w, wa.xRot, v[WX]);
			wa.yRot = Mth.lerp(w, wa.yRot, mir * v[WY]);
			wa.zRot = Mth.lerp(w, wa.zRot, mir * v[WZ]);
			oa.xRot = Mth.lerp(w, oa.xRot, v[OX]);
			oa.yRot = Mth.lerp(w, oa.yRot, mir * v[OY]);
			oa.zRot = Mth.lerp(w, oa.zRot, mir * v[OZ]);
			wl.xRot = Mth.lerp(w, wl.xRot, v[LEG_W]);
			ol.xRot = Mth.lerp(w, ol.xRot, v[LEG_O]);
			// the kingdom's stance: legs splay a little while committed to a move
			wl.zRot += mir * st.stance() * w;
			ol.zRot -= mir * st.stance() * w;
			m.head.xRot += v[HEAD] * w;
			twist = mir * v[TWIST] * w;
			lean = v[LEAN] * w;
			crouch = v[CROUCH] * w;
			lift = v[LIFT] * w;
		}
		float hw = s.hurtW;
		if (hw > 0F) {
			// flinch: torso rocks back, arms fly up a little, head snaps back
			lean -= 0.28F * hw;
			m.head.xRot -= 0.30F * hw;
			wa.xRot -= 0.35F * hw;
			oa.xRot -= 0.35F * hw;
			wa.zRot += mir * 0.20F * hw;
			oa.zRot -= mir * 0.20F * hw;
		}
		// head carriage of the kingdom (Mirewick's chin up, Hrafnholt's lowered brow) and the slow breath
		m.head.xRot += st.head();
		float breath = Mth.sin(s.ageInTicks * st.breathRate()) * st.breath();
		if (twist != 0F) {
			m.body.yRot += twist;
			rotateY(m.rightArm, twist);
			rotateY(m.leftArm, twist);
			m.rightArm.yRot += twist;
			m.leftArm.yRot += twist;
		}
		if (lean != 0F || crouch != 0F) {
			float cos = Mth.cos(lean), sin = Mth.sin(lean);
			float dy = 12F * (1F - cos) + crouch, dz = -12F * sin;
			m.body.xRot += lean;
			m.body.y += dy;
			m.body.z += dz;
			m.head.y += dy;
			m.head.z += dz;
			float ay = dy + 2F * (cos - 1F), az = dz + 2F * sin;
			m.rightArm.y += ay;
			m.rightArm.z += az;
			m.leftArm.y += ay;
			m.leftArm.z += az;
			m.rightArm.xRot += lean;
			m.leftArm.xRot += lean;
		}
		m.head.y += breath * 0.5F;
		m.rightArm.y += breath;
		m.leftArm.y += breath;
		if (lift != 0F) {
			m.body.y += lift;
			m.head.y += lift;
			m.rightArm.y += lift;
			m.leftArm.y += lift;
			m.rightLeg.y += lift;
			m.leftLeg.y += lift;
		}
	}

	/** Moves a shoulder pivot around the torso's vertical axis (the same rotation vanilla uses for the attack swing). */
	private static void rotateY(ModelPart arm, float a) {
		float c = Mth.cos(a), sn = Mth.sin(a);
		float x = arm.x, z = arm.z;
		arm.x = x * c + z * sn;
		arm.z = -x * sn + z * c;
	}

	static float easeOutCubic(float x) {
		float y = 1F - x;
		return 1F - y * y * y;
	}

	static float easeOutBack(float x) {
		float c1 = 1.4F, c3 = c1 + 1F;
		float y = x - 1F;
		return 1F + c3 * y * y * y + c1 * y * y;
	}

	static float easeInOutSine(float x) {
		return -(Mth.cos((float) Math.PI * Mth.clamp(x, 0F, 1F)) - 1F) / 2F;
	}
}
