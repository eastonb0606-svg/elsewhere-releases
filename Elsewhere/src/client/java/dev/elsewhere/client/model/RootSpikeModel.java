package dev.elsewhere.client.model;

import dev.elsewhere.client.model.gen.RootSpikeGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/** Root spike: erupts from the ground and sinks back. */
public class RootSpikeModel extends EntityModel<States.Spike> {
	private final ModelPart spikes;

	public RootSpikeModel(final ModelPart root) {
		super(root);
		this.spikes = root.getChild("root").getChild("spikes");
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(RootSpikeGeometry.mesh(), RootSpikeGeometry.TEXTURE_WIDTH, RootSpikeGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Spike s) {
		super.setupAnim(s);
		this.spikes.y += (1.0F - s.height) * 16.0F;
	}
}
