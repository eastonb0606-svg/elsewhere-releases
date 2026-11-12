package dev.elsewhere.gear3.s3.starpiercer.client;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.starpiercer.StarpiercerArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** The Starpiercer's arrow: the vanilla arrow model with its own star-steel texture, drawn at full brightness (it glows in the dark). */
public class StarpiercerArrowRenderer extends ArrowRenderer<StarpiercerArrow, ArrowRenderState> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/projectiles/starpiercer_arrow.png");

	public StarpiercerArrowRenderer(EntityRendererProvider.Context context) {
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

	@Override
	protected int getBlockLightLevel(StarpiercerArrow entity, BlockPos pos) {
		return 15;
	}
}
