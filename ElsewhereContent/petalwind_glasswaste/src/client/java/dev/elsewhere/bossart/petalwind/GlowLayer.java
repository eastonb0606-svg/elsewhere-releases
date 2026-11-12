package dev.elsewhere.bossart.petalwind;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Emissive pass (eyes render type) over the boss model: glowing eyes, Kazehana's blade, Tempestra's rods. */
public class GlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final RenderType type;

	public GlowLayer(RenderLayerParent<S, M> parent, Identifier texture) {
		super(parent);
		this.type = RenderTypes.eyes(texture);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isInvisible) return;
		collector.order(1).submitModel(this.getParentModel(), state, poseStack, this.type, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}
