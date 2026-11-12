package dev.elsewhere.boss.solaxis.client;

import dev.elsewhere.boss.solaxis.Solaxis;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class SolaxisRenderer extends BossRenderer<Solaxis, SolaxisRenderState, SolaxisModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("solaxis"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/solaxis/solaxis.png");
	private static final Identifier CHARGED = Reg.id("textures/entity/solaxis/solaxis_charged.png");

	public SolaxisRenderer(EntityRendererProvider.Context context) {
		super(context, new SolaxisModel(context.bakeLayer(LAYER)), 1.6F, 2.0F, true);
	}

	@Override
	public SolaxisRenderState createRenderState() {
		return new SolaxisRenderState();
	}

	@Override
	public void extractRenderState(Solaxis entity, SolaxisRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.rodsLeft = entity.rodsLeft();
		state.rodsGlow = entity.hasFlag(Solaxis.FLAG_RODS_GLOW);
		state.corePulse = entity.hasFlag(Solaxis.FLAG_CORE_PULSE);
	}

	@Override
	public Identifier getTextureLocation(SolaxisRenderState state) {
		return state.rodsGlow || state.corePulse ? CHARGED : TEXTURE;
	}
}
