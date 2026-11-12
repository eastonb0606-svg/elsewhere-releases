package dev.elsewhere.bossart.mournmaw;

import dev.elsewhere.boss.mournmaw.Mournmaw;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class MournmawArtRenderer extends BossRenderer<Mournmaw, BossRenderState, MournmawArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/mournmaw"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/mournmawart.png");
	private static final Identifier WAIL = Reg.id("textures/entity/boss/mournmawart_wail.png");

	public MournmawArtRenderer(EntityRendererProvider.Context context) {
		super(context, new MournmawArtModel(context.bakeLayer(LAYER)), 3.0F, 3.556F, false);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/mournmawart_emissive.png")));
	}

	@Override
	public BossRenderState createRenderState() {
		return new dev.elsewhere.bossart.nether.HurtAnim.State();
	}

	@Override
	public void extractRenderState(Mournmaw entity, BossRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(BossRenderState state) {
		return state.flag(Mournmaw.FLAG_INHALE) ? WAIL : TEXTURE;
	}
}
