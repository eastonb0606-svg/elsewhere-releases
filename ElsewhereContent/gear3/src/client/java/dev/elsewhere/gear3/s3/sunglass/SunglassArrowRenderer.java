package dev.elsewhere.gear3.s3.sunglass;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/**
 * Sunglass Arrow in flight and stuck in blocks: the vanilla arrow model (like the spectral arrow) with its own 32x32 texture in the
 * vanilla arrow layout (pale sunglass head, dark shaft, amber fletching; the colours of the item sprite).
 */
public class SunglassArrowRenderer extends ArrowRenderer<SunglassArrow, ArrowRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/projectiles/sunglass_arrow.png");

	public SunglassArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected Identifier getTextureLocation(ArrowRenderState state) {
		return TEXTURE;
	}

	@Override
	public ArrowRenderState createRenderState() {
		return new ArrowRenderState();
	}
}
