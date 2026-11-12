package dev.elsewhere.bossart.grunthar;

import dev.elsewhere.boss.grunthar.Grunthar;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class GruntharArtRenderer extends BossRenderer<Grunthar, GruntharArtModel.State, GruntharArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/grunthar"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/grunthar.png");

	public GruntharArtRenderer(EntityRendererProvider.Context context) {
		super(context, new GruntharArtModel(context.bakeLayer(LAYER)), 0.9F, 1.6F, false);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/grunthar_emissive.png")));
	}

	@Override
	public GruntharArtModel.State createRenderState() {
		return new GruntharArtModel.State();
	}

	@Override
	public void extractRenderState(Grunthar entity, GruntharArtModel.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
		state.riding = entity.isPassenger();
	}

	@Override
	public Identifier getTextureLocation(GruntharArtModel.State state) {
		return TEXTURE;
	}
}
