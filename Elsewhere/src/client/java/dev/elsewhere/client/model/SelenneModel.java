package dev.elsewhere.client.model;

import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.client.model.gen.SelenneGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Selenne: heavy lids that slide open and shut (blink tells), a pupil that drifts toward her focus, the iris
 * contracting while a Moonbeam charges, lids gone in phase 3, eyeblossom lashes and a slowly turning moon halo.
 */
public class SelenneModel extends EntityModel<States.Selenne> {
	private final ModelPart eye;
	private final ModelPart iris;
	private final ModelPart upperLid;
	private final ModelPart lowerLid;
	private final ModelPart halo;
	private final ModelPart[] lashes = new ModelPart[8];

	public SelenneModel(final ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.eye = root.getChild("root").getChild("eye");
		this.iris = this.eye.getChild("iris");
		this.upperLid = this.eye.getChild("upper_lid");
		this.lowerLid = this.eye.getChild("lower_lid");
		this.halo = this.eye.getChild("halo");
		for (int i = 0; i < 8; i++) {
			this.lashes[i] = this.eye.getChild("lash" + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(SelenneGeometry.mesh(), SelenneGeometry.TEXTURE_WIDTH, SelenneGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Selenne s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		this.eye.y += Mth.sin(t * 0.05F) * 0.8F;
		this.eye.xRot = s.xRot * Mth.DEG_TO_RAD * 0.5F;
		float open = s.open;
		// a quick blink tell just before she closes
		this.upperLid.xRot = open * 1.75F;
		this.lowerLid.xRot = -open * 1.75F;
		this.upperLid.visible = !s.torn;
		this.lowerLid.visible = !s.torn;
		this.iris.x = Mth.clamp(-s.yRot * 0.06F, -3.5F, 3.5F) + Mth.sin(t * 0.07F) * 0.6F;
		this.iris.y = Mth.clamp(s.xRot * 0.05F, -3.0F, 3.0F) + Mth.cos(t * 0.05F) * 0.4F;
		float contract = s.beam > 0 ? 0.65F + 0.35F * (s.beam / Selenne.BEAM_WINDUP) : 1.0F;
		this.iris.xScale = contract;
		this.iris.yScale = contract;
		this.halo.zRot = t * 0.01F;
		float haloScale = 1.0F + (s.fullMoon > 0 ? (1.0F - s.fullMoon / Selenne.FULL_MOON_TICKS) * 0.6F : 0.0F);
		this.halo.xScale = haloScale;
		this.halo.yScale = haloScale;
		for (int i = 0; i < 8; i++) {
			this.lashes[i].xRot = -0.25F * open + Mth.sin(t * 0.12F + i) * 0.12F;
		}
	}
}
