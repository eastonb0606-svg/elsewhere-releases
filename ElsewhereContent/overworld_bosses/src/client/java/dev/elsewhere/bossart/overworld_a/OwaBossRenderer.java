package dev.elsewhere.bossart.overworld_a;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Base renderer: copies the boss's animation states, draws the glow texture with an emissive layer, scales the model. */
public abstract class OwaBossRenderer<T extends ElsewhereBoss, S extends OwaRenderState, M extends EntityModel<S>> extends MobRenderer<T, S, M> {
	private final Identifier texture;
	private final float modelScale;

	protected OwaBossRenderer(EntityRendererProvider.Context ctx, M model, M glowModel, float shadow, float modelScale, Identifier texture, Identifier glow) {
		super(ctx, model, shadow);
		this.texture = texture;
		this.modelScale = modelScale;
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> glow, (s, age) -> glowAlpha(s), glowModel, RenderTypes::eyes, true));
	}

	protected float glowAlpha(S state) {
		return 1.0F;
	}

	@Override
	public void extractRenderState(T e, S s, float pt) {
		super.extractRenderState(e, s, pt);
		for (int i = 0; i < s.actions.length && i < e.actionAnim.length; i++) s.actions[i].copyFrom(e.actionAnim[i]);
		s.idle.copyFrom(e.idleAnim);
		s.transition.copyFrom(e.transitionAnim);
		s.phase = e.getPhase();
		s.action = e.getAction();
		// our own death animation replaces the vanilla sideways flip
		s.deathTicks = s.deathTime;
		s.deathTime = 0.0F;
	}

	@Override
	public Identifier getTextureLocation(S s) {
		return this.texture;
	}

	/** Extra lift in blocks (e.g. out of water), applied before the model scale. */
	protected float lift(S s) {
		return 0.0F;
	}

	@Override
	protected void scale(S s, PoseStack pose) {
		float lift = lift(s);
		if (lift != 0.0F) pose.translate(0.0F, -lift, 0.0F);
		pose.scale(this.modelScale, this.modelScale, this.modelScale);
	}
}
