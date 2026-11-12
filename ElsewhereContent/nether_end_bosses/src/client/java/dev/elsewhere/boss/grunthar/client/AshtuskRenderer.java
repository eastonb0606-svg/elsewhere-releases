package dev.elsewhere.boss.grunthar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.grunthar.Ashtusk;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.hoglin.HoglinModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.resources.Identifier;

/** Ashtusk: an armored war-hoglin twice a hoglin's size (vanilla hoglin model, ash-dark tint). */
public class AshtuskRenderer extends MobRenderer<Ashtusk, HoglinRenderState, HoglinModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/hoglin/hoglin.png");

	public AshtuskRenderer(EntityRendererProvider.Context context) {
		super(context, new HoglinModel(context.bakeLayer(ModelLayers.HOGLIN)), 1.8F);
	}

	@Override
	public HoglinRenderState createRenderState() {
		return new HoglinRenderState();
	}

	@Override
	public void extractRenderState(Ashtusk entity, HoglinRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		String attack = entity.currentAttackId();
		state.attackAnimationRemainingTicks = "tusk_toss".equals(attack) ? Math.max(0, 10 - entity.attackAge()) : entity.hasFlag(Ashtusk.FLAG_CHARGING) ? 5 : 0;
	}

	@Override
	protected void scale(HoglinRenderState state, PoseStack poseStack) {
		poseStack.scale(2.1F, 2.1F, 2.1F);
	}

	@Override
	protected int getModelTint(HoglinRenderState state) {
		return 0xFFB09080;
	}

	@Override
	public Identifier getTextureLocation(HoglinRenderState state) {
		return TEXTURE;
	}
}
