package dev.elsewhere.bossart.grunthar;

import dev.elsewhere.boss.grunthar.Ashtusk;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class AshtuskArtRenderer extends BossRenderer<Ashtusk, BossRenderState, AshtuskArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/ashtusk"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/ashtusk.png");

	public AshtuskArtRenderer(EntityRendererProvider.Context context) {
		super(context, new AshtuskArtModel(context.bakeLayer(LAYER)), 1.8F, 1.733F, false);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/ashtusk_emissive.png")));
	}

	@Override
	public BossRenderState createRenderState() {
		return new dev.elsewhere.bossart.nether.HurtAnim.State();
	}

	@Override
	public void extractRenderState(Ashtusk entity, BossRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(BossRenderState state) {
		return TEXTURE;
	}
}
