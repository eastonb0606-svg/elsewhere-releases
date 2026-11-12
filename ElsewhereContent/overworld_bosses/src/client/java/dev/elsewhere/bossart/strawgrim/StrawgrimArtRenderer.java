package dev.elsewhere.bossart.strawgrim;

import dev.elsewhere.bossart.overworld_a.OwaBossRenderer;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/** Strawgrim's renderer: about 5.8 blocks tall, ember-tinted while the effigy burns (phase 2+). */
public class StrawgrimArtRenderer extends OwaBossRenderer<StrawgrimEntity, StrawgrimArtModel.State, StrawgrimArtModel> {
	public static final float SCALE = 1.8F;

	public StrawgrimArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new StrawgrimArtModel(ctx.bakeLayer(StrawgrimArt.LAYER)), new StrawgrimArtModel(ctx.bakeLayer(StrawgrimArt.LAYER)), 1.3F, SCALE,
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/strawgrim.png"),
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/strawgrim_glow.png"));
	}

	@Override
	public StrawgrimArtModel.State createRenderState() {
		return new StrawgrimArtModel.State();
	}

	@Override
	public void extractRenderState(StrawgrimEntity e, StrawgrimArtModel.State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.headless = e.isHeadless();
		s.burning = e.isEffigyBurning();
	}

	@Override
	protected int getModelTint(StrawgrimArtModel.State s) {
		if (!s.burning) return 0xFFFFFFFF;
		// smouldering: a flickering ember tint
		float f = 0.85F + 0.15F * (float) Math.sin(s.ageInTicks * 0.6F);
		int g = (int) (150 + 40 * f), b = (int) (90 + 30 * f);
		return 0xFF000000 | 255 << 16 | g << 8 | b;
	}
}
