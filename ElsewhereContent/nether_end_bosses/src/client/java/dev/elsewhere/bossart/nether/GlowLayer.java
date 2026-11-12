package dev.elsewhere.bossart.nether;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Emissive overlay (the art's emis/glow boxes): eyes, crown, blade, lanterns, molten head. */
public class GlowLayer<S extends EntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M> {
	private final RenderType type;

	public GlowLayer(RenderLayerParent<S, M> parent, Identifier texture) {
		super(parent);
		this.type = RenderTypes.eyes(texture);
	}

	@Override
	public RenderType renderType() {
		return type;
	}
}
