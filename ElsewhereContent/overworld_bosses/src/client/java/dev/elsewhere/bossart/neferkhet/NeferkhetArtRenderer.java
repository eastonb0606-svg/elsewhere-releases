package dev.elsewhere.bossart.neferkhet;

import dev.elsewhere.bossart.overworld_a.OwaBossRenderer;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/** Neferkhet's renderer: a 6-block king (humanoid pixel scale x3), ashen during the eclipse with the eyes still burning. */
public class NeferkhetArtRenderer extends OwaBossRenderer<NeferkhetEntity, NeferkhetArtModel.State, NeferkhetArtModel> {
	public static final float SCALE = 3.0F;

	public NeferkhetArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new NeferkhetArtModel(ctx.bakeLayer(NeferkhetArt.LAYER)), new NeferkhetArtModel(ctx.bakeLayer(NeferkhetArt.LAYER)), 1.4F, SCALE,
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/neferkhet.png"),
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/neferkhet_glow.png"));
	}

	@Override
	public NeferkhetArtModel.State createRenderState() {
		return new NeferkhetArtModel.State();
	}

	@Override
	public void extractRenderState(NeferkhetEntity e, NeferkhetArtModel.State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.eclipse = e.isEclipse();
	}

	@Override
	protected int getModelTint(NeferkhetArtModel.State s) {
		return s.eclipse ? 0xFF8A8078 : 0xFFFFFFFF;
	}
}
