package dev.elsewhere.client.model;

import dev.elsewhere.client.model.gen.EyeblossomWispGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/** Eyeblossom Wisp: bobbing flower-eye, fluttering petal lashes, a blink every few seconds, petals flare on a shriek. */
public class EyeblossomWispModel extends EntityModel<States.Wisp> {
	private final ModelPart core;
	private final ModelPart lid;
	private final ModelPart tail;
	private final ModelPart[] petals = new ModelPart[6];

	public EyeblossomWispModel(final ModelPart root) {
		super(root);
		this.core = root.getChild("root").getChild("core");
		this.lid = this.core.getChild("lid");
		this.tail = this.core.getChild("tail");
		for (int i = 0; i < 6; i++) {
			this.petals[i] = this.core.getChild("petal" + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(EyeblossomWispGeometry.mesh(), EyeblossomWispGeometry.TEXTURE_WIDTH, EyeblossomWispGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Wisp s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		this.core.y += Mth.sin(t * 0.12F) * 1.2F;
		this.core.yRot = s.yRot * Mth.DEG_TO_RAD;
		this.core.xRot = s.xRot * Mth.DEG_TO_RAD * 0.6F;
		float flare = s.shriek > 0 ? 0.6F : 0.0F;
		for (int i = 0; i < 6; i++) {
			this.petals[i].xRot = -0.35F - flare + Mth.sin(t * 0.25F + i * 1.1F) * 0.12F;
		}
		this.tail.xRot = 0.25F + Mth.sin(t * 0.1F) * 0.25F;
		this.tail.zRot = Mth.cos(t * 0.08F) * 0.2F;
		if (s.shriek > 0) {
			this.core.x += Mth.sin(t * 3.1F) * 0.4F;
		}
		this.lid.visible = (t % 90.0F) < 4.0F && s.shriek <= 0;
	}
}
