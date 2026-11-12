package dev.elsewhere.bossart.heliodor;

import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Heliodor with its sun-face, core, halo rays, chest lens and heat seams on an emissive layer. */
public class HeliodorRenderer extends MobRenderer<Heliodor, HeliodorRenderState, HeliodorModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "bossart_heliodor"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/heliodor.png");
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/heliodor_glow.png");

	public HeliodorRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HeliodorModel(ctx.bakeLayer(LAYER)), 1.6F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, HeliodorRenderer::glow, this.getModel(), RenderTypes::entityTranslucentEmissive, false));
	}

	/** Full blaze normally; flickers while stunned by a mirror; dies out with the engine. */
	private static float glow(HeliodorRenderState s, float t) {
		if (s.deathTime > 0) return Math.max(0F, 1F - s.deathTime / 20F);
		if (s.stunned) return 0.35F + 0.25F * Mth.abs(Mth.sin(t * 0.9F));
		return 0.85F + 0.15F * Mth.sin(t * 0.15F);
	}

	@Override
	public HeliodorRenderState createRenderState() {
		return new HeliodorRenderState();
	}

	@Override
	public Identifier getTextureLocation(HeliodorRenderState state) {
		return TEXTURE;
	}

	@Override
	protected float getFlipDegrees() {
		return 0F; // the death animation kneels instead of the vanilla topple
	}

	@Override
	public void extractRenderState(Heliodor e, HeliodorRenderState s, float partialTicks) {
		super.extractRenderState(e, s, partialTicks);
		s.lance.copyFrom(e.lanceAnimation);
		s.slam.copyFrom(e.slamAnimation);
		s.crack.copyFrom(e.crackAnimation);
		s.vent.copyFrom(e.ventAnimation);
		s.sweep.copyFrom(e.sweepAnimation);
		s.phase = e.phase();
		s.cracks = e.cracks();
		s.stunned = e.stunned();
		int tt = e.transitionTicks();
		s.roarTime = tt > 0 ? (BossBase.TRANSITION_TICKS - tt + partialTicks) / 20F : -1F;
	}
}
