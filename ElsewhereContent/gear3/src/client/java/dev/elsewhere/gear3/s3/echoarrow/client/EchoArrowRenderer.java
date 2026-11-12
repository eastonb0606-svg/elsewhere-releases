package dev.elsewhere.gear3.s3.echoarrow.client;

import dev.elsewhere.gear3.s3.echoarrow.EchoArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** Echo Arrow in flight and stuck in blocks: the vanilla arrow model (like the spectral arrow) with the echo texture. */
public class EchoArrowRenderer extends ArrowRenderer<EchoArrowEntity, ArrowRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/projectiles/echo_arrow.png");

	public EchoArrowRenderer(EntityRendererProvider.Context context) {
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
