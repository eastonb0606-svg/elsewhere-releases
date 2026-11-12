package dev.elsewhere.bossart.solaxis;

import dev.elsewhere.boss.solaxis.Solaxis;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class SolaxisArtRenderer extends BossRenderer<Solaxis, SolaxisArtModel.State, SolaxisArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/solaxis"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/solaxisart.png");
	private static final Identifier CHARGED = Reg.id("textures/entity/boss/solaxisart_charged.png");

	public SolaxisArtRenderer(EntityRendererProvider.Context context) {
		super(context, new SolaxisArtModel(context.bakeLayer(LAYER)), 1.6F, 1.11F, true);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/solaxisart_emissive.png")));
	}

	@Override
	public SolaxisArtModel.State createRenderState() {
		return new SolaxisArtModel.State();
	}

	@Override
	public void extractRenderState(Solaxis entity, SolaxisArtModel.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.rodsLeft = entity.rodsLeft();
		state.rodsGlow = entity.hasFlag(Solaxis.FLAG_RODS_GLOW);
		state.corePulse = entity.hasFlag(Solaxis.FLAG_CORE_PULSE);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(SolaxisArtModel.State state) {
		return state.rodsGlow || state.corePulse ? CHARGED : TEXTURE;
	}
}
