package dev.elsewhere.boss.grunthar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.grunthar.Grunthar;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.BabyPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;

/** Grunthar: a 1.6x piglin warlord with his golden axe (vanilla brute model, gilded tint). */
public class GruntharRenderer extends HumanoidMobRenderer<Grunthar, PiglinRenderState, PiglinModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/piglin/piglin_brute.png");

	public GruntharRenderer(EntityRendererProvider.Context context) {
		super(context, new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN_BRUTE)), new BabyPiglinModel(context.bakeLayer(ModelLayers.PIGLIN_BABY)), 0.9F, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
	}

	@Override
	public PiglinRenderState createRenderState() {
		return new PiglinRenderState();
	}

	@Override
	public void extractRenderState(Grunthar entity, PiglinRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.isBrute = true;
		state.armPose = entity.hasFlag(Grunthar.FLAG_RAISED_AXE) || entity.hasFlag(Grunthar.FLAG_THROWING) ? PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON
			: entity.hasFlag(Grunthar.FLAG_GREED) ? PiglinArmPose.ADMIRING_ITEM : PiglinArmPose.DEFAULT;
	}

	@Override
	protected void scale(PiglinRenderState state, PoseStack poseStack) {
		poseStack.scale(1.6F, 1.6F, 1.6F);
	}

	@Override
	protected int getModelTint(PiglinRenderState state) {
		return 0xFFFFE6A0;
	}

	@Override
	public Identifier getTextureLocation(PiglinRenderState state) {
		return TEXTURE;
	}
}
