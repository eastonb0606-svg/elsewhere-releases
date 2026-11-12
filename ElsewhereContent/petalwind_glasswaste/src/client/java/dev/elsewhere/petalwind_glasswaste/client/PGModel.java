package dev.elsewhere.petalwind_glasswaste.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Generic animated model for the generated geometry. Parts animate by name: head looks, legs and arms walk, rings spin,
 * tails sway, fins and kites flutter; {@link PGState#pose} drives boss poses (Kazehana's iaido, bow, dash; Heliodor's
 * slam; Ankhara's breach).
 */
public class PGModel extends EntityModel<PGState> {
	private final Map<String, ModelPart> parts = new HashMap<>();
	private final String kind;

	public PGModel(ModelPart root, String kind, boolean translucent) {
		super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
		this.kind = kind;
		collect(root);
	}

	private void collect(ModelPart part) {
		for (String name : new String[] {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "ring1", "ring2", "ring3", "tail1", "tail2",
			"tail3", "fin_l", "fin_r", "kite", "jaw", "hat", "haori", "katana", "wind", "cube", "orb", "seg", "rays", "shell", "leg1", "leg2", "leg3",
			"leg4", "cloud", "harness", "gizzard", "banner"}) {
			ModelPart p = find(part, name);
			if (p != null) parts.put(name, p);
		}
	}

	private static ModelPart find(ModelPart root, String name) {
		if (root.hasChild(name)) return root.getChild(name);
		for (String child : new String[] {"head", "body", "tail1", "tail2", "kite", "seg", "right_arm"}) {
			if (root.hasChild(child)) {
				ModelPart r = find(root.getChild(child), name);
				if (r != null) return r;
			}
		}
		return null;
	}

	private ModelPart p(String n) {
		return parts.get(n);
	}

	@Override
	public void setupAnim(PGState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float walk = s.walkAnimationPos, speed = Math.min(1.0F, s.walkAnimationSpeed);
		ModelPart head = p("head");
		if (head != null && !kind.equals("ankhara_head") && !kind.equals("sandswimmer_head")) {
			head.yRot = s.yRot * Mth.DEG_TO_RAD;
			head.xRot = s.xRot * Mth.DEG_TO_RAD;
		}
		swing("right_leg", Mth.cos(walk * 0.6662F) * 1.2F * speed);
		swing("left_leg", Mth.cos(walk * 0.6662F + Mth.PI) * 1.2F * speed);
		swing("right_arm", Mth.cos(walk * 0.6662F + Mth.PI) * 0.8F * speed);
		swing("left_arm", Mth.cos(walk * 0.6662F) * 0.8F * speed);
		spin("ring1", t * 0.05F);
		spin("ring2", -t * 0.04F);
		spin("ring3", t * 0.08F);
		sway("tail1", Mth.sin(t * 0.15F) * 0.25F);
		sway("tail2", Mth.sin(t * 0.15F - 0.8F) * 0.3F);
		sway("tail3", Mth.sin(t * 0.15F - 1.6F) * 0.35F);
		if (p("fin_l") != null) p("fin_l").zRot += Mth.sin(t * 0.3F) * 0.25F;
		if (p("fin_r") != null) p("fin_r").zRot -= Mth.sin(t * 0.3F) * 0.25F;
		if (p("kite") != null) p("kite").xRot += Mth.sin(t * 0.4F) * 0.15F;
		if (p("wind") != null) p("wind").yRot = t * 0.3F;
		if (p("orb") != null) p("orb").y += Mth.sin(t * 0.1F) * 1.0F;
		if (p("cube") != null) p("cube").y += Mth.sin(t * 0.2F) * 0.3F;
		if (p("cloud") != null) p("cloud").yRot = t * 0.02F;
		for (int i = 1; i <= 4; i++) {
			ModelPart leg = p("leg" + i);
			if (leg != null) leg.yRot = Mth.sin(walk * 1.3F + i) * 0.4F * speed;
		}
		if (p("harness") != null) p("harness").visible = s.flag;
		if (p("gizzard") != null) p("gizzard").visible = s.flag;
		poses(s, t);
	}

	private void poses(PGState s, float t) {
		switch (kind) {
			case "kazehana" -> {
				ModelPart body = p("body"), ra = p("right_arm"), la = p("left_arm"), head = p("head");
				switch (s.pose) {
					case 1 -> { // bow
						if (body != null) body.xRot = 0.5F;
						if (head != null) head.xRot = 0.6F;
					}
					case 2 -> { // iaido: hand on hilt at the hip
						ra.xRot = -0.3F;
						ra.zRot = 0.6F;
						la.xRot = -0.6F;
						la.zRot = -0.4F;
					}
					case 3 -> { // draw cut dash: blade swept wide
						ra.xRot = -1.5F;
						ra.yRot = -1.2F;
						if (body != null) body.xRot = 0.35F;
					}
					case 4 -> ra.xRot = -1.6F + Mth.sin(t * 1.2F) * 1.2F; // combo
					case 5 -> { // parry: blade raised across
						ra.xRot = -2.2F;
						ra.zRot = 0.9F;
					}
					case 6 -> { // stunned
						if (head != null) head.zRot = Mth.sin(t * 0.5F) * 0.3F;
						ra.xRot = 0.2F;
					}
					case 7 -> {
						ra.xRot = -2.8F;
						la.xRot = -2.8F;
					}
					default -> ra.xRot = Math.min(ra.xRot, -0.4F);
				}
			}
			case "heliodor" -> {
				if (s.pose == 1) {
					p("right_arm").xRot = -2.6F;
					p("left_arm").xRot = -2.6F;
				}
				if (s.flag && p("rays") != null) p("rays").zRot = t * 0.4F;
			}
			case "ankhara_head" -> {
				if (p("jaw") != null) p("jaw").xRot = s.pose == 0 ? 0 : 0.5F + Mth.sin(t * 0.3F) * 0.2F;
				if (p("head") != null) p("head").xRot = s.pose == 1 ? -0.5F : 0;
			}
			case "tempestra" -> {
				if (s.pose == 1 && p("ring1") != null) p("ring1").yRot = s.ageInTicks * 0.3F;
			}
			default -> {}
		}
	}

	private void swing(String n, float a) {
		ModelPart part = p(n);
		if (part != null) part.xRot = a;
	}

	private void spin(String n, float a) {
		ModelPart part = p(n);
		if (part != null) part.yRot = a;
	}

	private void sway(String n, float a) {
		ModelPart part = p(n);
		if (part != null) part.yRot = a;
	}
}
