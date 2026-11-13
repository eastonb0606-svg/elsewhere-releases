package dev.elsewhere.bossart.galvanox;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.galvanox.GalvanoxBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Custom renderer for Galvanox: cube model, painted oxidized-copper texture, pulsing lens/visor/lamp glow layer. */
public class GalvanoxArtRenderer extends MobRenderer<GalvanoxBoss, GalvanoxArtState, GalvanoxArtModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/galvanox.png");
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/galvanox_glow.png");
	/** Model is ~61 px tall to the lamp; 1.85x puts the lamp at ~7 blocks on a 7-block hitbox. */
	private static final float SCALE = 1.85F;

	public GalvanoxArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GalvanoxArtModel(ctx.bakeLayer(GalvanoxArtModel.LAYER)), 2.0F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, GalvanoxArtRenderer::glow,
			new GalvanoxArtModel(ctx.bakeLayer(GalvanoxArtModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	private static float glow(GalvanoxArtState s, float age) {
		if (s.deathTime > 0) return Math.max(0, 1 - s.deathTime / 14F) * (0.5F + 0.5F * Mth.sin(age * 2.5F));
		if (s.stunned) return 0.25F + 0.25F * Mth.sin(age * 1.7F);
		if (s.attack == GalvanoxBoss.A_HAMMER || s.attack == GalvanoxBoss.A_BOLT) return 0.85F + 0.15F * Mth.sin(age * 1.3F);
		float base = 0.55F + 0.15F * s.phase;
		return Math.min(1, base + 0.2F * Mth.sin(age * (0.1F + 0.08F * s.phase)));
	}

	@Override
	public Identifier getTextureLocation(GalvanoxArtState state) {
		return TEXTURE;
	}

	@Override
	public GalvanoxArtState createRenderState() {
		return new GalvanoxArtState();
	}

	@Override
	public void extractRenderState(GalvanoxBoss entity, GalvanoxArtState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		// 3.0 (f-anim): vanilla kicks walkAnimationSpeed to 1.5 on every hit, which played the WALK keyframes (arms swung out)
		// over the flinch; a standing boss flinches by the whole-body rock only
		if (((dev.elsewhere.verdigris_ironpeak.anim3.Hurt3Holder) state).elsewhere$vipHurt3() >= 0F && entity.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) state.walkAnimationSpeed = 0F;
		state.hammer.copyFrom(entity.attackAnims[GalvanoxBoss.A_HAMMER]);
		state.bolt.copyFrom(entity.attackAnims[GalvanoxBoss.A_BOLT]);
		state.wax.copyFrom(entity.attackAnims[GalvanoxBoss.A_WAX]);
		state.roar.copyFrom(entity.phaseAnim);
		state.attack = entity.currentAttack();
		state.phase = entity.currentPhase();
		state.plates = entity.plates();
		state.waxed = entity.isWaxed();
		state.stunned = entity.isStunned();
	}

	@Override
	protected int getModelTint(GalvanoxArtState state) {
		return state.waxed ? 0xFFFFE8A0 : -1; // a honey sheen while its plates are waxed
	}

	@Override
	protected void scale(GalvanoxArtState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // the keyframed collapse replaces the vanilla sideways flip
	}
}
