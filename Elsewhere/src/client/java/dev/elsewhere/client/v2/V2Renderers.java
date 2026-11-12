package dev.elsewhere.client.v2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.palehollow.entity.CompanionEntity;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.animal.goat.GoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.GoatRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** 2.0 mob renderers: vanilla models with Elsewhere textures (cheap to render, per DECISIONS mob-model guidance). */
public final class V2Renderers {
	private V2Renderers() {
	}

	static Identifier tex(final String name) {
		return Elsewhere.id("textures/entity/" + name + ".png");
	}

	/** Hollow Stag: goat rig, horns always on (antlers), pale bone hide, a little larger than a goat. */
	public static class Stag extends AgeableMobRenderer<CompanionEntity, GoatRenderState, GoatModel> {
		private static final Identifier TEX = tex("hollow_stag");

		public Stag(final EntityRendererProvider.Context ctx) {
			super(ctx, new GoatModel(ctx.bakeLayer(ModelLayers.GOAT)), new GoatModel(ctx.bakeLayer(ModelLayers.GOAT)), 0.8F);
		}

		@Override
		public GoatRenderState createRenderState() {
			return new GoatRenderState();
		}

		@Override
		public void extractRenderState(final CompanionEntity entity, final GoatRenderState state, final float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.hasLeftHorn = true;
			state.hasRightHorn = true;
			state.rammingXHeadRot = 0.0F;
		}

		@Override
		protected void scale(final GoatRenderState state, final PoseStack pose) {
			pose.scale(1.15F, 1.15F, 1.15F);
		}

		@Override
		public Identifier getTextureLocation(final GoatRenderState state) {
			return TEX;
		}
	}

	/** Docile Glass Scarab: endermite rig, glassy amber-teal shell, scaled up. */
	public static class Scarab extends MobRenderer<CompanionEntity, LivingEntityRenderState, EndermiteModel> {
		private static final Identifier TEX = tex("docile_scarab");

		public Scarab(final EntityRendererProvider.Context ctx) {
			super(ctx, new EndermiteModel(ctx.bakeLayer(ModelLayers.ENDERMITE)), 0.4F);
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		protected float getFlipDegrees() {
			return 180.0F;
		}

		@Override
		protected void scale(final LivingEntityRenderState state, final PoseStack pose) {
			pose.scale(1.4F, 1.4F, 1.4F);
		}

		@Override
		public Identifier getTextureLocation(final LivingEntityRenderState state) {
			return TEX;
		}
	}

	/** Haze Ferret: cat rig, sage-grey fur, smaller than a cat; sits when ordered. */
	public static class Ferret extends AgeableMobRenderer<CompanionEntity, CatRenderState, AdultCatModel> {
		private static final Identifier TEX = tex("haze_ferret");

		public Ferret(final EntityRendererProvider.Context ctx) {
			super(ctx, new AdultCatModel(ctx.bakeLayer(ModelLayers.CAT)), new AdultCatModel(ctx.bakeLayer(ModelLayers.CAT)), 0.3F);
		}

		@Override
		public CatRenderState createRenderState() {
			return new CatRenderState();
		}

		@Override
		public void extractRenderState(final CompanionEntity entity, final CatRenderState state, final float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.texture = TEX;
			state.isSitting = entity.isInSittingPose();
			state.isCrouching = false;
			state.isSprinting = entity.isSprinting();
			state.collarColor = null;
		}

		@Override
		protected void scale(final CatRenderState state, final PoseStack pose) {
			pose.scale(0.8F, 0.8F, 0.8F);
		}

		@Override
		public Identifier getTextureLocation(final CatRenderState state) {
			return TEX;
		}
	}

	/** Bog Wight: zombie rig with a murky bog texture. */
	public static class Wight extends ZombieRenderer {
		private static final Identifier TEX = tex("bog_wight");

		public Wight(final EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public Identifier getTextureLocation(final ZombieRenderState state) {
			return TEX;
		}
	}
}
