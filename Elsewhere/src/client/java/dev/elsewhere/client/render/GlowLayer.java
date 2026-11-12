package dev.elsewhere.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Full-bright overlay of the model's glowing pixels (resin, eyes). */
public class GlowLayer<S extends EntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M> {
	private final RenderType type;

	public GlowLayer(final RenderLayerParent<S, M> parent, final Identifier texture) {
		super(parent);
		this.type = RenderTypes.eyes(texture);
	}

	@Override
	public RenderType renderType() {
		return this.type;
	}
}
