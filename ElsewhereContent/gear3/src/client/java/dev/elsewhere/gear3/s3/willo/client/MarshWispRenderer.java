package dev.elsewhere.gear3.s3.willo.client;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.willo.MarshWisp;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** The Marsh Wisp: a translucent, full-bright mote of marsh-light (MarshWispModel), no shadow. */
public class MarshWispRenderer extends MobRenderer<MarshWisp, LivingEntityRenderState, MarshWispModel> {
	static final Identifier TEXTURE = Gear3Content.id("textures/entity/marsh_wisp.png");

	public MarshWispRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new MarshWispModel(ctx.bakeLayer(MarshWispModel.LAYER)), 0.0F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState s) {
		return TEXTURE;
	}

	/** It is a light: always drawn full-bright. */
	@Override
	protected int getBlockLightLevel(MarshWisp e, BlockPos pos) {
		return 15;
	}
}
