package dev.elsewhere.arsenal.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.arsenal.anim.SwingCurves;
import dev.elsewhere.arsenal.anim.SwingCurves.Style;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * POLISH P6: use animations for the gear2 and arsenal weapons (client only).
 * <ul>
 * <li>Ironclad Flail: a heavy overhead arc (first and third person) with a steel trail; a "charged" model when the
 * attack is fully recharged ({@code elsewhere:attack_charge}).</li>
 * <li>Gale Rapier: a lunge (thrust plus a forward step in third person) with a wind trail; a "lunge ready" model while
 * sprinting ({@code elsewhere:lunge_ready}).</li>
 * <li>Amethyst Wand, Sunlance and Stormcoil staffs: a pointing cast flourish instead of a whack; the focus dims while
 * the spell recharges ({@code elsewhere:cast_cooldown}).</li>
 * <li>Sculk Resonator, Mirrorglass Prism, Hushward Bell: charge/channel/ring models ({@code elsewhere:channeling},
 * {@code elsewhere:channel_time}) and a first-person tremble, shimmer or ring while held in use.</li>
 * </ul>
 * Nothing ticks and nothing is sent: everything is computed from the swing/use state the client already has, at render
 * time. Kill switch: {@code "use_anims": false} in {@code config/elsewhere-overhaul3.json} (everything falls back to the
 * vanilla poses and the plain item models). Effects level: {@code /elsewhere_weaponfx low|normal|high} (low = no trails).
 */
public final class UseAnims {
	private UseAnims() {}

	public static final boolean ON = dev.elsewhere.arsenal.Overhaul3.on("use_anims");
	private static boolean inited;
	private static final Map<Item, Style> STYLES = new IdentityHashMap<>();

	public static void init() {
		if (inited) return;
		inited = true;
		UseAnimConfig.load();
		UseAnimConfig.registerCommand();
		UseAnimProps.register(); // always: the item model JSONs reference these ids even with the switch off
		if (!ON) return;
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (type != EntityTypes.PLAYER || !(renderer instanceof AvatarRenderer<?> ar)) return;
			@SuppressWarnings("unchecked")
			RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (RenderLayerParent<?, ?>) ar;
			helper.register(new SwingTrailLayer(parent));
		});
	}

	/** The weapon style of a stack (cached per item; no allocation after the first lookup). */
	public static Style style(ItemStack stack) {
		if (!ON || stack == null || stack.isEmpty()) return Style.NONE;
		Item item = stack.getItem();
		Style s = STYLES.get(item);
		if (s == null) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			s = SwingCurves.styleOf(id.getNamespace(), id.getPath());
			STYLES.put(item, s);
		}
		return s;
	}

	public static boolean isItem(ItemStack stack, String path) {
		if (stack == null || stack.isEmpty()) return false;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return "elsewhere".equals(id.getNamespace()) && path.equals(id.getPath());
	}

	// ------------------------------------------------------------------------------------------------ first person

	/** The stack and collector of the arm being drawn (set by the first-person mixin; render thread only). */
	static ItemStack fpStack = ItemStack.EMPTY;
	static SubmitNodeCollector fpCollector;
	private static final SwingCurves.Scratch FP_SCRATCH = new SwingCurves.Scratch();
	private static final Vector3f FP_T = new Vector3f();
	private static final Quaternionf FP_Q = new Quaternionf();
	private static final float[] FP_V = new float[7];

	public static void beginArm(ItemStack stack, SubmitNodeCollector collector) {
		fpStack = stack;
		fpCollector = collector;
	}

	/**
	 * Replaces vanilla's first-person whack swing for our weapons. Called at the start of the swing transform with the
	 * pose in hand space; returns false to let vanilla swing.
	 */
	public static boolean firstPersonSwing(float attack, PoseStack pose, int invert) {
		Style s = style(fpStack);
		if (s == Style.NONE) return false;
		if (s.trails() && fpCollector != null) {
			int n = SwingCurves.trailSamples(UseAnimConfig.level());
			if (n >= 2) {
				TrailGeom g = TrailGeom.next();
				g.count = SwingCurves.firstPersonTrail(s, attack, invert, n, FP_SCRATCH, g.pts);
				if (g.count >= 2) {
					g.color = trailColor(s);
					fpCollector.submitCustomGeometry(pose, TrailGeom.TYPE, g);
				}
			}
		}
		SwingCurves.firstPersonPose(s, attack, invert, FP_V, FP_T, FP_Q);
		pose.translate(FP_T.x, FP_T.y, FP_T.z);
		pose.rotate(FP_Q);
		return true;
	}

	/**
	 * Extra first-person transform for held-in-use casters, applied just before the item is drawn: the Sculk Resonator
	 * trembles harder as it charges, the Mirrorglass Prism shimmers, the Hushward Bell rings (rocks) while held up.
	 */
	public static void firstPersonUse(PoseStack pose, ItemStack stack, boolean using, float ticksUsed, int invert) {
		if (!ON || !using || stack.isEmpty()) return;
		if (isItem(stack, "sculk_resonator")) {
			float charge = Math.min(1F, ticksUsed / dev.elsewhere.arsenal.ArsenalStats.RESONATOR_CHARGE_TICKS);
			float shake = Mth.sin(ticksUsed * 2.7F) * 0.012F * charge;
			pose.translate(shake * invert, Mth.cos(ticksUsed * 3.1F) * 0.008F * charge, -0.05F * charge);
		} else if (isItem(stack, "mirrorglass_prism")) {
			pose.translate(0F, Mth.sin(ticksUsed * 0.35F) * 0.015F, 0F);
			pose.rotateDegrees(com.mojang.math.Axis.YP, Mth.sin(ticksUsed * 0.22F) * 6F * invert);
		} else if (isItem(stack, "hushward_bell")) {
			pose.rotateDegrees(com.mojang.math.Axis.ZP, Mth.sin(ticksUsed * 0.9F) * 9F * invert);
		}
	}

	// ------------------------------------------------------------------------------------------------ third person

	/**
	 * Replaces vanilla's third-person attack animation for our weapons. Returns false (vanilla runs) for any other item.
	 * The arm angles are blended from the walk pose by the curve weight, so a swing starts and ends seamlessly.
	 */
	public static boolean thirdPersonSwing(HumanoidModel<?> model, HumanoidRenderState state, float[] a) {
		if (!ON || state.swingAnimation <= 0F || state.currentSwing == null) return false;
		HumanoidArm arm = state.currentSwing.hand().asArm(state.mainArm);
		ItemStack stack = arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		Style s = style(stack);
		if (s == Style.NONE) return false;
		SwingCurves.thirdPerson(s, state.swingAnimation, state.xRot * Mth.DEG_TO_RAD, a);
		float w = a[6];
		float side = arm == HumanoidArm.RIGHT ? 1F : -1F;
		float bodyY = a[3] * side * w;
		model.body.yRot = bodyY;
		float ageScale = state.ageScale;
		model.rightArm.z = Mth.sin(bodyY) * 5.0F * ageScale;
		model.rightArm.x = -Mth.cos(bodyY) * 5.0F * ageScale;
		model.leftArm.z = -Mth.sin(bodyY) * 5.0F * ageScale;
		model.leftArm.x = Mth.cos(bodyY) * 5.0F * ageScale;
		ModelPart attack = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart other = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
		attack.xRot = Mth.lerp(w, attack.xRot, a[0]);
		attack.yRot = Mth.lerp(w, attack.yRot, a[1] * side) + bodyY;
		attack.zRot = Mth.lerp(w, attack.zRot, a[2] * side);
		other.yRot += bodyY;
		if (s == Style.RAPIER) {
			// lunge step: the sword-side leg forward, the other back
			ModelPart front = arm == HumanoidArm.RIGHT ? model.rightLeg : model.leftLeg;
			ModelPart back = arm == HumanoidArm.RIGHT ? model.leftLeg : model.rightLeg;
			front.xRot = Mth.lerp(w, front.xRot, a[4]);
			back.xRot = Mth.lerp(w, back.xRot, a[5]);
			other.xRot = Mth.lerp(w, other.xRot, 0.35F); // off arm swings back for balance
		}
		return true;
	}

	static int trailColor(Style s) {
		return s == Style.FLAIL ? 0xC8D6F0 : 0x9EEBFF;
	}
}
