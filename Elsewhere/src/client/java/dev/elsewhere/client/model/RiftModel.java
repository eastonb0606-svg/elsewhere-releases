package dev.elsewhere.client.model;

import dev.elsewhere.client.model.gen.RiftGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/** Two counter-rotating swirl discs, opening up over the first 3 s and collapsing when the rift closes. */
public class RiftModel extends EntityModel<States.Rift> {
	private final ModelPart outer;
	private final ModelPart inner;

	public RiftModel(final ModelPart root) {
		super(root, RenderTypes::entityTranslucentEmissive);
		this.outer = root.getChild("root").getChild("outer");
		this.inner = root.getChild("root").getChild("inner");
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(RiftGeometry.mesh(), RiftGeometry.TEXTURE_WIDTH, RiftGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Rift s) {
		super.setupAnim(s);
		float open = s.state == 2 ? 0.5F : Mth.clamp(s.age / 50.0F, 0.08F, 1.0F);
		float k = open * (1.0F + Mth.sin(s.age * 0.2F) * 0.03F);
		this.outer.xScale = 0.95F * k;
		this.outer.yScale = 1.45F * k;
		this.outer.zRot = s.age * 0.06F;
		this.inner.xScale = 0.6F * k;
		this.inner.yScale = 0.95F * k;
		this.inner.zRot = -s.age * 0.11F;
	}
}
