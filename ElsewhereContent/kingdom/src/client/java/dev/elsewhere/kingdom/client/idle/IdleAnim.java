package dev.elsewhere.kingdom.client.idle;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.idle.IdlePoses;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 polish P9 (s-p9-idle): client animation of the synced idle/work pose of kingdom soldiers and serfs (and any NPC whose
 * render state is a {@link Holder}). The server only changes one byte when a routine activity starts or ends (see
 * {@code idle.IdlePoses}); everything here is pure model math from game time, run at render extraction and at the end of
 * {@code HumanoidModel.setupAnim} (so the armor, tabard and held items follow the same pose). No packets, no ticking.
 * <p>Each kingdom does it its own way (lore): Aldermark sits straight with hands on the knees, salutes hand-to-brow (sword
 * salute when armed) and sweeps the yard; Hrafnholt sits wide-legged and leaning in, salutes with the weapon thrust up and a
 * fist on the chest, chops wood and drinks from a horn; Azmar sits cross-legged, bows with a hand on the heart, raises both
 * arms to the sun at the dawn rite, carries loads on the head and lounges hands-behind-head at siesta; Mirewick lounges back on
 * its elbows, tips its hat, stands at a slouch and polishes its gilded kit. Serfs work by tier: the Aldermark serf lays and
 * tamps stone, the Hrafnholt thrall chops, the Azmar porter lifts loads to the head, the Mirewick peat-cutter works a spade.
 * <p>Gating: no pose while dead, riding, hurt, swinging, using an item or aggressive, and the seated/kneeling/working poses
 * only while standing still (walking ones such as carry, torch and drink keep the legs walking). LOD: nothing beyond 48 blocks,
 * props only within 24. Kill switch: {@code "polishIdlePoses": false} in {@code config/elsewhere-client.json}.
 */
public final class IdleAnim {
	private IdleAnim() {}

	/** Render states that carry an idle track (soldiers, serfs, townsfolk). */
	public interface Holder {
		@Nullable Track idleTrack();

		void idleTrack(@Nullable Track t);
	}

	// animation ids: the routine ids (RoutinePoses.Pose), plus REST (a resting soldier) and the serf work ids
	static final int NONE = 0, SIT = 1, KNEEL = 2, SALUTE = 3, WORK = 4, SPAR = 5, CHEER = 6, TORCH = 7, ATTENTION = 8, CARRY = 9, DRINK = 10,
		LOUNGE = 11, PRAY = 12, REST = 13, S_CARRY = IdlePoses.SERF_CARRY, S_WORK = IdlePoses.SERF_WORK;
	/** Styles: one per kingdom (serf tiers map onto their kingdom). */
	public static final int ALDERMARK = 0, HRAFNHOLT = 1, AZMAR = 2, MIREWICK = 3, OTHER = 4;
	static final int W_SWINGS = 0, W_SWEEP = 1, W_CHOP = 2, W_POLISH = 3, W_DRAW = 4, W_MASON = 5, W_LIFT = 6, W_SPADE = 7;
	/** Props drawn by {@link IdlePropLayer}. */
	static final int P_NONE = 0, P_BROOM = 1, P_LOAD_HANDS = 2, P_LOAD_HEAD = 3;

	static final double LOD_SQ = 48 * 48, PROP_SQ = 24 * 24;
	private static final float FADE = 6F, FADE_FAST = 3F;
	private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

	/** Per-entity animation clock (client only, weak keys) plus this frame's resolved values. */
	public static final class Track {
		int cur, prev, work, prevWork;
		float curStart = -1000F, outStart = -1000F, prevW0, fade = FADE;
		boolean moving, armed, prevArmed;
		// this frame
		boolean on;
		int style, seed;
		boolean mirror;
		float age, wIn, wOut, drop;
		HumanoidArm mainArm = HumanoidArm.RIGHT;
		int prop;
		boolean propOn;
		final ItemStackRenderState propA = new ItemStackRenderState(), propB = new ItemStackRenderState();

		/** True when this frame applies a pose (read by the model mixin). */
		public boolean isOn() {
			return on;
		}

		/** The resolved pose id this frame (0 = none); for tests and debugging. */
		public int pose() {
			return cur;
		}
	}

	// ------------------------------------------------------------------ kill switch

	private static Boolean enabled;

	/** {@code polishIdlePoses} in {@code config/elsewhere-client.json} (default on); {@code -Delsewhere.polishIdlePoses=false} also works. */
	public static boolean enabled() {
		Boolean e = enabled;
		if (e == null) {
			boolean on = !"false".equals(System.getProperty("elsewhere.polishIdlePoses"));
			try {
				Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
				if (on && Files.exists(p)) {
					com.google.gson.JsonElement j = com.google.gson.JsonParser.parseString(Files.readString(p));
					if (j.isJsonObject() && j.getAsJsonObject().has("polishIdlePoses")) on = j.getAsJsonObject().get("polishIdlePoses").getAsBoolean();
				}
			} catch (Exception ignored) {
			}
			enabled = e = on;
		}
		return e;
	}

	public static void setEnabled(boolean on) {
		enabled = on;
	}

	// ------------------------------------------------------------------ styles

	/** Kingdom style from a faction id ("elsewhere:hrafnholt"); Free Companies, rebels and test factions use Aldermark's manner. */
	public static int styleOfFaction(String factionId) {
		if (factionId.endsWith("hrafnholt")) return HRAFNHOLT;
		if (factionId.endsWith("azmar")) return AZMAR;
		if (factionId.endsWith("mirewick")) return MIREWICK;
		if (factionId.endsWith("aldermark")) return ALDERMARK;
		return OTHER;
	}

	/** Kingdom style of a serf tier (serf, thrall, porter, peat_cutter). */
	public static int styleOfSerf(String variant) {
		return switch (variant) {
			case "thrall" -> HRAFNHOLT;
			case "porter" -> AZMAR;
			case "peat_cutter" -> MIREWICK;
			default -> ALDERMARK;
		};
	}

	// ------------------------------------------------------------------ extract (render thread, once per entity per frame)

	/**
	 * Soldier: the synced pose, or REST (seated at the fire, dozing) while the night routine keeps it crouched in place
	 * (soldiers crouch for nothing else; the resting flag itself is server-only, the crouch is synced).
	 */
	public static void extractSoldier(KingdomSoldier e, HumanoidRenderState s, Holder h, ItemModelResolver r) {
		int pose = e.idlePose();
		if (pose == NONE && s.isCrouching) pose = REST;
		extract(e, s, h, pose, styleOfFaction(e.syncedFaction()), r);
	}

	/**
	 * Resolves the pose for this frame: gating, cross-fade, prop and held-item swaps. Call at the end of the renderer's
	 * {@code extractRenderState}. {@code pose} is the synced byte (or REST), {@code style} the kingdom style.
	 */
	public static void extract(LivingEntity e, HumanoidRenderState s, Holder h, int pose, int style, ItemModelResolver r) {
		h.idleTrack(null);
		if (!enabled()) return;
		Track t = TRACKS.get(e);
		if (t == null) {
			if (pose == NONE) return; // the common case: no pose, nothing kept, nothing allocated
			t = new Track();
			t.cur = pose;
			t.curStart = s.ageInTicks - 200F; // first seen already posed: fully in, one-shot moves (salute) done
			TRACKS.put(e, t);
		}
		float age = s.ageInTicks;
		// gating
		boolean combat = e.isDeadOrDying() || s.isPassenger || e.hurtTime > 0 || s.isUsingItem || s.swingAnimation > 0F
			|| (e instanceof Mob m && m.isAggressive()) || s.isFallFlying || s.swimAmount > 0F;
		if (t.moving ? s.walkAnimationSpeed < 0.08F : s.walkAnimationSpeed > 0.16F) t.moving = !t.moving; // hysteresis
		int want = pose;
		if (combat || (t.moving && !walkable(want))) want = NONE;
		ItemStack main = e.getMainHandItem();
		boolean armed = !main.isEmpty();
		int work = workKind(want, style, main);
		if (want != t.cur || (want == WORK && work != t.work)) {
			float wNow = weightIn(t, age);
			t.prev = t.cur;
			t.prevWork = t.work;
			t.prevArmed = t.armed;
			t.prevW0 = Math.max(wNow, weightOut(t, age));
			t.outStart = age;
			t.cur = want;
			t.curStart = age;
			t.fade = combat ? FADE_FAST : FADE;
		}
		t.work = work;
		t.armed = armed;
		t.age = age;
		t.style = style;
		t.seed = e.getId() * 7919 & 0xFFFF;
		t.mainArm = s.mainArm;
		t.mirror = s.mainArm == HumanoidArm.LEFT;
		t.wIn = weightIn(t, age);
		t.wOut = weightOut(t, age);
		t.prop = P_NONE;
		t.propOn = false;
		t.on = s.distanceToCameraSq <= LOD_SQ && (t.wIn > 0F || t.wOut > 0F);
		if (!t.on) return;
		h.idleTrack(t);
		// what the dominant pose does with the hands (decided at mid-fade so items never pop on a still model)
		int dom = t.wIn >= 0.5F || t.wOut <= 0F ? t.cur : t.prev;
		int domWork = dom == t.cur ? t.work : t.prevWork;
		boolean near = s.distanceToCameraSq <= PROP_SQ;
		applyHands(e, s, t, dom, domWork, style, r, near);
		t.drop = dropOf(t.prev, t.style) * t.wOut;
		t.drop = Mth.lerp(t.wIn, t.drop, dropOf(t.cur, t.style));
		if (dom == REST || (t.wIn > 0F && t.cur == REST)) s.isCrouching = false; // seated instead of the vanilla crouch (no -2 px offset)
		if (s.nameTagAttachment != null && t.drop > 0F) s.nameTagAttachment = s.nameTagAttachment.subtract(0.0, t.drop / 16.0, 0.0);
	}

	private static float weightIn(Track t, float age) {
		return t.cur == NONE ? 0F : easeInOut(Mth.clamp((age - t.curStart) / t.fade, 0F, 1F));
	}

	private static float weightOut(Track t, float age) {
		return t.prev == NONE ? 0F : t.prevW0 * (1F - easeInOut(Mth.clamp((age - t.outStart) / t.fade, 0F, 1F)));
	}

	/** Poses that keep the legs walking (upper body only). */
	static boolean walkable(int pose) {
		return pose == CARRY || pose == TORCH || pose == DRINK || pose == CHEER || pose == S_CARRY;
	}

	static int workKind(int pose, int style, ItemStack main) {
		if (pose == S_WORK) return switch (style) {
			case HRAFNHOLT -> W_CHOP;
			case AZMAR -> W_LIFT;
			case MIREWICK -> W_SPADE;
			default -> W_MASON;
		};
		if (pose != WORK) return W_SWINGS;
		if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) return W_DRAW; // archers at the butts
		return switch (style) {
			case HRAFNHOLT -> W_CHOP;
			case AZMAR -> W_SWEEP;
			case MIREWICK -> W_POLISH;
			default -> W_SWEEP;
		};
	}

	static float dropOf(int pose, int style) {
		return switch (pose) {
			case SIT, REST -> style == AZMAR ? 11F : 10F;
			case LOUNGE -> 10.5F;
			case KNEEL, PRAY -> 6F;
			default -> 0F;
		};
	}

	// ------------------------------------------------------------------ hands and props

	private static ItemStack HORN, ALE, TORCH_S, LANTERN_S, BROOM_POLE, BROOM_HEAD, AXE, SPADE;
	private static final ItemStack[] LOADS = new ItemStack[5];

	private static void stacks() {
		if (HORN != null) return;
		HORN = new ItemStack(Items.GOAT_HORN);
		ALE = new ItemStack(Items.HONEY_BOTTLE);
		TORCH_S = new ItemStack(Items.TORCH);
		LANTERN_S = new ItemStack(Items.LANTERN);
		BROOM_POLE = new ItemStack(Items.STRIPPED_SPRUCE_LOG);
		BROOM_HEAD = new ItemStack(Items.HAY_BLOCK);
		AXE = new ItemStack(Items.STONE_AXE);
		SPADE = new ItemStack(Items.WOODEN_SHOVEL);
		LOADS[ALDERMARK] = new ItemStack(Items.BARREL); // provisions for the keep
		LOADS[HRAFNHOLT] = new ItemStack(Items.SPRUCE_LOG); // timber for the shipyard
		LOADS[AZMAR] = new ItemStack(Items.DECORATED_POT); // a jar, carried on the head
		LOADS[MIREWICK] = new ItemStack(Items.CHEST); // the Counting-House strongbox
		LOADS[OTHER] = LOADS[ALDERMARK];
	}

	private static void applyHands(LivingEntity e, HumanoidRenderState s, Track t, int dom, int work, int style, ItemModelResolver r, boolean near) {
		boolean right = s.mainArm == HumanoidArm.RIGHT;
		switch (dom) {
			case SIT, REST, KNEEL, PRAY, LOUNGE -> hide(s, true, true); // weapon and shield laid aside
			case CARRY -> {
				hide(s, true, true);
				if (near) {
					stacks();
					t.prop = style == AZMAR ? P_LOAD_HEAD : P_LOAD_HANDS;
					r.updateForLiving(t.propA, LOADS[Math.min(style, OTHER)], ItemDisplayContext.NONE, e);
					t.propOn = true;
				}
			}
			case DRINK -> {
				if (near) {
					stacks();
					swap(s, true, style == HRAFNHOLT ? HORN : ALE, r, e, right);
				} else hide(s, true, false);
			}
			case TORCH -> {
				ItemStack off = e.getOffhandItem();
				if (near && !off.is(Items.LANTERN) && !off.is(Items.TORCH)) {
					stacks();
					swap(s, false, style == MIREWICK ? LANTERN_S : TORCH_S, r, e, right);
				}
			}
			case WORK, S_WORK -> {
				if (work == W_SWEEP) {
					hide(s, true, true);
					if (near) {
						stacks();
						t.prop = P_BROOM;
						r.updateForLiving(t.propA, BROOM_POLE, ItemDisplayContext.NONE, e);
						r.updateForLiving(t.propB, BROOM_HEAD, ItemDisplayContext.NONE, e);
						t.propOn = true;
					}
				} else if (work == W_POLISH) {
					hide(s, false, true);
				} else if (dom == S_WORK && (work == W_CHOP || work == W_SPADE)) {
					if (near) {
						stacks();
						swap(s, true, work == W_CHOP ? AXE : SPADE, r, e, right);
					}
				}
			}
			case S_CARRY -> {
				if (style == AZMAR && !e.getMainHandItem().isEmpty()) {
					hide(s, true, true);
					if (near) {
						t.prop = P_LOAD_HEAD;
						r.updateForLiving(t.propA, e.getMainHandItem(), ItemDisplayContext.NONE, e);
						t.propOn = true;
					}
				}
			}
			default -> {
			}
		}
	}

	private static void hide(HumanoidRenderState s, boolean main, boolean off) {
		boolean right = s.mainArm == HumanoidArm.RIGHT;
		if (main) {
			if (right) clearRight(s);
			else clearLeft(s);
		}
		if (off) {
			if (right) clearLeft(s);
			else clearRight(s);
		}
	}

	private static void clearRight(HumanoidRenderState s) {
		s.rightHandItemState.clear();
		s.rightHandItemStack = ItemStack.EMPTY;
		s.rightArmPose = HumanoidModel.ArmPose.EMPTY;
	}

	private static void clearLeft(HumanoidRenderState s) {
		s.leftHandItemState.clear();
		s.leftHandItemStack = ItemStack.EMPTY;
		s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
	}

	private static void swap(HumanoidRenderState s, boolean main, ItemStack stack, ItemModelResolver r, LivingEntity e, boolean right) {
		boolean toRight = main == right;
		if (toRight) {
			r.updateForLiving(s.rightHandItemState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, e);
			s.rightHandItemStack = stack;
			s.rightArmPose = HumanoidModel.ArmPose.ITEM;
		} else {
			r.updateForLiving(s.leftHandItemState, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, e);
			s.leftHandItemStack = stack;
			s.leftArmPose = HumanoidModel.ArmPose.ITEM;
		}
	}

	// ------------------------------------------------------------------ apply (end of HumanoidModel.setupAnim)

	/** Channels: weapon arm x/y/z, off arm x/y/z, weapon-side leg x/y/z, off-side leg x/y/z (NaN = leave), head pitch, lean, twist, drop (additive). */
	static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLY = 7, RLZ = 8, LLX = 9, LLY = 10, LLZ = 11, HEAD = 12, LEAN = 13,
		TWIST = 14, DROP = 15, N = 16;
	private static final float[] A = new float[N], B = new float[N];
	private static final float X = Float.NaN;

	public static void apply(HumanoidModel<?> m, Track t, HumanoidRenderState s) {
		float lean = 0F, twist = 0F, drop = 0F, head = 0F;
		if (t.wOut > 0F) {
			pose(t.prev, t.prevWork, t.prevArmed, t, t.age - t.outStart + 1000F, A);
			blend(m, A, t.wOut, t.mirror);
			lean += A[LEAN] * t.wOut;
			twist += A[TWIST] * t.wOut;
			drop += A[DROP] * t.wOut;
			head += A[HEAD] * t.wOut;
		}
		if (t.wIn > 0F) {
			pose(t.cur, t.work, t.armed, t, t.age - t.curStart, B);
			blend(m, B, t.wIn, t.mirror);
			lean = Mth.lerp(t.wIn, lean, B[LEAN]);
			twist = Mth.lerp(t.wIn, twist, B[TWIST]);
			drop = Mth.lerp(t.wIn, drop, B[DROP]);
			head = Mth.lerp(t.wIn, head, B[HEAD]);
		}
		if (t.mirror) twist = -twist;
		m.head.xRot += head;
		if (twist != 0F) {
			m.body.yRot += twist;
			rotateY(m.rightArm, twist);
			rotateY(m.leftArm, twist);
			m.rightArm.yRot += twist;
			m.leftArm.yRot += twist;
		}
		if (lean != 0F) {
			float cos = Mth.cos(lean), sin = Mth.sin(lean);
			float dy = 12F * (1F - cos), dz = -12F * sin;
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
		if (drop != 0F) {
			m.body.y += drop;
			m.head.y += drop;
			m.rightArm.y += drop;
			m.leftArm.y += drop;
			m.rightLeg.y += drop;
			m.leftLeg.y += drop;
		}
	}

	private static void blend(HumanoidModel<?> m, float[] v, float w, boolean mirror) {
		ModelPart wa = mirror ? m.leftArm : m.rightArm, oa = mirror ? m.rightArm : m.leftArm;
		ModelPart wl = mirror ? m.leftLeg : m.rightLeg, ol = mirror ? m.rightLeg : m.leftLeg;
		float sg = mirror ? -1F : 1F;
		rot(wa, v[RAX], v[RAY], v[RAZ], w, sg);
		rot(oa, v[LAX], v[LAY], v[LAZ], w, sg);
		rot(wl, v[RLX], v[RLY], v[RLZ], w, sg);
		rot(ol, v[LLX], v[LLY], v[LLZ], w, sg);
	}

	private static void rot(ModelPart p, float x, float y, float z, float w, float sg) {
		if (!Float.isNaN(x)) p.xRot = Mth.lerp(w, p.xRot, x);
		if (!Float.isNaN(y)) p.yRot = Mth.lerp(w, p.yRot, y * sg);
		if (!Float.isNaN(z)) p.zRot = Mth.lerp(w, p.zRot, z * sg);
	}

	/** Moves a shoulder pivot around the torso's vertical axis (as vanilla's attack swing does). */
	private static void rotateY(ModelPart arm, float a) {
		float c = Mth.cos(a), sn = Mth.sin(a);
		float x = arm.x, z = arm.z;
		arm.x = x * c + z * sn;
		arm.z = -x * sn + z * c;
	}

	private static void set(float[] o, int at, float x, float y, float z) {
		o[at] = x;
		o[at + 1] = y;
		o[at + 2] = z;
	}

	/** Fills {@code o} with the pose {@code anim} at {@code u} ticks after it started (age-driven loops use {@code t.age}). */
	static void pose(int anim, int work, boolean armed, Track t, float u, float[] o) {
		for (int i = 0; i < 12; i++) o[i] = X;
		o[HEAD] = o[LEAN] = o[TWIST] = 0F;
		o[DROP] = dropOf(anim, t.style);
		int st = t.style;
		float age = t.age, ph = (t.seed & 63);
		float breath = Mth.sin(age * 0.08F) * 0.02F;
		switch (anim) {
			case SIT -> sit(o, st, breath);
			case REST -> {
				sitLegs(o, st);
				set(o, RAX, -1.1F, -0.45F, 0F);
				set(o, LAX, -1.1F, 0.45F, 0F);
				o[HEAD] = 0.45F + Mth.sin(age * 0.05F + ph) * 0.07F; // dozing by the fire
				o[LEAN] = 0.18F;
			}
			case KNEEL -> {
				kneelLegs(o);
				set(o, RAX, -0.7F, -0.2F, 0F);
				set(o, LAX, -0.15F + breath, 0.1F, -0.05F);
				o[HEAD] = 0.45F;
				o[LEAN] = 0.12F;
			}
			case PRAY -> {
				kneelLegs(o);
				if (st == AZMAR) { // the dawn rite: both arms raised to the sun
					float lift = Mth.sin(age * 0.06F + ph) * 0.06F;
					set(o, RAX, -2.7F + lift, 0F, -0.35F);
					set(o, LAX, -2.7F + lift, 0F, 0.35F);
					o[HEAD] = -0.45F;
				} else {
					set(o, RAX, -1.05F, -0.5F, 0F);
					set(o, LAX, -1.05F, 0.5F, 0F);
					o[HEAD] = 0.5F;
					o[LEAN] = 0.1F;
				}
			}
			case ATTENTION -> attention(o, st, breath);
			case SALUTE -> {
				attention(o, st, breath);
				salute(o, st, armed, u);
			}
			case WORK, S_WORK -> work(o, work, age, ph, armed);
			case SPAR -> {
				float p = cycle(age + ph, 30F);
				set(o, RLX, -0.35F, 0.05F, 0F);
				set(o, LLX, 0.3F, 0F, 0F);
				set(o, LAX, -1.0F, 0.65F, 0F); // shield (or fist) up front
				float k = seg(p, 0.3F, 0.5F), strike = seg(p, 0.5F, 0.6F), back = seg(p, 0.65F, 1F);
				float ax = Mth.lerp(k, -0.9F, -2.2F), ay = Mth.lerp(k, 0.2F, 0.3F);
				ax = Mth.lerp(strike, ax, -0.35F);
				ay = Mth.lerp(strike, ay, -0.55F);
				ax = Mth.lerp(back, ax, -0.9F);
				ay = Mth.lerp(back, ay, 0.2F);
				set(o, RAX, ax, ay, 0.1F);
				o[TWIST] = (strike - back) * -0.3F + Mth.sin(age * 0.2F + ph) * 0.05F;
				o[LEAN] = 0.1F;
			}
			case CHEER -> {
				if (st == AZMAR || st == MIREWICK) { // polite applause
					float k = (Mth.sin(age * 0.9F + ph) + 1F) * 0.5F;
					set(o, RAX, -1.15F, -0.25F - 0.3F * k, 0F);
					set(o, LAX, -1.15F, 0.25F + 0.3F * k, 0F);
				} else { // arms (and weapon) pumped high
					float c = Mth.sin(age * 0.45F + ph);
					set(o, RAX, -2.75F + 0.3F * c, 0F, -0.25F);
					set(o, LAX, st == HRAFNHOLT ? -1.0F : -2.75F - 0.3F * c, st == HRAFNHOLT ? 0.75F : 0F, st == HRAFNHOLT ? 0F : 0.25F);
					o[HEAD] = -0.2F;
				}
			}
			case TORCH -> {
				set(o, LAX, -2.2F + breath, 0F, -0.1F);
				o[HEAD] = -0.1F;
			}
			case CARRY -> carry(o, st);
			case DRINK -> {
				float p = cycle(age + ph, 70F);
				float up = seg(p, 0.55F, 0.62F) - seg(p, 0.9F, 0.97F);
				set(o, RAX, Mth.lerp(up, -0.55F, -2.05F), Mth.lerp(up, -0.2F, -0.5F), 0F);
				o[HEAD] = -(st == HRAFNHOLT ? 0.45F : 0.35F) * up;
			}
			case LOUNGE -> {
				set(o, RLX, -1.45F, 0.18F, 0.05F);
				set(o, LLX, -0.8F, -0.1F, -0.05F); // one knee up
				o[LEAN] = -0.3F;
				if (st == AZMAR) { // siesta in the shade, hands behind the head
					set(o, RAX, -2.9F, 0F, 0.6F);
					set(o, LAX, -2.9F, 0F, -0.6F);
				} else {
					set(o, RAX, -1.0F, -0.8F, 0F);
					set(o, LAX, -1.0F, 0.8F, 0F);
				}
				o[HEAD] = 0.35F + Mth.sin(age * 0.04F + ph) * 0.05F;
			}
			case S_CARRY -> {
				if (st == AZMAR) {
					set(o, RAX, -2.95F, 0F, 0.15F);
					set(o, LAX, -2.95F, 0F, -0.15F);
				} else set(o, RAX, -2.6F, -0.45F, 0F); // the log or stone on the shoulder
			}
			default -> {
			}
		}
	}

	private static void sitLegs(float[] o, int st) {
		if (st == AZMAR) { // cross-legged
			set(o, RLX, -1.5F, -0.95F, 0F);
			set(o, LLX, -1.5F, 0.95F, 0F);
		} else if (st == HRAFNHOLT) { // wide
			set(o, RLX, -1.4F, 0.45F, 0.08F);
			set(o, LLX, -1.4F, -0.45F, -0.08F);
		} else {
			set(o, RLX, -1.42F, 0.22F, 0.08F);
			set(o, LLX, -1.42F, -0.22F, -0.08F);
		}
	}

	private static void sit(float[] o, int st, float breath) {
		sitLegs(o, st);
		switch (st) {
			case HRAFNHOLT -> { // leaning in, elbows on the knees
				set(o, RAX, -0.6F + breath, -0.15F, 0F);
				set(o, LAX, -0.6F + breath, 0.15F, 0F);
				o[LEAN] = 0.3F;
			}
			case AZMAR -> {
				set(o, RAX, -0.7F + breath, -0.35F, 0F);
				set(o, LAX, -0.7F + breath, 0.35F, 0F);
			}
			case MIREWICK -> { // lounging back on the elbows
				set(o, RAX, 0.35F, 0F, 0.3F);
				set(o, LAX, 0.35F, 0F, -0.3F);
				o[LEAN] = -0.25F;
			}
			default -> { // straight, hands on the knees, looking into the fire
				set(o, RAX, -0.95F + breath, -0.1F, 0.05F);
				set(o, LAX, -0.95F + breath, 0.1F, -0.05F);
				o[HEAD] = 0.1F;
			}
		}
	}

	private static void kneelLegs(float[] o) {
		set(o, RLX, -1.05F, 0.1F, 0F); // front knee up
		set(o, LLX, 1.1F, -0.05F, 0F); // back knee on the ground
	}

	private static void attention(float[] o, int st, float breath) {
		switch (st) {
			case HRAFNHOLT -> {
				set(o, RAX, -0.25F, 0F, 0.12F);
				set(o, LAX, -0.15F, 0F, -0.12F);
				set(o, RLX, 0F, 0F, 0.1F);
				set(o, LLX, 0F, 0F, -0.1F);
			}
			case AZMAR -> { // rigid, not a breath
				set(o, RAX, 0F, 0F, 0.02F);
				set(o, LAX, 0F, 0F, -0.02F);
				set(o, RLX, 0F, 0F, 0F);
				set(o, LLX, 0F, 0F, 0F);
			}
			case MIREWICK -> { // a slouch, hand on the hip
				set(o, RAX, 0.05F + breath, 0F, 0.08F);
				set(o, LAX, 0F, 0F, -0.5F);
				set(o, RLX, 0F, 0F, 0.02F);
				set(o, LLX, -0.12F, 0F, -0.06F);
				o[LEAN] = 0.06F;
				o[HEAD] = 0.08F;
			}
			default -> {
				set(o, RAX, breath, 0F, 0.04F);
				set(o, LAX, breath, 0F, -0.04F);
				set(o, RLX, 0F, 0F, 0F);
				set(o, LLX, 0F, 0F, 0F);
				o[HEAD] = -0.05F;
			}
		}
	}

	/** One-shot salute over the attention pose: raise, hold, lower (u = ticks since the salute order). */
	private static void salute(float[] o, int st, boolean armed, float u) {
		float hold = st == MIREWICK ? 12F : st == HRAFNHOLT ? 30F : 24F;
		float k = easeInOut(Mth.clamp(u / 4F, 0F, 1F)) * (1F - easeInOut(Mth.clamp((u - hold) / 6F, 0F, 1F)));
		if (k <= 0F) return;
		switch (st) {
			case HRAFNHOLT -> { // weapon thrust up, fist on the chest
				lerp3(o, RAX, k, -2.95F, 0.1F, -0.2F);
				lerp3(o, LAX, k, -1.0F, 0.75F, 0F);
				o[HEAD] = Mth.lerp(k, o[HEAD], -0.25F);
			}
			case AZMAR -> { // hand on the heart, a bow
				lerp3(o, RAX, k, -0.95F, -0.95F, 0F);
				o[LEAN] = Mth.lerp(k, o[LEAN], 0.4F);
				o[HEAD] = Mth.lerp(k, o[HEAD], 0.35F);
			}
			case MIREWICK -> lerp3(o, RAX, k, -2.6F, -0.25F, 0.1F); // a quick tip of the hat
			default -> {
				if (armed) lerp3(o, RAX, k, -1.55F, -0.45F, 0F); // sword salute: hilt at the chin, blade up
				else lerp3(o, RAX, k, -2.25F, -0.6F, 0.05F); // hand to the brow
			}
		}
	}

	private static void lerp3(float[] o, int at, float k, float x, float y, float z) {
		o[at] = Mth.lerp(k, o[at], x);
		o[at + 1] = Mth.lerp(k, o[at + 1], y);
		o[at + 2] = Mth.lerp(k, o[at + 2], z);
	}

	private static void carry(float[] o, int st) {
		if (st == AZMAR) { // on the head
			set(o, RAX, -2.95F, 0F, 0.15F);
			set(o, LAX, -2.95F, 0F, -0.15F);
		} else {
			set(o, RAX, -1.05F, -0.22F, 0F);
			set(o, LAX, -1.05F, 0.22F, 0F);
			o[LEAN] = -0.06F;
		}
	}

	private static void work(float[] o, int kind, float age, float ph, boolean armed) {
		switch (kind) {
			case W_SWEEP -> {
				float sw = Mth.sin(age * (Mth.TWO_PI / 32F) + ph);
				set(o, RAX, -0.68F, -0.35F, 0F);
				set(o, LAX, -0.95F, 0.5F, 0F);
				set(o, RLX, -0.1F, 0F, 0.05F);
				set(o, LLX, 0.1F, 0F, -0.05F);
				o[TWIST] = 0.3F * sw;
				o[LEAN] = 0.15F;
				o[HEAD] = 0.25F;
			}
			case W_CHOP -> { // two-handed overhead chop
				float p = cycle(age + ph, 26F);
				float up = seg(p, 0F, 0.45F), down = seg(p, 0.5F, 0.6F), rec = seg(p, 0.75F, 1F);
				float ax = Mth.lerp(up, -1.2F, -2.8F);
				ax = Mth.lerp(down, ax, -0.55F);
				ax = Mth.lerp(rec, ax, -1.2F);
				set(o, RAX, ax, -0.15F - 0.15F * down, 0F);
				set(o, LAX, ax, 0.15F + 0.15F * down, 0F);
				set(o, RLX, -0.2F, 0F, 0.08F);
				set(o, LLX, 0.15F, 0F, -0.08F);
				o[LEAN] = Mth.lerp(down - rec, -0.1F, 0.35F) + 0.1F * up * -1F;
				o[HEAD] = 0.2F;
			}
			case W_POLISH -> { // rubbing the gilded kit with a cloth
				float th = age * (Mth.TWO_PI / 20F) + ph;
				set(o, RAX, -0.9F + 0.12F * Mth.cos(th), -0.45F + 0.15F * Mth.sin(th), 0F);
				set(o, LAX, -0.85F, 0.45F, 0F);
				o[HEAD] = 0.4F;
				o[LEAN] = 0.1F;
			}
			case W_DRAW -> { // archery practice: raise, draw, hold, loose, lower
				float p = cycle(age + ph, 50F);
				float up = seg(p, 0.3F, 0.44F) - seg(p, 0.84F, 1F);
				float loose = seg(p, 0.8F, 0.84F) - seg(p, 0.84F, 0.9F);
				set(o, RAX, Mth.lerp(up, -0.35F, -1.57F) + 0.25F * loose, Mth.lerp(up, 0F, -0.1F), 0F);
				set(o, LAX, Mth.lerp(up, -0.45F, -1.57F), Mth.lerp(up, 0.2F, 0.5F), 0F);
			}
			case W_MASON -> { // serf: lay the stone, tamp it down
				float s1 = Mth.sin(age * (Mth.TWO_PI / 24F) + ph), s2 = Mth.sin(age * (Mth.TWO_PI / 24F) + ph + 1.2F);
				set(o, RAX, -0.95F + 0.35F * s1, -0.2F, 0F);
				set(o, LAX, -0.8F + 0.3F * s2, 0.25F, 0F);
				set(o, RLX, -0.25F, 0F, 0.05F);
				set(o, LLX, 0.15F, 0F, -0.05F);
				o[LEAN] = 0.45F + 0.1F * s1;
				o[HEAD] = 0.35F;
				o[DROP] = 1F;
			}
			case W_LIFT -> { // porter: stoop for the load, lift it to the head
				float p = cycle(age + ph, 60F);
				float up = seg(p, 0.15F, 0.4F) - seg(p, 0.65F, 0.9F);
				set(o, RAX, Mth.lerp(up, -0.75F, -2.95F), Mth.lerp(up, -0.25F, 0F), Mth.lerp(up, 0F, 0.15F));
				set(o, LAX, Mth.lerp(up, -0.75F, -2.95F), Mth.lerp(up, 0.25F, 0F), Mth.lerp(up, 0F, -0.15F));
				o[LEAN] = Mth.lerp(up, 0.55F, 0F);
				o[DROP] = Mth.lerp(up, 3F, 0F);
				set(o, RLX, Mth.lerp(up, -0.35F, 0F), 0F, 0.05F);
				set(o, LLX, Mth.lerp(up, -0.35F, 0F), 0F, -0.05F);
			}
			case W_SPADE -> { // peat-cutter: foot on the spade, lever, lift the turf
				float p = cycle(age + ph, 32F);
				float push = seg(p, 0F, 0.3F) - seg(p, 0.35F, 0.5F), lift = seg(p, 0.5F, 0.7F) - seg(p, 0.85F, 1F);
				set(o, RAX, -0.95F + 0.4F * push - 0.5F * lift, -0.25F, 0F);
				set(o, LAX, -1.15F + 0.35F * push - 0.4F * lift, 0.3F, 0F);
				set(o, RLX, -0.45F * push, 0F, 0.05F);
				set(o, LLX, 0.1F, 0F, -0.05F);
				o[LEAN] = 0.1F + 0.3F * push + 0.1F * lift;
				o[HEAD] = 0.3F;
				o[DROP] = push;
			}
			default -> { // practice swings at the quintain or pell
				float p = cycle(age + ph, 24F);
				float up = seg(p, 0F, 0.45F), strike = seg(p, 0.45F, 0.6F), rec = seg(p, 0.6F, 1F);
				float ax = Mth.lerp(up, -0.6F, -2.5F), ay = Mth.lerp(up, 0F, 0.25F);
				ax = Mth.lerp(strike, ax, -0.45F);
				ay = Mth.lerp(strike, ay, -0.5F);
				ax = Mth.lerp(rec, ax, -0.6F);
				ay = Mth.lerp(rec, ay, 0F);
				set(o, RAX, ax, ay, 0.1F * up);
				set(o, LAX, -0.5F, 0.2F, 0F);
				set(o, RLX, -0.25F, 0F, 0F);
				set(o, LLX, 0.2F, 0F, 0F);
				o[TWIST] = -0.25F * (strike - rec);
			}
		}
	}

	// ------------------------------------------------------------------ easing

	static float cycle(float t, float len) {
		float p = (t % len) / len;
		return p < 0F ? p + 1F : p;
	}

	/** 0 before {@code a}, 1 after {@code b}, eased in between. */
	static float seg(float p, float a, float b) {
		return easeInOut(Mth.clamp((p - a) / (b - a), 0F, 1F));
	}

	static float easeInOut(float x) {
		return x * x * (3F - 2F * x);
	}
}
