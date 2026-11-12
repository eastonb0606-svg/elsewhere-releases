package dev.elsewhere.overworld_bosses.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import dev.elsewhere.boss.rimeback.RimebackEntity;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.squid.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.creaking.CreakingModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.SquidRenderState;
import net.minecraft.resources.Identifier;

/**
 * BETA renderers for the five bosses: scaled and tinted vanilla models (no custom textures needed), driven by the
 * bosses' synced actions. The custom models from the concept art replace these after the beta.
 */
public final class BossRenderers {
	private BossRenderers() {}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** Old Gulpmother: a giant mangrove toad (the frog model, warm swamp skin, x6). */
	public static class Gulpmother extends MobRenderer<GulpmotherEntity, FrogRenderState, FrogModel> {
		private static final Identifier TEX = mc("textures/entity/frog/frog_warm.png");

		public Gulpmother(EntityRendererProvider.Context ctx) {
			super(ctx, new FrogModel(ctx.bakeLayer(ModelLayers.FROG)), 2.4F);
		}

		@Override
		public FrogRenderState createRenderState() {
			return new FrogRenderState();
		}

		@Override
		public void extractRenderState(GulpmotherEntity e, FrogRenderState s, float pt) {
			super.extractRenderState(e, s, pt);
			s.texture = TEX;
			s.isSwimming = false;
			s.tongueAnimationState.copyFrom(e.actionAnim[GulpmotherEntity.ACT_TONGUE]);
			s.jumpAnimationState.copyFrom(e.actionAnim[GulpmotherEntity.ACT_FLOP]);
			s.croakAnimationState.copyFrom(e.idleAnim);
		}

		@Override
		public Identifier getTextureLocation(FrogRenderState s) {
			return TEX;
		}

		@Override
		protected void scale(FrogRenderState s, PoseStack pose) {
			pose.scale(6.0F, 6.0F, 6.0F);
		}
	}

	/** Strawgrim: a tall creaking-wood scarecrow (x1.6), tinted harvest-orange while burning. */
	public static class Strawgrim extends MobRenderer<StrawgrimEntity, CreakingRenderState, CreakingModel> {
		private static final Identifier TEX = mc("textures/entity/creaking/creaking.png");

		public Strawgrim(EntityRendererProvider.Context ctx) {
			super(ctx, new CreakingModel(ctx.bakeLayer(ModelLayers.CREAKING)), 0.9F);
		}

		@Override
		public CreakingRenderState createRenderState() {
			return new CreakingRenderState();
		}

		@Override
		public void extractRenderState(StrawgrimEntity e, CreakingRenderState s, float pt) {
			super.extractRenderState(e, s, pt);
			s.attackAnimationState.copyFrom(e.actionAnim[StrawgrimEntity.ACT_REAP]);
			s.invulnerabilityAnimationState.copyFrom(e.transitionAnim);
			s.canMove = !e.isHeadless();
			s.eyesGlowing = true;
		}

		@Override
		public Identifier getTextureLocation(CreakingRenderState s) {
			return TEX;
		}

		@Override
		protected int getModelTint(CreakingRenderState s) {
			return 0xFFE0A060;
		}

		@Override
		protected void scale(CreakingRenderState s, PoseStack pose) {
			pose.scale(1.6F, 1.6F, 1.6F);
		}
	}

	/** Neferkhet: a 6-block mummy king (the husk model, x3.1), golden while the sun shines, ashen in the eclipse. */
	public static class Neferkhet extends MobRenderer<NeferkhetEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		private static final Identifier TEX = mc("textures/entity/zombie/husk.png");
		private boolean eclipse;

		public Neferkhet(EntityRendererProvider.Context ctx) {
			super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.HUSK)), 1.0F);
		}

		@Override
		public HumanoidRenderState createRenderState() {
			return new HumanoidRenderState();
		}

		@Override
		public void extractRenderState(NeferkhetEntity e, HumanoidRenderState s, float pt) {
			super.extractRenderState(e, s, pt);
			this.eclipse = e.isEclipse();
		}

		@Override
		public Identifier getTextureLocation(HumanoidRenderState s) {
			return TEX;
		}

		@Override
		protected int getModelTint(HumanoidRenderState s) {
			return this.eclipse ? 0xFF9A8E80 : 0xFFFFE6A8;
		}

		@Override
		protected void scale(HumanoidRenderState s, PoseStack pose) {
			pose.scale(3.1F, 3.1F, 3.1F);
		}
	}

	/** Rimeback: an ice colossus (the iron golem model, x2.6, tinted glacier blue). */
	public static class Rimeback extends MobRenderer<RimebackEntity, IronGolemRenderState, IronGolemModel> {
		private static final Identifier TEX = mc("textures/entity/iron_golem/iron_golem.png");

		public Rimeback(EntityRendererProvider.Context ctx) {
			super(ctx, new IronGolemModel(ctx.bakeLayer(ModelLayers.IRON_GOLEM)), 2.0F);
		}

		@Override
		public IronGolemRenderState createRenderState() {
			return new IronGolemRenderState();
		}

		@Override
		public void extractRenderState(RimebackEntity e, IronGolemRenderState s, float pt) {
			super.extractRenderState(e, s, pt);
			int a = e.getAction();
			s.attackTicksRemaining = (a == RimebackEntity.ACT_SLAM || a == RimebackEntity.ACT_THROW || a == RimebackEntity.ACT_STOMP) ? 8.0F : 0.0F;
		}

		@Override
		public Identifier getTextureLocation(IronGolemRenderState s) {
			return TEX;
		}

		@Override
		protected int getModelTint(IronGolemRenderState s) {
			return 0xFF9FD4F5;
		}

		@Override
		protected void scale(IronGolemRenderState s, PoseStack pose) {
			pose.scale(2.6F, 2.6F, 2.6F);
		}
	}

	/** Vorlagh: the kraken body (the squid model, x7, tinted abyssal purple). */
	public static class Vorlagh extends MobRenderer<VorlaghEntity, SquidRenderState, SquidModel> {
		private static final Identifier TEX = mc("textures/entity/squid/squid.png");

		public Vorlagh(EntityRendererProvider.Context ctx) {
			super(ctx, new SquidModel(ctx.bakeLayer(ModelLayers.SQUID)), 3.0F);
		}

		@Override
		public SquidRenderState createRenderState() {
			return new SquidRenderState();
		}

		@Override
		public void extractRenderState(VorlaghEntity e, SquidRenderState s, float pt) {
			super.extractRenderState(e, s, pt);
			s.tentacleAngle = (float) (Math.sin(s.ageInTicks * 0.1F) * 0.4F + 0.5F);
			s.xBodyRot = 0.0F;
			s.zBodyRot = 0.0F;
		}

		@Override
		public Identifier getTextureLocation(SquidRenderState s) {
			return TEX;
		}

		@Override
		protected int getModelTint(SquidRenderState s) {
			return 0xFFB070E0;
		}

		@Override
		protected void scale(SquidRenderState s, PoseStack pose) {
			pose.scale(7.0F, 7.0F, 7.0F);
		}
	}
}
