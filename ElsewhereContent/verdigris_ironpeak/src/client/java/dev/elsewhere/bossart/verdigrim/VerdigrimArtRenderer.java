package dev.elsewhere.bossart.verdigrim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.verdigrim.VerdigrimBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Custom renderer for Archdeacon Verdigrim: cube model, painted texture, glowing eyes/mitre seam layer. */
public class VerdigrimArtRenderer extends MobRenderer<VerdigrimBoss, VerdigrimArtState, VerdigrimArtModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/verdigrim.png");
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/verdigrim_glow.png");
	/** Model is 42 px tall (with the mitre); 2.3x makes it ~6.1 blocks against a 6.2-block hitbox. */
	private static final float SCALE = 2.3F;

	public VerdigrimArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new VerdigrimArtModel(ctx.bakeLayer(VerdigrimArtModel.LAYER)), 1.3F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW,
			(s, age) -> s.deathTime > 0 ? Math.max(0, 1 - s.deathTime / 20F) : (s.stunned ? 0.35F : 0.8F + 0.2F * Mth.sin(age * 0.12F)),
			new VerdigrimArtModel(ctx.bakeLayer(VerdigrimArtModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public Identifier getTextureLocation(VerdigrimArtState state) {
		return TEXTURE;
	}

	@Override
	public VerdigrimArtState createRenderState() {
		return new VerdigrimArtState();
	}

	@Override
	public void extractRenderState(VerdigrimBoss entity, VerdigrimArtState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.slam.copyFrom(entity.attackAnims[VerdigrimBoss.A_SLAM]);
		state.note.copyFrom(entity.attackAnims[VerdigrimBoss.A_NOTE]);
		state.flood.copyFrom(entity.attackAnims[VerdigrimBoss.A_FLOOD]);
		state.arpeggio.copyFrom(entity.attackAnims[VerdigrimBoss.A_ARPEGGIO]);
		state.chord.copyFrom(entity.attackAnims[VerdigrimBoss.A_CHORD]);
		state.roar.copyFrom(entity.phaseAnim);
		state.attack = entity.currentAttack();
		state.phase = entity.currentPhase();
		state.stunned = entity.isStunned();
	}

	@Override
	protected void scale(VerdigrimArtState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // the keyframed death (kneel, topple forward) replaces the vanilla sideways flip
	}
}
